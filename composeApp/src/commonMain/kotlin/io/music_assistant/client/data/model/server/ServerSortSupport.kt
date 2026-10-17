package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.model.client.SortOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a list request carries its sort. */
enum class SortWire {
    /** One `order_by` key, such as `timestamp_added_desc`. */
    ORDER_BY,

    /** `sort_field` and `sort_direction`. From server 2.11, which deprecates `order_by`. */
    SORT_FIELD,
}

fun sortWireFor(schemaVersion: Int?): SortWire =
    if (schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA) SortWire.SORT_FIELD else SortWire.ORDER_BY

/** A sort in the form that the connected server reads. */
data class ListSort(val option: SortOption, val wire: SortWire)

/** One entry of `music/sort_options`. The first entry of a listing is its default. */
@Serializable
data class ServerSortOption(
    @SerialName("field") val field: String,
    @SerialName("supports_direction") val supportsDirection: Boolean = true,
    @SerialName("default_direction") val defaultDirection: String? = null,
)
