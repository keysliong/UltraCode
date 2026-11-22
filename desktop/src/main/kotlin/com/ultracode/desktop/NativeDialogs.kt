package com.ultracode.desktop

import org.lwjgl.system.MemoryStack
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NativeFileDialog.NFD_FreePath
import org.lwjgl.util.nfd.NativeFileDialog.NFD_Init
import org.lwjgl.util.nfd.NativeFileDialog.NFD_OKAY
import org.lwjgl.util.nfd.NativeFileDialog.NFD_OpenDialogMultiple
import org.lwjgl.util.nfd.NativeFileDialog.NFD_PathSet_Free
import org.lwjgl.util.nfd.NativeFileDialog.NFD_PathSet_FreePath
import org.lwjgl.util.nfd.NativeFileDialog.NFD_PathSet_GetCount
import org.lwjgl.util.nfd.NativeFileDialog.NFD_PathSet_GetPath
import org.lwjgl.util.nfd.NativeFileDialog.NFD_PickFolder
import org.lwjgl.util.nfd.NativeFileDialog.NFD_Quit
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Modern system file dialogs: Windows Explorer dialog (IFileDialog) and GTK on Linux via nativefiledialog;
 * on macOS the AWT dialog is already the native Finder panel.
 */
object NativeDialogs {
    private val useNfd = !LocalClaude.isMac

    fun pickImages(frame: Frame): List<File> {
        if (useNfd) runCatching { return nfdImages() }
        val d = FileDialog(frame, "Фото", FileDialog.LOAD).apply { isMultipleMode = true }
        d.isVisible = true
        return d.files.toList()
    }

    fun pickFolder(frame: Frame, start: File): File? {
        if (useNfd) runCatching { return nfdFolder(start) }
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val d = FileDialog(frame, "Рабочая папка", FileDialog.LOAD).apply { directory = start.absolutePath }
            d.isVisible = true
            return d.file?.let { File(d.directory, it) }
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }

    private fun nfdImages(): List<File> {
        NFD_Init()
        try {
            MemoryStack.stackPush().use { st ->
                val filters = NFDFilterItem.malloc(1, st)
                filters[0].name(st.UTF8("Изображения")).spec(st.UTF8("png,jpg,jpeg,webp,gif,bmp"))
                val out = st.mallocPointer(1)
                if (NFD_OpenDialogMultiple(out, filters, null as CharSequence?) != NFD_OKAY) return emptyList()
                val set = out.get(0)
                val count = st.mallocInt(1)
                NFD_PathSet_GetCount(set, count)
                val files = (0 until count.get(0)).mapNotNull { i ->
                    val p = st.mallocPointer(1)
                    NFD_PathSet_GetPath(set, i, p)
                    val path = p.getStringUTF8(0)
                    NFD_PathSet_FreePath(p.get(0))
                    File(path)
                }
                NFD_PathSet_Free(set)
                return files
            }
        } finally {
            NFD_Quit()
        }
    }

    private fun nfdFolder(start: File): File? {
        NFD_Init()
        try {
            MemoryStack.stackPush().use { st ->
                val out = st.mallocPointer(1)
                if (NFD_PickFolder(out, start.absolutePath) != NFD_OKAY) return null
                val path = out.getStringUTF8(0)
                NFD_FreePath(out.get(0))
                return File(path)
            }
        } finally {
            NFD_Quit()
        }
    }
}
