package com.ibreve.biolink.mqtt

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

data class MqttClientConfig(
    val serverUri: String,
    val clientId: String,
    val options: MqttConnectionOptions = MqttConnectionOptions(),
    val protocolVersion: MqttProtocolVersion = MqttProtocolVersion.MQTT_3_1_1,
)

enum class MqttProtocolVersion {
    MQTT_3_1_1,
    MQTT_5_0,
}

data class MqttConnectionOptions(
    val username: String? = null,
    val password: CharArray? = null,
    val cleanSession: Boolean = true,
    val automaticReconnect: Boolean = true,
    val keepAliveIntervalSeconds: Int = 60,
    val connectionTimeoutSeconds: Int = 30,
    val lastWill: MqttLastWill? = null,
    val sslSocketFactory: SSLSocketFactory? = null,
    val keyManagerFactory: KeyManagerFactory? = null,
    val trustManagerFactory: TrustManagerFactory? = null,
)

class MqttLastWill(
    val topic: String,
    val payload: ByteArray,
    val qos: MqttQos,
    val retained: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttLastWill) return false
        return topic == other.topic &&
            payload.contentEquals(other.payload) &&
            qos == other.qos &&
            retained == other.retained
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + qos.hashCode()
        result = 31 * result + retained.hashCode()
        return result
    }

    override fun toString(): String {
        return "MqttLastWill(topic=$topic, payload=${payload.contentToString()}, qos=$qos, retained=$retained)"
    }
}
