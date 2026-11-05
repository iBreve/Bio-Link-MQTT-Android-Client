# Third-Party Notices

## Vendored source

This module vendors source from Eclipse Paho Android Service 1.1.1
(`src/main/java/com/ibreve/biolink/mqtt/internal/paho/*.java`).

- Project: Eclipse Paho Android Service
- Source: https://github.com/eclipse-paho/paho.mqtt.android
- License: Eclipse Public License v1.0 and Eclipse Distribution License v1.0
  (copies in [`../licenses/`](../licenses))

The vendored files retain their original copyright and license headers. They are
not covered by the repository's Apache License 2.0; they remain available under
the licenses stated in those headers. The Kotlin files in the same directory
(`PahoMqttEngine.kt`, `PahoMqttExceptions.kt`) are original to this project and
are covered by the Apache License 2.0.

## Runtime dependencies (not vendored)

Resolved from Maven repositories at build time. Check each project for the
license terms that apply to the version you use.

- HiveMQ MQTT Client (`com.hivemq:hivemq-mqtt-client`) - Apache License 2.0
- Eclipse Paho Java Client (`org.eclipse.paho:org.eclipse.paho.client.mqttv3`)
  - Eclipse Public License 2.0
- kotlinx.coroutines - Apache License 2.0
- AndroidX libraries - Apache License 2.0
