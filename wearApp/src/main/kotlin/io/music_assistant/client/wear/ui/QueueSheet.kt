// Compose layout values (handle size, spacing, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnFirstLayoutItemProvider
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScreenScaffoldDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.WearQueueItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** Where the sheet rests: down, with only its handle showing, or over the whole screen. */
internal enum class QueueSheetValue { Hidden, Expanded }

/**
 * How tall the line's own part of the handle row is. It is a drag target before it is a line,
 * which is what it is this much taller than the four pixels it draws.
 */
internal val QUEUE_HANDLE_HEIGHT = 16.dp

/**
 * How far the queue's first row keeps below the handle while the sheet is up.
 *
 * Its own gap and not [QUEUE_HANDLE_MARGIN]: that one is measured against the bottom of the
 * glass, this one against a row of text, and there is no reason for them to be the same.
 */
internal val QUEUE_HANDLE_GAP = -8.dp

/** How far the handle keeps off the bottom edge of the screen while the sheet is down. */
internal val QUEUE_HANDLE_MARGIN = 8.dp

/**
 * How far up the screen the sheet reaches while it is down: the handle and the margin under
 * it, and nothing else. The margin is where the sheet parks rather than space inside the
 * handle, so it has nothing to do with what the handle is drawn over when the sheet is up.
 *
 * The now-playing screen reads this too: its rim button rides a circle this much further
 * inside the bezel, so the one control at the bottom of that screen and the handle below it
 * never share a tap target.
 */
internal val QUEUE_HANDLE_REACH = QUEUE_HANDLE_HEIGHT + QUEUE_HANDLE_MARGIN

/**
 * Where the queue sheet is heading.
 *
 * The sheet is always in the composition - its handle is what shows at the bottom of the
 * now-playing screen - so this is only the anchor it is aimed at. A gesture that settles it
 * somewhere else writes that back, so [open] and [close] always have something true to change.
 */
@Stable
class QueueSheetState internal constructor() {
    internal val draggable = AnchoredDraggableState(initialValue = QueueSheetValue.Hidden)

    internal var target by mutableStateOf(QueueSheetValue.Hidden)
        private set

    /** True from the moment the queue is on its way up until it is on its way down again. */
    val isOpen: Boolean get() = target == QueueSheetValue.Expanded

    fun open() {
        target = QueueSheetValue.Expanded
    }

    fun close() {
        target = QueueSheetValue.Hidden
    }

    /** The sheet came to rest at [settled], whoever put it there. */
    internal fun onSettled(settled: QueueSheetValue) {
        target = settled
    }
}

@Composable
fun rememberQueueSheetState(): QueueSheetState = remember { QueueSheetState() }

/**
 * The queue of the selected player, as a window around the track that is playing, drawn as a
 * sheet over the now-playing screen.
 *
 * A tap plays the row; a swipe reveals Remove. The row that is playing is drawn filled, so
 * the user's place in the queue is readable at a glance on a small screen.
 *
 * The sheet is never taken out of the composition: at rest it is parked a screen down bar its
 * handle, which is the line along the bottom of the now-playing screen and is the only thing
 * there is to pull the queue up by. What shows there is the handle and nothing else, because
 * `TransformingLazyColumn` is a pointer target over the whole of its bounds whether or not it
 * is scrollable: any dead space in that strip would swallow the taps meant for the rim button
 * of the screen behind it. That is also why the sheet stops below the clock rather than at the
 * top of the screen - the band the clock needs is the sheet's own top edge, not padding inside
 * a list that reaches further down the screen than it draws. The handle is the first item of the list rather than a bar
 * pinned above it, so scrolling into the queue carries it off the top like any other row - and
 * a sheet that settles back down is scrolled home, so the handle is there to grab again.
 *
 * Its background is only as opaque as the sheet is up: at rest the cover art shows through the
 * strip the handle sits in, and nothing but the handle is drawn over the now-playing screen.
 *
 * Three gestures move it, and all three end at the same anchors: the handle itself, a drag of
 * the list past the point where it has nothing left to scroll, and the rotary crown, which
 * scrolls the list first and then carries the sheet down with it.
 *
 * Wear has no sheet component of its own, so the anchors, the gesture hand-off and the
 * settling are all built here on [AnchoredDraggableState].
 */
