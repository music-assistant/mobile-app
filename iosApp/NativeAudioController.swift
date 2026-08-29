import Foundation
import AVFoundation
import AudioToolbox
import ComposeApp

/// Native iOS audio player using AudioQueue
/// Replaces MPVController for better iOS integration
class NativeAudioController: NSObject, PlatformAudioPlayer {

    // MARK: - AudioQueue
    private let queueLifecycle = AudioQueueLifecycle()
    private var audioFormat: AudioStreamBasicDescription = AudioStreamBasicDescription()

    // MARK: - Audio Buffer
    private var pcmBuffer: [Data] = []
    private let bufferLock = NSLock()
    private let kNumberOfBuffers = 5 // More buffers for smoother playback
    private let kBufferSize: UInt32 = 65536 // 64KB per buffer for less stuttering


    // MARK: - Decoder
    private var decoder: NativeAudioDecoder?
    private let decoderLock = NSLock()
    private var listener: MediaPlayerListener?

    // MARK: - Stream Configuration
    private var currentCodec: String = "flac"
    private var currentSampleRate: Int32 = 48000
    private var currentChannels: Int32 = 2
    private var currentBitDepth: Int32 = 16
    private var codecHeader: Data?

    // MARK: - State
    private let stateLock = NSLock()
    private var interruptionDepth = 0
    private var interruptionWasForwarded = false
    private var preparationPending = false
    private var pcmGeneration: UInt64 = 0
    private var interruptionHandler: InterruptionHandler?

    /// True while local playback owns or is claiming the shared audio session.
    var isRenderingAudio: Bool { queueLifecycle.isRenderingAudio }

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
            logInfo("Audio session interrupted")
            let transition = stateLock.withLock { () -> (AudioQueueRef?, InterruptionHandler?) in
                interruptionDepth += 1
                guard interruptionDepth == 1 else { return (nil, nil) }
                pcmGeneration &+= 1
                let detached = queueLifecycle.detachQueue(allowFutureStart: false)
                bufferLock.withLock { pcmBuffer.removeAll() }
                interruptionWasForwarded = detached.wasRendering
                return (detached.queue, interruptionWasForwarded ? interruptionHandler : nil)
            }
            disposeQueue(transition.0)
            transition.1?.onBegan()
        case .ended:
            let handler = stateLock.withLock { () -> InterruptionHandler? in
                guard interruptionDepth > 0 else { return nil }
                interruptionDepth -= 1
                guard interruptionDepth == 0 else { return nil }
                if preparationPending {
                    preparationPending = false
                    queueLifecycle.prepareToPlay()
                }
                guard interruptionWasForwarded else { return nil }
                interruptionWasForwarded = false
                return interruptionHandler
            }
            let resumeAllowed = !AVAudioSession.sharedInstance().secondaryAudioShouldBeSilencedHint
            handler?.onEnded(resumeAllowed: resumeAllowed)
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
        guard queueLifecycle.hasStartedStream else { return }
        let prev = previousRoute?.outputs.first?.portType.rawValue ?? "unknown"
        logInfo("\(prev) disappeared — pausing playback")
        stopAudioQueue(allowFutureStart: false)
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

        // Decode codec header if present
        if let headerBase64 = codecHeader, let headerData = Data(base64Encoded: headerBase64) {
            self.codecHeader = headerData
            logDebug("Decoded codec header: \(headerData.count) bytes")
        } else {
            self.codecHeader = nil
        }

        // Keep late packets gated until the new decoder configuration is ready.
        // An active interruption owns the gate; preparation cannot reopen it.
        let oldQueue = stateLock.withLock { () -> AudioQueueRef? in
            pcmGeneration &+= 1
            let detached = queueLifecycle.detachQueue(allowFutureStart: false)
            bufferLock.withLock { pcmBuffer.removeAll() }
            preparationPending = interruptionDepth > 0
            if !preparationPending { queueLifecycle.prepareToPlay() }
            return detached.queue
        }
        disposeQueue(oldQueue)

        // Create decoder for codec
        do {
            let newDecoder = try AudioDecoderFactory.create(
                codec: currentCodec,
                sampleRate: Int(sampleRate),
                channels: Int(channels),
                bitDepth: Int(bitDepth),
                codecHeader: self.codecHeader
            )
            decoderLock.lock()
            decoder = newDecoder
            decoderLock.unlock()
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
        // Snapshot the producer generation before decoding. Teardown increments it;
        // the append-side recheck below drops work that crossed that boundary.
        let generation = stateLock.withLock { () -> UInt64? in
            guard interruptionDepth == 0, queueLifecycle.acceptsAudio() else { return nil }
            return pcmGeneration
        }
        guard let generation else { return }

        // Start audio queue on first data. The token is invalidated by any
        // concurrent pause/stop while queue construction is in progress.
        if let startToken = queueLifecycle.beginStartIfNeeded() {
            logDebug("First data received (\(swiftData.count) bytes)")
            NowPlayingCoordinator.shared.activatePlayback()
            startAudioQueue(token: startToken)
        }

        decoderLock.lock()
        defer { decoderLock.unlock() }

        guard let decoder = decoder else {
            logDebug("No decoder available — dropping packet")
            return
        }

        do {
            let pcmData = try decoder.decode(swiftData)
            stateLock.withLock {
                guard interruptionDepth == 0,
                      pcmGeneration == generation,
                      queueLifecycle.acceptsAudio() else { return }
                bufferLock.withLock { pcmBuffer.append(pcmData) }
            }
        } catch {
            logDebug("Decode error: \(error)")
        }
    }

