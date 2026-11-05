package com.ibreve.biolink.mqtt.internal

import com.ibreve.biolink.mqtt.MqttClientException

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
