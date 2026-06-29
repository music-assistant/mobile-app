import Foundation
import AVFoundation
import AudioToolbox
import ComposeApp

/// Native iOS audio player using AudioQueue
/// Replaces MPVController for better iOS integration
class NativeAudioController: NSObject, PlatformAudioPlayer {

    // MARK: - AudioQueue
    private var audioQueue: AudioQueueRef?
    private var audioFormat: AudioStreamBasicDescription = AudioStreamBasicDescription()

    // MARK: - Audio Buffer
    private var pcmBuffer: [Data] = []
    private let bufferLock = NSLock()
    private let kNumberOfBuffers = 5 // More buffers for smoother playback
    private let kBufferSize: UInt32 = 65536 // 64KB per buffer for less stuttering


    // MARK: - Decoder
    private var decoder: NativeAudioDecoder?
    private var listener: MediaPlayerListener?

    // MARK: - Stream Configuration
    private var currentCodec: String = "flac"
    private var currentSampleRate: Int32 = 48000
    private var currentChannels: Int32 = 2
    private var currentBitDepth: Int32 = 16
    private var codecHeader: Data?

    // MARK: - State
    private var isPlaying = false
    /// True while local playback owns or is claiming the shared audio session.
    var isRenderingAudio: Bool { streamStarted || isPlaying }
    private var streamStarted = false
    // Play-intent gate (mirrors Android's shouldPlayAudio). While false — paused or
    // interrupted — incoming audio is dropped instead of (re)starting the queue, so
    // a packet still in the consumer pipeline can't undo an optimistic pause.
    private var shouldPlay = true
    // True only while we hold a server pause issued in response to an audio-session
    // interruption (phone call, Siri). On .ended we auto-resume the server only if
    // this is set — so we never spontaneously start playback that the user didn't
    // have running before the interruption.
    private var pausedByInterruption = false

    // MARK: - Logging
    // Routes through Kermit (NativeLog) so these reach the shareable in-memory buffer
    // and os.Logger
    private static let logTag = "NativeAudioController"
    private func logInfo(_ message: String) { NativeLog.shared.info(tag: Self.logTag, message: message) }
    private func logError(_ message: String) { NativeLog.shared.error(tag: Self.logTag, message: message) }
    private func logDebug(_ message: String) { NativeLog.shared.debug(tag: Self.logTag, message: message) }

    override init() {
        super.init()
        logDebug("Initialized")

        // Handle audio session interruptions (phone calls, Siri, alarms)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioSessionInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
        // Handle route changes (headphones unplugged, Bluetooth disconnects)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioRouteChange(_:)),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleAudioSessionInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }

        switch type {
        case .began:
            // System auto-pauses AudioQueue. Tell the server to pause too so playback
            // resumes from the same position afterwards instead of skipping ahead while
            // the call held the audio session.
            logInfo("Audio session interrupted")
            if isPlaying {
                pausedByInterruption = true
                logInfo("Pausing server playback due to interruption")
                remoteCommandHandler?.onCommand(command: "pause", source: "interruption")
            }
        case .ended:
            guard pausedByInterruption else { break }
            pausedByInterruption = false
            // We deliberately do not use .shouldResume here as it is not guaranteed
            // to be set even in cases it should be. As per Apple, it's a hint not
            // a contract. Instead we track for ourselves if we were interrupted,
            // and once control is handed back, if another app is now using the
            // audio device exclusively.
            if !AVAudioSession.sharedInstance().secondaryAudioShouldBeSilencedHint {
                logInfo("Resuming server playback after interruption")
                remoteCommandHandler?.onCommand(command: "play", source: "interruption")
            } else {
                logInfo("Another app holds audio — staying paused")
            }
        @unknown default:
            break
        }
    }

    @objc private func handleAudioRouteChange(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else { return }

        if reason == .oldDeviceUnavailable {
            logInfo("Audio output device disconnected")
            let previousRoute = userInfo[AVAudioSessionRouteChangePreviousRouteKey]
                as? AVAudioSessionRouteDescription
            handleOldDeviceUnavailable(previousRoute: previousRoute)
        }
    }

