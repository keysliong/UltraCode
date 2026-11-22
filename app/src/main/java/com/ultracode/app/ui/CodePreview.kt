package com.ultracode.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ultracode.app.AppViewModel

/** Buttons under code blocks: run Python (output below) and open HTML right in the chat. */
@Composable
fun PhoneCodeActions(vm: AppViewModel, lang: String, code: String) {
    val py = isPythonLang(lang)
    val html = isHtmlLang(lang)
    if (!py && !html) return
    var open by rememberSaveable(code) { mutableStateOf(false) }
    var full by rememberSaveable(code) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (py) {
                val running = code in vm.codeRunning
                Button(onClick = { vm.runCodeBlock(code) }, enabled = !running, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(if (running) "Выполняется…" else "Запустить")
                }
            }
            if (html) {
                Button(onClick = { open = !open }, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(if (open) "Скрыть сайт" else "Открыть сайт")
                }
                OutlinedButton(onClick = { full = true }, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Fullscreen, "На весь экран", Modifier.size(18.dp))
                }
            }
        }
        if (py && code in vm.codeRunning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.codeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        vm.codeRuns[code]?.let { out ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Вывод", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer { Text(out.trimEnd(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (html && open) HtmlPreview(code, Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(16.dp)))
    }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Просмотр сайта", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { full = false }) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                HtmlPreview(code, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** HTML page rendered right in the app (scripts and CDN links work). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreview(html: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                // Let the page scroll itself instead of the chat list.
                setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                loadDataWithBaseURL("https://preview.ultracode.local/", html, "text/html", "utf-8", null)
            }
        },
    )
}
