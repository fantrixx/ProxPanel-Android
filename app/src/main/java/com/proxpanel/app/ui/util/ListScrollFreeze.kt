package com.proxpanel.app.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Non-compose flag for hot paths (e.g. Coil) that must not subscribe to scroll
 * state — new items read this while composing; existing items stay put.
 */
object ListScrollGate {
    @Volatile
    var active: Boolean = false
}

/**
 * For optional heavy extras (e.g. CPU sparkline on cards). Starts ready when idle
 * so icons/buttons are never delayed; only defers while a fling is already active.
 */
@Composable
fun rememberHeavyContentReady(): Boolean {
    var ready by remember { mutableStateOf(!ListScrollGate.active) }
    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        while (ListScrollGate.active) delay(16)
        ready = true
    }
    return ready
}

/**
 * Shared scroll gate. Tabs write via [update]; animation-heavy composables read
 * [scrolling] so only they recompose — never provide [scrolling] as a
 * CompositionLocal value at the shell (that would rebuild the whole NavHost).
 */
class ListScrollSession {
    var scrolling by mutableStateOf(false)
        private set

    fun update(value: Boolean) {
        if (scrolling != value) {
            scrolling = value
            ListScrollGate.active = value
        }
    }
}

val LocalListScrollSession = staticCompositionLocalOf { ListScrollSession() }

/**
 * Tracks list/grid scroll and reports settle after [settleMs].
 */
@Composable
fun rememberListScrollFreeze(
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    useGrid: Boolean = false,
    settleMs: Long = 400L,
    onScrolling: (Boolean) -> Unit,
) {
    LaunchedEffect(listState, gridState, useGrid, settleMs) {
        snapshotFlow {
            val active = when {
                useGrid -> gridState?.isScrollInProgress == true
                else -> listState?.isScrollInProgress == true
            }
            // Same snapshot as the fling — new tiles must see the gate on this frame.
            if (active) ListScrollGate.active = true
            active
        }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    onScrolling(true)
                } else {
                    delay(settleMs)
                    onScrolling(false)
                }
            }
    }
    DisposableEffect(onScrolling) {
        onDispose { onScrolling(false) }
    }
}

/** Same gate for Column + verticalScroll (guest detail, etc.). */
@Composable
fun rememberColumnScrollFreeze(
    scrollState: ScrollState,
    settleMs: Long = 400L,
    onScrolling: (Boolean) -> Unit,
): Boolean {
    var scrolling by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState, settleMs) {
        snapshotFlow { scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    scrolling = true
                    onScrolling(true)
                } else {
                    delay(settleMs)
                    scrolling = false
                    onScrolling(false)
                }
            }
    }
    DisposableEffect(onScrolling) {
        onDispose { onScrolling(false) }
    }
    return scrolling
}
