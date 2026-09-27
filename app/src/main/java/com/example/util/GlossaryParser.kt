package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.GlossaryTerm
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class GlossaryParseResult(
    val terms: List<GlossaryTerm>,
    val fileName: String,
    val fileType: String,
    val totalParsed: Int,
    val realmCount: Int,
    val techniqueCount: Int,
    val itemCount: Int,
    val characterCount: Int,
    val factionCount: Int,
    val locationCount: Int,
    val generalCount: Int
)

object GlossaryParser {

    /**
     * Parses and auto-rearranges terms from an input stream (TXT, JSON, or PDF) or raw text.
     */
    fun parseFromUri(context: Context, uri: Uri, novelId: Long?): GlossaryParseResult {
        val fileName = getFileName(context, uri)
        val lowerName = fileName.lowercase()
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

        val rawText: String = when {
            lowerName.endsWith(".pdf") -> {
                inputStream?.use { PdfTextExtractor.extractText(it) } ?: ""
            }
            else -> {
                inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
        }

        val fileType = when {
            lowerName.endsWith(".pdf") -> "PDF"
            lowerName.endsWith(".json") -> "JSON"
            else -> "TXT"
        }

        val rearrangedTerms = parseAndRearrange(rawText, novelId)
        return buildResult(rearrangedTerms, fileName, fileType)
    }

    fun parseAndRearrange(rawContent: String, novelId: Long?): List<GlossaryTerm> {
        val rawTerms = mutableListOf<GlossaryTerm>()
        val trimmed = rawContent.trim()

        if (trimmed.isBlank()) return emptyList()

        // 1. Try JSON parsing
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            try {
                parseJsonGlossary(trimmed, novelId, rawTerms)
            } catch (_: Exception) {}
        }

        // 2. If no terms found from JSON, parse line-by-line (TXT, CSV, Markdown, TSV, or PDF text)
        if (rawTerms.isEmpty()) {
            parseDelimitedOrFreeText(trimmed, novelId, rawTerms)
        }

        // 3. Auto-rearrange: Classify, clean, deduplicate, and sort for optimal translation reference
        return rearrangeForTranslation(rawTerms, novelId)
    }

