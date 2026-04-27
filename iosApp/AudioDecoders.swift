import Foundation
import AVFoundation
import AudioToolbox

// Import external decoder libraries
// NOTE: These must be added via Xcode SPM:
// - https://github.com/alta/swift-opus.git
// - https://github.com/sbooth/flac-binary-xcframework.git
// - https://github.com/sbooth/ogg-binary-xcframework.git
import Opus
import FLAC

/// Protocol for audio decoders
protocol NativeAudioDecoder {
    func decode(_ data: Data) throws -> Data
}

/// Factory to create appropriate decoder
enum AudioDecoderFactory {
    static func create(
        codec: String,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        codecHeader: Data?
    ) throws -> NativeAudioDecoder {
        switch codec.lowercased() {
        case "pcm":
            return PCMPassthroughDecoder(bitDepth: bitDepth, channels: channels)
        case "flac":
            return try FLACLibDecoder(sampleRate: sampleRate, channels: channels, bitDepth: bitDepth, header: codecHeader)
        case "opus":
            return try OpusLibDecoder(sampleRate: sampleRate, channels: channels, bitDepth: bitDepth)
        default:
            throw AudioDecoderError.unsupportedCodec(codec)
        }
    }
}

// MARK: - PCM Passthrough Decoder

/// PCM decoder - handles 16/24/32 bit formats
class PCMPassthroughDecoder: NativeAudioDecoder {
    private let bitDepth: Int
    private let channels: Int
    
    init(bitDepth: Int, channels: Int) {
        self.bitDepth = bitDepth
        self.channels = channels
    }
    
    func decode(_ data: Data) throws -> Data {
        switch bitDepth {
        case 16, 32:
            return data
        case 24:
            return try unpack24Bit(data)
        default:
            throw AudioDecoderError.unsupportedBitDepth(bitDepth)
        }
    }
    
    private func unpack24Bit(_ data: Data) throws -> Data {
        let bytesPerSample = 3
        guard data.count % bytesPerSample == 0 else {
            throw AudioDecoderError.invalidDataSize
        }
        
        let sampleCount = data.count / bytesPerSample
        var samples = [Int32]()
        samples.reserveCapacity(sampleCount)
        
        let bytes = [UInt8](data)
        
        for i in 0..<sampleCount {
            let offset = i * bytesPerSample
            let b0 = Int32(bytes[offset])
            let b1 = Int32(bytes[offset + 1])
            let b2 = Int32(bytes[offset + 2])
            
            var sample = (b2 << 16) | (b1 << 8) | b0
            if sample & 0x800000 != 0 {
                sample |= Int32(bitPattern: 0xFF000000)
            }
            sample <<= 8
            samples.append(sample)
        }
        
        return samples.withUnsafeBytes { Data($0) }
    }
}

// MARK: - Opus Decoder using swift-opus

/// Opus decoder using swift-opus (libopus wrapper).
///
/// Outputs interleaved Int16 PCM directly — matches AudioQueue's configured
/// format (kLinearPCMFormatFlagIsSignedInteger, 16-bit) so no float→int16
/// conversion is needed.
///
/// One inbound `Data` is expected to be exactly one complete Opus packet —
/// swift-opus calls `opus_decode` per packet. The KMP layer's
/// `AudioStreamManager` preserves frame boundaries by sending one binary
/// message per packet, so this contract holds.
class OpusLibDecoder: NativeAudioDecoder {
    private let decoder: Opus.Decoder
    private let channels: Int
    private let sampleRate: Int

    init(sampleRate: Int, channels: Int, bitDepth: Int) throws {
        self.channels = channels
        self.sampleRate = sampleRate

        // swift-opus REQUIRES interleaved layout for stereo (see
        // AVAudioFormat.isValidOpusPCMFormat), and AudioQueue wants Int16.
        // Use the helper that picks the right interleaved flag per channel count.
        guard let format = AVAudioFormat(
            opusPCMFormat: .int16,
            sampleRate: Double(sampleRate),
            channels: AVAudioChannelCount(channels)
        ) else {
            throw AudioDecoderError.decodingFailed("Failed to create audio format for Opus")
        }

        do {
            self.decoder = try Opus.Decoder(format: format)
            print("🎵 OpusLibDecoder: ✅ Created decoder for \(sampleRate)Hz, \(channels)ch")
        } catch {
            throw AudioDecoderError.decodingFailed("Opus decoder: \(error.localizedDescription)")
        }
    }

