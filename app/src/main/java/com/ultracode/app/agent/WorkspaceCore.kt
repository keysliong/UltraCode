package com.ultracode.app.agent

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

