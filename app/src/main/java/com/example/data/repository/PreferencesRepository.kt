package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkweave_reader_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_SYSTEM_PROMPT = "system_translation_prompt"
        private const val KEY_READER_THEME = "reader_theme"
        private const val KEY_READER_FONT = "reader_font"
        private const val KEY_FONT_SIZE = "reader_font_size"
        private const val KEY_LINE_SPACING = "reader_line_spacing"
        private const val KEY_PARAGRAPH_SPACING = "reader_paragraph_spacing"
        private const val KEY_READING_MODE = "reader_reading_mode"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_AUTO_TRANSLATE_NEXT = "auto_translate_next"

        const val MODEL_PRO = "gemini-3.1-pro-preview"
        const val MODEL_FLASH = "gemini-3.5-flash"

        val DEFAULT_SYSTEM_PROMPT = """
You are a master literary translator and localization editor of Chinese web novels (Xianxia, Xuanhuan, Wuxia, Danmei, and Urban Fantasy) producing published-book caliber translations (comparable to premium editions by Seven Seas Entertainment, Wuxiaworld, and professional fantasy presses).

INTEGRAL MANDATORY RULE — 100% FULL FIDELITY & ZERO OMISSIONS:
- You MUST translate EVERY SINGLE SENTENCE, PARAGRAPH, AND DIALOGUE of the chapter from beginning to end without skipping, cutting, condensing, summarizing, or omitting any part of the raw source text.
- NEVER summarize battle scenes, inner thoughts, comedic banter, cultivation explanations, or system notifications.
- The translated chapter must have a complete 1:1 narrative and paragraph completeness matching the raw original from first word to last.

Literary Translation Guidelines:
1. Fluent & Evocative English: Translate with evocative, lyrical, and immersion-first prose. Completely eliminate clunky, mechanical Chinglish and stiff literalisms while honoring the original author's artistic voice, tension, and narrative pacing.
2. Cultivation & Martial Arts Nuance: Accurately translate Eastern cultivation concepts (Qi, Dantian, Spiritual Roots, Heavenly Tribulation, Divine Consciousness, Dao Comprehension) with consistency and solemn dignity.
3. Dialogue & Character Voice: Ensure dialogue captures personality, age, hierarchy, humor, and respect/disrespect naturally.
4. Strict Glossary Enforcement: Always enforce all provided Glossary terms verbatim.
5. Content Cleanliness: Strip out ONLY external website navigation lines, advertisement slogans, site links, or chapter button text ('上一章', '下一章', '目录', '加入书签', '69shu', '笔趣阁', etc.). Never remove any legitimate story content.
6. Output Scope: Output strictly the Chapter Title, the entire narrative unabridged, and the Author's Note at the end if one exists. Do not add conversational remarks, translator notes, or preamble.
""".trimIndent()
    }

    private val _apiKeyFlow = MutableStateFlow(getEffectiveApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _geminiModelFlow = MutableStateFlow(getGeminiModel())
    val geminiModelFlow: StateFlow<String> = _geminiModelFlow.asStateFlow()

    private val _systemPromptFlow = MutableStateFlow(getSystemPrompt())
    val systemPromptFlow: StateFlow<String> = _systemPromptFlow.asStateFlow()

    private val _readerThemeFlow = MutableStateFlow(getReaderTheme())
    val readerThemeFlow: StateFlow<ReaderTheme> = _readerThemeFlow.asStateFlow()

    private val _readerFontFlow = MutableStateFlow(getReaderFont())
    val readerFontFlow: StateFlow<ReaderFont> = _readerFontFlow.asStateFlow()

    private val _fontSizeFlow = MutableStateFlow(getFontSize())
    val fontSizeFlow: StateFlow<Float> = _fontSizeFlow.asStateFlow()

    private val _lineSpacingFlow = MutableStateFlow(getLineSpacing())
    val lineSpacingFlow: StateFlow<Float> = _lineSpacingFlow.asStateFlow()

    private val _paragraphSpacingFlow = MutableStateFlow(getParagraphSpacing())
    val paragraphSpacingFlow: StateFlow<Float> = _paragraphSpacingFlow.asStateFlow()

    private val _readingModeFlow = MutableStateFlow(getReadingMode())
    val readingModeFlow: StateFlow<ReadingMode> = _readingModeFlow.asStateFlow()

    private val _isDarkModeFlow = MutableStateFlow(isDarkMode())
    val isDarkModeFlow: StateFlow<Boolean> = _isDarkModeFlow.asStateFlow()

    private val _autoTranslateNextFlow = MutableStateFlow(isAutoTranslateNext())
    val autoTranslateNextFlow: StateFlow<Boolean> = _autoTranslateNextFlow.asStateFlow()

    fun getCustomApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun getEffectiveApiKey(): String {
        val userKey = getCustomApiKey().trim()
        if (userKey.isNotEmpty()) return userKey
        // Fallback to BuildConfig if provided
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
        _apiKeyFlow.value = getEffectiveApiKey()
    }

    fun getGeminiModel(): String {
        return prefs.getString(KEY_GEMINI_MODEL, MODEL_PRO) ?: MODEL_PRO
    }

    fun setGeminiModel(model: String) {
        prefs.edit().putString(KEY_GEMINI_MODEL, model).apply()
        _geminiModelFlow.value = model
    }

    fun getSystemPrompt(): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
        _systemPromptFlow.value = prompt
    }

    fun resetSystemPrompt() {
        setSystemPrompt(DEFAULT_SYSTEM_PROMPT)
    }

    fun getReaderTheme(): ReaderTheme {
        val name = prefs.getString(KEY_READER_THEME, ReaderTheme.WARM_PARCHMENT.name)
        return try {
            ReaderTheme.valueOf(name ?: ReaderTheme.WARM_PARCHMENT.name)
        } catch (e: Exception) {
            ReaderTheme.WARM_PARCHMENT
        }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        prefs.edit().putString(KEY_READER_THEME, theme.name).apply()
        _readerThemeFlow.value = theme
    }

    fun getReaderFont(): ReaderFont {
        val name = prefs.getString(KEY_READER_FONT, ReaderFont.SERIF.name)
        return try {
            ReaderFont.valueOf(name ?: ReaderFont.SERIF.name)
        } catch (e: Exception) {
            ReaderFont.SERIF
        }
    }

    fun setReaderFont(font: ReaderFont) {
        prefs.edit().putString(KEY_READER_FONT, font.name).apply()
        _readerFontFlow.value = font
    }

    fun getFontSize(): Float = prefs.getFloat(KEY_FONT_SIZE, 18f)

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(12f, 32f)
        prefs.edit().putFloat(KEY_FONT_SIZE, clamped).apply()
        _fontSizeFlow.value = clamped
    }

    fun getLineSpacing(): Float = prefs.getFloat(KEY_LINE_SPACING, 1.55f)

    fun setLineSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(1.1f, 2.4f)
        prefs.edit().putFloat(KEY_LINE_SPACING, clamped).apply()
        _lineSpacingFlow.value = clamped
    }

    fun getParagraphSpacing(): Float = prefs.getFloat(KEY_PARAGRAPH_SPACING, 16f)

    fun setParagraphSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(4f, 36f)
        prefs.edit().putFloat(KEY_PARAGRAPH_SPACING, clamped).apply()
        _paragraphSpacingFlow.value = clamped
    }

    fun getReadingMode(): ReadingMode {
        val name = prefs.getString(KEY_READING_MODE, ReadingMode.TRANSLATED_ONLY.name)
        return try {
            ReadingMode.valueOf(name ?: ReadingMode.TRANSLATED_ONLY.name)
        } catch (e: Exception) {
            ReadingMode.TRANSLATED_ONLY
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        prefs.edit().putString(KEY_READING_MODE, mode.name).apply()
        _readingModeFlow.value = mode
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
        _isDarkModeFlow.value = dark
        if (dark) {
            if (_readerThemeFlow.value != ReaderTheme.DARK_OBSIDIAN && _readerThemeFlow.value != ReaderTheme.OLED_MIDNIGHT) {
                setReaderTheme(ReaderTheme.DARK_OBSIDIAN)
            }
        } else {
            if (_readerThemeFlow.value == ReaderTheme.DARK_OBSIDIAN || _readerThemeFlow.value == ReaderTheme.OLED_MIDNIGHT) {
                setReaderTheme(ReaderTheme.WARM_PARCHMENT)
            }
        }
    }

    fun isAutoTranslateNext(): Boolean = prefs.getBoolean(KEY_AUTO_TRANSLATE_NEXT, true)

    fun setAutoTranslateNext(auto: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE_NEXT, auto).apply()
        _autoTranslateNextFlow.value = auto
    }
}
