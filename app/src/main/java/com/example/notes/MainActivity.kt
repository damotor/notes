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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

class SearchVisualTransformation(
    private val query: String,
    private val results: List<IntRange>,
    private val currentIndex: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            append(text.text)
            
            // Search highlights
            if (query.isNotEmpty()) {
                results.forEachIndexed { index, range ->
                    val start = range.first.coerceIn(0, text.length)
                    val end = (range.last + 1).coerceIn(0, text.length)
                    if (start < end) {
                        addStyle(
                            style = SpanStyle(
                                background = if (index == currentIndex) Color(0xFFFFCC00) else Color(0xFF666600),
                                color = Color.Black
                            ),
                            start = start,
                            end = end
                        )
                    }
                }
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
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
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val editorScrollState = rememberScrollState()

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var recentFiles by remember { mutableStateOf(listOf<Uri>()) }
    var historyExpanded by remember { mutableStateOf(false) }
    var hasBackedUpForCurrentSession by remember { mutableStateOf(false) }

    // Track last captured text for debounced undo
    var lastSnapshotText by remember { mutableStateOf("") }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchIsVisible by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(listOf<IntRange>()) }
    var currentSearchIndex by remember { mutableIntStateOf(-1) }
    var searchCaseSensitive by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("recent_files", Context.MODE_PRIVATE) }

    fun performSearch(query: String, text: String, caseSensitive: Boolean) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            currentSearchIndex = -1
            return
        }
        val results = mutableListOf<IntRange>()
        var index = text.indexOf(query, 0, ignoreCase = !caseSensitive)
        while (index >= 0) {
            results.add(index until index + query.length)
            index = text.indexOf(query, index + 1, ignoreCase = !caseSensitive)
        }
        searchResults = results
        currentSearchIndex = if (results.isNotEmpty()) 0 else -1
    }

    fun scrollToResult(index: Int) {
        if (index in searchResults.indices) {
            val range = searchResults[index]
            textFieldValue = textFieldValue.copy(selection = TextRange(range.first, range.last + 1))
            
            textLayoutResult?.let { layout ->
                scope.launch {
                    try {
                        val rect = layout.getBoundingBox(range.first)
                        bringIntoViewRequester.bringIntoView(
                            Rect(rect.left, rect.top - 50f, rect.right, rect.bottom + 50f)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun addToRecent(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val newList = (listOf(uri) + recentFiles.filter { it != uri }).take(10)
        recentFiles = newList
        prefs.edit { putString("uris_ordered", newList.joinToString("|")) }
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
                            withContext(Dispatchers.Main) {
                                hasBackedUpForCurrentSession = true
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                    withContext(Dispatchers.Main) {
                        isDirty = false
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun openFile(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                textFieldValue = TextFieldValue(content)
                lastSnapshotText = content
                currentUri = uri
                isDirty = false
                hasBackedUpForCurrentSession = false
                undoStack.clear()
                redoStack.clear()
                addToRecent(uri)
                if (searchIsVisible) performSearch(searchQuery, content, searchCaseSensitive)
            }
        } catch (e: Exception) {
            recentFiles = recentFiles.filter { it != uri }
            prefs.edit { putString("uris_ordered", recentFiles.joinToString("|")) }
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        val savedString = prefs.getString("uris_ordered", "") ?: ""
        val uris = if (savedString.isNotEmpty()) savedString.split("|").map { it.toUri() } else emptyList()
        recentFiles = uris
        
        val incomingUri = intent?.data
        if (incomingUri != null) {
            openFile(incomingUri)
        } else {
            uris.firstOrNull()?.let { lastUri ->
                openFile(lastUri)
            }
        }
        
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(textFieldValue.text, currentUri) {
        if (isDirty && currentUri != null) {
            delay(3000) // Debounce auto-save
            saveFile(currentUri!!, textFieldValue.text)
        }
    }

    // Debounce search matching
    LaunchedEffect(textFieldValue.text, searchQuery, searchCaseSensitive, searchIsVisible) {
        if (searchIsVisible && searchQuery.isNotEmpty()) {
            delay(300)
            performSearch(searchQuery, textFieldValue.text, searchCaseSensitive)
        } else if (!searchIsVisible || searchQuery.isEmpty()) {
            searchResults = emptyList()
            currentSearchIndex = -1
        }
    }

    // Debounce undo snapshots
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text == lastSnapshotText) return@LaunchedEffect
        
        delay(1000) // Wait for typing pause
        undoStack.add(lastSnapshotText)
        if (undoStack.size > 100) undoStack.removeAt(0)
        lastSnapshotText = textFieldValue.text
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
            isDirty = true
        }
    }

    fun paste() {
        val clipboardText = clipboardManager.getText()?.text ?: ""
        if (clipboardText.isNotEmpty()) {
            val selection = textFieldValue.selection
            val newText = textFieldValue.text.replaceRange(selection.start, selection.end, clipboardText)
            textFieldValue = textFieldValue.copy(text = newText, selection = TextRange(selection.start + clipboardText.length))
            isDirty = true
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

    val isKeyboardVisible = WindowInsets.isImeVisible
    val keyboardVisibleState by rememberUpdatedState(isKeyboardVisible)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f)
        ) {
            val boxConstraints = this.constraints
            val boxMaxHeight = this.maxHeight
            val visibleHeightPx by rememberUpdatedState(boxConstraints.maxHeight.toFloat())
            val density = LocalDensity.current
            val paddingPx = with(density) { 16.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.changedToUp() }) {
                                    if (!keyboardVisibleState) {
                                        scope.launch {
                                            delay(600)
                                            val layout = textLayoutResult ?: return@launch
                                            val offset = textFieldValue.selection.start
                                            if (offset <= layout.layoutInput.text.length) {
                                                val line = layout.getLineForOffset(offset)
                                                val lineTop = layout.getLineTop(line)
                                                val targetScroll = (lineTop + paddingPx) - (visibleHeightPx * 0.25f)
                                                editorScrollState.animateScrollTo(targetScroll.toInt().coerceAtLeast(0))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        val oldText = textFieldValue.text
                        textFieldValue = it
                        if (it.text != oldText) {
                            redoStack.clear()
                            isDirty = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = boxMaxHeight)
                        .focusRequester(focusRequester)
                        .onFocusChanged { }
                        .bringIntoViewRequester(bringIntoViewRequester),
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color.White),
                    onTextLayout = { textLayoutResult = it },
                    visualTransformation = SearchVisualTransformation(searchQuery, searchResults, currentSearchIndex),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(16.dp)) {
                            if (textFieldValue.text.isEmpty()) {
                                Text("Start typing...", color = Color.Gray, fontSize = 18.sp)
                            }
                            innerTextField()
                        }
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search...", color = Color.Gray) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black,
                            cursorColor = Color.White
                        )
                    )
                    IconButton(onClick = { searchCaseSensitive = !searchCaseSensitive }) {
                        Icon(Icons.Default.TextFields, "Case Sensitive", tint = if (searchCaseSensitive) Color(0xFFFFCC00) else Color.White)
                    }
                    if (searchResults.isNotEmpty()) {
                        Text("${currentSearchIndex + 1}/${searchResults.size}", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    IconButton(onClick = {
                        if (searchResults.isNotEmpty()) {
                            currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
                            scrollToResult(currentSearchIndex)
                        }
                    }) { Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = Color.White) }
                    IconButton(onClick = {
                        if (searchResults.isNotEmpty()) {
                            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                            scrollToResult(currentSearchIndex)
                        }
                    }) { Icon(Icons.Default.KeyboardArrowDown, "Next", tint = Color.White) }
                    IconButton(onClick = {
                        searchIsVisible = false; searchQuery = ""; searchResults = emptyList(); currentSearchIndex = -1
                    }) { Icon(Icons.Default.Close, "Close Search", tint = Color.White) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFF111111))
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                Box {
                    IconButton(onClick = { historyExpanded = true }) {
                        Icon(Icons.Default.History, "Recent Files", tint = Color.White)
                    }
                    DropdownMenu(expanded = historyExpanded, onDismissRequest = { historyExpanded = false }, modifier = Modifier.background(Color.DarkGray)) {
                        if (recentFiles.isEmpty()) DropdownMenuItem(text = { Text("No recent files", color = Color.White) }, onClick = { historyExpanded = false })
                        recentFiles.forEach { uri ->
                            val fullPath = getReadablePath(context, uri)
                            DropdownMenuItem(text = { Text(fullPath, color = Color.White, fontSize = 14.sp) }, onClick = { openFile(uri); historyExpanded = false })
                        }
                    }
                }
                IconButton(onClick = { createLauncher.launch("new_file.txt") }) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, "New File", tint = Color.White)
                }
                IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FileOpen, "Open File", tint = Color.White)
                }
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Color.Gray)
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Color.Gray)
                }
                IconButton(onClick = { cut() }, enabled = !textFieldValue.selection.collapsed) {
                    Icon(Icons.Default.ContentCut, "Cut", tint = if (!textFieldValue.selection.collapsed) Color.White else Color.Gray)
                }
                IconButton(onClick = { copy() }, enabled = !textFieldValue.selection.collapsed) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = if (!textFieldValue.selection.collapsed) Color.White else Color.Gray)
                }
                IconButton(onClick = { paste() }) {
                    Icon(Icons.Default.ContentPaste, "Paste", tint = Color.White)
                }
                IconButton(onClick = { searchIsVisible = !searchIsVisible }) {
                    Icon(Icons.Default.Search, "Search", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
