package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.nav_search
import org.jetbrains.compose.resources.stringResource

/**
 * Flexible search input allowing for both "search on action" and "search as you type"  behavior
 * based on whether [onSearchAction] is non-null or not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInput(
    query: String,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit = {},
    onSearchAction: (() -> Unit)? = null,
    focusManager: FocusManager = LocalFocusManager.current,
    placeholder: String,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    val imeAction = if (onSearchAction != null) {
        ImeAction.Search
    } else {
        ImeAction.Done
    }

    val submit = {
        onSearchAction?.invoke()
        focusManager.clearFocus()
    }

    val textStyle = MaterialTheme.typography.bodyLarge
    TextField(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SearchBarDefaults.InputFieldHeight)
            .focusRequester(focusRequester),
        shape = SearchBarDefaults.inputFieldShape,
        value = query,
        onValueChange = onQueryChanged,
        placeholder = {
            Text(placeholder, style = textStyle)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onSearch = { submit() },
            onDone = { focusManager.clearFocus() },
        ),
        textStyle = textStyle,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                Row {
                    IconButton(
                        onClick = {
                            onQueryChanged("")
                            onSearchAction?.invoke()
                        },
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.common_clear),
                        )
                    }

                    if (onSearchAction != null) {
                        IconButton(onClick = submit) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(Res.string.nav_search),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SearchBarDefaults.colors().containerColor,
            unfocusedContainerColor = SearchBarDefaults.colors().containerColor,
            disabledContainerColor = SearchBarDefaults.colors().containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Preview
@Composable
fun SearchInputPreview() {
    SearchInput(
        query = "A query for something",
        onSearchAction = {},
        placeholder = "Search...",
    )
}

@Preview
@Composable
fun SearchInputEmptyPreview() {
    SearchInput(
        query = "",
        onSearchAction = {},
        placeholder = "Search...",
    )
}

@Preview
@Composable
fun SearchInputFindInListPreview() {
    SearchInput(
        query = "radiohead",
        onSearchAction = null,
        placeholder = "Search...",
    )
}

@Preview
@Composable
fun SearchInputLongQueryPreview() {
    SearchInput(
        query = "a really long query for something that isn't likely to be something anyone would actually ever type",
        onSearchAction = {},
        placeholder = "Search...",
    )
}
