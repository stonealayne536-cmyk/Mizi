package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Chapter
import com.example.data.model.GlossaryTerm
import com.example.data.model.Novel
import com.example.data.model.Quote
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY lastReadTimestamp DESC")
    fun getAllNovels(): Flow<List<Novel>>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getNovelById(id: Long): Novel?

    @Query("SELECT * FROM novels WHERE id = :id")
    fun getNovelFlow(id: Long): Flow<Novel?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: Novel): Long

    @Update
    suspend fun updateNovel(novel: Novel)

    @Delete
    suspend fun deleteNovel(novel: Novel)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteNovelById(id: Long)

    @Query("UPDATE novels SET lastReadChapterIndex = :chapterIndex, lastReadScrollOffset = :offset, lastReadTimestamp = :timestamp WHERE id = :novelId")
    suspend fun updateReadingProgress(novelId: Long, chapterIndex: Int, offset: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET totalChapters = :totalChapters WHERE id = :novelId")
    suspend fun updateTotalChapters(novelId: Long, totalChapters: Int)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex ASC")
    fun getChaptersByNovel(novelId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex ASC")
    suspend fun getChaptersByNovelSync(novelId: Long): List<Chapter>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapterByIndex(novelId: Long, index: Int): Chapter?

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): Chapter?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterFlow(id: Long): Flow<Chapter?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Update
    suspend fun updateChapter(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersByNovel(novelId: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId AND isTranslated = 1")
    fun getTranslatedCountFlow(novelId: Long): Flow<Int>
}

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_terms WHERE novelId IS NULL OR novelId = :novelId ORDER BY rawTerm ASC")
    fun getTermsForNovel(novelId: Long): Flow<List<GlossaryTerm>>

    @Query("SELECT * FROM glossary_terms WHERE novelId IS NULL OR novelId = :novelId ORDER BY rawTerm ASC")
    suspend fun getTermsForNovelSync(novelId: Long): List<GlossaryTerm>

    @Query("SELECT * FROM glossary_terms ORDER BY rawTerm ASC")
    fun getAllTerms(): Flow<List<GlossaryTerm>>

    @Query("SELECT * FROM glossary_terms ORDER BY rawTerm ASC")
    suspend fun getAllTermsSync(): List<GlossaryTerm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerm(term: GlossaryTerm): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerms(terms: List<GlossaryTerm>)

    @Delete
    suspend fun deleteTerm(term: GlossaryTerm)

    @Query("DELETE FROM glossary_terms WHERE id = :id")
    suspend fun deleteTermById(id: Long)

    @Query("DELETE FROM glossary_terms WHERE novelId = :novelId")
    suspend fun deleteTermsByNovel(novelId: Long)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun getAllQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quotes WHERE novelId = :novelId ORDER BY createdAt DESC")
    fun getQuotesForNovel(novelId: Long): Flow<List<Quote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: Quote): Long

    @Delete
    suspend fun deleteQuote(quote: Quote)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: Long)
}