    private fun parseJsonGlossary(jsonStr: String, novelId: Long?, out: MutableList<GlossaryTerm>) {
        if (jsonStr.startsWith("[")) {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                when (item) {
                    is JSONObject -> {
                        val raw = item.optString("rawTerm", item.optString("raw", item.optString("chinese", item.optString("zh", ""))))
                        val trans = item.optString("translatedTerm", item.optString("translated", item.optString("english", item.optString("en", ""))))
                        val cat = item.optString("category", item.optString("type", ""))
                        val notes = if (item.has("notes")) item.optString("notes") else item.optString("description", "")
                        if (raw.isNotBlank() && trans.isNotBlank()) {
                            out.add(GlossaryTerm(novelId = novelId, rawTerm = raw.trim(), translatedTerm = trans.trim(), category = cat.trim(), notes = notes.ifBlank { null }))
                        }
                    }
                    is JSONArray -> {
                        // Array of values: ["金丹期", "Golden Core Stage", "Realm"]
                        if (item.length() >= 2) {
                            val raw = item.optString(0)
                            val trans = item.optString(1)
                            val cat = if (item.length() > 2) item.optString(2) else ""
                            if (raw.isNotBlank() && trans.isNotBlank()) {
                                out.add(GlossaryTerm(novelId = novelId, rawTerm = raw.trim(), translatedTerm = trans.trim(), category = cat.trim()))
                            }
                        }
                    }
                }
            }
        } else if (jsonStr.startsWith("{")) {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                when (value) {
                    is String -> {
                        // "金丹期": "Golden Core Stage"
                        out.add(GlossaryTerm(novelId = novelId, rawTerm = key.trim(), translatedTerm = value.trim(), category = ""))
                    }
                    is JSONObject -> {
                        // Categorized dictionary: "Cultivation Realm": { "金丹期": "Golden Core" }
                        val innerKeys = value.keys()
                        while (innerKeys.hasNext()) {
                            val innerKey = innerKeys.next()
                            val innerVal = value.optString(innerKey)
                            if (innerKey.isNotBlank() && innerVal.isNotBlank()) {
                                out.add(GlossaryTerm(novelId = novelId, rawTerm = innerKey.trim(), translatedTerm = innerVal.trim(), category = key.trim()))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseDelimitedOrFreeText(text: String, novelId: Long?, out: MutableList<GlossaryTerm>) {
        val lines = text.lines()
        for (line in lines) {
            val l = line.trim()
            if (l.isBlank() || l.startsWith("#") || l.startsWith("//")) continue

            // Markdown table row: | raw | trans | category | notes |
            if (l.startsWith("|") && l.endsWith("|")) {
                val parts = l.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2 && !parts[0].contains("---")) {
                    val raw = parts[0]
                    val trans = parts[1]
                    val cat = if (parts.size > 2) parts[2] else ""
                    val notes = if (parts.size > 3) parts[3] else null
                    if (raw.isNotBlank() && trans.isNotBlank() && raw != "Raw" && raw != "Chinese") {
                        out.add(GlossaryTerm(novelId = novelId, rawTerm = raw, translatedTerm = trans, category = cat, notes = notes))
                        continue
                    }
                }
            }

            // Bullet points: - raw: trans
            val cleanLine = l.removePrefix("-").removePrefix("•").removePrefix("*").trim()

            val delimiter = when {
                cleanLine.contains(" = ") -> " = "
                cleanLine.contains("=") -> "="
                cleanLine.contains(" -> ") -> " -> "
                cleanLine.contains("->") -> "->"
                cleanLine.contains(" : ") -> " : "
                cleanLine.contains(":") -> ":"
                cleanLine.contains("\t") -> "\t"
                cleanLine.contains(",") -> ","
                cleanLine.contains("——") -> "——"
                cleanLine.contains("--") -> "--"
                else -> null
            }

            if (delimiter != null) {
                val parts = cleanLine.split(delimiter, limit = 4)
                if (parts.size >= 2) {
                    val raw = cleanTerm(parts[0])
                    val trans = cleanTerm(parts[1])
                    val cat = if (parts.size > 2) cleanTerm(parts[2]) else ""
                    val notes = if (parts.size > 3) cleanTerm(parts[3]).ifBlank { null } else null

                    if (raw.isNotBlank() && trans.isNotBlank()) {
                        out.add(GlossaryTerm(novelId = novelId, rawTerm = raw, translatedTerm = trans, category = cat, notes = notes))
                    }
                }
            }
        }
    }

    private fun cleanTerm(s: String): String {
        return s.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("[", "]")
            .removeSurrounding("(", ")")
            .trim()
    }

    /**
     * Automatically re-arranges and organizes terms so that the translation engine
     * can access and understand them effortlessly without collisions.
     */
    private fun rearrangeForTranslation(rawTerms: List<GlossaryTerm>, novelId: Long?): List<GlossaryTerm> {
        val termMap = LinkedHashMap<String, GlossaryTerm>()

        for (term in rawTerms) {
            val cleanRaw = term.rawTerm.trim()
            val cleanTrans = term.translatedTerm.trim()
            if (cleanRaw.isBlank() || cleanTrans.isBlank()) continue

            // Auto-detect and standardize category
            val category = resolveCategory(cleanRaw, term.category)

            val existing = termMap[cleanRaw]
            if (existing == null) {
                termMap[cleanRaw] = term.copy(
                    novelId = novelId,
                    rawTerm = cleanRaw,
                    translatedTerm = cleanTrans,
                    category = category,
                    notes = term.notes?.trim()?.ifBlank { null }
                )
            } else {
                // If term already exists, preserve the richer notes or more specific category
                val mergedCategory = if (existing.category == "General" && category != "General") category else existing.category
                val mergedNotes = when {
                    existing.notes.isNullOrBlank() -> term.notes?.trim()
                    term.notes.isNullOrBlank() -> existing.notes
                    else -> "${existing.notes}; ${term.notes?.trim()}"
                }
                termMap[cleanRaw] = existing.copy(
                    translatedTerm = cleanTrans,
                    category = mergedCategory,
                    notes = mergedNotes?.ifBlank { null }
                )
            }
        }

        // Re-arrange for translation efficiency:
        // Priority: Longest Chinese raw terms first (prevents sub-phrase collision during translation)
        // Grouped by Category weight: Cultivation Realm -> Character -> Technique -> Item -> Faction -> Location -> General
        return termMap.values.sortedWith(
            compareBy<GlossaryTerm> { getCategoryPriority(it.category) }
                .thenByDescending { it.rawTerm.length }
                .thenBy { it.rawTerm }
        )
    }

    private fun getCategoryPriority(category: String): Int {
        return when (category.lowercase()) {
            "cultivation realm", "realm", "cultivation" -> 1
            "character", "characters", "name", "title" -> 2
            "technique", "techniques", "skill", "martial art", "spell" -> 3
            "item", "items", "treasure", "artifact", "pill", "weapon" -> 4
            "faction", "factions", "sect", "clan", "school" -> 5
            "location", "locations", "place", "world", "realm location" -> 6
            else -> 7
        }
    }

    /**
     * Deep Xianxia / Chinese web novel keyword classification engine.
     */
    fun resolveCategory(raw: String, existingCategory: String): String {
        val cat = existingCategory.trim()
        if (cat.isNotBlank() && !cat.equals("General", ignoreCase = true)) {
            // Standardize existing category name
            return when (cat.lowercase()) {
                "realm", "cultivation", "realms" -> "Cultivation Realm"
                "skill", "skills", "spell", "spells", "martial art" -> "Technique"
                "treasure", "artifact", "artifacts", "weapon", "pill" -> "Item"
                "name", "person", "characters" -> "Character"
                "sect", "clan", "organization", "school" -> "Faction"
                "place", "world", "domain" -> "Location"
                else -> cat.replaceFirstChar { it.uppercase() }
            }
        }

        // 1. Cultivation Realm
        if (raw.endsWith("期") || raw.endsWith("境") || raw.endsWith("阶") || raw.endsWith("层") ||
            raw.contains("筑基") || raw.contains("结丹") || raw.contains("金丹") || raw.contains("元婴") ||
            raw.contains("化神") || raw.contains("返虚") || raw.contains("合体") || raw.contains("大乘") ||
            raw.contains("渡劫") || raw.contains("飞升") || raw.contains("真仙") || raw.contains("金仙") ||
            raw.contains("仙王") || raw.contains("仙帝") || raw.contains("准帝") || raw.contains("天尊") ||
            raw.contains("练气") || raw.contains("炼气") || raw.contains("开脉") || raw.contains("通脉")) {
            return "Cultivation Realm"
        }

        // 2. Technique / Martial Art
        if (raw.endsWith("功") || raw.endsWith("诀") || raw.endsWith("术") || raw.endsWith("法") ||
            raw.endsWith("经") || raw.endsWith("拳") || raw.endsWith("掌") || raw.endsWith("步") ||
            raw.endsWith("指") || raw.endsWith("阵") || raw.endsWith("斩") || raw.endsWith("式") ||
            raw.endsWith("剑法") || raw.endsWith("心法") || raw.endsWith("秘术") || raw.contains("九阳") ||
            raw.contains("太极") || raw.contains("万剑") || raw.contains("御剑") || raw.contains("雷法")) {
            return "Technique"
        }

        // 3. Item / Treasure / Alchemy
        if (raw.endsWith("丹") || raw.endsWith("药") || raw.endsWith("草") || raw.endsWith("花") ||
            raw.endsWith("果") || raw.endsWith("戒") || raw.endsWith("袋") || raw.endsWith("鼎") ||
            raw.endsWith("炉") || raw.endsWith("符") || raw.endsWith("珠") || raw.endsWith("塔") ||
            raw.endsWith("旗") || raw.endsWith("印") || raw.endsWith("镜") || raw.endsWith("石") ||
            raw.endsWith("剑") || raw.endsWith("刀") || raw.endsWith("琴") || raw.endsWith("扇") ||
            raw.contains("灵石") || raw.contains("法宝") || raw.contains("灵宝") || raw.contains("仙器") ||
            raw.contains("储物") || raw.contains("乾坤") || raw.contains("灵芝") || raw.contains("雪莲")) {
            return "Item"
        }

        // 4. Character / Title
        if (raw.endsWith("老祖") || raw.endsWith("宗主") || raw.endsWith("掌门") || raw.endsWith("长老") ||
            raw.endsWith("仙子") || raw.endsWith("道友") || raw.endsWith("大夫") || raw.endsWith("圣子") ||
            raw.endsWith("圣女") || raw.endsWith("皇子") || raw.endsWith("公主") || raw.endsWith("师尊") ||
            raw.endsWith("师父") || raw.endsWith("师兄") || raw.endsWith("师姐") || raw.endsWith("师弟") ||
            raw.endsWith("师妹") || raw.endsWith("前辈") || raw.endsWith("晚辈") || raw.endsWith("神君") ||
            raw.endsWith("真君") || raw.endsWith("真主") || raw.endsWith("魔皇") || raw.endsWith("仙尊")) {
            return "Character"
        }

        // 5. Faction / Sect / Clan
        if (raw.endsWith("宗") || raw.endsWith("门") || raw.endsWith("派") || raw.endsWith("阁") ||
            raw.endsWith("殿") || raw.endsWith("谷") || raw.endsWith("庄") || raw.endsWith("帮") ||
            raw.endsWith("盟") || raw.endsWith("教") || raw.endsWith("世家") || raw.endsWith("氏") ||
            raw.endsWith("朝") || raw.endsWith("王朝") || raw.endsWith("皇朝") || raw.endsWith("洞天")) {
            return "Faction"
        }

        // 6. Location / Territory
        if (raw.endsWith("山") || raw.endsWith("海") || raw.endsWith("峰") || raw.endsWith("城") ||
            raw.endsWith("界") || raw.endsWith("域") || raw.endsWith("洲") || raw.endsWith("洞") ||
            raw.endsWith("岛") || raw.endsWith("潭") || raw.endsWith("湖") || raw.endsWith("林") ||
            raw.endsWith("荒") || raw.endsWith("深渊") || raw.endsWith("秘境") || raw.endsWith("禁地")) {
            return "Location"
        }

        return "General"
    }

    private fun buildResult(terms: List<GlossaryTerm>, fileName: String, fileType: String): GlossaryParseResult {
        return GlossaryParseResult(
            terms = terms,
            fileName = fileName,
            fileType = fileType,
            totalParsed = terms.size,
            realmCount = terms.count { it.category == "Cultivation Realm" },
            techniqueCount = terms.count { it.category == "Technique" },
            itemCount = terms.count { it.category == "Item" },
            characterCount = terms.count { it.category == "Character" },
            factionCount = terms.count { it.category == "Faction" },
            locationCount = terms.count { it.category == "Location" },
            generalCount = terms.count { it.category == "General" }
        )
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "glossary_file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }
}
