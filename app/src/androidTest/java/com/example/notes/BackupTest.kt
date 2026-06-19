package com.example.notes

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun save_createsBackupEveryTime() {
        lateinit var state: EditorState
        var filesDir: File? = null
        
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            filesDir = context.filesDir
            state = remember { EditorState(context, scope) }
            TextEditorApp(providedState = state)
        }

        val testFile = File(filesDir, "test_backup.txt")
        val backupFile = File(filesDir, "test_backup.txt~")
        
        // Clean up
        testFile.delete()
        backupFile.delete()

        // 1. Create initial file
        testFile.writeText("Version 1")
        val uri = Uri.fromFile(testFile)

        // 2. Open file in app
        composeTestRule.runOnIdle {
            state.open(uri)
        }
        
        // 3. Change text to "Version 2" and save
        composeTestRule.runOnIdle {
            state.onValueChange(state.value.copy(text = "Version 2"))
            state.save()
        }
        
        // Wait for save (it's on Dispatchers.IO)
        Thread.sleep(500)
        
        composeTestRule.runOnIdle {
            assertTrue("Backup file should exist", backupFile.exists())
            assertEquals("Version 1", backupFile.readText())
            assertEquals("Version 2", testFile.readText())
        }

        // 4. Change text to "Version 3" and save again
        composeTestRule.runOnIdle {
            state.onValueChange(state.value.copy(text = "Version 3"))
            state.save()
        }
        
        Thread.sleep(500)

        composeTestRule.runOnIdle {
            assertEquals("Version 2", backupFile.readText())
            assertEquals("Version 3", testFile.readText())
        }
    }
}
