package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.testRadio
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemDetailsRadioTracksVisibilityTest {
    private fun state(
        isEndless: Boolean,
        isDynamic: Boolean = false,
    ) = ItemDetailsViewModel.State(
        itemState = DataState.Data(
            testRadio(isDynamic = isDynamic, isEndless = isEndless),
        ),
        albumsState = DataState.NoData(),
        playableItemsState = DataState.NoData(),
    )

    @Test
    fun `radio tracks tab appears for finite non-dynamic station`() {
        assertTrue(
            ItemDetailsTab.RADIO_TRACKS in state(isEndless = false).tabs,
        )
    }

    @Test
    fun `radio tracks tab is hidden for endless station`() {
        assertFalse(
            ItemDetailsTab.RADIO_TRACKS in state(isEndless = true).tabs,
        )
    }

    @Test
    fun `radio tracks tab is hidden for dynamic station`() {
        assertFalse(
            ItemDetailsTab.RADIO_TRACKS in
                    state(isEndless = false, isDynamic = true).tabs,
        )
    }

    @Test
    fun `radio versions tab is hidden when request returns no versions`() {
        val state = ItemDetailsViewModel.State(
            itemState = DataState.Data(testRadio(isEndless = false)),
            albumsState = DataState.NoData(),
            playableItemsState = DataState.NoData(),
            radioVersionsState = DataState.Data(emptyList()),
        )

        assertFalse(ItemDetailsTab.RADIO_VERSIONS in state.tabs)
    }

    @Test
    fun `radio versions tab appears when versions are available`() {
        val state = ItemDetailsViewModel.State(
            itemState = DataState.Data(testRadio(isEndless = false)),
            albumsState = DataState.NoData(),
            playableItemsState = DataState.NoData(),
            radioVersionsState =
                DataState.Data(listOf(testRadio(isEndless = false))),
        )

        assertTrue(ItemDetailsTab.RADIO_VERSIONS in state.tabs)
    }

    @Test
    fun `radio versions tab remains visible while loading`() {
        val state = ItemDetailsViewModel.State(
            itemState = DataState.Data(testRadio(isEndless = false)),
            albumsState = DataState.NoData(),
            playableItemsState = DataState.NoData(),
            radioVersionsState = DataState.Loading(),
        )

        assertTrue(ItemDetailsTab.RADIO_VERSIONS in state.tabs)
    }

    @Test
    fun `radio versions tab remains visible on request error`() {
        val state = ItemDetailsViewModel.State(
            itemState = DataState.Data(testRadio(isEndless = false)),
            albumsState = DataState.NoData(),
            playableItemsState = DataState.NoData(),
            radioVersionsState = DataState.Error(),
        )

        assertTrue(ItemDetailsTab.RADIO_VERSIONS in state.tabs)
    }
}
