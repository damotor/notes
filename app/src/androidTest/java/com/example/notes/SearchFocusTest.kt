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
class SearchFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun togglingSearch_focusesSearchField() {
        composeTestRule.setContent {
            TextEditorApp()
        }

        // 1. Search field should not exist initially
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()

        // 2. Click the search button
        composeTestRule.onNodeWithTag("search_toggle_button").performClick()

        // 3. Search field should exist and be focused
        composeTestRule.onNodeWithTag("search_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_field").assertIsFocused()

        // 4. Close search
        composeTestRule.onNodeWithTag("search_close_button").performClick()

        // 5. Search field should not exist again
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()

        // 6. Toggle search again to re-verify focus
        composeTestRule.onNodeWithTag("search_toggle_button").performClick()
        composeTestRule.onNodeWithTag("search_field").assertIsFocused()
    }
}
