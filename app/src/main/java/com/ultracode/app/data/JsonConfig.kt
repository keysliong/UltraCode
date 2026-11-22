package com.ultracode.app.data

import kotlinx.serialization.json.Json

/** Shared JSON settings (phone and desktop). */
object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
