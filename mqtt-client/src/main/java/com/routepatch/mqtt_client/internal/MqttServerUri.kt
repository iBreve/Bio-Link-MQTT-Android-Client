package com.routepatch.mqtt_client.internal

import com.routepatch.mqtt_client.MqttClientException
import java.net.URI

internal data class MqttServerUri(
    val host: String,
    val port: Int,
    val secure: Boolean,
) {
    companion object {
        fun parse(serverUri: String): MqttServerUri {
            val uri = try {
                URI(serverUri.trim())
            } catch (exception: IllegalArgumentException) {
                throw MqttClientException("MQTT serverUri is malformed: $serverUri", exception)
            }

            val scheme = uri.scheme?.lowercase()
                ?: throw MqttClientException("MQTT serverUri must include a scheme")
            val secure = when (scheme) {
                "tcp", "mqtt" -> false
                "ssl", "tls" -> true
                else -> throw MqttClientException("Unsupported MQTT serverUri scheme: $scheme")
            }
            val host = uri.host
                ?: throw MqttClientException("MQTT serverUri must include a host")
            val port = if (uri.port == -1) {
                if (secure) 8883 else 1883
            } else {
                uri.port
            }
            if (port !in 1..65535) {
                throw MqttClientException("MQTT serverUri port is out of range: $port")
            }

            return MqttServerUri(
                host = host,
                port = port,
                secure = secure,
            )
        }
    }
}
