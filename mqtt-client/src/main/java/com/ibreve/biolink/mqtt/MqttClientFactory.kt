package com.ibreve.biolink.mqtt

import android.content.Context
import com.ibreve.biolink.mqtt.internal.DefaultMqttClient
import com.ibreve.biolink.mqtt.internal.hivemq.HiveMqMqttEngine
import com.ibreve.biolink.mqtt.internal.paho.PahoMqttEngine
import com.ibreve.biolink.mqtt.internal.paho.validatePahoConfig

object MqttClientFactory {
    fun create(
        context: Context,
        config: MqttClientConfig,
        implementation: MqttClientImplementation = MqttClientImplementation.HIVE_MQ,
    ): MqttClient {
        return when (implementation) {
            MqttClientImplementation.HIVE_MQ -> createHiveMq(config)
            MqttClientImplementation.PAHO -> createPaho(context, config)
        }
    }

    fun createHiveMq(config: MqttClientConfig): MqttClient {
        return DefaultMqttClient(
            config = config,
            engine = HiveMqMqttEngine(config),
        )
    }

    fun createPaho(context: Context, config: MqttClientConfig): MqttClient {
        validatePahoConfig(config)

        return DefaultMqttClient(
            config = config,
            engine = PahoMqttEngine(context.applicationContext, config),
        )
    }
}
