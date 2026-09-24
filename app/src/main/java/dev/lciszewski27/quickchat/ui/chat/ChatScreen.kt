package dev.lciszewski27.quickchat.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.domain.model.AiModelInfo
import dev.lciszewski27.quickchat.domain.model.ChatMessage
import dev.lciszewski27.quickchat.domain.model.ChatRole
import dev.lciszewski27.quickchat.domain.model.ChatSession
import dev.lciszewski27.quickchat.domain.model.ToolCallRecord
import dev.lciszewski27.quickchat.ui.components.MarkdownText
import dev.lciszewski27.quickchat.ui.theme.QuickSpacing
import kotlinx.coroutines.launch

/**
 * UI contract
 * - Primary task: ask + read answer. Primary action: Send (filled).
 * - Hierarchy: app bar (history, model, settings) → messages → input.
 * - Adaptive: compact = modal drawer; ≥840dp = permanent history pane.
 * - States: no-key CTA, empty chat, sending, error w/ retry, delete confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var showModelSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var pendingRename by remember { mutableStateOf<ChatSession?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val usePermanentDrawer = maxWidth >= 840.dp
        val drawerContent: @Composable () -> Unit = {
            HistoryPane(
                sessions = state.sessions,
                selectedId = state.currentSessionId,
                query = state.searchQuery,
                onQuery = { onEvent(ChatUiEvent.OnSearchChange(it)) },
                onSelect = {
                    onEvent(ChatUiEvent.SelectSession(it))
                    if (!usePermanentDrawer) scope.launch { drawerState.close() }
                },
                onNewChat = {
                    onEvent(ChatUiEvent.NewChat)
                    if (!usePermanentDrawer) scope.launch { drawerState.close() }
                },
                onDelete = { pendingDelete = it },
                onRename = { pendingRename = it },
                onOpenSettings = onOpenSettings
            )
        }

        if (usePermanentDrawer) {
            Row(Modifier.fillMaxSize()) {
                PermanentDrawerSheet(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) { drawerContent() }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ChatScaffold(
                        state = state,
                        onEvent = onEvent,
                        onOpenSettings = onOpenSettings,
                        onOpenDrawer = {},
                        showDrawerButton = false,
                        onShowModels = { showModelSheet = true },
                        snackbar = snackbar
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        drawerContent()
                    }
                }
            ) {
                ChatScaffold(
                    state = state,
                    onEvent = onEvent,
                    onOpenSettings = onOpenSettings,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    showDrawerButton = true,
                    onShowModels = { showModelSheet = true },
                    snackbar = snackbar
                )
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            selectedProviderId = state.selectedProviderId,
            selectedModelId = state.selectedModelId,
            models = state.availableModels,
            providerLabels = state.providerLabels,
            onSelect = {
                onEvent(ChatUiEvent.SelectModel(it.providerId, it.id))
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false },
            onManageModels = {
                showModelSheet = false
                onOpenSettings()
            }
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("“${session.title}” and its messages will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(ChatUiEvent.DeleteSession(session.id))
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    pendingRename?.let { session ->
        var name by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(ChatUiEvent.RenameSession(session.id, name))
                        pendingRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScaffold(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    showDrawerButton: Boolean,
    onShowModels: () -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showDrawerButton) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open chat history")
                        }
                    }
                },
                title = {
                    Column(
                        modifier = Modifier.clickable(onClick = onShowModels).padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.selectedModelId.ifBlank { "Select model" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Change model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = state.providerDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ChatUiEvent.NewChat) }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            AnimatedVisibility(visible = state.isSending, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.hasApiKey) {
                NoKeyBanner(onOpenSettings = onOpenSettings)
            }
            // A stream started in another session keeps generating in the background;
            // only render its live text when viewing that session.
            val streamHere = state.streamingSessionId != null &&
                state.streamingSessionId == state.currentSessionId
            if (state.messages.isEmpty() && !(streamHere && state.streamingText != null)) {
                EmptyChat(
                    hasKey = state.hasApiKey,
                    modelName = state.selectedModelId,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            } else {
                MessageList(
                    messages = state.messages,
                    toolCalls = state.toolCalls,
                    isSending = state.isSending && streamHere,
                    streamingText = if (streamHere) state.streamingText else null,
                    runningToolName = if (streamHere) state.runningTool?.displayName else null,
                    streamingTps = if (streamHere) state.streamingTps else null,
                    onRetry = { onEvent(ChatUiEvent.Retry) },
                    modifier = Modifier.weight(1f)
                )
            }
            InputBar(
                input = state.input,
                isSending = state.isSending && streamHere,
                onInput = { onEvent(ChatUiEvent.OnInputChange(it)) },
                onSend = { onEvent(ChatUiEvent.Send) },
                onStop = { onEvent(ChatUiEvent.Stop) }
            )
        }
    }
}

@Composable
private fun HistoryPane(
    sessions: List<ChatSession>,
    selectedId: String?,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (ChatSession) -> Unit,
    onRename: (ChatSession) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "QuickChat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search chats") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        val filtered = if (query.isBlank()) sessions else sessions.filter {
            it.title.contains(query, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(QuickSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(QuickSpacing.sm))
                Text(
                    if (sessions.isEmpty()) "No conversations yet" else "No matches",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (sessions.isEmpty()) "Start a new chat to see history here."
                    else "Try a different search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered, key = { it.id }) { session ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                session.title.ifBlank { "Untitled" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        },
                        badge = {
                            Row {
                                IconButton(
                                    onClick = { onRename(session) }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDelete(session) }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        selected = session.id == selectedId,
                        onClick = { onSelect(session.id) },
                        shape = MaterialTheme.shapes.large,
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun NoKeyBanner(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect Google AI Studio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Add your API key to start chatting.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpenSettings) { Text("Set up") }
        }
    }
}

@Composable
private fun EmptyChat(
    hasKey: Boolean,
    modelName: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(QuickSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(QuickSpacing.xl))
        Text(
            "Ask anything",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(QuickSpacing.xs))
        Text(
            if (hasKey) "Chatting with $modelName. Messages stay on this device except what you send to the provider."
            else "Add your API key, pick a favorite model, and start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = QuickSpacing.md)
        )
        if (!hasKey) {
            Spacer(Modifier.height(QuickSpacing.md))
            androidx.compose.material3.FilledTonalButton(onClick = onOpenSettings) {
                Text("Open providers")
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    toolCalls: List<ToolCallRecord>,
    isSending: Boolean,
    streamingText: String?,
    runningToolName: String?,
    streamingTps: Float?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One chronological timeline: tool cards slot in where they executed
    // (between the user message and the reply they served).
    val entries = remember(messages, toolCalls) {
        (messages.map { TimelineEntry.Msg(it) } + toolCalls.map { TimelineEntry.Tool(it) })
            .sortedBy { it.timestamp }
    }
    val listState = rememberLazyListState()
    // Persisted messages arrive rarely — animate to them as before.
    // Tool arrivals are deliberately excluded: they land mid-stream and must
    // not restart an animation (the snap effect below already follows them).
    LaunchedEffect(messages.size, isSending) {
        if (entries.isNotEmpty() || streamingText != null) {
            listState.animateScrollToItem((entries.lastIndex + 1).coerceAtLeast(0))
        }
    }
    // Stream chunks arrive many times per second: restarting an animated
    // scroll on each one never settles and makes the message flicker.
    // Instead snap instantly and keep the fresh bottom edge on screen, so
    // the view travels with the generated text for the whole stream.
    LaunchedEffect(streamingText?.length) {
        if (streamingText == null) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total == 0) return@LaunchedEffect
        listState.scrollToItem((total - 1).coerceAtLeast(0))
        // scrollToItem pins the item's TOP, but the bubble grows downward —
        // scroll forward past the overflow so the newest text stays visible.
        val after = listState.layoutInfo
        val item = after.visibleItemsInfo.find { it.index == total - 1 }
            ?: return@LaunchedEffect
        val viewportEnd = after.viewportSize.height - after.afterContentPadding
        val overflow = (item.offset + item.size) - viewportEnd
        if (overflow > 0) listState.scroll { scrollBy(overflow.toFloat()) }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            entries,
            key = { it.key },
            contentType = { if (it is TimelineEntry.Msg) "msg" else "tool" }
        ) { entry ->
            when (entry) {
                is TimelineEntry.Msg -> MessageBubble(message = entry.message, onRetry = onRetry)
                is TimelineEntry.Tool -> ToolCallCard(record = entry.record)
            }
        }
        // Live stream bubble: same geometry as a persisted model message, so
        // the layout doesn't jump when the stream is saved to history.
        if (streamingText != null || isSending) {
            item(key = "streaming") {
                StreamingBubble(
                    text = streamingText.orEmpty(),
                    runningToolName = runningToolName,
                    tps = streamingTps
                )
            }
        }
    }
}

private sealed interface TimelineEntry {
    val timestamp: Long
    val key: String

    data class Msg(val message: ChatMessage) : TimelineEntry {
        override val timestamp: Long get() = message.timestamp
        override val key: String get() = "m:${message.id}"
    }

    data class Tool(val record: ToolCallRecord) : TimelineEntry {
        override val timestamp: Long get() = record.timestamp
        override val key: String get() = "t:${record.id}"
    }
}

@Composable
private fun ToolCallCard(record: ToolCallRecord) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    val failed = record.result.startsWith("Error")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = record.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (failed) "Failed • ${formatDuration(record.durationMs)}"
                        else "${formatDuration(record.durationMs)} • tap for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SelectionContainer {
                        Column {
                            Text(
                                "Arguments",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = record.argsJson.ifBlank { "{}" }.take(2000),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Result",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = record.result.take(4000),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "${ms}ms" else "${ms / 1000}.${(ms % 1000) / 100}s"

@Composable
private fun StreamingBubble(text: String, runningToolName: String?, tps: Float?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (text.isEmpty() && runningToolName == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    if (text.isNotEmpty()) {
                        // Plain text while streaming: re-parsing markdown on every
                        // chunk visibly flashes the whole bubble. Full markdown is
                        // applied once, when the completed reply is persisted.
                        Text(text = text + "▍", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (runningToolName != null) {
                        if (text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Running $runningToolName…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (tps != null) {
                        Spacer(Modifier.height(4.dp))
                        TpsCaption(text = formatTps(tps))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
    val isUser = message.role == ChatRole.USER
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            ) {
                SelectionContainer {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    } else {
                        ModelMessageContent(
                            text = message.text,
                            genCaption = genCaptionFor(message),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            // Copy stays on model replies only — user messages need no actions.
            // Copies the visible reply (thought blocks excluded).
            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.isError) {
                        Text(
                            "Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(stripThoughts(message.text))) }
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy message",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model reply body: markdown text with `<thought>` blocks collapsed into
 * expandable sections (same bubble, no extra containers). Replies without
 * tags render exactly as before. An optional generation-speed caption closes
 * the bubble when stats were recorded.
 */
