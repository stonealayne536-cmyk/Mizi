package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chapter
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.NovelViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    novelId: Long,
    chapterId: Long,
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val novel by viewModel.selectedNovel.collectAsStateWithLifecycle()
    val chapter by viewModel.activeChapter.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    val isTranslating by viewModel.isTranslating.collectAsStateWithLifecycle()
    val translationStatus by viewModel.translationStatus.collectAsStateWithLifecycle()

    val currentTheme by viewModel.preferences.readerThemeFlow.collectAsStateWithLifecycle()
    val currentFont by viewModel.preferences.readerFontFlow.collectAsStateWithLifecycle()
    val fontSize by viewModel.preferences.fontSizeFlow.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.preferences.lineSpacingFlow.collectAsStateWithLifecycle()
    val paragraphSpacing by viewModel.preferences.paragraphSpacingFlow.collectAsStateWithLifecycle()
    val readingMode by viewModel.preferences.readingModeFlow.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterPickerSheet by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Highlight / Quote Modal state
    var quoteSelectedText by remember { mutableStateOf<String?>(null) }
    var quoteSelectedRawContext by remember { mutableStateOf<String?>(null) }
    var showQuoteDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Determine typography font family
    val activeFontFamily = remember(currentFont) {
        when (currentFont) {
            ReaderFont.SERIF -> FontFamily.Serif
            ReaderFont.SANS_SERIF -> FontFamily.SansSerif
            ReaderFont.MONOSPACE -> FontFamily.Monospace
            ReaderFont.CURSIVE -> FontFamily.Cursive
        }
    }

    val readerBgColor = Color(currentTheme.backgroundColorHex)
    val readerTextColor = Color(currentTheme.textColorHex)
    val readerSurfaceColor = Color(currentTheme.surfaceColorHex)
    val readerAccentColor = Color(currentTheme.accentColorHex)

    // Current Chapter Index in novel
    val currentIndex = chapters.indexOfFirst { it.id == chapter?.id }
    val hasPrev = currentIndex > 0
    val hasNext = (currentIndex in 0 until chapters.size - 1) || !chapter?.nextChapterUrl.isNullOrBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBgColor)
            .testTag("reader_screen")
    ) {
        if (chapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = readerAccentColor)
            }
        } else {
            val curChapter = chapter!!

            // Split into paragraphs for comfortable reading & highlighting
            val rawParagraphs = remember(curChapter.contentRaw) {
                curChapter.contentRaw.split("\n\n", "\n").filter { it.isNotBlank() }
            }
            val translatedParagraphs = remember(curChapter.contentTranslated) {
                curChapter.contentTranslated?.split("\n\n", "\n")?.filter { it.isNotBlank() } ?: emptyList()
            }

            // Reader Scrollable Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(
                    top = if (showControls) 80.dp else 40.dp,
                    bottom = if (showControls) 120.dp else 60.dp
                )
            ) {
                // Chapter Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val novelDisplayTitle = novel?.titleTranslated ?: (if (!novel?.title.isNullOrBlank()) com.example.util.PinyinHelper.translateNovelTitle(novel!!.title) else "")
                        Text(
                            text = novelDisplayTitle,
                            style = TextStyle(
                                color = readerTextColor.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = activeFontFamily,
                                fontWeight = FontWeight.Normal
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        val displayTitle = when (readingMode) {
                            ReadingMode.RAW_CHINESE -> curChapter.titleRaw
                            ReadingMode.TRANSLATED_ONLY -> curChapter.titleTranslated ?: curChapter.titleRaw
                            ReadingMode.DUAL_VIEW -> curChapter.titleTranslated ?: curChapter.titleRaw
                        }

                        Text(
                            text = displayTitle,
                            style = TextStyle(
                                color = readerTextColor,
                                fontSize = (fontSize + 6).sp,
                                fontFamily = activeFontFamily,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (fontSize + 12).sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        if (readingMode == ReadingMode.DUAL_VIEW && curChapter.titleTranslated != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = curChapter.titleRaw,
                                style = TextStyle(
                                    color = readerTextColor.copy(alpha = 0.5f),
                                    fontSize = (fontSize - 2).sp,
                                    fontFamily = activeFontFamily
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(1.dp)
                                .background(readerAccentColor.copy(alpha = 0.4f))
                        )
                    }
                }

                // Translation status card if untranslated
                if (!curChapter.isTranslated && readingMode != ReadingMode.RAW_CHINESE) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = readerSurfaceColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "This chapter is in raw Chinese.",
                                    style = TextStyle(color = readerTextColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                )
                                Button(
                                    onClick = { viewModel.translateActiveChapter(forceReTranslate = true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = readerAccentColor,
                                        contentColor = readerBgColor
                                    ),
                                    modifier = Modifier.testTag("translate_chapter_inline_button")
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Translate to English Now")
                                }
                            }
                        }
                    }
                }

                // Body Paragraphs
                when (readingMode) {
                    ReadingMode.TRANSLATED_ONLY -> {
                        val paragraphs = if (curChapter.isTranslated && translatedParagraphs.isNotEmpty()) {
                             translatedParagraphs
                        } else {
                            rawParagraphs
                        }

                        itemsIndexed(paragraphs) { index, para ->
                            ParagraphItem(
                                paragraphText = para,
                                rawText = rawParagraphs.getOrNull(index),
                                fontFamily = activeFontFamily,
                                textColor = readerTextColor,
                                fontSize = fontSize,
                                lineSpacing = lineSpacing,
                                paragraphSpacing = paragraphSpacing,
                                onQuoteClick = { selected, raw ->
                                    quoteSelectedText = selected
                                    quoteSelectedRawContext = raw
                                    showQuoteDialog = true
                                }
                            )
                        }
                    }

                    ReadingMode.RAW_CHINESE -> {
                        itemsIndexed(rawParagraphs) { index, para ->
                            ParagraphItem(
                                paragraphText = para,
                                rawText = para,
                                fontFamily = activeFontFamily,
                                textColor = readerTextColor,
                                fontSize = fontSize,
                                lineSpacing = lineSpacing,
                                paragraphSpacing = paragraphSpacing,
                                onQuoteClick = { selected, raw ->
                                    quoteSelectedText = selected
                                    quoteSelectedRawContext = raw
                                    showQuoteDialog = true
                                }
                            )
                        }
                    }

                    ReadingMode.DUAL_VIEW -> {
                        val maxCount = maxOf(rawParagraphs.size, translatedParagraphs.size)
                        items(maxCount) { index ->
                            val raw = rawParagraphs.getOrNull(index) ?: ""
                            val trans = translatedParagraphs.getOrNull(index) ?: raw

                            DualParagraphItem(
                                rawText = raw,
                                translatedText = trans,
                                fontFamily = activeFontFamily,
                                textColor = readerTextColor,
                                accentColor = readerAccentColor,
                                surfaceColor = readerSurfaceColor,
                                fontSize = fontSize,
                                lineSpacing = lineSpacing,
                                paragraphSpacing = paragraphSpacing,
                                onQuoteClick = { selected, rawText ->
                                    quoteSelectedText = selected
                                    quoteSelectedRawContext = rawText
                                    showQuoteDialog = true
                                }
                            )
                        }
                    }
                }

                // Chapter End Navigation Footer
                item {
                    Spacer(Modifier.height(32.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "— End of Chapter ${curChapter.chapterIndex} —",
                            style = TextStyle(
                                color = readerTextColor.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = activeFontFamily
                            )
                        )

                        // Sleek minimal < > navigation arrows only with live pre-translate status
                        val nextChapter = if (currentIndex in 0 until chapters.size - 1) chapters[currentIndex + 1] else null
                        val isNextReady = nextChapter?.isTranslated == true

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.prevChapter() },
                                enabled = hasPrev,
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = readerSurfaceColor,
                                    contentColor = readerTextColor
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = "Previous Chapter",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            if (hasNext) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isNextReady) readerAccentColor.copy(alpha = 0.12f) else readerSurfaceColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isNextReady) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = readerAccentColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Next Chapter Ready",
                                                style = TextStyle(
                                                    color = readerAccentColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = readerTextColor.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = "Preparing Next...",
                                                style = TextStyle(
                                                    color = readerTextColor.copy(alpha = 0.6f),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { viewModel.nextChapter() },
                                enabled = hasNext,
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = readerAccentColor.copy(alpha = 0.2f),
                                    contentColor = readerAccentColor
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = "Next Chapter",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top Bar Overlay (Animated)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = readerSurfaceColor.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("reader_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = readerTextColor)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = curChapter.titleTranslated ?: curChapter.titleRaw,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = readerTextColor,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val topNovelTitle = novel?.titleTranslated ?: (if (!novel?.title.isNullOrBlank()) com.example.util.PinyinHelper.translateNovelTitle(novel!!.title) else "")
                            Text(
                                text = "Ch. ${curChapter.chapterIndex} of ${chapters.size} • $topNovelTitle",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = readerTextColor.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Translate Chapter Button
                        IconButton(
                            onClick = { viewModel.translateActiveChapter(forceReTranslate = true) },
                            enabled = !isTranslating,
                            modifier = Modifier.testTag("reader_translate_button")
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = readerAccentColor
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Translate Chapter",
                                    tint = readerAccentColor
                                )
                            }
                        }

                        // Reader Appearance Settings
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("reader_settings_button")
                        ) {
                            Icon(Icons.Default.FormatSize, contentDescription = "Format & Themes", tint = readerTextColor)
                        }
                    }
                }
            }

            // Bottom Bar Overlay (Animated)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = readerSurfaceColor.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Reading Mode Selector Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = readingMode == ReadingMode.TRANSLATED_ONLY,
                                    onClick = { viewModel.preferences.setReadingMode(ReadingMode.TRANSLATED_ONLY) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                                ) {
                                    Text("English", fontSize = 12.sp)
                                }
                                SegmentedButton(
                                    selected = readingMode == ReadingMode.DUAL_VIEW,
                                    onClick = { viewModel.preferences.setReadingMode(ReadingMode.DUAL_VIEW) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                                ) {
                                    Text("Dual", fontSize = 12.sp)
                                }
                                SegmentedButton(
                                    selected = readingMode == ReadingMode.RAW_CHINESE,
                                    onClick = { viewModel.preferences.setReadingMode(ReadingMode.RAW_CHINESE) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                                ) {
                                    Text("Raw CN", fontSize = 12.sp)
                                }
                            }
                        }

                        // Chapter Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { viewModel.prevChapter() },
                                enabled = hasPrev,
                                modifier = Modifier.testTag("reader_prev_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Chapter", tint = readerTextColor)
                            }

                            // Chapter selector button
                            TextButton(
                                onClick = { showChapterPickerSheet = true },
                                modifier = Modifier.testTag("chapter_list_drawer_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = readerTextColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Chapter ${curChapter.chapterIndex} / ${chapters.size}",
                                    style = TextStyle(color = readerTextColor, fontWeight = FontWeight.Bold)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.nextChapter() },
                                enabled = hasNext,
                                modifier = Modifier.testTag("reader_next_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Chapter", tint = readerTextColor)
                            }
                        }
                    }
                }
            }
        }
    }

    // Formatting & Theme Customization Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus(force = true)
                showSettingsSheet = false
            },
            containerColor = readerSurfaceColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Reader Styling & Themes",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = readerTextColor,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Theme Presets Swatches
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reading Theme", style = TextStyle(color = readerTextColor, fontSize = 13.sp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReaderTheme.values().forEach { theme ->
                            val isSelected = theme == currentTheme
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(theme.backgroundColorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) readerAccentColor else Color.Gray.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.preferences.setReaderTheme(theme) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "文",
                                    color = Color(theme.textColorHex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Font Family Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Font Style", style = TextStyle(color = readerTextColor, fontSize = 13.sp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderFont.values().forEach { font ->
                            val isSelected = font == currentFont
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.preferences.setReaderFont(font) },
                                label = { Text(font.displayName.split(" ").first()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = readerAccentColor,
                                    selectedLabelColor = readerBgColor
                                )
                            )
                        }
                    }
                }

                // Font Size Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = TextStyle(color = readerTextColor, fontSize = 13.sp))
                        Text("${fontSize.toInt()} sp", style = TextStyle(color = readerAccentColor, fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = { viewModel.preferences.setFontSize(it) },
                        valueRange = 14f..28f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = readerAccentColor,
                            activeTrackColor = readerAccentColor
                        )
                    )
                }

                // Line Height Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Line Spacing", style = TextStyle(color = readerTextColor, fontSize = 13.sp))
                        Text(String.format("%.2fx", lineSpacing), style = TextStyle(color = readerAccentColor, fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = lineSpacing,
                        onValueChange = { viewModel.preferences.setLineSpacing(it) },
                        valueRange = 1.2f..2.2f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = readerAccentColor,
                            activeTrackColor = readerAccentColor
                        )
                    )
                }

                // Paragraph Spacing Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paragraph Spacing", style = TextStyle(color = readerTextColor, fontSize = 13.sp))
                        Text("${paragraphSpacing.toInt()} dp", style = TextStyle(color = readerAccentColor, fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = paragraphSpacing,
                        onValueChange = { viewModel.preferences.setParagraphSpacing(it) },
                        valueRange = 4f..36f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = readerAccentColor,
                            activeTrackColor = readerAccentColor
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Chapter Picker Bottom Sheet
    if (showChapterPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus(force = true)
                showChapterPickerSheet = false
            },
            containerColor = readerSurfaceColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Jump to Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = readerTextColor,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(chapters) { idx, ch ->
                        val isSelected = ch.id == chapter?.id
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Ch. ${ch.chapterIndex}: ${ch.titleTranslated ?: ch.titleRaw}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) readerAccentColor else readerTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                if (ch.isTranslated) {
                                    Text("Translated", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                                } else {
                                    Text("Raw", color = AmberGoldDark, fontSize = 11.sp)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showChapterPickerSheet = false
                                    viewModel.openReader(novelId, ch.id)
                                }
                        )
                        HorizontalDivider(color = readerTextColor.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }

    // Quote & Highlight Modal
    if (showQuoteDialog && quoteSelectedText != null) {
        HighlightQuoteDialog(
            initialText = quoteSelectedText!!,
            rawContext = quoteSelectedRawContext,
            onDismiss = { showQuoteDialog = false },
            onSave = { text, note, colorHex ->
                viewModel.addQuote(text, quoteSelectedRawContext, note, colorHex)
                showQuoteDialog = false
            }
        )
    }
}

@Composable
fun ParagraphItem(
    paragraphText: String,
    rawText: String?,
    fontFamily: FontFamily,
    textColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    paragraphSpacing: Float = 16f,
    onQuoteClick: (String, String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = paragraphSpacing.dp)
    ) {
        Column {
            Text(
                text = paragraphText,
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    lineHeight = (fontSize * lineSpacing).sp,
                    letterSpacing = 0.3.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onQuoteClick(paragraphText, rawText)
                    }
            )
        }
    }
}

