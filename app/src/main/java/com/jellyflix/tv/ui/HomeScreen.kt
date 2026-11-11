package com.jellyflix.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellyflix.tv.ui.components.HeroBanner
import com.jellyflix.tv.ui.components.JellyflixTextField
import com.jellyflix.tv.ui.components.MediaCard
import com.jellyflix.tv.ui.components.NavRailHeight
import com.jellyflix.tv.ui.components.NavTab
import com.jellyflix.tv.ui.components.PageBlack
import com.jellyflix.tv.ui.components.TopNavRail
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val EdgePad = 56.dp
private val RailSolidDistance = 48.dp
private val RowGap = 20.dp

private fun BaseItemDto.isLibrary(): Boolean =
    type == BaseItemKind.COLLECTION_FOLDER || type == BaseItemKind.USER_VIEW

private object HomeTabs {
    const val SEARCH = "search"
    const val HOME = "home"
    const val TV_SHOWS = "tv_shows"
    const val MOVIES = "movies"
    const val MY_LIST = "my_list"
}

@Composable
fun HomeScreen(
    onOpenLibrary: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onPlay: (itemId: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(HomeTabs.HOME) }

    val tabs = remember {
        listOf(
            NavTab(HomeTabs.SEARCH, "Search"),
            NavTab(HomeTabs.HOME, "Home"),
            NavTab(HomeTabs.TV_SHOWS, "TV Shows"),
            NavTab(HomeTabs.MOVIES, "Movies"),
            NavTab(HomeTabs.MY_LIST, "My List"),
        )
    }

    // === Hero selection ===
    // Fallback when nothing has focus: the first title we have, from continue watching, next up, or libraries.
    val fallbackHero = remember(state.continueWatching, state.nextUp, state.libraries) {
        (state.continueWatching + state.nextUp + state.libraries).firstOrNull()
    }
    // A card in any row drives the hero while it has focus.
    var rowFocusedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    // Focus on the hero's buttons keeps the last row item, so the hero doesn't jump back to the fallback.
    var lastRowItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var heroHasFocus by remember { mutableStateOf(false) }
    val heroItem: BaseItemDto? = rowFocusedItem
        ?: (if (heroHasFocus) lastRowItem else null)
        ?: fallbackHero
    val onRowCardFocusChange: (BaseItemDto, Boolean) -> Unit = { item, focused ->
        if (focused) {
            rowFocusedItem = item
            lastRowItem = item
        } else if (rowFocusedItem?.id == item.id) {
            rowFocusedItem = null
        }
    }

    // One scroll state for whichever tab is showing.
    val listState = rememberLazyListState()
    LaunchedEffect(tab) {
        listState.scrollToItem(0)
        if (tab == HomeTabs.MY_LIST) vm.refreshFavorites()
    }

    // === DPAD-up from the top row: scroll the page up to the hero, then focus its Play button ===
    val playFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var playFocusRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(playFocusRequest) {
        if (playFocusRequest > 0) runCatching { playFocusRequester.requestFocus() }
    }
    val onRowDpadUp: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            playFocusRequest++
        }
    }

    // === Rail: transparent over the hero, solid PageBlack once the content scrolls ===
    val solidDistancePx = with(LocalDensity.current) { RailSolidDistance.toPx() }
    val scrolledFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / solidDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val railSolid by animateFloatAsState(
        targetValue = scrolledFraction,
        animationSpec = tween(200),
        label = "rail-solid",
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(PageBlack)) {

            Column(Modifier.fillMaxSize()) {
                when (tab) {
                    HomeTabs.HOME -> HomeTab(
                        state = state,
                        heroItem = heroItem,
                        playFocusRequester = playFocusRequester,
                        listState = listState,
                        onRowCardFocusChange = onRowCardFocusChange,
                        onRowDpadUp = onRowDpadUp,
                        onHeroFocusChanged = { heroHasFocus = it },
                        onPlay = { item ->
                            // The fallback hero can be a library tile; those open the library, not the player.
                            if (item.isLibrary()) onOpenLibrary(item.id.toString())
                            else onPlay(item.id.toString(), item.name.orEmpty())
                        },
                        onMoreInfo = { item ->
                            if (item.isLibrary()) onOpenLibrary(item.id.toString())
                            else onOpenDetails(item.id.toString())
                        },
                        onOpenDetails = onOpenDetails,
                        onOpenLibrary = onOpenLibrary,
                    )
                    HomeTabs.MOVIES -> PosterGrid(
                        items = state.movies,
                        listState = listState,
                        emptyText = if (state.loading) "Loading movies…" else "No movies found in your libraries.",
                        onOpenDetails = onOpenDetails,
                        header = { GridHeader("Movies") },
                    )
                    HomeTabs.TV_SHOWS -> PosterGrid(
                        items = state.shows,
                        listState = listState,
                        emptyText = if (state.loading) "Loading TV shows…" else "No TV shows found in your libraries.",
                        onOpenDetails = onOpenDetails,
                        header = { GridHeader("TV Shows") },
                    )
                    HomeTabs.SEARCH -> SearchTab(
                        state = state,
                        listState = listState,
                        onQuery = vm::onSearchQuery,
                        onOpenDetails = onOpenDetails,
                    )
                    HomeTabs.MY_LIST -> MyListTab(state = state, listState = listState, onOpenDetails = onOpenDetails)
                }
            }

            TopNavRail(
                tabs = tabs,
                selected = tab,
                onSelect = { tab = it },
                onSettings = onOpenSettings,
                solidFraction = railSolid,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun HomeTab(
    state: HomeViewModel.State,
    heroItem: BaseItemDto?,
    playFocusRequester: FocusRequester,
    listState: LazyListState,
    onRowCardFocusChange: (BaseItemDto, Boolean) -> Unit,
    onRowDpadUp: () -> Unit,
    onHeroFocusChanged: (Boolean) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onMoreInfo: (BaseItemDto) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenLibrary: (String) -> Unit,
) {
    // The hero is the first item of the page, so it scrolls away with the rows, as on Netflix.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            HeroBanner(
                item = heroItem,
                onPlay = onPlay,
                onMoreInfo = onMoreInfo,
                playFocusRequester = playFocusRequester,
                onFocusWithinChanged = onHeroFocusChanged,
                // Library tiles have nothing to play, so the hero shows no actions for them.
                showActions = heroItem?.isLibrary() != true,
            )
        }

        // The top row (first on screen) sends DPAD-up to the hero.
        val continueIsTop = state.continueWatching.isNotEmpty()
        val nextUpIsTop = !continueIsTop
        val libraryIsTop = !continueIsTop && state.nextUp.isEmpty()

        if (continueIsTop) {
            item {
                RowSection(
                    "Continue watching",
                    state.continueWatching,
                    onClick = onOpenDetails,
                    onFocusChange = onRowCardFocusChange,
                    onDpadUp = if (continueIsTop) onRowDpadUp else null,
                )
            }
        }
        if (state.nextUp.isNotEmpty()) {
            item {
                RowSection(
                    "Next up",
                    state.nextUp,
                    onClick = onOpenDetails,
                    onFocusChange = onRowCardFocusChange,
                    onDpadUp = if (nextUpIsTop) onRowDpadUp else null,
                )
            }
        }
        if (state.libraries.isNotEmpty()) {
            item {
                LibraryRow(
                    state.libraries,
                    onOpen = onOpenLibrary,
                    onFocusChange = onRowCardFocusChange,
                    onDpadUp = if (libraryIsTop) onRowDpadUp else null,
                )
            }
        }

        if (state.loading && state.continueWatching.isEmpty() && state.libraries.isEmpty()) {
            item {
                Text(
                    "Loading your library…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFA8ADBD),
                    modifier = Modifier.padding(horizontal = EdgePad),
                )
            }
        }
        state.error?.let { err ->
            item {
                Text(
                    "Couldn't load home: $err",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = EdgePad),
                )
            }
        }
    }
}

