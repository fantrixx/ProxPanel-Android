package com.proxpanel.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(haptics, view) {
        AppHaptics(
            tick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            confirm = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            },
            longPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            },
        )
    }
}

class AppHaptics(
    val tick: () -> Unit,
    val confirm: () -> Unit,
    val longPress: () -> Unit,
)
