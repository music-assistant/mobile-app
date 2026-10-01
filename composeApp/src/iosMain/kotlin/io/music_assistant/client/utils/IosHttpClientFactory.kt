package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodClientCertificate
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionTask

/**
 * Darwin clients that answer a client-certificate challenge (mTLS) with the identity
 * imported into [keychain]. Every other challenge keeps the system default handling.
 */
class IosHttpClientFactory(
    private val clientCertificateAlias: StateFlow<String?>,
    private val keychain: KeychainClientIdentity,
) : HttpClientFactory {
    override fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
        engine { handleChallenge(::answerChallenge) }
        block()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun answerChallenge(
        session: NSURLSession,
        task: NSURLSessionTask,
        challenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        challenge.protectionSpace.authenticationMethod
            .takeIf { it == NSURLAuthenticationMethodClientCertificate && clientCertificateAlias.value != null }
            ?.let { keychain.credential() }
            ?.let { completionHandler(NSURLSessionAuthChallengeUseCredential, it) }
            ?: completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, challenge.proposedCredential)
    }
}