@Composable
fun QueueSheet(
    state: WearUiState,
    sheetState: QueueSheetState,
    loadArtwork: suspend (String) -> ImageBitmap?,
    swipeAwayPx: () -> Int = { 0 },
    onPlayItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
) {
    val draggable = sheetState.draggable
    // The band the clock sits in, which is where the sheet stops rather than what it hides.
    val clockBand = ScreenScaffoldDefaults.contentPadding.calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    // The sheet covers the screen when it is up, so what is left showing when it is down is
    // measured from its top edge: the band the list keeps clear of the clock, then the handle,
    // then the margin that keeps the handle off the bottom of the glass.
    val restingPx = with(density) { (clockBand + QUEUE_HANDLE_REACH).roundToPx() }
    val parked = with(density) { (screenHeight - clockBand - QUEUE_HANDLE_REACH).roundToPx() }
    val flingVelocity = with(density) { FLING_VELOCITY.toPx() }
    // Where the list's own content starts: below the clock and below the handle, which sits
    // over the list rather than in it.
    val contentTop = clockBand + QUEUE_HANDLE_HEIGHT + QUEUE_HANDLE_GAP
    val clockBandPx = with(density) { clockBand.roundToPx() }
    // How far above the first row the handle rides: the whole of it, and then the gap.
    val handleRisePx = with(density) { (QUEUE_HANDLE_HEIGHT + QUEUE_HANDLE_GAP).roundToPx() }
    val background = MaterialTheme.colorScheme.background

    // The anchors come from the measured height, in the measure pass, so the first placement
    // already has them: a guess made from the configuration's rounded screen height would be a
    // pixel or two out, and the sheet would visibly jump to its anchor on the frame after it
    // first drew. Until the first measure there are none, and every drag is a no-op.
    val anchored by remember(draggable) { derivedStateOf { draggable.anchors.size > 0 } }
    // Anything above the hidden anchor: the list only takes gestures, and the crown only
    // reaches it, once the sheet is off the bottom. Otherwise it would eat the touches that
    // belong to the now-playing screen behind it.
    val lifted by remember(draggable) {
        derivedStateOf { draggable.liftedFraction() > 0f }
    }
    // Settled at the top, rather than merely off the bottom: this is what makes the queue the
    // screen the clock belongs to, and handing it over while anything is still moving hands it
    // a list that is mid-gesture. `scrollAway` hides the clock outright - instantly, no
    // animation - for an anchor offset it cannot read or that is past its range, and only
    // gives it back once the screen stage leaves Scrolling. Registering on the first pixel of
    // a lift opens exactly that window, and the clock is sometimes already gone by the time
    // the sheet arrives.
    val settledOpen by remember(draggable) {
        derivedStateOf { draggable.settledValue == QueueSheetValue.Expanded }
    }
    // Aimed anywhere but the top: at rest, and from the first frame of a close rather than its
    // last. The list's resting layout is what the handle's place in the strip depends on, and
    // establishing it only once the sheet has arrived moves the handle under a sheet that has
    // already stopped - which reads as the handle jumping rather than as the queue closing.
    val pinned by remember(draggable) {
        derivedStateOf { draggable.targetValue != QueueSheetValue.Expanded }
    }

    // On Wear the system back gesture is the swipe from the left edge, and the nav host lets
    // it through at the start destination - where it would leave the app. While the queue is
    // up it closes the queue instead, and while the queue is down it is not ours to take.
    BackHandler(enabled = sheetState.isOpen) { sheetState.close() }

    LaunchedEffect(anchored, sheetState.target) {
        if (anchored) draggable.animateTo(sheetState.target)
    }

    val listState = rememberTransformingLazyColumnState()
    // Wherever the sheet comes to rest is what it was aimed at, so a drag, a fling or the
    // crown leaves [QueueSheetState] able to move it again.
    LaunchedEffect(sheetState) {
        snapshotFlow { draggable.settledValue }.collect(sheetState::onSettled)
    }

    val rotaryScroll = rememberQueueRotaryScroll(listState, draggable, flingVelocity)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                draggable.updateAnchors(
                    newAnchors = DraggableAnchors {
                        QueueSheetValue.Expanded at 0f
                        QueueSheetValue.Hidden at (placeable.height - restingPx).toFloat()
                    },
                    // A re-measure must not re-aim the sheet at whichever anchor it happens
                    // to be nearest while it is moving.
                    newTarget = draggable.settledValue,
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            // The sheet is placed down the screen rather than sized to it, so what is behind
            // it stays visible above while it moves.
            .offset {
                val at = draggable.offset.takeUnless(Float::isNaN)?.roundToInt() ?: parked
                // The handle belongs to the controls: when they are swiped off the bottom for
                // the album page, it goes with them. The sheet is not one of the pages - it
                // covers them when it is up - so it has to be carried by hand.
                IntOffset(0, at + swipeAwayPx())
            }
            // Read in the draw phase, so following the finger costs no recomposition.
            .drawBehind { drawRect(background, alpha = draggable.liftedFraction()) }
            .nestedScroll(rememberSheetNestedScroll(draggable, anchored, flingVelocity)),
    ) {
        // Only while it is off the bottom. The list's bounds have to reach the top of the
        // sheet - that is where its rows end, and anything less leaves them vanishing into a
        // band of sheet before they reach the edge of the screen - and bounds that reach the
        // top of the sheet lie over the rim button of the screen behind it while the sheet is
        // down. It is the handle, not the list, that has to be there at rest.
        if (lifted) {
            QueueList(
                state = state,
                listState = listState,
                loadArtwork = loadArtwork,
                contentTop = contentTop,
                pinned = pinned,
                rotaryScroll = rotaryScroll,
                settledOpen = settledOpen,
                onPlayItem = onPlayItem,
                onRemoveItem = onRemoveItem,
            )
        }
        // Over the list rather than in it, so where it lands is this offset and nothing else -
        // no lazy column's idea of which item to lay out from, on any pass. It still goes up
        // with the queue: the first row's offset is how far the list has scrolled, and the
        // handle rides [QUEUE_HANDLE_GAP] above it. With the list gone it rests where that
        // content would have begun.
        QueueHandle(
            modifier = Modifier
                .offset {
                    val top = listState.layoutInfo.visibleItems
                        .firstOrNull()
                        ?.takeIf { lifted }
                        ?.let { it.offset - handleRisePx }
                        ?: clockBandPx
                    IntOffset(0, top)
                }
                .anchoredDraggable(draggable, Orientation.Vertical),
        )
    }
}

