package io.music_assistant.client.ui.compose.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.repository.PartyRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sidebar entry and the invite sheet of the Party plugin. */
class PartyViewModel(private val partyRepository: PartyRepository) : ViewModel() {
    val available: StateFlow<Boolean> = partyRepository.available

    private val _invite = MutableStateFlow<DataState<PartyInvite>>(DataState.NoData())
    val invite: StateFlow<DataState<PartyInvite>> = _invite.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Loads the invite each time the sheet opens, so a change of guest access or of the party
     * name shows on the next opening. The open sheet does not follow such a change.
     */
    fun load() {
        loadJob?.cancel()
        _invite.value = DataState.Loading()
        loadJob = viewModelScope.launch {
            _invite.value = partyRepository.invite().fold(
                onSuccess = { DataState.Data(it) },
                onFailure = { DataState.Error() },
            )
        }
    }
}
