package io.music_assistant.client.settings

import com.russhwolf.settings.Settings
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.GenreEmptyFilter
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.utils.myJson
import io.music_assistant.sendspin.wire.AudioCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SettingsRepository(
    private val settings: Settings,
    private val secrets: Settings,
) {
    /**
     * Move secrets out of the general store into [secrets].
     *
     * This block is declared first on purpose. Kotlin runs initialisers in
     * declaration order, and the property initialisers below read these keys.
     *
     * The block is idempotent, so it is safe on every start. It also catches a
     * secret that a platform backup restored from a version before the split.
     */
    init {
        purgeLegacyAddressKeyedSecrets()
        settings.keys
            .filter { it.startsWith(TOKEN_PREFIX) }
            .forEach { moveString(it) }
        SECRET_STRING_KEYS.forEach { moveString(it) }
        moveInt("port")
        moveBoolean("isTls")
    }

    /**
     * Drop credentials that earlier versions keyed by address.
     *
     * A legacy token cannot be re-keyed, because nothing records which server issued it.
     * So delete it: the user logs in once more. The rule matches only the old key shapes,
     * so it is idempotent and it never touches a server-id key.
     */
    private fun purgeLegacyAddressKeyedSecrets() {
        val isLegacy = { key: String ->
            key.startsWith(SERVER_ID_PREFIX) ||
                key.removePrefix(TOKEN_PREFIX).let { rest ->
                    rest != key && (rest.startsWith("direct:") || rest.startsWith("webrtc:"))
                }
        }
        (settings.keys + secrets.keys).filter(isLegacy).forEach { key ->
            settings.remove(key)
            secrets.remove(key)
        }
    }

    private fun moveString(key: String) {
        settings.getStringOrNull(key)
            ?.takeIf { !secrets.hasKey(key) }
            ?.let { secrets.putString(key, it) }
        settings.remove(key)
    }

    private fun moveInt(key: String) {
        settings.getIntOrNull(key)
            ?.takeIf { !secrets.hasKey(key) }
            ?.let { secrets.putInt(key, it) }
        settings.remove(key)
    }

    private fun moveBoolean(key: String) {
        settings.getBooleanOrNull(key)
            ?.takeIf { !secrets.hasKey(key) }
            ?.let { secrets.putBoolean(key, it) }
        settings.remove(key)
    }

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
        secrets.getStringOrNull("host")?.takeIf { it.isNotBlank() }?.let { host ->
            secrets.getIntOrNull("port")?.takeIf { it > 0 }?.let { port ->
                ConnectionInfo(
                    host = host,
                    port = port,
                    isTls = secrets.getBoolean("isTls", false),
                    // Absent for every pre-existing install, which is exactly the root-path case.
                    basePath = secrets.getString("basePath", ""),
                )
            }
        },
    )
    val connectionInfo = _connectionInfo.asStateFlow()

    fun updateConnectionInfo(connectionInfo: ConnectionInfo?) {
        if (connectionInfo != this._connectionInfo.value) {
            secrets.putString("host", connectionInfo?.host.orEmpty())
            secrets.putInt("port", connectionInfo?.port ?: 0)
            secrets.putBoolean("isTls", connectionInfo?.isTls == true)
            secrets.putString("basePath", connectionInfo?.basePath.orEmpty())
            _connectionInfo.update { connectionInfo }
        }
    }

    /**
     * Get the authentication token of a specific server.
     *
     * The key is the server's own `server_id`, not its address. One address can host
     * different servers over time, so an address key can hand a token to the wrong server.
     *
     * @param serverId `ServerInfo.serverId` of the server.
     */
    fun getTokenForServer(serverId: String): String? {
        return secrets.getStringOrNull("$TOKEN_PREFIX$serverId")?.takeIf { it.isNotBlank() }
    }

    /**
     * Save the authentication token of a specific server.
     * @param serverId `ServerInfo.serverId` of the server.
     * @param token Authentication token (null to clear)
     */
    fun setTokenForServer(serverId: String, token: String?) {
        if (token.isNullOrBlank()) {
            secrets.remove("$TOKEN_PREFIX$serverId")
        } else {
            secrets.putString("$TOKEN_PREFIX$serverId", token)
        }
    }

    /**
     * Is there a usable token for any server last seen at this address?
     *
     * The address alone cannot identify a server, so this asks the history: it is true only
     * if some entry for this address names a server whose token is still saved.
     *
     * @param serverIdentifier `ConnectionHistoryEntry.serverIdentifier` of the address.
     */
    fun hasCredentialsForAddress(serverIdentifier: String): Boolean {
        return _connectionHistory.value.any { entry ->
            entry.serverIdentifier == serverIdentifier &&
                entry.serverId?.let { getTokenForServer(it) } != null
        }
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

    // Car DSP: what to do to the local player's DSP on connect / disconnect from the car.
    // Stored as the polymorphic JSON of [CarDspAction] per direction; absent -> Nothing.
    private val _carDspConnectAction = MutableStateFlow(loadCarDspAction(CAR_DSP_CONNECT_KEY))
    val carDspConnectAction = _carDspConnectAction.asStateFlow()

    private val _carDspDisconnectAction = MutableStateFlow(loadCarDspAction(CAR_DSP_DISCONNECT_KEY))
    val carDspDisconnectAction = _carDspDisconnectAction.asStateFlow()

    private fun loadCarDspAction(key: String): CarDspAction {
        val raw = settings.getStringOrNull(key) ?: return CarDspAction.Nothing
        return runCatching { myJson.decodeFromString<CarDspAction>(raw) }
            .getOrDefault(CarDspAction.Nothing)
    }

    fun setCarDspConnectAction(action: CarDspAction) =
        persistCarDspAction(CAR_DSP_CONNECT_KEY, action, _carDspConnectAction)

    fun setCarDspDisconnectAction(action: CarDspAction) =
        persistCarDspAction(CAR_DSP_DISCONNECT_KEY, action, _carDspDisconnectAction)

    private fun persistCarDspAction(
        key: String,
        action: CarDspAction,
        flow: MutableStateFlow<CarDspAction>,
    ) {
        settings.putString(key, myJson.encodeToString<CarDspAction>(action))
        flow.update { action }
    }

    // Whether player surfaces derive their background from the current track's artwork.
    private val _dynamicColors = MutableStateFlow(
        settings.getBoolean("dynamic_colors", true),
    )
    val dynamicColors = _dynamicColors.asStateFlow()

    fun setDynamicColors(enabled: Boolean) {
        settings.putBoolean("dynamic_colors", enabled)
        _dynamicColors.update { enabled }
    }

    // Opt-in escape hatch from the compact-device portrait lock: when set, the
    // platform layer stops constraining orientation on any device.
    private val _allowLandscapeOnAllDevices = MutableStateFlow(
        settings.getBoolean("allow_landscape_all_devices", false),
    )
    val allowLandscapeOnAllDevices = _allowLandscapeOnAllDevices.asStateFlow()

    fun setAllowLandscapeOnAllDevices(enabled: Boolean) {
        settings.putBoolean("allow_landscape_all_devices", enabled)
        _allowLandscapeOnAllDevices.update { enabled }
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

    // Persisted dismissal of the "background usage disabled" warning (Android). Set only by an
    // explicit dialog dismissal; never auto-reset.
    private val _bgWarningDismissed = MutableStateFlow(
        settings.getBoolean("sendspin_bg_warning_dismissed", false),
    )
    val bgWarningDismissed = _bgWarningDismissed.asStateFlow()

    fun setBgWarningDismissed(dismissed: Boolean) {
        settings.putBoolean("sendspin_bg_warning_dismissed", dismissed)
        _bgWarningDismissed.update { dismissed }
    }

    // The player id the app addresses the local player by: the Sendspin identity
    // (the device's X25519 public key), written by LocalPlayerAdapter once the
    // module reports it. Persisted so consumers address the right player from
    // process start, before the first connection.
    private val _sendspinEffectivePlayerId = MutableStateFlow(
        settings.getStringOrNull("sendspin_effective_player_id") ?: "",
    )
    val sendspinEffectivePlayerId = _sendspinEffectivePlayerId.asStateFlow()

    fun setSendspinEffectivePlayerId(id: String) {
        settings.putString("sendspin_effective_player_id", id)
        _sendspinEffectivePlayerId.update { id }
    }

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
        AudioCodec.entries.firstOrNull {
            it.name.equals(settings.getString("sendspin_codec_preference", DEFAULT_CODEC.name), ignoreCase = true)
        } ?: DEFAULT_CODEC,
    )
    val sendspinCodecPreference = _sendspinCodecPreference.asStateFlow()

    fun setSendspinCodecPreference(codec: AudioCodec) {
        settings.putString("sendspin_codec_preference", codec.name)
        _sendspinCodecPreference.update { codec }
    }

    // Advertised buffer_capacity, stored in MB (converted to bytes when building the client hello).
    private val _sendspinBufferCapacityMb = MutableStateFlow(
        settings.getInt("sendspin_buffer_capacity_mb", BUFFER_MB_DEFAULT),
    )
    val sendspinBufferCapacityMb = _sendspinBufferCapacityMb.asStateFlow()

    fun setSendspinBufferCapacityMb(mb: Int) {
        settings.putInt("sendspin_buffer_capacity_mb", mb)
        _sendspinBufferCapacityMb.update { mb }
    }

    // Whether the local player's now-playing slider draws the buffered-ahead segment.
    private val _showBufferVisualization = MutableStateFlow(
        settings.getBoolean("show_buffer_visualization", true),
    )
    val showBufferVisualization = _showBufferVisualization.asStateFlow()

    fun setShowBufferVisualization(show: Boolean) {
        settings.putBoolean("show_buffer_visualization", show)
        _showBufferVisualization.update { show }
    }

    private val _sendspinHost = MutableStateFlow(
        secrets.getString("sendspin_host", ""),
    )
    val sendspinHost = _sendspinHost.asStateFlow()

    fun setSendspinHost(host: String) {
        secrets.putString("sendspin_host", host)
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

    // User-tuned client-side playback delay (ms). LocalPlayerAdapter negates it
    // into LocalPlayerConfig.userDelayMs, so each chunk's local target time is
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
            val hasCustomHost = secrets.getString("sendspin_host", "").isNotEmpty()
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
        secrets.getString("webrtc_remote_id", ""),
    )
    val webrtcRemoteId = _webrtcRemoteId.asStateFlow()

    fun setWebrtcRemoteId(remoteId: String) {
        secrets.putString("webrtc_remote_id", remoteId)
        _webrtcRemoteId.update { remoteId }
    }

    // Last successful connection mode ("direct" or "webrtc")
    // Used for auto-connect - reconnects using the last mode that worked
    private val _lastConnectionMode = MutableStateFlow(
        secrets.getStringOrNull("last_connection_mode"),
    )

    fun setLastConnectionMode(mode: String) {
        secrets.putString("last_connection_mode", mode)
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
        val json = secrets.getStringOrNull("connection_history")
        if (json != null) {
            return try { myJson.decodeFromString(json) } catch (_: Exception) { emptyList() }
        }
        // Migration: build history from legacy single-server keys (runs once on first upgrade)
        return when (secrets.getStringOrNull("last_connection_mode")) {
            "webrtc" -> {
                val id = secrets.getString("webrtc_remote_id", "").takeIf { it.isNotBlank() }
                    ?: return emptyList()
                listOf(ConnectionHistoryEntry(type = ConnectionType.WEBRTC, remoteId = id))
            }
            else -> {
                val host = secrets.getStringOrNull("host")?.takeIf { it.isNotBlank() } ?: return emptyList()
                val port = secrets.getIntOrNull("port")?.takeIf { it > 0 } ?: return emptyList()
                listOf(
                    ConnectionHistoryEntry(
                    type = ConnectionType.DIRECT,
                    host = host,
                    port = port,
                    isTls = secrets.getBoolean("isTls", false),
                ),
                )
            }
        }
    }

    fun addOrUpdateHistoryEntry(entry: ConnectionHistoryEntry) {
        val updated = _connectionHistory.value
            .filter {
                // Replace the same server at the same address, and absorb any entry that
                // predates server ids. Keep a different server on the same address.
                it.historyKey != entry.historyKey &&
                    !(it.serverIdentifier == entry.serverIdentifier && it.serverId == null)
            }
            .let { listOf(entry) + it }
            .take(10)
        secrets.putString("connection_history", myJson.encodeToString(updated))
        _connectionHistory.update { updated }
    }

    fun removeHistoryEntry(historyKey: String) {
        val updated = _connectionHistory.value.filter { it.historyKey != historyKey }
        secrets.putString("connection_history", myJson.encodeToString(updated))
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

    // Per-MediaType library filters, persisted like view mode (settings are the
    // source of truth; the VM folds emissions back into state).
    private val libraryFilterFlows = mutableMapOf<MediaType, MutableStateFlow<LibraryFilters>>()

    private fun libraryFiltersKey(mediaType: MediaType) = "library_filters_${mediaType.name}"

    private fun libraryFiltersFlow(mediaType: MediaType) = libraryFilterFlows.getOrPut(mediaType) {
        MutableStateFlow(loadLibraryFilters(mediaType))
    }

    fun libraryFilters(mediaType: MediaType) = libraryFiltersFlow(mediaType).asStateFlow()

    fun setLibraryFilters(mediaType: MediaType, filters: LibraryFilters) {
        settings.putString(libraryFiltersKey(mediaType), myJson.encodeToString(filters))
        libraryFiltersFlow(mediaType).update { filters }
    }

    private fun loadLibraryFilters(mediaType: MediaType): LibraryFilters {
        settings.getStringOrNull(libraryFiltersKey(mediaType))?.let { raw ->
            // coerceInputValues shields top-level nullable enums, but NOT unknown
            // elements inside albumTypes; a full runCatching fallback is required.
            return runCatching {
                myJson.decodeFromString<LibraryFilters>(raw)
            }.getOrDefault(LibraryFilters())
        }
        // Legacy migration: fold the old genres-only single-key filters into the
        // new per-type object, then drop the legacy keys.
        if (mediaType == MediaType.GENRE) {
            val legacyEmpty = settings.getStringOrNull("genre_empty_filter")
                ?.let { runCatching { GenreEmptyFilter.valueOf(it) }.getOrNull() }
            val legacyType = MediaType.fromServer(settings.getStringOrNull("genre_media_type_filter"))
            if (legacyEmpty != null || legacyType != null) {
                val migrated = LibraryFilters(
                    hideEmpty = legacyEmpty ?: GenreEmptyFilter.DEFAULT,
                    genreMediaType = legacyType,
                )
                settings.putString(libraryFiltersKey(mediaType), myJson.encodeToString(migrated))
                settings.remove("genre_empty_filter")
                settings.remove("genre_media_type_filter")
                return migrated
            }
        }
        return LibraryFilters()
    }

    // Per-MediaType library-list sort, persisted like view mode and filters. The
    // ViewModel is the only writer, so a plain get/set is enough — no flow.
    fun getSortOption(mediaType: MediaType): SortOption {
        val stored = settings.getStringOrNull(librarySortKey(mediaType))?.let { parseSortOption(it) }
        // A field dropped from SortConfig by a later app version must not stick.
        return stored?.takeIf { it.field in SortConfig.fieldsFor(mediaType) }
            ?: SortConfig.defaultFor(mediaType)
    }

    fun setSortOption(mediaType: MediaType, option: SortOption) {
        settings.putString(librarySortKey(mediaType), serializeSortOption(option))
    }

    private fun librarySortKey(mediaType: MediaType) = "library_sort_${mediaType.name}"

    fun getSortOption(context: SubItemContext): SortOption {
        // A fixed-order context has no chip, so a value persisted by an older app version could
        // never be changed back — including a stale descending flag on ORIGINAL. Ignore it outright.
        if (!SortConfig.isUserSortable(context)) return SortConfig.defaultFor(context)
        val stored = settings.getStringOrNull("sort_sub_${context.name}")?.let { parseSortOption(it) }
        return stored?.takeIf { it.field in SortConfig.fieldsFor(context) }
            ?: SortConfig.defaultFor(context)
    }

    fun setSortOption(context: SubItemContext, option: SortOption) {
        settings.putString("sort_sub_${context.name}", serializeSortOption(option))
    }

    private fun serializeSortOption(option: SortOption) = "${option.field.name}:${option.descending}"

    private fun parseSortOption(raw: String): SortOption? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val field = runCatching { SortField.valueOf(parts[0]) }.getOrNull() ?: return null
        val desc = parts[1].toBooleanStrictOrNull() ?: return null
        return SortOption(field, desc)
    }

    companion object {
        private const val CAR_DSP_CONNECT_KEY = "car_dsp_action_connect"
        private const val CAR_DSP_DISCONNECT_KEY = "car_dsp_action_disconnect"

        // Keys below live in `secrets`, not in `settings`. Add a new key here
        // when it authenticates to the user's server or identifies it.
        private const val TOKEN_PREFIX = "token_"
        private const val SERVER_ID_PREFIX = "id_"
        private val SECRET_STRING_KEYS = listOf(
            "host",
            "webrtc_remote_id",
            "last_connection_mode",
            "connection_history",
            "sendspin_host",
        )

        val CODECS: List<AudioCodec> = listOf(AudioCodec.OPUS, AudioCodec.FLAC, AudioCodec.PCM)
        val DEFAULT_CODEC: AudioCodec = AudioCodec.OPUS

        // Advertised to the server in client/hello as `buffer_capacity`: a hard per-player
        // limit in BYTES on queued audio, uniform across codecs. User slider limits, in MB.
        const val BYTES_PER_MB: Int = 1_000_000
        const val BUFFER_MB_MIN: Int = 5
        const val BUFFER_MB_MAX: Int = 50
        const val BUFFER_MB_STEP: Int = 5
        const val BUFFER_MB_DEFAULT: Int = 15
    }
}
