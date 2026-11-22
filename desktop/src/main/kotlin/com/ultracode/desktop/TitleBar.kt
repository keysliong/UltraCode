package com.ultracode.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Colors the native window title bar to match the app theme (Windows 10/11, DWM API).
 * The window keeps native behavior: dragging, snapping, resizing.
 */
object TitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: Pointer, size: Int): Int
    }

    private val dwm: Dwm? by lazy {
        if (!LocalClaude.isWindows) null else runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }

    private fun colorRef(c: Color): Int {
        val argb = c.toArgb()
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r // COLORREF = 0x00BBGGRR
    }

    private fun set(hwnd: WinDef.HWND, attr: Int, value: Int) {
        val m = Memory(4); m.setInt(0, value)
        dwm?.DwmSetWindowAttribute(hwnd, attr, m, 4)
    }

    fun apply(window: Window, dark: Boolean, background: Color, text: Color) {
        if (dwm == null) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            set(hwnd, 20, if (dark) 1 else 0)   // DWMWA_USE_IMMERSIVE_DARK_MODE (Win10 20H1+ / Win11)
            set(hwnd, 19, if (dark) 1 else 0)   // same attribute on older Windows 10
            set(hwnd, 35, colorRef(background)) // DWMWA_CAPTION_COLOR (Windows 11)
            set(hwnd, 36, colorRef(text))       // DWMWA_TEXT_COLOR (Windows 11)
            set(hwnd, 34, colorRef(background)) // DWMWA_BORDER_COLOR (Windows 11)
        }
    }
}
