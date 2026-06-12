package io.music_assistant.client.settings

import com.russhwolf.settings.Settings
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.audio.Codecs
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SettingsRepository(
    private val settings: Settings,
) {
    private val _theme = MutableStateFlow(
        ThemeSetting.valueOf(
            settings.getString("theme", ThemeSetting.FollowSystem.name),
        ),
    )
    val theme = _theme.asStateFlow()

    fun switchTheme(theme: ThemeSetting) {
        settings.putString("theme", theme.name)
        _theme.update { theme }
    }

    private val _connectionInfo = MutableStateFlow(
        settings.getStringOrNull("host")?.takeIf { it.isNotBlank() }?.let { host ->
            settings.getIntOrNull("port")?.takeIf { it > 0 }?.let { port ->
                ConnectionInfo(host, port, settings.getBoolean("isTls", false))
            }
        },
    )
    val connectionInfo = _connectionInfo.asStateFlow()

    fun updateConnectionInfo(connectionInfo: ConnectionInfo?) {
        if (connectionInfo != this._connectionInfo.value) {
            settings.putString("host", connectionInfo?.host ?: "")
            settings.putInt("port", connectionInfo?.port ?: 0)
            settings.putBoolean("isTls", connectionInfo?.isTls == true)
            _connectionInfo.update { connectionInfo }
        }
    }

    /**
     * Get authentication token for a specific server.
     * @param serverIdentifier "direct:ws://host:port" / "direct:wss://host:port" or "webrtc:remoteId"
     */
    fun getTokenForServer(serverIdentifier: String): String? {
        return settings.getStringOrNull("token_$serverIdentifier")?.takeIf { it.isNotBlank() }
    }

    /**
     * Save authentication token for a specific server.
     * @param serverIdentifier "direct:ws://host:port" / "direct:wss://host:port" or "webrtc:remoteId"
     * @param token Authentication token (null to clear)
     */
    fun setTokenForServer(serverIdentifier: String, token: String?) {
        if (token.isNullOrBlank()) {
            settings.remove("token_$serverIdentifier")
        } else {
            settings.putString("token_$serverIdentifier", token)
        }
    }

    /**
     * Get server identifier for Direct connection.
     */
    fun getDirectServerIdentifier(host: String, port: Int, isTls: Boolean): String {
        return "direct:${if (isTls) "wss" else "ws"}://$host:$port"
    }

    /**
     * Get server identifier for WebRTC connection.
     */
    fun getWebRTCServerIdentifier(remoteId: String): String {
        return "webrtc:$remoteId"
    }

    @OptIn(ExperimentalUuidApi::class)
    val deviceName = MutableStateFlow(
        settings.getStringOrNull("deviceName")
            ?: run {
                val name = "KMP app ${Uuid.random()}"
                settings.putString("deviceName", name)
                name
            },
    ).asStateFlow()

    private val _playersSorting = MutableStateFlow(
        settings.getStringOrNull("players_sort")?.split(","),
    )
    val playersSorting = _playersSorting.asStateFlow()

    fun updatePlayersSorting(newValue: List<String>) {
        settings.putString("players_sort", newValue.joinToString(","))
        _playersSorting.update { newValue }
    }

    // Home-screen rows: visibility + user-defined order in a single ordered list.
    // Order = display sort; enabled=false = hidden. JSON-encoded because folder
    // ids are arbitrary server strings (may contain the delimiters a flat string
    // encoding would rely on). Reconciliation against the live server list happens
    // at the ViewModel/UI boundary — the repo deals with raw id/enabled pairs only.
    @Serializable
    data class HomeRowPref(val id: String, val enabled: Boolean)

    private val _homeRowsConfig = MutableStateFlow(loadHomeRowsConfig())
    val homeRowsConfig = _homeRowsConfig.asStateFlow()

    private fun loadHomeRowsConfig(): List<HomeRowPref> {
        settings.getStringOrNull("home_rows_config")?.let { raw ->
            return runCatching {
                myJson.decodeFromString<List<HomeRowPref>>(raw)
            }.getOrDefault(emptyList())
        }
        // Legacy migration.
        val legacy = settings.getStringOrNull("hidden_recommendation_folders")
            ?.split(",")
            ?.filter { it.isNotBlank() }
        return legacy
            ?.map { HomeRowPref(id = it, enabled = false) }
            ?.also {
                settings.putString("home_rows_config", myJson.encodeToString(it))
                settings.remove("hidden_recommendation_folders")
            }
            ?: emptyList()
    }

    fun setHomeRowsConfig(config: List<HomeRowPref>) {
        settings.putString("home_rows_config", myJson.encodeToString(config))
        _homeRowsConfig.update { config }
    }

    // Library tabs visibility + ordering. Stored as comma-separated "NAME:0|1"
    // pairs. Reconciliation against the live tab universe happens at the
    // ViewModel boundary — repo deals with raw name/enabled pairs only.
    data class LibraryCategoryPref(val name: String, val enabled: Boolean)

    private val _libraryCategoryConfig = MutableStateFlow(loadLibraryCategoryConfig())
    val libraryCategoryConfig = _libraryCategoryConfig.asStateFlow()

    private fun loadLibraryCategoryConfig(): List<LibraryCategoryPref>? {
        val raw = settings.getStringOrNull("library_tabs_config") ?: return null
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            parts.takeIf { it.size == 2 }?.let {
                LibraryCategoryPref(name = it[0], enabled = it[1] == "1")
            }
        }.takeIf { it.isNotEmpty() }
    }

    fun setLibraryCategoryConfig(config: List<LibraryCategoryPref>) {
        val encoded = config.joinToString(",") { "${it.name}:${if (it.enabled) "1" else "0"}" }
        settings.putString("library_tabs_config", encoded)
        _libraryCategoryConfig.update { config }
    }

    // Android Auto / CarPlay root tabs: visibility + order, independent of the phone library tabs.
    // Same name/enabled encoding as library_tabs_config; reconciliation against the AA-supported
    // tab universe happens at the ViewModel boundary. Null = never customized → AA falls back to
    // its default tab set.
    private val _carTabsConfig = MutableStateFlow(loadCarTabsConfig())
    val carTabsConfig = _carTabsConfig.asStateFlow()

    private fun loadCarTabsConfig(): List<LibraryCategoryPref>? {
        val raw = settings.getStringOrNull("car_tabs_config") ?: return null
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            parts.takeIf { it.size == 2 }?.let {
                LibraryCategoryPref(name = it[0], enabled = it[1] == "1")
            }
        }.takeIf { it.isNotEmpty() }
    }

    fun setCarTabsConfig(config: List<LibraryCategoryPref>) {
        val encoded = config.joinToString(",") { "${it.name}:${if (it.enabled) "1" else "0"}" }
        settings.putString("car_tabs_config", encoded)
        _carTabsConfig.update { config }
    }

    // Default click action keyed by item kind then context. JSON map of
    // ItemKind.name -> (ClickContext.name -> DefaultClickAction.name). Absent keys
    // resolve to PLAY_NOW at the call site (= the historic hard-coded behavior), so there's
    // nothing to migrate.
    private val _defaultClickActions = MutableStateFlow(loadDefaultClickActions())
    val defaultClickActions = _defaultClickActions.asStateFlow()

    private fun loadDefaultClickActions(): Map<ItemKind, Map<ClickContext, DefaultClickOption>> {
        val raw = settings.getStringOrNull("default_click_actions") ?: return emptyMap()
        return runCatching {
            myJson.decodeFromString<Map<String, Map<String, String>>>(raw).mapNotNull { (k, perContext) ->
                val kind = runCatching { ItemKind.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                kind to perContext.mapNotNull { (c, v) ->
                    val ctx = runCatching { ClickContext.valueOf(c) }.getOrNull() ?: return@mapNotNull null
                    val action = runCatching { DefaultClickOption.valueOf(v) }.getOrNull() ?: return@mapNotNull null
                    ctx to action
                }.toMap()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** Replaces the per-context table for a single [kind]; other kinds are preserved. */
    fun setDefaultClickActions(kind: ItemKind, perContext: Map<ClickContext, DefaultClickOption>) {
        val updated = _defaultClickActions.value.toMutableMap().apply { put(kind, perContext) }
        val encoded = myJson.encodeToString(
            updated.entries.associate { (k, m) -> k.name to m.entries.associate { it.key.name to it.value.name } },
        )
        settings.putString("default_click_actions", encoded)
        _defaultClickActions.update { updated }
    }

    // Car (Android Auto / CarPlay) per-kind tap action. JSON map ItemKind.name ->
    // DefaultClickAction.name. Absent keys resolve to PLAY_NOW at the call site (= today's
    // hard-coded REPLACE-on-tap), so there's nothing to migrate.
    private val _carPlayableClickActions = MutableStateFlow(loadCarPlayableClickActions())
    val carPlayableClickActions = _carPlayableClickActions.asStateFlow()

    private fun loadCarPlayableClickActions(): Map<ItemKind, DefaultClickOption> {
        val raw = settings.getStringOrNull("car_playable_click_actions") ?: return emptyMap()
        return runCatching {
            myJson.decodeFromString<Map<String, String>>(raw).mapNotNull { (k, v) ->
                val kind = runCatching { ItemKind.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                val action = runCatching { DefaultClickOption.valueOf(v) }.getOrNull() ?: return@mapNotNull null
                kind to action
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun setCarPlayableClickAction(kind: ItemKind, action: DefaultClickOption) {
        val updated = _carPlayableClickActions.value.toMutableMap().apply { put(kind, action) }
        settings.putString(
            "car_playable_click_actions",
            myJson.encodeToString(updated.entries.associate { it.key.name to it.value.name }),
        )
        _carPlayableClickActions.update { updated }
    }

    // Car browsable bulk actions: the ordered, enabled buttons prepended to a browsable
    // drill-down. JSON map ItemKind.name -> [DefaultClickAction.name]. Absent keys resolve to
    // [PLAY_NOW, ADD_TO_QUEUE] (= today's two buttons) at the call site.
    private val _carBrowsableBulkActions = MutableStateFlow(loadCarBrowsableBulkActions())
    val carBrowsableBulkActions = _carBrowsableBulkActions.asStateFlow()

    private fun loadCarBrowsableBulkActions(): Map<ItemKind, List<DefaultClickOption>> {
        val raw = settings.getStringOrNull("car_browsable_bulk_actions") ?: return emptyMap()
        return runCatching {
            myJson.decodeFromString<Map<String, List<String>>>(raw).mapNotNull { (k, list) ->
                val kind = runCatching { ItemKind.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                kind to list.mapNotNull { v -> runCatching { DefaultClickOption.valueOf(v) }.getOrNull() }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /** Replaces the bulk-action list for a single [kind]; other kinds are preserved. */
    fun setCarBrowsableBulkActions(kind: ItemKind, actions: List<DefaultClickOption>) {
        val updated = _carBrowsableBulkActions.value.toMutableMap().apply { put(kind, actions) }
        settings.putString(
            "car_browsable_bulk_actions",
            myJson.encodeToString(updated.entries.associate { (k, v) -> k.name to v.map { it.name } }),
        )
        _carBrowsableBulkActions.update { updated }
    }

    // Sendspin settings
    private val _sendspinEnabled = MutableStateFlow(
        settings.getBoolean("sendspin_enabled", false),
    )
    val sendspinEnabled = _sendspinEnabled.asStateFlow()

    fun setSendspinEnabled(enabled: Boolean) {
        settings.putBoolean("sendspin_enabled", enabled)
        _sendspinEnabled.update { enabled }
    }

    @OptIn(ExperimentalUuidApi::class)
    private val _sendspinClientId = MutableStateFlow(
        settings.getStringOrNull("sendspin_client_id") ?: Uuid.random().toString().also {
            settings.putString("sendspin_client_id", it)
        },
    )
    val sendspinClientId = _sendspinClientId.asStateFlow()

    private val _sendspinDeviceName = MutableStateFlow(
        settings.getStringOrNull("sendspin_device_name") ?: "My Phone",
    )
    val sendspinDeviceName = _sendspinDeviceName.asStateFlow()

    fun setSendspinDeviceName(name: String) {
        settings.putString("sendspin_device_name", name)
        _sendspinDeviceName.update { name }
    }

    private val _sendspinPort = MutableStateFlow(
        settings.getInt("sendspin_port", 8095),
    )
    val sendspinPort = _sendspinPort.asStateFlow()

    fun setSendspinPort(port: Int) {
        settings.putInt("sendspin_port", port)
        _sendspinPort.update { port }
    }

    private val _sendspinPath = MutableStateFlow(
        settings.getString("sendspin_path", "/sendspin"),
    )
    val sendspinPath = _sendspinPath.asStateFlow()

    fun setSendspinPath(path: String) {
        settings.putString("sendspin_path", path)
        _sendspinPath.update { path }
    }

    private val _sendspinCodecPreference = MutableStateFlow(
        Codec.valueOf(
            settings.getString(
                "sendspin_codec_preference",
                (Codecs.list.getOrNull(0) ?: Codecs.default).name,
            ).uppercase(),
        ),
    )
    val sendspinCodecPreference = _sendspinCodecPreference.asStateFlow()

    fun setSendspinCodecPreference(codec: Codec) {
        settings.putString("sendspin_codec_preference", codec.name)
        _sendspinCodecPreference.update { codec }
    }

    private val _sendspinHost = MutableStateFlow(
        settings.getString("sendspin_host", ""),
    )
    val sendspinHost = _sendspinHost.asStateFlow()

    fun setSendspinHost(host: String) {
        settings.putString("sendspin_host", host)
        _sendspinHost.update { host }
    }

    private val _sendspinUseTls = MutableStateFlow(
        settings.getBoolean("sendspin_use_tls", false),
    )
    val sendspinUseTls = _sendspinUseTls.asStateFlow()

    fun setSendspinUseTls(enabled: Boolean) {
        settings.putBoolean("sendspin_use_tls", enabled)
        _sendspinUseTls.update { enabled }
    }

    // User-tuned client-side playback delay (ms). Fed into AudioStreamManager's
    // wall-clock gate as a subtraction from each chunk's local target time:
    //   target = serverTimeToLocal(ts) - userDelay*1000
    // Positive → play earlier to compensate for downstream pipeline lag (the
    // normal case; ~250 ms is typical for Android AudioTrack + DAC). Negative
    // → play later (escape hatch if this device somehow leads the group).
    // We don't report this to the server — it's purely client-side scheduling.
    // Range ±2000 ms; default 250.
    private val _sendspinStaticDelayMs = MutableStateFlow(
        settings.getInt("sendspin_static_delay_ms", 250).coerceIn(-2000, 2000),
    )
    val sendspinStaticDelayMs = _sendspinStaticDelayMs.asStateFlow()

    fun setSendspinStaticDelayMs(ms: Int) {
        val clamped = ms.coerceIn(-2000, 2000)
        settings.putInt("sendspin_static_delay_ms", clamped)
        _sendspinStaticDelayMs.update { clamped }
    }

    // Migration logic: if user has custom host or non-default port, they're using custom connection
    private val _sendspinUseCustomConnection = MutableStateFlow(
        settings.getBooleanOrNull("sendspin_use_custom_connection") ?: run {
            val hasCustomHost = settings.getString("sendspin_host", "").isNotEmpty()
            val hasCustomPort = settings.getInt("sendspin_port", 8095) != 8095
            val useCustom = hasCustomHost || hasCustomPort
            settings.putBoolean("sendspin_use_custom_connection", useCustom)
            useCustom
        },
    )
    val sendspinUseCustomConnection = _sendspinUseCustomConnection.asStateFlow()

    fun setSendspinUseCustomConnection(enabled: Boolean) {
        settings.putBoolean("sendspin_use_custom_connection", enabled)
        _sendspinUseCustomConnection.update { enabled }
    }

    // Connection method preference
    private val _preferredConnectionMethod = MutableStateFlow(
        settings.getString("preferred_connection_method", "direct"),
    )
    val preferredConnectionMethod = _preferredConnectionMethod.asStateFlow()

    fun setPreferredConnectionMethod(method: String) {
        settings.putString("preferred_connection_method", method)
        _preferredConnectionMethod.update { method }
    }

    // WebRTC Remote Access settings
    private val _webrtcRemoteId = MutableStateFlow(
        settings.getString("webrtc_remote_id", ""),
    )
    val webrtcRemoteId = _webrtcRemoteId.asStateFlow()

    fun setWebrtcRemoteId(remoteId: String) {
        settings.putString("webrtc_remote_id", remoteId)
        _webrtcRemoteId.update { remoteId }
    }

    // Last successful connection mode ("direct" or "webrtc")
    // Used for auto-connect - reconnects using the last mode that worked
    private val _lastConnectionMode = MutableStateFlow(
        settings.getStringOrNull("last_connection_mode"),
    )

    fun setLastConnectionMode(mode: String) {
        settings.putString("last_connection_mode", mode)
        _lastConnectionMode.update { mode }
    }

    // Persisted user player choice. Null means "no explicit choice yet" — on
    // first launch the resolver in `MainDataSource.resolveSelectedPlayerId`
    // falls back to the first visible player. Written by
    // `MainDataSource.selectPlayer` (driven by the in-app player picker); no
    // other path touches it today.
    private val _lastSelectedPlayerId = MutableStateFlow(
        settings.getStringOrNull("last_selected_player_id"),
    )
    val lastSelectedPlayerId = _lastSelectedPlayerId.asStateFlow()

    fun setLastSelectedPlayerId(id: String?) {
        if (id.isNullOrBlank()) {
            settings.remove("last_selected_player_id")
        } else {
            settings.putString("last_selected_player_id", id)
        }
        _lastSelectedPlayerId.update { id }
    }

    // Connection history (most-recent-first, max 10 entries)
    private val _connectionHistory = MutableStateFlow(loadConnectionHistory())
    val connectionHistory = _connectionHistory.asStateFlow()

    private fun loadConnectionHistory(): List<ConnectionHistoryEntry> {
        val json = settings.getStringOrNull("connection_history")
        if (json != null) {
            return try { myJson.decodeFromString(json) } catch (_: Exception) { emptyList() }
        }
        // Migration: build history from legacy single-server keys (runs once on first upgrade)
        return when (settings.getStringOrNull("last_connection_mode")) {
            "webrtc" -> {
                val id = settings.getString("webrtc_remote_id", "").takeIf { it.isNotBlank() }
                    ?: return emptyList()
                listOf(ConnectionHistoryEntry(type = ConnectionType.WEBRTC, remoteId = id))
            }
            else -> {
                val host = settings.getStringOrNull("host")?.takeIf { it.isNotBlank() } ?: return emptyList()
                val port = settings.getIntOrNull("port")?.takeIf { it > 0 } ?: return emptyList()
                listOf(
                    ConnectionHistoryEntry(
                    type = ConnectionType.DIRECT,
                    host = host,
                    port = port,
                    isTls = settings.getBoolean("isTls", false),
                ),
                )
            }
        }
    }

    fun addOrUpdateHistoryEntry(entry: ConnectionHistoryEntry) {
        val updated = _connectionHistory.value
            .filter { it.serverIdentifier != entry.serverIdentifier }
            .let { listOf(entry) + it }
            .take(10)
        settings.putString("connection_history", myJson.encodeToString(updated))
        _connectionHistory.update { updated }
    }

    fun removeHistoryEntry(serverIdentifier: String) {
        val updated = _connectionHistory.value.filter { it.serverIdentifier != serverIdentifier }
        settings.putString("connection_history", myJson.encodeToString(updated))
        _connectionHistory.update { updated }
    }

    // UI preferences
    init {
        // Migrate legacy global "items_row_mode" boolean to per-MediaType "view_mode_*" enum.
        val legacyKey = "items_row_mode"
        if (settings.hasKey(legacyKey)) {
            val legacy = if (settings.getBoolean(legacyKey, false)) ViewMode.LIST else ViewMode.GRID
            MediaType.entries.forEach { mediaType ->
                val key = viewModeKey(mediaType)
                if (!settings.hasKey(key)) settings.putString(key, legacy.name)
            }
            settings.remove(legacyKey)
        }
    }

    private val viewModeFlows = mutableMapOf<MediaType, MutableStateFlow<ViewMode>>()

    private fun viewModeKey(mediaType: MediaType) = "view_mode_${mediaType.name}"

    private fun viewModeFlow(mediaType: MediaType) = viewModeFlows.getOrPut(mediaType) {
        val stored = settings.getStringOrNull(viewModeKey(mediaType))
        val initial = stored?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.GRID
        MutableStateFlow(initial)
    }

    fun viewMode(mediaType: MediaType) = viewModeFlow(mediaType).asStateFlow()

    fun setViewMode(mediaType: MediaType, mode: ViewMode) {
        settings.putString(viewModeKey(mediaType), mode.name)
        viewModeFlow(mediaType).update { mode }
    }

    fun getSortOption(context: SubItemContext): SortOption {
        val raw = settings.getStringOrNull("sort_sub_${context.name}")
            ?: return SortConfig.defaultFor(context)
        return parseSortOption(raw) ?: SortConfig.defaultFor(context)
    }

    fun setSortOption(context: SubItemContext, option: SortOption) {
        settings.putString("sort_sub_${context.name}", "${option.field.name}:${option.descending}")
    }

    private fun parseSortOption(raw: String): SortOption? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val field = runCatching { SortField.valueOf(parts[0]) }.getOrNull() ?: return null
        val desc = parts[1].toBooleanStrictOrNull() ?: return null
        return SortOption(field, desc)
    }
}
