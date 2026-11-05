package com.routepatch.mqtt_client.internal.paho

import com.routepatch.mqtt_client.MqttClientException
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
