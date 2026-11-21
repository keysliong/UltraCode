@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultracode.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultracode.app.AppViewModel

@Composable
fun ReposScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    val connected = vm.settings.githubToken.isNotBlank()
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    LaunchedEffect(connected) { if (connected && vm.repos.isEmpty()) vm.loadRepos() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(if (connected) "GitHub · ${vm.settings.githubLogin}" else "GitHub") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (connected) {
                        IconButton(onClick = { vm.loadRepos() }) { Icon(Icons.Outlined.Refresh, "Обновить") }
                        IconButton(onClick = { vm.disconnectGitHub() }) { Icon(Icons.AutoMirrored.Outlined.Logout, "Отключить") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (connected) CookieButton(onClick = { creating = true }, size = 64.dp, lobes = 9, container = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Filled.Add, "Новый репозиторий")
            }
        },
    ) { inner ->
        if (!connected) {
            var token by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CookieBadge(size = 56.dp, container = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Outlined.Code, null, Modifier.size(28.dp))
                        }
                        Text("Подключите GitHub", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Агент сможет читать ваши репозитории, создавать новые, делать коммиты и собирать APK/EXE через GitHub Actions.\n\n" +
                                "1. Нажмите «Создать токен» и войдите в GitHub.\n2. Отметьте права repo и workflow, нажмите Generate token.\n3. Скопируйте токен и вставьте ниже.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = {
                            openUrl(ctx, "https://github.com/settings/tokens/new?scopes=repo,workflow&description=UltraCode")
                        }) { Text("Создать токен") }
                        KeyField("Токен GitHub (ghp_…)", token) { token = it }
                        Button(onClick = { vm.connectGitHub(token) }, enabled = token.isNotBlank()) { Text("Подключить") }
                    }
                }
            }
        } else {
            val attached = vm.current?.repo
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                if (vm.reposLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    Text(
                        "Нажмите на репозиторий, чтобы подключить его к текущему чату",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (attached != null) item {
                    TextButton(onClick = { vm.attachRepo(null) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text("Отключить $attached от чата")
                    }
                }
                items(vm.repos, key = { it.fullName }) { r ->
                    val sel = r.fullName == attached
                    ListItem(
                        headlineContent = { Text(r.fullName) },
                        supportingContent = {
                            Text(listOf(r.language, r.description).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { r.defaultBranch }, maxLines = 2)
                        },
                        leadingContent = {
                            CookieBadge(
                                size = 40.dp, lobes = if (r.private) 6 else 9,
                                container = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Icon(
                                    if (sel) Icons.Filled.Check else if (r.private) Icons.Outlined.Lock else Icons.Outlined.Public, null,
                                    Modifier.size(20.dp),
                                    tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { vm.attachRepo(r.fullName); onBack() },
                    )
                }
                item { Spacer(Modifier.size(96.dp)) }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var private by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            shape = RoundedCornerShape(32.dp),
            title = { Text("Новый репозиторий") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.replace(' ', '-') }, label = { Text("Название") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Приватный", Modifier.weight(1f)); Switch(private, { private = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.createRepo(name.trim(), private); creating = false }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Отмена") } },
        )
    }
}
