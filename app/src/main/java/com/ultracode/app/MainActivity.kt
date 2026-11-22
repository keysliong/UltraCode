package com.ultracode.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ultracode.app.ui.ChatScreen
import com.ultracode.app.ui.ReposScreen
import com.ultracode.app.ui.SettingsScreen
import com.ultracode.app.ui.UltraTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            UltraTheme(vm.settings.themeMode, vm.settings.dynamicColor) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.ultracode.app.ui.LocalCodeActions provides { lang, code -> com.ultracode.app.ui.PhoneCodeActions(vm, lang, code) },
                ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by rememberSaveable { mutableStateOf("chat") }
                    BackHandler(enabled = screen != "chat") { screen = "chat" }
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut() },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            "settings" -> SettingsScreen(vm, onBack = { screen = "chat" }, onClaude = { openClaude() })
                            "repos" -> ReposScreen(vm, onBack = { screen = "chat" })
                            "terminal" -> {
                                androidx.compose.runtime.LaunchedEffect(Unit) { vm.openTerminal() }
                                com.ultracode.app.term.TerminalView(
                                    session = vm.terminal, title = "Linux · /workspace (папка проектов)", status = vm.terminalStatus,
                                    quick = listOf("python", "python main.py", "pip install requests", "ls", "cat ", "git status"),
                                    onBack = { screen = "chat" },
                                    modifier = Modifier.systemBarsPadding().imePadding(),
                                )
                            }
                            else -> ChatScreen(vm, onSettings = { screen = "settings" }, onRepos = { screen = "repos" }, onClaude = { openClaude() }, onTerminal = { screen = "terminal" })
                        }
                    }
                }
                }
            }
        }
    }

    private fun openClaude() {
        com.ultracode.app.ui.openClaudeCode(this)
    }

    override fun onStart() { super.onStart(); vm.appVisible = true }
    override fun onStop() { super.onStop(); vm.appVisible = false }
}
