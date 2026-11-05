package com.ibreve.biolink.mqtt.internal

import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage

internal interface MqttEngine {
    val isConnected: Boolean

    fun setListener(listener: MqttEngineListener)

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

    fun close()
}

internal interface MqttEngineListener {
    fun onConnected(serverUri: String, reconnected: Boolean)

    fun onConnectionLost(cause: Throwable?)

    fun onMessageArrived(message: MqttReceivedMessage)
}

internal object NoOpMqttEngineListener : MqttEngineListener {
    override fun onConnected(serverUri: String, reconnected: Boolean) = Unit

    override fun onConnectionLost(cause: Throwable?) = Unit

    override fun onMessageArrived(message: MqttReceivedMessage) = Unit
}
