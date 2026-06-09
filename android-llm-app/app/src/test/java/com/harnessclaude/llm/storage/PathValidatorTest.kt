package com.harnessclaude.llm.storage

import com.harnessclaude.llm.model.ModelLoadError
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathValidatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newValidator(): Pair<PathValidator, File> {
        val root = tempFolder.newFolder("filesDir")
        return PathValidator(root) to root
    }

    @Test
    fun `path inside allowed root is accepted`() {
        val (validator, root) = newValidator()
        val modelFile =
            File(root, "models/llama.gguf").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf())
            }
        validator.requireSafe(modelFile.absolutePath)
    }

    @Test
    fun `sdcard path is rejected`() {
        val (validator, _) = newValidator()
        assertThrows(ModelLoadError.UnsafePath::class.java) {
            validator.requireSafe("/sdcard/Download/model.gguf")
        }
    }

    @Test
    fun `data local tmp is rejected`() {
        val (validator, _) = newValidator()
        assertThrows(ModelLoadError.UnsafePath::class.java) {
            validator.requireSafe("/data/local/tmp/model.gguf")
        }
    }

    @Test
    fun `system path is rejected`() {
        val (validator, _) = newValidator()
        assertThrows(ModelLoadError.UnsafePath::class.java) {
            validator.requireSafe("/system/bin/model.gguf")
        }
    }

    @Test
    fun `path traversal escaping root is rejected`() {
        val (validator, root) = newValidator()
        val parentEscape = File(root, "../escaped.gguf")
        assertThrows(ModelLoadError.UnsafePath::class.java) {
            validator.requireSafe(parentEscape.absolutePath)
        }
    }

    @Test
    fun `path with dot dot segments resolves canonically and is checked against root`() {
        val (validator, root) = newValidator()
        val nested =
            File(root, "models/../models/m.gguf").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf())
            }
        validator.requireSafe(nested.absolutePath)
    }

    @Test
    fun `path outside root but with prefix overlap is rejected`() {
        val (validator, root) = newValidator()
        val sibling = File(root.parentFile, "${root.name}-twin/m.gguf")
        assertThrows(ModelLoadError.UnsafePath::class.java) {
            validator.requireSafe(sibling.absolutePath)
        }
    }
}
