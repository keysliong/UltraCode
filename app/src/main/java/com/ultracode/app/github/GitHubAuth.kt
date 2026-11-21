package com.ultracode.app.github

import com.ultracode.app.ai.ApiException
import com.ultracode.app.ai.LlmClient
import com.ultracode.app.ai.str
import com.ultracode.app.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.Request

/** "Sign in with GitHub" via OAuth Device Flow (no client secret needed). */
object GitHubAuth {
    const val CLIENT_ID = "Ov23li5hQFTIcc7PQJf5"
    private const val SCOPES = "repo workflow read:user"

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
        val expiresIn: Int,
    )

    private suspend fun post(url: String, form: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        LlmClient.http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "GitHub ${r.code}: ${s.take(200)}")
            Storage.json.parseToJsonElement(s).jsonObject
        }
    }

    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull

    suspend fun start(): DeviceCode {
        val o = post("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to SCOPES))
        o["error"].str()?.let { throw ApiException(0, explain(it, o["error_description"].str())) }
        return DeviceCode(
            deviceCode = o["device_code"].str() ?: throw ApiException(0, "GitHub не вернул код"),
            userCode = o["user_code"].str() ?: "",
            verificationUri = o["verification_uri"].str() ?: "https://github.com/login/device",
            interval = o.int("interval") ?: 5,
            expiresIn = o.int("expires_in") ?: 900,
        )
    }

    /** Waits until the user confirms the code on github.com. Returns the access token. */
    suspend fun waitForToken(code: DeviceCode): String {
        var interval = code.interval
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val o = post(
                "https://github.com/login/oauth/access_token",
                mapOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to code.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            o["access_token"].str()?.let { return it }
            when (val err = o["error"].str()) {
                "authorization_pending", null -> Unit
                "slow_down" -> interval = (o.int("interval") ?: (interval + 5))
                else -> throw ApiException(0, explain(err, o["error_description"].str()))
            }
        }
        throw ApiException(0, "Время на подтверждение кода истекло. Попробуйте ещё раз.")
    }

    private fun explain(err: String, desc: String?) = when (err) {
        "access_denied" -> "Вход отменён на GitHub."
        "expired_token" -> "Код истёк. Попробуйте ещё раз."
        "device_flow_disabled" -> "В настройках OAuth App на GitHub не включён Device Flow (Settings → Developer settings → OAuth Apps → UltraCode → Enable Device Flow)."
        "incorrect_client_credentials" -> "Неверный Client ID OAuth App."
        else -> desc ?: err
    }
}
