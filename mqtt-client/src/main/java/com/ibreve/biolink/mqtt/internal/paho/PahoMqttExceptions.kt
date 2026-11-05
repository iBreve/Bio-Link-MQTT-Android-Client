package com.ibreve.biolink.mqtt.internal.paho

import com.ibreve.biolink.mqtt.MqttClientException
import org.eclipse.paho.client.mqttv3.MqttException

internal fun Throwable.asPahoMqttClientException(operation: String): MqttClientException {
    if (this is MqttClientException) {
        return this
    }

    val reasonCode = (this as? MqttException)?.reasonCode
    val detail = message ?: javaClass.simpleName
    return MqttClientException(
        message = "MQTT $operation failed: $detail",
        cause = this,
        reasonCode = reasonCode,
    )
}
