package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.files.FileScope

data class WorkspaceScopeOption(
    val workspaceId: String,
    val label: String,
    val role: String
)

@Composable
fun FilesScopeSection(
    currentScope: FileScope,
    workspaces: List<WorkspaceScopeOption>,
    onSelectUserScope: () -> Unit,
    onSelectWorkspaceScope: (WorkspaceScopeOption) -> Unit
) {
    val currentLabel = when (currentScope) {
        FileScope.User -> "Current scope: My files"
        is FileScope.Workspace -> {
            val name = currentScope.workspaceName.ifBlank { currentScope.workspaceId }
            "Current scope: Workspace • $name"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = currentScope == FileScope.User,
                        onClick = onSelectUserScope,
                        label = { Text("My files") }
                    )
                }

                items(workspaces, key = { it.workspaceId }) { ws ->
                    val selected =
                        currentScope is FileScope.Workspace &&
                                currentScope.workspaceId == ws.workspaceId

                    FilterChip(
                        selected = selected,
                        onClick = { onSelectWorkspaceScope(ws) },
                        label = {
                            val suffix = when (ws.role.lowercase()) {
                                "owner" -> " • owner"
                                "editor" -> " • editor"
                                "viewer" -> " • viewer"
                                else -> ""
                            }
                            Text(ws.label + suffix)
                        }
                    )
                }
            }

            if (workspaces.isEmpty()) {
                Text(
                    text = "No workspaces available for this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}