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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
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
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavRailHeight)
            .background(PageBlack.copy(alpha = solidFraction.coerceIn(0f, 1f)))
            .padding(horizontal = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
            color = Color.White,
        )
        Spacer(Modifier.width(40.dp))

        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Color.Transparent,
            separator = {},
            // Focus and selection are drawn by each tab, not by the default indicator.
            indicator = { _, _ -> },
            tabs = {
                tabs.forEachIndexed { index, tab ->
                    NavTabItem(
                        label = tab.label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(tab.id) },
                    )
                }
            },
        )

        Spacer(Modifier.weight(1f))

        RailButton(label = "Settings", onClick = onSettings)
    }
}

@Composable
private fun TabRowScope.NavTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Tab(
        selected = selected,
        onFocus = {},
        onClick = onClick,
        interactionSource = interaction,
        colors = TabDefaults.pillIndicatorTabColors(),
        modifier = modifier.padding(horizontal = 6.dp),
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
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 17.sp,
                    ),
                    color = when {
                        focused -> Color.Black
                        selected -> Color.White
                        else -> Color(0xB3D8DBE5)
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
            color = if (focused) Color.Black else Color(0xB3D8DBE5),
        )
    }
}
