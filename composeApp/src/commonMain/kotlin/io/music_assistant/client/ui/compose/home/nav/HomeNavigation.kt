package io.music_assistant.client.ui.compose.home.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.data.model.server.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data class Library(val type: MediaType?) : MainNav

    @Serializable
    data class ItemDetails(
        val itemId: String,
        val mediaType: MediaType,
        val providerId: String,
    ) : MainNav

    @Serializable
    data object Search : MainNav
}

@Composable
fun rememberMainNavBackStack(bottom: MainNav) = rememberNavBackStack(
    SavedStateConfiguration(
        from = SavedStateConfiguration.DEFAULT,
        builderAction = {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(MainNav.Landing::class, MainNav.Landing.serializer())
                    subclass(MainNav.Library::class, MainNav.Library.serializer())
                    subclass(
                        MainNav.ItemDetails::class,
                        MainNav.ItemDetails.serializer(),
                    )
                    subclass(MainNav.Search::class, MainNav.Search.serializer())
                }
            }
        },
    ),
    bottom,
)
