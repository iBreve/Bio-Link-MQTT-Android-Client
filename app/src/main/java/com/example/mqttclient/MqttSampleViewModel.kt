package com.example.mqttclient

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routepatch.mqtt_client.MqttClient
import com.routepatch.mqtt_client.MqttClientConfig
import com.routepatch.mqtt_client.MqttClientFactory
import com.routepatch.mqtt_client.MqttClientImplementation
import com.routepatch.mqtt_client.MqttConnectionOptions
import com.routepatch.mqtt_client.MqttConnectionState
import com.routepatch.mqtt_client.MqttProtocolVersion
import com.routepatch.mqtt_client.MqttQos
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class MqttSampleViewModel(application: Application) : AndroidViewModel(application) {
    enum class MqttImplementation(val label: String) {
        HIVE_MQ("HiveMQ"),
        PAHO("Paho"),
    }

    data class UiState(
        val implementation: MqttImplementation = MqttImplementation.HIVE_MQ,
        val hiveMqProtocol: MqttProtocolVersion = MqttProtocolVersion.MQTT_3_1_1,
        val brokerUrl: String = "tcp://broker.hivemq.com:1883",
        val clientId: String = "android-${java.util.UUID.randomUUID().toString().take(8)}",
        val username: String = "",
        val password: String = "",
        val topic: String = "sample/android",
        val message: String = "",
        val connectionState: MqttConnectionState = MqttConnectionState.Disconnected,
        val busy: Boolean = false,
        val logEntries: List<String> = emptyList(),
    ) {
        val connected get() = connectionState is MqttConnectionState.Connected
        val connecting get() = connectionState is MqttConnectionState.Connecting
        val canSwitchImplementation get() = !busy && !connected && !connecting
        val canConnect get() = brokerUrl.isNotBlank() && clientId.isNotBlank() && !busy && !connected && !connecting
        val canUseConnection get() = connected && !busy
        val label get() = if (implementation == MqttImplementation.HIVE_MQ) {
            "${implementation.label} ${hiveMqProtocol.formatLabel()}"
        } else {
            implementation.label
        }

        private fun MqttProtocolVersion.formatLabel(): String = when (this) {
            MqttProtocolVersion.MQTT_3_1_1 -> "MQTT 3.1.1"
            MqttProtocolVersion.MQTT_5_0 -> "MQTT 5.0"
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var client: MqttClient? = null
    private var stateCollectionJob: Job? = null
    private var messageCollectionJob: Job? = null

    fun selectImplementation(implementation: MqttImplementation) {
        closeClient()
        _uiState.value = _uiState.value.copy(implementation = implementation)
        addLog("Selected ${implementation.label}")
    }

    fun selectHiveMqProtocol(protocol: MqttProtocolVersion) {
        _uiState.value = _uiState.value.copy(hiveMqProtocol = protocol)
        val label = when (protocol) {
            MqttProtocolVersion.MQTT_3_1_1 -> "MQTT 3.1.1"
            MqttProtocolVersion.MQTT_5_0 -> "MQTT 5.0"
        }
        addLog("Selected HiveMQ $label")
    }

    fun updateBrokerUrl(url: String) { _uiState.value = _uiState.value.copy(brokerUrl = url) }
    fun updateClientId(id: String) { _uiState.value = _uiState.value.copy(clientId = id) }
    fun updateUsername(u: String) { _uiState.value = _uiState.value.copy(username = u) }
    fun updatePassword(p: String) { _uiState.value = _uiState.value.copy(password = p) }
    fun updateTopic(t: String) { _uiState.value = _uiState.value.copy(topic = t) }
    fun updateMessage(m: String) { _uiState.value = _uiState.value.copy(message = m) }

    fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                val state = _uiState.value
                val broker = state.brokerUrl.trim()
                val mqttClient = createClient(
                    serverUri = broker,
                    clientId = state.clientId.trim(),
                )
                closeClient()
                client = mqttClient
                startCollecting(mqttClient)
                addLog("[${state.label}] Connecting to $broker")
                mqttClient.connect()
                addLog("[${state.label}] Connected")
            } catch (throwable: Throwable) {
                addLog("Connect failed: ${throwable.message.orEmpty()}")
                closeClient()
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                val activeClient = client
                val label = _uiState.value.label
                activeClient?.disconnect()
                activeClient?.close()
                client = null
                stopCollecting()
                _uiState.value = _uiState.value.copy(
                    connectionState = MqttConnectionState.Disconnected,
                )
                addLog("[$label] Disconnected")
            } catch (throwable: Throwable) {
                addLog("Disconnect failed: ${throwable.message.orEmpty()}")
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }

    fun subscribe() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                val state = _uiState.value
                val t = state.topic.trim()
                val activeClient = requireNotNull(client) { "No active MQTT client" }
                activeClient.subscribe(t, MqttQos.AT_LEAST_ONCE)
                addLog("[${state.label}] Subscribed to $t")
            } catch (throwable: Throwable) {
                addLog("Subscribe failed: ${throwable.message.orEmpty()}")
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }

    fun publish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                val state = _uiState.value
                val t = state.topic.trim()
                val payload = state.message.encodeToByteArray()
                val activeClient = requireNotNull(client) { "No active MQTT client" }
                activeClient.publish(t, payload, MqttQos.AT_LEAST_ONCE)
                addLog("[${state.label}] Published to $t: ${state.message}")
            } catch (throwable: Throwable) {
                addLog("Publish failed: ${throwable.message.orEmpty()}")
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }

    override fun onCleared() {
        closeClient()
    }

    private fun getSSLSocketFactory(context: Context): SSLSocketFactory? {
        try {
            // get the certificate
            val caInput: InputStream = context.resources.openRawResource(
                context.resources.getIdentifier(
                    "ca",
                    "raw",
                    context.packageName
                )
            )
            val cf = CertificateFactory.getInstance("X.509")
            val myCaCertificateObject = cf.generateCertificate(caInput)
            caInput.close()
            // initialise keystore with this cert
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", myCaCertificateObject)
            // trust the keystore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
            tmf.init(keyStore)
            // bind trust managers to the ssl context
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.getTrustManagers(), null)
            // extract the socket factory
            return sslContext.getSocketFactory()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun createClient(serverUri: String, clientId: String): MqttClient {
        val context = getApplication<Application>()
        val state = _uiState.value
        val sslSocketFactory: SSLSocketFactory? = getSSLSocketFactory(context)

        return when (state.implementation) {
            MqttImplementation.HIVE_MQ -> MqttClientFactory.create(
                context = context,
                config = MqttClientConfig(
                    serverUri = serverUri,
                    clientId = clientId,
                    protocolVersion = state.hiveMqProtocol,
                ),
                implementation = MqttClientImplementation.HIVE_MQ,
            )
            MqttImplementation.PAHO -> MqttClientFactory.create(
                context = context,
                config = MqttClientConfig(
                    serverUri = serverUri,
                    clientId = clientId,
                    options = MqttConnectionOptions(
                        sslSocketFactory = sslSocketFactory,
                        username = state.username,
                        password = state.password.toCharArray(),
                        cleanSession = true,
                    ),
                ),
                implementation = MqttClientImplementation.PAHO,
            )
        }
    }

    private fun startCollecting(mqttClient: MqttClient) {
        stopCollecting()
        stateCollectionJob = viewModelScope.launch {
            mqttClient.state.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
                addLog("[${_uiState.value.label}] State: ${state.formatText()}")
            }
        }
        messageCollectionJob = viewModelScope.launch {
            mqttClient.messages.collect { received ->
                addLog(
                    "[${_uiState.value.label}] Received ${received.topic}: " +
                        "${received.payload.decodeToString()} " +
                        "(qos=${received.qos.value}, retained=${received.retained})",
                )
            }
        }
    }

    private fun stopCollecting() {
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        messageCollectionJob?.cancel()
        messageCollectionJob = null
    }

    private fun closeClient() {
        stopCollecting()
        client?.close()
        client = null
        _uiState.value = _uiState.value.copy(
            connectionState = MqttConnectionState.Disconnected,
        )
    }

    private fun addLog(entry: String) {
        val current = _uiState.value.logEntries
        _uiState.value = _uiState.value.copy(logEntries = listOf(entry) + current.take(79))
    }

    private fun MqttConnectionState.formatText(): String = when (this) {
        MqttConnectionState.Closed -> "Closed"
        is MqttConnectionState.Connected -> if (reconnected) "Connected to $serverUri (reconnected)" else "Connected to $serverUri"
        MqttConnectionState.Connecting -> "Connecting"
        is MqttConnectionState.ConnectionLost -> "Connection lost: ${exception?.message.orEmpty()}"
        MqttConnectionState.Disconnected -> "Disconnected"
        MqttConnectionState.Disconnecting -> "Disconnecting"
        is MqttConnectionState.Error -> "Error: ${exception.message.orEmpty()}"
    }
}