/**
 * The speed at or past which a flick decides for itself, rather than the sheet going to
 * whichever anchor it happens to be nearer. It is the same 125 dp/s that
 * `Modifier.anchoredDraggable` uses for the handle's own drag.
 */
private val FLING_VELOCITY = 125.dp

/**
 * Puts the sheet at an anchor after a gesture the list could not use.
 *
 * Past [flingVelocity] it goes the way it was thrown; below it the sheet has been placed
 * rather than flicked, and it settles at the anchor it is closest to.
 *
 * `AnchoredDraggableState.settle(velocity)` would do both in one call, but it belongs to the
 * constructor that takes its own thresholds and throws on the one used here.
 */
private suspend fun AnchoredDraggableState<QueueSheetValue>.settleAt(
    velocity: Float,
    flingVelocity: Float,
) {
    if (offset.isNaN()) return
    when {
        velocity >= flingVelocity -> animateTo(QueueSheetValue.Hidden)
        velocity <= -flingVelocity -> animateTo(QueueSheetValue.Expanded)
        else -> settle(AnchoredDraggableDefaults.SnapAnimationSpec)
    }
}

/**
 * How far up the sheet is: 0 while it rests at the hidden anchor, 1 while it covers the
 * screen. It is the sheet's own position rather than a progress between whichever two anchors
 * a drag is between, which with these two anchors is the same thing but does not read as one.
 */
private fun AnchoredDraggableState<QueueSheetValue>.liftedFraction(): Float {
    val down = anchors.positionOf(QueueSheetValue.Hidden)
    val up = anchors.positionOf(QueueSheetValue.Expanded)
    val at = offset
    if (at.isNaN() || down.isNaN() || up.isNaN() || down <= up) return 0f
    return ((down - at) / (down - up)).coerceIn(0f, 1f)
}

