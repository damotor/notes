package com.example.notes

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun typingAtBottom_scrollsAutomatically() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // 1. Fill the editor with many lines so it becomes scrollable
        val manyLines = (1..100).joinToString("\n") { "Line $it" }
        composeTestRule.runOnIdle {
            state.onValueChange(state.value.copy(text = manyLines, selection = TextRange(0)))
        }

        composeTestRule.waitForIdle()
        
        // Ensure we are at the top
        composeTestRule.runOnIdle {
            assert(state.scrollOffset == 0)
        }

        // 2. Move cursor to the end and add a line
        composeTestRule.runOnIdle {
            val text = state.value.text
            state.onValueChange(state.value.copy(selection = TextRange(text.length)))
        }
        
        composeTestRule.onNodeWithTag("editor").performTextInput("\nTarget Line")
        
        // 3. Wait for the scroll LaunchedEffect to trigger (animateScrollTo)
        // We might need to advance the clock if it's an animation
        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()
        
        // 4. Check if scrollOffset has increased
        val finalScroll = state.scrollOffset
        assert(finalScroll > 0) {
            "Expected scroll offset to be greater than 0, but it's $finalScroll"
        }
    }
}
