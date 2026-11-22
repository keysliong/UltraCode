package com.ultracode.app.agent

import com.ultracode.app.ai.ToolSpec
import com.ultracode.app.ai.str
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
    /** Runs Python code and returns the output (desktop: installed Python, phone: embedded Linux). */
    private val python: (suspend (code: String) -> String)? = null,
) {
    private val json = com.ultracode.app.data.JsonConfig.json

    private fun spec(name: String, desc: String, schema: String) =
        ToolSpec(name, desc, json.parseToJsonElement(schema).jsonObject)

    private val str = """{"type":"string"}"""

    /** Only internet tools — for plain chat mode. */
    val webSpecs: List<ToolSpec> get() = specs.filter { it.name.startsWith("web_") }

    val specs: List<ToolSpec> = buildList {
        if (python != null) {
            add(spec("run_python", "Запустить код на Python и получить вывод (stdout/stderr). Рабочая папка — папка проекта. Используй, чтобы проверить скрипт, посчитать, обработать данные.",
                """{"type":"object","properties":{"code":{"type":"string","description":"Код на Python 3"}},"required":["code"]}"""))
        }
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
            add(spec("github_deploy_site", "Опубликовать сайт на GitHub Pages ОДНОЙ командой: создаёт публичный репозиторий (если нет), загружает файлы из папки рабочей области (там должен быть index.html) и включает Pages. Возвращает ссылку на сайт. Используй ВСЕГДА, когда пользователь хочет сайт — не проси его хостить самому.",
                """{"type":"object","properties":{"name":{"type":"string","description":"Имя репозитория (латиница, без пробелов), например my-portfolio. Имя <логин>.github.io даст сайт в корне домена."},"folder":{"type":"string","description":"Подпапка проекта в рабочей папке с index.html"},"description":$str},"required":["name","folder"]}"""))
            add(spec("github_enable_pages", "Включить GitHub Pages для существующего репозитория (публикация из ветки, корень или /docs).",
                """{"type":"object","properties":{"repo":$str,"branch":$str,"path":{"type":"string","enum":["/","/docs"]}},"required":["repo"]}"""))
            add(spec("github_pages_status", "Ссылка на сайт GitHub Pages и статус публикации (built / building / errored).",
                """{"type":"object","properties":{"repo":$str},"required":["repo"]}"""))
            add(spec("github_latest_release", "Ссылки на файлы последнего релиза (APK, EXE и т.д.).",
                """{"type":"object","properties":{"repo":$str},"required":["repo"]}"""))
        }
    }

    fun label(name: String, args: String): String {
        val a = runCatching { json.parseToJsonElement(args.ifBlank { "{}" }).jsonObject }.getOrNull()
        val detail = a?.let { it["url"].str() ?: it["query"].str() ?: it["path"].str() ?: it["repo"].str() ?: it["name"].str() ?: it["question"].str() } ?: ""
        val icon = when {
            name == "github_deploy_site" || name.contains("pages") -> "🚀"
            name == "run_python" -> "▶️"
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
                "run_python" -> (python ?: error("Python недоступен")).invoke(req("code"))
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
                "github_deploy_site" -> {
                    val folder = req("folder")
                    val files = ws.walk(folder)
                    if (files.isEmpty()) "В папке '$folder' нет файлов — сначала создай сайт (index.html и др.)"
                    else if (files.none { it.equals("index.html", true) }) "В корне папки '$folder' нет index.html — GitHub Pages откроет именно его. Создай index.html."
                    else {
                        val prefix = segments(folder).joinToString("/").let { if (it.isEmpty()) "" else "$it/" }
                        g().deploySite(req("name"), files.associateWith { ws.readBytes(prefix + it) ?: ByteArray(0) }, s("description") ?: "Сайт, созданный в UltraCode")
                    }
                }
                "github_enable_pages" -> "GitHub Pages включён. Сайт: " + g().enablePages(repo(), s("branch"), s("path") ?: "/")
                "github_pages_status" -> g().pagesStatus(repo())
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
    /** Tells Claude Code that GitHub is already connected in UltraCode (token comes via GH_TOKEN / GITHUB_TOKEN). */
    fun githubForClaudeCode(login: String, repo: String?): String = buildString {
        append("\n\nGITHUB УЖЕ ПОДКЛЮЧЁН в UltraCode (аккаунт $login). Токен доступа лежит в переменных окружения GH_TOKEN и GITHUB_TOKEN. ")
        append("НЕ проси пользователя выполнять gh auth login — всё уже настроено. Никогда не выводи токен в ответ и не записывай его в файлы и в git remote. ")
        append("Работай так: команда gh (она сама берёт GH_TOKEN); если gh не установлен — GitHub REST API через curl с заголовком ")
        append("\"Authorization: Bearer \$GH_TOKEN\", а для git push/clone — одноразово: ")
        append("git -c http.extraHeader=\"Authorization: Basic \$(printf 'x-access-token:%s' \"\$GH_TOKEN\" | base64 | tr -d '\\n')\" push https://github.com/OWNER/REPO.git HEAD:main. ")
        append("Создать репозиторий: gh repo create ИМЯ --public --source . --push (или POST https://api.github.com/user/repos). ")
        append("GitHub Pages: gh api repos/OWNER/REPO/pages -X POST -f \"source[branch]=main\" -f \"source[path]=/\" — и дай пользователю ссылку https://$login.github.io/REPO/.")
        if (repo != null) append(" Подключённый к чату репозиторий: $repo (https://github.com/$repo). Если в текущей папке его нет — склонируй его сюда и работай в нём; можешь менять и удалять его содержимое, если пользователь просит.")
    }

    /** Design rules so generated apps and sites don't look like typical "AI slop". */
    val design = """
        ПРАВИЛА ДИЗАЙНА для любых приложений, сайтов и игр, которые ты создаёшь (соблюдай всегда, если пользователь явно не попросил иначе):
        • НИКАКИХ градиентов в фоне, кнопках и заголовках — особенно фиолетово-синих, фиолетово-розовых и «неоновых». Никаких glow-свечений, glassmorphism, размытых пятен-«блобов», текста с градиентной заливкой.
        • НИКАКИХ эмодзи в интерфейсе: ни в кнопках, ни в заголовках, ни в списках, ни в пустых состояниях, ни вместо иконок.
          Вместо эмодзи используй нормальные векторные иконки:
          – Android (Compose): Material Icons — androidx.compose.material:material-icons-extended (Icons.Outlined.*, Icons.Filled.*);
          – веб: Lucide (<script src="https://unpkg.com/lucide@0.469.0/dist/umd/lucide.min.js"></script> + <i data-lucide="name"></i> и lucide.createIcons()) или встроенные inline SVG;
          – ПК: иконки из набора (Lucide, Material Symbols, Fluent UI, Font Awesome).
        • Спокойная палитра: нейтральный фон (белый / светло-серый или тёмно-серый #121212–#1E1E1E), ОДИН акцентный цвет, приглушённые оттенки, контраст текста не ниже WCAG AA. Поддержка светлой и тёмной темы.
        • Android — строго Material 3 / Material You (dynamicColor на Android 12+, стандартные компоненты, без самодельных градиентных кнопок).
        • Веб — чистый минимализм: system-ui / Inter / Roboto, 1–2 шрифта, понятная типографическая иерархия, отступы кратные 4/8 px, скругления 8–16 px, тени мягкие или без них.
        • Тексты интерфейса короткие и по делу. Без рекламных клише («Welcome to the future», «Blazing fast», «Unleash», «Revolutionary»), без лишних бейджей и «вау-эффектов».
        • Анимации — короткие (150–300 мс) и только там, где помогают пониманию.
    """.trimIndent()

    /** How to behave in general: a normal helpful assistant, code only when it is actually asked for. */
    private val general = """
        Ты — UltraCode, универсальный ИИ-помощник. Отвечай на языке пользователя.
        Ты помогаешь с чем угодно: обычный разговор, вопросы и объяснения, учёба, советы, тексты и письма, переводы,
        идеи, планы, математика, здоровье и быт — и, конечно, программирование (это одна из твоих сильных сторон, но не единственная).
        Понимай запрос буквально и по смыслу. НЕ предполагай, что человеку нужен код, приложение или сайт, если он об этом не просил.
        На короткие сообщения вроде «привет», «help», «помоги», «sos» — ответь по-человечески и спроси, с чем помочь, не навязывая разработку.
        Если просьба неясна — задай один короткий уточняющий вопрос.
        Код пиши только когда он нужен для ответа; оформляй его в блоках ```язык ... ```.
        В своих ответах не злоупотребляй эмодзи.
    """.trimIndent()

    val chat = general + """

        Если нужна свежая информация — используй web_search и web_fetch.
        В режиме «Чат» у тебя нет инструментов для файлов и GitHub. Если просят создать репозиторий, файлы или опубликовать сайт —
        скажи, что для этого нужно включить режим «Агент» (кнопка «Агент» над чатом), и тогда ты сделаешь всё сам.
    """.trimIndent().let { "\n\n" + it } + "\n\nЕсли пользователь просит сделать приложение, сайт или игру:\n" + design

    val agent = general + "\n\n" + """
        Сейчас включён режим «Агент»: у тебя есть инструменты — файлы в рабочей папке, GitHub, интернет и ask_user.
        Пользуйся инструментами ТОЛЬКО когда задача действительно этого требует (создать или изменить файлы/проект, работа с репозиторием,
        поиск свежей информации). На обычные вопросы и разговор отвечай просто текстом, ничего не создавая и не вызывая инструменты.

        Ты умеешь делать полноценные приложения:
        • Android: Kotlin + Jetpack Compose + Gradle (Kotlin DSL), minSdk 26, AGP 8.7.3, Kotlin 2.0.21. gradle-wrapper.jar не нужен: в workflow используй actions/setup-java (17) и gradle/actions/setup-gradle@v4 с `gradle-version: '8.11.1'`, затем команду `gradle assembleDebug`.
        • ПК: Python + PyInstaller, Electron, Tauri, .NET/C#, C++ — с workflow для Windows/macOS/Linux (matrix).
        • Сайты: HTML/CSS/JS (или React/Vite с готовой сборкой). ТЫ САМ публикуешь сайт на GitHub Pages инструментом github_deploy_site
          и даёшь пользователю готовую ссылку https://<логин>.github.io/<репозиторий>/. НИКОГДА не предлагай пользователю хостить сайт самому.
          Для статического сайта используй относительные пути (./style.css), index.html в корне папки проекта.
          Для React/Vite: собери статическую версию без сборщика (чистые HTML/CSS/JS) или добавь workflow, который собирает и деплоит в Pages.
        • Игры, Telegram/Discord-боты, скрипты, сервера.

        На телефоне нельзя компилировать, поэтому чтобы собрать приложение:
        1. Создай проект в отдельной подпапке рабочей папки (write_file), пиши ПОЛНОЕ содержимое каждого файла.
        2. Добавь .github/workflows/build.yml, который собирает приложение и выкладывает APK/EXE через actions/upload-artifact и softprops/action-gh-release (permissions: contents: write).
        3. Создай репозиторий (github_create_repo), если его нет, и отправь проект github_push_workspace (folder = подпапка проекта).

        РЕПОЗИТОРИИ ТЫ СОЗДАЁШЬ САМ. Если пользователь просит создать репозиторий — сразу вызови github_create_repo
        (публичный по умолчанию, приватный — если попросили; имя латиницей через дефис), затем дай ссылку https://github.com/<логин>/<имя>.
        Никогда не говори «создайте репозиторий сами» и не проси выполнять команды в терминале. Если GitHub не подключён —
        попроси нажать «Подключить GitHub» в меню.
        4. Проверь сборку github_actions_status; если упала — github_job_log, исправь файлы и отправь снова.
        5. В конце дай ссылку на релиз (github_latest_release) или на сайт (github_pages_status) и кратко объясни, что сделано.
        Если GitHub не подключён — попроси пользователя нажать «Подключить GitHub» в меню (через ask_user), а не хостить вручную.

        Если нужна документация, свежие версии библиотек или решение ошибки — ищи в интернете (web_search, web_fetch).
        Если что-то неясно или есть важный выбор (название, дизайн, платформа) — спроси через ask_user, не гадай.
        Действуй по шагам, не останавливайся на полпути, пока задача не решена.
        В своих ответах не злоупотребляй эмодзи.
    """.trimIndent() + "\n\n" + design
}
