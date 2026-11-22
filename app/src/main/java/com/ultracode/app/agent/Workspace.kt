package com.ultracode.app.agent

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

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

    override fun list(path: String): List<String> {
        val dir = find(segments(path)) ?: return emptyList()
        return dir.listFiles()
            .sortedWith(compareBy({ !it.isDirectory }, { it.name ?: "" }))
            .map { if (it.isDirectory) "${it.name}/" else it.name ?: "?" }
    }

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
