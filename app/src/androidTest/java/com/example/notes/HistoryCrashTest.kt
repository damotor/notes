package com.example.notes

import android.net.Uri
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
class HistoryCrashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingHistoryIcon_withInvalidUri_doesNotCrash() {
        lateinit var state: EditorState
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // Add a URI that will likely cause a SecurityException if queried
        val invalidUri = Uri.parse("content://com.android.contacts/contacts")
        composeTestRule.runOnIdle {
            state.recentFiles.add(invalidUri)
        }

        // Tap history button
        composeTestRule.onNodeWithTag("history_button").performClick()

        // Wait for it to handle the click and try to show the menu
        composeTestRule.waitForIdle()

        // If it crashes, it won't reach here.
        composeTestRule.onAllNodesWithTag("history_item").onFirst().assertExists()
    }
}
