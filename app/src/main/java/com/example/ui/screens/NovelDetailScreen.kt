package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chapter
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.ui.viewmodel.NovelViewModel
import com.example.util.PinyinHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    novelId: Long,
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val novel by viewModel.selectedNovel.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchTranslateProgress.collectAsStateWithLifecycle()

    var showAddChapterDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.testTag("novel_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = novel?.title ?: "Novel Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Glossary(novelId)) },
                        modifier = Modifier.testTag("detail_glossary_button")
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = "Glossary")
                    }
                    IconButton(
                        onClick = { showPromptDialog = true },
                        modifier = Modifier.testTag("detail_prompt_button")
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = "Custom AI Prompt")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Novel")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val target = chapters.getOrNull((novel?.lastReadChapterIndex ?: 1) - 1) ?: chapters.firstOrNull()
                    if (target != null) {
                        viewModel.openReader(novelId, target.id)
                    }
                },
                icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                text = {
                    Text(
                        if ((novel?.lastReadChapterIndex ?: 0) > 0) "Continue Ch. ${novel?.lastReadChapterIndex}" else "Start Reading",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("detail_continue_fab")
            )
        }
    ) { innerPadding ->
        if (novel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val curNovel = novel!!
            val displayTitle = curNovel.titleTranslated ?: PinyinHelper.translateNovelTitle(curNovel.title)
            val displayAuthor = curNovel.authorPinyin ?: PinyinHelper.formatAuthorWithPinyin(curNovel.author)

            val gradientPalettes = listOf(
                listOf(Color(0xFF1B4332), Color(0xFF081C15)),
                listOf(Color(0xFF6B1724), Color(0xFF38040E)),
                listOf(Color(0xFF240046), Color(0xFF10002B)),
                listOf(Color(0xFF003049), Color(0xFF001B2E)),
                listOf(Color(0xFF5A2A0C), Color(0xFF261002)),
                listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                listOf(Color(0xFF4A1525), Color(0xFF22050E))
            )
            val gradientColors = gradientPalettes[curNovel.coverGradientIndex % gradientPalettes.size]

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                item {
                    // Header Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(colors = gradientColors))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AmberGold
                                ) {
                                    Text(
                                        text = curNovel.genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkBlack,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "By $displayAuthor",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ParchmentLight.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = ParchmentLight,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Text(
                                text = curNovel.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ParchmentLight.copy(alpha = 0.75f),
                                    fontFamily = FontFamily.Serif
                                )
                            )

                            if (curNovel.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = curNovel.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ParchmentLight.copy(alpha = 0.75f),
                                    lineHeight = 18.sp
                                )
                            }

                            if (curNovel.sourceUrl.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = null,
                                        tint = AmberGoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = curNovel.sourceUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGoldLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Quick Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(Screen.Glossary(novelId)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Glossary")
                        }
                        OutlinedButton(
                            onClick = { showAddChapterDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Chapter")
                        }
                    }
                }

                item {
                    val untranslatedCount = chapters.count { !it.isTranslated }
                    val isBatchActive = batchProgress != null && batchProgress?.novelId == curNovel.id && batchProgress?.isRunning == true

                    if (isBatchActive && batchProgress != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Downloading & Translating...",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.cancelBatchTranslation() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Stop")
                                    }
                                }

                                val progressFraction = if (batchProgress!!.total > 0) {
                                    batchProgress!!.current.toFloat() / batchProgress!!.total.toFloat()
                                } else 0f

                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = batchProgress!!.currentTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${batchProgress!!.current} / ${batchProgress!!.total}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else if (untranslatedCount > 0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Translate All for Offline Reading",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Translate remaining $untranslatedCount chapters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { viewModel.startBatchTranslation(novelId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Translate All")
                                }
                            }
                        }
                    } else if (chapters.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "All ${chapters.size} chapters are translated and ready for offline reading!",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    // Chapters Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val translatedCount = chapters.count { it.isTranslated }
                        Text(
                            text = "Chapters (${chapters.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$translatedCount / ${chapters.size} Translated",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Chapter List Items
                items(chapters, key = { it.id }) { chapter ->
                    ChapterListItem(
                        chapter = chapter,
                        isCurrent = chapter.chapterIndex == curNovel.lastReadChapterIndex,
                        onClick = {
                            viewModel.openReader(novelId, chapter.id)
                        },
                        onTranslateClick = if (!chapter.isTranslated) {
                            { viewModel.translateSingleChapter(chapter.id) }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddChapterDialog) {
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var rawTitle by remember { mutableStateOf("第${chapters.size + 1}章") }
        var rawContent by remember { mutableStateOf("") }

        val handleDismiss = {
            focusManager.clearFocus(force = true)
            showAddChapterDialog = false
        }

        AlertDialog(
            onDismissRequest = handleDismiss,
            title = { Text("Add Raw Chinese Chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rawTitle,
                        onValueChange = { rawTitle = it },
                        label = { Text("Chapter Title (Raw Chinese)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rawContent,
                        onValueChange = { rawContent = it },
                        label = { Text("Raw Chinese Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (rawContent.isNotBlank()) {
                            viewModel.addChapterToNovel(novelId, rawTitle, rawContent)
                            showAddChapterDialog = false
                        }
                    },
                    enabled = rawContent.isNotBlank()
                ) {
                    Text("Add Chapter")
                }
            },
            dismissButton = {
                TextButton(onClick = handleDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPromptDialog) {
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var customPrompt by remember { mutableStateOf(novel?.customSystemPrompt ?: "") }

        val handleDismiss = {
            focusManager.clearFocus(force = true)
            showPromptDialog = false
        }

        AlertDialog(
            onDismissRequest = handleDismiss,
            title = { Text("Novel System Instructions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize the AI translation tone, voice, and stylistic rules for this specific novel (e.g. historical wuxia tone, cultivation terminology, comedy style):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        placeholder = { Text("Leave blank to inherit global system instructions...") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        viewModel.updateNovelSystemPrompt(novelId, customPrompt)
                        showPromptDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = handleDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Novel?") },
            text = { Text("Are you sure you want to remove '${novel?.title}' and all its cached raw and translated chapters from your library?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteNovel(novelId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChapterListItem(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onTranslateClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("chapter_item_${chapter.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "Chapter ${chapter.chapterIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chapter.titleTranslated ?: chapter.titleRaw,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapter.titleTranslated != null && chapter.titleTranslated != chapter.titleRaw) {
                    Text(
                        text = chapter.titleRaw,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (chapter.isTranslated) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Translated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AmberGold.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AmberGoldDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Raw",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGoldDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onTranslateClick != null) {
                        FilledTonalIconButton(
                            onClick = onTranslateClick,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Translate Chapter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
