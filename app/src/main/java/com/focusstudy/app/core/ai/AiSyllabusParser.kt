package com.focusstudy.app.core.ai

import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.datastore.UserPreferencesManager
import com.focusstudy.app.core.security.FileImportValidator
import com.focusstudy.app.feature.syllabus.ParsedTopicItem
import com.focusstudy.app.feature.syllabus.PdfTextExtractor
import com.focusstudy.app.feature.syllabus.SyllabusParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class AiSyllabusParseResult(
    val isValid: Boolean,
    val topics: List<ParsedTopicItem> = emptyList(),
    val extractedRawText: String = "",
    val engineSource: String = "local_heuristic", // "pdf_native_extractor", "gemini_multimodal", "local_heuristic"
    val message: String = ""
)

/**
 * Service converting raw syllabus files (PDF, image, text) into structured academic topics.
 * Combines offline deterministic parsing with optional Bring-Your-Own-Key (BYOK) Gemini multimodal vision.
 */
class AiSyllabusParser(
    private val db: AppDatabase? = null,
    private val preferencesManager: UserPreferencesManager? = null
) {

    /**
     * Ingests and parses document bytes into structured syllabus topics
     */
    suspend fun parseDocument(
        bytes: ByteArray,
        mimeType: String,
        examTitle: String = "Exam",
        overrideApiKey: String? = null
    ): AiSyllabusParseResult = withContext(Dispatchers.IO) {
        val metaValidation = FileImportValidator.validateFileMetadata(mimeType, bytes.size.toLong())
        if (!metaValidation.isValid) {
            return@withContext AiSyllabusParseResult(
                isValid = false,
                message = metaValidation.errorMessage ?: "Invalid file or size limit exceeded."
            )
        }

        val apiKey = overrideApiKey?.takeIf { it.isNotBlank() }
            ?: preferencesManager?.userPreferencesFlow?.firstOrNull()?.customGeminiApiKey?.takeIf { it.isNotBlank() }

        val cleanMime = mimeType.lowercase()

        when {
            // 1. PDF Document
            cleanMime == "application/pdf" -> {
                val extractedText = PdfTextExtractor.extractText(bytes)
                val sanitizedValidation = FileImportValidator.sanitizeExtractedText(extractedText)

                if (sanitizedValidation.isValid && !sanitizedValidation.sanitizedText.isNullOrBlank() && sanitizedValidation.sanitizedText!!.length >= 30) {
                    val safeText = sanitizedValidation.sanitizedText!!
                    val parsed = SyllabusParser.parseDocument(safeText, "text/plain")
                    if (parsed.isNotEmpty()) {
                        return@withContext AiSyllabusParseResult(
                            isValid = true,
                            topics = parsed,
                            extractedRawText = safeText,
                            engineSource = "pdf_native_extractor",
                            message = "Successfully extracted ${parsed.size} topics from PDF via native engine."
                        )
                    }
                }

                // If native text extraction found minimal text (scanned PDF), attempt multimodal Gemini if key available
                if (!apiKey.isNullOrBlank()) {
                    try {
                        val aiResult = callGeminiMultimodal(bytes, "application/pdf", apiKey, examTitle)
                        if (aiResult.isNotEmpty()) {
                            return@withContext AiSyllabusParseResult(
                                isValid = true,
                                topics = aiResult,
                                extractedRawText = "Multimodal PDF extraction (${aiResult.size} topics)",
                                engineSource = "gemini_multimodal",
                                message = "Successfully extracted ${aiResult.size} topics from PDF via Gemini 1.5 Flash."
                            )
                        }
                    } catch (e: Exception) {
                        // Fallback below
                    }
                }

                // Fallback for PDF
                if (extractedText.isNotBlank()) {
                    val fallbackParsed = SyllabusParser.parseText(extractedText, examTitle)
                    if (fallbackParsed.isNotEmpty()) {
                        return@withContext AiSyllabusParseResult(
                            isValid = true,
                            topics = fallbackParsed,
                            extractedRawText = extractedText,
                            engineSource = "pdf_native_extractor",
                            message = "Extracted ${fallbackParsed.size} topics from PDF."
                        )
                    }
                }

                return@withContext AiSyllabusParseResult(
                    isValid = false,
                    message = if (apiKey.isNullOrBlank()) {
                        "Scanned or image-based PDF detected. Add your free Gemini API key in Settings to enable AI multimodal scanning, or paste syllabus text."
                    } else {
                        "Could not detect structured topics in PDF. Please ensure syllabus contains clear units and topics."
                    }
                )
            }

            // 2. Image Document (Photo of syllabus / whiteboard)
            cleanMime.startsWith("image/") -> {
                if (!apiKey.isNullOrBlank()) {
                    try {
                        val aiResult = callGeminiMultimodal(bytes, cleanMime, apiKey, examTitle)
                        if (aiResult.isNotEmpty()) {
                            return@withContext AiSyllabusParseResult(
                                isValid = true,
                                topics = aiResult,
                                extractedRawText = "Multimodal Image Extraction (${aiResult.size} topics)",
                                engineSource = "gemini_multimodal",
                                message = "Successfully extracted ${aiResult.size} topics from image via Gemini 1.5 Flash."
                            )
                        }
                    } catch (e: Exception) {
                        return@withContext AiSyllabusParseResult(
                            isValid = false,
                            message = "Gemini image scan failed: ${e.localizedMessage ?: "Unknown error"}"
                        )
                    }
                }

                return@withContext AiSyllabusParseResult(
                    isValid = false,
                    message = "Image scanning requires an optional free Gemini API key (BYOK) configured in Settings, or paste syllabus text."
                )
            }

            // 3. Plain Text or Markdown Document
            else -> {
                val rawText = String(bytes, Charsets.UTF_8)
                val sanitized = FileImportValidator.sanitizeExtractedText(rawText)
                if (!sanitized.isValid || sanitized.sanitizedText.isNullOrBlank()) {
                    return@withContext AiSyllabusParseResult(
                        isValid = false,
                        message = sanitized.errorMessage ?: "Document is empty or unreadable."
                    )
                }

                val safeText = sanitized.sanitizedText!!
                val parsed = SyllabusParser.parseDocument(safeText, cleanMime)
                if (parsed.isEmpty()) {
                    return@withContext AiSyllabusParseResult(
                        isValid = false,
                        message = "No structured topics detected. Please verify syllabus format."
                    )
                }

                return@withContext AiSyllabusParseResult(
                    isValid = true,
                    topics = parsed,
                    extractedRawText = safeText,
                    engineSource = "local_heuristic",
                    message = "Successfully imported ${parsed.size} topics."
                )
            }
        }
    }

    /**
     * Executes native REST call to Gemini 1.5 Flash with multimodal payload
     */
    suspend fun callGeminiMultimodal(
        bytes: ByteArray,
        mimeType: String,
        apiKey: String,
        examTitle: String
    ): List<ParsedTopicItem> = withContext(Dispatchers.IO) {
        val base64Data = Base64.getEncoder().encodeToString(bytes)

        val prompt = """
            You are an academic syllabus parser.
            Exam Title: $examTitle
            Extract all subjects, units/modules, and study topics from this document into valid JSON schema:
            {
              "subjectName": "$examTitle",
              "units": [
                {
                  "unitTitle": "Unit 1: Title",
                  "topics": [
                    {
                      "name": "Topic Name",
                      "difficulty": "medium",
                      "importance": "normal",
                      "estimatedMinutes": 60
                    }
                  ]
                }
              ]
            }
            Difficulty must be one of: "easy", "medium", "hard".
            Importance must be one of: "critical", "high", "normal", "low".
            Output ONLY valid JSON with no markdown formatting or extra text.
        """.trimIndent()

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }

        val requestBody = JSONObject().apply {
            val inlineData = JSONObject().apply {
                put("mimeType", mimeType)
                put("data", base64Data)
            }
            val imagePart = JSONObject().put("inlineData", inlineData)
            val textPart = JSONObject().put("text", prompt)

            val parts = JSONArray().put(imagePart).put(textPart)
            val contents = JSONArray().put(JSONObject().put("parts", parts))
            put("contents", contents)
        }.toString()

        conn.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseString = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseString)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val rawReply = content?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim() ?: ""

            val cleanedJson = rawReply
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            SyllabusParser.parseStructuredAiResponse(cleanedJson)
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Gemini Multimodal error ($code): $errorText")
        }
    }
}
