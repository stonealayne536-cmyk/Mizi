package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.repository.PreferencesRepository
import com.example.ui.theme.AmberGold
import com.example.ui.theme.JadePrimary
import com.example.ui.viewmodel.NovelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val currentApiKey by viewModel.preferences.apiKeyFlow.collectAsStateWithLifecycle()
    val currentSystemPrompt by viewModel.preferences.systemPromptFlow.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.preferences.isDarkModeFlow.collectAsStateWithLifecycle()
    val defaultTheme by viewModel.preferences.readerThemeFlow.collectAsStateWithLifecycle()
    val defaultFont by viewModel.preferences.readerFontFlow.collectAsStateWithLifecycle()
    val autoTranslateNext by viewModel.preferences.autoTranslateNextFlow.collectAsStateWithLifecycle()
    val selectedModel by viewModel.preferences.geminiModelFlow.collectAsStateWithLifecycle()

    var apiKeyInput by remember(currentApiKey) { mutableStateOf(viewModel.preferences.getCustomApiKey()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var systemPromptInput by remember(currentSystemPrompt) { mutableStateOf(currentSystemPrompt) }
    var showUploadPromptDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Settings & AI Translation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI Translation & Gemini Key
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Gemini API Key",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Text(
                            text = "Add your Gemini API key to activate professional, published-book grade translation. Gemini AI will accurately localize cultivation terminology, intricate dialogue, idioms, poetry, and character voice into fluid, natural English prose with zero robotic phrasing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Status Badge
                        if (currentApiKey.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text("Active AI Key Loaded (Contextual Translation Unlocked)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AmberGold.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("No custom key set. Using offline rule & glossary translation engine.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = { Text("Paste AI Studio API Key...") },
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.preferences.setCustomApiKey(apiKeyInput.trim())
                                    viewModel.showSnackbar("API Key saved successfully!")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_key_button")
                            ) {
                                Text("Save Key")
                            }

                            if (apiKeyInput.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        apiKeyInput = ""
                                        viewModel.preferences.setCustomApiKey("")
                                        viewModel.showSnackbar("API Key cleared")
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }

            // Section 1.5: Gemini Model Selection (Pro vs Flash)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Gemini Model Engine",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Text(
                            text = "Choose your translation model. Gemini Pro delivers published literary fiction quality with deep comprehension of Xianxia idioms and poetic dialogue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option 1: Gemini 3.1 Pro (Recommended)
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedModel == PreferencesRepository.MODEL_PRO)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (selectedModel == PreferencesRepository.MODEL_PRO)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.preferences.setGeminiModel(PreferencesRepository.MODEL_PRO)
                                    viewModel.showSnackbar("Switched to Gemini 3.1 Pro (Published Caliber)")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = selectedModel == PreferencesRepository.MODEL_PRO,
                                    onClick = {
                                        viewModel.preferences.setGeminiModel(PreferencesRepository.MODEL_PRO)
                                        viewModel.showSnackbar("Switched to Gemini 3.1 Pro (Published Caliber)")
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Gemini 3.1 Pro",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "RECOMMENDED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Deep literary prose, poetic cadence, cultivation metaphysics, and nuanced character voices.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Option 2: Gemini 3.5 Flash
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedModel == PreferencesRepository.MODEL_FLASH)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (selectedModel == PreferencesRepository.MODEL_FLASH)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.preferences.setGeminiModel(PreferencesRepository.MODEL_FLASH)
                                    viewModel.showSnackbar("Switched to Gemini 3.5 Flash")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = selectedModel == PreferencesRepository.MODEL_FLASH,
                                    onClick = {
                                        viewModel.preferences.setGeminiModel(PreferencesRepository.MODEL_FLASH)
                                        viewModel.showSnackbar("Switched to Gemini 3.5 Flash")
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gemini 3.5 Flash",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Ultra-fast generation with light resource usage.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: System Translation Instructions ("System Command")
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "System Translation Instructions",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Text(
                            text = "Fine-tune how the AI learns, interprets tone, balances literal vs localization, and translates terms across the novel:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Style Presets
                        Text("Style Presets:", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    systemPromptInput = """
You are a master translator of Chinese Xianxia & Cultivation sagas.
Tone: Majestic, mystical, and reverent.
Rules:
1. Strict adherence to cultivation realms (Qi Condensation, Foundation, Golden Core, Nascent Soul).
2. Preserve grand martial names, formations, and artifacts with poetic English equivalents.
3. Keep Chinese honorifics like Shifu, Senior Martial Brother, Daoist Friend, Sect Master.
""".trimIndent()
                                },
                                label = { Text("Xianxia", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    systemPromptInput = """
You are a translator specializing in Classic Wuxia and Jianghu martial arts tales.
Tone: Atmospheric, gritty, honorable, and cinematic.
Rules:
1. Translate martial movements with dynamic vigor (e.g. palms, strikes, stances).
2. Maintain the tension between the righteous path and demonic factions.
3. Keep chivalric idioms and oath formulas intact.
""".trimIndent()
                                },
                                label = { Text("Wuxia", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    systemPromptInput = PreferencesRepository.DEFAULT_SYSTEM_PROMPT
                                },
                                label = { Text("Default", fontSize = 11.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = systemPromptInput,
                            onValueChange = { systemPromptInput = it },
                            minLines = 6,
                            maxLines = 10,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_prompt_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.preferences.setSystemPrompt(systemPromptInput)
                                    viewModel.showSnackbar("System instructions updated!")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Instructions")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.preferences.resetSystemPrompt()
                                    systemPromptInput = PreferencesRepository.DEFAULT_SYSTEM_PROMPT
                                    viewModel.showSnackbar("Reset to default system prompt")
                                }
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }

            // Section 3: Reading Preferences & Dark Mode
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Reading & Appearance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // Dark Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Dark Mode", fontWeight = FontWeight.Medium)
                                Text("Optimized for night reading and OLED screens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.preferences.setDarkMode(it) }
                            )
                        }

                        HorizontalDivider()

                        // Auto-translate Next Chapter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Auto-Translate Next Chapter", fontWeight = FontWeight.Medium)
                                Text("Translates as you navigate between chapters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoTranslateNext,
                                onCheckedChange = { viewModel.preferences.setAutoTranslateNext(it) }
                            )
                        }

                        HorizontalDivider()

                        // Default Reader Theme
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Default Reader Theme", fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ReaderTheme.values().forEach { theme ->
                                    val isSelected = theme == defaultTheme
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(theme.backgroundColorHex))
                                            .clickable { viewModel.preferences.setReaderTheme(theme) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(theme.accentColorHex), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Default Font
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Default Reader Font", fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReaderFont.values().forEach { font ->
                                    FilterChip(
                                        selected = font == defaultFont,
                                        onClick = { viewModel.preferences.setReaderFont(font) },
                                        label = { Text(font.displayName.split(" ").first()) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Offline Storage & Info
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Offline Access & Local Storage",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "All your raw novel chapters, AI translations, custom glossaries, and highlighted quotes are stored locally in the on-device Room database. You can read your translated library anytime, even with no internet connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
