package com.example.data.repository

import com.example.data.local.ChapterDao
import com.example.data.local.GlossaryDao
import com.example.data.local.NovelDao
import com.example.data.local.QuoteDao
import com.example.data.model.Chapter
import com.example.data.model.GlossaryTerm
import com.example.data.model.Novel
import com.example.data.model.Quote
import kotlinx.coroutines.flow.Flow

class NovelRepository(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val glossaryDao: GlossaryDao,
    private val quoteDao: QuoteDao
) {
    val allNovels: Flow<List<Novel>> = novelDao.getAllNovels()
    val allQuotes: Flow<List<Quote>> = quoteDao.getAllQuotes()
    val allGlossaryTerms: Flow<List<GlossaryTerm>> = glossaryDao.getAllTerms()

    fun getNovelFlow(novelId: Long): Flow<Novel?> = novelDao.getNovelFlow(novelId)
    suspend fun getNovelById(novelId: Long): Novel? = novelDao.getNovelById(novelId)
    suspend fun insertNovel(novel: Novel): Long = novelDao.insertNovel(novel)
    suspend fun updateNovel(novel: Novel) = novelDao.updateNovel(novel)
    suspend fun deleteNovelById(novelId: Long) {
        chapterDao.deleteChaptersByNovel(novelId)
        glossaryDao.deleteTermsByNovel(novelId)
        novelDao.deleteNovelById(novelId)
    }

    suspend fun updateReadingProgress(novelId: Long, chapterIndex: Int, offset: Int) {
        novelDao.updateReadingProgress(novelId, chapterIndex, offset)
    }

    fun getChaptersByNovel(novelId: Long): Flow<List<Chapter>> = chapterDao.getChaptersByNovel(novelId)
    suspend fun getChaptersByNovelSync(novelId: Long): List<Chapter> = chapterDao.getChaptersByNovelSync(novelId)
    suspend fun getChapterByIndex(novelId: Long, index: Int): Chapter? = chapterDao.getChapterByIndex(novelId, index)
    suspend fun getChapterById(chapterId: Long): Chapter? = chapterDao.getChapterById(chapterId)
    fun getChapterFlow(chapterId: Long): Flow<Chapter?> = chapterDao.getChapterFlow(chapterId)
    suspend fun insertChapter(chapter: Chapter): Long = chapterDao.insertChapter(chapter)
    suspend fun insertChapters(chapters: List<Chapter>) = chapterDao.insertChapters(chapters)
    suspend fun updateChapter(chapter: Chapter) = chapterDao.updateChapter(chapter)

    fun getTermsForNovel(novelId: Long): Flow<List<GlossaryTerm>> = glossaryDao.getTermsForNovel(novelId)
    suspend fun getTermsForNovelSync(novelId: Long): List<GlossaryTerm> = glossaryDao.getTermsForNovelSync(novelId)
    suspend fun insertGlossaryTerm(term: GlossaryTerm): Long = glossaryDao.insertTerm(term)
    suspend fun insertGlossaryTerms(terms: List<GlossaryTerm>) = glossaryDao.insertTerms(terms)
    suspend fun deleteGlossaryTerm(id: Long) = glossaryDao.deleteTermById(id)

    fun getQuotesForNovel(novelId: Long): Flow<List<Quote>> = quoteDao.getQuotesForNovel(novelId)
    suspend fun insertQuote(quote: Quote): Long = quoteDao.insertQuote(quote)
    suspend fun deleteQuote(id: Long) = quoteDao.deleteQuoteById(id)
}
