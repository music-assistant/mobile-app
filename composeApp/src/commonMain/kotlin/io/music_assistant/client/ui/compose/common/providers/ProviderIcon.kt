package io.music_assistant.client.ui.compose.common.providers

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Composable to render a provider icon based on its type
 *
 * @param providerIconModel The type of icon to render (Mdi, Png, or Svg)
 * @param modifier Optional modifier for the icon container
 */
@Composable
fun ProviderIcon(
    modifier: Modifier = Modifier,
    providerIconModel: ProviderIconModel?,
) {
    when (providerIconModel) {
        is ProviderIconModel.Mdi -> {
            Icon(
                imageVector = providerIconModel.icon,
                contentDescription = stringResource(Res.string.cd_provider_icon),
                modifier = modifier,
                tint = providerIconModel.tint,
            )
        }

        is ProviderIconModel.MdiGlyph -> {
            MdiIcon(
                name = providerIconModel.name,
                modifier = modifier,
                tint = providerIconModel.tint,
            )
        }

        is ProviderIconModel.Png -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(providerIconModel.imageBytes)
                    .build(),
                contentDescription = stringResource(Res.string.cd_provider_icon),
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }

        is ProviderIconModel.Svg -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(providerIconModel.svgBytes)
                    .build(),
                contentDescription = stringResource(Res.string.cd_provider_icon),
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }

        null -> Unit
    }
}
