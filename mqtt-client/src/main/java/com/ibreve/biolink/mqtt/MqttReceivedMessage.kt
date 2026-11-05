package com.ibreve.biolink.mqtt

class MqttReceivedMessage(
    val topic: String,
    val payload: ByteArray,
    val qos: MqttQos,
    val retained: Boolean,
    val duplicate: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttReceivedMessage) return false
        return topic == other.topic &&
            payload.contentEquals(other.payload) &&
            qos == other.qos &&
            retained == other.retained &&
            duplicate == other.duplicate
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + qos.hashCode()
        result = 31 * result + retained.hashCode()
        result = 31 * result + duplicate.hashCode()
        return result
    }

    override fun toString(): String {
        return "MqttReceivedMessage(topic=$topic, payload=${payload.contentToString()}, qos=$qos, retained=$retained, duplicate=$duplicate)"
    }
}
