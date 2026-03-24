package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ServerSetupScreen(
    onContinueLegacy: (String) -> Unit,
    onScanPair: (String) -> Unit
) {
    var baseUrl by remember { mutableStateOf("https://pqnas-dev.pqnas-test.uk") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Connect to PQ-NAS")

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onScanPair(baseUrl) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan pairing QR")
        }

        Button(
            onClick = { onContinueLegacy(baseUrl) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Legacy QR login")
        }
    }
}