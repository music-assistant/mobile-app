package io.music_assistant.client.sharedicons

import musicassistantclient.shared_icons.generated.resources.Res
import musicassistantclient.shared_icons.generated.resources.airplay
import musicassistantclient.shared_icons.generated.resources.apple_tv
import musicassistantclient.shared_icons.generated.resources.bathroom
import musicassistantclient.shared_icons.generated.resources.bedroom
import musicassistantclient.shared_icons.generated.resources.bluetooth
import musicassistantclient.shared_icons.generated.resources.building
import musicassistantclient.shared_icons.generated.resources.car
import musicassistantclient.shared_icons.generated.resources.cast
import musicassistantclient.shared_icons.generated.resources.garden
import musicassistantclient.shared_icons.generated.resources.google_nest
import musicassistantclient.shared_icons.generated.resources.hallway
import musicassistantclient.shared_icons.generated.resources.headphones
import musicassistantclient.shared_icons.generated.resources.home
import musicassistantclient.shared_icons.generated.resources.homepod_mini
import musicassistantclient.shared_icons.generated.resources.kitchen
import musicassistantclient.shared_icons.generated.resources.laptop
import musicassistantclient.shared_icons.generated.resources.living_room
import musicassistantclient.shared_icons.generated.resources.mac
import musicassistantclient.shared_icons.generated.resources.mic
import musicassistantclient.shared_icons.generated.resources.monitor
import musicassistantclient.shared_icons.generated.resources.music
import musicassistantclient.shared_icons.generated.resources.office
import musicassistantclient.shared_icons.generated.resources.outdoor
import musicassistantclient.shared_icons.generated.resources.radio
import musicassistantclient.shared_icons.generated.resources.smartphone
import musicassistantclient.shared_icons.generated.resources.sonos
import musicassistantclient.shared_icons.generated.resources.soundbar
import musicassistantclient.shared_icons.generated.resources.speaker
import musicassistantclient.shared_icons.generated.resources.speakers
import musicassistantclient.shared_icons.generated.resources.sun
import musicassistantclient.shared_icons.generated.resources.tablet
import musicassistantclient.shared_icons.generated.resources.toilet
import musicassistantclient.shared_icons.generated.resources.tv
import musicassistantclient.shared_icons.generated.resources.vinyl
import musicassistantclient.shared_icons.generated.resources.voice_pe
import musicassistantclient.shared_icons.generated.resources.volume
import musicassistantclient.shared_icons.generated.resources.wiim
import org.jetbrains.compose.resources.DrawableResource

/**
 * Compose Multiplatform implementation of
 * https://github.com/music-assistant/shared-icons/blob/main/manifest.json. Icons can be provided
 * as [DrawableResource] objects using [getResource].
 */
object SharedIcons {
    fun getResource(id: String?): DrawableResource = resourceMap[id] ?: Res.drawable.speaker

    const val SMARTPHONE = "smartphone"
    const val HOMEPOD_MINI = "homepod-mini"
    const val SONOS = "sonos"
    const val MAC = "mac"
    const val APPLE_TV = "apple-tv"
    const val GOOGLE_NEST = "google-nest"
    const val VOICE_PE = "voice-pe"
    const val WIIM = "wiim"
    const val SPEAKER = "speaker"
    const val SPEAKERS = "speakers"
    const val SOUNDBAR = "soundbar"
    const val RADIO = "radio"
    const val TV = "tv"
    const val MONITOR = "monitor"
    const val LAPTOP = "laptop"
    const val TABLET = "tablet"
    const val HEADPHONES = "headphones"
    const val BLUETOOTH = "bluetooth"
    const val AIRPLAY = "airplay"
    const val CAST = "cast"
    const val CAR = "car"
    const val MUSIC = "music"
    const val VINYL = "vinyl"
    const val MIC = "mic"
    const val VOLUME = "volume"
    const val LIVING_ROOM = "living-room"
    const val BEDROOM = "bedroom"
    const val BATHROOM = "bathroom"
    const val TOILET = "toilet"
    const val KITCHEN = "kitchen"
    const val OFFICE = "office"
    const val HALLWAY = "hallway"
    const val GARDEN = "garden"
    const val OUTDOOR = "outdoor"
    const val SUN = "sun"
    const val HOME = "home"
    const val BUILDING = "building"

    private val resourceMap: Map<String, DrawableResource> = mapOf(
        HOMEPOD_MINI to Res.drawable.homepod_mini,
        SONOS to Res.drawable.sonos,
        MAC to Res.drawable.mac,
        APPLE_TV to Res.drawable.apple_tv,
        GOOGLE_NEST to Res.drawable.google_nest,
        VOICE_PE to Res.drawable.voice_pe,
        WIIM to Res.drawable.wiim,
        SPEAKER to Res.drawable.speaker,
        SPEAKERS to Res.drawable.speakers,
        SOUNDBAR to Res.drawable.soundbar,
        RADIO to Res.drawable.radio,
        TV to Res.drawable.tv,
        MONITOR to Res.drawable.monitor,
        LAPTOP to Res.drawable.laptop,
        SMARTPHONE to Res.drawable.smartphone,
        TABLET to Res.drawable.tablet,
        HEADPHONES to Res.drawable.headphones,
        BLUETOOTH to Res.drawable.bluetooth,
        AIRPLAY to Res.drawable.airplay,
        CAST to Res.drawable.cast,
        CAR to Res.drawable.car,
        MUSIC to Res.drawable.music,
        VINYL to Res.drawable.vinyl,
        MIC to Res.drawable.mic,
        VOLUME to Res.drawable.volume,
        LIVING_ROOM to Res.drawable.living_room,
        BEDROOM to Res.drawable.bedroom,
        BATHROOM to Res.drawable.bathroom,
        TOILET to Res.drawable.toilet,
        KITCHEN to Res.drawable.kitchen,
        OFFICE to Res.drawable.office,
        HALLWAY to Res.drawable.hallway,
        GARDEN to Res.drawable.garden,
        OUTDOOR to Res.drawable.outdoor,
        SUN to Res.drawable.sun,
        HOME to Res.drawable.home,
        BUILDING to Res.drawable.building,
    )
}
