package com.ultracode.app.agent

import com.ultracode.app.ai.ToolSpec
import com.ultracode.app.ai.str
import com.ultracode.app.data.Storage
import com.ultracode.app.github.GitHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ToolBox(
    private val ws: Workspace,
    private val gh: GitHub?,
    private val defaultRepo: String?,
    private val askUser: suspend (String, List<String>) -> String,
    private val web: WebTools? = null,
) {
    private val json = Storage.json

    private fun spec(name: String, desc: String, schema: String) =
        ToolSpec(name, desc, json.parseToJsonElement(schema).jsonObject)

    private val str = """{"type":"string"}"""

    /** Only internet tools — for plain chat mode. */
    val webSpecs: List<ToolSpec> get() = specs.filter { it.name.startsWith("web_") }

    val specs: List<ToolSpec> = buildList {
        if (web != null) {
            add(spec("web_search", "Искать в интернете (DuckDuckGo). Возвращает заголовки, ссылки и описания. Пользователь видит, какие сайты ты смотришь.",
                """{"type":"object","properties":{"query":{"type":"string","description":"Поисковый запрос"}},"required":["query"]}"""))
            add(spec("web_fetch", "Открыть веб-страницу и прочитать её текст (документация, статьи, GitHub и т.д.).",
                """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""))
        }
        add(spec("list_files", "Показать файлы и папки в рабочей папке. Папки оканчиваются на '/'.",
            """{"type":"object","properties":{"path":{"type":"string","description":"Путь относительно рабочей папки, '' = корень"}}}"""))
        add(spec("read_file", "Прочитать текстовый файл из рабочей папки.",
            """{"type":"object","properties":{"path":$str},"required":["path"]}"""))
        add(spec("write_file", "Создать или полностью перезаписать файл в рабочей папке. Пиши ПОЛНОЕ содержимое файла.",
            """{"type":"object","properties":{"path":$str,"content":$str},"required":["path","content"]}"""))
        add(spec("delete_file", "Удалить файл или папку в рабочей папке.",
            """{"type":"object","properties":{"path":$str},"required":["path"]}"""))
        add(spec("ask_user", "Задать пользователю вопрос и дождаться ответа. Используй, когда нужно уточнение или выбор. Пользователь получит уведомление «Агент хочет спросить».",
            """{"type":"object","properties":{"question":$str,"options":{"type":"array","items":$str,"description":"Варианты ответа (необязательно)"}},"required":["question"]}"""))
        if (gh != null) {
            add(spec("github_list_repos", "Список репозиториев пользователя на GitHub.", """{"type":"object","properties":{}}"""))
            add(spec("github_list_files", "Показать файлы в репозитории GitHub.",
                """{"type":"object","properties":{"repo":{"type":"string","description":"owner/name"},"path":$str,"branch":$str},"required":["repo"]}"""))
            add(spec("github_read_file", "Прочитать файл из репозитория GitHub.",
                """{"type":"object","properties":{"repo":$str,"path":$str,"branch":$str},"required":["repo","path"]}"""))
            add(spec("github_write_file", "Создать/изменить один файл в репозитории GitHub (отдельный коммит).",
                """{"type":"object","properties":{"repo":$str,"path":$str,"content":$str,"message":$str,"branch":$str},"required":["repo","path","content"]}"""))
            add(spec("github_create_repo", "Создать новый репозиторий у пользователя на GitHub.",
                """{"type":"object","properties":{"name":$str,"private":{"type":"boolean"},"description":$str},"required":["name"]}"""))
            add(spec("github_push_workspace", "Отправить все файлы из папки рабочей области в репозиторий одним коммитом. folder — подпапка проекта в рабочей папке (её содержимое станет корнем репозитория).",
                """{"type":"object","properties":{"repo":$str,"folder":$str,"message":$str,"branch":$str},"required":["repo"]}"""))
            add(spec("github_pull_to_workspace", "Скачать репозиторий в рабочую папку (в подпапку folder, по умолчанию — имя репозитория).",
                """{"type":"object","properties":{"repo":$str,"branch":$str,"folder":$str},"required":["repo"]}"""))
            add(spec("github_actions_status", "Статус последних сборок GitHub Actions в репозитории.",
                """{"type":"object","properties":{"repo":$str},"required":["repo"]}"""))
            add(spec("github_job_log", "Получить лог упавших jobs запуска GitHub Actions, чтобы исправить ошибку сборки.",
                """{"type":"object","properties":{"repo":$str,"run_id":$str},"required":["repo","run_id"]}"""))
            add(spec("github_latest_release", "Ссылки на файлы последнего релиза (APK, EXE и т.д.).",
                """{"type":"object","properties":{"repo":$str},"required":["repo"]}"""))
        }
    }

    fun label(name: String, args: String): String {
        val a = runCatching { json.parseToJsonElement(args.ifBlank { "{}" }).jsonObject }.getOrNull()
        val detail = a?.let { it["url"].str() ?: it["query"].str() ?: it["path"].str() ?: it["repo"].str() ?: it["name"].str() ?: it["question"].str() } ?: ""
        val icon = when {
            name == "web_search" -> "🔎"
            name == "web_fetch" -> "🌐"
            name == "write_file" -> "✏️"
            name == "read_file" || name == "github_read_file" -> "📖"
            name.startsWith("list") || name == "github_list_files" -> "📂"
            name == "delete_file" -> "🗑️"
            name == "ask_user" -> "❓"
            name.contains("push") || name.contains("write") -> "⬆️"
            name.contains("pull") -> "⬇️"
            name.contains("actions") || name.contains("log") -> "🛠️"
            else -> "🔧"
        }
        return "$icon $name ${detail.take(60)}".trim()
    }

    suspend fun run(name: String, args: String): String = withContext(Dispatchers.IO) {
        try {
            val a: JsonObject = runCatching { json.parseToJsonElement(args.ifBlank { "{}" }).jsonObject }
                .getOrElse { return@withContext "Ошибка: аргументы не являются корректным JSON" }
            fun s(k: String) = a[k].str()
            fun req(k: String) = s(k) ?: throw IllegalArgumentException("Не указан параметр '$k'")
            fun repo() = s("repo") ?: defaultRepo ?: throw IllegalArgumentException("Не указан repo")
            val g = { gh ?: throw IllegalStateException("GitHub не подключён. Попроси пользователя добавить токен в настройках.") }
            val out = when (name) {
                "web_search" -> (web ?: error("Интернет выключен")).search(req("query"))
                "web_fetch" -> (web ?: error("Интернет выключен")).fetch(req("url"))
                "list_files" -> ws.list(s("path") ?: "").joinToString("\n").ifEmpty { "(пусто)" }
                "read_file" -> ws.read(req("path")) ?: "Файл не найден"
                "write_file" -> { val c = req("content"); ws.write(req("path"), c); "Записано ${c.length} символов в ${s("path")}" }
                "delete_file" -> if (ws.delete(req("path"))) "Удалено" else "Не найдено"
                "ask_user" -> {
                    val opts = (a["options"] as? JsonArray)?.mapNotNull { it.str() } ?: emptyList()
                    "Ответ пользователя: " + askUser(req("question"), opts)
                }
                "github_list_repos" -> g().repos().joinToString("\n") {
                    "${it.fullName}${if (it.private) " (private)" else ""} ${it.language} — ${it.description}"
                }
                "github_list_files" -> g().listDir(repo(), s("path") ?: "", s("branch")).joinToString("\n")
                "github_read_file" -> g().readFile(repo(), req("path"), s("branch"))
                "github_write_file" -> g().writeFile(repo(), req("path"), req("content").toByteArray(),
                    s("message") ?: "Update ${s("path")} via UltraCode", s("branch"))
                "github_create_repo" -> {
                    val r = g().createRepo(req("name"), a["private"]?.jsonPrimitive?.booleanOrNull ?: false, s("description") ?: "Создано в UltraCode")
                    "Создан репозиторий ${r.fullName} (ветка ${r.defaultBranch})"
                }
                "github_push_workspace" -> {
                    val folder = s("folder") ?: ""
                    val files = ws.walk(folder)
                    if (files.isEmpty()) "В папке '$folder' нет файлов"
                    else {
                        val prefix = segments(folder).joinToString("/").let { if (it.isEmpty()) "" else "$it/" }
                        val map = files.associateWith { ws.readBytes(prefix + it) ?: ByteArray(0) }
                        g().pushFiles(repo(), s("branch"), s("message") ?: "Update from UltraCode", map)
                    }
                }
                "github_pull_to_workspace" -> {
                    val r = repo()
                    val folder = s("folder") ?: r.substringAfter('/')
                    var n = 0
                    ZipInputStream(ByteArrayInputStream(g().downloadZip(r, s("branch")))).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            if (!e.isDirectory) {
                                val rel = e.name.substringAfter('/')
                                if (rel.isNotEmpty()) { ws.writeBytes("$folder/$rel", zip.readBytes()); n++ }
                            }
                        }
                    }
                    "Скачано файлов: $n в папку $folder"
                }
                "github_actions_status" -> g().actionRuns(repo())
                "github_job_log" -> g().failedJobLogs(repo(), req("run_id"))
                "github_latest_release" -> g().latestRelease(repo())
                else -> "Неизвестный инструмент $name"
            }
            if (out.length > 30000) out.take(30000) + "\n…(обрезано)" else out
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Ошибка: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}

object Prompts {
    val chat = """
        Ты — UltraCode, дружелюбный ИИ-помощник программиста в мобильном приложении.
        Отвечай на языке пользователя. Код оформляй в блоках ```язык ... ```.
        Если нужна свежая информация или документация — используй web_search и web_fetch.
        Помогай писать приложения для телефона (Android), ПК (Windows, macOS, Linux), сайты, игры и ботов.
    """.trimIndent()

    val agent = """
        Ты — UltraCode Agent, автономный ИИ-агент для программирования, который работает прямо в телефоне пользователя.
        Отвечай на языке пользователя. У тебя есть инструменты: работа с файлами в рабочей папке, GitHub и ask_user.

        Ты умеешь делать полноценные приложения:
        • Android: Kotlin + Jetpack Compose + Gradle (Kotlin DSL), minSdk 26, AGP 8.7.3, Kotlin 2.0.21. gradle-wrapper.jar не нужен: в workflow используй actions/setup-java (17) и gradle/actions/setup-gradle@v4 с `gradle-version: '8.11.1'`, затем команду `gradle assembleDebug`.
        • ПК: Python + PyInstaller, Electron, Tauri, .NET/C#, C++ — с workflow для Windows/macOS/Linux (matrix).
        • Веб: HTML/CSS/JS, React, Vite; публикация через GitHub Pages.
        • Игры, Telegram/Discord-боты, скрипты, сервера.

        На телефоне нельзя компилировать, поэтому чтобы собрать приложение:
        1. Создай проект в отдельной подпапке рабочей папки (write_file), пиши ПОЛНОЕ содержимое каждого файла.
        2. Добавь .github/workflows/build.yml, который собирает приложение и выкладывает APK/EXE через actions/upload-artifact и softprops/action-gh-release (permissions: contents: write).
        3. Создай репозиторий (github_create_repo), если его нет, и отправь проект github_push_workspace (folder = подпапка проекта).
        4. Проверь сборку github_actions_status; если упала — github_job_log, исправь файлы и отправь снова.
        5. В конце дай ссылку на релиз (github_latest_release) и кратко объясни, что сделано.

        Если нужна документация, свежие версии библиотек или решение ошибки — ищи в интернете (web_search, web_fetch).
        Если что-то неясно или есть важный выбор (название, дизайн, платформа) — спроси через ask_user, не гадай.
        Действуй по шагам, не останавливайся на полпути, пока задача не решена.
    """.trimIndent()
}