@Composable
private fun GridHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        modifier = Modifier.padding(bottom = 20.dp),
    )
}

@Composable
private fun SearchTab(
    state: HomeViewModel.State,
    listState: LazyListState,
    onQuery: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    PosterGrid(
        items = state.searchResults,
        listState = listState,
        emptyText = when {
            state.searchQuery.isBlank() -> "Search for movies and shows in your library."
            state.searching -> "Searching…"
            else -> "No results for \"${state.searchQuery}\"."
        },
        onOpenDetails = onOpenDetails,
        header = {
            JellyflixTextField(
                value = state.searchQuery,
                onValueChange = onQuery,
                label = "Search",
                placeholder = "Titles",
                modifier = Modifier.width(560.dp).padding(bottom = 24.dp),
            )
        },
    )
}

@Composable
private fun MyListTab(
    state: HomeViewModel.State,
    listState: LazyListState,
    onOpenDetails: (String) -> Unit,
) {
    PosterGrid(
        items = state.favorites,
        listState = listState,
        emptyText = if (state.loading) {
            "Loading your list…"
        } else {
            "Titles you mark as favorite in Jellyfin appear here."
        },
        onOpenDetails = onOpenDetails,
        header = {
            Text(
                "My List",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        },
    )
}

/** Vertical list of poster rows, six per row. Used by Search and My List. */
@Composable
private fun PosterGrid(
    items: List<BaseItemDto>,
    listState: LazyListState,
    emptyText: String,
    onOpenDetails: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EdgePad, end = EdgePad, top = NavRailHeight, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.let { h -> item { h() } }
        if (items.isEmpty()) {
            item {
                Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFA8ADBD))
            }
        }
        items(items.chunked(6), key = { row -> row.first().id.toString() }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                row.forEach { entry ->
                    MediaCard(item = entry, onClick = { onOpenDetails(entry.id.toString()) })
                }
            }
        }
    }
}

