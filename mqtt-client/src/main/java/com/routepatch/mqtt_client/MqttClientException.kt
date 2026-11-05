package com.routepatch.mqtt_client

class MqttClientException(
    message: String,
    cause: Throwable? = null,
    val reasonCode: Int? = null,
) : Exception(message, cause)