@Composable
private fun QueueList(
    state: WearUiState,
    listState: TransformingLazyColumnState,
    loadArtwork: suspend (String) -> ImageBitmap?,
    contentTop: Dp,
    pinned: Boolean,
    rotaryScroll: ScrollableState,
    settledOpen: Boolean,
    onPlayItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
) {
    val spec = rememberTransformationSpec()
    val contentTopPx = with(LocalDensity.current) { contentTop.roundToPx() }
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(rotaryScroll)
    val layoutDirection = LocalLayoutDirection.current
    // The scaffold registers this list with the AppScaffold, which is what scrolls the clock
    // away by it and what shows and hides the scroll indicator. Registration is gated on
    // [LocalScreenIsActive], and the sheet is only a screen once it has arrived: left ungated,
    // the clock would be driven by a queue parked off the bottom of the display - which is what
    // shifted it on the first layout, before any of it had ever been on screen.
    CompositionLocalProvider(LocalScreenIsActive provides settledOpen) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                // Padding inside the column, not on it: the column's bounds reach the top of
                // the sheet, so a row that scrolls out of them has reached the top of the
                // screen rather than ending in a band of sheet above it.
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    top = contentTop,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                // While the sheet is not aimed at the top, the handle is the whole of it that
                // shows, and where it lands cannot be left to the column: the column lays out
                // from the item nearest the middle of its viewport, and on its very first pass
                // it has no layout to read that from and starts the anchor at the middle of the
                // screen. The handle would appear halfway down the sheet and snap to the top on
                // the pass after. Pinned to the top instead, it is in the same place on every
                // pass, the first included - and a close puts the queue back to its top as it
                // starts, rather than re-laying the list out once the sheet has stopped moving.
                //
                // Aimed at the top, the column goes back to its own anchoring, handed the layout
                // this left it in, so it carries on from where it is.
                firstLayoutItemProvider = { centerItem ->
                    if (!pinned) {
                        centerItem
                    } else {
                        TransformingLazyColumnFirstLayoutItemProvider.ItemInfo(
                            index = 0,
                            itemEdge = TransformingLazyColumnFirstLayoutItemProvider.ItemEdge.Start,
                            offset = contentTopPx,
                        )
                    }
                },
                // The column's own rotary handling would scroll the list and stop at its top.
                // [rotaryScroll] does that and then keeps going into the sheet.
                rotaryScrollableBehavior = null,
                verticalArrangement = Arrangement.spacedBy(CHAIN_GAP, Alignment.Top),
                modifier = Modifier
                    .fillMaxWidth()
                    // The focus the crown needs, taken whenever this screen is the active one -
                    // which is how the column itself asks for it when it handles its own rotary.
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(behavior = rotaryBehavior, focusRequester = focusRequester),
            ) {
                    item {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(stringResource(R.string.queue_title))
                    }
                }
                if (state.queue.items.isEmpty()) {
                    item {
                        QueuePlaceholder(isLoading = state.queue.isLoading)
                    }
                }
                itemsIndexed(
                    state.queue.items,
                    key = { _, queueItem -> queueItem.id },
                ) { index, queueItem ->
                    SwipeToReveal(
                        modifier = Modifier.transformedHeight(this, spec),

                        primaryAction = {
                            PrimaryActionButton(
                                onClick = { onRemoveItem(queueItem.id) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                    )
                                },
                                text = { Text(stringResource(R.string.cd_remove)) },
                            )
                        },
                        onSwipePrimaryAction = { onRemoveItem(queueItem.id) },
                    ) {
                        QueueRow(
                            item = queueItem,
                            isPlaying = queueItem.id == state.activeQueueItemId,
                            loadArtwork = loadArtwork,
                            transformation = SurfaceTransformation(spec),
                            onClick = { onPlayItem(queueItem.id) },
                        )
                    }
                }
                item {
                    EdgeButton(
                        onClick = {},
                    ) {
                        Text("haaj")
                    }
                }
            }
        }
    }
}

/**
 * The line the sheet is dragged by, at the top of the list and part of it.
 *
 * It is [QUEUE_HANDLE_HEIGHT] tall and no taller, because while the sheet is down it is
 * the only part of it that can take a gesture, and the rim button of the now-playing screen
 * sits just above it.
 *
 * It carries no transformation spec: the morphing a [SurfaceTransformation] applies is meant
 * for the surfaces of rows, and a four-pixel line that shrinks as it nears the bezel reads as
 * a rendering fault rather than as a handle.
 */
@Composable
private fun QueueHandle(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.cd_queue)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(QUEUE_HANDLE_HEIGHT)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 16.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

