package com.pqnas.mobile.files

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

fun stageUriToTempFile(
    context: Context,
    uri: Uri,
    fileNameHint: String? = null
): File {
    val suffix = fileNameHint
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        ?: ".bin"

    val tempFile = File.createTempFile("pqnas_upload_", suffix, context.cacheDir)

    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
            output.flush()
        }
    } ?: throw IllegalStateException("Could not open input stream")

    return tempFile
}

fun tempFileRequestBody(
    file: File,
    mimeType: String? = null,
    onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
): RequestBody {
    return object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val total = file.length()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var uploaded = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, total)
                }

                sink.flush()
            }
        }
    }
}