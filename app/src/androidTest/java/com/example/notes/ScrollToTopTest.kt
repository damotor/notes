package com.example.notes

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScrollToTopTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @org.junit.Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun openingDocument_scrollsToTop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = File(context.filesDir, "test_scroll.txt")
        testFile.writeText("TOP_LINE\n" + "filler\n".repeat(100) + "BOTTOM_LINE")
        val uri = Uri.fromFile(testFile)

        lateinit var state: EditorState

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        // 1. Open document
        composeTestRule.runOnIdle {
            state.open(uri)
        }

        // 2. Assert top is visible (scroll offset is 0)
        composeTestRule.waitUntil(5000) {
            state.scrollOffset == 0
        }
        
        // Check text exists in the editor
        composeTestRule.onNodeWithText("TOP_LINE", substring = true).assertExists()

        // 3. Scroll down
        composeTestRule.onNodeWithTag("editor").performTouchInput {
            swipeUp()
            swipeUp()
            swipeUp()
        }

        // 4. Assert scrolled down
        composeTestRule.waitUntil(5000) {
            state.scrollOffset > 0
        }

        // 5. Re-open document (simulating history selection)
        composeTestRule.runOnIdle {
            state.open(uri)
        }

        // 6. Assert top is visible again
        composeTestRule.waitUntil(5000) {
            state.scrollOffset == 0
        }
        
        // And top line should be displayed (visible)
        composeTestRule.onNodeWithText("TOP_LINE", substring = true).assertIsDisplayed()
    }
}
