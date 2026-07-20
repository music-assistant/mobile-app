package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.search_query_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInput(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    focusManager: FocusManager = LocalFocusManager.current,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    val textStyle = MaterialTheme.typography.bodyLarge
    TextField(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .focusRequester(focusRequester),
        shape = SearchBarDefaults.inputFieldShape,
        value = query,
        onValueChange = onQueryChanged,
        placeholder = {
            Text(
                stringResource(Res.string.search_query_label),
                style = textStyle,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            },
        ),
        textStyle = textStyle,
        trailingIcon = if (query.isNotEmpty()) {
            {
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
    SearchInput(query = "A query for something")
}

@Preview
@Composable
fun SearchInputEmptyPreview() {
    SearchInput(query = "")
}

@Preview
@Composable
fun SearchInputLongQueryPreview() {
    SearchInput(
        query = "a really long query for something that isn't likely to be something anyone would actually ever type",
    )
}
