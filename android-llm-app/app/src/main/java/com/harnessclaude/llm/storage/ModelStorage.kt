package com.harnessclaude.llm.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Copies model files chosen via the Storage Access Framework (SAF) into
 * `context.filesDir/models/`, the only location the loader's
 * [PathValidator] accepts.
 *
 * Streams the source [Uri] in 8 MiB chunks; never materialises the full
 * model in JVM heap. If the copy fails partway through, the partial
 * destination file is deleted so the next attempt starts clean
 * (FR-004-3 AC3).
 */
object ModelStorage {
    private const val MODELS_DIR = "models"
    private const val FALLBACK_FILENAME = "model.gguf"
    private const val BUFFER_BYTES = 8 * 1024 * 1024

    suspend fun copyModelFromUri(
        context: Context,
        uri: Uri,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val modelsDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
                val filename = resolveFilename(uri)
                val target = File(modelsDir, filename)
                val input =
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("ContentResolver returned null for $uri")
                try {
                    input.use { src ->
                        target.outputStream().use { dst ->
                            src.copyTo(dst, bufferSize = BUFFER_BYTES)
                        }
                    }
                    Timber.tag("ModelStorage").i(
                        "copied uri to %s bytes=%d",
                        target.absolutePath,
                        target.length(),
                    )
                    target.absolutePath
                    // must catch all to clean up partial file; rethrows immediately
                } catch (t: Throwable) {
                    if (target.exists() && !target.delete()) {
                        Timber.tag("ModelStorage").w(
                            "failed to clean up partial file %s",
                            target.absolutePath,
                        )
                    }
                    throw t
                }
            }
        }

    private fun resolveFilename(uri: Uri): String {
        val segment = uri.lastPathSegment ?: return FALLBACK_FILENAME
        return segment.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: FALLBACK_FILENAME
    }
}