    /// Pause when the active output route disappears (Bluetooth disconnect,
    /// headphones unplug, AirPods power-off, CarPlay disconnect) — never let
    /// playback silently fall back to the phone speaker. Shutting `shouldPlay`
    /// drops in-flight packets so the next one can't rebuild the queue on the
    /// new route; the server pause stops the stream at the source. Unlike an
    /// interruption, iOS sends no matching `.ended`, so this is a deliberate
    /// pause the user resumes by hand, on whatever route is then active.
    /// (AirPods already send their own `pause` remote command on removal; the
    /// `streamStarted` guard makes this a no-op once that has shut the gate.)
    private func handleOldDeviceUnavailable(previousRoute: AVAudioSessionRouteDescription?) {
        guard streamStarted else { return }
        let prev = previousRoute?.outputs.first?.portType.rawValue ?? "unknown"
        logInfo("\(prev) disappeared — pausing playback")
        shouldPlay = false
        streamStarted = false
        stopAudioQueue()
        bufferLock.lock()
        pcmBuffer.removeAll()
        bufferLock.unlock()
        remoteCommandHandler?.onCommand(command: "pause", source: "route_loss")
    }

    // MARK: - PlatformAudioPlayer Protocol

    func prepareStream(codec: String, sampleRate: Int32, channels: Int32, bitDepth: Int32, codecHeader: String?, listener: MediaPlayerListener) {
        logInfo("prepareStream - codec=\(codec), rate=\(sampleRate), ch=\(channels), bit=\(bitDepth)")

        self.listener = listener
        self.currentCodec = codec.lowercased()
        self.currentSampleRate = sampleRate
        self.currentChannels = channels
        self.currentBitDepth = bitDepth
        self.streamStarted = false
        self.shouldPlay = true

        // Decode codec header if present
        if let headerBase64 = codecHeader, let headerData = Data(base64Encoded: headerBase64) {
            self.codecHeader = headerData
            logDebug("Decoded codec header: \(headerData.count) bytes")
        } else {
            self.codecHeader = nil
        }

        // Stop any existing playback
        stopAudioQueue()

        // Clear buffers
        bufferLock.lock()
        pcmBuffer.removeAll()
        bufferLock.unlock()

        // Create decoder for codec
        do {
            decoder = try AudioDecoderFactory.create(
                codec: currentCodec,
                sampleRate: Int(sampleRate),
                channels: Int(channels),
                bitDepth: Int(bitDepth),
                codecHeader: self.codecHeader
            )
            logInfo("Created decoder for \(codec)")
        } catch {
            logError("Failed to create decoder: \(error)")
            listener.onError(error: KotlinThrowable(message: error.localizedDescription))
            return
        }

        listener.onReady()
    }

    /// Called from Kotlin via efficient NSData bulk-copy path (avoids per-byte Swift interop).
    func writeRawPcmNSData(data: Data) {
        processAudioData(data)
    }

    /// Legacy path: still satisfies the PlatformAudioPlayer protocol but is no longer
    /// called from Kotlin (Kotlin always uses writeRawPcmNSData now).
    func writeRawPcm(data: KotlinByteArray) {
        let size = Int(data.size)
        var swiftData = Data(count: size)
        for i in 0..<size {
            swiftData[i] = UInt8(bitPattern: data.get(index: Int32(i)))
        }
        processAudioData(swiftData)
    }

    private func processAudioData(_ swiftData: Data) {
        // Suspended (paused / interrupted): drop in-flight audio rather than
        // restart the queue, so a packet still in the consumer pipeline can't
        // undo the pause before the server stops streaming.
        guard shouldPlay else { return }

        // Start audio queue on first data
        if !streamStarted {
            streamStarted = true
            logDebug("First data received (\(swiftData.count) bytes)")
            NowPlayingManager.shared.activatePlayback()
            startAudioQueue()
        }

        guard let decoder = decoder else {
            logDebug("No decoder available — dropping packet")
            return
        }

        do {
            let pcmData = try decoder.decode(swiftData)
            bufferLock.lock()
            pcmBuffer.append(pcmData)
            bufferLock.unlock()
        } catch {
            logDebug("Decode error: \(error)")
        }
    }