@Composable
private fun ModelMessageContent(
    text: String,
    genCaption: String?,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { splitThoughts(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    if (segment.text.isNotBlank()) {
                        MarkdownText(text = segment.text)
                    }
                }
                is MessageSegment.Thought -> ThoughtSection(text = segment.text)
            }
        }
        if (genCaption != null) {
            TpsCaption(text = genCaption)
        }
    }
}

/** Small right-aligned speed caption at the bottom of a bubble. */
@Composable
private fun TpsCaption(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun genCaptionFor(message: ChatMessage): String? {
    if (message.role != ChatRole.MODEL || message.isError) return null
    val avg = computeTps(message.genTokens, message.genMs) ?: return null
    return "${formatTps(avg)} avg"
}

@Composable
private fun ThoughtSection(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Thought process",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide thought process" else "Show thought process",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(2.dp)
                    )
                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
            ) {
                MarkdownText(text = text)
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isSending: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Message… (Enter for a new line)") },
                shape = MaterialTheme.shapes.extraLarge,
                // Plain multiline: Enter inserts a newline, the arrow button sends.
                keyboardOptions = KeyboardOptions.Default,
                maxLines = 6,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (isSending) {
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop generating")
                }
            } else {
                FilledIconButton(
                    onClick = {
                        onSend()
                        keyboard?.hide()
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    selectedProviderId: String,
    selectedModelId: String,
    models: List<AiModelInfo>,
    providerLabels: Map<String, String>,
    onSelect: (AiModelInfo) -> Unit,
    onDismiss: () -> Unit,
    onManageModels: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Favorite models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Only favorites are listed here — catalogs like OpenRouter's are too big to scroll. " +
                    "Star models in Settings → Models & Favorites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (models.isEmpty()) {
                Text(
                    "No favorites yet. Fetch a catalog in Settings and tap ★ on the models you use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Preserve first-seen provider order for stable grouping.
                val order = models.map { it.providerId }.distinct()
                order.forEach { providerId ->
                    Text(
                        providerLabels[providerId] ?: providerId,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    models.filter { it.providerId == providerId }.forEach { m ->
                        ModelRow(
                            model = m,
                            selected = m.providerId == selectedProviderId && m.id == selectedModelId,
                            onClick = { onSelect(m) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onManageModels, modifier = Modifier.fillMaxWidth()) {
                Text("Manage models & favorites")
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: AiModelInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName.ifBlank { model.id },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Text(
                    "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
