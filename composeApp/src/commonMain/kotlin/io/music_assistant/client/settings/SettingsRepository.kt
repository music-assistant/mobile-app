package io.music_assistant.client.settings

import com.russhwolf.settings.Settings
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.utils.Codecs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SettingsRepository(
    private val settings: Settings
) {

    private val _theme = MutableStateFlow(
        ThemeSetting.valueOf(
            settings.getString("theme", ThemeSetting.FollowSystem.name)
        )
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
        }
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

    private val _token = MutableStateFlow(
        settings.getStringOrNull("token")?.takeIf { it.isNotBlank() }
    )
    val token = _token.asStateFlow()

    fun updateToken(token: String?) {
        if (token != this._token.value) {
            settings.putString("token", token ?: "")
            _token.update { token }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    val deviceName = MutableStateFlow(
        settings.getStringOrNull("deviceName")
            ?: run {
                val name = "KMP app ${Uuid.random()}"
                settings.putString("deviceName", name)
                name
            }
    ).asStateFlow()

    private val _playersSorting = MutableStateFlow(
        settings.getStringOrNull("players_sort")?.split(",")
    )
    val playersSorting = _playersSorting.asStateFlow()

    fun updatePlayersSorting(newValue: List<String>) {
        settings.putString("players_sort", newValue.joinToString(","))
        _playersSorting.update { newValue }
    }

    // Sendspin settings
    private val _sendspinEnabled = MutableStateFlow(
        settings.getBoolean("sendspin_enabled", false)
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
        }
    )
    val sendspinClientId = _sendspinClientId.asStateFlow()

    private val _sendspinDeviceName = MutableStateFlow(
        settings.getStringOrNull("sendspin_device_name") ?: "My Phone"
    )
    val sendspinDeviceName = _sendspinDeviceName.asStateFlow()

    fun setSendspinDeviceName(name: String) {
        settings.putString("sendspin_device_name", name)
        _sendspinDeviceName.update { name }
    }

    private val _sendspinPort = MutableStateFlow(
        settings.getInt("sendspin_port", 8927)
    )
    val sendspinPort = _sendspinPort.asStateFlow()

    fun setSendspinPort(port: Int) {
        settings.putInt("sendspin_port", port)
        _sendspinPort.update { port }
    }

    private val _sendspinPath = MutableStateFlow(
        settings.getString("sendspin_path", "/sendspin")
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
                (Codecs.list.getOrNull(0) ?: Codec.PCM).name
            ).uppercase()
        )
    )
    val sendspinCodecPreference = _sendspinCodecPreference.asStateFlow()

    fun setSendspinCodecPreference(codec: Codec) {
        settings.putString("sendspin_codec_preference", codec.name)
        _sendspinCodecPreference.update { codec }
    }

    private val _sendspinHost = MutableStateFlow(
        settings.getString("sendspin_host", "")
    )
    val sendspinHost = _sendspinHost.asStateFlow()

    fun setSendspinHost(host: String) {
        settings.putString("sendspin_host", host)
        _sendspinHost.update { host }
    }

    private val _sendspinUseTls = MutableStateFlow(
        settings.getBoolean("sendspin_use_tls", false)
    )
    val sendspinUseTls = _sendspinUseTls.asStateFlow()

    fun setSendspinUseTls(enabled: Boolean) {
        settings.putBoolean("sendspin_use_tls", enabled)
        _sendspinUseTls.update { enabled }
    }
}