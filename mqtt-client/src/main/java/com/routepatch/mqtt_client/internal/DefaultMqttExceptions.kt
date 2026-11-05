package com.routepatch.mqtt_client.internal

import com.routepatch.mqtt_client.MqttClientException

internal fun Throwable.asMqttClientException(operation: String): MqttClientException {
    if (this is MqttClientException) {
        return this
    }

    val detail = message ?: javaClass.simpleName
    return MqttClientException(
        message = "MQTT $operation failed: $detail",
        cause = this,
    )
}
