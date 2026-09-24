package io.music_assistant.client.utils

import platform.UIKit.UIDevice

actual fun platformDeviceName(): String = UIDevice.currentDevice.name