@Composable
fun DualParagraphItem(
    rawText: String,
    translatedText: String,
    fontFamily: FontFamily,
    textColor: Color,
    accentColor: Color,
    surfaceColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    paragraphSpacing: Float = 16f,
    onQuoteClick: (String, String?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = paragraphSpacing.dp)
            .clickable {
                onQuoteClick(translatedText, rawText)
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Raw Chinese
            Text(
                text = rawText,
                style = TextStyle(
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = (fontSize - 2).sp,
                    fontFamily = fontFamily,
                    lineHeight = ((fontSize - 2) * 1.4f).sp
                )
            )

            Spacer(Modifier.height(6.dp))

            // Translated English
            Text(
                text = translatedText,
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Normal,
                    lineHeight = (fontSize * lineSpacing).sp
                )
            )
        }
    }
}

@Composable
fun HighlightQuoteDialog(
    initialText: String,
    rawContext: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var text by remember { mutableStateOf(initialText) }
    var userNote by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFFFFD166) }

    val handleDismiss = {
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    val handleSave = {
        focusManager.clearFocus(force = true)
        if (text.isNotBlank()) {
            onSave(text, userNote, selectedColor)
        }
    }

    val colors = listOf(
        0xFFFFD166 to "Amber Gold",
        0xFF06D6A0 to "Jade Green",
        0xFFE76F51 to "Vermilion",
        0xFF118AB2 to "Azure Blue",
        0xFFB5838D to "Lotus Violet"
    )

    AlertDialog(
        onDismissRequest = handleDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FormatQuote, contentDescription = null, tint = Color(selectedColor))
                Text("Highlight & Save Quote")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quote Text:",
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!rawContext.isNullOrBlank()) {
                    Text(
                        text = "Original Chinese: $rawContext",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Highlight Color:",
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { (colorHex, _) ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                Text(
                    text = "Personal Reflection / Note (Optional):",
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = userNote,
                    onValueChange = { userNote = it },
                    placeholder = { Text("e.g. Significant quote, cultivator technique notes...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = handleSave,
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(selectedColor))
            ) {
                Text("Save Quote", color = InkBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = handleDismiss) {
                Text("Cancel")
            }
        }
    )
}
