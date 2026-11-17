package io.music_assistant.client.ui.compose.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.client.PartySetting
import io.music_assistant.client.data.model.client.PartySettings
import io.music_assistant.client.data.repository.PartyRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Party sheet: the invite for everyone, and the host settings for a user who may edit them. */
class PartyViewModel(private val partyRepository: PartyRepository) : ViewModel(), PartySheetActions {
    data class State(
        val invite: DataState<PartyInvite> = DataState.NoData(),
        /** [DataState.NoData] when the user may not edit the settings. */
        val settings: DataState<PartySettings> = DataState.NoData(),
        /** The text that the host typed or picked, by setting key, until a save stores it. */
        val edits: Map<String, String> = emptyMap(),
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
    ) {
        /** The edits that change a stored value. Null while one of them is not a valid value. */
        val pendingEdits: Map<PartySetting, String>?
            get() {
                val fields = settings.dataOrNull?.fields.orEmpty()
                val changed = fields.mapNotNull { field -> edits[field.key]?.takeIf { it != field.value }?.let { field to it } }
                return changed.toMap().takeIf { edited -> edited.all { (field, text) -> field.accepts(text) } }
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var openJob: Job? = null

    /**
     * Loads the sheet, then loads it again on each provider change while it is open: a save
     * reloads the plugin, and the web frontend can change the settings too. A failed reload
     * keeps the last answer, because the plugin is briefly gone while it reloads.
     */
    fun open() {
        openJob?.cancel()
        _state.value = State(invite = DataState.Loading(), settings = settingsLoading())
        openJob = viewModelScope.launch {
            reload()
            partyRepository.changes.collect { reload() }
        }
    }

    /** Stops following the changes and drops the edits. */
    fun close() {
        openJob?.cancel()
        openJob = null
        _state.value = State()
    }

    override fun edit(setting: PartySetting, text: String) {
        _state.update { it.copy(edits = it.edits + (setting.key to text), saveFailed = false) }
    }

    /**
     * Turns the party on or off at once. Turning it on also saves the edits, because the host
     * sets the party up while it is off. An invalid edit blocks it, like it blocks [saveSettings].
     */
    override fun setGuestAccess(on: Boolean) {
        val state = _state.value
        val guestAccess = state.settings.dataOrNull?.guestAccess ?: return
        // Starting the party applies the setup in the same save. Ending it keeps the edits.
        val edits = if (on) state.pendingEdits ?: return else emptyMap()
        save(edits + (guestAccess to on.toString()))
    }

    override fun saveSettings() {
        _state.value.pendingEdits?.takeIf { it.isNotEmpty() }?.let(::save)
    }

    private fun save(edits: Map<PartySetting, String>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            partyRepository.save(edits).fold(
                onSuccess = { settings ->
                    _state.update { state ->
                        state.copy(
                            settings = DataState.Data(settings),
                            edits = state.edits - edits.keys.map { it.key }.toSet(),
                            saving = false,
                        )
                    }
                    // The plugin reads the new config at once, before the server reloads it.
                    _state.update { it.copy(invite = loadInvite(it.invite)) }
                },
                onFailure = { _state.update { it.copy(saving = false, saveFailed = true) } },
            )
        }
    }

    private suspend fun reload() = coroutineScope {
        val settings = async { loadSettings(_state.value.settings) }
        val invite = loadInvite(_state.value.invite)
        _state.update { it.copy(invite = invite, settings = settings.await()) }
    }

    private suspend fun loadInvite(last: DataState<PartyInvite>): DataState<PartyInvite> =
        partyRepository.invite().fold(
            onSuccess = { DataState.Data(it) },
            onFailure = { last.takeIf { it is DataState.Data } ?: DataState.Error() },
        )

    private suspend fun loadSettings(last: DataState<PartySettings>): DataState<PartySettings> =
        if (!partyRepository.canConfigure.value) {
            DataState.NoData()
        } else {
            partyRepository.settings().fold(
                onSuccess = { DataState.Data(it) },
                onFailure = { last.takeIf { it is DataState.Data } ?: DataState.Error() },
            )
        }

    private fun settingsLoading(): DataState<PartySettings> =
        if (partyRepository.canConfigure.value) DataState.Loading() else DataState.NoData()
}
