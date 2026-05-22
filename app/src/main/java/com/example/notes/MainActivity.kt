package com.example.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notes.ui.theme.NotesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NotesTheme { TextEditorApp(intent) } }
    }
}

@Stable
class EditorState(val context: Context, val scope: CoroutineScope) {
    var value by mutableStateOf(TextFieldValue(""))
    var uri by mutableStateOf<Uri?>(null)
    var isDirty by mutableStateOf(false)
    var hasBackedUp by mutableStateOf(false)
    val undoStack = mutableStateListOf<String>()
    val redoStack = mutableStateListOf<String>()
    var lastSnapshotText by mutableStateOf("")
    var openTrigger by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    var searchVisible by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<IntRange>>(emptyList())
    var searchIndex by mutableIntStateOf(-1)
    var searchCaseSensitive by mutableStateOf(false)

    val recentFiles = mutableStateListOf<Uri>()
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun onValueChange(nv: TextFieldValue) {
        if (nv.text != value.text) {
            redoStack.clear()
            isDirty = true
        }
        value = nv
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val last = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(value.text)
        value = value.copy(text = last, selection = TextRange(last.length))
        lastSnapshotText = last
        isDirty = true
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(value.text)
        value = value.copy(text = next, selection = TextRange(next.length))
        lastSnapshotText = next
        isDirty = true
    }

    fun save() {
        val u = uri ?: return
        val content = value.text
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasBackedUp) {
                    context.contentResolver.openInputStream(u)?.use { input ->
                        File(context.filesDir, "${getFileName(context, u)}~").writeBytes(input.readBytes())
                        hasBackedUp = true
                    }
                }
                context.contentResolver.openOutputStream(u, "wt")?.use { it.write(content.toByteArray()) }
                withContext(Dispatchers.Main) { isDirty = false }
            } catch (_: Exception) {}
        }
    }

    fun open(u: Uri) {
        try {
            context.contentResolver.openInputStream(u)?.use { input ->
                val content = input.bufferedReader().readText()
                value = TextFieldValue(content)
                lastSnapshotText = content
                uri = u
                openTrigger++
                isDirty = false
                hasBackedUp = false
                undoStack.clear()
                redoStack.clear()
                addToRecent(u)
            }
        } catch (_: Exception) {
            recentFiles.remove(u)
            saveRecent()
        }
    }

    private fun addToRecent(u: Uri) {
        try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        recentFiles.remove(u)
        recentFiles.add(0, u)
        if (recentFiles.size > 10) recentFiles.removeAt(10)
        saveRecent()
    }

    fun loadRecent() {
        val uris = prefs.getString("uris_ordered", "")?.split("|")?.filter { it.isNotEmpty() }?.map { it.toUri() } ?: emptyList()
        recentFiles.clear()
        recentFiles.addAll(uris)
    }

    private fun saveRecent() = prefs.edit { putString("uris_ordered", recentFiles.joinToString("|")) }
}

