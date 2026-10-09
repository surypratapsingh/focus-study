package com.focusstudy.app

import com.focusstudy.app.core.ai.AiSyllabusParser
import com.focusstudy.app.feature.syllabus.PdfTextExtractor
import com.focusstudy.app.feature.syllabus.SyllabusParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class AiSyllabusParserTest {

    @Test
    fun testPdfTextExtractor_UnescapePdfString() {
        assertEquals("Hello World", PdfTextExtractor.unescapePdfString("Hello World"))
        assertEquals("Line 1\nLine 2", PdfTextExtractor.unescapePdfString("Line 1\\nLine 2"))
        assertEquals("(Parentheses)", PdfTextExtractor.unescapePdfString("\\(Parentheses\\)"))
        assertEquals("Backslash \\", PdfTextExtractor.unescapePdfString("Backslash \\\\"))
        assertEquals("Tab\tSpace", PdfTextExtractor.unescapePdfString("Tab\\tSpace"))
    }

    @Test
    fun testPdfTextExtractor_ParseTextFromStream() {
        val streamContent = """
            BT
            /F1 12 Tf
            (Subject: Machine Learning) Tj
            T*
            (Unit 1: Supervised Learning) Tj
            T*
            (Linear Regression) Tj
            T*
            (Logistic Regression) Tj
            ET
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val extracted = PdfTextExtractor.parseTextFromStream(streamContent)
        assertTrue(extracted.contains("Subject: Machine Learning"))
        assertTrue(extracted.contains("Unit 1: Supervised Learning"))
        assertTrue(extracted.contains("Linear Regression"))
        assertTrue(extracted.contains("Logistic Regression"))
    }

    @Test
    fun testPdfTextExtractor_ExtractUncompressedPdf() {
        val pdfContent = """
            %PDF-1.4
            1 0 obj
            << /Length 120 >>
            stream
            BT
            /F1 14 Tf
            (Subject: Software Engineering) Tj T*
            (Unit 1: Agile Methodologies) Tj T*
            (Scrum and Kanban) Tj T*
            ET
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val text = PdfTextExtractor.extractText(pdfContent)
        assertTrue(text.contains("Software Engineering"))
        assertTrue(text.contains("Agile Methodologies"))
        assertTrue(text.contains("Scrum and Kanban"))
    }

    @Test
    fun testPdfTextExtractor_ExtractFlateCompressedPdf() {
        val rawStream = """
            BT
            /F1 12 Tf
            (Subject: Computer Architecture) Tj T*
            (Unit 1: Processor Design) Tj T*
            (Pipelining and Hazards) Tj T*
            (Cache Memory Hierarchy) Tj T*
            ET
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        // Compress stream with Deflate
        val deflater = Deflater()
        val byteOut = ByteArrayOutputStream()
        val deflaterOut = DeflaterOutputStream(byteOut, deflater)
        deflaterOut.write(rawStream)
        deflaterOut.finish()
        val compressed = byteOut.toByteArray()

        val pdfHeader = "%PDF-1.5\n1 0 obj\n<< /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1)
        val pdfFooter = "\nendstream\nendobj\n%%EOF".toByteArray(Charsets.ISO_8859_1)

        val fullPdf = ByteArrayOutputStream().apply {
            write(pdfHeader)
            write(compressed)
            write(pdfFooter)
        }.toByteArray()

        val text = PdfTextExtractor.extractText(fullPdf)
        assertTrue(text.contains("Computer Architecture"))
        assertTrue(text.contains("Processor Design"))
        assertTrue(text.contains("Pipelining and Hazards"))
        assertTrue(text.contains("Cache Memory Hierarchy"))
    }

    @Test
    fun testAiSyllabusParser_ParseDocumentWithPdfBytes() = runBlocking {
        val pdfContent = """
            %PDF-1.4
            1 0 obj
            << /Length 150 >>
            stream
            BT
            (Subject: Distributed Systems) Tj T*
            (Unit 1: Consensus Protocols) Tj T*
            (Raft and Paxos) Tj T*
            (Vector Clocks) Tj T*
            ET
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val parser = AiSyllabusParser()
        val result = parser.parseDocument(
            bytes = pdfContent,
            mimeType = "application/pdf",
            examTitle = "Distributed Systems Exam"
        )

        assertTrue(result.isValid)
        assertTrue(result.topics.isNotEmpty())
        assertEquals("pdf_native_extractor", result.engineSource)
        assertTrue(result.topics.any { it.name.contains("Raft and Paxos") })
        assertTrue(result.topics.any { it.name.contains("Vector Clocks") })
    }

    @Test
    fun testAiSyllabusParser_ParsePlainText() = runBlocking {
        val textContent = """
            Subject: Linear Algebra
            Unit 1: Vector Spaces
            Eigenvalues and Eigenvectors
            Matrix Decomposition
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val parser = AiSyllabusParser()
        val result = parser.parseDocument(
            bytes = textContent,
            mimeType = "text/plain",
            examTitle = "Math Finals"
        )

        assertTrue(result.isValid)
        assertEquals(2, result.topics.size)
        assertEquals("local_heuristic", result.engineSource)
        assertTrue(result.topics.any { it.name.contains("Eigenvalues") })
    }

    @Test
    fun testAiSyllabusParser_RejectsOversizedFile() = runBlocking {
        val parser = AiSyllabusParser()
        val oversizedBytes = ByteArray(16 * 1024 * 1024) // 16MB

        val result = parser.parseDocument(
            bytes = oversizedBytes,
            mimeType = "application/pdf"
        )

        assertFalse(result.isValid)
        assertTrue(result.message.contains("limit"))
    }

    @Test
    fun testAiSyllabusParser_ImageRequiresApiKeyGuidanceWhenOffline() = runBlocking {
        val parser = AiSyllabusParser()
        val dummyImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header

        val result = parser.parseDocument(
            bytes = dummyImageBytes,
            mimeType = "image/png",
            overrideApiKey = null
        )

        assertFalse(result.isValid)
        assertTrue(result.message.contains("Gemini API key"))
    }
}
