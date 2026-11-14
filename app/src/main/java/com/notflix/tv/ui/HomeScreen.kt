package com.notflix.tv.ui

import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.notflix.tv.ui.components.CardArt
import com.notflix.tv.ui.components.HeroBanner
import com.notflix.tv.ui.components.NotflixTextField
import com.notflix.tv.ui.components.MediaCard
import com.notflix.tv.ui.components.NavRailHeight
import com.notflix.tv.ui.components.NavTab
import com.notflix.tv.ui.components.PageBlack
import com.notflix.tv.ui.components.TopNavRail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.compose.material.icons.filled.Movie
import androidx.tv.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** How long focus must rest on a card before the hero switches to it. */
private const val HeroSettleMs = 350L
private val EdgePad = 56.dp
private val RailSolidDistance = 48.dp
private val RowGap = 12.dp

/** Where to resume from, in milliseconds (0 when the item has never been started). */
private fun BaseItemDto.resumeMs(): Long = (userData?.playbackPositionTicks ?: 0L) / 10_000L

private fun BaseItemDto.isLibrary(): Boolean =
    type == BaseItemKind.COLLECTION_FOLDER || type == BaseItemKind.USER_VIEW

private object HomeTabs {
    const val SEARCH = "search"
    const val HOME = "home"
    const val TV_SHOWS = "tv_shows"
    const val MOVIES = "movies"
}

