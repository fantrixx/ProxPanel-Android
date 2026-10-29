package com.proxpanel.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.proxpanel.app.AppContainer
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.ui.backups.BackupsScreen
import com.proxpanel.app.ui.backups.BackupsViewModel
import com.proxpanel.app.ui.guests.GuestsScreen
import com.proxpanel.app.ui.guests.GuestsViewModel
import com.proxpanel.app.ui.marketplace.MarketplaceScreen
import com.proxpanel.app.ui.marketplace.MarketplaceViewModel
import com.proxpanel.app.ui.media.MediaScreen
import com.proxpanel.app.ui.media.MediaViewModel
import com.proxpanel.app.ui.settings.SettingsScreen
import com.proxpanel.app.ui.settings.SettingsViewModel
import com.proxpanel.app.ui.storage.StorageScreen
import com.proxpanel.app.ui.storage.StorageViewModel
import com.proxpanel.app.ui.tasks.TasksScreen
import com.proxpanel.app.ui.tasks.TasksViewModel
import com.proxpanel.app.ui.util.ListScrollSession
import com.proxpanel.app.ui.util.LocalListScrollSession
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeShell(
    container: AppContainer,
    onOpenGuest: (GuestItem) -> Unit,
    onCreateGuest: () -> Unit,
    onLogout: () -> Unit,
) {
    val tabNav = rememberNavController()
    val backStack by tabNav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val guestsVm: GuestsViewModel = viewModel(
        factory = GuestsViewModel.factory(
            container.api,
            container.jobTracker,
            container.prefsStore,
            container.l10n,
        ),
    )
    val jobTracker = container.jobTracker
    val scrollSession = remember { ListScrollSession() }

    LaunchedEffect(tabNav) {
        container.pendingHomeTab.collectLatest { route ->
            if (route.isNullOrBlank()) return@collectLatest
            tabNav.navigate(route) {
                popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            container.pendingHomeTab.value = null
        }
    }

    // Provide the session object only — never `session.scrolling` here, or the
    // whole NavHost recomposes on every fling start/stop.
    // Job banner/toasts come from LocalActiveJobsChrome in MainActivity.
    CompositionLocalProvider(LocalListScrollSession provides scrollSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = tabNav,
                        startDestination = HomeTab.Overview.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 88.dp),
                    ) {
                        composable(HomeTab.Overview.route) {
                            GuestsScreen(
                                viewModel = guestsVm,
                                jobTracker = jobTracker,
                                onOpenGuest = onOpenGuest,
                                onCreateGuest = onCreateGuest,
                                onLogout = onLogout,
                            )
                        }
                        composable(HomeTab.Backups.route) {
                            val vm: BackupsViewModel = viewModel(
                                factory = BackupsViewModel.factory(
                                    container.api,
                                    jobTracker,
                                    container.l10n,
                                    container.prefsStore,
                                ),
                            )
                            BackupsScreen(viewModel = vm)
                        }
                        composable(HomeTab.Storage.route) {
                            val vm: StorageViewModel = viewModel(
                                factory = StorageViewModel.factory(
                                    container.api,
                                    container.l10n,
                                    container.prefsStore,
                                ),
                            )
                            StorageScreen(viewModel = vm)
                        }
                        composable(HomeTab.Tasks.route) {
                            val vm: TasksViewModel = viewModel(
                                factory = TasksViewModel.factory(
                                    container.api,
                                    container.l10n,
                                    container.prefsStore,
                                ),
                            )
                            TasksScreen(viewModel = vm)
                        }
                        composable(HomeTab.Media.route) {
                            val vm: MediaViewModel = viewModel(
                                factory = MediaViewModel.factory(
                                    container.api,
                                    container.l10n,
                                    container.prefsStore,
                                ),
                            )
                            MediaScreen(viewModel = vm)
                        }
                        composable(HomeTab.Apps.route) {
                            val vm: MarketplaceViewModel = viewModel(
                                factory = MarketplaceViewModel.factory(
                                    container.l10n,
                                    container.prefsStore,
                                ),
                            )
                            MarketplaceScreen(viewModel = vm)
                        }
                        composable(HomeTab.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.factory(
                                    container.prefsStore,
                                    container.serverProfileStore,
                                    container.sessionStore,
                                    container.credentialStore,
                                    container.api,
                                ),
                            )
                            SettingsScreen(
                                viewModel = vm,
                                onSwitchServer = onLogout,
                            )
                        }
                    }

                    FloatingBottomNav(
                        selectedRoute = current,
                        onSelect = { tab ->
                            tabNav.navigate(tab.route) {
                                popUpTo(tabNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}
