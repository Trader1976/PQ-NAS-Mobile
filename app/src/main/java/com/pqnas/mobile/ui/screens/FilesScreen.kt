package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    filesRepository: FilesRepository
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    val scope = rememberCoroutineScope()

    fun load(path: String?) {
        scope.launch {
            try {
                val resp = filesRepository.list(path)
                items = resp.items
                currentPath = if (resp.path.isBlank()) null else resp.path
                status = "OK"
            } catch (e: Exception) {
                status = "Error: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        load(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Path: ${currentPath ?: "/"}")
        Spacer(Modifier.height(8.dp))
        Text(status)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.type == "dir") {
                                val next = listOfNotNull(currentPath, item.name).joinToString("/")
                                load(next)
                            }
                        }
                        .padding(12.dp)
                ) {
                    Text("${item.type.uppercase()}: ${item.name}")
                }
            }
        }
    }
}