package io.music_assistant.client.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.sendspin.protocol.SendSpinLogLevel
import com.sendspin.protocol.installSendSpinLogger
import coil3.SingletonImageLoader
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.imageloader.WebRTCImageFetcher
import io.music_assistant.client.imageloader.buildAppImageLoader
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.logging.platformLogWriters
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatform

fun initKoin(
    vararg platformModules: Module,
    verboseLogging: Boolean = false,
    config: KoinAppDeclaration? = null,
) {
    // Release builds drop Debug/Verbose logs: the WebSocket layer emits ~4 debug
    // lines/sec for the idle clock-sync heartbeat, which otherwise floods (and
    // evicts useful entries from) InMemoryLogWriter for the entire session.
    Logger.setMinSeverity(if (verboseLogging) Severity.Verbose else Severity.Info)
    // setLogWriters (not addLogWriter) so each platform's console sink replaces
    // Kermit's default rather than doubling it: on iOS the default NSLog writer
    // renders every line as <private>, which would otherwise duplicate the
    // public os.Logger bridge.
    Logger.setLogWriters(listOf(InMemoryLogWriter) + platformLogWriters())
    // Bridge the sendspin-kmp library's logging into Kermit.
    val sendspinLog = Logger.withTag("sendspin-protocol")
    installSendSpinLogger { level, throwable, message ->
        when (level) {
            SendSpinLogLevel.VERBOSE -> sendspinLog.v(throwable) { message }
            SendSpinLogLevel.DEBUG -> sendspinLog.d(throwable) { message }
            SendSpinLogLevel.INFO -> sendspinLog.i(throwable) { message }
            SendSpinLogLevel.WARN -> sendspinLog.w(throwable) { message }
            SendSpinLogLevel.ERROR -> sendspinLog.e(throwable) { message }
        }
    }
    startKoin {
        config?.invoke(this)
        modules(sharedModule(), webrtcModule, *platformModules)
    }
    val webrtcFetcherFactory = WebRTCImageFetcher.Factory(KoinPlatform.getKoin().get<ServiceClient>())
    SingletonImageLoader.setSafe { context -> buildAppImageLoader(context, webrtcFetcherFactory) }
}
