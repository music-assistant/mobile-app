package io.music_assistant.client.utils

import android.content.Context
import android.security.KeyChain
import android.security.KeyChainException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Delegates to an `SSLContext` built for the current [alias]. A fresh context per alias
 * also means a TLS session made with another certificate (or none) is never resumed.
 */
internal class KeyChainSocketFactory(
    private val context: Context,
    private val alias: StateFlow<String?>,
    private val trustManager: X509TrustManager,
) : SSLSocketFactory() {
    private class Delegate(val alias: String?, val factory: SSLSocketFactory)

    @Volatile
    private var delegate: Delegate? = null

    private fun current(): SSLSocketFactory {
        val alias = alias.value
        return delegate?.takeIf { it.alias == alias }?.factory
            ?: Delegate(alias, build(alias)).also { delegate = it }.factory
    }

    private fun build(alias: String?): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply {
            init(alias?.let { arrayOf<KeyManager>(KeyChainKeyManager(context, it)) }, arrayOf(trustManager), null)
        }.socketFactory

    override fun getDefaultCipherSuites(): Array<String> = current().defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = current().supportedCipherSuites

    override fun createSocket(): Socket = current().createSocket()

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        current().createSocket(s, host, port, autoClose)

    override fun createSocket(host: String?, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        current().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress?, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket = current().createSocket(address, port, localAddress, localPort)
}

/**
 * Offers the KeyChain certificate [alias] to any server that asks for a client certificate.
 * KeyChain reads block, which is fine: handshakes run on OkHttp threads, never on main.
 */
private class KeyChainKeyManager(
    private val context: Context,
    private val alias: String,
) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) =
        alias

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) =
        alias

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        readKeyChain { KeyChain.getCertificateChain(context, it) }

    override fun getPrivateKey(alias: String?): PrivateKey? = readKeyChain { KeyChain.getPrivateKey(context, it) }

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null

    // The user can delete the certificate or revoke the grant in system settings.
    // Then the handshake goes on without a certificate and the server decides.
    private fun <T> readKeyChain(read: (String) -> T?): T? = try {
        read(alias)
    } catch (e: KeyChainException) {
        Logger.withTag("KeyChainKeyManager").w(e) { "Client certificate unavailable" }
        null
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    }
}
