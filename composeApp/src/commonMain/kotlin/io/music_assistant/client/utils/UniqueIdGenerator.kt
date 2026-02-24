package io.music_assistant.client.utils

class UniqueIdGenerator {

    private var nextInt = 0

    fun nextInt(): Int {
        return nextInt++
    }


}
