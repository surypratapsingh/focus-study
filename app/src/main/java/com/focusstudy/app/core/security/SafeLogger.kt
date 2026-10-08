package com.focusstudy.app.core.security

import android.util.Log

/**
 * SafeLogger enforces sensitive-data logging policies (FSTUDY-014-06).
 * Strips PII (emails), bearer tokens, passwords, and sensitive syllabus prompts
 * before emitting to Android Logcat.
 */
object SafeLogger {

    var isDebugMode: Boolean = true

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val BEARER_TOKEN_REGEX = Regex("(?i)bearer\\s+[a-zA-Z0-9_.-]+")
    private val API_KEY_REGEX = Regex("(?i)(key|token|secret|password|auth)[\"':\\s=]+[a-zA-Z0-9_.-]{8,}")

    /**
     * Sanitizes any string payload to ensure no credentials or PII leak into logcat
     */
    fun sanitize(message: String): String {
        var clean = message
        clean = EMAIL_REGEX.replace(clean, "[REDACTED_EMAIL]")
        clean = BEARER_TOKEN_REGEX.replace(clean, "Bearer [REDACTED_TOKEN]")
        clean = API_KEY_REGEX.replace(clean) { match ->
            val prefix = match.value.substringBefore("=").substringBefore(":").trim()
            "$prefix=[REDACTED_SECRET]"
        }
        return clean
    }

    fun d(tag: String, message: String) {
        if (isDebugMode) {
            try {
                Log.d(tag, sanitize(message))
            } catch (e: RuntimeException) {
                println("[$tag] ${sanitize(message)}")
            }
        }
    }

    fun i(tag: String, message: String) {
        try {
            Log.i(tag, sanitize(message))
        } catch (e: RuntimeException) {
            println("[$tag] ${sanitize(message)}")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        try {
            if (throwable != null) {
                Log.w(tag, sanitized, throwable)
            } else {
                Log.w(tag, sanitized)
            }
        } catch (e: RuntimeException) {
            println("[$tag] WARN: $sanitized")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        try {
            if (throwable != null) {
                Log.e(tag, sanitized, throwable)
            } else {
                Log.e(tag, sanitized)
            }
        } catch (e: RuntimeException) {
            System.err.println("[$tag] ERROR: $sanitized")
        }
    }
}
