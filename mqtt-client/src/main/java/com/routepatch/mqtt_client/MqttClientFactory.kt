package com.routepatch.mqtt_client

import android.content.Context
import com.routepatch.mqtt_client.internal.DefaultMqttClient
import com.routepatch.mqtt_client.internal.hivemq.HiveMqMqttEngine
import com.routepatch.mqtt_client.internal.paho.PahoMqttEngine
import com.routepatch.mqtt_client.internal.paho.validatePahoConfig

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
