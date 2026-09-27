package com.example.ui.navigation

sealed class Screen {
    object Bookshelf : Screen()
    data class NovelDetail(val novelId: Long) : Screen()
    data class Reader(val novelId: Long, val chapterId: Long) : Screen()
    data class Glossary(val novelId: Long? = null) : Screen()
    object Quotes : Screen()
    object Settings : Screen()
}