    func stopRawPcmStream() {
        logInfo("Stopping stream")
        shouldPlay = false
        streamStarted = false
        stopAudioQueue()

        bufferLock.lock()
        pcmBuffer.removeAll()
        bufferLock.unlock()
    }

    /// Tear down rather than `AudioQueuePause`: a paused queue replays its stale
    /// primed buffers on resume, then underruns. `shouldPlay = false` drops any
    /// in-flight audio so the consumer can't immediately rebuild the queue;
    /// resume then rebuilds clean on the next packet, like a cold start.
    func pauseSink() {
        logInfo("pauseSink")
        shouldPlay = false
        streamStarted = false
        tearDownQueue()
    }

    /// Reactivating the session reclaims audio from another app that grabbed it.
    /// `shouldPlay = true` re-opens the write gate; the queue rebuilds on the next
    /// audio packet, or is started here if one still exists (gapless restart).
    func resumeSink() {
        logInfo("resumeSink")
        shouldPlay = true
        NowPlayingManager.shared.activatePlayback()
        isPlaying = true
        if let queue = audioQueue {
            AudioQueueStart(queue, nil)
        }
    }

    /// Drop buffered PCM (track transition / playback-delay re-phase).
    func flush() {
        bufferLock.lock()
        pcmBuffer.removeAll()
        bufferLock.unlock()
    }

    func setVolume(volume: Int32) {
        guard let queue = audioQueue else { return }
        let floatVolume = Float(volume) / 100.0
        AudioQueueSetParameter(queue, kAudioQueueParam_Volume, floatVolume)
    }

    func setMuted(muted: Bool) {
        guard let queue = audioQueue else { return }
        AudioQueueSetParameter(queue, kAudioQueueParam_Volume, muted ? 0.0 : 1.0)
    }

    func dispose() {
        NowPlayingManager.shared.clearNowPlayingInfo()
        stopAudioQueue()
        decoder = nil
    }

    // MARK: - AudioQueue Management

    private func startAudioQueue() {
        // Configure audio format (always output PCM)
        audioFormat.mSampleRate = Float64(currentSampleRate)
        audioFormat.mFormatID = kAudioFormatLinearPCM
        audioFormat.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked
        audioFormat.mFramesPerPacket = 1
        audioFormat.mChannelsPerFrame = UInt32(currentChannels)

        // FLAC decoder always outputs Int32 (scaled to full range).
        // PCM 24-bit is unpacked to Int32 by PCMPassthroughDecoder.
        // All other cases use the negotiated bit depth directly.
        let effectiveBitDepth: Int32
        if currentCodec == "flac" || currentBitDepth == 24 {
            effectiveBitDepth = 32
        } else {
            effectiveBitDepth = currentBitDepth
        }
        let bytesPerSample = effectiveBitDepth / 8

        audioFormat.mBitsPerChannel = UInt32(effectiveBitDepth)
        audioFormat.mBytesPerFrame = UInt32(currentChannels) * UInt32(bytesPerSample)
        audioFormat.mBytesPerPacket = audioFormat.mBytesPerFrame

        logDebug("Audio format - \(currentSampleRate)Hz, \(currentChannels)ch, \(effectiveBitDepth)bit")

        // Create AudioQueue
        let selfPointer = Unmanaged.passUnretained(self).toOpaque()

        var queue: AudioQueueRef?
        let status = AudioQueueNewOutput(
            &audioFormat,
            audioQueueCallback,
            selfPointer,
            nil,
            nil,
            0,
            &queue
        )

        guard status == noErr, let queue = queue else {
            logError("Failed to create AudioQueue: \(status)")
            return
        }

        audioQueue = queue

        // Allocate and prime buffers
        for _ in 0..<kNumberOfBuffers {
            var buffer: AudioQueueBufferRef?
            let allocStatus = AudioQueueAllocateBuffer(queue, kBufferSize, &buffer)

            if allocStatus == noErr, let buffer = buffer {
                fillBuffer(queue: queue, buffer: buffer)
            }
        }

        // Start playback
        let startStatus = AudioQueueStart(queue, nil)
        if startStatus == noErr {
            isPlaying = true
            logInfo("AudioQueue started")
        } else {
            logError("Failed to start AudioQueue: \(startStatus)")
        }
    }

