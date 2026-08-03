package com.example.notes

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BoundaryNegativeTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testSearchWithNoResults() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextInput("The quick brown fox")

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        val searchField = composeTestRule.onNodeWithTag("search_field")
        
        searchField.performTextInput("lazy dog")
        composeTestRule.mainClock.advanceTimeBy(500)

        // Results count should not be displayed when there are no matches
        composeTestRule.onNodeWithTag("search_results_count").assertDoesNotExist()
    }

    @Test
    fun testSearchNavigationButtonsDisabledWhenNoResults() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextInput("abc")

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        val searchField = composeTestRule.onNodeWithTag("search_field")
        
        searchField.performTextInput("xyz")
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNodeWithTag("search_prev_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("search_next_button").assertIsNotEnabled()
    }

    @Test
    fun testSearchNavigationButtonsDisabledInitially() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        
        // No query yet
        composeTestRule.onNodeWithTag("search_prev_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("search_next_button").assertIsNotEnabled()
    }

    @Test
    fun testSearchWithSpecialCharacters() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextInput("Price is $10.00 (discounted?)")

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        val searchField = composeTestRule.onNodeWithTag("search_field")
        
        // Test characters that often have special meaning in regex
        searchField.performTextInput("$10.00 (")
        composeTestRule.mainClock.advanceTimeBy(1000)

        // Should find exactly 1 result if treated as literal
        composeTestRule.onNodeWithTag("search_results_count").assertIsDisplayed().assertTextContains("1/1")
    }

    @Test
    fun testRedoStackClearedOnNewInput() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        val undoButton = composeTestRule.onNodeWithTag("undo_button")
        val redoButton = composeTestRule.onNodeWithTag("redo_button")

        editor.performTextInput("A")
        editor.performTextInput("B")
        
        undoButton.performClick()
        editor.assertTextContains("A")
        redoButton.assertIsEnabled()

        // New input after undo should clear redo stack
        editor.performTextInput("C")
        editor.assertTextContains("AC")
        redoButton.assertIsNotEnabled()
    }

    @Test
    fun testHistoryLimitBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var state: EditorState
        
        composeTestRule.setContent {
            state = rememberEditorState()
            TextEditorApp(providedState = state)
        }

        // Add 12 real files to history
        composeTestRule.runOnIdle {
            for (i in 1..12) {
                val file = File(context.filesDir, "file$i.txt")
                file.writeText("content $i")
                val uri = Uri.fromFile(file)
                state.open(uri) 
            }
        }

        // Verify history is capped at 10
        composeTestRule.runOnIdle {
            assertEquals(10, state.recentFiles.size)
            assertEquals("file12.txt", getFileName(context, state.recentFiles[0]))
            assertEquals("file3.txt", getFileName(context, state.recentFiles[9]))
        }
    }

    @Test
    fun testLargeTextStability() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        val largeText = "A".repeat(100_000)
        
        editor.performTextReplacement(largeText)
        editor.assertExists()
        
        editor.performTextInput("B")
        editor.assertTextContains("B", substring = true)
    }

    @Test
    fun testUndoAfterLargePaste() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        val undoButton = composeTestRule.onNodeWithTag("undo_button")
        
        val initialText = "Start"
        editor.performTextInput(initialText)
        
        val largeText = "X".repeat(10_000)
        editor.performTextInput(largeText)
        
        undoButton.performClick()
        editor.assertTextEquals(initialText)
    }

    @Test
    fun testOpeningNonExistentFile() {
        lateinit var state: EditorState
        
        composeTestRule.setContent {
            state = rememberEditorState()
            TextEditorApp(providedState = state)
        }

        val nonExistentUri = Uri.parse("file:///non/existent/path/file_missing.txt")
        
        composeTestRule.runOnIdle {
            state.open(nonExistentUri)
        }
        
        composeTestRule.runOnIdle {
            assertEquals("", state.value.text)
        }
    }

    @Test
    fun testSearchWithLongQuery() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextInput("abc")

        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        val searchField = composeTestRule.onNodeWithTag("search_field")
        
        searchField.performTextInput("abcdefgh")
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNodeWithTag("search_results_count").assertDoesNotExist()
    }

    @Test
    fun testUndoEmptyStack() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            state = rememberEditorState()
            TextEditorApp(providedState = state)
        }

        composeTestRule.runOnIdle {
            state.undo()
            assertEquals("", state.value.text)
        }
    }
}

@androidx.compose.runtime.Composable
fun rememberEditorState(): EditorState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return androidx.compose.runtime.remember { EditorState(context, scope) }
}
