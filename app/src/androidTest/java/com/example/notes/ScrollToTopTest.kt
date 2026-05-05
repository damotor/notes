package com.example.notes

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScrollToTopTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun openingDocument_scrollsToTop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = File(context.filesDir, "test.txt")
        testFile.writeText("TOP_LINE\n" + "filler\n".repeat(100) + "BOTTOM_LINE")
        val uri = Uri.fromFile(testFile)

        lateinit var state: EditorState

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            state = remember { EditorState(LocalContext.current, scope) }
            TextEditorApp(state = state)
        }

        // 1. Open document
        composeTestRule.runOnIdle {
            state.open(uri)
        }

        // 2. Assert top is visible
        composeTestRule.onNodeWithText("TOP_LINE").assertIsDisplayed()

        // 3. Scroll down
        composeTestRule.onNodeWithTag("editor").performTouchInput {
            swipeUp()
            swipeUp()
            swipeUp()
        }

        // 4. Assert top is NOT visible
        composeTestRule.onNodeWithText("TOP_LINE").assertIsNotDisplayed()

        // 5. Re-open document (simulating history selection)
        composeTestRule.runOnIdle {
            state.open(uri)
        }

        // 6. Assert top is visible again
        composeTestRule.onNodeWithText("TOP_LINE").assertIsDisplayed()
    }
}
