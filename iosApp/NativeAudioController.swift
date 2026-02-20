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
    private var streamStarted = false
    
    override init() {
        super.init()
        print("🎵 NativeAudioController: Initialized")

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
            // System has paused AudioQueue automatically
            print("🎵 NativeAudioController: Audio session interrupted")
        case .ended:
            let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) && isPlaying, let queue = audioQueue {
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                    AudioQueueStart(queue, nil)
                    print("🎵 NativeAudioController: ✅ Resumed after interruption")
                } catch {
                    print("🎵 NativeAudioController: ❌ Failed to resume after interruption: \(error)")
                }
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
            // Headphones or Bluetooth device disconnected — standard iOS behaviour pauses audio
            print("🎵 NativeAudioController: Audio output device disconnected")
        }
    }
    
    // MARK: - PlatformAudioPlayer Protocol
    
    func prepareStream(codec: String, sampleRate: Int32, channels: Int32, bitDepth: Int32, codecHeader: String?, listener: MediaPlayerListener) {
        print("🎵 NativeAudioController: prepareStream - codec=\(codec), rate=\(sampleRate), ch=\(channels), bit=\(bitDepth)")
        
        self.listener = listener
        self.currentCodec = codec.lowercased()
        self.currentSampleRate = sampleRate
        self.currentChannels = channels
        self.currentBitDepth = bitDepth
        self.streamStarted = false
        
        // Decode codec header if present
        if let headerBase64 = codecHeader, let headerData = Data(base64Encoded: headerBase64) {
            self.codecHeader = headerData
            print("🎵 NativeAudioController: Decoded codec header: \(headerData.count) bytes")
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
            print("🎵 NativeAudioController: Created decoder for \(codec)")
        } catch {
            print("🎵 NativeAudioController: ❌ Failed to create decoder: \(error)")
            listener.onError(error: KotlinThrowable(message: error.localizedDescription))
            return
        }
        
        listener.onReady()
    }
    
    /// Called from Kotlin via efficient NSData bulk-copy path (avoids per-byte Swift interop).
    func writeRawPcmNSData(data: NSData) {
        processAudioData(data as Data)
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
        // Start audio queue on first data
        if !streamStarted {
            streamStarted = true
            print("🎵 NativeAudioController: First data received (\(swiftData.count) bytes)")
            NowPlayingManager.shared.activatePlayback()
            startAudioQueue()
        }

        guard let decoder = decoder else {
            print("🎵 NativeAudioController: ❌ No decoder available")
            return
        }

        do {
            let pcmData = try decoder.decode(swiftData)
            bufferLock.lock()
            pcmBuffer.append(pcmData)
            bufferLock.unlock()
        } catch {
            print("🎵 NativeAudioController: ❌ Decode error: \(error)")
        }
    }
    
    func stopRawPcmStream() {
        print("🎵 NativeAudioController: Stopping stream")
        streamStarted = false
        stopAudioQueue()
        
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
        
        // For 24-bit, decoder outputs 32-bit Int32
        let effectiveBitDepth = (currentBitDepth == 24) ? 32 : currentBitDepth
        let bytesPerSample = effectiveBitDepth / 8
        
        audioFormat.mBitsPerChannel = UInt32(effectiveBitDepth)
        audioFormat.mBytesPerFrame = UInt32(currentChannels) * UInt32(bytesPerSample)
        audioFormat.mBytesPerPacket = audioFormat.mBytesPerFrame
        
        print("🎵 NativeAudioController: Audio format - \(currentSampleRate)Hz, \(currentChannels)ch, \(effectiveBitDepth)bit")
        
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
            print("🎵 NativeAudioController: ❌ Failed to create AudioQueue: \(status)")
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
            print("🎵 NativeAudioController: ✅ AudioQueue started")
        } else {
            print("🎵 NativeAudioController: ❌ Failed to start AudioQueue: \(startStatus)")
        }
    }
    
    private func stopAudioQueue() {
        guard let queue = audioQueue else { return }
        
        AudioQueueStop(queue, true)
        AudioQueueDispose(queue, true)
        
        audioQueue = nil
        isPlaying = false
        print("🎵 NativeAudioController: AudioQueue stopped")
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
    
    func updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    ) {
        NowPlayingManager.shared.updateNowPlayingInfo(
            title: title,
            artist: artist,
            album: album,
            artworkUrl: artworkUrl,
            duration: duration,
            elapsedTime: elapsedTime,
            playbackRate: playbackRate
        )
    }
    
    func clearNowPlaying() {
        NowPlayingManager.shared.clearNowPlayingInfo()
    }
    
    func setRemoteCommandHandler(handler: RemoteCommandHandler?) {
        self.remoteCommandHandler = handler
        
        NowPlayingManager.shared.setCommandHandler { [weak self] command in
            print("🎵 NativeAudioController: Remote command: \(command)")
            self?.remoteCommandHandler?.onCommand(command: command)
        }
    }
}

// MARK: - AudioQueue Callback

private let audioQueueCallback: AudioQueueOutputCallback = { userData, queue, buffer in
    guard let userData = userData else { return }
    
    let controller = Unmanaged<NativeAudioController>.fromOpaque(userData).takeUnretainedValue()
    controller.fillBuffer(queue: queue, buffer: buffer)
}
