package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Chapter
import com.example.data.model.GlossaryTerm
import com.example.data.model.Novel
import com.example.data.model.Quote
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.data.network.GeminiTranslationService
import com.example.data.network.NovelWebScraper
import com.example.data.repository.NovelRepository
import com.example.data.repository.PreferencesRepository
import com.example.ui.navigation.Screen
import com.example.util.GlossaryParseResult
import com.example.util.GlossaryParser
import com.example.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BatchTranslateProgress(
    val novelId: Long,
    val current: Int,
    val total: Int,
    val currentTitle: String,
    val isCompleted: Boolean = false,
    val isRunning: Boolean = true
)

class NovelViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = NovelRepository(
        database.novelDao(),
        database.chapterDao(),
        database.glossaryDao(),
        database.quoteDao()
    )
    val preferences = PreferencesRepository(application)
    private val webScraper = NovelWebScraper()
    private val translationService = GeminiTranslationService()

    // Navigation Backstack
    private val screenStack = mutableListOf<Screen>(Screen.Bookshelf)
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Bookshelf)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Library Data
    val novels: StateFlow<List<Novel>> = repository.allNovels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuotes: StateFlow<List<Quote>> = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalGlossary: StateFlow<List<GlossaryTerm>> = repository.allGlossaryTerms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently Selected Novel
    private val _selectedNovel = MutableStateFlow<Novel?>(null)
    val selectedNovel: StateFlow<Novel?> = _selectedNovel.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    // Current Reading Chapter
    private val _activeChapter = MutableStateFlow<Chapter?>(null)
    val activeChapter: StateFlow<Chapter?> = _activeChapter.asStateFlow()

    // Translation State
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _translationStatus = MutableStateFlow<String?>(null)
    val translationStatus: StateFlow<String?> = _translationStatus.asStateFlow()

    // Batch Translation State (Offline Download)
    private var batchTranslateJob: Job? = null
    private val _batchTranslateProgress = MutableStateFlow<BatchTranslateProgress?>(null)
    val batchTranslateProgress: StateFlow<BatchTranslateProgress?> = _batchTranslateProgress.asStateFlow()

    private var prefetchJob: Job? = null

    // Import State
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isImportingGlossary = MutableStateFlow(false)
    val isImportingGlossary: StateFlow<Boolean> = _isImportingGlossary.asStateFlow()

    private val _glossaryImportResult = MutableStateFlow<GlossaryParseResult?>(null)
    val glossaryImportResult: StateFlow<GlossaryParseResult?> = _glossaryImportResult.asStateFlow()

    init {
        // Automatically ensure all existing and seeded novels have accurate translated English titles and pinyin
        viewModelScope.launch(Dispatchers.IO) {
            repository.allNovels.collect { novelList ->
                for (novel in novelList) {
                    val needsTitleUpdate = novel.titleTranslated.isNullOrBlank() || novel.titleTranslated == novel.title
                    val needsAuthorUpdate = novel.authorPinyin.isNullOrBlank()
                    if (needsTitleUpdate || needsAuthorUpdate) {
                        val transTitle = if (needsTitleUpdate) PinyinHelper.translateNovelTitle(novel.title) else novel.titleTranslated
                        val pinyinAuthor = if (needsAuthorUpdate) PinyinHelper.formatAuthorWithPinyin(novel.author) else novel.authorPinyin
                        repository.updateNovel(
                            novel.copy(
                                titleTranslated = transTitle,
                                authorPinyin = pinyinAuthor
                            )
                        )
                    }
                }
            }
        }
    }

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    // User Feedback Toast/Snackbar
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // Navigation Methods
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.lastIndex)
            _currentScreen.value = screenStack.last()
            return true
        }
        return false
    }

    fun openNovelDetail(novelId: Long) {
        viewModelScope.launch {
            val novel = repository.getNovelById(novelId)
            _selectedNovel.value = novel
            repository.getChaptersByNovel(novelId).collect {
                _chapters.value = it
            }
        }
        navigateTo(Screen.NovelDetail(novelId))
    }

    fun openReader(novelId: Long, chapterId: Long) {
        viewModelScope.launch {
            val novel = repository.getNovelById(novelId)
            _selectedNovel.value = novel

            val chapter = repository.getChapterById(chapterId)
            _activeChapter.value = chapter

            // Update reading progress in database
            chapter?.let { ch ->
                repository.updateReadingProgress(novelId, ch.chapterIndex, 0)
            }

            // Load chapters for selector
            val chapterList = repository.getChaptersByNovelSync(novelId)
            _chapters.value = chapterList

            // If not translated yet and auto-translate is on, trigger translation
            if (chapter != null) {
                if (!chapter.isTranslated) {
                    translateChapterInternal(chapter)
                }
                // Proactively pre-translate the next chapter in the background!
                prefetchAndPretranslateNextChapter(novelId, chapter, chapterList)
            }
        }
        navigateTo(Screen.Reader(novelId, chapterId))
    }

    private fun prefetchAndPretranslateNextChapter(
        novelId: Long,
        currentChapter: Chapter,
        allChapters: List<Chapter>
    ) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val currentIndex = allChapters.indexOfFirst { it.id == currentChapter.id }
            val nextInDb = if (currentIndex in 0 until allChapters.size - 1) {
                allChapters[currentIndex + 1]
            } else null

            if (nextInDb != null) {
                if (!nextInDb.isTranslated) {
                    var rawContent = nextInDb.contentRaw
                    var rawTitle = nextInDb.titleRaw
                    var nextNextUrl = nextInDb.nextChapterUrl

                    // If content is placeholder, fetch it first from web
                    if ((rawContent.startsWith("Loading chapter content...") || rawContent.isBlank()) && !nextInDb.chapterUrl.isNullOrBlank()) {
                        val scraped = webScraper.fetchChapterContent(nextInDb.chapterUrl)
                        scraped.onSuccess { content ->
                            rawContent = content.content
                            rawTitle = content.title
                            nextNextUrl = content.nextChapterUrl
                        }
                    }

                    val glossary = repository.getTermsForNovelSync(novelId)
                    val novel = _selectedNovel.value ?: repository.getNovelById(novelId)
                    val prompt = novel?.customSystemPrompt?.ifBlank { null } ?: preferences.getSystemPrompt()
                    val apiKey = preferences.getEffectiveApiKey()
                    val model = preferences.getGeminiModel()

                    val result = translationService.translateChapter(
                        rawTitle = rawTitle,
                        rawContent = rawContent,
                        glossary = glossary,
                        systemPrompt = prompt,
                        apiKey = apiKey,
                        model = model
                    )

                    val updatedNext = nextInDb.copy(
                        titleRaw = rawTitle,
                        contentRaw = rawContent,
                        nextChapterUrl = nextNextUrl ?: nextInDb.nextChapterUrl,
                        titleTranslated = result.translatedTitle,
                        contentTranslated = result.translatedContent,
                        isTranslated = true,
                        translationNotes = result.translationNotes,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.updateChapter(updatedNext)
                    val refreshed = repository.getChaptersByNovelSync(novelId)
                    _chapters.value = refreshed

                    if (_activeChapter.value?.id == updatedNext.id) {
                        _activeChapter.value = updatedNext
                    }
                }
            } else {
                // If next chapter isn't in database yet, crawl it in background if URL exists!
                val nextUrl = currentChapter.nextChapterUrl
                if (!nextUrl.isNullOrBlank()) {
                    val scraped = webScraper.fetchChapterContent(nextUrl)
                    scraped.onSuccess { content ->
                        val nextIndex = currentChapter.chapterIndex + 1
                        val newChapter = Chapter(
                            novelId = novelId,
                            chapterIndex = nextIndex,
                            titleRaw = content.title,
                            contentRaw = content.content,
                            isTranslated = false,
                            chapterUrl = nextUrl,
                            nextChapterUrl = content.nextChapterUrl
                        )
                        val newId = repository.insertChapter(newChapter)

                        val glossary = repository.getTermsForNovelSync(novelId)
                        val novel = _selectedNovel.value ?: repository.getNovelById(novelId)
                        val prompt = novel?.customSystemPrompt?.ifBlank { null } ?: preferences.getSystemPrompt()
                        val apiKey = preferences.getEffectiveApiKey()
                        val model = preferences.getGeminiModel()

                        val result = translationService.translateChapter(
                            rawTitle = content.title,
                            rawContent = content.content,
                            glossary = glossary,
                            systemPrompt = prompt,
                            apiKey = apiKey,
                            model = model
                        )

                        val translatedChapter = newChapter.copy(
                            id = newId,
                            titleTranslated = result.translatedTitle,
                            contentTranslated = result.translatedContent,
                            isTranslated = true,
                            translationNotes = result.translationNotes
                        )
                        repository.updateChapter(translatedChapter)

                        val refreshed = repository.getChaptersByNovelSync(novelId)
                        _chapters.value = refreshed
                        _selectedNovel.value?.let { nov ->
                            repository.updateNovel(nov.copy(totalChapters = refreshed.size))
                        }

                        if (_activeChapter.value?.id == newId) {
                            _activeChapter.value = translatedChapter
                        }
                    }
                }
            }
        }
    }

    fun nextChapter() {
        val current = _activeChapter.value ?: return
        val currentChapters = _chapters.value
        val currentIndex = currentChapters.indexOfFirst { it.id == current.id }
        if (currentIndex in 0 until currentChapters.size - 1) {
            val next = currentChapters[currentIndex + 1]
            openReader(current.novelId, next.id)
        } else {
            // End of cached chapters. Check if current chapter has a nextChapterUrl!
            val nextUrl = current.nextChapterUrl
            if (!nextUrl.isNullOrBlank()) {
                fetchAndOpenNextOnlineChapter(current.novelId, current.chapterIndex + 1, nextUrl)
            } else {
                showSnackbar("You've reached the latest chapter available.")
            }
        }
    }

    private fun fetchAndOpenNextOnlineChapter(novelId: Long, nextIndex: Int, nextUrl: String) {
        viewModelScope.launch {
            _isTranslating.value = true
            _translationStatus.value = "Accessing Chapter $nextIndex from novel site..."

            val scraped = webScraper.fetchChapterContent(nextUrl)
            scraped.onSuccess { content ->
                val newChapter = Chapter(
                    novelId = novelId,
                    chapterIndex = nextIndex,
                    titleRaw = content.title,
                    contentRaw = content.content,
                    isTranslated = false,
                    chapterUrl = nextUrl,
                    nextChapterUrl = content.nextChapterUrl
                )
                val newId = repository.insertChapter(newChapter)
                val updatedChapters = repository.getChaptersByNovelSync(novelId)
                _chapters.value = updatedChapters
                repository.updateReadingProgress(novelId, nextIndex, 0)
                _selectedNovel.value?.let { nov ->
                    repository.updateNovel(nov.copy(totalChapters = updatedChapters.size))
                }
                _isTranslating.value = false
                openReader(novelId, newId)
            }.onFailure { err ->
                _isTranslating.value = false
                showSnackbar("Could not access next chapter: ${err.message}")
            }
        }
    }

    fun prevChapter() {
        val current = _activeChapter.value ?: return
        val currentChapters = _chapters.value
        val currentIndex = currentChapters.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            val prev = currentChapters[currentIndex - 1]
            openReader(current.novelId, prev.id)
        }
    }

    fun continueReadingNovel(novel: Novel) {
        viewModelScope.launch {
            val chapters = repository.getChaptersByNovelSync(novel.id)
            val target = chapters.getOrNull(novel.lastReadChapterIndex - 1) ?: chapters.firstOrNull()
            if (target != null) {
                openReader(novel.id, target.id)
            } else {
                openNovelDetail(novel.id)
            }
        }
    }

    fun updateNovelSystemPrompt(novelId: Long, customPrompt: String?) {
        viewModelScope.launch {
            val novel = repository.getNovelById(novelId) ?: return@launch
            val updated = novel.copy(customSystemPrompt = customPrompt?.trim()?.ifBlank { null })
            repository.updateNovel(updated)
            _selectedNovel.value = updated
            showSnackbar("Novel translation prompt updated!")
        }
    }

    // Translate Active Chapter
    fun translateActiveChapter(forceReTranslate: Boolean = false) {
        val chapter = _activeChapter.value ?: return
        if (chapter.isTranslated && !forceReTranslate) return

        viewModelScope.launch {
            translateChapterInternal(chapter)
        }
    }

    private suspend fun translateChapterInternal(chapter: Chapter) {
        _isTranslating.value = true
        _translationStatus.value = "Translating Chapter ${chapter.chapterIndex} with AI..."

        var rawContent = chapter.contentRaw
        var rawTitle = chapter.titleRaw
        var nextUrl = chapter.nextChapterUrl

        // If content was not fetched yet (e.g. from catalog import)
        if ((rawContent.startsWith("Loading chapter content...") || rawContent.isBlank()) && !chapter.chapterUrl.isNullOrBlank()) {
            val scraped = webScraper.fetchChapterContent(chapter.chapterUrl)
            scraped.onSuccess { content ->
                rawContent = content.content
                rawTitle = content.title
                nextUrl = content.nextChapterUrl
            }
        }

        val glossary = repository.getTermsForNovelSync(chapter.novelId)
        val novel = _selectedNovel.value
        val prompt = novel?.customSystemPrompt?.ifBlank { null } ?: preferences.getSystemPrompt()
        val apiKey = preferences.getEffectiveApiKey()
        val model = preferences.getGeminiModel()

        val result = translationService.translateChapter(
            rawTitle = rawTitle,
            rawContent = rawContent,
            glossary = glossary,
            systemPrompt = prompt,
            apiKey = apiKey,
            model = model
        )

        val updatedChapter = chapter.copy(
            titleRaw = rawTitle,
            contentRaw = rawContent,
            nextChapterUrl = nextUrl ?: chapter.nextChapterUrl,
            titleTranslated = result.translatedTitle,
            contentTranslated = result.translatedContent,
            isTranslated = true,
            translationNotes = result.translationNotes,
            lastUpdated = System.currentTimeMillis()
        )

        repository.updateChapter(updatedChapter)
        _activeChapter.value = updatedChapter

        // Update list
        val list = _chapters.value.map { if (it.id == updatedChapter.id) updatedChapter else it }
        _chapters.value = list

        _isTranslating.value = false
        _translationStatus.value = if (result.isAiPowered) {
            "Translated with Gemini AI (${result.glossaryTermsApplied} glossary terms matched)"
        } else {
            result.translationNotes
        }
    }

    // Translate any specific chapter from detail list
    fun translateSingleChapter(chapterId: Long) {
        viewModelScope.launch {
            val chapter = repository.getChapterById(chapterId) ?: return@launch
            translateChapterInternal(chapter)
            showSnackbar("Chapter ${chapter.chapterIndex} translated!")
        }
    }

    // Batch Offline Translation
    fun startBatchTranslation(novelId: Long) {
        if (batchTranslateJob?.isActive == true) {
            showSnackbar("A batch translation is already in progress.")
            return
        }

        batchTranslateJob = viewModelScope.launch(Dispatchers.IO) {
            val allChapters = repository.getChaptersByNovelSync(novelId)
            val untranslated = allChapters.filter { !it.isTranslated }

            if (untranslated.isEmpty()) {
                withContext(Dispatchers.Main) {
                    showSnackbar("All chapters are already translated and available offline!")
                }
                return@launch
            }

            val novel = repository.getNovelById(novelId)
            val prompt = novel?.customSystemPrompt?.ifBlank { null } ?: preferences.getSystemPrompt()
            val apiKey = preferences.getEffectiveApiKey()
            val model = preferences.getGeminiModel()
            val glossary = repository.getTermsForNovelSync(novelId)

            _batchTranslateProgress.value = BatchTranslateProgress(
                novelId = novelId,
                current = 0,
                total = untranslated.size,
                currentTitle = "Starting offline translation...",
                isRunning = true
            )

            var completedCount = 0
            for (chapter in untranslated) {
                if (!isActive) break

                _batchTranslateProgress.value = BatchTranslateProgress(
                    novelId = novelId,
                    current = completedCount + 1,
                    total = untranslated.size,
                    currentTitle = chapter.titleRaw,
                    isRunning = true
                )

                var rawContent = chapter.contentRaw
                var rawTitle = chapter.titleRaw
                var nextUrl = chapter.nextChapterUrl

                // If content is placeholder, fetch from source website
                if ((rawContent.startsWith("Loading chapter content...") || rawContent.isBlank()) && !chapter.chapterUrl.isNullOrBlank()) {
                    val scraped = webScraper.fetchChapterContent(chapter.chapterUrl)
                    scraped.onSuccess { content ->
                        rawContent = content.content
                        rawTitle = content.title
                        nextUrl = content.nextChapterUrl
                    }
                }

                try {
                    val result = translationService.translateChapter(
                        rawTitle = rawTitle,
                        rawContent = rawContent,
                        glossary = glossary,
                        systemPrompt = prompt,
                        apiKey = apiKey,
                        model = model
                    )

                    val updated = chapter.copy(
                        titleRaw = rawTitle,
                        contentRaw = rawContent,
                        nextChapterUrl = nextUrl ?: chapter.nextChapterUrl,
                        titleTranslated = result.translatedTitle,
                        contentTranslated = result.translatedContent,
                        isTranslated = true,
                        translationNotes = result.translationNotes,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.updateChapter(updated)
                    completedCount++

                    // Refresh chapters flow
                    val refreshed = repository.getChaptersByNovelSync(novelId)
                    _chapters.value = refreshed

                    // If active chapter is this one, update it as well
                    if (_activeChapter.value?.id == updated.id) {
                        _activeChapter.value = updated
                    }
                } catch (e: Exception) {
                    // continue to next chapter
                }

                delay(350)
            }

            _batchTranslateProgress.value = BatchTranslateProgress(
                novelId = novelId,
                current = completedCount,
                total = untranslated.size,
                currentTitle = "Batch translation complete! $completedCount chapters saved for offline reading.",
                isCompleted = true,
                isRunning = false
            )

            withContext(Dispatchers.Main) {
                showSnackbar("Offline translation complete! $completedCount chapters ready offline.")
            }
        }
    }

    fun cancelBatchTranslation() {
        batchTranslateJob?.cancel()
        batchTranslateJob = null
        _batchTranslateProgress.value = null
        showSnackbar("Batch translation stopped. Saved chapters remain offline.")
    }

    fun dismissBatchProgress() {
        _batchTranslateProgress.value = null
    }

    // Scrape / Import Novel from URL
    fun importNovelFromUrl(url: String, onComplete: (Long, Long) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null

            val result = webScraper.scrapeNovelFromUrl(url)
            result.onSuccess { scraped ->
                val apiKey = preferences.getEffectiveApiKey()
                val model = preferences.getGeminiModel()
                val accurateEnglishTitle = translationService.translateNovelTitle(scraped.novel.title, apiKey, model)
                val refinedNovel = scraped.novel.copy(
                    titleTranslated = accurateEnglishTitle,
                    authorPinyin = PinyinHelper.formatAuthorWithPinyin(scraped.novel.author)
                )

                val novelId = repository.insertNovel(refinedNovel)
                val chaptersWithId = scraped.chapters.map { it.copy(novelId = novelId) }
                repository.insertChapters(chaptersWithId)

                val storedChapters = repository.getChaptersByNovelSync(novelId)
                _selectedNovel.value = refinedNovel.copy(id = novelId)
                _chapters.value = storedChapters
                _isImporting.value = false
                val displayTitle = refinedNovel.titleTranslated ?: refinedNovel.title
                showSnackbar("Imported '$displayTitle' (${chaptersWithId.size} chapters)")

                // Find the exact chapter from link, or first chapter
                val targetChapter = storedChapters.find { it.chapterUrl == url.trim() }
                    ?: storedChapters.firstOrNull()

                val targetChapterId = targetChapter?.id ?: 0L
                onComplete(novelId, targetChapterId)
            }.onFailure { err ->
                _isImporting.value = false
                _importError.value = err.message ?: "Failed to load link"
                showSnackbar("Could not scrape link: ${err.message}. You can still paste raw chapters or use sample novels!")
            }
        }
    }

    // Add Novel manually with raw Chinese text
    fun addManualNovel(
        title: String,
        author: String,
        description: String,
        firstChapterTitle: String,
        firstChapterContent: String,
        sourceUrl: String = ""
    ) {
        viewModelScope.launch {
            val rawTitle = title.ifBlank { "Untitled Chinese Novel" }
            val rawAuthor = author.ifBlank { "Unknown" }
            val apiKey = preferences.getEffectiveApiKey()
            val model = preferences.getGeminiModel()
            val translatedTitle = translationService.translateNovelTitle(rawTitle, apiKey, model)

            val novel = Novel(
                title = rawTitle,
                titleTranslated = translatedTitle,
                author = rawAuthor,
                authorPinyin = PinyinHelper.formatAuthorWithPinyin(rawAuthor),
                coverGradientIndex = (rawTitle.hashCode() and 0x7FFFFFFF) % 7,
                description = description.ifBlank { "Custom raw chapter collection" },
                sourceUrl = sourceUrl,
                totalChapters = 1
            )
            val novelId = repository.insertNovel(novel)
            val chapter = Chapter(
                novelId = novelId,
                chapterIndex = 1,
                titleRaw = firstChapterTitle.ifBlank { "第一章" },
                contentRaw = firstChapterContent,
                isTranslated = false
            )
            val chapterId = repository.insertChapter(chapter)
            showSnackbar("Novel created! Opening chapter...")
            openReader(novelId, chapterId)
        }
    }

    // Add extra chapter to current novel
    fun addChapterToNovel(novelId: Long, titleRaw: String, contentRaw: String) {
        viewModelScope.launch {
            val currentChapters = repository.getChaptersByNovelSync(novelId)
            val newIndex = currentChapters.size + 1
            val chapter = Chapter(
                novelId = novelId,
                chapterIndex = newIndex,
                titleRaw = titleRaw.ifBlank { "第${newIndex}章" },
                contentRaw = contentRaw,
                isTranslated = false
            )
            repository.insertChapter(chapter)
            val updated = repository.getChaptersByNovelSync(novelId)
            _chapters.value = updated
            repository.updateReadingProgress(novelId, newIndex, 0)
            showSnackbar("Added Chapter $newIndex!")
        }
    }

    fun deleteNovel(novelId: Long, navigateBackIfDetail: Boolean = true) {
        viewModelScope.launch {
            repository.deleteNovelById(novelId)
            showSnackbar("Novel deleted from library")
            if (navigateBackIfDetail && _currentScreen.value is Screen.NovelDetail) {
                navigateBack()
            }
        }
    }

    // Glossary Operations
    fun addGlossaryTerm(
        rawTerm: String,
        translatedTerm: String,
        category: String,
        notes: String?,
        novelId: Long?
    ) {
        viewModelScope.launch {
            if (rawTerm.isBlank() || translatedTerm.isBlank()) return@launch
            val term = GlossaryTerm(
                rawTerm = rawTerm.trim(),
                translatedTerm = translatedTerm.trim(),
                category = category.ifBlank { "General" },
                notes = notes?.trim()?.ifBlank { null },
                novelId = novelId
            )
            repository.insertGlossaryTerm(term)
            showSnackbar("Added term '${term.rawTerm}' -> '${term.translatedTerm}'")
        }
    }

    fun deleteGlossaryTerm(id: Long) {
        viewModelScope.launch {
            repository.deleteGlossaryTerm(id)
            showSnackbar("Glossary term removed")
        }
    }

    // One-Tap Import Glossary from File (TXT, JSON, PDF)
    fun importGlossaryFromUri(context: Context, uri: Uri, novelId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImportingGlossary.value = true
            try {
                val result = GlossaryParser.parseFromUri(context, uri, novelId)
                if (result.terms.isNotEmpty()) {
                    repository.insertGlossaryTerms(result.terms)
                    _glossaryImportResult.value = result
                    withContext(Dispatchers.Main) {
                        showSnackbar("Imported ${result.totalParsed} terms from ${result.fileName}! (${result.realmCount} realms, ${result.techniqueCount} techniques, ${result.itemCount} items) auto-arranged for translation.")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showSnackbar("No terms could be recognized in ${result.fileName}. Ensure it contains term pairs or glossary entries.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Failed to import file: ${e.message ?: "Unknown error"}")
                }
            } finally {
                _isImportingGlossary.value = false
            }
        }
    }

    // Import Glossary from text/paste (Auto-rearranges and categorizes)
    fun importGlossaryFile(content: String, novelId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImportingGlossary.value = true
            try {
                val terms = GlossaryParser.parseAndRearrange(content, novelId)
                if (terms.isNotEmpty()) {
                    repository.insertGlossaryTerms(terms)
                    withContext(Dispatchers.Main) {
                        showSnackbar("Successfully imported and auto-arranged ${terms.size} glossary terms!")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showSnackbar("Could not detect terms. Use format: 'RawTerm = TranslatedTerm', JSON, or CSV.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error importing glossary: ${e.message}")
                }
            } finally {
                _isImportingGlossary.value = false
            }
        }
    }

    fun dismissGlossaryImportResult() {
        _glossaryImportResult.value = null
    }

    // Quotes & Highlights
    fun addQuote(
        highlightedText: String,
        rawContext: String?,
        userNote: String?,
        colorHex: Long = 0xFFFFD166
    ) {
        val chapter = _activeChapter.value ?: return
        val novel = _selectedNovel.value ?: return

        viewModelScope.launch {
            val quote = Quote(
                novelId = novel.id,
                novelTitle = novel.title,
                chapterId = chapter.id,
                chapterTitle = chapter.titleTranslated ?: chapter.titleRaw,
                highlightedText = highlightedText.trim(),
                rawContext = rawContext?.trim(),
                userNote = userNote?.trim()?.ifBlank { null },
                colorHex = colorHex
            )
            repository.insertQuote(quote)
            showSnackbar("Saved to Quotes & Highlights!")
        }
    }

    fun deleteQuote(quoteId: Long) {
        viewModelScope.launch {
            repository.deleteQuote(quoteId)
            showSnackbar("Quote deleted")
        }
    }
}