@Composable
fun HomeScreen(
    onOpenLibrary: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onPlay: (itemId: String, title: String, startMs: Long) -> Unit,
    onOpenSettings: () -> Unit,
    profileName: String? = null,
    profileAvatarUrl: String? = null,
    onSwitchProfile: (() -> Unit)? = null,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(HomeTabs.HOME) }
    // Back from the player: reload so the progress bars and Continue watching are current.
    OnScreenResumed { vm.load() }

    val tabs = remember {
        listOf(
            NavTab(HomeTabs.SEARCH, "Search", Icons.Filled.Search, leading = true),
            NavTab(HomeTabs.HOME, "Home"),
            NavTab(HomeTabs.TV_SHOWS, "Shows"),
            NavTab(HomeTabs.MOVIES, "Movies"),
        )
    }

    // === Hero selection ===
    // Fallback when nothing has focus: the show with the latest episode added (the recent rows are
    // newest first), else the newest movie, else the first library. Continue watching is not used here,
    // since that title already has its own row.
    val fallbackHero = remember(state.recentShows, state.recentMovies, state.libraries) {
        (state.recentShows.firstOrNull() ?: state.recentMovies.firstOrNull() ?: state.libraries.firstOrNull())
    }
    // A card in any row drives the hero while it has focus.
    var rowFocusedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    // Focus on the hero's buttons keeps the last row item, so the hero doesn't jump back to the fallback.
    var lastRowItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var heroHasFocus by remember { mutableStateOf(false) }
    // The hero follows the focused card only once focus has settled. Fast sideways moves then load
    // one backdrop instead of one per card, which was the main cost of each step on the TV.
    var settledRowItem by remember { mutableStateOf<BaseItemDto?>(null) }
    LaunchedEffect(rowFocusedItem?.id) {
        val item = rowFocusedItem ?: return@LaunchedEffect
        delay(HeroSettleMs)
        settledRowItem = item
    }
    val heroItem: BaseItemDto? = (if (heroHasFocus) lastRowItem else settledRowItem)
        ?: fallbackHero
    // Only a card gaining focus changes the hero. Clearing it on blur made the hero flip to the
    // fallback and back on every sideways move, which loaded and faded two full-screen backdrops.
    val onRowCardFocusChange: (BaseItemDto, Boolean) -> Unit = { item, focused ->
        if (focused) {
            rowFocusedItem = item
            lastRowItem = item
        }
    }

    // One scroll state for whichever tab is showing.
    val listState = rememberLazyListState()
    LaunchedEffect(tab) {
        listState.scrollToItem(0)
    }

    // === Going down into the page from the nav or from the top row ===
    // Scroll to the top, then focus the tab's first item: Play on Home, the search box on Search,
    // the first poster on the grid tabs.
    val playFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val navFocusRequester = remember { FocusRequester() }
    // DPAD-up from the top row of a tab goes back to that tab's nav item.
    val onUpToNav: () -> Unit = { runCatching { navFocusRequester.requestFocus() } }
    val scope = rememberCoroutineScope()
    var focusTarget by remember { mutableStateOf<FocusRequester?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) focusTarget?.let { runCatching { it.requestFocus() } }
    }
    val focusContent: (FocusRequester) -> Unit = { target ->
        scope.launch {
            listState.animateScrollToItem(0)
            focusTarget = target
            focusTick++
        }
    }
    val onRowDpadUp: () -> Unit = { focusContent(playFocusRequester) }
    val onRailDown: () -> Unit = {
        focusContent(
            when (tab) {
                HomeTabs.HOME -> playFocusRequester
                HomeTabs.SEARCH -> searchFocusRequester
                else -> gridFocusRequester
            }
        )
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
                    HomeTabs.HOME -> if (state.loading && state.libraries.isEmpty() && state.continueWatching.isEmpty()) {
                        // First load: the same splash as the picker, not a page of greyed-out buttons.
                        SplashScreen()
                    } else HomeTab(
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
                            else vm.resolvePlay(item) { target ->
                                onPlay(target.id.toString(), target.name.orEmpty(), target.resumeMs())
                            }
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
                        firstFocus = gridFocusRequester,
                        onUpToNav = onUpToNav,
                        emptyText = if (state.loading) "Loading movies…" else "No movies found in your libraries.",
                        onOpenDetails = onOpenDetails,
                        header = { GridHeader("Movies") },
                    )
                    HomeTabs.TV_SHOWS -> PosterGrid(
                        items = state.shows,
                        listState = listState,
                        firstFocus = gridFocusRequester,
                        onUpToNav = onUpToNav,
                        emptyText = if (state.loading) "Loading TV shows…" else "No TV shows found in your libraries.",
                        onOpenDetails = onOpenDetails,
                        header = { GridHeader("TV Shows") },
                    )
                    HomeTabs.SEARCH -> SearchTab(
                        state = state,
                        listState = listState,
                        onQuery = vm::onSearchQuery,
                        onOpenDetails = onOpenDetails,
                        searchFocus = searchFocusRequester,
                        onUpToNav = onUpToNav,
                        navFocus = navFocusRequester,
                    )
                }
            }

            TopNavRail(
                tabs = tabs,
                selected = tab,
                onSelect = { tab = it },
                onSettings = onOpenSettings,
                solidFraction = railSolid,
                onDown = onRailDown,
                selectedFocus = navFocusRequester,
                profileName = profileName,
                profileAvatarUrl = profileAvatarUrl,
                onProfile = onSwitchProfile,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/**
 * One titled carousel on the Home tab. Home rows are 16:9 like Netflix: episode stills for
 * Continue watching and Next up, backdrops for recently added. [captionOnFocusOnly] hides the
 * titles until a card has focus.
 */
private data class HomeRow(
    val title: String,
    val items: List<BaseItemDto>,
    val art: CardArt = CardArt.Poster,
    val captionOnFocusOnly: Boolean = false,
    /** Tiles start playback straight away (Continue watching, Next up) instead of opening the title page. */
    val playOnClick: Boolean = false,
)

// Netflix-sized: wide cards are ~1/3 of the screen width, the focused card grows over its neighbours.
private const val HomeCardWidth = 320
private const val HomeCardHeight = 180

@OptIn(ExperimentalFoundationApi::class)
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
    // Keep the focused card's title clear of the bottom edge. Focus rectangles cover the poster only.
    val bottomMarginPx = with(LocalDensity.current) { 150.dp.toPx() }
    // ...and the focused row's heading clear of the top bar, which sits over the page.
    val topMarginPx = with(LocalDensity.current) { (NavRailHeight + 48.dp).toPx() }
    val pageSpec = remember(topMarginPx, bottomMarginPx) { EdgeBringIntoViewSpec(topMarginPx, bottomMarginPx) }
    // The hero is the first item of the page, so it scrolls away with the rows, as on Netflix.
    CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // The hero is the first item of the page, so it scrolls away with the rows, as on Netflix.
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

        // Rows in Netflix's order: what you're watching, then what's new.
        // The top row (first on screen) sends DPAD-up to the hero.
        val rows = buildList {
            if (state.continueWatching.isNotEmpty()) add(HomeRow("Continue watching", state.continueWatching, captionOnFocusOnly = true, playOnClick = true))
            if (state.nextUp.isNotEmpty()) add(HomeRow("Next up", state.nextUp, captionOnFocusOnly = true, playOnClick = true))
            if (state.recentShows.isNotEmpty()) add(HomeRow("Recently added TV shows", state.recentShows, CardArt.Backdrop, captionOnFocusOnly = true))
            if (state.recentMovies.isNotEmpty()) add(HomeRow("Recently added movies", state.recentMovies, CardArt.Backdrop, captionOnFocusOnly = true))
        }
        rows.forEachIndexed { index, row ->
            item(key = row.title) {
                RowSection(
                    row.title,
                    row.items,
                    onClick = onOpenDetails,
                    onPlay = if (row.playOnClick) onPlay else null,
                    onFocusChange = onRowCardFocusChange,
                    onDpadUp = if (index == 0) onRowDpadUp else null,
                    art = row.art,
                    captionOnFocusOnly = row.captionOnFocusOnly,
                )
            }
        }
        if (state.libraries.isNotEmpty()) {
            item {
                LibraryRow(
                    state.libraries,
                    onOpen = onOpenLibrary,
                    onFocusChange = onRowCardFocusChange,
                    onDpadUp = if (rows.isEmpty()) onRowDpadUp else null,
                )
            }
        }

        if (state.loading && state.continueWatching.isEmpty() && state.libraries.isEmpty()) {
            item {
                Text(
                    "Loading your library…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB3B3B3),
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

/** Letters and digits for the on-screen search keyboard, in rows of six. */
private val SearchKeyRows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ0123", "456789")

/**
 * Netflix-style search: an on-screen keyboard on the left (so focus stays in the app, not in the
 * system keyboard), and results on the right as two-wide poster rows.
 *
 * Every key has its own focus target linked to its neighbours, so DPAD moves predictably and the
 * top row of keys returns to the nav.
 */
@Composable
private fun SearchTab(
    state: HomeViewModel.State,
    listState: LazyListState,
    onQuery: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    searchFocus: FocusRequester,
    onUpToNav: () -> Unit,
    navFocus: FocusRequester,
) {
    val query = state.searchQuery
    val keyFocus = remember { List(SearchKeyRows.size) { List(6) { FocusRequester() } } }
    val bottomFocus = remember { List(3) { FocusRequester() } }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = NavRailHeight, start = EdgePad, end = EdgePad),
    ) {
        Column(modifier = Modifier.width(360.dp)) {
            Text(
                query.ifEmpty { "Search" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (query.isEmpty()) Color(0xFF8C8C8C) else Color.White,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color(0xFF2F2F2F))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchKeyRows.forEachIndexed { row, keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEachIndexed { column, ch ->
                            SearchKey(
                                label = ch.toString(),
                                modifier = Modifier.focusRequester(
                                    if (row == 0 && column == 0) searchFocus else keyFocus[row][column],
                                ),
                                up = if (row == 0) navFocus else keyFocus[row - 1][column],
                                down = if (row < SearchKeyRows.lastIndex) keyFocus[row + 1][column] else bottomFocus[bottomIndex(column)],
                                left = if (column > 0) keyFocus[row][column - 1] else null,
                                right = if (column < keys.lastIndex) keyFocus[row][column + 1] else null,
                                onClick = { onQuery(query + ch) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchKey(
                        label = "Space", wide = 110, modifier = Modifier.focusRequester(bottomFocus[0]),
                        up = keyFocus[SearchKeyRows.lastIndex][0], down = null, left = null, right = bottomFocus[1],
                        onClick = { onQuery("$query ") },
                    )
                    SearchKey(
                        label = "Delete", wide = 110, modifier = Modifier.focusRequester(bottomFocus[1]),
                        up = keyFocus[SearchKeyRows.lastIndex][2], down = null, left = bottomFocus[0], right = bottomFocus[2],
                        onClick = { onQuery(query.dropLast(1)) },
                    )
                    SearchKey(
                        label = "Clear", wide = 110, modifier = Modifier.focusRequester(bottomFocus[2]),
                        up = keyFocus[SearchKeyRows.lastIndex][5], down = null, left = bottomFocus[1], right = null,
                        onClick = { onQuery("") },
                    )
                }
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                query.isBlank() -> Text(
                    "Search for movies and shows in your library.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFA8ADBD),
                )
                state.searching -> Text("Searching…", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFA8ADBD))
                state.searchError != null -> Text(
                    "Search failed: ${state.searchError}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                state.searchResults.isEmpty() -> Text(
                    "No results for \"$query\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFA8ADBD),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(state.searchResults.chunked(2), key = { index, row -> "$index-${row.first().id}" }) { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                            row.forEachIndexed { column, entry ->
                                MediaCard(
                                    item = entry,
                                    onClick = { onOpenDetails(entry.id.toString()) },
                                    onDpadUp = if (rowIndex == 0) onUpToNav else null,
                                    blockRight = column == row.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Which of the three bottom keys sits above a given column of the letter grid. */
private fun bottomIndex(column: Int): Int = when {
    column <= 1 -> 0
    column <= 3 -> 1
    else -> 2
}

/** Message for a list with nothing in it: an icon and one line of text. */
@Composable
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier.padding(horizontal = EdgePad, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Movie,
            contentDescription = null,
            tint = Color(0xFF6D6D6E),
            modifier = Modifier.size(48.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB3B3B3))
    }
}

/** One key of the on-screen search keyboard, with explicit focus links to its neighbours. */
@Composable
private fun SearchKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    wide: Int = 44,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = wide.dp, height = 44.dp)
            .focusProperties {
                this.up = up ?: FocusRequester.Default
                this.down = down ?: FocusRequester.Default
                this.left = left ?: FocusRequester.Default
                this.right = right ?: FocusRequester.Default
            },
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF2F2F2F),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Vertical list of poster carousels. Used by the TV Shows and Movies tabs. */
@Composable
private fun PosterGrid(
    items: List<BaseItemDto>,
    listState: LazyListState,
    emptyText: String,
    onOpenDetails: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
    /** Attached to the first poster so the nav can move focus straight into the grid. */
    firstFocus: FocusRequester? = null,
    /** DPAD-up from the top row goes here (the nav). */
    onUpToNav: (() -> Unit)? = null,
) {
    // Rows and the header carry their own side padding, so every edge lines up at EdgePad.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = NavRailHeight, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.let { h -> item { Box(Modifier.padding(horizontal = EdgePad)) { h() } } }
        if (items.isEmpty()) {
            item { EmptyState(emptyText) }
        }
        // Fixed grid: five posters per row, rows stack downwards. Nothing scrolls sideways.
        itemsIndexed(items.chunked(PosterGridColumns), key = { index, row -> "$index-${row.first().id}" }) { rowIndex, row ->
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = EdgePad)) {
                val cardWidth = ((maxWidth - PosterGridGap * (PosterGridColumns - 1)) / PosterGridColumns)
                val cardHeight = cardWidth * PosterAspect
                Row(horizontalArrangement = Arrangement.spacedBy(PosterGridGap)) {
                    row.forEachIndexed { column, entry ->
                        val isFirst = rowIndex == 0 && column == 0
                        MediaCard(
                            item = entry,
                            onClick = { onOpenDetails(entry.id.toString()) },
                            modifier = if (isFirst && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                            onDpadUp = if (rowIndex == 0) onUpToNav else null,
                            blockRight = column == row.lastIndex,
                            widthDp = cardWidth.value.toInt(),
                            heightDp = cardHeight.value.toInt(),
                            // Netflix-style: titles appear on the focused poster only.
                            captionOnFocusOnly = true,
                        )
                    }
                }
            }
        }
    }
}

/** Posters per row on the TV Shows and Movies pages. */
private const val PosterGridColumns = 5
private val PosterGridGap = 20.dp
/** Poster height / width (272 / 160). */
private const val PosterAspect = 1.7f

@Composable
private fun RowSection(
    title: String,
    items: List<BaseItemDto>,
    onClick: (String) -> Unit,
    /** When set, a tile starts playing the item instead of calling [onClick]. */
    onPlay: ((BaseItemDto) -> Unit)? = null,
    onFocusChange: ((BaseItemDto, Boolean) -> Unit)? = null,
    onDpadUp: (() -> Unit)? = null,
    art: CardArt = CardArt.Poster,
    captionOnFocusOnly: Boolean = false,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = Color.White,
            modifier = Modifier.padding(start = EdgePad, end = EdgePad, bottom = 4.dp),
        )
        CarouselRow {
            itemsIndexed(items, key = { index, item -> "$index-${item.id}" }) { _, item ->
                MediaCard(
                    item = item,
                    onClick = { if (onPlay != null) onPlay(item) else onClick(item.id.toString()) },
                    onFocusChange = onFocusChange,
                    onDpadUp = onDpadUp,
                    widthDp = HomeCardWidth,
                    heightDp = HomeCardHeight,
                    art = art,
                    captionOnFocusOnly = captionOnFocusOnly,
                    label = cardTitle(item),
                    subtitle = cardSubtitle(item),
                    rating = cardRottenTomatoes(item),
                )
            }
        }
    }
}

/**
 * Rotten Tomatoes score for shows and films, from Jellyfin's critic rating (filled by a metadata plugin).
 * Episodes have no score of their own, so they get none.
 */
private fun cardRottenTomatoes(item: BaseItemDto): Int? {
    if (item.type == BaseItemKind.EPISODE) return null
    return item.criticRating?.let { Math.round(it) }?.takeIf { it in 0..100 }
}

/** Shows show name for episodes, so a card always says what it is. */
private fun cardTitle(item: BaseItemDto): String =
    if (item.type == BaseItemKind.EPISODE) item.seriesName ?: item.name.orEmpty() else item.name.orEmpty()

/**
 * "S1 E4 · Episode title" for episodes. For shows and movies, Netflix's line: genre · year · seasons · rating,
 * e.g. "TV Dramas · 2011 · 7 Seasons · TV-MA". Missing parts are skipped.
 */
private fun cardSubtitle(item: BaseItemDto): String? {
    if (item.type != BaseItemKind.EPISODE) {
        val seasons = if (item.type == BaseItemKind.SERIES) item.childCount?.takeIf { it > 0 } else null
        return listOfNotNull(
            item.genres?.firstOrNull()?.takeIf { it.isNotBlank() },
            item.productionYear?.toString(),
            seasons?.let { if (it == 1) "1 Season" else "$it Seasons" },
            item.officialRating?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null }
    }
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    val code = when {
        season != null && episode != null -> "S$season E$episode"
        episode != null -> "E$episode"
        else -> null
    }
    return listOfNotNull(code, item.name?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null }
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
                    widthDp = 160,
                    heightDp = 93,
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
    val padPx = with(LocalDensity.current) { EdgePad.toPx() }
    val rowSpec = remember(padPx) { EdgeBringIntoViewSpec(padPx, padPx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            content = content,
        )
    }
}

/**
 * Netflix-style scrolling for one axis. Nothing moves while the focused item is inside
 * the visible band; otherwise it moves the minimum needed to bring it in. The band is
 * the container minus [startPx] and [endPx], so padding and bottom margins are respected.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class EdgeBringIntoViewSpec(
    private val startPx: Float,
    private val endPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visibleStart = startPx
        val visibleEnd = containerSize - endPx
        return when {
            offset < visibleStart -> offset - visibleStart
            offset + size > visibleEnd -> offset + size - visibleEnd
            else -> 0f
        }
    }
}

/**
 * Runs [onResume] each time the screen comes back on top, so rows and progress reflect what was
 * watched in the player, not what was loaded when the screen first opened.
 */
@Composable
internal fun OnScreenResumed(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onResume)
    // The first resume is the one the screen's own load already covers, so skip it.
    val firstResume = remember { BooleanArray(1) { true } }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume[0]) firstResume[0] = false else latest()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
