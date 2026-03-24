package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    filesRepository: FilesRepository,
    onLogout: (() -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    val scope = rememberCoroutineScope()

    fun load(path: String?) {
        scope.launch {
            status = "Loading..."
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

    fun parentPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return parts.dropLast(1).joinToString("/").ifBlank { null }
    }

    LaunchedEffect(Unit) {
        load(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "PQ-NAS Files",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Path: ${currentPath ?: "/"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                status.startsWith("Error:") -> MaterialTheme.colorScheme.error
                status == "OK" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { load(parentPath(currentPath)) },
                enabled = currentPath != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Up")
            }

            Button(
                onClick = { load(currentPath) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh")
            }

            if (onLogout != null) {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Logout")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No files here",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "This folder is empty or could not be loaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(items) { item ->
                        FileRow(
                            item = item,
                            onOpen = {
                                if (item.type == "dir") {
                                    val next = listOfNotNull(currentPath, item.name)
                                        .joinToString("/")
                                    load(next)
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    item: FileItemDto,
    onOpen: () -> Unit
) {
    val isDir = item.type == "dir"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDir, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (isDir) "Directory" else "File • ${item.size_bytes} bytes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isDir) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}