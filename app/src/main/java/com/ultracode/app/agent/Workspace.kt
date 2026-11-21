package com.ultracode.app.agent

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** A folder the agent can work in: the app's own folder or a folder chosen by the user. */
interface Workspace {
    val label: String
    fun list(path: String): List<String>
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray)
    fun delete(path: String): Boolean
    /** All files (relative paths) under [path]. */
    fun walk(path: String = ""): List<String>

    fun read(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)
    fun write(path: String, text: String) = writeBytes(path, text.toByteArray())
}

internal val SKIP_DIRS = setOf(".git", "build", ".gradle", "node_modules", ".idea", "__pycache__", ".kotlin", "dist")

internal fun segments(path: String): List<String> {
    val segs = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
    require(segs.none { it == ".." }) { "Путь не может содержать '..'" }
    return segs
}

class FileWorkspace(private val root: File) : Workspace {
    init { root.mkdirs() }
    override val label = "Папка приложения"
    private fun f(path: String) = segments(path).fold(root) { acc, s -> File(acc, s) }

    override fun list(path: String): List<String> =
        f(path).listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?.map { if (it.isDirectory) it.name + "/" else it.name } ?: emptyList()

    override fun readBytes(path: String): ByteArray? = f(path).takeIf { it.isFile }?.readBytes()

    override fun writeBytes(path: String, bytes: ByteArray) {
        val file = f(path); file.parentFile?.mkdirs(); file.writeBytes(bytes)
    }

    override fun delete(path: String) = f(path).let { if (it.isDirectory) it.deleteRecursively() else it.delete() }

    override fun walk(path: String): List<String> {
        val base = f(path)
        val out = mutableListOf<String>()
        fun rec(d: File, prefix: String) {
            d.listFiles()?.forEach {
                if (it.isDirectory) { if (it.name !in SKIP_DIRS) rec(it, "$prefix${it.name}/") }
                else out += prefix + it.name
            }
        }
        if (base.isDirectory) rec(base, "")
        return out
    }
}

class SafWorkspace(private val ctx: Context, uri: Uri, name: String?) : Workspace {
    private val root = DocumentFile.fromTreeUri(ctx, uri) ?: error("Нет доступа к папке")
    override val label = name ?: root.name ?: "Выбранная папка"

    private fun find(segs: List<String>): DocumentFile? {
        var cur = root
        for (s in segs) cur = cur.findFile(s) ?: return null
        return cur
    }

    private fun dir(segs: List<String>): DocumentFile {
        var cur = root
        for (s in segs) {
            val next = cur.findFile(s)
            cur = if (next != null && next.isDirectory) next else cur.createDirectory(s) ?: error("Не удалось создать папку $s")
        }
        return cur
    }

    override fun list(path: String): List<String> =
        (find(segments(path)) ?: return emptyList()).listFiles()
            .sortedWith(compareBy({ !it.isDirectory }, { it.name ?: "" }))
            .map { if (it.isDirectory) "${it.name}/" else it.name ?: "?" }

    override fun readBytes(path: String): ByteArray? {
        val f = find(segments(path))?.takeIf { it.isFile } ?: return null
        return ctx.contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        val segs = segments(path)
        require(segs.isNotEmpty()) { "Пустой путь" }
        val parent = dir(segs.dropLast(1))
        val f = parent.findFile(segs.last()) ?: parent.createFile("application/octet-stream", segs.last())
            ?: error("Не удалось создать файл $path")
        val out = runCatching { ctx.contentResolver.openOutputStream(f.uri, "wt") }.getOrNull()
            ?: ctx.contentResolver.openOutputStream(f.uri, "w") ?: error("Не удалось открыть $path")
        out.use { it.write(bytes) }
    }

    override fun delete(path: String) = find(segments(path))?.delete() ?: false

    override fun walk(path: String): List<String> {
        val base = find(segments(path)) ?: return emptyList()
        val out = mutableListOf<String>()
        fun rec(d: DocumentFile, prefix: String) {
            d.listFiles().forEach {
                val n = it.name ?: return@forEach
                if (it.isDirectory) { if (n !in SKIP_DIRS) rec(it, "$prefix$n/") } else out += prefix + n
            }
        }
        rec(base, "")
        return out
    }
}
