package com.harnessclaude.llm.storage

import com.harnessclaude.llm.model.ModelLoadError
import java.io.File

/**
 * Enforces the path-safety rules defined in ADR-004 §6.1:
 *
 *  1. Reject deny-listed system prefixes outright.
 *  2. Canonicalise the path (resolves `..`, symlinks) to defeat traversal
 *     attacks.
 *  3. Require the canonical path to live under [allowedRoot], typically
 *     `context.filesDir`.
 *
 * Stateless and safe to share between threads.
 */
class PathValidator(allowedRoot: File) {
    private val allowedRootCanonical: String = allowedRoot.canonicalPath

    private val deniedPrefixes: List<String> =
        listOf(
            "/sdcard/",
            "/data/local/tmp/",
            "/system/",
        )

    /**
     * @throws ModelLoadError.UnsafePath if [path] is rejected.
     */
    fun requireSafe(path: String) {
        val canonical = File(path).canonicalPath
        if (deniedPrefixes.any { canonical.startsWith(it) }) {
            throw ModelLoadError.UnsafePath(canonical)
        }
        // Anchor with File.separator so /tmp/filesDir-twin is not accepted
        // when allowedRoot is /tmp/filesDir.
        val anchoredRoot = allowedRootCanonical.trimEnd(File.separatorChar) + File.separatorChar
        if (canonical != allowedRootCanonical && !canonical.startsWith(anchoredRoot)) {
            throw ModelLoadError.UnsafePath(canonical)
        }
    }
}
