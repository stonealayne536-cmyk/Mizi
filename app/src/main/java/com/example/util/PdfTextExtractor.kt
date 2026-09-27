package com.example.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Lightweight, zero-dependency PDF text extractor designed specifically for Android.
 * Extracts text streams (FlateDecode compressed or uncompressed) and parses standard
 * PDF text operators (Tj, TJ, ', ") with support for UTF-16, UTF-8, and PDFDocEncoding.
 */
object PdfTextExtractor {

    fun extractText(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return ""

        val extracted = StringBuilder()

        // 1. Locate all stream ... endstream blocks
        val streams = findStreams(bytes)
        for (streamData in streams) {
            val text = parsePdfStreamText(streamData)
            if (text.isNotBlank()) {
                extracted.append(text).append("\n")
            }
        }

        // 2. If no BT/ET text operators were found in streams, try raw text search in decompressed streams
        if (extracted.isBlank()) {
            for (streamData in streams) {
                val fallbackText = scanPrintableText(streamData.data)
                if (fallbackText.isNotBlank()) {
                    extracted.append(fallbackText).append("\n")
                }
            }
        }

        // 3. If still blank, search the raw PDF bytes for printable sequences
        if (extracted.isBlank()) {
            extracted.append(scanPrintableText(bytes))
        }

        return cleanExtractedText(extracted.toString())
    }

    private data class StreamBlock(val dict: String, val data: ByteArray)

    private fun findStreams(bytes: ByteArray): List<StreamBlock> {
        val blocks = mutableListOf<StreamBlock>()
        val streamMarker = "stream".toByteArray(Charsets.ISO_8859_1)
        val endstreamMarker = "endstream".toByteArray(Charsets.ISO_8859_1)

        var index = 0
        while (index < bytes.size) {
            val streamPos = indexOf(bytes, streamMarker, index)
            if (streamPos == -1) break

            // Find preceding dictionary (<< ... >>)
            val dictStart = lastIndexOf(bytes, "<<".toByteArray(Charsets.ISO_8859_1), streamPos, (streamPos - 500).coerceAtLeast(0))
            val dict = if (dictStart != -1) {
                String(bytes, dictStart, streamPos - dictStart, Charsets.ISO_8859_1)
            } else ""

            // Skip "stream" and any immediate \r\n or \n
            var dataStart = streamPos + streamMarker.size
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endstreamPos = indexOf(bytes, endstreamMarker, dataStart)
            if (endstreamPos == -1) break

            var dataEnd = endstreamPos
            if (dataEnd > dataStart && bytes[dataEnd - 1] == '\n'.code.toByte()) dataEnd--
            if (dataEnd > dataStart && bytes[dataEnd - 1] == '\r'.code.toByte()) dataEnd--

            val length = (dataEnd - dataStart).coerceAtLeast(0)
            val rawStream = ByteArray(length)
            System.arraycopy(bytes, dataStart, rawStream, 0, length)

            // Decompress if /FlateDecode
            val isFlate = dict.contains("/FlateDecode") || isZlib(rawStream)
            val decompressed = if (isFlate) {
                decompressZlib(rawStream) ?: rawStream
            } else {
                rawStream
            }

            blocks.add(StreamBlock(dict, decompressed))
            index = endstreamPos + endstreamMarker.size
        }

        return blocks
    }

    private fun isZlib(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        return (b0 == 0x78) && (b0 * 256 + b1) % 31 == 0
    }

    private fun decompressZlib(data: ByteArray): ByteArray? {
        // Try standard InflaterInputStream
        try {
            val bis = ByteArrayInputStream(data)
            val iis = InflaterInputStream(bis)
            return iis.readBytes()
        } catch (_: Exception) {}

        // Try raw nowrap Inflater
        try {
            val inflater = Inflater(true)
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0 && inflater.needsInput()) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            return outputStream.toByteArray()
        } catch (_: Exception) {}

        return null
    }

    private fun parsePdfStreamText(block: StreamBlock): String {
        val streamText = String(block.data, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Extract BT ... ET blocks
        var btIndex = 0
        while (true) {
            val bt = streamText.indexOf("BT", btIndex)
            if (bt == -1) break
            val et = streamText.indexOf("ET", bt + 2)
            if (et == -1) break

            val blockContent = streamText.substring(bt + 2, et)
            val parsedText = parseTextOperators(blockContent)
            if (parsedText.isNotBlank()) {
                sb.append(parsedText).append("\n")
            }

            btIndex = et + 2
        }

        return sb.toString().trim()
    }

    private fun parseTextOperators(block: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = block.length

        while (i < len) {
            val c = block[i]

            // Literal string: (...) Tj, (...)'
            if (c == '(') {
                val strEnd = findMatchingParen(block, i)
                if (strEnd != -1) {
                    val rawStr = block.substring(i + 1, strEnd)
                    val unescaped = decodePdfString(rawStr)
                    sb.append(unescaped)
                    i = strEnd + 1
                    continue
                }
            }

            // Hex string: <...> Tj
            if (c == '<' && i + 1 < len && block[i + 1] != '<') {
                val hexEnd = block.indexOf('>', i)
                if (hexEnd != -1) {
                    val hexContent = block.substring(i + 1, hexEnd).replace("\\s".toRegex(), "")
                    val decoded = decodeHexString(hexContent)
                    sb.append(decoded)
                    i = hexEnd + 1
                    continue
                }
            }

            // Array string: [ ... ] TJ
            if (c == '[') {
                val arrayEnd = block.indexOf(']', i)
                if (arrayEnd != -1) {
                    val arrayContent = block.substring(i + 1, arrayEnd)
                    sb.append(parseTJArray(arrayContent))
                    i = arrayEnd + 1
                    continue
                }
            }

            // Newline operators: T*, TD, Td
            if (c == 'T' && i + 1 < len) {
                val next = block[i + 1]
                if (next == '*' || next == 'D' || next == 'd') {
                    sb.append("\n")
                }
            }

            i++
        }

        return sb.toString()
    }

    private fun parseTJArray(arrayContent: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = arrayContent.length

        while (i < len) {
            val c = arrayContent[i]
            if (c == '(') {
                val end = findMatchingParen(arrayContent, i)
                if (end != -1) {
                    sb.append(decodePdfString(arrayContent.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            } else if (c == '<' && i + 1 < len && arrayContent[i + 1] != '<') {
                val end = arrayContent.indexOf('>', i)
                if (end != -1) {
                    val hex = arrayContent.substring(i + 1, end).replace("\\s".toRegex(), "")
                    sb.append(decodeHexString(hex))
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun findMatchingParen(s: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '(') depth++
            if (c == ')') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun decodePdfString(s: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> { bytes.write('\n'.code); i += 2 }
                    'r' -> { bytes.write('\r'.code); i += 2 }
                    't' -> { bytes.write('\t'.code); i += 2 }
                    'b' -> { bytes.write(8); i += 2 }
                    'f' -> { bytes.write(12); i += 2 }
                    '(', ')', '\\' -> { bytes.write(next.code); i += 2 }
                    in '0'..'7' -> {
                        // Octal
                        var octal = "" + next
                        var j = i + 2
                        while (j < s.length && j < i + 4 && s[j] in '0'..'7') {
                            octal += s[j]
                            j++
                        }
                        bytes.write(octal.toInt(8))
                        i = j
                    }
                    else -> {
                        bytes.write(next.code)
                        i += 2
                    }
                }
            } else {
                bytes.write(c.code)
                i++
            }
        }

        val raw = bytes.toByteArray()
        // Check for UTF-16BE BOM (\xFE\xFF)
        if (raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) {
            return String(raw, 2, raw.size - 2, Charsets.UTF_16BE)
        }
        // Try UTF-8
        val utf8 = String(raw, Charsets.UTF_8)
        if (isValidUtf8(utf8)) return utf8
        return String(raw, Charsets.ISO_8859_1)
    }

    private fun decodeHexString(hex: String): String {
        if (hex.isBlank()) return ""
        val clean = if (hex.length % 2 != 0) hex + "0" else hex
        val bytes = ByteArray(clean.length / 2)
        try {
            for (i in bytes.indices) {
                bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            return ""
        }

        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        if (isValidUtf8(utf8)) return utf8
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun isValidUtf8(s: String): Boolean {
        return !s.contains("\uFFFD")
    }

    private fun scanPrintableText(bytes: ByteArray): String {
        val sb = StringBuilder()
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length >= 3 && containsCjkOrAscii(trimmed)) {
                sb.append(trimmed).append("\n")
            }
        }
        return sb.toString()
    }

    private fun containsCjkOrAscii(s: String): Boolean {
        var cjk = 0
        var ascii = 0
        for (ch in s) {
            val code = ch.code
            if (code in 0x4E00..0x9FFF) cjk++
            else if (ch.isLetterOrDigit() || ch in "=:,-\t/|") ascii++
        }
        return (cjk > 0 || ascii > 3)
    }

    private fun cleanExtractedText(text: String): String {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex >= source.size || target.isEmpty()) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun lastIndexOf(source: ByteArray, target: ByteArray, fromIndex: Int, minIndex: Int): Int {
        val start = (fromIndex - target.size).coerceAtMost(source.size - target.size)
        outer@ for (i in start downTo minIndex) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
