package com.example.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun togglingSearch_focusesSearchField() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        // 1. Search field should not exist initially
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()

        // 2. Click the search button
        composeTestRule.onNodeWithContentDescription("Toggle Search").performClick()

        // 3. Search field should exist and be focused
        composeTestRule.onNodeWithTag("search_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_field").assertIsFocused()

        // 4. Close search
        composeTestRule.onNodeWithContentDescription("Close Search").performClick()

        // 5. Search field should not exist again
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()

        // 6. Toggle search again to re-verify focus
        composeTestRule.onNodeWithContentDescription("Toggle Search").performClick()
        composeTestRule.onNodeWithTag("search_field").assertIsFocused()
    }
}
