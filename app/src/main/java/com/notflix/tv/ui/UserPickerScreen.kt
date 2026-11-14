package com.notflix.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.notflix.tv.R

private val DefaultAccent = Color(0xFFE50914)

/** Banner per profile, matched by name. Unknown profiles get no banner. */
private fun profileBanner(name: String?): Int? = when (name?.lowercase()) {
    "twins" -> R.drawable.picker_bg_rachel
    "gracie" -> R.drawable.picker_bg_wednesday
    "gemma" -> R.drawable.picker_bg_bridgerton
    "lewy" -> R.drawable.picker_bg_nocturne
    null -> R.drawable.picker_bg_rachel
    else -> null
}

// Square tiles with softened corners. The highlighted tile gets a white border, no zoom.
private val AvatarSize = 72.dp
private val TileShape = RoundedCornerShape(10.dp)

@Composable
fun UserPickerScreen(
    onPickUser: (username: String, requiresPassword: Boolean) -> Unit,
    /** A problem from the last sign-in attempt, e.g. the server could not be reached. */
    notice: String? = null,
    vm: UserPickerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val accent = state.accentColor?.let { Color(it) } ?: DefaultAccent
    // The highlighted profile's banner. Nothing highlighted shows the Twins banner by default.
    var highlighted by remember { mutableStateOf<String?>(null) }
    val banner = profileBanner(highlighted)

    // Splash until the profile list has loaded, so the picker never appears half-empty.
    if (state.loading) {
        SplashScreen()
        return
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // Per-profile banner: fades in behind the list when a profile is highlighted.
            Crossfade(targetState = banner, animationSpec = tween(400), label = "picker-banner") { bg ->
                if (bg != null) {
                    Image(
                        painter = painterResource(bg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Ambient backdrop. No blur modifier: it kills the GPU on low-end TV boxes.
            if (state.splashscreenUrl != null) {
                AsyncImage(
                    model = state.splashscreenUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.18f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.14f), Color(0xFF141414)),
                        radius = 1400f,
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 72.dp, end = 72.dp, top = 24.dp, bottom = 40.dp),
            ) {
                // Notflix wordmark, then a compact title. Kept small so all five tiles fit on screen.
                Image(
                    painter = painterResource(R.drawable.notflix_wordmark),
                    contentDescription = "Notflix",
                    modifier = Modifier.height(36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose a profile",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))

                when {
                    state.loading -> Text("Loading users…", color = Color(0xFFB3B3B3))
                    state.users.isEmpty() && state.error == null -> Text(
                        "No public users on this server. Choose add profile to sign in manually.",
                        color = Color(0xFFB3B3B3),
                    )
                    else -> Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        state.users.forEach { u ->
                            ProfileRow(
                                user = u,
                                onFocus = { highlighted = u.name },
                                onClick = { onPickUser(u.name, u.hasPassword) },
                            )
                        }
                                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Couldn't fetch users: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** A profile tile with its name beside it. The name only shows while the tile is highlighted. */
@Composable
private fun ProfileRow(
    user: UserPickerViewModel.PublicUser,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(TileShape)
                .background(Color(0xFF2F2F2F))
                .border(
                    width = if (focused) 4.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = TileShape,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
                    color = Color(0xFFFFFFFF),
                )
            }
        }
        Spacer(Modifier.width(40.dp))
        Text(
            user.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp),
            color = Color.White,
            maxLines = 1,
            // Names are always visible; the focused one is brighter.
            modifier = Modifier.alpha(if (focused) 1f else 0.7f),
        )
    }
}
