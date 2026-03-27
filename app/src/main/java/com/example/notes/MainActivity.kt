package com.example.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notes.ui.theme.NotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotesTheme {
                TextEditorApp(intent)
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {}
    }
    return uri.lastPathSegment ?: "unknown_file"
}

fun getReadablePath(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.path ?: uri.toString()
    if (uri.scheme != "content") return uri.toString()

    try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if (docId.contains(":")) {
                val split = docId.split(":")
                val type = split[0]
                val path = if (split.size > 1) split[1] else ""
                if ("primary".equals(type, ignoreCase = true)) {
                    return "/storage/emulated/0/$path".replace("//", "/")
                } else if ("com.android.externalstorage.documents" == uri.authority) {
                    return "/storage/$type/$path".replace("//", "/")
                }
            }
        }
    } catch (e: Exception) {}

    val uriString = uri.toString()
    if (uriString.contains("primary%3A")) {
        val subPath = Uri.decode(uriString.substringAfter("primary%3A").substringBefore("?"))
        return "/storage/emulated/0/$subPath".replace("//", "/")
    }
    
    val fileName = getFileName(context, uri)
    val authority = uri.authority ?: ""
    val decodedUri = Uri.decode(uriString)
    return when {
        decodedUri.contains("/storage/emulated/0/") -> {
            "/storage/emulated/0/" + decodedUri.substringAfter("/storage/emulated/0/").substringBefore("?")
        }
        authority.contains("externalstorage") -> "/storage/emulated/0/$fileName"
        authority.contains("downloads") -> "/storage/emulated/0/Download/$fileName"
        else -> "/storage/emulated/0/$fileName"
    }
}

@Stable
class SearchVisualTransformation(
    private val query: String,
    private val results: List<IntRange>,
    private val currentIndex: Int
) : VisualTransformation {
    companion object {
        val normalStyle = SpanStyle(background = Color(0xFF666600), color = Color.Black)
        val currentStyle = SpanStyle(background = Color(0xFFFFCC00), color = Color.Black)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        if (query.isEmpty() || results.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val annotatedString = buildAnnotatedString {
            append(text.text)
            val textLength = text.length
            for (i in results.indices) {
                val range = results[i]
                val start = range.first
                val end = range.last + 1
                if (start >= textLength) break
                val actualEnd = if (end > textLength) textLength else end
                if (start < actualEnd) {
                    addStyle(
                        style = if (i == currentIndex) currentStyle else normalStyle,
                        start = start,
                        end = actualEnd
                    )
                }
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

@Stable
class TextLayoutHolder {
    var value: TextLayoutResult? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorContent(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    visualTransformation: VisualTransformation,
    onTextLayout: (TextLayoutResult) -> Unit,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester
) {
    val textStyle = remember { TextStyle(color = Color.White, fontSize = 18.sp) }
    val cursorBrush = remember { SolidColor(Color.White) }
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester),
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        onTextLayout = onTextLayout,
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(16.dp)) {
                if (value.text.isEmpty()) {
                    Text("Start typing...", color = Color.Gray, fontSize = 18.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveToggle: () -> Unit,
    resultsCount: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search...", color = Color.Gray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black,
                cursorColor = Color.White
            )
        )
        IconButton(onClick = onCaseSensitiveToggle) {
            Icon(Icons.Default.TextFields, "Case Sensitive", tint = if (caseSensitive) Color(0xFFFFCC00) else Color.White)
        }
        if (resultsCount > 0) {
            Text("${currentIndex + 1}/$resultsCount", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
        }
        IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = Color.White) }
        IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowDown, "Next", tint = Color.White) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close Search", tint = Color.White) }
    }
}

@Composable
fun ActionToolbar(
    onHistoryClick: () -> Unit,
    onNewFileClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    hasSelection: Boolean,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSearchToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFF111111))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-6).dp)
    ) {
        IconButton(onClick = onHistoryClick) {
            Icon(Icons.Default.History, "Recent Files", tint = Color.White)
        }
        IconButton(onClick = onNewFileClick) {
            Icon(Icons.AutoMirrored.Filled.NoteAdd, "New File", tint = Color.White)
        }
        IconButton(onClick = onOpenFileClick) {
            Icon(Icons.Default.FileOpen, "Open File", tint = Color.White)
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Color.White else Color.Gray)
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) Color.White else Color.Gray)
        }
        IconButton(onClick = onCut, enabled = hasSelection) {
            Icon(Icons.Default.ContentCut, "Cut", tint = if (hasSelection) Color.White else Color.Gray)
        }
        IconButton(onClick = onCopy, enabled = hasSelection) {
            Icon(Icons.Default.ContentCopy, "Copy", tint = if (hasSelection) Color.White else Color.Gray)
        }
        IconButton(onClick = onPaste) {
            Icon(Icons.Default.ContentPaste, "Paste", tint = Color.White)
        }
        IconButton(onClick = onSearchToggle) {
            Icon(Icons.Default.Search, "Search", tint = Color.White)
        }
    }
}

