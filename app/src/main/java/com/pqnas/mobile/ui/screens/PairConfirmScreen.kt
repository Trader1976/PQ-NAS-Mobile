package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import kotlinx.coroutines.launch

@Composable
fun PairConfirmScreen(
    payload: PairQrPayload,
    configuredBaseUrl: String,
    authRepository: AuthRepository,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    var deviceName by remember { mutableStateOf("DNA-Nexus Android") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun normalizeOriginForCompare(value: String): String {
        return value.trim().trimEnd('/').lowercase()
    }

    val configuredOrigin = normalizeOriginForCompare(configuredBaseUrl)
    val qrOrigin = normalizeOriginForCompare(payload.origin)
    val originMismatch = configuredOrigin.isBlank() || configuredOrigin != qrOrigin
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cpunk_about),
                        contentDescription = "CPUNK DNA-Nexus mascot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = "Server: ${payload.origin}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (originMismatch) {
                    Text(
                        text = "Configured server: $configuredBaseUrl\nThis QR code points to a different server. Pairing is blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "App: ${payload.appName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "TLS identity: ${payload.tlsPinSha256.take(24)}…",
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
                            if (originMismatch) {
                                status = "Error: QR server does not match configured server."
                                return@launch
                            }

                            busy = true
                            status = "Pairing..."
                            try {
                                val ok = authRepository.consumePair(
                                    baseUrl = payload.origin,
                                    pairToken = payload.pairToken,
                                    tlsPinSha256 = payload.tlsPinSha256,
                                    deviceName = deviceName
                                )
                                if (ok) {
                                    onPaired()
                                } else {
                                    status = "Pairing failed"
                                }
                            } catch (_: javax.net.ssl.SSLException) {
                                status = "Error: Server identity check failed. Re-pair with a fresh QR code."
                            } catch (_: Exception) {
                                status = "Error: Pairing failed. Check the server address and QR code."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !originMismatch
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