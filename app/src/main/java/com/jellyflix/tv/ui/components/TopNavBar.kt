package com.jellyflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class NavTab(val id: String, val label: String)

/** Height of the top rail. Screens reserve this much space at the top of their lists. */
val NavRailHeight = 96.dp

/**
 * Netflix-style top rail: wordmark, a [TabRow] of destinations, and Settings on the right.
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
    title: String = "Jellyflix",
    /**
     * Called on DPAD-down from anywhere in the rail. Focus search from the tab row does not
     * reliably reach the content below, so the host decides where DPAD-down goes.
     * Null leaves DPAD-down to the default focus search.
     */
    onDown: (() -> Unit)? = null,
    /** Attached to the selected tab so the content can route DPAD-up back into the rail. */
    selectedFocus: FocusRequester? = null,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)

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
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
            color = Color.White,
        )
        Spacer(Modifier.width(40.dp))

        // A plain Row, not TabRow: TabRow lays its tabs out through SubcomposeLayout, which
        // re-runs on every focus move and made the rail stutter. Behaviour is the same here.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.forEachIndexed { index, tab ->
                NavTabItem(
                    label = tab.label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(tab.id) },
                    modifier = if (index == selectedIndex && selectedFocus != null) {
                        Modifier.focusRequester(selectedFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        RailButton(label = "Settings", onClick = onSettings)
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
                    .background(if (focused) Color.White else Color.Transparent)
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
            // Active-tab marker, hidden while the tab has focus so the white pill reads as the focus state.
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(if (selected && !focused) 24.dp else 0.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun RailButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            color = if (focused) Color.Black else Color(0xB3E5E5E5),
        )
    }
}
