package com.jellyflix.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jellyflix.tv.playback.PlayerActivity
import com.jellyflix.tv.session.SessionViewModel
import com.jellyflix.tv.ui.DetailsScreen
import com.jellyflix.tv.ui.HomeScreen
import com.jellyflix.tv.ui.LibraryScreen
import com.jellyflix.tv.ui.LoginScreen
import com.jellyflix.tv.ui.ServerConnectScreen
import com.jellyflix.tv.ui.ServerPluginsScreen
import com.jellyflix.tv.ui.SettingsScreen
import com.jellyflix.tv.ui.UserPickerScreen
import com.jellyflix.tv.ui.theme.JellyflixTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JellyflixTheme { App(applicationContext) } }
    }
}

object Routes {
    const val CONNECT = "connect"
    const val USERS = "users"
    const val LOGIN = "login?username={username}"
    const val HOME = "home"
    const val LIBRARY = "library/{id}"
    const val DETAILS = "details/{id}"
    const val SETTINGS = "settings"
    const val SERVER_PLUGINS = "server_plugins"
    fun login(username: String = "") = "login?username=$username"
    fun library(id: String) = "library/$id"
    fun details(id: String) = "details/$id"
}

@Composable
private fun App(appContext: Context, sessionVm: SessionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val state by sessionVm.state.collectAsState()

    LaunchedEffect(state.stage) {
        val target = when (state.stage) {
            SessionViewModel.Stage.NeedsServer -> Routes.CONNECT
            SessionViewModel.Stage.NeedsLogin -> Routes.USERS
            SessionViewModel.Stage.Authenticated -> Routes.HOME
            SessionViewModel.Stage.Loading -> null
        }
        if (target != null && nav.currentDestination?.route?.substringBefore('?') != target.substringBefore('?')) {
            nav.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val openDetails: (String) -> Unit = { id -> nav.navigate(Routes.details(id)) }
    val openPlayer: (String, String, Long) -> Unit = { id, title, startMs ->
        appContext.startActivity(
            PlayerActivity.intent(appContext, UUID.fromString(id), title, startMs)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    NavHost(navController = nav, startDestination = Routes.CONNECT) {
        composable(Routes.CONNECT) { ServerConnectScreen(onConnected = sessionVm::onServerSet) }

        composable(Routes.USERS) {
            UserPickerScreen(
                onPickUser = { username, _ -> nav.navigate(Routes.login(username)) },
                onAddUser = { nav.navigate(Routes.login("")) },
                onChangeServer = { sessionVm.clearServer() },
            )
        }

        composable(
            Routes.LOGIN,
            arguments = listOf(navArgument("username") { type = NavType.StringType; defaultValue = "" }),
        ) { back ->
            val username = back.arguments?.getString("username").orEmpty()
            LoginScreen(
                onAuthenticated = sessionVm::refresh,
                onBack = { nav.popBackStack() },
                initialUsername = username,
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenLibrary = { id -> nav.navigate(Routes.library(id)) },
                onOpenDetails = openDetails,
                onPlay = openPlayer,
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIBRARY) { back ->
            val id = back.arguments?.getString("id").orEmpty()
            LibraryScreen(libraryId = id, onOpenDetails = openDetails, onBack = { nav.popBackStack() })
        }
        composable(Routes.DETAILS) { back ->
            val id = back.arguments?.getString("id").orEmpty()
            DetailsScreen(itemId = id, onPlay = openPlayer, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenServerPlugins = { nav.navigate(Routes.SERVER_PLUGINS) })
        }
        composable(Routes.SERVER_PLUGINS) { ServerPluginsScreen() }
    }
}