    func stopRawPcmStream() {
        logInfo("Stopping stream")
        stopAudioQueue(allowFutureStart: false)

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
        tearDownQueue(allowFutureStart: false)
    }

    /// Reactivating the session reclaims audio from another app that grabbed it.
    /// Re-open the write gate; the queue rebuilds on the next audio packet.
    func resumeSink() {
        logInfo("resumeSink")
        let resumed = stateLock.withLock { () -> Bool in
            guard interruptionDepth == 0 else { return false }
            preparationPending = false
            queueLifecycle.resume { queue in AudioQueueStart(queue, nil) == noErr }
            return true
        }
        if resumed { NowPlayingCoordinator.shared.activatePlayback() }
    }

    /// Drop buffered PCM (track transition / playback-delay re-phase).
    func flush() {
        bufferLock.lock()
        pcmBuffer.removeAll()
        bufferLock.unlock()
    }

    func setVolume(volume: Int32) {
        let floatVolume = Float(volume) / 100.0
        queueLifecycle.withQueue { queue in
            AudioQueueSetParameter(queue, kAudioQueueParam_Volume, floatVolume)
        }
    }

    func setMuted(muted: Bool) {
        queueLifecycle.withQueue { queue in
            AudioQueueSetParameter(queue, kAudioQueueParam_Volume, muted ? 0.0 : 1.0)
        }
    }

    func dispose() {
        // The Now Playing surface is cleared by the track channel going null
        // (pipeline teardown removes the current item); no direct clear here.
        stopAudioQueue(allowFutureStart: false)
        decoderLock.lock()
        decoder = nil
        decoderLock.unlock()
    }

    // MARK: - AudioQueue Management

    private func startAudioQueue(token: AudioQueueLifecycle.StartToken) {
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
            queueLifecycle.cancelStart(token)
            logError("Failed to create AudioQueue: \(status)")
            return
        }

        // Allocate and prime buffers before publishing the handle. A concurrent
        // pause may invalidate `token` while this work is in progress.
        for _ in 0..<kNumberOfBuffers {
            var buffer: AudioQueueBufferRef?
            let allocStatus = AudioQueueAllocateBuffer(queue, kBufferSize, &buffer)

            if allocStatus == noErr, let buffer = buffer {
                fillBuffer(queue: queue, buffer: buffer)
            }
        }

        var startStatus: OSStatus = noErr
        let installed = queueLifecycle.installIfCurrent(queue, token: token) { queue in
            startStatus = AudioQueueStart(queue, nil)
            return startStatus == noErr
        }
        guard installed else {
            // A pause/stop won while this queue was being constructed, or start
            // failed. It was never published, so this thread owns disposal.
            AudioQueueDispose(queue, true)
            if startStatus != noErr {
                logError("Failed to start AudioQueue: \(startStatus)")
            } else {
                logInfo("Discarded AudioQueue created during concurrent teardown")
            }
            return
        }
        logInfo("AudioQueue started")
    }

    private func stopAudioQueue(allowFutureStart: Bool = true) {
        tearDownQueue(allowFutureStart: allowFutureStart)
    }

    /// `AudioQueueStop(_, true)` discards enqueued hardware buffers, so a rebuilt
    /// queue never replays stale audio.
    @discardableResult
    private func tearDownQueue(allowFutureStart: Bool) -> Bool {
        let detached = stateLock.withLock {
            pcmGeneration &+= 1
            return queueLifecycle.detachQueue(
                allowFutureStart: allowFutureStart && interruptionDepth == 0
            )
        }
        disposeQueue(detached.queue)
        return detached.wasRendering
    }

    private func disposeQueue(_ queue: AudioQueueRef?) {
        guard let queue else { return }
        AudioQueueStop(queue, true)
        AudioQueueDispose(queue, true)
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

    func setInterruptionHandler(handler: InterruptionHandler?) {
        stateLock.withLock { interruptionHandler = handler }
    }

    func setLongFormSeekIntervals(backSeconds: Int64, forwardSeconds: Int64) {
        NowPlayingCoordinator.shared.setLongFormSeekIntervals(
            backSeconds: backSeconds,
            forwardSeconds: forwardSeconds
        )
    }

    func setRemoteCommandHandler(handler: RemoteCommandHandler?) {
        self.remoteCommandHandler = handler

        NowPlayingCoordinator.shared.setCommandHandler { [weak self] command in
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
