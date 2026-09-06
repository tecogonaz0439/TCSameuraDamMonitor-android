// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * マウスのドラッグ操作によるスクロールをエミュレートするカスタムモディファイア [Modifier.mouseDragScroll] の UI テストクラス。
 * Jetpack Compose で通常はタッチ操作に限られるスクロール挙動に対し、マウスクリック＆ドラッグで
 * 縦スクロール（ScrollStateおよびLazyColumn）と横スクロール（ScrollState）が意図通りに実行され、
 * スクロールオフセット値やインデックスが更新されることを検証します。
 */
class MouseDragScrollAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verticalScroll_acceptsMouseDrag() {
        lateinit var scrollState: ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 180.dp)
                    .mouseDragScroll(scrollState, Orientation.Vertical)
                    .verticalScroll(scrollState)
                    .testTag(SCROLL_TAG)
            ) {
                Column {
                    repeat(80) { index ->
                        Text(text = "Row $index", modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithTag(SCROLL_TAG).performMouseInput {
            dragAndDrop(start = bottomCenter, end = topCenter, durationMillis = 250)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollState.value > 0 }
        assertTrue(scrollState.value > 0)
    }

    @Test
    fun lazyColumn_acceptsMouseDrag() {
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .size(width = 240.dp, height = 180.dp)
                    .mouseDragScroll(listState, Orientation.Vertical)
                    .testTag(SCROLL_TAG)
            ) {
                items((0 until 120).toList()) { index ->
                    Text(text = "Item $index", modifier = Modifier.height(32.dp))
                }
            }
        }

        composeRule.onNodeWithTag(SCROLL_TAG).performMouseInput {
            dragAndDrop(start = bottomCenter, end = topCenter, durationMillis = 250)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
        assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
    }

    @Test
    fun horizontalScroll_acceptsMouseDrag() {
        lateinit var scrollState: ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 120.dp)
                    .mouseDragScroll(scrollState, Orientation.Horizontal)
                    .horizontalScroll(scrollState)
                    .testTag(SCROLL_TAG)
            ) {
                Row {
                    repeat(60) { index ->
                        Text(text = "Cell $index", modifier = Modifier.width(72.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithTag(SCROLL_TAG).performMouseInput {
            dragAndDrop(start = centerRight, end = centerLeft, durationMillis = 250)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollState.value > 0 }
        assertTrue(scrollState.value > 0)
    }

    private companion object {
        const val SCROLL_TAG = "mouse_drag_scroll_target"
    }
}
