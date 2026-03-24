package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import kotlinx.coroutines.launch

@Composable
fun PairConfirmScreen(
    payload: PairQrPayload,
    authRepository: AuthRepository,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    var deviceName by remember { mutableStateOf("PQ-NAS Android") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pair device",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Server: ${payload.origin}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "App: ${payload.appName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Device name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (busy) {
                    CircularProgressIndicator()
                }

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Pairing..."
                            try {
                                android.util.Log.d("PQNAS_PAIR", "consumePair origin=${payload.origin} token=${payload.pairToken.take(12)}...")
                                val ok = authRepository.consumePair(
                                    baseUrl = payload.origin,
                                    pairToken = payload.pairToken,
                                    deviceName = deviceName
                                )
                                if (ok) {
                                    onPaired()
                                } else {
                                    status = "Pairing failed"
                                }
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text("Pair this device")
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text("Back")
                }
            }
        }
    }
}