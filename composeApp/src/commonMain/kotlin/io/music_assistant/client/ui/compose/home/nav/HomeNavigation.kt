package io.music_assistant.client.ui.compose.home.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.data.model.server.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

sealed interface HomeNavScreen : NavKey {

    @Serializable
    data object Landing : HomeNavScreen

    @Serializable
    data class Library(val type: MediaType?) : HomeNavScreen

    @Serializable
    data class ItemDetails(
        val itemId: String,
        val mediaType: MediaType,
        val providerId: String
    ) : HomeNavScreen

    @Serializable
    data object Search : HomeNavScreen
}

@Composable
fun rememberHomeNavBackStack(bottom: HomeNavScreen) = rememberNavBackStack(
    SavedStateConfiguration(
        from = SavedStateConfiguration.DEFAULT,
        builderAction = {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeNavScreen.Landing::class, HomeNavScreen.Landing.serializer())
                    subclass(HomeNavScreen.Library::class, HomeNavScreen.Library.serializer())
                    subclass(
                        HomeNavScreen.ItemDetails::class,
                        HomeNavScreen.ItemDetails.serializer()
                    )
                    subclass(HomeNavScreen.Search::class, HomeNavScreen.Search.serializer())
                }
            }
        }
    ),
    bottom
)
