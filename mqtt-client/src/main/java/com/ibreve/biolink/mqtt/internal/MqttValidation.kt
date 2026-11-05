package com.ibreve.biolink.mqtt.internal

import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttClientException
import com.ibreve.biolink.mqtt.MqttConnectionOptions
import com.ibreve.biolink.mqtt.MqttLastWill

internal fun validateConfig(config: MqttClientConfig) {
    if (config.serverUri.isBlank()) {
        throw MqttClientException("MQTT serverUri must not be blank")
    }
    if (config.clientId.isBlank()) {
        throw MqttClientException("MQTT clientId must not be blank")
    }
    validateOptions(config.options)
    MqttServerUri.parse(config.serverUri)
}

private fun validateOptions(options: MqttConnectionOptions) {
    if (options.keepAliveIntervalSeconds <= 0) {
        throw MqttClientException("MQTT keepAliveIntervalSeconds must be greater than 0")
    }
    if (options.connectionTimeoutSeconds <= 0) {
        throw MqttClientException("MQTT connectionTimeoutSeconds must be greater than 0")
    }
    options.lastWill?.let(::validateLastWill)
}

private fun validateLastWill(lastWill: MqttLastWill) {
    validatePublishTopic(lastWill.topic)
}

internal fun validatePublishTopic(topic: String) {
    validateTopic(topic)
    if (topic.contains('#') || topic.contains('+')) {
        throw MqttClientException("MQTT publish topic must not contain wildcards")
    }
}

internal fun validateSubscriptionTopic(topic: String) {
    validateTopic(topic)
}

private fun validateTopic(topic: String) {
    if (topic.isEmpty()) {
        throw MqttClientException("MQTT topic must not be empty")
    }
    if (topic.indexOf('\u0000') >= 0) {
        throw MqttClientException("MQTT topic must not contain a null character")
    }
}
