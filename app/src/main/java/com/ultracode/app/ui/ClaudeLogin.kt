package com.ultracode.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
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
        b.build().launchUrl(ctx, uri)
    }.onFailure { openUrl(ctx, CLAUDE_CODE_URL) }
}
