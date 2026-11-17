package io.music_assistant.client.ui.compose.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColors
import io.music_assistant.client.ui.compose.common.ExtractedColorsSource
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.item_description_more
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private val NoColors = object : ExtractedColorsSource {
    override fun peek(imageUrl: String): ExtractedColors? = null
    override suspend fun fetch(imageUrl: String): ExtractedColors? = null
}

private fun ComposeContentTestRule.setInspectableContent(content: @Composable () -> Unit) =
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true, content = content)
    }

/**
 * The "More" label follows the text layout: only the native graphics report the ellipsis of a
 * clamped text, so this test runs apart from the legacy-mode [ItemDetailsTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemDescriptionBlockTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `more opens the full text in a sheet`() {
        val text = "A long review line. ".repeat(100).trim()
        val album = AppMediaItemFixtures.album().copy(metadata = AppMediaItemFixtures.metadata(review = text))

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithText(Res.string.item_description_more.get()).performScrollTo().performClick()

        composeTestRule.onAllNodes(hasText(text)).assertCountEquals(2)
    }
}
