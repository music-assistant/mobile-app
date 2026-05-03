package io.music_assistant.client.settings

import com.russhwolf.settings.Settings
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.audio.Codecs
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    // Used for autoconnect - reconnects using the last mode that worked
    private val _lastConnectionMode = MutableStateFlow(
        settings.getStringOrNull("last_connection_mode"),
    )
    val lastConnectionMode = _lastConnectionMode.asStateFlow()

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
    private val _itemsRowMode = MutableStateFlow(
        settings.getBoolean("items_row_mode", false),
    )
    val itemsRowMode = _itemsRowMode.asStateFlow()

    fun setItemsRowMode(enabled: Boolean) {
        settings.putBoolean("items_row_mode", enabled)
        _itemsRowMode.update { enabled }
    }

    fun getSortOption(mediaType: MediaType): SortOption {
        val raw = settings.getStringOrNull("sort_${mediaType.name}")
            ?: return SortConfig.defaultFor(mediaType)
        return parseSortOption(raw) ?: SortConfig.defaultFor(mediaType)
    }

    fun setSortOption(mediaType: MediaType, option: SortOption) {
        settings.putString("sort_${mediaType.name}", "${option.field.name}:${option.descending}")
    }

    fun getSortOption(context: SubItemContext): SortOption {
        val raw = settings.getStringOrNull("sort_sub_${context.name}")
            ?: return SortConfig.defaultFor(context)
        return parseSortOption(raw) ?: SortConfig.defaultFor(context)
    }

    fun setSortOption(context: SubItemContext, option: SortOption) {
        settings.putString("sort_sub_${context.name}", "${option.field.name}:${option.descending}")
    }

    // Android Auto keeps its own per-type sort independent of the main app: the
    // car UI exposes a curated subset of presets, so blending them risks leaving
    // a "Custom" subtitle with no matching menu entry. Defaults still come from
    // SortConfig — they happen to match the AA matrix.
    fun getAutoSortOption(mediaType: MediaType): SortOption {
        val raw = settings.getStringOrNull("auto_sort_${mediaType.name}")
            ?: return SortConfig.defaultFor(mediaType)
        return parseSortOption(raw) ?: SortConfig.defaultFor(mediaType)
    }

    fun setAutoSortOption(mediaType: MediaType, option: SortOption) {
        settings.putString("auto_sort_${mediaType.name}", "${option.field.name}:${option.descending}")
    }

    fun getAutoSortOption(context: SubItemContext): SortOption {
        val raw = settings.getStringOrNull("auto_sort_sub_${context.name}")
            ?: return SortConfig.defaultFor(context)
        return parseSortOption(raw) ?: SortConfig.defaultFor(context)
    }

    fun setAutoSortOption(context: SubItemContext, option: SortOption) {
        settings.putString("auto_sort_sub_${context.name}", "${option.field.name}:${option.descending}")
    }

    private fun parseSortOption(raw: String): SortOption? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val field = runCatching { SortField.valueOf(parts[0]) }.getOrNull() ?: return null
        val desc = parts[1].toBooleanStrictOrNull() ?: return null
        return SortOption(field, desc)
    }
}
