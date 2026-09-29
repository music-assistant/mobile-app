package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FindInPage
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.find_in_list_label
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.search_query_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How the field's text is consumed. The two behave differently, so they must look different:
 * a live find narrows the loaded list on every keystroke (the browser "find in page" idea),
 * while a server search only runs when the user submits it. "Filter" is deliberately not used
 * here: the library screen's Filters sheet already owns that word and the filter-list icon.
 */
enum class SearchInputMode(
    val icon: ImageVector,
    val placeholder: StringResource,
    val imeAction: ImeAction,
) {
    /** Narrows an already loaded list as the user types; nothing to submit. */
    FIND_IN_LIST(Icons.Default.FindInPage, Res.string.find_in_list_label, ImeAction.Done),

    /** Runs a server query on IME Search or the submit button; typing alone does nothing. */
    EXPLICIT_SEARCH(Icons.Default.Search, Res.string.search_query_label, ImeAction.Search),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInput(
    mode: SearchInputMode,
    query: String,
    modifier: Modifier = Modifier,
    onQueryChanged: (String) -> Unit = {},
    /** Submit callback; only meaningful for [SearchInputMode.EXPLICIT_SEARCH]. */
    onSearch: () -> Unit = {},
    focusManager: FocusManager = LocalFocusManager.current,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }
    val submit = {
        onSearch()
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
            Text(
                stringResource(mode.placeholder),
                style = textStyle,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = mode.imeAction),
        keyboardActions = KeyboardActions(
            onSearch = { submit() },
            onDone = { focusManager.clearFocus() },
        ),
        textStyle = textStyle,
        leadingIcon = { Icon(mode.icon, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                Row {
                    IconButton(
                        onClick = {
                            onQueryChanged("")
                            onSearch()
                        },
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(Res.string.common_clear),
                        )
                    }
                    if (mode == SearchInputMode.EXPLICIT_SEARCH) {
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
    SearchInput(mode = SearchInputMode.EXPLICIT_SEARCH, query = "A query for something")
}

@Preview
@Composable
fun SearchInputEmptyPreview() {
    SearchInput(mode = SearchInputMode.EXPLICIT_SEARCH, query = "")
}

@Preview
@Composable
fun SearchInputFindInListPreview() {
    SearchInput(mode = SearchInputMode.FIND_IN_LIST, query = "radiohead")
}

@Preview
@Composable
fun SearchInputLongQueryPreview() {
    SearchInput(
        mode = SearchInputMode.EXPLICIT_SEARCH,
        query = "a really long query for something that isn't likely to be something anyone would actually ever type",
    )
}
