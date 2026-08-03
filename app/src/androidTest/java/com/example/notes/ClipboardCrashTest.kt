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

    @org.junit.Before
    fun setup() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

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
        composeTestRule.onNodeWithTag("cut_button").performClick()

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
        composeTestRule.onNodeWithTag("copy_button").performClick()

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

        // Click Paste
        composeTestRule.onNodeWithTag("paste_button").performClick()
    }

    @Test
    fun cutText_withOutOfBoundsSelection_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // Force an out-of-bounds selection
        composeTestRule.runOnIdle {
            state.value = TextFieldValue("123", selection = TextRange(10, 20))
        }

        // Click Cut
        composeTestRule.onNodeWithTag("cut_button").performClick()
    }

    @Test
    fun copyText_withOutOfBoundsSelection_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        composeTestRule.runOnIdle {
            state.value = TextFieldValue("123", selection = TextRange(10, 20))
        }

        // Click Copy
        composeTestRule.onNodeWithTag("copy_button").performClick()
    }
}
