package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.pqnas.mobile.auth.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun QrLoginScreen(
    baseUrl: String,
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Starting session...") }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    LaunchedEffect(baseUrl) {
        scope.launch {
            try {
                val session = authRepository.startSession(baseUrl)
                qrUrl = baseUrl.trimEnd('/') + session.qr_svg
                statusText = "Waiting for scan..."

                val approved = authRepository.waitForApproval(baseUrl, session.k)
                if (!approved) {
                    statusText = "Approval failed or timed out"
                    return@launch
                }

                statusText = "Approved, finishing login..."
                val ok = authRepository.consumeApp(baseUrl, session.k)
                if (ok) {
                    onLoggedIn()
                } else {
                    statusText = "Login failed"
                }
            } catch (e: Exception) {
                statusText = "Error: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scan this QR with DNA Messenger")

        if (qrUrl != null) {
            AsyncImage(
                model = qrUrl,
                imageLoader = imageLoader,
                contentDescription = "Login QR",
                modifier = Modifier.size(280.dp)
            )
        } else {
            CircularProgressIndicator()
        }

        Text(statusText)
    }
}