    private func stopAudioQueue() {
        tearDownQueue()
        pausedByInterruption = false // Stream stopped — no auto-resume on .ended.
    }

    /// `AudioQueueStop(_, true)` discards enqueued hardware buffers, so a rebuilt
    /// queue never replays stale audio. Leaves `pausedByInterruption` untouched —
    /// a pause issued during `.began` must still auto-resume on `.ended`.
    private func tearDownQueue() {
        guard let queue = audioQueue else { return }

        AudioQueueStop(queue, true)
        AudioQueueDispose(queue, true)

        audioQueue = nil
        isPlaying = false
        logInfo("AudioQueue stopped")
    }

    fileprivate func fillBuffer(queue: AudioQueueRef, buffer: AudioQueueBufferRef) {
        // Get next PCM data from buffer
        bufferLock.lock()
        let pcmData = pcmBuffer.isEmpty ? nil : pcmBuffer.removeFirst()
        bufferLock.unlock()

        if let data = pcmData {
            // Copy PCM data to buffer
            let copySize = min(data.count, Int(buffer.pointee.mAudioDataBytesCapacity))
            _ = data.withUnsafeBytes { srcBytes in
                memcpy(buffer.pointee.mAudioData, srcBytes.baseAddress, copySize)
            }
            buffer.pointee.mAudioDataByteSize = UInt32(copySize)
        } else {
            // No data - output silence
            memset(buffer.pointee.mAudioData, 0, Int(buffer.pointee.mAudioDataBytesCapacity))
            buffer.pointee.mAudioDataByteSize = buffer.pointee.mAudioDataBytesCapacity
        }

        // Re-enqueue buffer
        AudioQueueEnqueueBuffer(queue, buffer, 0, nil)
    }

    // MARK: - Now Playing (Control Center / Lock Screen)

    private var remoteCommandHandler: RemoteCommandHandler?

    /// `duration` and `elapsedTime` are passed as `KotlinDouble?` because Kotlin/Native
    /// boxes nullable primitives in interface signatures. A nil here means "value
    /// unknown — leave the corresponding `MPNowPlayingInfoCenter` field alone" (vs.
    /// the prior contract which forced callers to fabricate a 0 and visibly reset the
    /// playback bar). See `MainDataSource`'s position-tracker overlay for why this
    /// distinction matters in practice.
    func updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: KotlinDouble?,
        elapsedTime: KotlinDouble?,
        playbackRate: Double,
        isLongFormContent: Bool
    ) {
        NowPlayingManager.shared.updateNowPlayingInfo(
            title: title,
            artist: artist,
            album: album,
            artworkUrl: artworkUrl,
            duration: duration?.doubleValue,
            elapsedTime: elapsedTime?.doubleValue,
            playbackRate: playbackRate,
            isLongFormContent: isLongFormContent
        )
    }

    func setLongFormSeekIntervals(backSeconds: Int64, forwardSeconds: Int64) {
        NowPlayingManager.shared.setLongFormSeekIntervals(
            backSeconds: backSeconds,
            forwardSeconds: forwardSeconds
        )
    }

    func clearNowPlaying() {
        NowPlayingManager.shared.clearNowPlayingInfo()
    }

    func setRemoteCommandHandler(handler: RemoteCommandHandler?) {
        self.remoteCommandHandler = handler

        NowPlayingManager.shared.setCommandHandler { [weak self] command in
            self?.logInfo("Remote command: \(command)")
            self?.remoteCommandHandler?.onCommand(command: command, source: "remote")
        }
    }
}

// MARK: - AudioQueue Callback

private let audioQueueCallback: AudioQueueOutputCallback = { userData, queue, buffer in
    guard let userData = userData else { return }

    let controller = Unmanaged<NativeAudioController>.fromOpaque(userData).takeUnretainedValue()
    controller.fillBuffer(queue: queue, buffer: buffer)
}
