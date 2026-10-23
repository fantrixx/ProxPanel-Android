package com.proxpanel.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.proxpanel.app.data.AccentChoice
import com.proxpanel.app.data.GuestItem
import com.proxpanel.app.ui.create.CreateGuestScreen
import com.proxpanel.app.ui.create.CreateGuestType
import com.proxpanel.app.ui.create.CreateGuestViewModel
import com.proxpanel.app.ui.detail.GuestDetailScreen
import com.proxpanel.app.ui.detail.GuestDetailViewModel
import com.proxpanel.app.ui.login.LoginScreen
import com.proxpanel.app.ui.login.LoginViewModel
import com.proxpanel.app.ui.shell.HomeShell
import com.proxpanel.app.R
import com.proxpanel.app.ui.theme.ProxPanelTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val app = application as ProxPanelApp
        setContent {
            val accent by app.container.prefsStore.accentChoice.collectAsStateWithLifecycle(
                initialValue = AccentChoice.Orange,
            )
            ProxPanelTheme(accent = accent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ProxPanelNav(app.container)
                }
            }
        }
    }
}

@Composable
private fun ProxPanelNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            val vm: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(
                    container.api,
                    container.sessionStore,
                    container.credentialStore,
                    container.l10n,
                ),
            )
            LoginScreen(
                viewModel = vm,
                onSignedIn = {
                    nav.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }

        composable("home") {
            HomeShell(
                container = container,
                onOpenGuest = { guest ->
                    nav.navigate(
                        "guest/${guest.type}/${enc(guest.node)}/${guest.vmid}/${enc(guest.name)}/${enc(guest.status)}",
                    )
                },
                onCreateGuest = { nav.navigate("create") },
                onLogout = {
                    nav.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }

        composable("create") {
            val vm: CreateGuestViewModel = viewModel(
                factory = CreateGuestViewModel.factory(container.api, container.l10n, CreateGuestType.Lxc),
            )
            CreateGuestScreen(
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onCreated = { result ->
                    container.jobTracker.trackCreate(
                        node = result.node,
                        type = result.type,
                        vmid = result.vmid,
                        upid = result.upid,
                    )
                    nav.navigate(
                        "guest/${result.type}/${enc(result.node)}/${result.vmid}/${enc(container.l10n.get(R.string.guest_fallback_name, result.vmid))}/creating",
                    ) {
                        popUpTo("home")
                    }
                },
            )
        }

        composable(
            route = "guest/{type}/{node}/{vmid}/{name}/{status}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("node") { type = NavType.StringType },
                navArgument("vmid") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType },
                navArgument("status") { type = NavType.StringType },
            ),
        ) { entry ->
            val guest = remember(entry.arguments) {
                GuestItem(
                    node = dec(entry.arguments!!.getString("node")!!),
                    type = entry.arguments!!.getString("type")!!,
                    vmid = entry.arguments!!.getInt("vmid"),
                    name = dec(entry.arguments!!.getString("name")!!),
                    status = dec(entry.arguments!!.getString("status")!!),
                    cpu = null,
                    mem = null,
                    maxmem = null,
                    disk = null,
                    maxdisk = null,
                    uptime = null,
                    ips = emptyList(),
                    lock = null,
                )
            }
            val vm: GuestDetailViewModel = viewModel(
                factory = GuestDetailViewModel.factory(
                    container.api,
                    container.jobTracker,
                    container.prefsStore,
                    container.l10n,
                    guest,
                ),
            )
            GuestDetailScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
    }
}

private fun enc(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

private fun dec(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
