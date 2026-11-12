package com.jellyflix.tv.ui

import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
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

/** Where to resume from, in milliseconds (0 when the item has never been started). */
private fun BaseItemDto.resumeMs(): Long = (userData?.playbackPositionTicks ?: 0L) / 10_000L

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
    onPlay: (itemId: String, title: String, startMs: Long) -> Unit,
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
                            else onPlay(item.id.toString(), item.name.orEmpty(), item.resumeMs())
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
                    HomeTabs.MY_LIST -> MyListTab(
                        state = state,
                        listState = listState,
                        onOpenDetails = onOpenDetails,
                        firstFocus = gridFocusRequester,
                        onUpToNav = onUpToNav,
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
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

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
    val pageSpec = remember(bottomMarginPx) { EdgeBringIntoViewSpec(0f, bottomMarginPx) }
    // The hero is the first item of the page, so it scrolls away with the rows, as on Netflix.
    CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
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
                    landscape = true,
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
                    landscape = true,
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
                        label = "Back", wide = 110, modifier = Modifier.focusRequester(bottomFocus[1]),
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
                    itemsIndexed(state.searchResults.chunked(2), key = { _, row -> row.first().id.toString() }) { rowIndex, row ->
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

@Composable
private fun MyListTab(
    state: HomeViewModel.State,
    listState: LazyListState,
    onOpenDetails: (String) -> Unit,
    firstFocus: FocusRequester,
    onUpToNav: () -> Unit,
) {
    PosterGrid(
        items = state.favorites,
        listState = listState,
        firstFocus = firstFocus,
        onUpToNav = onUpToNav,
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
    /** Attached to the first poster so the nav can move focus straight into the grid. */
    firstFocus: FocusRequester? = null,
    /** DPAD-up from the top row goes here (the nav). */
    onUpToNav: (() -> Unit)? = null,
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
                Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB3B3B3))
            }
        }
        // Each group of twelve is a horizontal carousel, so cards past the screen edge scroll into view.
        itemsIndexed(items.chunked(12), key = { _, row -> row.first().id.toString() }) { rowIndex, row ->
            CarouselRow {
                itemsIndexed(row, key = { _, entry -> entry.id.toString() }) { column, entry ->
                    val isFirst = rowIndex == 0 && column == 0
                    MediaCard(
                        item = entry,
                        onClick = { onOpenDetails(entry.id.toString()) },
                        modifier = if (isFirst && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                        onDpadUp = if (rowIndex == 0) onUpToNav else null,
                        blockRight = column == row.lastIndex,
                    )
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
    /** Landscape 16:9 cards, used for Continue watching and Next up, where the art is episode stills. */
    landscape: Boolean = false,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(start = EdgePad, end = EdgePad, bottom = 4.dp),
        )
        CarouselRow {
            itemsIndexed(items, key = { _, item -> item.id.toString() }) { _, item ->
                MediaCard(
                    item = item,
                    onClick = { onClick(item.id.toString()) },
                    onFocusChange = onFocusChange,
                    onDpadUp = onDpadUp,
                    widthDp = if (landscape) 280 else 180,
                    heightDp = if (landscape) 158 else 270,
                    label = cardTitle(item),
                    subtitle = cardSubtitle(item),
                )
            }
        }
    }
}

/** Shows show name for episodes, so a card always says what it is. */
private fun cardTitle(item: BaseItemDto): String =
    if (item.type == BaseItemKind.EPISODE) item.seriesName ?: item.name.orEmpty() else item.name.orEmpty()

/** "S1 E4 · Episode title" for episodes; year for movies and shows. */
private fun cardSubtitle(item: BaseItemDto): String? {
    if (item.type != BaseItemKind.EPISODE) return item.productionYear?.toString()
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
private class EdgeBringIntoViewSpec(
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
