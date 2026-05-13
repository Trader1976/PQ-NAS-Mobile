package com.pqnas.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pqnas.mobile.api.FileVersionItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.ScopedFilesOps

@Composable
fun FileVersionCompareDialog(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    relPath: String,
    displayName: String,
    version: FileVersionItemDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }

    var loading by remember(version.version_id, relPath, fileScope) { mutableStateOf(true) }
    var error by remember(version.version_id, relPath, fileScope) { mutableStateOf("") }
    var oldText by remember(version.version_id, relPath, fileScope) { mutableStateOf("") }
    var currentText by remember(version.version_id, relPath, fileScope) { mutableStateOf("") }
    var hideUnchanged by remember { mutableStateOf(true) }

    LaunchedEffect(version.version_id, relPath, fileScope) {
        loading = true
        error = ""

        try {
            val oldResp = scopedOps.readVersionText(
                scope = fileScope,
                path = relPath,
                versionId = version.version_id
            )
            val currentResp = scopedOps.readText(fileScope, relPath)

            oldText = oldResp.text
            currentText = currentResp.text.orEmpty()
        } catch (e: Exception) {
            error = friendlyCompareMessage(context, e)
        } finally {
            loading = false
        }
    }

    val diff = remember(oldText, currentText) {
        buildUnifiedDiff(oldText, currentText)
    }

    val rows = remember(diff, hideUnchanged) {
        if (hideUnchanged) compactUnchangedRows(diff.rows, contextLines = 3) else diff.rows
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Compare version",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = relPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Selected version → Current file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hideUnchanged,
                        onCheckedChange = { hideUnchanged = it }
                    )
                    Text(
                        text = "Hide unchanged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val summary = diffSummary(diff)
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                when {
                    loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    error.isNotBlank() -> {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(rows) { row ->
                                DiffLineRow(row)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(row: DiffLine) {
    val colorScheme = MaterialTheme.colorScheme

    val bg = when (row.kind) {
        DiffKind.Delete -> colorScheme.errorContainer.copy(alpha = 0.72f)
        DiffKind.Insert -> colorScheme.tertiaryContainer.copy(alpha = 0.78f)
        DiffKind.Skip -> colorScheme.surfaceVariant.copy(alpha = 0.72f)
        DiffKind.Equal -> Color.Transparent
    }

    val mark = when (row.kind) {
        DiffKind.Delete -> "-"
        DiffKind.Insert -> "+"
        DiffKind.Skip -> "⋯"
        DiffKind.Equal -> " "
    }

    val markColor = when (row.kind) {
        DiffKind.Delete -> colorScheme.error
        DiffKind.Insert -> colorScheme.tertiary
        else -> colorScheme.onSurfaceVariant
    }

    val lineNo = when (row.kind) {
        DiffKind.Delete -> row.oldNo?.toString().orEmpty()
        DiffKind.Insert -> row.newNo?.toString().orEmpty()
        DiffKind.Equal -> {
            val oldNo = row.oldNo?.toString().orEmpty()
            val newNo = row.newNo?.toString().orEmpty()
            if (oldNo == newNo) oldNo else "$oldNo:$newNo"
        }
        DiffKind.Skip -> ""
    }

    val text = if (row.kind == DiffKind.Skip) {
        "${row.skipCount} unchanged line(s) hidden"
    } else {
        row.text.ifEmpty { " " }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = lineNo,
            modifier = Modifier
                .width(44.dp)
                .padding(end = 4.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = colorScheme.onSurfaceVariant
        )

        Text(
            text = mark,
            modifier = Modifier.width(18.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = markColor
        )

        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = colorScheme.onBackground,
            softWrap = true
        )
    }
}

private enum class DiffKind {
    Equal,
    Delete,
    Insert,
    Skip
}

private data class DiffLine(
    val kind: DiffKind,
    val oldNo: Int? = null,
    val newNo: Int? = null,
    val text: String = "",
    val skipCount: Int = 0
)

private data class DiffResult(
    val rows: List<DiffLine>,
    val added: Int,
    val removed: Int,
    val fallback: Boolean = false
)

private fun buildUnifiedDiff(oldText: String, newText: String): DiffResult {
    val oldLines = oldText.split("\n")
    val newLines = newText.split("\n")

    val m = oldLines.size
    val n = newLines.size

    if (m.toLong() * n.toLong() > 1_000_000L) {
        return buildPositionFallback(oldLines, newLines)
    }

    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in m - 1 downTo 0) {
        for (j in n - 1 downTo 0) {
            dp[i][j] = if (oldLines[i] == newLines[j]) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
    }

    val rows = mutableListOf<DiffLine>()
    var added = 0
    var removed = 0
    var i = 0
    var j = 0

    while (i < m && j < n) {
        if (oldLines[i] == newLines[j]) {
            rows += DiffLine(
                kind = DiffKind.Equal,
                oldNo = i + 1,
                newNo = j + 1,
                text = oldLines[i]
            )
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            rows += DiffLine(
                kind = DiffKind.Delete,
                oldNo = i + 1,
                text = oldLines[i]
            )
            removed++
            i++
        } else {
            rows += DiffLine(
                kind = DiffKind.Insert,
                newNo = j + 1,
                text = newLines[j]
            )
            added++
            j++
        }
    }

    while (i < m) {
        rows += DiffLine(
            kind = DiffKind.Delete,
            oldNo = i + 1,
            text = oldLines[i]
        )
        removed++
        i++
    }

    while (j < n) {
        rows += DiffLine(
            kind = DiffKind.Insert,
            newNo = j + 1,
            text = newLines[j]
        )
        added++
        j++
    }

    return DiffResult(rows = rows, added = added, removed = removed)
}

private fun buildPositionFallback(oldLines: List<String>, newLines: List<String>): DiffResult {
    val rows = mutableListOf<DiffLine>()
    var added = 0
    var removed = 0
    val max = maxOf(oldLines.size, newLines.size)

    for (i in 0 until max) {
        val hasOld = i < oldLines.size
        val hasNew = i < newLines.size

        if (hasOld && hasNew && oldLines[i] == newLines[i]) {
            rows += DiffLine(
                kind = DiffKind.Equal,
                oldNo = i + 1,
                newNo = i + 1,
                text = oldLines[i]
            )
        } else {
            if (hasOld) {
                rows += DiffLine(
                    kind = DiffKind.Delete,
                    oldNo = i + 1,
                    text = oldLines[i]
                )
                removed++
            }
            if (hasNew) {
                rows += DiffLine(
                    kind = DiffKind.Insert,
                    newNo = i + 1,
                    text = newLines[i]
                )
                added++
            }
        }
    }

    return DiffResult(rows = rows, added = added, removed = removed, fallback = true)
}

private fun compactUnchangedRows(rows: List<DiffLine>, contextLines: Int): List<DiffLine> {
    if (rows.isEmpty()) return rows

    val keep = mutableSetOf<Int>()

    rows.forEachIndexed { index, row ->
        if (row.kind != DiffKind.Equal && row.kind != DiffKind.Skip) {
            val from = maxOf(0, index - contextLines)
            val to = minOf(rows.lastIndex, index + contextLines)
            for (i in from..to) keep += i
        }
    }

    if (keep.isEmpty()) return rows

    val out = mutableListOf<DiffLine>()
    var i = 0

    while (i < rows.size) {
        if (i in keep) {
            out += rows[i]
            i++
            continue
        }

        val start = i
        while (i < rows.size && i !in keep) {
            i++
        }

        out += DiffLine(
            kind = DiffKind.Skip,
            skipCount = i - start
        )
    }

    return out
}

private fun diffSummary(diff: DiffResult): String {
    if (diff.added == 0 && diff.removed == 0) {
        return "No line differences found."
    }

    val parts = mutableListOf<String>()
    if (diff.added > 0) parts += "+${diff.added} added"
    if (diff.removed > 0) parts += "-${diff.removed} removed"
    if (diff.fallback) parts += "large-file fallback"

    return parts.joinToString(" • ")
}

private fun friendlyCompareMessage(context: Context, error: Throwable): String {
    val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
    return "Compare failed: $msg"
}
