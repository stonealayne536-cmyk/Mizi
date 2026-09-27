package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class Novel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val titleTranslated: String? = null,
    val author: String = "Unknown",
    val authorPinyin: String? = null,
    val coverUrl: String? = null,
    val coverGradientIndex: Int = 0,
    val sourceUrl: String = "",
    val description: String = "",
    val totalChapters: Int = 0,
    val lastReadChapterIndex: Int = 0,
    val lastReadScrollOffset: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val customSystemPrompt: String? = null,
    val genre: String = "Xianxia / Fantasy",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val novelId: Long,
    val chapterIndex: Int,
    val titleRaw: String,
    val titleTranslated: String? = null,
    val contentRaw: String,
    val contentTranslated: String? = null,
    val isTranslated: Boolean = false,
    val chapterUrl: String = "",
    val nextChapterUrl: String? = null,
    val translationNotes: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "glossary_terms")
data class GlossaryTerm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val novelId: Long? = null, // null means global glossary applied to all novels
    val rawTerm: String,        // e.g. "金丹期", "储物袋", "青云宗"
    val translatedTerm: String, // e.g. "Golden Core Stage", "Storage Pouch", "Azure Cloud Sect"
    val category: String = "General", // Character, Cultivation Realm, Technique, Item, Faction, Location, General
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val novelId: Long,
    val novelTitle: String,
    val chapterId: Long,
    val chapterTitle: String,
    val highlightedText: String,
    val rawContext: String? = null,
    val userNote: String? = null,
    val colorHex: Long = 0xFFFFD166, // highlight color
    val createdAt: Long = System.currentTimeMillis()
)

enum class ReaderTheme(
    val displayName: String,
    val backgroundColorHex: Long,
    val textColorHex: Long,
    val surfaceColorHex: Long,
    val accentColorHex: Long
) {
    WARM_PARCHMENT(
        displayName = "Parchment",
        backgroundColorHex = 0xFFF7F2E7,
        textColorHex = 0xFF2D251E,
        surfaceColorHex = 0xFFEDE5D5,
        accentColorHex = 0xFF9E6B38
    ),
    CLASSIC_SEPIA(
        displayName = "Sepia",
        backgroundColorHex = 0xFFF1E7D0,
        textColorHex = 0xFF3E3024,
        surfaceColorHex = 0xFFE5D7BC,
        accentColorHex = 0xFF8B5A2B
    ),
    JADE_MIST(
        displayName = "Jade Mist",
        backgroundColorHex = 0xFFEBF3EE,
        textColorHex = 0xFF1C3325,
        surfaceColorHex = 0xFFD8E7DD,
        accentColorHex = 0xFF2F7050
    ),
    DARK_OBSIDIAN(
        displayName = "Obsidian",
        backgroundColorHex = 0xFF191716,
        textColorHex = 0xFFE2DED9,
        surfaceColorHex = 0xFF272320,
        accentColorHex = 0xFFD4A373
    ),
    OLED_MIDNIGHT(
        displayName = "OLED Black",
        backgroundColorHex = 0xFF0D0D0D,
        textColorHex = 0xFFCECAC4,
        surfaceColorHex = 0xFF1A1A1A,
        accentColorHex = 0xFFC59B27
    ),
    CLEAN_LIGHT(
        displayName = "Day Clean",
        backgroundColorHex = 0xFFFAF9F6,
        textColorHex = 0xFF1B1B1B,
        surfaceColorHex = 0xFFEFEFEF,
        accentColorHex = 0xFF5B3926
    )
}

enum class ReaderFont(val displayName: String, val fontFamilyName: String) {
    SERIF("Serif (Classic Book)", "serif"),
    SANS_SERIF("Sans-Serif (Modern)", "sans-serif"),
    MONOSPACE("Monospace (Clean)", "monospace"),
    CURSIVE("Cursive (Calligraphic)", "cursive")
}

enum class ReadingMode {
    TRANSLATED_ONLY,
    DUAL_VIEW,
    RAW_CHINESE
}
