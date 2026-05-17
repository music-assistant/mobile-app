package io.music_assistant.client.data.model.client

enum class ImageType(val serverValue: String) {
    THUMB("thumb"),
    LANDSCAPE("landscape"),
    FANART("fanart"),
    LOGO("logo"),
    CLEARART("clearart"),
    BANNER("banner"),
    CUTOUT("cutout"),
    BACK("back"),
    DISCART("discart"),
    OTHER("other"),
    MAIN("main"),
    UNKNOWN("unknown"),
    ;

    companion object {
        private val byServerValue = ImageType.entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): ImageType = raw?.let { byServerValue[it] } ?: UNKNOWN
    }
}