/**
 * Hands the part of a touch scroll that the list cannot use to the sheet.
 *
 * Downwards it is the leftover of a gesture the list has already refused, which is what makes
 * a drag down from the top of the queue close it. Upwards it is taken before the list sees it
 * at all, so a sheet that is hanging part way down is pulled back up before the queue starts
 * scrolling under it.
 */
@Composable
private fun rememberSheetNestedScroll(
    draggable: AnchoredDraggableState<QueueSheetValue>,
    anchored: Boolean,
    flingVelocity: Float,
): NestedScrollConnection = remember(draggable, anchored, flingVelocity) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (anchored && source == NestedScrollSource.UserInput && available.y < 0f) {
                Offset(0f, draggable.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset =
            if (anchored && source == NestedScrollSource.UserInput) {
                Offset(0f, draggable.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val offset = draggable.offset
            val hanging = anchored && !offset.isNaN() && offset > draggable.anchors.minPosition()
            return if (hanging && available.y < 0f) {
                draggable.settleAt(available.y, flingVelocity)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!anchored) return Velocity.Zero
            draggable.settleAt(available.y, flingVelocity)
            return available
        }
    }
}

/**
 * The crown: the queue first, the sheet after.
 *
 * Rotary never goes through nested scroll, so the hand-off the touch gestures get for free is
 * built here instead - as one [ScrollableState] that feeds the list and spends whatever is
 * left of a backwards turn on the sheet's offset. A turn is one scroll for as long as the
 * crown is moving, so the end of it is where a sheet left between anchors is settled.
 *
 * While the sheet is down the crown does nothing, as it does on the now-playing screen the
 * sheet is sitting on: there is no list to scroll there, only a handle.
 */
@Composable
private fun rememberQueueRotaryScroll(
    listState: TransformingLazyColumnState,
    draggable: AnchoredDraggableState<QueueSheetValue>,
    flingVelocity: Float,
): ScrollableState {
    val rotaryScroll = remember(listState, draggable) {
        ScrollableState { delta ->
            if (draggable.liftedFraction() <= 0f) return@ScrollableState 0f
            val byList = listState.dispatchRawDelta(delta)
            val left = delta - byList
            val bySheet = if (left < 0f) {
                // Backwards past the top of the queue: the sheet goes down by what is left.
                -draggable.dispatchRawDelta(-left)
            } else {
                0f
            }
            byList + bySheet
        }
    }
    LaunchedEffect(rotaryScroll, draggable) {
        snapshotFlow { rotaryScroll.isScrollInProgress }
            .distinctUntilChanged()
            .dropWhile { !it }
            .filter { !it }
            .collect { draggable.settleAt(velocity = 0f, flingVelocity = flingVelocity) }
    }
    return rotaryScroll
}

@Composable
private fun QueueRow(
    item: WearQueueItem,
    isPlaying: Boolean,
    loadArtwork: suspend (String) -> ImageBitmap?,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        transformation = transformation,
        colors = if (isPlaying) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = if (isPlaying) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
        } else {
            { QueueArtwork(item.imageUrl, loadArtwork) }
        },
        secondaryLabel = item.artist?.let { artist ->
            {
                Text(text = artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        label = {
            Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

/** What keeps two links of the chain apart. */
private val CHAIN_GAP = 2.dp

/**
 * The cover for one row, fetched through the watch's own `ArtworkLoader` rather than an image
 * library: the phone app's Coil has no network fetcher of its own - it is handed bytes by a
 * repository this module does not have - so a URL given straight to it here would quietly
 * resolve to nothing.
 *
 * The note stands in while the fetch is in flight and stays for a track the server has no
 * cover for, so a row is the same height either way and the list does not reflow under the
 * thumb as covers land.
 */
@Composable
private fun QueueArtwork(url: String?, load: suspend (String) -> ImageBitmap?) {
    val cover by produceState<ImageBitmap?>(null, url, load) {
        value = url?.let { load(it) }
    }
    cover?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(COVER_CORNER)),
        )
    } ?: Icon(
        painter = painterResource(R.drawable.ic_music_note),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
}

/** Box art is square, so its corners are the chain's and not the button's. */
private val COVER_CORNER = 4.dp

@Composable
private fun QueuePlaceholder(isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    } else {
        Text(
            text = stringResource(R.string.queue_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
