package com.example.notes

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UndoRedoTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun undo_revertsCharacterByCharacter() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // 1. Type "H"
        composeTestRule.onNodeWithTag("editor").performTextInput("H")
        composeTestRule.runOnIdle {
            assertEquals("H", state.value.text)
            assertEquals(1, state.undoStack.size)
            assertEquals("", state.undoStack[0])
        }

        // 2. Type "i"
        composeTestRule.onNodeWithTag("editor").performTextInput("i")
        composeTestRule.runOnIdle {
            assertEquals("Hi", state.value.text)
            assertEquals(2, state.undoStack.size)
            assertEquals("H", state.undoStack[1])
        }

        // 3. Click Undo
        composeTestRule.onNodeWithContentDescription("Undo").performClick()
        composeTestRule.runOnIdle {
            assertEquals("H", state.value.text)
        }

        // 4. Click Undo again
        composeTestRule.onNodeWithContentDescription("Undo").performClick()
        composeTestRule.runOnIdle {
            assertEquals("", state.value.text)
        }
    }
}