@Composable
fun TextEditorApp(
    intent: Intent? = null,
    providedState: EditorState? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = providedState ?: remember { EditorState(context, scope) }

    val focusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    var historyExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(state.openTrigger) {
        if (state.openTrigger > 0) {
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(scrollState.value) {
        state.scrollOffset = scrollState.value
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { state.open(it) } }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let { state.uri = it; state.value = TextFieldValue(""); state.save(); state.open(it) } }

    LaunchedEffect(Unit) {
        state.loadRecent()
        intent?.data?.let { state.open(it) } ?: state.recentFiles.firstOrNull()?.let { state.open(it) }
        delay(300)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(state.searchVisible) {
        if (state.searchVisible) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(state.value.text, state.uri) {
        if (state.isDirty && state.uri != null) {
            delay(1000)
            state.save()
        }
    }

    LaunchedEffect(state.value.text, state.searchQuery, state.searchCaseSensitive, state.searchVisible) {
        if (state.searchVisible && state.searchQuery.isNotEmpty()) {
            delay(1000)
            val res = mutableListOf<IntRange>()
            var idx = state.value.text.indexOf(state.searchQuery, 0, !state.searchCaseSensitive)
            while (idx >= 0 && res.size < 500) {
                res.add(idx until idx + state.searchQuery.length)
                idx = state.value.text.indexOf(state.searchQuery, idx + 1, !state.searchCaseSensitive)
            }
            state.searchResults = res
            if (state.searchIndex !in res.indices) state.searchIndex = if (res.isNotEmpty()) 0 else -1
        } else {
            state.searchResults = emptyList()
            state.searchIndex = -1
        }
    }

    LaunchedEffect(state.value.text) {
        if (state.value.text != state.lastSnapshotText) {
            delay(2000)
            state.undoStack.add(state.lastSnapshotText)
            if (state.undoStack.size > 50) state.undoStack.removeAt(0)
            state.lastSnapshotText = state.value.text
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && state.isDirty) state.save()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(state.searchIndex) {
        if (state.searchIndex >= 0 && state.searchIndex < state.searchResults.size) {
            val range = state.searchResults[state.searchIndex]
            textLayoutResult?.let { layout ->
                val top = layout.getCursorRect(range.first).top
                scrollState.animateScrollTo(top.toInt())
            }
        }
    }

    val vt = remember(state.searchQuery, state.searchResults, state.searchIndex, state.searchVisible) {
        if (state.searchVisible && state.searchQuery.isNotEmpty() && state.searchResults.isNotEmpty()) {
            VisualTransformation { text ->
                TransformedText(buildAnnotatedString {
                    append(text.text)
                    state.searchResults.forEachIndexed { i, range ->
                        addStyle(
                            SpanStyle(
                                background = if (i == state.searchIndex) Color(0xFFFFCC00) else Color(0xFF666600),
                                color = Color.Black
                            ),
                            range.first,
                            range.last + 1
                        )
                    }
                }, OffsetMapping.Identity)
            }
        } else VisualTransformation.None
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        val customTextSelectionColors = TextSelectionColors(
            handleColor = Color.White,
            backgroundColor = Color.White.copy(alpha = 0.4f)
        )
        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            BasicTextField(
                value = state.value,
                onValueChange = { state.onValueChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .focusRequester(focusRequester)
                    .testTag("editor"),
                onTextLayout = { textLayoutResult = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    lineBreak = LineBreak.Paragraph
                ),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = vt,
                decorationBox = { innerTextField ->
                    if (state.value.text.isEmpty()) {
                        Text(text = "Start typing...", color = Color.Gray, fontSize = 18.sp)
                    }
                    innerTextField()
                }
            )
        }
        Column(Modifier.fillMaxWidth().background(Color.Black).imePadding().navigationBarsPadding()) {
            if (state.searchVisible) Row(Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(state.searchQuery, { state.searchQuery = it }, Modifier.weight(1f).testTag("search_field").focusRequester(searchFocusRequester), placeholder = { Text(text = "Search...") }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                IconButton(onClick = { state.searchCaseSensitive = !state.searchCaseSensitive }) {
                    Icon(Icons.Default.TextFields, contentDescription = "Case Sensitive", tint = if (state.searchCaseSensitive) Color(0xFFFFCC00) else Color.White)
                }
                if (state.searchResults.isNotEmpty()) {
                    Text(text = "${state.searchIndex + 1}/${state.searchResults.size}", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                }
                IconButton(onClick = { state.searchIndex = (state.searchIndex - 1 + state.searchResults.size) % state.searchResults.size }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(onClick = { state.searchIndex = (state.searchIndex + 1) % state.searchResults.size }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = Color.White)
                }
                IconButton(onClick = { state.searchVisible = false; state.searchQuery = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF111111)).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                IconButton(onClick = { historyExpanded = true }, modifier = Modifier.testTag("history_button")) {
                    Icon(Icons.Default.History, contentDescription = "Recent Files", tint = Color.White)
                }
                IconButton(onClick = { createLauncher.launch("new_file.txt") }) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New File", tint = Color.White)
                }
                IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Open File", tint = Color.White)
                }
                IconButton(onClick = { state.undo() }, enabled = state.undoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (state.undoStack.isNotEmpty()) Color.White else Color.Gray)
                }
                IconButton(onClick = { state.redo() }, enabled = state.redoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (state.redoStack.isNotEmpty()) Color.White else Color.Gray)
                }
                IconButton(
                    onClick = {
                        val s = state.value.selection
                        if (!s.collapsed) {
                            val textToCut = state.value.text.substring(s.min, s.max)
                            clipboard.setText(AnnotatedString(textToCut))
                            state.onValueChange(state.value.copy(text = state.value.text.removeRange(s.min, s.max), selection = TextRange(s.min)))
                        }
                    },
                    enabled = !state.value.selection.collapsed
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = if (!state.value.selection.collapsed) Color.White else Color.Gray)
                }
                IconButton(
                    onClick = {
                        val s = state.value.selection
                        if (!s.collapsed) {
                            val textToCopy = state.value.text.substring(s.min, s.max)
                            clipboard.setText(AnnotatedString(textToCopy))
                        }
                    },
                    enabled = !state.value.selection.collapsed
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = if (!state.value.selection.collapsed) Color.White else Color.Gray)
                }
                IconButton(
                    onClick = {
                        clipboard.getText()?.text?.let { p ->
                            val s = state.value.selection
                            state.onValueChange(state.value.copy(text = state.value.text.replaceRange(s.min, s.max, p), selection = TextRange(s.min + p.length)))
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White)
                }
                IconButton(onClick = { state.searchVisible = !state.searchVisible }) {
                    Icon(Icons.Default.Search, contentDescription = "Toggle Search", tint = Color.White)
                }
            }
            DropdownMenu(historyExpanded, { historyExpanded = false }, Modifier.background(Color.DarkGray)) {
                if (state.recentFiles.isEmpty()) {
                    DropdownMenuItem(text = { Text(text = "No recent files", color = Color.White) }, onClick = { historyExpanded = false })
                } else {
                    state.recentFiles.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(text = getFileName(context, u), color = Color.White) },
                            onClick = { state.open(u); historyExpanded = false },
                            modifier = Modifier.testTag("history_item")
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result ?: "file"
}