@Composable
private fun RowSection(
    title: String,
    items: List<BaseItemDto>,
    onClick: (String) -> Unit,
    onFocusChange: ((BaseItemDto, Boolean) -> Unit)? = null,
    onDpadUp: (() -> Unit)? = null,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(start = EdgePad, end = EdgePad, bottom = 4.dp),
        )
        CarouselRow {
            items(items, key = { it.id.toString() }) { item ->
                MediaCard(
                    item = item,
                    onClick = { onClick(item.id.toString()) },
                    onFocusChange = onFocusChange,
                    onDpadUp = onDpadUp,
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    libs: List<BaseItemDto>,
    onOpen: (String) -> Unit,
    onFocusChange: ((BaseItemDto, Boolean) -> Unit)? = null,
    onDpadUp: (() -> Unit)? = null,
) {
    Column {
        Text(
            "Your libraries",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(start = EdgePad, end = EdgePad, bottom = 4.dp),
        )
        CarouselRow {
            items(libs, key = { it.id.toString() }) { lib ->
                MediaCard(
                    item = lib,
                    onClick = { onOpen(lib.id.toString()) },
                    onFocusChange = onFocusChange,
                    widthDp = 240,
                    heightDp = 140,
                    onDpadUp = onDpadUp,
                )
            }
        }
    }
}

/**
 * Netflix-style carousel: edge-to-edge, cards bleed off the screen edge when scrolled,
 * vertical padding leaves room for the focused card's 1.15x scale, and the focused card is
 * left-anchored (see [EdgeBringIntoViewSpec]) so the row moves only when focus reaches an edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselRow(content: LazyListScope.() -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeBringIntoViewSpec) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            content = content,
        )
    }
}

/**
 * Left-anchored, Netflix-style scrolling: the row stays still while the focused card is on
 * screen, and moves only the minimum needed to bring it fully into view at an edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private object EdgeBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0f -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}