    func decode(_ data: Data) throws -> Data {
        // Decode one Opus packet → AVAudioPCMBuffer of Int16 PCM.
        let pcmBuffer: AVAudioPCMBuffer
        do {
            pcmBuffer = try decoder.decode(data)
        } catch {
            throw AudioDecoderError.decodingFailed("Opus decode failed: \(error.localizedDescription)")
        }

        guard let int16ChannelData = pcmBuffer.int16ChannelData else {
            throw AudioDecoderError.decodingFailed("No int16 channel data in decoded buffer")
        }

        let frameLength = Int(pcmBuffer.frameLength)
        let totalSamples = frameLength * channels

        // For mono and interleaved stereo, AVAudioPCMBuffer exposes a single
        // channel pointer at [0] containing `frameLength * channels` Int16
        // samples (interleaved as L, R, L, R, ... for stereo).
        let buffer = UnsafeBufferPointer(start: int16ChannelData[0], count: totalSamples)
        return Data(buffer: buffer)
    }
}

// MARK: - FLAC Decoder using libFLAC

/// FLAC decoder using libFLAC C library
class FLACLibDecoder: NativeAudioDecoder {
    private var decoder: UnsafeMutablePointer<FLAC__StreamDecoder>?
    private let sampleRate: Int
    private let channels: Int
    private let bitDepth: Int
    
    // Buffer for input data
    private var pendingData: Data = Data()
    private var readOffset: Int = 0
    
    // Buffer for decoded samples (Int32 to preserve native bit depth)
    private var decodedSamples: [Int32] = []
    private var lastError: FLAC__StreamDecoderErrorStatus?
    
    init(sampleRate: Int, channels: Int, bitDepth: Int, header: Data?) throws {
        self.sampleRate = sampleRate
        self.channels = channels
        self.bitDepth = bitDepth
        
        // Create FLAC stream decoder
        guard let flacDecoder = FLAC__stream_decoder_new() else {
            throw AudioDecoderError.decodingFailed("Failed to create FLAC stream decoder")
        }
        self.decoder = flacDecoder
        
        // Initialize decoder with callbacks
        let clientData = Unmanaged.passUnretained(self).toOpaque()
        
        let initStatus = FLAC__stream_decoder_init_stream(
            decoder,
            // Read callback
            { decoder, buffer, bytes, clientData -> FLAC__StreamDecoderReadStatus in
                guard let clientData = clientData else {
                    return FLAC__STREAM_DECODER_READ_STATUS_ABORT
                }
                let selfRef = Unmanaged<FLACLibDecoder>.fromOpaque(clientData).takeUnretainedValue()
                return selfRef.readCallback(buffer: buffer, bytes: bytes)
            },
            nil,  // seek callback
            nil,  // tell callback
            nil,  // length callback
            nil,  // eof callback
            // Write callback
            { decoder, frame, buffer, clientData -> FLAC__StreamDecoderWriteStatus in
                guard let clientData = clientData else {
                    return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT
                }
                let selfRef = Unmanaged<FLACLibDecoder>.fromOpaque(clientData).takeUnretainedValue()
                return selfRef.writeCallback(frame: frame, buffer: buffer)
            },
            nil,  // metadata callback
            // Error callback
            { decoder, status, clientData in
                guard let clientData = clientData else { return }
                let selfRef = Unmanaged<FLACLibDecoder>.fromOpaque(clientData).takeUnretainedValue()
                selfRef.lastError = status
                print("🎵 FLACLibDecoder: Error callback - status \(status.rawValue)")
            },
            clientData
        )
        
        guard initStatus == FLAC__STREAM_DECODER_INIT_STATUS_OK else {
            FLAC__stream_decoder_delete(flacDecoder)
            throw AudioDecoderError.decodingFailed("FLAC decoder init failed: \(initStatus.rawValue)")
        }
        
        print("🎵 FLACLibDecoder: ✅ Created decoder for \(sampleRate)Hz, \(channels)ch, \(bitDepth)bit")
        
        // If we have a codec header, add it to pending data
        if let header = header {
            pendingData.append(header)
            print("🎵 FLACLibDecoder: Added \(header.count) bytes codec header")
        }
    }
    
