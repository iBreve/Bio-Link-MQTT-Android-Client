package com.routepatch.mqtt_client

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MqttClient : AutoCloseable {
    val state: StateFlow<MqttConnectionState>
    val messages: SharedFlow<MqttReceivedMessage>

    suspend fun connect()

    suspend fun disconnect(quiesceTimeoutMillis: Long = 30_000)

    suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: MqttQos,
        retained: Boolean = false,
    )

    suspend fun subscribe(topic: String, qos: MqttQos)

    suspend fun unsubscribe(topic: String)

    override fun close()
}
