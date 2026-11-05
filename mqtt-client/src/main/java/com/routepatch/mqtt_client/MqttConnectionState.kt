package com.routepatch.mqtt_client

sealed interface MqttConnectionState {
    data object Disconnected : MqttConnectionState
    data object Connecting : MqttConnectionState
    data class Connected(
        val serverUri: String,
        val reconnected: Boolean = false,
    ) : MqttConnectionState
    data object Disconnecting : MqttConnectionState
    data class ConnectionLost(val exception: MqttClientException?) : MqttConnectionState
    data class Error(val exception: MqttClientException) : MqttConnectionState
    data object Closed : MqttConnectionState
}
