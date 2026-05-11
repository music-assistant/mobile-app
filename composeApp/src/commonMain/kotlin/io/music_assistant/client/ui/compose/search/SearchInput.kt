package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchInput(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    focusManager: FocusManager = LocalFocusManager.current,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        value = query,
        onValueChange = onQueryChanged,
        maxLines = 1,
        label = { Text(stringResource(Res.string.search_query_label)) },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.common_clear),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onSearchTriggered()
                    },
                    enabled = query.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(Res.string.search_title),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            onSearchTriggered()
        }),
    )
}
