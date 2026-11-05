package com.routepatch.mqtt_client

enum class MqttQos(val value: Int) {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2);

    companion object {
        fun fromValue(value: Int): MqttQos {
            return entries.firstOrNull { it.value == value }
                ?: throw MqttClientException("Unsupported MQTT QoS value: $value")
        }
    }
}
