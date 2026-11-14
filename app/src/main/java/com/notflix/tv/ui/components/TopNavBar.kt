package com.notflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ArrowDropDown
import android.app.Activity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.notflix.tv.R

/**
 * A rail destination. Tabs with an [icon] sit at the right of the rail as round icon buttons,
 * the way Netflix puts search; the rest are text tabs on the left.
 */
data class NavTab(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    /** Icon tabs placed before the text tabs, as Netflix puts search. */
    val leading: Boolean = false,
)

/** Height of the top rail. Screens reserve this much space at the top of their lists. */
val NavRailHeight = 96.dp

/**
 * Netflix-style top rail: wordmark, text tabs, then the icon buttons (Settings cog, search, profile) on the right.
 *
 * - Selection is on click, so moving focus between tabs does not reload content.
 * - The focused tab is solid white; the selected (active) tab is white text with an underline.
 * - [solidFraction] (0..1) fades the rail's background from transparent to solid [PageBlack],
 *   as Netflix does once the user scrolls into content. The items stay visible.
 */
@Composable
fun TopNavRail(
    tabs: List<NavTab>,
    selected: String,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
    solidFraction: Float,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
    /**
     * Called on DPAD-down from anywhere in the rail. Focus search from the tab row does not
     * reliably reach the content below, so the host decides where DPAD-down goes.
     * Null leaves DPAD-down to the default focus search.
     */
    onDown: (() -> Unit)? = null,
    /** Attached to the selected tab so the content can route DPAD-up back into the rail. */
    selectedFocus: FocusRequester? = null,
    /** Signed-in profile's name, shown as an avatar that opens [onProfile] (switch profile). */
    profileName: String? = null,
    profileAvatarUrl: String? = null,
    onProfile: (() -> Unit)? = null,
) {
    val leadingTabs = tabs.filter { it.leading }
    val textTabs = tabs.filter { it.icon == null }
    val iconTabs = tabs.filter { it.icon != null && !it.leading }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavRailHeight)
            .background(PageBlack.copy(alpha = solidFraction.coerceIn(0f, 1f)))
            .onPreviewKeyEvent { event ->
                if (onDown != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDown()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The full wordmark sits on the start screen. In the rail the logo is just the C, at the far right as Netflix's N.

        // Three groups: avatar and search on the left, tabs centred, settings and the C mark on the right.
        // The left and right groups share the free width, so the tabs sit in the middle as on Netflix.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profileName != null && onProfile != null) {
                ProfileMenu(
                    name = profileName,
                    avatarUrl = profileAvatarUrl,
                    onSwitchProfile = onProfile,
                    onSettings = onSettings,
                )
            }
        }

        // A plain Row, not TabRow: TabRow lays its tabs out through SubcomposeLayout, which
        // re-runs on every focus move and made the rail stutter. Behaviour is the same here.
        // Search sits beside the tabs, as on Netflix, so the avatar dropdown never covers it.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingTabs.forEach { tab ->
                IconTab(
                    icon = tab.icon!!,
                    description = tab.label,
                    selected = tab.id == selected,
                    onClick = { onSelect(tab.id) },
                    modifier = if (tab.id == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
            textTabs.forEach { tab ->
                val isSelected = tab.id == selected
                NavTabItem(
                    label = tab.label,
                    selected = isSelected,
                    onClick = { onSelect(tab.id) },
                    modifier = if (isSelected && selectedFocus != null) {
                        Modifier.focusRequester(selectedFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTab(
                icon = Icons.Filled.Settings,
                description = "Settings",
                selected = false,
                onClick = onSettings,
            )
            Spacer(Modifier.width(16.dp))
            iconTabs.forEach { tab ->
                val isSelected = tab.id == selected
                IconTab(
                    icon = tab.icon!!,
                    description = tab.label,
                    selected = isSelected,
                    onClick = { onSelect(tab.id) },
                    modifier = if (isSelected && selectedFocus != null) {
                        Modifier.focusRequester(selectedFocus)
                    } else {
                        Modifier
                    },
                )
                Spacer(Modifier.width(16.dp))
            }
            Image(
                painter = painterResource(R.drawable.notflix_mark_c),
                contentDescription = title,
                modifier = Modifier.height(44.dp),
            )
        }
    }
}

/** Square-edged avatar shape, matching the profile picker tiles. */
private val AvatarShape = RoundedCornerShape(10.dp)

/** Round icon button, white when focused. Matches [ProfileChip]'s size so the two sit together. */
@Composable
private fun IconTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(44.dp)
            // Square-edged like the avatar beside it, so the rail uses one icon shape.
            .clip(AvatarShape)
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Color(0xB3E5E5E5)
            },
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun ProfileChip(name: String, avatarUrl: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(AvatarShape)
            .background(Color(0xFF2F2F2F))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = AvatarShape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The initial sits underneath, so it shows if the avatar is missing or still loading.
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Switch profile, currently $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(AvatarShape),
            )
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            focused -> Color.White
                            // Netflix marks the current section with a grey pill, not an underline.
                            selected -> Color(0x59FFFFFF)
                            else -> Color.Transparent
                        },
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    ),
                    color = when {
                        focused -> Color.Black
                        selected -> Color.White
                        else -> Color(0xB3E5E5E5)
                    },
                )
            }
        }
    }
}

/**
 * Avatar at the left of the rail, as Netflix has it. Selecting it opens a dropdown with the
 * current profile, Switch profile, Settings and Exit. Back closes it.
 */
@Composable
private fun ProfileMenu(
    name: String,
    avatarUrl: String?,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    // The menu opens from the avatar's own top-left corner, so its header avatar lands exactly
    // on the top-bar avatar and the name appears beside it.
    // Nudged slightly left of the avatar's edge.
    val dropOffset = with(density) { IntOffset((-12).dp.roundToPx(), 0) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileChip(name = name, avatarUrl = avatarUrl, onClick = { open = !open })
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }

    if (open) {
        Popup(
            alignment = Alignment.TopStart,
            offset = dropOffset,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { open = false },
        ) {
            Column(
                modifier = Modifier
                    // Narrow enough to stop short of the Home tab pill.
                    .width(230.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF2202020))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp),
            ) {
                MenuHeader(name = name, avatarUrl = avatarUrl)
                Spacer(Modifier.height(8.dp))
                MenuItem("Switch profile") {
                    open = false
                    onSwitchProfile()
                }
                MenuItem("Settings") {
                    open = false
                    onSettings()
                }
                MenuItem("Exit Notflix") {
                    open = false
                    (context as? Activity)?.finish()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) Color.Black else Color(0xFFE5E5E5),
        )
    }
}

/** Avatar and name at the top of the dropdown, as Netflix shows the current profile. */
@Composable
private fun MenuHeader(name: String, avatarUrl: String?) {
    Row(
        // Same inset as the menu item labels (10dp item padding + 16dp label padding), so the avatar lines up with them.
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(start = 26.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(AvatarShape)
                .background(Color(0xFF2F2F2F)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(AvatarShape),
                )
            }
        }
        // Gap to the name matches the gap from the menu border to the item labels (26dp).
        Spacer(Modifier.width(26.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

