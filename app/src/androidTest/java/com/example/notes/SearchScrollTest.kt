package com.example.notes

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SearchScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchingAndNavigating_scrollsToMultipleResults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = File(context.filesDir, "search_test_multi.txt")
        val match1 = "MATCH_ONE"
        val match2 = "MATCH_TWO"
        // Space them out significantly
        testFile.writeText(match1 + "\n".repeat(200) + match2 + "\n".repeat(200))
        val uri = Uri.fromFile(testFile)

        lateinit var state: EditorState

        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val localContext = LocalContext.current
            state = remember { EditorState(localContext, scope) }
            TextEditorApp(providedState = state)
        }

        // 1. Open document
        composeTestRule.runOnIdle {
            state.open(uri)
        }

        // 2. Open search and type "MATCH"
        composeTestRule.onNodeWithContentDescription("Toggle Search").performClick()
        composeTestRule.onNodeWithTag("search_field").performTextInput("MATCH")

        // 3. Wait for search and first scroll
        composeTestRule.mainClock.advanceTimeBy(3000)
        composeTestRule.waitForIdle()

        // 4. Should be at first match (scroll ~ 0)
        val firstScroll = state.scrollOffset
        
        // 5. Click Next to go to match 2
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
        
        val secondScroll = state.scrollOffset
        assertTrue("Second match should be further down ($secondScroll > $firstScroll)", secondScroll > firstScroll)

        // 6. Click Previous to go back to match 1
        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()
        
        assertTrue("Should have scrolled back up (${state.scrollOffset} < $secondScroll)", state.scrollOffset < secondScroll)
    }
}
