package com.ibreve.biolink.mqtt.internal.hivemq

import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttClientException
import com.ibreve.biolink.mqtt.MqttProtocolVersion
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage
import com.ibreve.biolink.mqtt.internal.MqttEngine
import com.ibreve.biolink.mqtt.internal.MqttEngineListener
import com.ibreve.biolink.mqtt.internal.MqttServerUri
import com.ibreve.biolink.mqtt.internal.NoOpMqttEngineListener
import com.hivemq.client.mqtt.MqttClientBuilder
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttClientTransportConfigBuilder
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos as HiveMqQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.hivemq.client.mqtt.MqttClient as HiveMqClient
import kotlinx.coroutines.withTimeoutOrNull

internal class HiveMqMqttEngine(
    private val config: MqttClientConfig,
) : MqttEngine {
    private val serverUri = MqttServerUri.parse(config.serverUri)
    private val explicitDisconnect = AtomicBoolean(false)
    private val connectedOnce = AtomicBoolean(false)
    private val listenerRef = AtomicReference<MqttEngineListener>(NoOpMqttEngineListener)
    private val client = buildProtocolClient()

    override val isConnected: Boolean
        get() = client.isConnected

    override fun setListener(listener: MqttEngineListener) {
        listenerRef.set(listener)
    }

    override suspend fun connect(options: MqttConnectionOptions) {
        explicitDisconnect.set(false)
        client.connect(options)
    }

    override suspend fun disconnect(quiesceTimeoutMillis: Long) {
        explicitDisconnect.set(true)
        client.disconnect(quiesceTimeoutMillis)
    }

    override suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: MqttQos,
        retained: Boolean,
    ) {
        client.publish(topic, payload, qos, retained)
    }

    override suspend fun subscribe(topic: String, qos: MqttQos) {
        client.subscribe(topic, qos)
    }

    override suspend fun unsubscribe(topic: String) {
        client.unsubscribe(topic)
    }

    override fun close() {
        if (client.shouldDisconnectOnClose) {
            explicitDisconnect.set(true)
            client.disconnectAsync()
        }
    }

    private fun buildProtocolClient(): ProtocolClient {
        var builder = HiveMqClient.builder()
            .identifier(config.clientId)
            .configureTransport()

        if (config.options.automaticReconnect) {
            builder = builder.automaticReconnectWithDefaultConfig()
        }

        builder = builder
            .addConnectedListener {
                val reconnected = connectedOnce.getAndSet(true)
                listenerRef.get().onConnected(config.serverUri, reconnected)
            }
            .addDisconnectedListener { context ->
                if (explicitDisconnect.compareAndSet(true, false)) {
                    return@addDisconnectedListener
                }
                listenerRef.get().onConnectionLost(context.cause)
            }

        return when (config.protocolVersion) {
            MqttProtocolVersion.MQTT_3_1_1 -> buildMqtt3Client(builder)
            MqttProtocolVersion.MQTT_5_0 -> buildMqtt5Client(builder)
        }
    }

    private fun buildMqtt3Client(builder: MqttClientBuilder): ProtocolClient {
        val client = builder.useMqttVersion3()
            .buildAsync()

        client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            listenerRef.get().onMessageArrived(toReceivedMessage(publish.topic, publish.payloadAsBytes, publish.qos, publish.isRetain))
        }

        return Mqtt3ProtocolClient(client)
    }

    private fun buildMqtt5Client(builder: MqttClientBuilder): ProtocolClient {
        val client = builder.useMqttVersion5()
            .buildAsync()

        client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            listenerRef.get().onMessageArrived(toReceivedMessage(publish.topic, publish.payloadAsBytes, publish.qos, publish.isRetain))
        }

        return Mqtt5ProtocolClient(client)
    }

    private fun MqttClientBuilder.configureTransport(): MqttClientBuilder {
        var transportBuilder = transportConfig()
            .serverHost(serverUri.host)
            .serverPort(serverUri.port)
            .socketConnectTimeout(config.options.connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .mqttConnectTimeout(config.options.connectionTimeoutSeconds.toLong(), TimeUnit.SECONDS)

        if (serverUri.secure) {
            transportBuilder = if (config.options.keyManagerFactory != null ||
                config.options.trustManagerFactory != null
            ) {
                var sslBuilder = transportBuilder.sslConfig()
                config.options.keyManagerFactory?.let {
                    sslBuilder = sslBuilder.keyManagerFactory(it)
                }
                config.options.trustManagerFactory?.let {
                    sslBuilder = sslBuilder.trustManagerFactory(it)
                }
                sslBuilder.applySslConfig()
            } else {
                transportBuilder.sslWithDefaultConfig()
            }
        }

        return transportBuilder.applyTransportConfig()
    }

    private fun buildConnect(
        client: Mqtt3AsyncClient,
        options: MqttConnectionOptions,
    ): Mqtt3ConnectBuilder.Send<CompletableFuture<Mqtt3ConnAck>> {
        var connectBuilder = client.connectWith()
            .cleanSession(options.cleanSession)
            .keepAlive(options.keepAliveIntervalSeconds)

        options.username?.let { username ->
            var authBuilder = connectBuilder.simpleAuth()
                .username(username)
            options.password?.let {
                authBuilder = authBuilder.password(encodeToByteArray(it))
            }
            connectBuilder = authBuilder.applySimpleAuth()
        }

        options.lastWill?.let { lastWill ->
            connectBuilder = connectBuilder.willPublish()
                .topic(lastWill.topic)
                .payload(lastWill.payload)
                .qos(lastWill.qos.toHiveMqQos())
                .retain(lastWill.retained)
                .applyWillPublish()
        }

        return connectBuilder
    }

    private fun buildConnect(
        client: Mqtt5AsyncClient,
        options: MqttConnectionOptions,
    ): Mqtt5ConnectBuilder.Send<CompletableFuture<Mqtt5ConnAck>> {
        var connectBuilder = client.connectWith()
            .cleanStart(options.cleanSession)
            .keepAlive(options.keepAliveIntervalSeconds)

        connectBuilder = if (options.cleanSession) {
            connectBuilder.sessionExpiryInterval(0)
        } else {
            connectBuilder.noSessionExpiry()
        }

        options.username?.let { username ->
            var authBuilder = connectBuilder.simpleAuth()
                .username(username)
            options.password?.let {
                authBuilder = authBuilder.password(encodeToByteArray(it))
            }
            connectBuilder = authBuilder.applySimpleAuth()
        }

        options.lastWill?.let { lastWill ->
            connectBuilder = connectBuilder.willPublish()
                .topic(lastWill.topic)
                .payload(lastWill.payload)
                .qos(lastWill.qos.toHiveMqQos())
                .retain(lastWill.retained)
                .applyWillPublish()
        }

        return connectBuilder
    }

    private fun toReceivedMessage(
        topic: com.hivemq.client.mqtt.datatypes.MqttTopic,
        payloadAsBytes: ByteArray,
        qos: HiveMqQos,
        isRetain: Boolean,
    ): MqttReceivedMessage {
        return MqttReceivedMessage(
            topic = topic.toString(),
            payload = payloadAsBytes,
            qos = qos.toPublicQos(),
            retained = isRetain,
            duplicate = false,
        )
    }

    private fun MqttQos.toHiveMqQos(): HiveMqQos {
        return when (this) {
            MqttQos.AT_MOST_ONCE -> HiveMqQos.AT_MOST_ONCE
            MqttQos.AT_LEAST_ONCE -> HiveMqQos.AT_LEAST_ONCE
            MqttQos.EXACTLY_ONCE -> HiveMqQos.EXACTLY_ONCE
        }
    }

    private fun HiveMqQos.toPublicQos(): MqttQos {
        return when (this) {
            HiveMqQos.AT_MOST_ONCE -> MqttQos.AT_MOST_ONCE
            HiveMqQos.AT_LEAST_ONCE -> MqttQos.AT_LEAST_ONCE
            HiveMqQos.EXACTLY_ONCE -> MqttQos.EXACTLY_ONCE
        }
    }

    private interface ProtocolClient {
        val isConnected: Boolean
        val shouldDisconnectOnClose: Boolean

        suspend fun connect(options: MqttConnectionOptions)

        suspend fun disconnect(quiesceTimeoutMillis: Long)

        suspend fun publish(
            topic: String,
            payload: ByteArray,
            qos: MqttQos,
            retained: Boolean,
        )

        suspend fun subscribe(topic: String, qos: MqttQos)

        suspend fun unsubscribe(topic: String)

        fun disconnectAsync()
    }

    private abstract inner class AbstractProtocolClient : ProtocolClient {
        protected abstract fun doConnect(options: MqttConnectionOptions): CompletableFuture<*>
        protected abstract fun doDisconnect(): CompletableFuture<Void>
        protected abstract fun doPublish(
            topic: String,
            payload: ByteArray,
            qos: HiveMqQos,
            retained: Boolean,
        ): CompletableFuture<*>

        protected abstract fun doSubscribe(topic: String, qos: HiveMqQos): CompletableFuture<*>

        protected abstract fun doUnsubscribe(topic: String): CompletableFuture<*>

        protected abstract fun doDisconnectAsync()

        override suspend fun connect(options: MqttConnectionOptions) {
            doConnect(options).awaitMqtt("connect")
        }

        override suspend fun disconnect(quiesceTimeoutMillis: Long) {
            val disconnected = withTimeoutOrNull(quiesceTimeoutMillis) {
                doDisconnect().awaitMqtt("disconnect")
                true
            } ?: false
            if (!disconnected) {
                throw MqttClientException("MQTT disconnect timed out after $quiesceTimeoutMillis ms")
            }
        }

        override suspend fun publish(
            topic: String,
            payload: ByteArray,
            qos: MqttQos,
            retained: Boolean,
        ) {
            doPublish(topic, payload, qos.toHiveMqQos(), retained).awaitMqtt("publish")
        }

        override suspend fun subscribe(topic: String, qos: MqttQos) {
            doSubscribe(topic, qos.toHiveMqQos()).awaitMqtt("subscribe")
        }

        override suspend fun unsubscribe(topic: String) {
            doUnsubscribe(topic).awaitMqtt("unsubscribe")
        }

        override fun disconnectAsync() {
            doDisconnectAsync()
        }
    }

    private inner class Mqtt3ProtocolClient(
        private val client: Mqtt3AsyncClient,
    ) : AbstractProtocolClient() {
        override val isConnected: Boolean
            get() = client.state.isConnected

        override val shouldDisconnectOnClose: Boolean
            get() = client.state.shouldDisconnectOnClose()

        override fun doConnect(options: MqttConnectionOptions) =
            buildConnect(client, options).send()

        override fun doDisconnect() = client.disconnect()

        override fun doPublish(
            topic: String,
            payload: ByteArray,
            qos: HiveMqQos,
            retained: Boolean,
        ) = client.publishWith()
            .topic(topic)
            .payload(payload)
            .qos(qos)
            .retain(retained)
            .send()

        override fun doSubscribe(topic: String, qos: HiveMqQos) =
            client.subscribeWith()
                .topicFilter(topic)
                .qos(qos)
                .send()

        override fun doUnsubscribe(topic: String) =
            client.unsubscribeWith()
                .topicFilter(topic)
                .send()

        override fun doDisconnectAsync() {
            client.disconnect()
        }
    }

    private inner class Mqtt5ProtocolClient(
        private val client: Mqtt5AsyncClient,
    ) : AbstractProtocolClient() {
        override val isConnected: Boolean
            get() = client.state.isConnected

        override val shouldDisconnectOnClose: Boolean
            get() = client.state.shouldDisconnectOnClose()

        override fun doConnect(options: MqttConnectionOptions) =
            buildConnect(client, options).send()

        override fun doDisconnect() = client.disconnect()

        override fun doPublish(
            topic: String,
            payload: ByteArray,
            qos: HiveMqQos,
            retained: Boolean,
        ) = client.publishWith()
            .topic(topic)
            .payload(payload)
            .qos(qos)
            .retain(retained)
            .send()

        override fun doSubscribe(topic: String, qos: HiveMqQos) =
            client.subscribeWith()
                .topicFilter(topic)
                .qos(qos)
                .send()

        override fun doUnsubscribe(topic: String) =
            client.unsubscribeWith()
                .topicFilter(topic)
                .send()

        override fun doDisconnectAsync() {
            client.disconnect()
        }
    }

    private fun MqttClientState.shouldDisconnectOnClose(): Boolean {
        return this == MqttClientState.CONNECTED ||
            this == MqttClientState.DISCONNECTED_RECONNECT ||
            this == MqttClientState.CONNECTING_RECONNECT
    }

    private fun encodeToByteArray(chars: CharArray, charset: Charset = Charsets.UTF_8): ByteArray {
        val byteBuffer = charset.encode(java.nio.CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }
}