    /// Maximum pending buffer size (1 MB). If exceeded, the buffer is reset to
    /// prevent unbounded memory growth from malformed or undecodable data.
    private let maxPendingSize = 1_048_576

    func decode(_ data: Data) throws -> Data {
        // Append new data to pending buffer
        pendingData.append(data)

        if pendingData.count > maxPendingSize {
            print("🎵 FLACLibDecoder: ⚠️ Pending buffer exceeded \(maxPendingSize) bytes (\(pendingData.count)), resetting")
            pendingData.removeAll(keepingCapacity: true)
            readOffset = 0
        }
        decodedSamples.removeAll(keepingCapacity: true)
        lastError = nil
        
        guard let decoder = decoder else {
            throw AudioDecoderError.notInitialized
        }
        
        // Process blocks until we get audio samples
        let startOffset = readOffset
        var iterations = 0
        
        while decodedSamples.isEmpty && iterations < 100 {
            iterations += 1
            let success = FLAC__stream_decoder_process_single(decoder)
            let state = FLAC__stream_decoder_get_state(decoder)
            
            guard success != 0 else {
                print("🎵 FLACLibDecoder: process_single returned false, state=\(state.rawValue)")
                break
            }
            
            if state == FLAC__STREAM_DECODER_END_OF_STREAM {
                break
            }
            
            // If readOffset didn't advance, we need more data
            if readOffset == startOffset && iterations > 1 {
                break
            }
        }
        
        // Remove consumed bytes from pending buffer
        let bytesConsumed = readOffset - startOffset
        if bytesConsumed > 0 {
            pendingData.removeFirst(bytesConsumed)
            readOffset = startOffset
        }
        
        // Return decoded samples as Data (Int32 format, scaled to full range)
        return decodedSamples.withUnsafeBytes { Data($0) }
    }
    
    private func readCallback(buffer: UnsafeMutablePointer<FLAC__byte>?, bytes: UnsafeMutablePointer<Int>?) -> FLAC__StreamDecoderReadStatus {
        guard let buffer = buffer, let bytes = bytes else {
            return FLAC__STREAM_DECODER_READ_STATUS_ABORT
        }
        
        let bytesToRead = min(bytes.pointee, pendingData.count - readOffset)
        
        guard bytesToRead > 0 else {
            bytes.pointee = 0
            return FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM
        }
        
        pendingData.withUnsafeBytes { srcBytes in
            let src = srcBytes.baseAddress!.advanced(by: readOffset)
            memcpy(buffer, src, bytesToRead)
        }
        
        readOffset += bytesToRead
        bytes.pointee = bytesToRead
        
        return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE
    }
    
    private func writeCallback(frame: UnsafePointer<FLAC__Frame>?, buffer: UnsafePointer<UnsafePointer<FLAC__int32>?>?) -> FLAC__StreamDecoderWriteStatus {
        guard let frame = frame, let buffer = buffer else {
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT
        }

        let blocksize = Int(frame.pointee.header.blocksize)
        // Scale samples to fill Int32 range: shift left by (32 - source bit depth)
        let shift = Int32(32 - bitDepth)

        // FLAC outputs int32 samples per channel
        // Interleave channels and scale to Int32 range
        for i in 0..<blocksize {
            for channel in 0..<channels {
                guard let channelBuffer = buffer[channel] else {
                    return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT
                }
                decodedSamples.append(channelBuffer[i] << shift)
            }
        }

        return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE
    }
    
    deinit {
        if let decoder = decoder {
            FLAC__stream_decoder_finish(decoder)
            FLAC__stream_decoder_delete(decoder)
        }
    }
}

// MARK: - Errors

enum AudioDecoderError: Error, LocalizedError {
    case unsupportedCodec(String)
    case unsupportedBitDepth(Int)
    case invalidDataSize
    case converterCreationFailed(OSStatus)
    case notInitialized
    case decodingFailed(String)
    
    var errorDescription: String? {
        switch self {
        case .unsupportedCodec(let codec):
            return "Unsupported codec: \(codec)"
        case .unsupportedBitDepth(let depth):
            return "Unsupported bit depth: \(depth)"
        case .invalidDataSize:
            return "Invalid data size"
        case .converterCreationFailed(let status):
            return "AudioConverter creation failed: \(status)"
        case .notInitialized:
            return "Decoder not initialized"
        case .decodingFailed(let reason):
            return "Decoding failed: \(reason)"
        }
    }
}
