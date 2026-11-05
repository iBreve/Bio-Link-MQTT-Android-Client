# Bio-Link MQTT Android Client

<p align="center">
  <img src="https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Bio-Link – Funded by the European Union via FORTIS" width="420"/>
</p>

An open-source MQTT client library for Android with a modern Kotlin coroutine API, plus a small Jetpack Compose sample app.

The library is a general-purpose MQTT transport layer. It carries MQTT messages but does not define or interpret their content, so it is not tied to any application, message schema or robotics framework. It was developed in the [FORTIS project](https://fortis-project.eu) as the MQTT transport of the Bio-Link Android application, which exchanges messages with robots through the [Bio-Link ROS2 Robot Bridge](https://github.com/iBreve/Bio-Link-ROS2-Robot-Bridge). It is released openly so that other FORTIS partners and third-party Android developers can reuse it independently. Application-level topics and payloads are defined by the applications built on top of it and are not part of this repository.

## Highlights

- **One Kotlin API for MQTT.** Suspending `connect`, `publish`, `subscribe`, `unsubscribe` and `disconnect` calls, with connection state exposed as a `StateFlow` and incoming messages as a `SharedFlow`.
- **Two interchangeable engines behind the same interface.** HiveMQ MQTT Client (MQTT 3.1.1 and 5.0) or Eclipse Paho (MQTT 3.1.1). Application code does not import engine classes, and the engine is chosen with a single parameter.
- **Secure connections.** `ssl://` and `tls://` brokers, username and password authentication, and custom trust and client-certificate configuration.
- **Reliable sessions.** QoS 0, 1 and 2, retained messages, last will, keep-alive, connection timeout and automatic reconnect.
- **Early error detection.** Inputs are validated before they reach the engine, and engine failures are mapped to a single exception type.
- **Self-contained Paho support.** The Paho Android service source is vendored and maintained in this repository, so no separate Paho Android artifact is required.
- **Sample app and tests.** A runnable sample app, unit tests, and an instrumented test against a public broker.

## Modules

- `mqtt-client`: the Android library (public MQTT API, engine selection, validation, ProGuard consumer rules, publishing configuration).
- `app`: sample app that connects to a broker, subscribes, publishes, receives messages, disconnects, and logs state changes.

## Quick Start

The library is currently used from source or from your local Maven repository (see [Publishing](#publishing)). Create a client through `MqttClientFactory`:

```kotlin
import com.routepatch.mqtt_client.*

val client = MqttClientFactory.create(
    context = context,
    config = MqttClientConfig(
        serverUri = "tcp://broker.hivemq.com:1883",
        clientId = "android-client",
        protocolVersion = MqttProtocolVersion.MQTT_5_0,
    ),
    implementation = MqttClientImplementation.HIVE_MQ,
)
```

Connect, subscribe, publish, and close from a coroutine:

```kotlin
client.connect()
client.subscribe("sample/android", MqttQos.AT_LEAST_ONCE)

client.publish(
    topic = "sample/android",
    payload = "Hello MQTT".encodeToByteArray(),
    qos = MqttQos.AT_LEAST_ONCE,
)

client.disconnect()
client.close()
```

Collect connection state and received messages:

```kotlin
viewModelScope.launch {
    client.state.collect { state ->
        // MqttConnectionState.Disconnected, Connecting, Connected, etc.
    }
}

viewModelScope.launch {
    client.messages.collect { message ->
        val payloadText = message.payload.decodeToString()
    }
}
```

## Configuration

`MqttClientConfig` contains the broker URI, client ID, connection options, and MQTT protocol version:

```kotlin
val config = MqttClientConfig(
    serverUri = "ssl://example.com:8883",
    clientId = "android-client",
    protocolVersion = MqttProtocolVersion.MQTT_3_1_1,
    options = MqttConnectionOptions(
        username = "user",
        password = "secret".toCharArray(),
        cleanSession = true,
        automaticReconnect = true,
        keepAliveIntervalSeconds = 60,
        connectionTimeoutSeconds = 30,
        lastWill = MqttLastWill(
            topic = "clients/android/status",
            payload = "offline".encodeToByteArray(),
            qos = MqttQos.AT_LEAST_ONCE,
            retained = true,
        ),
    ),
)
```

Supported URI schemes:

- `tcp://` and `mqtt://` for plaintext connections. The default port is `1883`.
- `ssl://` and `tls://` for TLS connections. The default port is `8883`.

MQTT 3.1.1 is the default protocol version. HiveMQ supports `MQTT_3_1_1` and `MQTT_5_0`; Paho supports `MQTT_3_1_1` only in this wrapper.

The library validates basic inputs before calling the underlying engine:

- `serverUri` and `clientId` must not be blank.
- Publish topics must not contain `+` or `#` wildcards.
- Subscribe and unsubscribe topics must not be empty.
- Topics must not contain a null character.
- Keep-alive and connection timeout values must be greater than zero.

### TLS

`ssl://` and `tls://` URIs enable TLS. Without extra options, both engines use the platform default trust configuration. `MqttConnectionOptions` offers engine-specific settings for custom trust and client certificates:

| Option | HiveMQ | Paho |
| --- | --- | --- |
| `trustManagerFactory` | Used | Ignored |
| `keyManagerFactory` | Used | Ignored |
| `sslSocketFactory` | Ignored | Used |

Example: trusting a private CA with the HiveMQ engine.

```kotlin
val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
    load(null, null)
    setCertificateEntry("ca", caCertificate) // java.security.cert.Certificate
}
val trustManagerFactory = TrustManagerFactory
    .getInstance(TrustManagerFactory.getDefaultAlgorithm())
    .apply { init(keyStore) }

val config = MqttClientConfig(
    serverUri = "ssl://example.com:8883",
    clientId = "android-client",
    options = MqttConnectionOptions(trustManagerFactory = trustManagerFactory),
)
```

For Paho, build an `SSLContext` from the same trust managers and pass `sslContext.socketFactory` as `sslSocketFactory`. The sample app's `MqttSampleViewModel` shows this approach.

## Public API Surface

The main public types are:

- `MqttClient`: connect, disconnect, publish, subscribe, unsubscribe, close.
- `MqttClientFactory`: creates HiveMQ or Paho-backed clients.
- `MqttClientConfig` and `MqttConnectionOptions`: connection setup.
- `MqttProtocolVersion`: `MQTT_3_1_1` or `MQTT_5_0`.
- `MqttClientImplementation`: `HIVE_MQ` or `PAHO`.
- `MqttQos`: `AT_MOST_ONCE`, `AT_LEAST_ONCE`, `EXACTLY_ONCE`.
- `MqttConnectionState`: observable lifecycle state through `StateFlow`.
- `MqttReceivedMessage`: received topic, payload, QoS, retained flag, and duplicate flag.
- `MqttClientException`: normalized exception type with an optional Paho reason code.

## Android Manifest And Shrinking

The library manifest contributes:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.WAKE_LOCK`
- The internal Paho `MqttService`

The library also ships consumer ProGuard rules that keep the internal Paho service and binder classes.

If your app enables code shrinking and uses the HiveMQ engine, also follow the [HiveMQ MQTT Client Android guidance](https://www.hivemq.com/blog/hivemq-mqtt-client-features/android-support/): keep rules for `io.netty` and `org.jctools`, and exclusion of two `META-INF` files from packaging. The sample app and library modules already apply the packaging exclusions (`META-INF/INDEX.LIST` and `META-INF/io.netty.versions.properties`).

## Sample App

Run the sample app with:

```bash
./gradlew :app:assembleDebug
```

The app defaults to the public HiveMQ broker `tcp://broker.hivemq.com:1883`. It lets you choose:

- HiveMQ or Paho implementation.
- MQTT 3.1.1 or MQTT 5.0 when HiveMQ is selected.
- Broker URL, client ID, username, password, topic, and message payload.

Use a unique client ID when testing against a public broker. The sample generates one by default.

In the sample app, the username and password fields and the optional custom CA certificate are applied to the Paho implementation only. To trust a private CA for `ssl://` brokers with Paho, place a PEM or DER encoded X.509 certificate at `app/src/main/res/raw/ca` (no file extension). If no such file exists, the sample uses the Android default trust store.

## Requirements

- Android Studio or the included Gradle wrapper.
- JDK 21 for the Gradle daemon. The wrapper is configured with Foojay toolchain resolution.
- Android SDK matching the project compile SDK configuration: API 36 with minor API level 1.
- Android device or emulator running Android 14 (API 34) or newer.

Current build stack:

- Gradle wrapper: `9.4.1`
- Android Gradle Plugin: `9.2.1`
- Kotlin: `2.2.10`
- Compose BOM: `2026.02.01`
- Kotlin coroutines: `1.10.2`
- HiveMQ MQTT Client: `1.3.14`
- Eclipse Paho MQTT v3 client: `1.2.5`

## Build And Test

Build the sample app:

```bash
./gradlew :app:assembleDebug
```

Run library unit tests:

```bash
./gradlew :mqtt-client:testDebugUnitTest
```

Run instrumented broker tests on a connected device or emulator:

```bash
./gradlew :mqtt-client:connectedDebugAndroidTest
```

The instrumented test connects to `tcp://broker.hivemq.com:1883`, so it requires network access and depends on the availability of that public broker.

## Publishing

Publish the library to Maven local:

```bash
./gradlew :mqtt-client:publishToMavenLocal
```

The checked-in `gradle.properties` publishes the library as:

```text
com.routepatch.biolink:mqtt-client:0.0.1-SNAPSHOT
```

Override coordinates with Gradle properties:

```properties
mqttClientGroup=com.routepatch.biolink
mqttClientArtifactId=mqtt-client
mqttClientVersion=0.0.1-SNAPSHOT
```

To publish to a generic private Maven repository:

```properties
mqttClientRepositoryUrl=https://maven.example.com/releases
mqttClientRepositoryUsername=your-username
mqttClientRepositoryPassword=your-password
```

To publish to Bitbucket Packages, set credentials in your user-level Gradle properties or CI environment, not in the repository:

```properties
bitbucketPackagesWorkspace=your-workspace-slug
atlassianEmail=your.email@example.com
atlassianApiToken=your-atlassian-api-token
```

Equivalent Bitbucket-specific property names are also supported:

```properties
bitbucketPackagesUrl=https://maven.apkg.io/your-workspace-slug
bitbucketPackagesUsername=your.email@example.com
bitbucketPackagesToken=your-atlassian-api-token
```

In Bitbucket Pipelines, the build also reads:

```text
BITBUCKET_PACKAGES_USERNAME
BITBUCKET_PACKAGES_TOKEN
```

## Status And Limitations

- Pre-release version `0.0.1-SNAPSHOT`; the API may still change.
- The Paho engine supports MQTT 3.1.1 only. Use the HiveMQ engine for MQTT 5.0.
- No artifact is published to a public Maven repository yet; use the library from source or Maven local.

## License

Copyright 2026 iBreve Ltd

This project is licensed under the [Apache License, Version 2.0](LICENSE). You may not use these files except in compliance with the License. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.

Exception: the vendored Eclipse Paho Android Service sources in `mqtt-client/src/main/java/com/routepatch/mqtt_client/internal/paho/*.java` keep their original copyright and license headers and remain available under the Eclipse Public License v1.0 and Eclipse Distribution License v1.0 (copies in [`licenses/`](licenses)).

## Third-Party Notices

The mqtt-client module vendors source from Eclipse Paho Android Service 1.1.1. See [mqtt-client/THIRD_PARTY_NOTICES.md](mqtt-client/THIRD_PARTY_NOTICES.md) for details.

## Acknowledgement

<img src="https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Funded by the European Union" width="300"/>

The Bio-Link project has received funding from EC through the FORTIS (Grant Agreement no. 101135707).

Views and opinions expressed are those of the author(s) only and do not necessarily reflect those of the European Union or the European Research Executive Agency. Neither the European Union nor the granting authority can be held responsible for them.

## Contact

For questions, bug reports or feature requests, please [open an issue](../../issues) in this repository.
