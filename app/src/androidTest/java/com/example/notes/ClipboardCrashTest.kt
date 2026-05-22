package com.example.notes

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardCrashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cutText_withReverseSelection_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // Set text and reverse selection (start > end)
        composeTestRule.runOnIdle {
            state.value = TextFieldValue("Hello World", selection = TextRange(11, 6))
        }

        // Click Cut
        composeTestRule.onNodeWithContentDescription("Cut").performClick()

        // Verify text was cut and no crash occurred
        composeTestRule.onNodeWithText("Hello ").assertIsDisplayed()
        composeTestRule.onNodeWithText("World").assertDoesNotExist()
    }

    @Test
    fun copyText_withReverseSelection_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // Set text and reverse selection (start > end)
        composeTestRule.runOnIdle {
            state.value = TextFieldValue("Hello World", selection = TextRange(11, 6))
        }

        // Click Copy
        composeTestRule.onNodeWithContentDescription("Copy").performClick()

        // Verify text is still there and no crash occurred
        composeTestRule.onNodeWithText("Hello World").assertIsDisplayed()
    }

    @Test
    fun pasteText_withReverseSelection_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // Set text and reverse selection (start > end)
        composeTestRule.runOnIdle {
            state.value = TextFieldValue("Hello World", selection = TextRange(11, 6))
        }

        // Click Paste (we assume there's something in clipboard, 
        // but the fix should handle the range regardless of whether p is empty or not 
        // as long as the replacement happens)
        // We can't easily mock clipboard in instrumented tests without some effort, 
        // but we can at least trigger the action.
        composeTestRule.onNodeWithContentDescription("Paste").performClick()
    }
}
