package com.moa.babylanguage

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTitleScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun automaticGreetingFinishesExactlyOnce() {
        var exits = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { AppTitleScreen { exits++ } } }
        // Also valid with system animations removed, where the greeting finishes immediately.
        compose.mainClock.advanceTimeBy(1_100)
        compose.runOnIdle { assertEquals(1, exits) }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals("Leaving the composable mounted must not repeat completion", 1, exits) }
    }

    @Test fun skipAndAutomaticDeadlineCannotCompleteTwice() {
        var exits = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { AppTitleScreen { exits++ } } }
        compose.onNodeWithTag("app-title-screen").performClick().performClick()
        compose.mainClock.advanceTimeBy(1_100)
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun compactScreenAtDoubleFontKeepsTitleAndSubtitleReachable() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                        AppTitleScreen { }
                    }
                }
            }
        }
        val screen = compose.onNodeWithTag("app-title-screen").fetchSemanticsNode().boundsInRoot
        for (text in listOf("아가톡", "지금 내 기분이 그래")) {
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            node.performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$text must stay inside the available width", bounds.left >= screen.left && bounds.right <= screen.right)
        }
        compose.onNodeWithTag("app-title-screen").assertHasClickAction()
    }
}

/** Exercises the production activity boundary rather than adding a greeting to JigeumApp tests. */
class AppTitleActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coldStartReachesHomeAndRecreationDoesNotReplayGreeting() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("page-home").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("app-title-screen").assertDoesNotExist()
        // Freeze Compose time so a mistakenly replayed greeting cannot disappear before assertion.
        compose.mainClock.autoAdvance = false
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("app-title-screen").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("page-home").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
