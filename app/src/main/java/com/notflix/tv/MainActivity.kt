package com.notflix.tv

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
import com.notflix.tv.playback.PlayerActivity
import com.notflix.tv.session.SessionViewModel
import com.notflix.tv.ui.DetailsScreen
import com.notflix.tv.ui.HomeScreen
import com.notflix.tv.ui.LibraryScreen
import com.notflix.tv.ui.LoginScreen
import com.notflix.tv.ui.SplashScreen
import com.notflix.tv.ui.SettingsScreen
import com.notflix.tv.ui.UserPickerScreen
import com.notflix.tv.ui.theme.NotflixTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotflixTheme { App(applicationContext) } }
    }
}

object Routes {
    const val SPLASH = "splash"
    const val USERS = "users"
    const val LOGIN = "login?username={username}"
    const val HOME = "home"
    const val LIBRARY = "library/{id}"
    const val DETAILS = "details/{id}"
    const val SETTINGS = "settings"
    /** The username is URL-encoded so names with &, #, % or spaces survive the navigation route. */
    fun login(username: String = "") = "login?username=${java.net.URLEncoder.encode(username, "UTF-8")}"
    fun library(id: String) = "library/$id"
    fun details(id: String) = "details/$id"
}

private fun avatarUrl(serverUrl: String?, userId: String?): String? =
    if (serverUrl.isNullOrBlank() || userId.isNullOrBlank()) null
    else "$serverUrl/Users/$userId/Images/Primary?quality=90&maxWidth=160"

@Composable
private fun App(appContext: Context, sessionVm: SessionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val state by sessionVm.state.collectAsState()

    LaunchedEffect(state.stage) {
        val target = when (state.stage) {
            // The server is fixed in the build, so there is no connect step: go straight to the profiles.
            SessionViewModel.Stage.NeedsServer, SessionViewModel.Stage.NeedsLogin -> Routes.USERS
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

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        // Shown until the saved session has loaded; the LaunchedEffect above then moves on.
        composable(Routes.SPLASH) { SplashScreen() }


        composable(Routes.USERS) {
            UserPickerScreen(
                notice = state.notice,
                onPickUser = { username, _ ->
                    // Always try an empty password first (the server's "has password" flag is unreliable),
                    // showing splash meanwhile. The password form only appears if that is refused.
                    nav.navigate(Routes.SPLASH) { launchSingleTop = true }
                    sessionVm.signInProfile(username) {
                        nav.navigate(Routes.USERS) { popUpTo(0) { inclusive = true } }
                        nav.navigate(Routes.login(username))
                    }
                },
            )
        }

        composable(
            Routes.LOGIN,
            arguments = listOf(
                navArgument("username") { type = NavType.StringType; defaultValue = "" },
            ),
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
                profileName = state.username,
                profileAvatarUrl = avatarUrl(state.serverUrl, state.userId),
                onSwitchProfile = { sessionVm.switchProfile() },
            )
        }
        composable(Routes.LIBRARY) { back ->
            val id = back.arguments?.getString("id").orEmpty()
            LibraryScreen(libraryId = id, onOpenDetails = openDetails, onBack = { nav.popBackStack() })
        }
        composable(Routes.DETAILS) { back ->
            val id = back.arguments?.getString("id").orEmpty()
            DetailsScreen(
                itemId = id,
                onPlay = openPlayer,
                onOpenDetails = openDetails,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                serverUrl = state.serverUrl,
                username = state.username,
                onSwitchProfile = { sessionVm.switchProfile() },
                onAddProfile = { nav.navigate(Routes.login()) },
            )
        }
    }
}
