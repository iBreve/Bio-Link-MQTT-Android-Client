package com.ibreve.biolink.mqtt

import android.content.ContextWrapper
import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttClientException
import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttConnectionState
import com.ibreve.biolink.mqtt.MqttProtocolVersion
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage
import com.ibreve.biolink.mqtt.internal.DefaultMqttClient
import com.ibreve.biolink.mqtt.internal.MqttEngine
import com.ibreve.biolink.mqtt.internal.MqttEngineListener
import com.ibreve.biolink.mqtt.internal.NoOpMqttEngineListener
import com.ibreve.biolink.mqtt.internal.paho.validatePahoConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultMqttClientTest {
    @Test
    fun defaultProtocolVersionIsMqtt311() {
        val config = MqttClientConfig(
            serverUri = "tcp://broker.example:1883",
            clientId = "test-client",
        )

        assertEquals(MqttProtocolVersion.MQTT_3_1_1, config.protocolVersion)
    }

    @Test
    fun hiveMqFactoryCanCreateMqtt5Client() {
        val client = MqttClientFactory.createHiveMq(
            MqttClientConfig(
                serverUri = "tcp://broker.example:1883",
                clientId = "test-client",
                protocolVersion = MqttProtocolVersion.MQTT_5_0,
            ),
        )

        client.close()
    }

    @Test
    fun pahoConfigRejectsMqtt5() {
        val exception = assertMqttThrowsSync {
            validatePahoConfig(
                MqttClientConfig(
                    serverUri = "tcp://broker.example:1883",
                    clientId = "test-client",
                    protocolVersion = MqttProtocolVersion.MQTT_5_0,
                ),
            )
        }

        assertTrue(exception.message.orEmpty().contains("Paho"))
        assertTrue(exception.message.orEmpty().contains("MQTT 5.0"))
    }

    @Test
    fun pahoFactoryRejectsMqtt5Client() {
        val exception = assertMqttThrowsSync {
            MqttClientFactory.create(
                context = ContextWrapper(null),
                config = MqttClientConfig(
                    serverUri = "tcp://broker.example:1883",
                    clientId = "test-client",
                    protocolVersion = MqttProtocolVersion.MQTT_5_0,
                ),
                implementation = MqttClientImplementation.PAHO,
            )
        }

        assertTrue(exception.message.orEmpty().contains("Paho"))
        assertTrue(exception.message.orEmpty().contains("MQTT 5.0"))
    }

    @Test
    fun explicitMqtt5ConfigIsAcceptedByDefaultClient() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(
            engine = engine,
            config = MqttClientConfig(
                serverUri = "tcp://broker.example:1883",
                clientId = "test-client",
                protocolVersion = MqttProtocolVersion.MQTT_5_0,
            ),
        )

        client.connect()

        assertEquals(MqttConnectionState.Connected("tcp://broker.example:1883"), client.state.value)
        assertTrue(engine.isConnected)
    }

    @Test
    fun connectAndDisconnectUpdateState() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(engine)

        client.connect()

        assertEquals(MqttConnectionState.Connected("tcp://broker.example:1883"), client.state.value)
        assertTrue(engine.isConnected)

        client.disconnect()

        assertEquals(MqttConnectionState.Disconnected, client.state.value)
        assertEquals(listOf("connect", "disconnect:30000"), engine.calls)
    }

    @Test
    fun disconnectForwardsCustomQuiesceTimeout() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(engine)

        client.connect()
        client.disconnect(quiesceTimeoutMillis = 5_000)

        assertEquals(listOf("connect", "disconnect:5000"), engine.calls)
    }

    @Test
    fun failureIsMappedToClientExceptionAndErrorState() = runBlocking {
        val engine = FakeMqttEngine(failingOperation = "connect")
        val client = createClient(engine)

        val exception = assertMqttThrows {
            client.connect()
        }

        assertTrue(exception.message.orEmpty().contains("connect"))
        val state = client.state.value
        assertTrue(state is MqttConnectionState.Error)
        assertSame(exception, (state as MqttConnectionState.Error).exception)
    }

    @Test
    fun publishSubscribeAndUnsubscribeDelegateToEngine() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(engine)
        val payload = byteArrayOf(1, 2, 3)

        client.publish(
            topic = "sensors/temperature",
            payload = payload,
            qos = MqttQos.AT_LEAST_ONCE,
            retained = true,
        )
        client.subscribe("sensors/#", MqttQos.EXACTLY_ONCE)
        client.unsubscribe("sensors/#")

        assertEquals(
            listOf(
                "publish:sensors/temperature:1:true",
                "subscribe:sensors/#:2",
                "unsubscribe:sensors/#",
            ),
            engine.calls,
        )
        assertArrayEquals(payload, engine.lastPayload)
    }

    @Test
    fun receivedMessagesAreEmitted() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(engine)
        val expected = MqttReceivedMessage(
            topic = "devices/one",
            payload = byteArrayOf(10, 20),
            qos = MqttQos.AT_MOST_ONCE,
            retained = false,
            duplicate = false,
        )

        val received = async {
            withTimeout(1_000) {
                client.messages.first()
            }
        }
        yield()
        engine.emit(expected)

        assertEquals(expected, received.await())
    }

    @Test
    fun validationRejectsInvalidConfigAndTopics() = runBlocking {
        assertMqttThrows {
            createClient(FakeMqttEngine(), MqttClientConfig(serverUri = "", clientId = "client"))
        }
        assertMqttThrows {
            createClient(FakeMqttEngine(), MqttClientConfig(serverUri = "ws://broker.example", clientId = "client"))
        }

        val client = createClient(FakeMqttEngine())

        assertMqttThrows {
            client.publish(
                topic = "devices/+",
                payload = ByteArray(0),
                qos = MqttQos.AT_MOST_ONCE,
            )
        }
        assertMqttThrows {
            client.subscribe("", MqttQos.AT_MOST_ONCE)
        }
        Unit
    }

    @Test
    fun closeReleasesEngineAndPreventsFurtherOperations() = runBlocking {
        val engine = FakeMqttEngine()
        val client = createClient(engine)

        client.close()

        assertTrue(engine.closed)
        assertEquals(MqttConnectionState.Closed, client.state.value)
        assertMqttThrows {
            client.connect()
        }
        Unit
    }

    private fun createClient(
        engine: FakeMqttEngine,
        config: MqttClientConfig = MqttClientConfig(
            serverUri = "tcp://broker.example:1883",
            clientId = "test-client",
        ),
    ): DefaultMqttClient {
        return DefaultMqttClient(config, engine)
    }

    private suspend fun assertMqttThrows(block: suspend () -> Unit): MqttClientException {
        try {
            block()
            fail("Expected MqttClientException")
        } catch (exception: MqttClientException) {
            return exception
        }
        throw AssertionError("Expected MqttClientException")
    }

    private fun assertMqttThrowsSync(block: () -> Unit): MqttClientException {
        try {
            block()
            fail("Expected MqttClientException")
        } catch (exception: MqttClientException) {
            return exception
        }
        throw AssertionError("Expected MqttClientException")
    }

    private class FakeMqttEngine(
        private val failingOperation: String? = null,
    ) : MqttEngine {
        override var isConnected: Boolean = false
            private set

        val calls = mutableListOf<String>()
        var lastPayload: ByteArray = ByteArray(0)
            private set
        var closed: Boolean = false
            private set

        private var listener: MqttEngineListener = NoOpMqttEngineListener

        override fun setListener(listener: MqttEngineListener) {
            this.listener = listener
        }

        override suspend fun connect(options: MqttConnectionOptions) {
            calls += "connect"
            failIfNeeded("connect")
            isConnected = true
        }

        override suspend fun disconnect(quiesceTimeoutMillis: Long) {
            calls += "disconnect:$quiesceTimeoutMillis"
            failIfNeeded("disconnect")
            isConnected = false
        }

        override suspend fun publish(
            topic: String,
            payload: ByteArray,
            qos: MqttQos,
            retained: Boolean,
        ) {
            calls += "publish:$topic:${qos.value}:$retained"
            lastPayload = payload
            failIfNeeded("publish")
        }

        override suspend fun subscribe(topic: String, qos: MqttQos) {
            calls += "subscribe:$topic:${qos.value}"
            failIfNeeded("subscribe")
        }

        override suspend fun unsubscribe(topic: String) {
            calls += "unsubscribe:$topic"
            failIfNeeded("unsubscribe")
        }

        override fun close() {
            closed = true
        }

        fun emit(message: MqttReceivedMessage) {
            listener.onMessageArrived(message)
        }

        private fun failIfNeeded(operation: String) {
            if (failingOperation == operation) {
                throw IllegalStateException("boom")
            }
        }
    }
}
