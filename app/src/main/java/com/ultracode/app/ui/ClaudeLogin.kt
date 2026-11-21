package com.ultracode.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent

private const val CLAUDE_CODE_URL = "https://claude.ai/code"

/**
 * Opens claude.ai/code in a Chrome Custom Tab (the system browser shown on top of UltraCode).
 * Unlike an embedded WebView, Google / Apple sign-in work here, and the login is remembered.
 * You sign in on Anthropic's own site, so Claude Code works with your Pro/Max subscription.
 */
fun openClaudeCode(ctx: Context, toolbarColor: Int? = null) {
    val uri = Uri.parse(CLAUDE_CODE_URL)
    runCatching {
        val b = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        if (toolbarColor != null) {
            b.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        }
        val tab = b.build()
        // Force the browser: otherwise Android hands claude.ai links to the Claude app,
        // which opens a regular chat instead of Claude Code.
        val browser = CustomTabsClient.getPackageName(ctx, null)
            ?: ctx.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), 0)
                ?.activityInfo?.packageName?.takeIf { it != "android" }
        if (browser != null) tab.intent.setPackage(browser)
        tab.launchUrl(ctx, uri)
    }.onFailure {
        // No browser with Custom Tabs: open a plain browser window.
        runCatching {
            val i = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).let { probe ->
                ctx.packageManager.resolveActivity(probe, 0)?.activityInfo?.packageName
            }?.takeIf { it != "android" }?.let { i.setPackage(it) }
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { openUrl(ctx, CLAUDE_CODE_URL) }
    }
}
