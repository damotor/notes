package com.example.notes

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testEditorInput() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextReplacement("Hello World")
        editor.assertTextContains("Hello World")
    }

    @Test
    fun testUndoRedo() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        val undoButton = composeTestRule.onNodeWithTag("undo_button")
        val redoButton = composeTestRule.onNodeWithTag("redo_button")

        // Initial state: buttons disabled
        undoButton.assertIsNotEnabled()
        redoButton.assertIsNotEnabled()

        // Type something
        editor.performTextInput("A")
        undoButton.assertIsEnabled()
        
        editor.performTextInput("B")
        editor.assertTextContains("AB")

        // Undo
        undoButton.performClick()
        editor.assertTextContains("A")
        redoButton.assertIsEnabled()

        // Redo
        redoButton.performClick()
        editor.assertTextContains("AB")
    }

    @Test
    fun testSearchAction() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextReplacement("Find find")

        // Toggle search
        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        
        val searchField = composeTestRule.onNodeWithTag("search_field")
        searchField.assertIsDisplayed()
        
        searchField.performTextInput("find")
        
        // Wait for search debounce (300ms in code)
        composeTestRule.mainClock.advanceTimeBy(500)
        
        // Case insensitive by default: should find both "Find" and "find"
        composeTestRule.onNodeWithTag("search_results_count").assertTextContains("1/2")

        // Toggle case sensitive
        composeTestRule.onNodeWithTag("search_case_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        
        // Now should only find "find" (1 result)
        composeTestRule.onNodeWithTag("search_results_count").assertTextContains("1/1")

        // Close search
        composeTestRule.onNodeWithTag("search_close_button").performClick()
        searchField.assertDoesNotExist()
    }

    @Test
    fun testHistoryMenu() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val historyButton = composeTestRule.onNodeWithTag("history_button")
        historyButton.performClick()

        composeTestRule.onNodeWithText("No recent files").assertIsDisplayed()
    }

    @Test
    fun testCutCopyPasteVisibility() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        val editor = composeTestRule.onNodeWithTag("editor")
        editor.performTextReplacement("Test")

        composeTestRule.onNodeWithTag("cut_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("copy_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("paste_button").assertIsEnabled()
    }

    @Test
    fun testFileActionsVisibility() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        composeTestRule.onNodeWithTag("new_file_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("open_file_button").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun testEmptyEditorPlaceholder() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        composeTestRule.onNodeWithText("Start typing...").assertIsDisplayed()
        
        composeTestRule.onNodeWithTag("editor").performTextInput("a")
        composeTestRule.onNodeWithText("Start typing...").assertDoesNotExist()
    }
}
