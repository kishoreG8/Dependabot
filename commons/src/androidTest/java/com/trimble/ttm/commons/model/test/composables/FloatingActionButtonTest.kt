package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.model.FabItem
import com.trimble.ttm.commons.composable.fab.FabState
import com.trimble.ttm.commons.composable.fab.MultiFloatingActionButton
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.test.NEW_FORM
import com.trimble.ttm.commons.model.test.NEW_MESSAGE
import com.trimble.ttm.commons.utils.FAB_ICON
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingActionButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testComposable() {
        // Set up your Composable here
        composeTestRule.setContent {
            val fabToState = remember { mutableStateOf(FabState.COLLAPSED) }
            MultiFloatingActionButton(listOf(
                FabItem(
                    NEW_MESSAGE
                ),
                FabItem(
                    NEW_FORM
                )
            ),fabToState.value,{
                fabToState.value = it
            },{
                Log.i("FAB ITEM CLICK","CLICKED FAB ITEM :  ${it.label}")
            })
        }

        // Verify that the FAB is displayed
        composeTestRule.onNodeWithContentDescription(FAB_ICON).assertExists()
        composeTestRule.onNodeWithContentDescription(FAB_ICON).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(FAB_ICON).performClick()

        composeTestRule.onNodeWithText(NEW_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText(NEW_FORM).assertIsDisplayed()

        composeTestRule.onNodeWithText(NEW_MESSAGE).assertHasClickAction()
        composeTestRule.onNodeWithText(NEW_FORM).assertHasClickAction()

        composeTestRule.onNodeWithText(NEW_MESSAGE).performClick()
        composeTestRule.onNodeWithText(NEW_FORM).performClick()
    }
}