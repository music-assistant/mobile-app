package io.music_assistant.client.ui.compose.common.providers

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
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

        is ProviderIconModel.Png -> {
            // Render PNG using Coil - bytes are already decoded
            val context = LocalPlatformContext.current
            val imageLoader = remember {
                getImageLoader(context)
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(providerIconModel.imageBytes)
                    .build(),
                contentDescription = stringResource(Res.string.cd_provider_icon),
                imageLoader = imageLoader,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }

        is ProviderIconModel.Svg -> {
            // Render SVG using Coil - bytes are already encoded
            val context = LocalPlatformContext.current
            val imageLoader = remember {
                getImageLoader(context)
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(providerIconModel.svgBytes)
                    .build(),
                contentDescription = stringResource(Res.string.cd_provider_icon),
                imageLoader = imageLoader,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        }

        null -> {
            // No icon to show
        }
    }
}

/**
 * Create an ImageLoader configured for SVG support
 */
private fun getImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
        }
        .build()
}
