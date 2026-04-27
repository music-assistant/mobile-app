// Compose layout values (dot size, spacing) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HorizontalPagerIndicator(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
) {
    val pageCount = pagerState.pageCount

    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pageCount <= 15) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 4.dp else 2.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.3f,
                                )
                            },
                        ),
                )
            }
        } else {
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
        )
    }
}
