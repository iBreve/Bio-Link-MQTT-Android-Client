package com.ibreve.biolink.mqtt.internal.paho

import android.content.Context
import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttClientException
import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttProtocolVersion
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage
import com.ibreve.biolink.mqtt.internal.MqttEngine
import com.ibreve.biolink.mqtt.internal.MqttEngineListener
import com.ibreve.biolink.mqtt.internal.NoOpMqttEngineListener
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PahoMqttEngine(
    context: Context,
    config: MqttClientConfig,
) : MqttEngine {
    private val pahoConfig = config.also(::validatePahoConfig)
    private val client = MqttAndroidClient(
        context.applicationContext,
        pahoConfig.serverUri,
        pahoConfig.clientId,
        MqttAndroidClient.Ack.AUTO_ACK,
    )
    private var listener: MqttEngineListener = NoOpMqttEngineListener

    override val isConnected: Boolean
        get() = client.isConnected

    init {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                listener.onConnected(serverURI, reconnect)
            }

            override fun connectionLost(cause: Throwable?) {
                listener.onConnectionLost(cause)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                listener.onMessageArrived(
                    MqttReceivedMessage(
                        topic = topic,
                        payload = message.payload ?: ByteArray(0),
                        qos = MqttQos.fromValue(message.qos),
                        retained = message.isRetained,
                        duplicate = message.isDuplicate,
                    ),
                )
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
    }

    override fun setListener(listener: MqttEngineListener) {
        this.listener = listener
    }

    override suspend fun connect(options: MqttConnectionOptions) {
        awaitAction("connect") { callback ->
            client.connect(options.toPahoOptions(), null, callback)
        }
    }

    override suspend fun disconnect(quiesceTimeoutMillis: Long) {
        awaitAction("disconnect") { callback ->
            client.disconnect(quiesceTimeoutMillis, null, callback)
        }
    }

    override suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: MqttQos,
        retained: Boolean,
    ) {
        awaitAction("publish") { callback ->
            client.publish(topic, payload, qos.value, retained, null, callback)
        }
    }

    override suspend fun subscribe(topic: String, qos: MqttQos) {
        awaitAction("subscribe") { callback ->
            client.subscribe(topic, qos.value, null, callback)
        }
    }

    override suspend fun unsubscribe(topic: String) {
        awaitAction("unsubscribe") { callback ->
            client.unsubscribe(topic, null, callback)
        }
    }

    override fun close() {
        client.unregisterResources()
        client.close()
    }

    private suspend fun awaitAction(
        operation: String,
        action: (IMqttActionListener) -> IMqttToken,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val callback = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            (exception ?: RuntimeException("Unknown MQTT $operation failure"))
                                .asPahoMqttClientException(operation),
                        )
                    }
                }
            }

            try {
                action(callback)
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable.asPahoMqttClientException(operation))
                }
            }
        }
    }

    private fun MqttConnectionOptions.toPahoOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            setCleanSession(cleanSession)
            setAutomaticReconnect(automaticReconnect)
            setKeepAliveInterval(keepAliveIntervalSeconds)
            setConnectionTimeout(connectionTimeoutSeconds)
            username?.let(::setUserName)
            this@toPahoOptions.password?.let { setPassword(it.copyOf()) }
            sslSocketFactory?.let(::setSocketFactory)
            lastWill?.let {
                setWill(it.topic, it.payload, it.qos.value, it.retained)
            }
        }
    }
}

internal fun validatePahoConfig(config: MqttClientConfig) {
    when (config.protocolVersion) {
        MqttProtocolVersion.MQTT_3_1_1 -> Unit
        MqttProtocolVersion.MQTT_5_0 -> {
            throw MqttClientException(
                "Paho MQTT implementation only supports MQTT 3.1.1; use HiveMQ for MQTT 5.0",
            )
        }
    }
}
