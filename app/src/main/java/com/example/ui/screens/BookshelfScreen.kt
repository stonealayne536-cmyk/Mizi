package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Novel
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.ui.viewmodel.NovelViewModel
import com.example.util.PinyinHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.preferences.isDarkModeFlow.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }

    val filteredNovels = remember(novels, searchQuery) {
        if (searchQuery.isBlank()) novels
        else novels.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            (it.titleTranslated?.contains(searchQuery, ignoreCase = true) == true) ||
            it.author.contains(searchQuery, ignoreCase = true) ||
            (it.authorPinyin?.contains(searchQuery, ignoreCase = true) == true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier.testTag("bookshelf_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_mizi_leaf),
                                    contentDescription = "Mizi Logo",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Mizi",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "AI Chinese Novel Library & Translator",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Dark / Light Mode Toggle
                    IconButton(
                        onClick = { viewModel.preferences.setDarkMode(!isDarkMode) },
                        modifier = Modifier.testTag("dark_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Glossary tab
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Glossary(null)) },
                        modifier = Modifier.testTag("glossary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Glossary Terminology",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Quotes tab
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Quotes) },
                        modifier = Modifier.testTag("quotes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = "Quotes & Highlights",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Settings tab
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & AI Key",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.AddLink, contentDescription = null) },
                text = { Text("Translate Novel Link", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_novel_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search & Quick Stats
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search English or Chinese title, author, pinyin...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field")
            )

            // Banner when loading/importing
            AnimatedVisibility(visible = isImporting) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Analyzing link and fetching novel chapters...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (filteredNovels.isEmpty()) {
                // Empty Library State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No novels match '$searchQuery'" else "Your Bookshelf is Empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Add a novel link from any Chinese web novel site (69shu, biquge, etc.) or choose from our featured raw classics!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("empty_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Chinese Novel")
                        }
                    }
                }
            } else {
                // Bookshelf Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNovels, key = { it.id }) { novel ->
                        NovelCard(
                            novel = novel,
                            onOpenDetail = { viewModel.openNovelDetail(novel.id) },
                            onContinueReading = {
                                viewModel.continueReadingNovel(novel)
                            },
                            onDelete = {
                                novelToDelete = novel
                            },
                            onOpenGlossary = {
                                viewModel.navigateTo(Screen.Glossary(novel.id))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNovelDialog(
            isImporting = isImporting,
            importError = importError,
            onDismiss = { showAddDialog = false },
            onImportLink = { url ->
                viewModel.importNovelFromUrl(url) { novelId, targetChapterId ->
                    showAddDialog = false
                    if (targetChapterId > 0L) {
                        viewModel.openReader(novelId, targetChapterId)
                    } else {
                        viewModel.openNovelDetail(novelId)
                    }
                }
            },
            onAddManual = { title, author, desc, chTitle, chContent, url ->
                viewModel.addManualNovel(title, author, desc, chTitle, chContent, url)
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog from Bookshelf
    if (novelToDelete != null) {
        val targetNovel = novelToDelete!!
        AlertDialog(
            onDismissRequest = { novelToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete Novel from Library?")
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${targetNovel.titleTranslated ?: targetNovel.title}' from your library? All cached chapters and translation data will be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = targetNovel.id
                        novelToDelete = null
                        viewModel.deleteNovel(id, navigateBackIfDetail = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NovelCard(
    novel: Novel,
    onOpenDetail: () -> Unit,
    onContinueReading: () -> Unit,
    onDelete: () -> Unit,
    onOpenGlossary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Palette gradients: Aesthetic, rich, playful colors
    val gradientPalettes = listOf(
        listOf(Color(0xFF1B4332), Color(0xFF081C15)), // Celestial Jade
        listOf(Color(0xFF6B1724), Color(0xFF38040E)), // Imperial Vermilion
        listOf(Color(0xFF240046), Color(0xFF10002B)), // Mystic Amethyst
        listOf(Color(0xFF003049), Color(0xFF001B2E)), // Ocean Azure
        listOf(Color(0xFF5A2A0C), Color(0xFF261002)), // Sunset Amber
        listOf(Color(0xFF1E293B), Color(0xFF0F172A)), // Midnight Slate
        listOf(Color(0xFF4A1525), Color(0xFF22050E))  // Lotus Plum
    )

    val gradientColors = gradientPalettes[novel.coverGradientIndex % gradientPalettes.size]

    val displayTitle = novel.titleTranslated ?: PinyinHelper.translateNovelTitle(novel.title)
    val displayAuthor = novel.authorPinyin ?: PinyinHelper.formatAuthorWithPinyin(novel.author)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() }
            .testTag("novel_card_${novel.id}")
    ) {
        Column {
            // Aesthetic Rich Book Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .background(Brush.verticalGradient(colors = gradientColors))
                    .padding(10.dp)
            ) {
                // Chinese Character Watermark Art
                val firstChar = novel.title.firstOrNull()?.toString() ?: "书"
                Text(
                    text = firstChar,
                    fontSize = 80.sp,
                    color = Color.White.copy(alpha = 0.08f),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Top row: Genre Tag & Card Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AmberGold.copy(alpha = 0.95f)
                    ) {
                        Text(
                            text = novel.genre.split("/").first().trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkBlack,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 3-dots action menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = ParchmentLight.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Details & Chapters") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenDetail()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Glossary Terms") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenGlossary()
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Novel", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }

                // Bottom title block on cover
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = ParchmentLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            lineHeight = 19.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ParchmentLight.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Serif
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Author's Pinyin Pen Name
                Text(
                    text = displayAuthor,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${novel.totalChapters} Chs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (novel.lastReadChapterIndex > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Read Ch. ${novel.lastReadChapterIndex}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onContinueReading,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("read_button_${novel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (novel.lastReadChapterIndex > 0) "Continue" else "Read",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun AddNovelDialog(
    isImporting: Boolean,
    importError: String?,
    onDismiss: () -> Unit,
    onImportLink: (String) -> Unit,
    onAddManual: (String, String, String, String, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableStateOf(0) }
    var urlInput by remember { mutableStateOf("") }

    val handleDismiss = {
        focusManager.clearFocus(force = true)
        if (!isImporting) onDismiss()
    }

    // Manual input states
    var manualTitle by remember { mutableStateOf("") }
    var manualAuthor by remember { mutableStateOf("") }
    var manualDesc by remember { mutableStateOf("") }
    var manualChTitle by remember { mutableStateOf("第一章 初始") }
    var manualChContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = handleDismiss,
        title = {
            Text(
                text = "Add Chinese Web Novel",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Novel Link", maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Featured Classics", maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Paste Raw", maxLines = 1) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Link Tab
                        Text(
                            text = "Paste any Chinese novel chapter or catalog link. The app will open the exact chapter and automatically load the next chapters as you read:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("https://www.69shu.com/txt/...") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            singleLine = true,
                            enabled = !isImporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("novel_url_input")
                        )

                        // Quick sample link pills
                        Text("Popular link examples:", style = MaterialTheme.typography.labelSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SuggestionChip(
                                onClick = { urlInput = "https://www.69shu.com/txt/1042.htm" },
                                label = { Text("69shu", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { urlInput = "https://www.biquge.tv/0_1/" },
                                label = { Text("biquge", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { urlInput = "https://www.uukanshu.com/b/4352/" },
                                label = { Text("uukanshu", fontSize = 11.sp) }
                            )
                        }

                        if (importError != null) {
                            Text(
                                text = "Notice: $importError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    1 -> {
                        // Featured Classics
                        Text(
                            text = "Select an authentic raw Chinese novel to import with real chapters:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddManual(
                                        "诡秘之主",
                                        "爱潜水的乌贼",
                                        "Steam, clockwork, potion, tarot cards, sealed artifacts. Zhou Mingrui awakens in the body of Klein Moretti in an alternate Victorian-era world filled with supernatural mysteries.",
                                        "第一章 绯红",
                                        """痛！
好痛！
头好痛！
眩晕和撕裂般的痛苦让周明瑞在迷茫中缓缓苏醒。
他下意识地伸手摸向自己的后脑，指尖触碰到的是一片黏稠冰凉的液体——血！
空气中弥漫着淡淡的煤气味与陈旧书卷混合的气息。他强撑着坐起身，眼前的景象让他瞳孔骤然收缩：
这是一间充满维多利亚时代风格的复古书房。泛黄的羊皮纸散落在橡木书桌上，旁边摆放着一支精致的羽毛笔，以及一把闪烁着冷冽金属光泽的左轮手枪！
窗外，一轮硕大而诡异的深红之月高高悬挂于漆黑夜空之中，散发着令人心悸的幽暗微光。
“我……穿越了？”周明瑞喃喃自语。""",
                                        "https://book.qidian.com/info/1010868264/"
                                    )
                                }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Lord of the Mysteries (诡秘之主)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Author: Ai Qian Shui De Wu Zei (爱潜水的乌贼)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddManual(
                                        "天官赐福",
                                        "墨香铜臭",
                                        "Eight hundred years ago, Xie Lian was the noble Crown Prince of Xianle. Ascended to Godhood thrice, he now returns to the heavens as a junk collector, crossing paths with the Ghost King Hua Cheng.",
                                        "第一章 太子进京",
                                        """为你，明灯三千；为你，花开满城；为你，所向披靡。
八百年前，仙乐国太子谢怜年少飞升，风光无限。
神武大街惊鸿一瞥，百鬼林中红伞漫步。谁曾想，三次飞升，三度贬谪，昔日金枝玉叶如今竟沦为三界笑柄。
“听说了吗？那个收破烂的武神，今天又飞升了！”
通天仙京金钟齐鸣，震动九霄。谢怜顶着一顶破斗笠，站在神武大殿前，微笑着向惊骇莫名的诸位神官拱了拱手：
“诸位仙僚，别来无恙。”""",
                                        "https://www.jjwxc.net/onebook.php?novelid=3200150"
                                    )
                                }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Heaven Official's Blessing (天官赐福)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Author: Mo Xiang Tong Xiu (墨香铜臭)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    2 -> {
                        // Paste Raw Text
                        OutlinedTextField(
                            value = manualTitle,
                            onValueChange = { manualTitle = it },
                            label = { Text("Novel Title (Raw Chinese)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualAuthor,
                            onValueChange = { manualAuthor = it },
                            label = { Text("Author (e.g. 忘语, 天蚕土豆)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualChTitle,
                            onValueChange = { manualChTitle = it },
                            label = { Text("Chapter 1 Title (Raw Chinese)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualChContent,
                            onValueChange = { manualChContent = it },
                            label = { Text("Chapter 1 Raw Chinese Content") },
                            minLines = 4,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (urlInput.isNotBlank()) {
                            onImportLink(urlInput.trim())
                        }
                    },
                    enabled = urlInput.isNotBlank() && !isImporting,
                    modifier = Modifier.testTag("submit_link_button")
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Fetching...")
                    } else {
                        Text("Import & Translate")
                    }
                }
            } else if (selectedTab == 2) {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (manualTitle.isNotBlank() && manualChContent.isNotBlank()) {
                            onAddManual(manualTitle, manualAuthor, manualDesc, manualChTitle, manualChContent, "")
                        }
                    },
                    enabled = manualTitle.isNotBlank() && manualChContent.isNotBlank()
                ) {
                    Text("Add Novel")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = handleDismiss,
                enabled = !isImporting
            ) {
                Text("Cancel")
            }
        }
    )
}
