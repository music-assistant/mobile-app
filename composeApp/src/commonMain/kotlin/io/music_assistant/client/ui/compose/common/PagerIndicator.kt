package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.MoveUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun HorizontalPagerIndicator(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    onItemMoved: ((Int) -> Unit)?,
) {
    val pageCount = pagerState.pageCount
    val coroutineScope = rememberCoroutineScope()

    // Animation states
    val verticalOffset = remember { Animatable(0f) }
    val swapProgress = remember { Animatable(0f) }
    var animationState by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun moveWithAnimation(indexShift: Int) {
        val fromIndex = pagerState.currentPage
        val toIndex = (fromIndex + indexShift).coerceIn(0, pageCount - 1)

        if (fromIndex == toIndex) {
            onItemMoved?.invoke(indexShift)
            return
        }

        coroutineScope.launch {
            animationState = fromIndex to toIndex

            // Phase 1: Pull down (166ms)
            verticalOffset.animateTo(
                targetValue = 4f,
                animationSpec = tween(durationMillis = 166)
            )

            // Phase 2: Swap horizontally (168ms)
            swapProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 168)
            )

            // Phase 3: Pull up (166ms)
            verticalOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 166)
            )

            // Reset
            swapProgress.snapTo(0f)
            animationState = null
            onItemMoved?.invoke(indexShift)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        onItemMoved?.let {
            IconButton(
                modifier = Modifier.size(32.dp),
                enabled = pagerState.currentPage > 0,
                onClick = { moveWithAnimation(-1) }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Default.MoveUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                        .copy(alpha = if (pagerState.currentPage > 0) 1f else 0.4f),
                )
            }
        }

        if (pageCount <= 15) {
            repeat(pageCount) { index ->
                val (fromIndex, toIndex) = animationState ?: (null to null)
                val isFrom = index == fromIndex
                val isTo = index == toIndex

                // Calculate offsets
                val xOffset = when {
                    isFrom && toIndex != null -> {
                        val direction = if (toIndex > fromIndex) 1f else -1f
                        16f * direction * swapProgress.value
                    }

                    isTo && fromIndex != null -> {
                        val direction = if (fromIndex > toIndex) 1f else -1f
                        16f * direction * swapProgress.value
                    }

                    else -> 0f
                }

                val yOffset = if (isFrom) verticalOffset.value else 0f

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .offset(x = xOffset.dp, y = yOffset.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.3f
                                )
                        )
                )
            }
        } else {
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        onItemMoved?.let {
            IconButton(
                modifier = Modifier.size(32.dp),
                enabled = pagerState.currentPage < pageCount - 1,
                onClick = { moveWithAnimation(1) }
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Default.MoveDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                        .copy(alpha = if (pagerState.currentPage < pageCount - 1) 1f else 0.4f),
                )
            }
        }
    }
}

@Preview
@Composable
fun HorizontalPagerIndicatorNumbersPreview() {
    MaterialTheme {
        HorizontalPagerIndicator(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 16 }, initialPage = 2),
            onItemMoved = {},
        )
    }
}

@Preview
@Composable
fun HorizontalPagerIndicatorDotsPreview() {
    MaterialTheme {
        HorizontalPagerIndicator(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 9 }, initialPage = 2),
            onItemMoved = {},
        )
    }
}