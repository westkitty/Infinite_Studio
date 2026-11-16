package com.example

import android.app.Application
import android.graphics.PointF
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.model.StudioTool
import com.example.ui.theme.InfiniteStudioTheme
import com.example.viewmodel.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiFlowsRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `UI CUJ opens Workspace Hub tabs toggles ruler jumps Orchard node loads Sample and accepts Futures study`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudioViewModel(app)

        // Paint an ink stroke so canvas artwork exists
        vm.selectTool(StudioTool.INK)
        vm.setActiveColor("#38BDF8")
        vm.beginPointerAction(PointF(-50f, -40f), 0.9f)
        vm.movePointerAction(PointF(60f, 30f), 0.9f)
        vm.endPointerAction()
        val originalInkHash = vm.uiState.value.document.layers
            .first { it.id == vm.uiState.value.document.activeLayerId }.pixelContentHash()

        composeRule.setContent {
            InfiniteStudioTheme {
                InfiniteStudioApp(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        // 1. Verify primary canvas & top controls exist
        composeRule.onNodeWithTag("studio_canvas_viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_studio_hub").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_open_futures").assertIsDisplayed()

        // 2. Open Studio Workspace Hub -> navigate tabs -> toggle 8-Head Ruler -> add & click Orchard node
        composeRule.onNodeWithTag("btn_studio_hub").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_workspace_hub").assertIsDisplayed()

        // Visit Vector & Foundry tab, then Vault/Branch/Orchard tab
        composeRule.onNodeWithTag("hub_tab_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_export_svg").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hub_tab_4").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_toggle_ruler").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.document.characterSheets.first().showProportionRuler)

        composeRule.onNodeWithTag("btn_add_orchard").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("orchard_node_orch_root").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Hub closes upon Orchard node jump
        composeRule.onNodeWithTag("studio_workspace_hub").assertDoesNotExist()

        // 3. Open Futures Table via top button -> wait for async study generation -> select Copper -> Accept
        composeRule.onNodeWithTag("btn_open_futures").performClick()
        composeRule.waitUntil(timeoutMillis = 10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.futuresSession != null
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("futures_table_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("branch_card_salt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("branch_card_night").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("branch_card_ember").performScrollTo().performClick()
        composeRule.waitForIdle()

        val layerCountBeforeAccept = vm.uiState.value.document.layers.size
        composeRule.onNodeWithTag("btn_futures_accept").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(layerCountBeforeAccept + 1, vm.uiState.value.document.layers.size)
        // Verify original ink layer hash is strictly unchanged
        val inkLayerNow = vm.uiState.value.document.layers.first { it.id == "layer_ink" }
        assertEquals(originalInkHash, inkLayerNow.pixelContentHash())

        // 4. Open Project Vault -> Load Offline Sample (via AlertDialog confirmation)
        composeRule.onNodeWithTag("btn_project_vault").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_load_sample").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntil(timeoutMillis = 10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.document.vectorObjects.any { it.text == "ONE MARK, THREE FUTURES" }
        }
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.document.layers.size >= 4)

        // 5. 5-tap Brand Badge reveals Redacted Developer Diagnostics
        repeat(5) {
            composeRule.onNodeWithTag("brand_badge_diag_trigger").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_dialog").assertIsDisplayed()
    }
}
