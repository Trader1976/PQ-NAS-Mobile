package com.pqnas.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pqnas.mobile.auth.PairQrParser
import com.pqnas.mobile.auth.PairQrPayload

@Composable
fun ScanPairQrScreen(
    onParsed: (PairQrPayload) -> Unit,
    onBack: () -> Unit
) {
    var status by remember { mutableStateOf("Ready to scan PQ-NAS pairing QR") }

    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            status = "Scan cancelled"
            return@rememberLauncherForActivityResult
        }

        val parsed = PairQrParser.parse(contents)
        if (parsed == null) {
            status = "Invalid PQ-NAS pairing QR"
            return@rememberLauncherForActivityResult
        }

        onParsed(parsed)
    }

    fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan PQ-NAS pairing QR")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        launcher.launch(options)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scan pairing QR")
        Text(status)

        Button(
            onClick = { startScan() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start QR scan")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}