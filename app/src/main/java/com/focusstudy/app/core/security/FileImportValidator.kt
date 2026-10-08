package com.focusstudy.app.core.security

import java.io.InputStream

data class FileValidationResult(
    val isValid: Boolean,
    val sanitizedText: String? = null,
    val errorMessage: String? = null
)

object FileImportValidator {

    private const val MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024 // 15 MB
    private const val MAX_TEXT_LENGTH_CHARS = 200_000 // 200,000 characters

    private val ALLOWED_MIME_TYPES = setOf(
        "text/plain",
        "text/markdown",
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    // Suspicious prompt-injection sequences that untrusted syllabus files might embed
    private val PROMPT_INJECTION_PATTERNS = listOf(
        Regex("(?i)ignore\\s+all\\s+(previous|prior)\\s+instructions"),
        Regex("(?i)system\\s+prompt"),
        Regex("(?i)you\\s+are\\s+now\\s+in\\s+developer\\s+mode"),
        Regex("(?i)reveal\\s+(the|your)\\s+(secret|hidden|system)\\s+(instructions|prompt)"),
        Regex("(?i)bypass\\s+all\\s+safety\\s+filters")
    )

    /**
     * Validates file metadata before ingestion
     */
    fun validateFileMetadata(
        mimeType: String?,
        sizeBytes: Long
    ): FileValidationResult {
        val cleanMime = mimeType?.lowercase() ?: "application/octet-stream"
        val isAllowed = cleanMime in ALLOWED_MIME_TYPES || cleanMime.startsWith("text/") || cleanMime.startsWith("image/")

        if (!isAllowed) {
            return FileValidationResult(
                isValid = false,
                errorMessage = "Unsupported file format ($cleanMime). Please select a PDF, image, or text file."
            )
        }

        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            val sizeMb = sizeBytes / (1024 * 1024)
            return FileValidationResult(
                isValid = false,
                errorMessage = "File size (${sizeMb}MB) exceeds the maximum allowed limit of 15MB."
            )
        }

        return FileValidationResult(isValid = true)
    }

    /**
     * Sanitizes raw text extracted from uploaded syllabus documents
     */
    fun sanitizeExtractedText(rawText: String): FileValidationResult {
        if (rawText.isBlank()) {
            return FileValidationResult(
                isValid = false,
                errorMessage = "Document appears to be empty or unreadable."
            )
        }

        if (rawText.length > MAX_TEXT_LENGTH_CHARS) {
            return FileValidationResult(
                isValid = false,
                errorMessage = "Document exceeds maximum allowed length of 200,000 characters."
            )
        }

        // 1. Strip null bytes and non-printable control characters (except newline, tab, carriage return)
        var sanitized = rawText.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

        // 2. Neutralize suspected prompt-injection phrases with harmless warning tags
        for (pattern in PROMPT_INJECTION_PATTERNS) {
            sanitized = sanitized.replace(pattern, "[REDACTED_SUSPICIOUS_PROMPT_INJECTION]")
        }

        return FileValidationResult(
            isValid = true,
            sanitizedText = sanitized
        )
    }

    /**
     * Reads text safely from an input stream with length boundaries
     */
    fun readSafeTextFromStream(inputStream: InputStream): FileValidationResult {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                return FileValidationResult(isValid = false, errorMessage = "File exceeds 15MB limit.")
            }
            val text = String(bytes, Charsets.UTF_8)
            sanitizeExtractedText(text)
        } catch (e: Exception) {
            FileValidationResult(isValid = false, errorMessage = "Failed to read file: ${e.localizedMessage}")
        }
    }
}