@Composable
fun RecentFilesDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    recentFiles: List<Uri>,
    onFileClick: (Uri) -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(Color.DarkGray)
    ) {
        if (recentFiles.isEmpty()) {
            DropdownMenuItem(text = { Text("No recent files", color = Color.White) }, onClick = onDismiss)
        }
        recentFiles.forEach { uri ->
            val path = remember(uri) { getReadablePath(context, uri) }
            DropdownMenuItem(
                text = { Text(path, color = Color.White, fontSize = 14.sp) },
                onClick = { onFileClick(uri); onDismiss() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TextEditorApp(intent: Intent? = null) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val textLayoutHolder = remember { TextLayoutHolder() }
    val editorScrollState = rememberScrollState()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    val recentFiles = remember { mutableStateListOf<Uri>() }
    var historyExpanded by remember { mutableStateOf(false) }
    var hasBackedUpForCurrentSession by remember { mutableStateOf(false) }

    var lastSnapshotText by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var searchIsVisible by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(listOf<IntRange>()) }
    var currentSearchIndex by remember { mutableIntStateOf(-1) }
    var searchCaseSensitive by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("recent_files", Context.MODE_PRIVATE) }
    val density = LocalDensity.current

    val canUndo by remember { derivedStateOf { undoStack.isNotEmpty() } }
    val canRedo by remember { derivedStateOf { redoStack.isNotEmpty() } }
    val hasSelection by remember { derivedStateOf { !textFieldValue.selection.collapsed } }

    fun scrollToResult(index: Int) {
        if (index in searchResults.indices) {
            val range = searchResults[index]
            textFieldValue = textFieldValue.copy(selection = TextRange(range.first, range.last + 1))
            
            textLayoutHolder.value?.let { layout ->
                scope.launch {
                    try {
                        val rect = layout.getBoundingBox(range.first)
                        val margin = with(density) { 64.dp.toPx() }
                        bringIntoViewRequester.bringIntoView(
                            Rect(rect.left, rect.top - margin, rect.right, rect.bottom + margin)
                        )
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun updateRecentPrefs(newList: List<Uri>) {
        prefs.edit { putString("uris_ordered", newList.joinToString("|")) }
    }

    fun addToRecent(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {}
        
        if (recentFiles.firstOrNull() == uri) return
        
        recentFiles.remove(uri)
        recentFiles.add(0, uri)
        if (recentFiles.size > 10) recentFiles.removeAt(recentFiles.size - 1)
        updateRecentPrefs(recentFiles)
    }

    fun saveFile(uri: Uri, content: String) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasBackedUpForCurrentSession) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val oldContent = inputStream.readBytes()
                            val fileName = getFileName(context, uri)
                            val backupName = "$fileName~"
                            val backupFile = if (uri.scheme == "file") {
                                File(File(uri.path!!).parent, backupName)
                            } else {
                                File(context.filesDir, backupName)
                            }
                            backupFile.writeBytes(oldContent)
                            withContext(Dispatchers.Main) { hasBackedUpForCurrentSession = true }
                        }
                    } catch (e: Exception) {}
                }
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                    withContext(Dispatchers.Main) { isDirty = false }
                }
            } catch (e: Exception) {}
        }
    }

    fun openFile(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                textFieldValue = TextFieldValue(content)
                lastSnapshotText = content
                currentUri = uri
                isDirty = false
                hasBackedUpForCurrentSession = false
                undoStack.clear()
                redoStack.clear()
                addToRecent(uri)
            }
        } catch (e: Exception) {
            recentFiles.remove(uri)
            updateRecentPrefs(recentFiles)
        }
    }

    LaunchedEffect(Unit) {
        val savedString = prefs.getString("uris_ordered", "") ?: ""
        val uris = if (savedString.isNotEmpty()) savedString.split("|").map { it.toUri() } else emptyList()
        recentFiles.clear()
        recentFiles.addAll(uris)
        
        val incomingUri = intent?.data
        if (incomingUri != null) {
            openFile(incomingUri)
        } else {
            recentFiles.firstOrNull()?.let { openFile(it) }
        }
        
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(textFieldValue.text, currentUri) {
        if (isDirty && currentUri != null) {
            delay(5000)
            saveFile(currentUri!!, textFieldValue.text)
        }
    }

    LaunchedEffect(textFieldValue.text, searchQuery, searchCaseSensitive, searchIsVisible) {
        if (searchIsVisible && searchQuery.isNotEmpty()) {
            delay(1500)
            val textToSearch = textFieldValue.text
            val query = searchQuery
            val caseSensitive = searchCaseSensitive
            
            val results = withContext(Dispatchers.Default) {
                val res = mutableListOf<IntRange>()
                var index = textToSearch.indexOf(query, 0, ignoreCase = !caseSensitive)
                while (index >= 0 && res.size < 500) {
                    res.add(index until index + query.length)
                    index = textToSearch.indexOf(query, index + 1, ignoreCase = !caseSensitive)
                }
                res
            }
            searchResults = results
            if (currentSearchIndex !in results.indices) {
                currentSearchIndex = if (results.isNotEmpty()) 0 else -1
            }
        } else {
            if (searchResults.isNotEmpty()) searchResults = emptyList()
            if (currentSearchIndex != -1) currentSearchIndex = -1
        }
    }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text == lastSnapshotText) return@LaunchedEffect
        delay(2000)
        undoStack.add(lastSnapshotText)
        if (undoStack.size > 50) undoStack.removeAt(0)
        lastSnapshotText = textFieldValue.text
    }

    val isKeyboardVisible = WindowInsets.isImeVisible

    // Keep cursor in view when typing, moving selection, or when keyboard appears
    LaunchedEffect(textFieldValue.selection, textFieldValue.text, isKeyboardVisible) {
        // Wait a bit for layout to update
        delay(50)
        val layout = textLayoutHolder.value ?: return@LaunchedEffect
        val offset = textFieldValue.selection.start.coerceIn(0, layout.layoutInput.text.length)
        
        val cursorRect = try { layout.getCursorRect(offset) } catch (e: Exception) { null } ?: return@LaunchedEffect
        
        // Account for 16dp padding in decoration box and add a generous margin
        val paddingPx = with(density) { 16.dp.toPx() }
        val marginPx = with(density) { 56.dp.toPx() }
        
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = cursorRect.left + paddingPx,
                top = cursorRect.top + paddingPx - marginPx,
                right = cursorRect.right + paddingPx,
                bottom = cursorRect.bottom + paddingPx + marginPx
            )
        )
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(textFieldValue.text)
            textFieldValue = textFieldValue.copy(text = last)
            lastSnapshotText = last
            isDirty = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(textFieldValue.text)
            textFieldValue = textFieldValue.copy(text = next)
            lastSnapshotText = next
            isDirty = true
        }
    }

    fun copy() {
        val selection = textFieldValue.selection
        if (!selection.collapsed) {
            val selectedText = textFieldValue.text.substring(selection.start, selection.end)
            clipboardManager.setText(AnnotatedString(selectedText))
        }
    }

    fun cut() {
        val selection = textFieldValue.selection
        if (!selection.collapsed) {
            val selectedText = textFieldValue.text.substring(selection.start, selection.end)
            clipboardManager.setText(AnnotatedString(selectedText))
            val newText = textFieldValue.text.removeRange(selection.start, selection.end)
            textFieldValue = textFieldValue.copy(text = newText, selection = TextRange(selection.start))
            if (!isDirty) isDirty = true
        }
    }

    fun paste() {
        val clipboardText = clipboardManager.getText()?.text ?: ""
        if (clipboardText.isNotEmpty()) {
            val selection = textFieldValue.selection
            val newText = textFieldValue.text.replaceRange(selection.start, selection.end, clipboardText)
            textFieldValue = textFieldValue.copy(text = newText, selection = TextRange(selection.start + clipboardText.length))
            if (!isDirty) isDirty = true
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { openFile(it) } }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let {
        currentUri = it
        textFieldValue = TextFieldValue("")
        lastSnapshotText = ""
        saveFile(it, "")
        undoStack.clear()
        redoStack.clear()
        hasBackedUpForCurrentSession = true
        addToRecent(it)
    } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUri, textFieldValue.text, isDirty) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (isDirty && currentUri != null) saveFile(currentUri!!, textFieldValue.text)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onValueChange = remember {
        { newValue: TextFieldValue ->
            val old = textFieldValue
            textFieldValue = newValue
            if (newValue.text !== old.text) {
                if (newValue.text.length != old.text.length || newValue.text != old.text) {
                    if (redoStack.isNotEmpty()) redoStack.clear()
                    isDirty = true
                }
            }
        }
    }

    val onTextLayout = remember { { it: TextLayoutResult -> textLayoutHolder.value = it } }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
            ) {
                val visualTransformation = remember(searchQuery, searchResults, currentSearchIndex, searchIsVisible) {
                    if (searchIsVisible && searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
                        SearchVisualTransformation(searchQuery, searchResults, currentSearchIndex)
                    } else {
                        VisualTransformation.None
                    }
                }

                EditorContent(
                    value = textFieldValue,
                    onValueChange = onValueChange,
                    visualTransformation = visualTransformation,
                    onTextLayout = onTextLayout,
                    focusRequester = focusRequester,
                    bringIntoViewRequester = bringIntoViewRequester
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (searchIsVisible) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    caseSensitive = searchCaseSensitive,
                    onCaseSensitiveToggle = { searchCaseSensitive = !searchCaseSensitive },
                    resultsCount = searchResults.size,
                    currentIndex = currentSearchIndex,
                    onPrev = {
                        if (searchResults.isNotEmpty()) {
                            currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
                            scrollToResult(currentSearchIndex)
                        }
                    },
                    onNext = {
                        if (searchResults.isNotEmpty()) {
                            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                            scrollToResult(currentSearchIndex)
                        }
                    },
                    onClose = {
                        searchIsVisible = false; searchQuery = ""; searchResults = emptyList(); currentSearchIndex = -1
                    }
                )
            }

            Box {
                ActionToolbar(
                    onHistoryClick = { historyExpanded = true },
                    onNewFileClick = { createLauncher.launch("new_file.txt") },
                    onOpenFileClick = { openLauncher.launch(arrayOf("*/*")) },
                    canUndo = canUndo,
                    onUndo = { undo() },
                    canRedo = canRedo,
                    onRedo = { redo() },
                    hasSelection = hasSelection,
                    onCut = { cut() },
                    onCopy = { copy() },
                    onPaste = { paste() },
                    onSearchToggle = { searchIsVisible = !searchIsVisible }
                )
                
                RecentFilesDropdown(
                    expanded = historyExpanded,
                    onDismiss = { historyExpanded = false },
                    recentFiles = recentFiles,
                    onFileClick = { openFile(it) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
