package com.focusstudy.app.feature.syllabus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Lightweight, native PDF text extractor conforming to the Ponytail principle.
 * Extracts textual content from uncompressed and FlateDecode-compressed PDF streams
 * without third-party library bloat.
 */
object PdfTextExtractor {

    private val STREAM_MARKER = "stream".toByteArray(Charsets.US_ASCII)
    private val ENDSTREAM_MARKER = "endstream".toByteArray(Charsets.US_ASCII)

    /**
     * Extracts text from PDF bytes.
     * Returns extracted text or empty string if no extractable text found (e.g. scanned image).
     */
    fun extractText(bytes: ByteArray): String {
        if (bytes.size < 8) return ""

        val header = String(bytes.copyOfRange(0, minOf(bytes.size, 10)), Charsets.US_ASCII)
        if (!header.startsWith("%PDF-")) {
            return ""
        }

        val extractedBlocks = mutableListOf<String>()
        var offset = 0

        while (offset < bytes.size) {
            val streamStart = indexOf(bytes, STREAM_MARKER, offset)
            if (streamStart == -1) break

            // Skip "stream" and newline (\r\n or \n)
            var dataStart = streamStart + STREAM_MARKER.size
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val streamEnd = indexOf(bytes, ENDSTREAM_MARKER, dataStart)
            if (streamEnd == -1) break

            // Inspect preceding dictionary for /FlateDecode compression
            val dictHeaderLen = minOf(streamStart, 512)
            val dictSlice = String(bytes.copyOfRange(maxOf(0, streamStart - dictHeaderLen), streamStart), Charsets.US_ASCII)
            val isFlate = dictSlice.contains("/FlateDecode")

            val streamBytes = bytes.copyOfRange(dataStart, streamEnd)
            val rawContent = if (isFlate) {
                decompressFlate(streamBytes) ?: streamBytes
            } else {
                streamBytes
            }

            val text = parseTextFromStream(rawContent)
            if (text.isNotBlank()) {
                extractedBlocks.add(text)
            }

            offset = streamEnd + ENDSTREAM_MARKER.size
        }

        // If stream-based extraction found content, return formatted text
        val combinedStreamText = extractedBlocks.joinToString("\n").trim()
        if (combinedStreamText.length > 20) {
            return cleanExtractedText(combinedStreamText)
        }

        // Fallback: Scan full byte buffer for literal string objects (...) and syllabus markers
        val fallbackText = scanLiteralStrings(bytes)
        return cleanExtractedText(fallbackText)
    }

    /**
     * Decompresses Deflate-compressed streams with standard or raw header fallback
     */
    private fun decompressFlate(bytes: ByteArray): ByteArray? {
        // 1. Standard zlib wrapper
        try {
            val inflater = Inflater(false)
            val input = ByteArrayInputStream(bytes)
            val inflaterStream = InflaterInputStream(input, inflater)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (inflaterStream.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
            }
            return output.toByteArray()
        } catch (_: Exception) {}

        // 2. Raw Deflate (nowrap = true)
        try {
            val inflater = Inflater(true)
            val input = ByteArrayInputStream(bytes)
            val inflaterStream = InflaterInputStream(input, inflater)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (inflaterStream.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
            }
            return output.toByteArray()
        } catch (_: Exception) {}

        return null
    }

    /**
     * Parses PDF operators inside BT (Begin Text) and ET (End Text) blocks
     */
    fun parseTextFromStream(streamBytes: ByteArray): String {
        val content = String(streamBytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Match BT ... ET blocks
        val btEtRegex = Regex("""BT\s+(.*?)\s+ET""", RegexOption.DOT_MATCHES_ALL)
        val matches = btEtRegex.findAll(content).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val block = match.groupValues[1]
                val blockText = extractStringsFromTextOperators(block)
                if (blockText.isNotBlank()) {
                    sb.append(blockText).append("\n")
                }
            }
        } else {
            // Unstructured content: extract Tj / TJ operators directly
            val fallback = extractStringsFromTextOperators(content)
            if (fallback.isNotBlank()) {
                sb.append(fallback)
            }
        }

        return sb.toString().trim()
    }

    /**
     * Extracts strings from Tj, TJ, ', and " operators
     */
    private fun extractStringsFromTextOperators(content: String): String {
        val result = StringBuilder()
        val lines = content.split('\r', '\n')

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 1. Match Tj: (Text) Tj
            val tjRegex = Regex("""\((.*?)\)\s*Tj""")
            for (m in tjRegex.findAll(trimmed)) {
                result.append(unescapePdfString(m.groupValues[1])).append(" ")
            }

            // 2. Match TJ array: [(Text1) -120 (Text2)] TJ
            val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""")
            for (m in tjArrayRegex.findAll(trimmed)) {
                val arrayContent = m.groupValues[1]
                val itemRegex = Regex("""\((.*?)\)""")
                val parts = itemRegex.findAll(arrayContent).map { unescapePdfString(it.groupValues[1]) }
                result.append(parts.joinToString("")).append(" ")
            }

            // 3. Hex string: <48656C6C6F> Tj
            val hexRegex = Regex("""<([0-9a-fA-F]+)>\s*Tj""")
            for (m in hexRegex.findAll(trimmed)) {
                result.append(decodeHexString(m.groupValues[1])).append(" ")
            }

            // 4. Line terminators in PDF: T*, TD, Td, ', "
            if (trimmed.endsWith("T*") || trimmed.endsWith("'") || trimmed.endsWith("\"") ||
                trimmed.endsWith("TD") || trimmed.endsWith("Td")) {
                result.append("\n")
            }
        }

        return result.toString()
    }

    /**
     * Fallback scanner for literal strings (...) when BT/ET blocks are missing
     */
    private fun scanLiteralStrings(bytes: ByteArray): String {
        val content = String(bytes, Charsets.ISO_8859_1)
        val stringLiteralRegex = Regex("""\(([^()]{3,120})\)""")
        val sb = StringBuilder()

        for (match in stringLiteralRegex.findAll(content)) {
            val text = unescapePdfString(match.groupValues[1]).trim()
            if (isPlausibleText(text)) {
                sb.append(text).append("\n")
            }
        }

        return sb.toString()
    }

    private fun isPlausibleText(text: String): Boolean {
        if (text.length < 3) return false
        val printable = text.count { it.isLetterOrDigit() || it.isWhitespace() }
        return (printable.toFloat() / text.length) >= 0.75f
    }

    /**
     * Decodes standard PDF escape sequences: \(, \), \\, \n, \r, \t
     */
    fun unescapePdfString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    '(' -> sb.append('(')
                    ')' -> sb.append(')')
                    '\\' -> sb.append('\\')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun decodeHexString(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val byteVal = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            sb.append(byteVal.toChar())
            i += 2
        }
        return sb.toString()
    }

    private fun cleanExtractedText(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
            .joinToString("\n")
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int = 0): Int {
        if (fromIndex + target.size > source.size) return -1
        for (i in fromIndex..(source.size - target.size)) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
