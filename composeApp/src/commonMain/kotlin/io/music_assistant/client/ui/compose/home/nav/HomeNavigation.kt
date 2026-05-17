package io.music_assistant.client.ui.compose.home.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import io.music_assistant.client.data.model.client.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface MainNav : NavKey {
    @Serializable
    data object Landing : MainNav

    @Serializable
    data class Library(val type: MediaType?) : MainNav

    /**
     * Multiple instances of the same item can appear in a back stack - [stackingId] ensures they
     * are treated as different entries.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    data class ItemDetails(
        val itemId: String,
        val mediaType: MediaType,
        val providerId: String,
        val stackingId: String = Uuid.generateV4().toString(),
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
