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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notes.ui.theme.NotesTheme
import kotlinx.coroutines.*
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
    val undoStack = mutableStateListOf<String>()
    val redoStack = mutableStateListOf<String>()
    var lastSnapshotText = ""
    var hasBackedUp = false
    val recentFiles = mutableStateListOf<Uri>()

    var searchVisible by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf(listOf<IntRange>())
    var searchIndex by mutableIntStateOf(-1)
    var searchCaseSensitive by mutableStateOf(false)

    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

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
fun TextEditorApp(intent: Intent? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { EditorState(context, scope) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    var historyExpanded by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { state.open(it) } }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let { state.uri = it; state.value = TextFieldValue(""); state.save(); state.open(it) } }

    LaunchedEffect(Unit) {
        state.loadRecent()
        intent?.data?.let { state.open(it) } ?: state.recentFiles.firstOrNull()?.let { state.open(it) }
        delay(300)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(state.value.text, state.uri) {
        if (state.isDirty && state.uri != null) {
            delay(5000)
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .focusRequester(focusRequester),
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
                TextField(state.searchQuery, { state.searchQuery = it }, Modifier.weight(1f), placeholder = { Text(text = "Search...") }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
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
                IconButton(onClick = { historyExpanded = true }) {
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
                            clipboard.setText(AnnotatedString(state.value.text.substring(s.start, s.end)))
                            state.onValueChange(state.value.copy(text = state.value.text.removeRange(s.start, s.end), selection = TextRange(s.start)))
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
                            clipboard.setText(AnnotatedString(state.value.text.substring(s.start, s.end)))
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
                            state.onValueChange(state.value.copy(text = state.value.text.replaceRange(s.start, s.end, p), selection = TextRange(state.value.selection.start + p.length)))
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
                }
                state.recentFiles.forEach { uri ->
                    DropdownMenuItem(text = { Text(text = uri.lastPathSegment ?: "file", color = Color.White) }, onClick = { state.open(uri); historyExpanded = false })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

fun getFileName(context: Context, uri: Uri): String = 
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
    } ?: uri.lastPathSegment ?: "file"
