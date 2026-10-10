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

            // Inspect preceding dictionary for /FlateDecode or /Fl compression
            val dictHeaderLen = minOf(streamStart, 1024)
            val dictSlice = String(bytes.copyOfRange(maxOf(0, streamStart - dictHeaderLen), streamStart), Charsets.US_ASCII)
            val isFlate = dictSlice.contains("/FlateDecode") || dictSlice.contains("/Fl")

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
     * Extracts strings from Tj, TJ, ', and " operators across both single and multi-line syntax.
     */
    private fun extractStringsFromTextOperators(content: String): String {
        val result = StringBuilder()

        // 1. First, process multi-line and single-line TJ arrays: [ ... ] TJ
        val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""", RegexOption.DOT_MATCHES_ALL)
        var lastEnd = 0
        val textSegments = mutableListOf<String>()

        for (match in tjArrayRegex.findAll(content)) {
            val arrayContent = match.groupValues[1]
            val decodedArray = parseArrayContent(arrayContent)
            if (decodedArray.isNotBlank()) {
                textSegments.add(decodedArray)
            }
        }

        // 2. Process line-by-line for standard Tj, ', ", and line break operators
        val lines = content.split('\r', '\n')
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Tj: (Text) Tj
            val tjRegex = Regex("""\((.*?)\)\s*Tj""")
            for (m in tjRegex.findAll(trimmed)) {
                result.append(unescapePdfString(m.groupValues[1])).append(" ")
            }

            // Hex Tj: <48656C6C6F> Tj
            val hexRegex = Regex("""<([0-9a-fA-F]+)>\s*Tj""")
            for (m in hexRegex.findAll(trimmed)) {
                result.append(decodeHexString(m.groupValues[1])).append(" ")
            }

            // ' operator: (Text) ' (move to next line and show text)
            val quoteRegex = Regex("""\((.*?)\)\s*'""")
            for (m in quoteRegex.findAll(trimmed)) {
                result.append(unescapePdfString(m.groupValues[1])).append("\n")
            }

            // Line terminators in PDF: T*, TD, Td
            if (trimmed.endsWith("T*") || trimmed.endsWith("TD") || trimmed.endsWith("Td")) {
                result.append("\n")
            }
        }

        val combined = (textSegments.joinToString("\n") + "\n" + result.toString()).trim()
        return combined
    }

    /**
     * Parses the inner elements of a TJ array: parenthesized strings `(...)` and hex strings `<...>`
     */
    private fun parseArrayContent(arrayContent: String): String {
        val sb = StringBuilder()
        // Extract both (literal string) and <hex string>
        val tokenRegex = Regex("""\((.*?)\)|<([0-9a-fA-F]+)>""")
        for (m in tokenRegex.findAll(arrayContent)) {
            val literalGroup = m.groups[1]?.value
            val hexGroup = m.groups[2]?.value
            if (literalGroup != null) {
                sb.append(unescapePdfString(literalGroup))
            } else if (hexGroup != null) {
                sb.append(decodeHexString(hexGroup))
            }
        }
        return sb.toString().trim()
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

    /**
     * Decodes hex strings with support for both ASCII and UTF-16BE (Word / Adobe export)
     */
    fun decodeHexString(hex: String): String {
        val cleanHex = hex.trim()
        if (cleanHex.isEmpty()) return ""

        // Check for UTF-16BE Byte Order Mark (FEFF)
        val isUtf16Bom = cleanHex.startsWith("feff", ignoreCase = true)
        val isLikelyUtf16 = isUtf16Bom || (cleanHex.length >= 8 && cleanHex.length % 4 == 0 &&
                cleanHex.substring(0, 2) == "00" && cleanHex.substring(4, 6) == "00")

        if (isLikelyUtf16) {
            val startIdx = if (isUtf16Bom) 4 else 0
            val sb = StringBuilder()
            var i = startIdx
            while (i + 3 < cleanHex.length) {
                val codePoint = cleanHex.substring(i, i + 4).toIntOrNull(16) ?: break
                if (codePoint > 0) {
                    sb.append(codePoint.toChar())
                }
                i += 4
            }
            if (sb.isNotBlank()) return sb.toString()
        }

        // Standard 1-byte decoding (ASCII / Latin-1)
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < cleanHex.length) {
            val byteVal = cleanHex.substring(i, i + 2).toIntOrNull(16) ?: break
            if (byteVal in 32..126 || byteVal == 10 || byteVal == 13) {
                sb.append(byteVal.toChar())
            }
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
