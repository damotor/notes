package com.example.notes

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchingForMultipleTerms_scrollsToEach() {
        lateinit var state: EditorState
        val term1 = "TARGET_ONE"
        val term2 = "TARGET_TWO"
        val content = term1 + "\n".repeat(1000) + term2

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        composeTestRule.runOnIdle {
            state.value = androidx.compose.ui.text.input.TextFieldValue(content)
        }

        // Toggle search
        composeTestRule.onNodeWithContentDescription("Toggle Search").performClick()

        // Search for term1 (it's at the top)
        composeTestRule.onNodeWithTag("search_field").performTextInput(term1)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitUntil(5000) { state.searchResults.isNotEmpty() }
        
        composeTestRule.runOnIdle {
            assert(state.scrollOffset == 0)
        }

        // Search for term2 (it's at the bottom) WITHOUT clearing
        composeTestRule.onNodeWithTag("search_field").performTextReplacement(term2)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitUntil(5000) { state.searchResults.isNotEmpty() && state.searchQuery == term2 }

        // Check if we scrolled down
        composeTestRule.waitUntil(5000) {
            state.scrollOffset > 0
        }

        // Search for term1 again (at the top)
        composeTestRule.onNodeWithTag("search_field").performTextReplacement(term1)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitUntil(5000) { state.searchResults.isNotEmpty() && state.searchQuery == term1 }

        // Check if we scrolled back up
        composeTestRule.waitUntil(5000) {
            state.scrollOffset == 0
        }
    }
}
