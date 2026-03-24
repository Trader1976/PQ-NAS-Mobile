package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pair device")
        Text("Server: ${payload.origin}")
        Text("App: ${payload.appName}")

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device name") },
            modifier = Modifier.fillMaxWidth()
        )

        if (busy) {
            CircularProgressIndicator()
        }

        if (status.isNotBlank()) {
            Text(status)
        }

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    status = "Pairing..."
                    try {
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