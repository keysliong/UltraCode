package com.ultracode.app.github

import java.util.Base64
import com.ultracode.app.ai.ApiException
import com.ultracode.app.ai.LlmClient
import com.ultracode.app.ai.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

data class RepoInfo(
    val fullName: String,
    val private: Boolean,
    val description: String,
    val defaultBranch: String,
    val language: String,
)

class GitHub(private val token: String) {
    private val api = "https://api.github.com"
    private val json = com.ultracode.app.data.JsonConfig.json

    private fun enc(path: String) = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun builder(url: String) = Request.Builder().url(if (url.startsWith("http")) url else api + url)
        .header("Authorization", "Bearer ${token.trim()}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun call(url: String, method: String = "GET", body: JsonElement? = null, accept: String? = null): String =
        withContext(Dispatchers.IO) {
            val b = builder(url)
            if (accept != null) b.header("Accept", accept)
            val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
            b.method(method, rb ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody("application/json".toMediaType()))
            LlmClient.http.newCall(b.build()).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val msg = runCatching { json.parseToJsonElement(s).jsonObject["message"].str() }.getOrNull() ?: s.take(300)
                    throw ApiException(r.code, "GitHub ${r.code}: $msg")
                }
                s
            }
        }

    private suspend fun obj(url: String, method: String = "GET", body: JsonElement? = null) =
        json.parseToJsonElement(call(url, method, body)).jsonObject

    suspend fun me(): String = obj("/user")["login"].str() ?: "?"

    suspend fun repos(): List<RepoInfo> {
        val arr = json.parseToJsonElement(call("/user/repos?per_page=100&sort=updated")).jsonArray
        return arr.map { it.jsonObject.toRepo() }
    }

    private fun JsonObject.toRepo() = RepoInfo(
        fullName = this["full_name"].str() ?: "",
        private = this["private"]?.jsonPrimitive?.booleanOrNull ?: false,
        description = this["description"].str() ?: "",
        defaultBranch = this["default_branch"].str() ?: "main",
        language = this["language"].str() ?: "",
    )

    suspend fun defaultBranch(repo: String) = obj("/repos/$repo")["default_branch"].str() ?: "main"

    suspend fun createRepo(name: String, private: Boolean, description: String): RepoInfo =
        obj("/user/repos", "POST", buildJsonObject {
            put("name", name); put("private", private); put("description", description); put("auto_init", true)
        }).toRepo()

    suspend fun listDir(repo: String, path: String, ref: String?): List<String> {
        val q = if (ref.isNullOrBlank()) "" else "?ref=${enc(ref)}"
        val e = json.parseToJsonElement(call("/repos/$repo/contents/${enc(path)}$q"))
        return (e as? JsonArray)?.map {
            val o = it.jsonObject
            val n = o["name"].str() ?: ""
            if (o["type"].str() == "dir") "$n/" else n
        } ?: listOf(path)
    }

    suspend fun readFile(repo: String, path: String, ref: String?): String {
        val q = if (ref.isNullOrBlank()) "" else "?ref=${enc(ref)}"
        return call("/repos/$repo/contents/${enc(path)}$q", accept = "application/vnd.github.raw")
    }

    suspend fun writeFile(repo: String, path: String, content: ByteArray, message: String, branch: String?): String {
        val q = if (branch.isNullOrBlank()) "" else "?ref=${enc(branch)}"
        val sha = runCatching { obj("/repos/$repo/contents/${enc(path)}$q")["sha"].str() }.getOrNull()
        val res = obj("/repos/$repo/contents/${enc(path)}", "PUT", buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content))
            if (sha != null) put("sha", sha)
            if (!branch.isNullOrBlank()) put("branch", branch)
        })
        return res["commit"]?.jsonObject?.get("html_url").str() ?: "ok"
    }

    /** Pushes many files as ONE commit (Git Data API). */
    suspend fun pushFiles(repo: String, branch: String?, message: String, files: Map<String, ByteArray>): String {
        val br = branch?.takeIf { it.isNotBlank() } ?: defaultBranch(repo)
        val ref = runCatching { obj("/repos/$repo/git/ref/heads/${enc(br)}") }.getOrNull()
        if (ref == null) {
            // Empty repository or new branch: fall back to per-file commits.
            files.forEach { (p, b) -> writeFile(repo, p, b, message, br) }
            return "Загружено файлов: ${files.size} (ветка $br)"
        }
        val headSha = ref["object"]!!.jsonObject["sha"].str()!!
        val baseTree = obj("/repos/$repo/git/commits/$headSha")["tree"]!!.jsonObject["sha"].str()!!
        val entries = files.map { (path, bytes) ->
            val blob = obj("/repos/$repo/git/blobs", "POST", buildJsonObject {
                put("content", Base64.getEncoder().encodeToString(bytes)); put("encoding", "base64")
            })["sha"].str()!!
            path to blob
        }
        val tree = obj("/repos/$repo/git/trees", "POST", buildJsonObject {
            put("base_tree", baseTree)
            putJsonArray("tree") {
                entries.forEach { (p, sha) ->
                    addJsonObject {
                        put("path", p)
                        put("mode", if (p.endsWith("gradlew") || p.endsWith(".sh")) "100755" else "100644")
                        put("type", "blob"); put("sha", sha)
                    }
                }
            }
        })["sha"].str()!!
        val commit = obj("/repos/$repo/git/commits", "POST", buildJsonObject {
            put("message", message); put("tree", tree)
            putJsonArray("parents") { add(kotlinx.serialization.json.JsonPrimitive(headSha)) }
        })["sha"].str()!!
        obj("/repos/$repo/git/refs/heads/${enc(br)}", "PATCH", buildJsonObject { put("sha", commit) })
        return "Коммит ${commit.take(7)} в $repo ($br), файлов: ${files.size}"
    }

    suspend fun downloadZip(repo: String, ref: String?): ByteArray = withContext(Dispatchers.IO) {
        val url = "/repos/$repo/zipball" + if (ref.isNullOrBlank()) "" else "/${enc(ref)}"
        LlmClient.http.newCall(builder(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "GitHub ${r.code}")
            r.body!!.bytes()
        }
    }

    suspend fun actionRuns(repo: String): String {
        val runs = obj("/repos/$repo/actions/runs?per_page=6")["workflow_runs"]?.jsonArray ?: return "Нет запусков"
        if (runs.isEmpty()) return "Нет запусков GitHub Actions"
        return runs.joinToString("\n") {
            val o = it.jsonObject
            "#${o["id"].str()} ${o["name"].str()} [${o["head_branch"].str()}] status=${o["status"].str()} result=${o["conclusion"].str() ?: "-"} ${o["html_url"].str()}"
        }
    }

    suspend fun failedJobLogs(repo: String, runId: String): String {
        val jobs = obj("/repos/$repo/actions/runs/$runId/jobs")["jobs"]?.jsonArray ?: return "Нет jobs"
        val sb = StringBuilder()
        for (j in jobs) {
            val o = j.jsonObject
            if (o["conclusion"].str() != "failure") continue
            sb.append("=== Job: ${o["name"].str()} ===\n")
            val log = runCatching { call("/repos/$repo/actions/jobs/${o["id"].str()}/logs", accept = "*/*") }
                .getOrElse { "Не удалось получить лог: ${it.message}" }
            sb.append(log.takeLast(8000)).append('\n')
        }
        return sb.toString().ifEmpty { "Упавших jobs нет" }
    }

    suspend fun repoExists(repo: String): Boolean = try { obj("/repos/$repo"); true } catch (e: ApiException) {
        if (e.code == 404) false else throw e
    }

    private fun pagesUrl(repo: String): String {
        val owner = repo.substringBefore('/'); val name = repo.substringAfter('/')
        return if (name.equals("$owner.github.io", true)) "https://${owner.lowercase()}.github.io/"
        else "https://${owner.lowercase()}.github.io/$name/"
    }

    /** Turns on GitHub Pages for the repository (serving files from [branch] and [path]). */
    suspend fun enablePages(repo: String, branch: String?, path: String = "/"): String {
        val br = branch?.takeIf { it.isNotBlank() } ?: defaultBranch(repo)
        val p = if (path == "/docs") "/docs" else "/"
        val body = buildJsonObject {
            put("source", buildJsonObject { put("branch", br); put("path", p) })
        }
        val o = try {
            obj("/repos/$repo/pages", "POST", body)
        } catch (e: ApiException) {
            when (e.code) {
                409 -> { call("/repos/$repo/pages", "PUT", body); runCatching { obj("/repos/$repo/pages") }.getOrNull() }
                422 -> throw ApiException(422, "GitHub Pages не включается: ${e.message}. Для бесплатного аккаунта репозиторий должен быть публичным.")
                else -> throw e
            }
        }
        return o?.get("html_url").str() ?: pagesUrl(repo)
    }

    suspend fun pagesStatus(repo: String): String {
        val info = try { obj("/repos/$repo/pages") } catch (e: ApiException) {
            if (e.code == 404) return "GitHub Pages не включён для $repo" else throw e
        }
        val build = runCatching { obj("/repos/$repo/pages/builds/latest") }.getOrNull()
        val status = build?.get("status").str() ?: info["status"].str() ?: "unknown"
        val err = (build?.get("error") as? JsonObject)?.get("message").str()
        return "Сайт: ${info["html_url"].str() ?: pagesUrl(repo)}\nСтатус публикации: $status" +
            (if (status == "built") " (готово, сайт открывается)" else if (status == "building" || status == "queued") " (ещё публикуется, подожди 1–2 минуты)" else "") +
            (err?.let { "\nОшибка: $it" } ?: "")
    }

    /** Creates a public repo if needed, pushes the site files and turns on Pages. Returns the site URL. */
    suspend fun deploySite(name: String, files: Map<String, ByteArray>, description: String): String {
        val repo = if (name.contains('/')) name else "${me()}/$name"
        var created = false
        if (!repoExists(repo)) { createRepo(repo.substringAfter('/'), private = false, description = description); created = true }
        val all = files.toMutableMap()
        if (".nojekyll" !in all) all[".nojekyll"] = ByteArray(0)
        val push = pushFiles(repo, null, "Deploy site via UltraCode", all)
        val url = enablePages(repo, null, "/")
        return buildString {
            if (created) append("Создан публичный репозиторий $repo\n")
            append(push).append('\n')
            append("GitHub Pages включён. Сайт: $url\n")
            append("Первая публикация занимает 1–2 минуты. Проверить можно через github_pages_status.")
        }
    }

    suspend fun latestRelease(repo: String): String {
        val o = runCatching { obj("/repos/$repo/releases/latest") }.getOrNull() ?: return "Релизов нет"
        val assets = o["assets"]?.jsonArray?.joinToString("\n") {
            "- ${it.jsonObject["name"].str()}: ${it.jsonObject["browser_download_url"].str()}"
        } ?: ""
        return "${o["name"].str()} ${o["html_url"].str()}\n$assets"
    }

    @Suppress("unused")
    private fun JsonElement?.bool() = (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.boolean
}
