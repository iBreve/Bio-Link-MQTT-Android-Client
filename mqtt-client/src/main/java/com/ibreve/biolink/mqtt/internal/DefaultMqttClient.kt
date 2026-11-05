package com.ibreve.biolink.mqtt.internal

import com.ibreve.biolink.mqtt.MqttClient
import com.ibreve.biolink.mqtt.MqttClientConfig
import com.ibreve.biolink.mqtt.MqttClientException
import com.ibreve.biolink.mqtt.MqttConnectionState
import com.ibreve.biolink.mqtt.MqttQos
import com.ibreve.biolink.mqtt.MqttReceivedMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DefaultMqttClient(
    private val config: MqttClientConfig,
    private val engine: MqttEngine,
) : MqttClient, MqttEngineListener {
    private val closed = AtomicBoolean(false)
    private val _state = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Disconnected)
    private val _messages = MutableSharedFlow<MqttReceivedMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val state: StateFlow<MqttConnectionState> = _state.asStateFlow()
    override val messages: SharedFlow<MqttReceivedMessage> = _messages.asSharedFlow()

    init {
        validateConfig(config)
        engine.setListener(this)
    }

    override suspend fun connect() {
        ensureOpen()
        if (engine.isConnected) {
            _state.value = MqttConnectionState.Connected(config.serverUri)
            return
        }

        runOperation("connect") {
            _state.value = MqttConnectionState.Connecting
            engine.connect(config.options)
            _state.value = MqttConnectionState.Connected(config.serverUri)
        }
    }

    override suspend fun disconnect(quiesceTimeoutMillis: Long) {
        ensureOpen()
        if (quiesceTimeoutMillis < 0) {
            throw MqttClientException("MQTT quiesceTimeoutMillis must not be negative")
        }

        runOperation("disconnect") {
            _state.value = MqttConnectionState.Disconnecting
            engine.disconnect(quiesceTimeoutMillis)
            _state.value = MqttConnectionState.Disconnected
        }
    }

    override suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: MqttQos,
        retained: Boolean,
    ) {
        ensureOpen()
        validatePublishTopic(topic)
        runOperation("publish") {
            engine.publish(topic, payload, qos, retained)
        }
    }

    override suspend fun subscribe(topic: String, qos: MqttQos) {
        ensureOpen()
        validateSubscriptionTopic(topic)
        runOperation("subscribe") {
            engine.subscribe(topic, qos)
        }
    }

    override suspend fun unsubscribe(topic: String) {
        ensureOpen()
        validateSubscriptionTopic(topic)
        runOperation("unsubscribe") {
            engine.unsubscribe(topic)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                engine.close()
                _state.value = MqttConnectionState.Closed
            } catch (throwable: Throwable) {
                val exception = throwable.asMqttClientException("close")
                _state.value = MqttConnectionState.Error(exception)
                throw exception
            }
        }
    }

    override fun onConnected(serverUri: String, reconnected: Boolean) {
        _state.value = MqttConnectionState.Connected(serverUri, reconnected)
    }

    override fun onConnectionLost(cause: Throwable?) {
        _state.value = MqttConnectionState.ConnectionLost(
            exception = cause?.asMqttClientException("connection"),
        )
    }

    override fun onMessageArrived(message: MqttReceivedMessage) {
        _messages.tryEmit(message)
    }

    private suspend fun runOperation(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val exception = throwable.asMqttClientException(operation)
            _state.value = MqttConnectionState.Error(exception)
            throw exception
        }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw MqttClientException("MQTT client is closed")
        }
    }
}
