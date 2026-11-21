@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultracode.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val CLAUDE_CODE_URL = "https://claude.ai/code"

/** Hosts in which navigation stays inside the app (Claude sign-in and the site itself). */
private fun isClaudeHost(host: String?): Boolean {
    val h = host ?: return false
    return h == "claude.ai" || h.endsWith(".claude.ai") || h.endsWith("anthropic.com") ||
        h.endsWith("accounts.google.com") || h.endsWith("appleid.apple.com") || h.endsWith("github.com")
}

/**
 * The official claude.ai website inside UltraCode. You sign in with your own Claude account
 * on Anthropic's site, so Claude Code works with your Pro/Max subscription.
 * The app does not read or store your login — it is only a browser window.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ClaudeWebScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("Claude") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uris = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            ?: r.data?.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } }
        fileCallback?.onReceiveValue(if (r.resultCode == Activity.RESULT_OK) uris else null)
        fileCallback = null
    }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        CookieBadge(size = 30.dp, container = MaterialTheme.colorScheme.tertiaryContainer) {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("Claude Code", style = MaterialTheme.typography.titleMedium)
                            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Обновить") }
                    IconButton(onClick = { openUrl(ctx, webView?.url ?: CLAUDE_CODE_URL) }) {
                        Icon(Icons.Outlined.OpenInBrowser, "Открыть в браузере")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.setSupportMultipleWindows(false)
                        // Plain mobile Chrome user agent, without the WebView marker.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme == "http" || uri.scheme == "https") {
                                    if (isClaudeHost(uri.host)) return false
                                    // Other links (docs, sites from answers) open in the normal browser.
                                    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    return true
                                }
                                runCatching { c.startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)) }
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 5 }
                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                CookieManager.getInstance().flush()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                            override fun onReceivedTitle(view: WebView, t: String?) { if (!t.isNullOrBlank()) title = t }
                            override fun onShowFileChooser(
                                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
                            ): Boolean {
                                fileCallback?.onReceiveValue(null)
                                fileCallback = callback
                                return runCatching {
                                    filePicker.launch(params.createIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
                                }.isSuccess.also { if (!it) { callback.onReceiveValue(null); fileCallback = null } }
                            }
                        }
                        loadUrl(CLAUDE_CODE_URL)
                        webView = this
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
            )
        }
    }
}
