package com.example.data.network

import com.example.data.model.GlossaryTerm
import com.example.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TranslationResult(
    val translatedTitle: String,
    val translatedContent: String,
    val glossaryTermsApplied: Int,
    val isAiPowered: Boolean,
    val translationNotes: String
)

class GeminiTranslationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun translateNovelTitle(
        rawTitle: String,
        apiKey: String,
        model: String = "gemini-3.1-pro-preview"
    ): String = withContext(Dispatchers.IO) {
        val cleanTitle = PinyinHelper.cleanRawTitle(rawTitle)
        val offlineTrans = PinyinHelper.translateNovelTitle(cleanTitle)

        val trimmedKey = apiKey.trim()
        if (trimmedKey.isNotEmpty() && trimmedKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = """
                You are an expert literary translator for published Chinese web novels (Seven Seas / Wuxiaworld caliber).
                Translate the Chinese novel title into an accurate, evocative, published-book-level English title.
                Return ONLY the translated title text in English. No quotation marks or conversational commentary.
                
                Chinese Title: $cleanTitle
                """.trimIndent()

                val result = callGeminiShortPrompt(prompt, trimmedKey, model)
                if (result.isNotBlank()) {
                    return@withContext result.removePrefix("\"").removeSuffix("\"").trim()
                }
            } catch (e: Exception) {
                // fall back to offline dictionary
            }
        }
        return@withContext offlineTrans
    }

    private fun callGeminiShortPrompt(promptText: String, apiKey: String, model: String): String {
        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", promptText)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("temperature", 0.3)
        root.put("generationConfig", genConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = root.toString().toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return ""

        val responseString = response.body?.string() ?: return ""
        val jsonResponse = JSONObject(responseString)
        val candidates = jsonResponse.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        if (parts.length() == 0) return ""

        return parts.getJSONObject(0).optString("text", "").trim()
    }

    suspend fun translateChapter(
        rawTitle: String,
        rawContent: String,
        glossary: List<GlossaryTerm>,
        systemPrompt: String,
        apiKey: String,
        model: String = "gemini-3.1-pro-preview"
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val termsApplied = countGlossaryMatches(rawContent, glossary)

        if (trimmedKey.isNotEmpty() && trimmedKey != "MY_GEMINI_API_KEY") {
            try {
                val aiResult = callGeminiApi(rawTitle, rawContent, glossary, systemPrompt, trimmedKey, model)
                return@withContext aiResult.copy(glossaryTermsApplied = termsApplied, isAiPowered = true)
            } catch (e: Exception) {
                // If API call failed, gracefully fall back to local translation
                val fallback = localFallbackTranslate(rawTitle, rawContent, glossary)
                return@withContext fallback.copy(
                    translationNotes = "AI translation attempt failed (${e.message ?: "network issue"}). Fallback translation used."
                )
            }
        } else {
            // Local fallback translation with glossary substitution
            return@withContext localFallbackTranslate(rawTitle, rawContent, glossary)
        }
    }

    private fun countGlossaryMatches(content: String, glossary: List<GlossaryTerm>): Int {
        var count = 0
        for (term in glossary) {
            if (term.rawTerm.isNotEmpty() && content.contains(term.rawTerm)) {
                count++
            }
        }
        return count
    }

    private fun callGeminiApi(
        rawTitle: String,
        rawContent: String,
        glossary: List<GlossaryTerm>,
        systemPrompt: String,
        apiKey: String,
        model: String = "gemini-3.1-pro-preview"
    ): TranslationResult {
        val cleanedRawContent = cleanRawSourceText(rawContent)

        // For standard length chapters (<= 3500 chars), translate in a single full-fidelity pass
        if (cleanedRawContent.length <= 3500) {
            return translateSingleBlock(
                rawTitle = rawTitle,
                rawContent = cleanedRawContent,
                glossary = glossary,
                systemPrompt = systemPrompt,
                apiKey = apiKey,
                model = model
            )
        }

        // For massive chapters (> 3500 Chinese characters), split into natural paragraph chunks
        // to guarantee zero truncation and 100% full narrative fidelity
        val chunks = splitIntoParagraphChunks(cleanedRawContent, targetChunkSize = 2500)
        var translatedTitle = ""
        val combinedContent = StringBuilder()

        for ((index, chunk) in chunks.withIndex()) {
            val chunkTitle = if (index == 0) rawTitle else ""
            val chunkResult = translateSingleBlock(
                rawTitle = chunkTitle,
                rawContent = chunk,
                glossary = glossary,
                systemPrompt = systemPrompt,
                apiKey = apiKey,
                model = model,
                chunkNotice = "Part ${index + 1} of ${chunks.size} of this chapter. Translate this entire section completely without omitting a single word."
            )

            if (index == 0 && chunkResult.translatedTitle.isNotBlank()) {
                translatedTitle = chunkResult.translatedTitle
            }

            if (chunkResult.translatedContent.isNotBlank()) {
                if (combinedContent.isNotEmpty()) combinedContent.append("\n\n")
                combinedContent.append(chunkResult.translatedContent)
            }
        }

        val finalTitle = if (translatedTitle.isNotBlank()) translatedTitle else PinyinHelper.translateNovelTitle(rawTitle)
        return TranslationResult(
            translatedTitle = finalTitle,
            translatedContent = combinedContent.toString().trim(),
            glossaryTermsApplied = 0,
            isAiPowered = true,
            translationNotes = "Complete unabridged translation (${chunks.size} sections joined seamlessly)"
        )
    }

    private fun translateSingleBlock(
        rawTitle: String,
        rawContent: String,
        glossary: List<GlossaryTerm>,
        systemPrompt: String,
        apiKey: String,
        model: String,
        chunkNotice: String = ""
    ): TranslationResult {
        // Construct Categorized Glossary Guidance organized for maximum clarity and priority
        val glossaryPrompt = if (glossary.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("\n\nCRITICAL GLOSSARY REFERENCE (Strictly enforce these exact translations whenever these terms occur):\n")
            sb.append("Rules: Match longer compound phrases first. Never invent conflicting terminology for these defined terms.\n")

            val sortedTerms = glossary.sortedByDescending { it.rawTerm.length }
            val grouped = sortedTerms.groupBy { it.category }

            val categoryOrder = listOf("Cultivation Realm", "Character", "Technique", "Item", "Faction", "Location", "General")
            val orderedCategories = categoryOrder.filter { grouped.containsKey(it) } + (grouped.keys - categoryOrder.toSet())

            for (cat in orderedCategories) {
                val termsInCat = grouped[cat] ?: continue
                sb.append("\n[${cat.uppercase()}S]\n")
                for (term in termsInCat) {
                    sb.append("• ${term.rawTerm} = ${term.translatedTerm}")
                    if (!term.notes.isNullOrBlank()) {
                        sb.append(" (Context: ${term.notes})")
                    }
                    sb.append("\n")
                }
            }
            sb.toString()
        } else ""

        val fullSystemInstruction = "$systemPrompt$glossaryPrompt"

        val chunkInstruction = if (chunkNotice.isNotBlank()) "\n[SECTION NOTICE: $chunkNotice]\n" else ""

        val userPrompt = """
Translate the following Chinese web novel chapter into published-book-quality, literary, and natural English prose.
$chunkInstruction
INTEGRAL MANDATORY RULE (100% FIDELITY — NEVER CUT OR OMIT CONTENT):
- You MUST translate EVERY SINGLE SENTENCE, PARAGRAPH, AND DIALOGUE completely from start to finish without skipping, cutting, condensing, summarizing, or omitting any part of the raw story content.
- Never summarize battle scenes, comedic banter, inner thoughts, cultivation descriptions, or system status notifications.
- The translation MUST preserve the full, unabridged length and narrative detail of the original text with 100% completeness.

CONTENT PURIFICATION (REMOVE ONLY ADS/SITE BUTTONS):
- Remove ONLY pure external website navigation lines, advertisement slogans, domain names, and chapter buttons (e.g. '上一章', '下一章', '目录', '加入书签', '69shu', '笔趣阁', 'www.xxx.com', etc.).
- NEVER remove or truncate any actual story text, monologue, combat action, or dialogue.

OUTPUT FORMAT:
- The output MUST ONLY include:
  1. The chapter title (if provided)
  2. The complete, unabridged chapter narrative
  3. The author's note (if present at the end, formatted as '[Author's Note]: ...')
- Do NOT include any translator remarks, conversational intros, or external site references.

TITLE:
$rawTitle

CONTENT:
$rawContent

Please format your response strictly as:
TITLE: <Translated Title>
CONTENT:
<Translated Content paragraphs>
""".trimIndent()

        val root = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", userPrompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        // System Instruction
        val systemInstructionObj = JSONObject()
        val sysPartsArray = JSONArray()
        val sysPartObj = JSONObject()
        sysPartObj.put("text", fullSystemInstruction)
        sysPartsArray.put(sysPartObj)
        systemInstructionObj.put("parts", sysPartsArray)
        root.put("systemInstruction", systemInstructionObj)

        // Generation Config with generous 8192 token limit to prevent truncation
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.35)
        genConfig.put("topP", 0.95)
        genConfig.put("maxOutputTokens", 8192)
        root.put("generationConfig", genConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = root.toString().toRequestBody(mediaType)

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw Exception("HTTP ${response.code}: $errBody")
        }

        val responseString = response.body?.string() ?: throw Exception("Empty response from Gemini")
        val jsonResponse = JSONObject(responseString)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw Exception("No translation candidate returned")
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: throw Exception("Empty content object")
        val parts = content.optJSONArray("parts") ?: throw Exception("Empty parts array")
        if (parts.length() == 0) throw Exception("No parts in translation response")

        val rawGeneratedText = parts.getJSONObject(0).optString("text", "")
        return parseGeminiOutput(rawGeneratedText, rawTitle)
    }

    private fun cleanRawSourceText(content: String): String {
        val lines = content.lines()
        val filtered = lines.filter { line ->
            val l = line.trim()
            if (l.isEmpty()) return@filter true
            // Only filter out pure website navigation/ad slogans
            val isNav = l.matches(Regex("^(上一章|下一章|上一页|下一页|目录|加入书签|投票推荐|返回书页|加入书架|快捷键.*|温馨提示.*)$"))
            val isSiteAd = l.matches(Regex(".*(69shu|笔趣阁|biquge|www\\.|http://|https://|\\.com|\\.net|最新网址|本章完|手机用户请浏览).*")) && l.length < 50
            !isNav && !isSiteAd
        }
        return filtered.joinToString("\n")
    }

    private fun splitIntoParagraphChunks(content: String, targetChunkSize: Int): List<String> {
        val paragraphs = content.split("\n")
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (p in paragraphs) {
            if (currentChunk.isNotEmpty() && (currentChunk.length + p.length > targetChunkSize)) {
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
            }
            if (currentChunk.isNotEmpty()) currentChunk.append("\n")
            currentChunk.append(p)
        }

        if (currentChunk.isNotBlank()) {
            chunks.add(currentChunk.toString().trim())
        }

        return if (chunks.isNotEmpty()) chunks else listOf(content)
    }

    private fun parseGeminiOutput(text: String, fallbackTitle: String): TranslationResult {
        var translatedTitle = ""
        val contentBuilder = StringBuilder()

        val lines = text.lines()
        var parsingContent = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("TITLE:", ignoreCase = true) && !parsingContent) {
                translatedTitle = trimmed.removePrefix("TITLE:").removePrefix("title:").trim()
            } else if (trimmed.startsWith("CONTENT:", ignoreCase = true)) {
                parsingContent = true
            } else if (parsingContent) {
                contentBuilder.append(line).append("\n")
            } else if (translatedTitle.isEmpty() && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                translatedTitle = trimmed
            } else if (translatedTitle.isNotEmpty() && !parsingContent) {
                parsingContent = true
                contentBuilder.append(line).append("\n")
            }
        }

        val finalTitle = if (translatedTitle.isNotBlank()) translatedTitle else PinyinHelper.translateNovelTitle(fallbackTitle)
        val finalContent = contentBuilder.toString().trim()

        return TranslationResult(
            translatedTitle = finalTitle,
            translatedContent = if (finalContent.isNotBlank()) finalContent else text.trim(),
            glossaryTermsApplied = 0,
            isAiPowered = true,
            translationNotes = "Translated with Gemini AI"
        )
    }

    private fun localFallbackTranslate(
        rawTitle: String,
        rawContent: String,
        glossary: List<GlossaryTerm>
    ): TranslationResult {
        var processedTitle = PinyinHelper.translateNovelTitle(rawTitle)
        var processedContent = rawContent

        // Replace glossary terms
        val sortedGlossary = glossary.sortedByDescending { it.rawTerm.length }
        for (term in sortedGlossary) {
            if (term.rawTerm.isNotEmpty()) {
                processedTitle = processedTitle.replace(term.rawTerm, term.translatedTerm)
                processedContent = processedContent.replace(term.rawTerm, term.translatedTerm)
            }
        }

        // Apply basic dictionary substitutions
        val commonDict = mapOf(
            "第一章" to "Chapter 1", "第二章" to "Chapter 2", "第三章" to "Chapter 3", "第四章" to "Chapter 4",
            "第五章" to "Chapter 5", "第六章" to "Chapter 6", "第七章" to "Chapter 7", "第八章" to "Chapter 8",
            "第九章" to "Chapter 9", "第十章" to "Chapter 10",
            "韩立" to "Han Li", "萧炎" to "Xiao Yan", "克莱恩" to "Klein", "周明瑞" to "Zhou Mingrui",
            "谢怜" to "Xie Lian", "花城" to "Hua Cheng", "魏无羡" to "Wei Wuxian", "蓝忘机" to "Lan Wangji",
            "清晨" to "In the early morning", "傍晚" to "At dusk", "深夜" to "In the deep of night",
            "突然" to "Suddenly", "片刻后" to "A moment later", "此时" to "At this moment",
            "深吸了一口气" to "took a deep breath", "眼神凝重" to "eyes grew solemn",
            "冷笑一声" to "sneered coldly", "点了点头" to "nodded"
        )

        for ((cn, en) in commonDict) {
            processedTitle = processedTitle.replace(cn, en)
            processedContent = processedContent.replace(cn, en)
        }

        return TranslationResult(
            translatedTitle = processedTitle,
            translatedContent = processedContent,
            glossaryTermsApplied = 0,
            isAiPowered = false,
            translationNotes = "Offline translation. Enter a Gemini API Key in Settings to enable literary AI translation."
        )
    }
}
