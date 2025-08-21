package com.ably.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsAreDisplayed() {
        // Verify both tabs are displayed
        composeTestRule.onNodeWithText("Color Voting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Task Management").assertIsDisplayed()
    }
}
