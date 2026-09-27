package com.example.data.network

import com.example.data.model.Chapter
import com.example.data.model.Novel
import com.example.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ScrapedNovelResult(
    val novel: Novel,
    val chapters: List<Chapter>,
    val targetChapterIndex: Int = 1
)

data class ScrapedChapterContent(
    val title: String,
    val content: String,
    val nextChapterUrl: String? = null,
    val catalogUrl: String? = null
)

class NovelWebScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun scrapeNovelFromUrl(url: String): Result<ScrapedNovelResult> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = url.trim()
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty response body"))
            val rawStringForCharset = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
            val detectedCharset = detectCharset(rawStringForCharset)
            val html = String(bytes, detectedCharset)

            // Check if this is a chapter page or a novel directory/index page
            val isChapterPage = isSingleChapterPage(html, trimmedUrl)

            if (isChapterPage) {
                val chapterData = parseSingleChapterHtml(html, trimmedUrl)
                val bookTitle = extractMetaOrTitle(html) ?: "Chinese Web Novel"
                val rawAuthor = extractAuthor(html) ?: "Unknown Author"
                val pinyinAuthor = PinyinHelper.formatAuthorWithPinyin(rawAuthor)
                val translatedTitle = PinyinHelper.translateNovelTitle(bookTitle)

                // Try to find catalog URL to fetch other chapters if available
                var chaptersList = mutableListOf<Chapter>()
                val firstChapter = Chapter(
                    novelId = 0,
                    chapterIndex = 1,
                    titleRaw = chapterData.title,
                    contentRaw = chapterData.content,
                    chapterUrl = trimmedUrl,
                    nextChapterUrl = chapterData.nextChapterUrl
                )
                chaptersList.add(firstChapter)

                // If catalog URL exists, attempt to scrape TOC
                if (chapterData.catalogUrl != null && chapterData.catalogUrl != trimmedUrl) {
                    try {
                        val tocResult = fetchCatalogChapters(chapterData.catalogUrl)
                        if (tocResult.isNotEmpty()) {
                            // Find where current chapter sits in TOC or replace
                            chaptersList = tocResult.toMutableList()
                            val matchIndex = chaptersList.indexOfFirst {
                                it.chapterUrl == trimmedUrl || it.titleRaw == chapterData.title
                            }
                            if (matchIndex != -1) {
                                chaptersList[matchIndex] = chaptersList[matchIndex].copy(
                                    contentRaw = chapterData.content,
                                    nextChapterUrl = chapterData.nextChapterUrl
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Keep single chapter with nextChapterUrl
                    }
                }

                val novel = Novel(
                    title = bookTitle,
                    titleTranslated = translatedTitle,
                    author = rawAuthor,
                    authorPinyin = pinyinAuthor,
                    coverGradientIndex = (bookTitle.hashCode() and 0x7FFFFFFF) % 7,
                    sourceUrl = trimmedUrl,
                    description = "Imported from chapter link: $trimmedUrl",
                    totalChapters = chaptersList.size,
                    lastReadChapterIndex = 1
                )

                return@withContext Result.success(ScrapedNovelResult(novel, chaptersList, targetChapterIndex = 1))
            } else {
                val novelTitle = extractNovelTitle(html) ?: extractMetaOrTitle(html) ?: "Imported Chinese Novel"
                val rawAuthor = extractAuthor(html) ?: "Web Author"
                val pinyinAuthor = PinyinHelper.formatAuthorWithPinyin(rawAuthor)
                val translatedTitle = PinyinHelper.translateNovelTitle(novelTitle)
                val description = extractDescription(html) ?: "Imported from web: $trimmedUrl"
                val parsedChapters = extractChapterLinks(html, trimmedUrl)

                val novel = Novel(
                    title = novelTitle,
                    titleTranslated = translatedTitle,
                    author = rawAuthor,
                    authorPinyin = pinyinAuthor,
                    coverGradientIndex = (novelTitle.hashCode() and 0x7FFFFFFF) % 7,
                    sourceUrl = trimmedUrl,
                    description = description,
                    totalChapters = parsedChapters.size,
                    lastReadChapterIndex = 1
                )
                return@withContext Result.success(ScrapedNovelResult(novel, parsedChapters, targetChapterIndex = 1))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchChapterContent(chapterUrl: String): Result<ScrapedChapterContent> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(chapterUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch chapter: HTTP ${response.code}"))
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty body"))
            val preview = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
            val charset = detectCharset(preview)
            val html = String(bytes, charset)

            val parsed = parseSingleChapterHtml(html, chapterUrl)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchCatalogChapters(catalogUrl: String): List<Chapter> {
        val request = Request.Builder()
            .url(catalogUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val bytes = response.body?.bytes() ?: return emptyList()
        val preview = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val charset = detectCharset(preview)
        val html = String(bytes, charset)
        return extractChapterLinks(html, catalogUrl)
    }

    private fun detectCharset(headerPreview: String): Charset {
        val lower = headerPreview.lowercase()
        return when {
            lower.contains("charset=gbk") -> Charset.forName("GBK")
            lower.contains("charset=gb2312") -> Charset.forName("GB2312")
            lower.contains("charset=big5") -> Charset.forName("Big5")
            else -> Charsets.UTF_8
        }
    }

    private fun isSingleChapterPage(html: String, url: String): Boolean {
        if (url.matches(Regex(".*[/_](\\d+)\\.html?.*"))) return true
        if (html.contains("id=\"content\"") || html.contains("class=\"content\"") || html.contains("id=\"chaptercontent\"")) {
            return true
        }
        return false
    }

    private fun parseSingleChapterHtml(html: String, currentUrl: String): ScrapedChapterContent {
        // Extract Title
        val h1Matcher = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE).matcher(html)
        val title = if (h1Matcher.find()) {
            cleanHtmlText(h1Matcher.group(1) ?: "")
        } else {
            extractMetaOrTitle(html) ?: "Chapter"
        }

        // Extract Content
        val contentRegex = Regex(
            "(?:id|class)=[\"'](?:content|chaptercontent|txtcontent|read-content|book-content)[\"'][^>]*>(.*?)</div>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = contentRegex.find(html)
        val rawBody = match?.groupValues?.get(1) ?: html

        // Clean paragraphs & remove excess navigation sites / advertisements
        val cleanContent = cleanChapterBody(rawBody)

        // Extract Next Chapter Link if available
        var nextUrl: String? = null
        val nextRegex = Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(?:下一章|下一页|Next|下一篇)</a>", RegexOption.IGNORE_CASE)
        val nextMatch = nextRegex.find(html)
        if (nextMatch != null) {
            val href = nextMatch.groupValues[1]
            if (!href.contains("javascript") && !href.endsWith("#")) {
                nextUrl = resolveUrl(currentUrl, href)
            }
        }

        // Extract Catalog / TOC Link if available
        var catalogUrl: String? = null
        val catalogRegex = Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(?:目录|返回目录|章节列表|Index|Catalog)</a>", RegexOption.IGNORE_CASE)
        val catalogMatch = catalogRegex.find(html)
        if (catalogMatch != null) {
            val href = catalogMatch.groupValues[1]
            if (!href.contains("javascript") && !href.endsWith("#")) {
                catalogUrl = resolveUrl(currentUrl, href)
            }
        }

        return ScrapedChapterContent(
            title = title,
            content = cleanContent,
            nextChapterUrl = nextUrl,
            catalogUrl = catalogUrl
        )
    }

    /**
     * Aggressively removes excess navigation sites, buttons, ads, domain names,
     * and promotional lines while preserving the chapter narrative and author notes.
     */
    fun cleanChapterBody(rawHtml: String): String {
        var text = rawHtml
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        val rawLines = text.lines()
        val cleanedLines = mutableListOf<String>()

        // Navigation and site junk blacklist patterns
        val junkPatterns = listOf(
            Regex(".*(?:上一章|下一章|上一页|下一页|返回目录|加入书签|加入书架|目录|书页).*"),
            Regex(".*(?:投票推荐|投推荐票|章节报错|返回书目|回到首页|快捷键).*"),
            Regex(".*(?:69书吧|69shu|笔趣阁|biquge|uukanshu|UU看书|起点中文网|顶点小说).*"),
            Regex(".*(?:请记住本书首发域名|天才一秒记住|最新章节|最快更新|无广告阅读|无弹窗).*"),
            Regex(".*(?:www\\.[a-zA-Z0-9-]+\\.[a-zA-Z]+|https?://[a-zA-Z0-9./-]+).*"),
            Regex(".*(?:本章未完，请点击下一页继续阅读|点击下一页继续阅读|网页版章节内容慢).*"),
            Regex(".*(?:手机用户请浏览|请收藏本站|防采集|最新网址).*"),
            Regex(".*(?:按回车|按键盘|Ctrl\\+D).*")
        )

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check if this line is an Author's Note (preserve it!)
            val isAuthorNote = trimmed.startsWith("PS:", ignoreCase = true) ||
                    trimmed.startsWith("ps：", ignoreCase = true) ||
                    trimmed.startsWith("作者有话要说") ||
                    trimmed.startsWith("作者的话") ||
                    trimmed.startsWith("【作者的话】") ||
                    trimmed.startsWith("后记：") ||
                    trimmed.startsWith("写在后面：")

            if (isAuthorNote) {
                cleanedLines.add("\n[Author's Note]: " + trimmed.removePrefix("PS:").removePrefix("ps：").removePrefix("作者有话要说：").removePrefix("作者的话：").trim())
                continue
            }

            // Test against junk blacklist
            var isJunk = false
            for (pattern in junkPatterns) {
                if (pattern.matches(trimmed)) {
                    isJunk = true
                    break
                }
            }

            // Exclude single words like "目录", "下一章"
            if (trimmed in listOf("目录", "上一章", "下一章", "上一页", "下一页", "书签", "首页")) {
                isJunk = true
            }

            if (!isJunk) {
                cleanedLines.add(trimmed)
            }
        }

        val result = cleanedLines.joinToString("\n\n").trim()
        return if (result.isNotBlank()) result else rawHtml.replace(Regex("<[^>]+>"), "").trim()
    }

    private fun extractChapterLinks(html: String, baseUrl: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)
        val matcher = linkPattern.matcher(html)
        var index = 1

        val seenUrls = mutableSetOf<String>()

        while (matcher.find()) {
            val href = matcher.group(1)?.trim() ?: ""
            val text = cleanHtmlText(matcher.group(2) ?: "").trim()

            // Filter for chapter-like titles (e.g. 第...章 or Chapter ... or non-trivial titles)
            val isChapterTitle = text.matches(Regex(".*第[0-9一二三四五六七八九十百千万]+[章节回集卷].*")) ||
                    text.matches(Regex(".*Chapter\\s*\\d+.*", RegexOption.IGNORE_CASE)) ||
                    (text.length in 4..60 && !text.contains("首页") && !text.contains("返回") && !text.contains("登录") && !text.contains("书架") && !text.contains("排行榜"))

            if (isChapterTitle && !href.startsWith("javascript") && !href.startsWith("#")) {
                val fullUrl = resolveUrl(baseUrl, href)
                if (fullUrl !in seenUrls) {
                    seenUrls.add(fullUrl)
                    chapters.add(
                        Chapter(
                            novelId = 0,
                            chapterIndex = index++,
                            titleRaw = text,
                            contentRaw = "Loading chapter content...",
                            chapterUrl = fullUrl
                        )
                    )
                }
            }
        }
        return chapters
    }

    private fun extractNovelTitle(html: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta\\s+property=[\"']og:novel:book_name[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val title = cleanHtmlText(matcher.group(1) ?: "").trim()
                if (title.isNotEmpty() && !title.contains("404")) return title
            }
        }
        return null
    }

    private fun extractAuthor(html: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta\\s+property=[\"']og:novel:author[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("作者[：: ]*(?:<[^>]+>)?([^<\\n]+)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val author = cleanHtmlText(matcher.group(1) ?: "").trim()
                if (author.isNotEmpty()) return author
            }
        }
        return null
    }

    private fun extractDescription(html: String): String? {
        val pattern = Pattern.compile("<meta\\s+property=[\"']og:description[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val desc = cleanHtmlText(matcher.group(1) ?: "").trim()
            if (desc.isNotEmpty()) return desc
        }
        return null
    }

    private fun extractMetaOrTitle(html: String): String? {
        val pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val t = cleanHtmlText(matcher.group(1) ?: "").trim()
            return t.split("_", "-", "|").firstOrNull()?.trim()
        }
        return null
    }

    private fun cleanHtmlText(text: String): String {
        return text.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }

    private fun resolveUrl(base: String, relative: String): String {
        return try {
            val baseUri = URI(base)
            baseUri.resolve(relative).toString()
        } catch (e: Exception) {
            if (relative.startsWith("http")) relative else "$base/$relative"
        }
    }
}
