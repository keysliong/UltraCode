package com.ultracode.app.cc

import android.system.Os
import java.io.File
import java.io.InputStream

/** Minimal tar reader (ustar + GNU long names + pax): files, dirs, symlinks, hardlinks. */
object Tar {
    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        var r = 0L
        for (i in off until off + len) {
            val c = b[i].toInt() and 0xff
            if (c == 0 || c == ' '.code) { if (r > 0) break else continue }
            if (c < '0'.code || c > '7'.code) break
            r = r * 8 + (c - '0'.code)
        }
        return r
    }

    private fun str(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int = buf.size): Boolean {
        var n = 0
        while (n < len) {
            val r = input.read(buf, n, len - n)
            if (r < 0) return false
            n += r
        }
        return true
    }

    private fun skip(input: InputStream, count: Long) {
        var left = count
        val tmp = ByteArray(65536)
        while (left > 0) {
            val r = input.read(tmp, 0, minOf(tmp.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    /**
     * Extracts [input] into [dest]. If [only] is set, extracts just that entry to [onlyTarget].
     * [stripPrefix] removes a leading path ("./" is always removed).
     */
    fun extract(
        input: InputStream,
        dest: File,
        only: String? = null,
        onlyTarget: File? = null,
        onEntry: (String) -> Unit = {},
    ) {
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null
        var paxSize: Long? = null
        val hardlinks = mutableListOf<Pair<File, String>>()
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            val type = header[156].toInt().toChar()
            var size = paxSize ?: octal(header, 124, 12)
            val mode = octal(header, 100, 8).toInt()
            val prefix = str(header, 345, 155)
            var name = paxPath ?: longName ?: str(header, 0, 100).let { if (prefix.isNotEmpty() && str(header, 257, 5) == "ustar") "$prefix/$it" else it }
            val link = paxLink ?: longLink ?: str(header, 157, 100)
            longName = null; longLink = null; paxPath = null; paxLink = null; paxSize = null
            val padded = (size + 511) / 512 * 512
            when (type) {
                'L', 'K' -> {
                    val data = ByteArray(size.toInt()); readFully(input, data); skip(input, padded - size)
                    val v = String(data, Charsets.UTF_8).trimEnd('\u0000')
                    if (type == 'L') longName = v else longLink = v
                    continue
                }
                'x' -> {
                    val data = ByteArray(size.toInt()); readFully(input, data); skip(input, padded - size)
                    String(data, Charsets.UTF_8).split('\n').forEach { rec ->
                        val kv = rec.substringAfter(' ', "")
                        when {
                            kv.startsWith("path=") -> paxPath = kv.removePrefix("path=")
                            kv.startsWith("linkpath=") -> paxLink = kv.removePrefix("linkpath=")
                            kv.startsWith("size=") -> paxSize = kv.removePrefix("size=").toLongOrNull()
                        }
                    }
                    continue
                }
                'g' -> { skip(input, padded); continue }
            }
            name = name.removePrefix("./").trimStart('/')
            if (only != null) {
                if (name == only && (type == '0' || type == '\u0000')) {
                    val target = onlyTarget ?: File(dest, name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> copy(input, out, size) }
                    skip(input, padded - size)
                    runCatching { Os.chmod(target.absolutePath, mode and 0x1ff or 0x1c0) }
                    return
                }
                skip(input, padded); continue
            }
            if (name.isEmpty() || name.split('/').any { it == ".." }) { skip(input, padded); continue }
            val f = File(dest, name)
            onEntry(name)
            when (type) {
                '5' -> { f.mkdirs(); runCatching { Os.chmod(f.absolutePath, (mode and 0xfff).takeIf { it != 0 } ?: 0x1ed) } }
                '2' -> {
                    f.parentFile?.mkdirs()
                    if (f.exists() || runCatching { Os.lstat(f.absolutePath) }.isSuccess) f.delete()
                    runCatching { Os.symlink(link, f.absolutePath) }
                    skip(input, padded)
                }
                '1' -> { hardlinks += f to link.removePrefix("./"); skip(input, padded) }
                '0', '\u0000', '7' -> {
                    f.parentFile?.mkdirs()
                    if (runCatching { Os.lstat(f.absolutePath) }.isSuccess) f.delete()
                    f.outputStream().use { out -> copy(input, out, size) }
                    skip(input, padded - size)
                    runCatching { Os.chmod(f.absolutePath, (mode and 0xfff).takeIf { it != 0 } ?: 0x1a4) }
                }
                else -> skip(input, padded)
            }
        }
        for ((f, target) in hardlinks) {
            val src = File(dest, target)
            runCatching { f.parentFile?.mkdirs(); src.copyTo(f, overwrite = true); Os.chmod(f.absolutePath, Os.stat(src.absolutePath).st_mode and 0xfff) }
        }
    }

    private fun copy(input: InputStream, out: java.io.OutputStream, size: Long) {
        val buf = ByteArray(1 shl 16)
        var left = size
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            out.write(buf, 0, r)
            left -= r
        }
    }
}
