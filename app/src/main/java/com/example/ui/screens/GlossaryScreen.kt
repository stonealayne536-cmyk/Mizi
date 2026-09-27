package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GlossaryTerm
import com.example.ui.theme.AmberGold
import com.example.ui.theme.JadePrimary
import com.example.ui.viewmodel.NovelViewModel
import com.example.util.GlossaryParseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(
    novelId: Long?,
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val allTerms by viewModel.globalGlossary.collectAsStateWithLifecycle()
    val isImportingGlossary by viewModel.isImportingGlossary.collectAsStateWithLifecycle()
    val importSummary by viewModel.glossaryImportResult.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importGlossaryFromUri(context, uri, novelId)
        }
    }

    val categories = listOf("All", "Cultivation Realm", "Technique", "Item", "Character", "Faction", "Location", "General")

    val filteredTerms = remember(allTerms, searchQuery, selectedCategory, novelId) {
        allTerms.filter { term ->
            (novelId == null || term.novelId == null || term.novelId == novelId) &&
            (selectedCategory == "All" || term.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
             term.rawTerm.contains(searchQuery, ignoreCase = true) ||
             term.translatedTerm.contains(searchQuery, ignoreCase = true) ||
             (term.notes?.contains(searchQuery, ignoreCase = true) == true))
        }
    }

    Scaffold(
        modifier = modifier.testTag("glossary_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Glossary & Terminology",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${filteredTerms.size} terms registered for consistent AI translation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("glossary_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // One-Tap File Import: Supports TXT, JSON, PDF directly
                    IconButton(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/json",
                                    "application/pdf",
                                    "text/*",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier.testTag("upload_glossary_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Glossary File (.txt, .json, .pdf)")
                    }

                    // More Options (Paste manually / Export)
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("glossary_more_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import File (.txt, .json, .pdf)") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    filePickerLauncher.launch(
                                        arrayOf("text/plain", "application/json", "application/pdf", "text/*", "*/*")
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paste Text / Preset") },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showUploadDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export to Clipboard") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val text = allTerms.joinToString("\n") { "${it.rawTerm} = ${it.translatedTerm} [${it.category}]" }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Mizi Glossary", text))
                                    viewModel.showSnackbar("Glossary exported to clipboard!")
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Term") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_term_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Import Processing Banner
            if (isImportingGlossary) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Analyzing file & auto-arranging terms into translation reference...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Chinese raw term or translation...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("glossary_search_field")
            )

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            // Glossary Terms List
            if (filteredTerms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching terms found" else "No Glossary Terms",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Add terms or upload a glossary file to ensure proper translation of cultivation realms, character names, and magical items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAddDialog = true }) {
                                Text("Add Term")
                            }
                            OutlinedButton(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf("text/plain", "application/json", "application/pdf", "text/*", "*/*")
                                    )
                                }
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Import File")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTerms, key = { it.id }) { term ->
                        GlossaryCard(
                            term = term,
                            onDelete = { viewModel.deleteGlossaryTerm(term.id) }
                        )
                    }
                }
            }
        }
    }

    // Add Single Term Dialog
    if (showAddDialog) {
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var rawInput by remember { mutableStateOf("") }
        var transInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("Cultivation Realm") }
        var notesInput by remember { mutableStateOf("") }

        val handleDismiss = {
            focusManager.clearFocus(force = true)
            showAddDialog = false
        }

        AlertDialog(
            onDismissRequest = handleDismiss,
            title = { Text("Add Glossary Term") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = { Text("Raw Chinese (e.g. 金丹期)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = transInput,
                        onValueChange = { transInput = it },
                        label = { Text("English Translation (e.g. Golden Core Stage)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category (Realm, Technique, Item, Character)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Context / Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (rawInput.isNotBlank() && transInput.isNotBlank()) {
                            viewModel.addGlossaryTerm(rawInput, transInput, categoryInput, notesInput, novelId)
                            showAddDialog = false
                        }
                    },
                    enabled = rawInput.isNotBlank() && transInput.isNotBlank()
                ) {
                    Text("Add Term")
                }
            },
            dismissButton = {
                TextButton(onClick = handleDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // Upload / Import File Dialog
    if (showUploadDialog) {
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var fileContentInput by remember { mutableStateOf("") }

        val handleDismiss = {
            focusManager.clearFocus(force = true)
            showUploadDialog = false
        }

        AlertDialog(
            onDismissRequest = handleDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Upload Glossary File")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Paste or load glossary terms. Supported formats:\n• 'Raw = Translated' (one per line)\n• CSV: 'Raw, Translated, Category'\n• JSON array: [{\"raw\": \"...\", \"translated\": \"...\"}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = fileContentInput,
                        onValueChange = { fileContentInput = it },
                        placeholder = {
                            Text("金丹期 = Golden Core Stage\n储物袋 = Storage Pouch\n青云宗 = Azure Cloud Sect\n九叶灵芝 = Nine-Leaf Ganoderma")
                        },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_file_content_input")
                    )

                    // Quick Load Preset Button
                    OutlinedButton(
                        onClick = {
                            fileContentInput = """
练气期 = Qi Condensation Stage = Cultivation Realm
筑基期 = Foundation Establishment Stage = Cultivation Realm
结丹期 = Core Formation Stage = Cultivation Realm
元婴期 = Nascent Soul Stage = Cultivation Realm
化神期 = Soul Formation Stage = Cultivation Realm
返虚期 = Void Refinement Stage = Cultivation Realm
合体期 = Body Integration Stage = Cultivation Realm
大乘期 = Mahayana Stage = Cultivation Realm
渡劫期 = Tribulation Transcendence = Cultivation Realm
真仙 = True Immortal = Cultivation Realm
储物袋 = Storage Pouch = Item
飞剑 = Flying Sword = Item
灵石 = Spirit Stones = Item
丹药 = Medicinal Pill = Item
本命法宝 = Life-Bound Dharma Treasure = Item
乾坤戒 = Universe Ring = Item
天道 = Heavenly Dao = General
神识 = Divine Sense = Technique
剑气 = Sword Qi = Technique
御剑术 = Sword Kinesis Art = Technique
缩地成寸 = Shrinking Ground to an Inch = Technique
万剑归宗 = Ten Thousand Swords Return to the Sect = Technique
墨大夫 = Doctor Mo = Character
掌门 = Sect Master = Character
太上长老 = Supreme Elder = Character
""".trimIndent()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load 25+ Xianxia Terms Template")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        if (fileContentInput.isNotBlank()) {
                            viewModel.importGlossaryFile(fileContentInput, novelId)
                            showUploadDialog = false
                        }
                    },
                    enabled = fileContentInput.isNotBlank(),
                    modifier = Modifier.testTag("submit_upload_button")
                ) {
                    Text("Import Terms")
                }
            },
            dismissButton = {
                TextButton(onClick = handleDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto-Arrangement Summary & Confirmation Dialog
    if (importSummary != null) {
        val summary = importSummary!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissGlossaryImportResult() },
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Glossary Auto-Arranged",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Successfully extracted ${summary.totalParsed} terms from '${summary.fileName}' (${summary.fileType}) and structured them for translation:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (summary.realmCount > 0) Text("• Cultivation Realms: ${summary.realmCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.techniqueCount > 0) Text("• Techniques & Arts: ${summary.techniqueCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.itemCount > 0) Text("• Items & Artifacts: ${summary.itemCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.characterCount > 0) Text("• Characters & Titles: ${summary.characterCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.factionCount > 0) Text("• Sects & Factions: ${summary.factionCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.locationCount > 0) Text("• Locations & Realms: ${summary.locationCount}", style = MaterialTheme.typography.labelSmall)
                            if (summary.generalCount > 0) Text("• General Terms: ${summary.generalCount}", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "✓ Re-arranged by category and prioritized by term length so compound Chinese phrases are never broken during translation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissGlossaryImportResult() }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun GlossaryCard(
    term: GlossaryTerm,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = term.rawTerm,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = term.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = term.translatedTerm,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                if (!term.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = term.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete term",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
