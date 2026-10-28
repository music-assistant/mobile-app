package io.music_assistant.client.ui.compose.item
import io.music_assistant.client.data.model.client.testRadio
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemDetailsRadioTracksVisibilityTest {
    private fun state(
        isEndless: Boolean?,
        isDynamic: Boolean = false,
        supported: Boolean = true,
    ) = ItemDetailsViewModel.State(
        itemState = DataState.Data(
            testRadio(isDynamic = isDynamic, isEndless = isEndless),
        ),
        albumsState = DataState.NoData(),
        playableItemsState = DataState.NoData(),
        radioTracksSupported = supported,
    )

    @Test
    fun `radio tracks tab appears for supported finite non-dynamic station`() {
        assertTrue(
            ItemDetailsTab.RADIO_TRACKS in state(isEndless = false).tabs,
        )
    }

    @Test
    fun `radio tracks tab is hidden when server does not support it`() {
        assertFalse(
            ItemDetailsTab.RADIO_TRACKS in
                    state(isEndless = false, supported = false).tabs,
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
    fun `radio tracks tab is hidden when endless status is unknown`() {
        assertFalse(
            ItemDetailsTab.RADIO_TRACKS in state(isEndless = null).tabs,
        )
    }

    @Test
    fun `radio versions tab is hidden when request returns no versions`() {
        val state = ItemDetailsViewModel.State(
            itemState = DataState.Data(testRadio(isEndless = false)),
            albumsState = DataState.NoData(),
            playableItemsState = DataState.NoData(),
            radioTracksSupported = true,
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
            radioTracksSupported = true,
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
            radioTracksSupported = true,
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
            radioTracksSupported = true,
            radioVersionsState = DataState.Error(),
        )

        assertTrue(ItemDetailsTab.RADIO_VERSIONS in state.tabs)
    }
}
