package com.proxpanel.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.proxpanel.app.jobs.JobTracker

data class ActiveJobsChrome(
    val tracker: JobTracker,
    val onOpenTasks: () -> Unit,
)

val LocalActiveJobsChrome = staticCompositionLocalOf<ActiveJobsChrome?> { null }

/** Drop below the screen title — keeps TopBar pinned, job strip optional. */
@Composable
fun ActiveJobsBelowTitle(modifier: Modifier = Modifier) {
    val chrome = LocalActiveJobsChrome.current ?: return
    ActiveJobsBanner(
        tracker = chrome.tracker,
        onOpenTasks = chrome.onOpenTasks,
        modifier = modifier,
    )
}
