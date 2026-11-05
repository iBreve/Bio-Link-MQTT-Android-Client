package com.ibreve.biolink.mqtt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ibreve.biolink.mqtt.MqttClient
import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttConnectionState
import com.ibreve.biolink.mqtt.MqttLastWill
import com.ibreve.biolink.mqtt.MqttProtocolVersion
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage
import java.util.UUID
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HiveMqBrokerInstrumentedTest {
    @Test
    fun connectsPublishesReceivesRetainedMessagesUnsubscribesAndDisconnects() {
        runBlocking {
            runBrokerScenario(MqttProtocolVersion.MQTT_3_1_1)
            runBrokerScenario(MqttProtocolVersion.MQTT_5_0)
        }
    }

    private suspend fun runBrokerScenario(protocolVersion: MqttProtocolVersion) = coroutineScope {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString().replace("-", "")
        val protocolName = protocolVersion.name.lowercase()
        val rootTopic = "android-hivemq-client-test/$protocolName/$testId"
        val retainedTopic = "$rootTopic/retained"
        val client = MqttClientFactory.create(
            context = context,
            config = MqttClientConfig(
                serverUri = "tcp://broker.hivemq.com:1883",
                clientId = "hivemq-${protocolName.take(4)}-${testId.take(7)}",
                protocolVersion = protocolVersion,
                options = MqttConnectionOptions(
                    cleanSession = true,
                    automaticReconnect = true,
                    keepAliveIntervalSeconds = 20,
                    connectionTimeoutSeconds = 30,
                    lastWill = MqttLastWill(
                        topic = "$rootTopic/last-will",
                        payload = "unexpected-disconnect".encodeToByteArray(),
                        qos = MqttQos.AT_LEAST_ONCE,
                    ),
                ),
            ),
        )
        val receivedMessages = Channel<MqttReceivedMessage>(Channel.UNLIMITED)
        val collector = launch {
            client.messages.collect { message ->
                if (message.topic.startsWith(rootTopic)) {
                    receivedMessages.send(message)
                }
            }
        }

        try {
            client.connect()
            assertTrue(client.state.value is MqttConnectionState.Connected)

            client.subscribe("$rootTopic/#", MqttQos.EXACTLY_ONCE)

            val qos0 = publishAndAwait(
                client = client,
                receivedMessages = receivedMessages,
                topic = "$rootTopic/qos0",
                payload = "message-qos-0",
                qos = MqttQos.AT_MOST_ONCE,
            )
            assertEquals(MqttQos.AT_MOST_ONCE, qos0.qos)

            val qos1 = publishAndAwait(
                client = client,
                receivedMessages = receivedMessages,
                topic = "$rootTopic/qos1",
                payload = "message-qos-1",
                qos = MqttQos.AT_LEAST_ONCE,
            )
            assertEquals(MqttQos.AT_LEAST_ONCE, qos1.qos)

            val qos2 = publishAndAwait(
                client = client,
                receivedMessages = receivedMessages,
                topic = "$rootTopic/qos2",
                payload = "message-qos-2",
                qos = MqttQos.EXACTLY_ONCE,
            )
            assertEquals(MqttQos.EXACTLY_ONCE, qos2.qos)

            client.unsubscribe("$rootTopic/#")
            drain(receivedMessages)

            val retainedPayload = "retained-message"
            client.publish(
                topic = retainedTopic,
                payload = retainedPayload.encodeToByteArray(),
                qos = MqttQos.AT_LEAST_ONCE,
                retained = true,
            )
            delay(500)

            client.subscribe(retainedTopic, MqttQos.AT_LEAST_ONCE)
            val retainedMessage = receivedMessages.awaitMessage(
                topic = retainedTopic,
                payload = retainedPayload,
            )
            assertTrue(retainedMessage.retained)
            assertEquals(MqttQos.AT_LEAST_ONCE, retainedMessage.qos)

            client.publish(
                topic = retainedTopic,
                payload = ByteArray(0),
                qos = MqttQos.AT_LEAST_ONCE,
                retained = true,
            )
            client.unsubscribe(retainedTopic)
            drain(receivedMessages)

            val afterUnsubscribeTopic = "$rootTopic/after-unsubscribe"
            client.publish(
                topic = afterUnsubscribeTopic,
                payload = "should-not-arrive".encodeToByteArray(),
                qos = MqttQos.AT_LEAST_ONCE,
            )
            assertFalse(receivedMessages.hasMessageForTopic(afterUnsubscribeTopic))

            client.disconnect()
            assertEquals(MqttConnectionState.Disconnected, client.state.value)
        } finally {
            runCatching {
                client.publish(
                    topic = retainedTopic,
                    payload = ByteArray(0),
                    qos = MqttQos.AT_LEAST_ONCE,
                    retained = true,
                )
            }
            runCatching { client.disconnect() }
            client.close()
            collector.cancelAndJoin()
            receivedMessages.close()
        }
    }

    private suspend fun publishAndAwait(
        client: MqttClient,
        receivedMessages: Channel<MqttReceivedMessage>,
        topic: String,
        payload: String,
        qos: MqttQos,
    ): MqttReceivedMessage {
        val bytes = payload.encodeToByteArray()
        client.publish(
            topic = topic,
            payload = bytes,
            qos = qos,
        )
        val message = receivedMessages.awaitMessage(topic, payload)
        assertArrayEquals(bytes, message.payload)
        assertFalse(message.retained)
        return message
    }

    private suspend fun Channel<MqttReceivedMessage>.awaitMessage(
        topic: String,
        payload: String,
        timeoutMillis: Long = 30_000,
    ): MqttReceivedMessage {
        return withTimeout(timeoutMillis) {
            while (true) {
                val message = receive()
                if (message.topic == topic && message.payload.decodeToString() == payload) {
                    return@withTimeout message
                }
            }
            error("Unreachable")
        }
    }

    private suspend fun Channel<MqttReceivedMessage>.hasMessageForTopic(
        topic: String,
        timeoutMillis: Long = 2_000,
    ): Boolean {
        return withTimeoutOrNull(timeoutMillis) {
            while (true) {
                if (receive().topic == topic) {
                    return@withTimeoutOrNull true
                }
            }
            false
        } ?: false
    }

    private fun drain(channel: Channel<MqttReceivedMessage>) {
        while (true) {
            channel.tryReceive().getOrNull() ?: return
        }
    }
}
