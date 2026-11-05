package com.example.mqttclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibreve.biolink.mqtt.MqttConnectionState
import com.ibreve.biolink.mqtt.MqttProtocolVersion
import com.example.mqttclient.ui.theme.MQTTClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MQTTClientTheme {
                MqttSampleScreen()
            }
        }
    }
}

@Composable
fun MqttSampleScreen(
    viewModel: MqttSampleViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MQTT Client",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "${state.label}: ${state.connectionState.displayText()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Implementation",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MqttSampleViewModel.MqttImplementation.entries.forEach { implementation ->
                    if (state.implementation == implementation) {
                        Button(
                            onClick = {},
                            enabled = state.canSwitchImplementation,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(implementation.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.selectImplementation(implementation) },
                            enabled = state.canSwitchImplementation,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(implementation.label)
                        }
                    }
                }
            }

            if (state.implementation == MqttSampleViewModel.MqttImplementation.HIVE_MQ) {
                Text(
                    text = "MQTT Version",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MqttProtocolVersion.entries.forEach { protocolVersion ->
                        if (state.hiveMqProtocol == protocolVersion) {
                            Button(
                                onClick = {},
                                enabled = state.canSwitchImplementation,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(protocolVersion.displayLabel())
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.selectHiveMqProtocol(protocolVersion) },
                                enabled = state.canSwitchImplementation,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(protocolVersion.displayLabel())
                            }
                        }
                    }
                }
            } else if (state.implementation == MqttSampleViewModel.MqttImplementation.PAHO) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { viewModel.updateUsername(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !state.connected && !state.busy,
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.connected && !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                )
            }

            OutlinedTextField(
                value = state.brokerUrl,
                onValueChange = { viewModel.updateBrokerUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Broker URL") },
                placeholder = { Text("tcp://broker.hivemq.com:1883") },
                singleLine = true,
                enabled = !state.connected && !state.busy,
            )

            OutlinedTextField(
                value = state.clientId,
                onValueChange = { viewModel.updateClientId(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Client ID") },
                singleLine = true,
                enabled = !state.connected && !state.busy,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.connect() },
                    enabled = state.canConnect,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Connect")
                }

                Button(
                    onClick = { viewModel.disconnect() },
                    enabled = state.canUseConnection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Disconnect")
                }
            }

            OutlinedTextField(
                value = state.topic,
                onValueChange = { viewModel.updateTopic(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Topic") },
                singleLine = true,
            )

            Button(
                onClick = { viewModel.subscribe() },
                enabled = state.canUseConnection && state.topic.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Subscribe")
            }

            OutlinedTextField(
                value = state.message,
                onValueChange = { viewModel.updateMessage(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                minLines = 3,
            )

            Button(
                onClick = { viewModel.publish() },
                enabled = state.canUseConnection && state.topic.isNotBlank() && state.message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Publish")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Log",
                style = MaterialTheme.typography.titleMedium,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.logEntries.isEmpty()) {
                        Text(
                            text = "No events",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        state.logEntries.forEach { entry ->
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MqttProtocolVersion.displayLabel(): String = when (this) {
    MqttProtocolVersion.MQTT_3_1_1 -> "MQTT 3.1.1"
    MqttProtocolVersion.MQTT_5_0 -> "MQTT 5.0"
}

private fun MqttConnectionState.displayText(): String = when (this) {
    MqttConnectionState.Closed -> "Closed"
    is MqttConnectionState.Connected -> if (reconnected) "Connected to $serverUri (reconnected)" else "Connected to $serverUri"
    MqttConnectionState.Connecting -> "Connecting"
    is MqttConnectionState.ConnectionLost -> "Connection lost: ${exception?.message.orEmpty()}"
    MqttConnectionState.Disconnected -> "Disconnected"
    MqttConnectionState.Disconnecting -> "Disconnecting"
    is MqttConnectionState.Error -> "Error: ${exception.message.orEmpty()}"
}
