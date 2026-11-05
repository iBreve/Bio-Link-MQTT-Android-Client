package com.ibreve.biolink.mqtt

class MqttClientException(
    message: String,
    cause: Throwable? = null,
    val reasonCode: Int? = null,
) : Exception(message, cause)
