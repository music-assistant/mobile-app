package io.music_assistant.client.utils

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp clients that present the KeyChain certificate [clientCertificateAlias] names
 * when a server asks for one (mTLS). Trust stays the platform default, so the network
 * security config still applies.
 */
class AndroidHttpClientFactory(
    context: Context,
    clientCertificateAlias: StateFlow<String?>,
) : HttpClientFactory {
    private val trustManager: X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    private val socketFactory =
        KeyChainSocketFactory(context.applicationContext, clientCertificateAlias, trustManager)

    // Shared by every client, so a certificate change can drop idle connections
    // that completed their handshake with the previous certificate (or none).
    private val connectionPool = ConnectionPool()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            clientCertificateAlias.drop(1).collect { connectionPool.evictAll() }
        }
    }

    override fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(socketFactory, trustManager)
                connectionPool(connectionPool)
            }
        }
        block()
    }
}
