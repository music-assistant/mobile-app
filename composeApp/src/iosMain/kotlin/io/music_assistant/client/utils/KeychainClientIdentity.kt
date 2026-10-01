package io.music_assistant.client.utils

import cnames.structs.__CFDictionary
import cnames.structs.__SecIdentity
import co.touchlab.kermit.Logger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRefVar
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.credentialWithIdentity
import platform.Security.SecCertificateCopySubjectSummary
import platform.Security.SecCertificateRefVar
import platform.Security.SecIdentityCopyCertificate
import platform.Security.SecIdentityRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecPKCS12Import
import platform.Security.errSecAuthFailed
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrLabel
import platform.Security.kSecClass
import platform.Security.kSecClassIdentity
import platform.Security.kSecImportExportPassphrase
import platform.Security.kSecImportItemIdentity
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnRef
import platform.Security.kSecValueRef

/**
 * The single client identity (certificate and private key) imported from a `.p12` file
 * and kept in the Keychain. Readable after the first unlock, so a reconnect during
 * background playback with the screen locked still finds it.
 *
 * All calls block: run [import] off the main thread, PKCS#12 key derivation is slow.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainClientIdentity {
    sealed interface ImportResult {
        /** [name] is the certificate subject summary, shown to the user. */
        data class Imported(val name: String) : ImportResult
        data object WrongPassword : ImportResult
        data object Failed : ImportResult
    }

    private val log = Logger.withTag("KeychainClientIdentity")

    /** Replaces the stored identity with the first one in [pkcs12]. */
    fun import(pkcs12: NSData, password: String, fallbackName: String): ImportResult =
        decode(pkcs12, password) { identity ->
            SecItemDelete(identityQuery())
            val added = SecItemAdd(
                dictionary(
                    kSecValueRef to identity,
                    kSecAttrLabel to bridge(LABEL),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ),
                null,
            )
            if (added != errSecSuccess) {
                log.w { "Keychain add failed: $added" }
                return@decode ImportResult.Failed
            }
            ImportResult.Imported(subjectSummary(identity) ?: fallbackName)
        }

    /** [import] without the Keychain write. The bare simulator test runner has no Keychain. */
    internal fun inspect(pkcs12: NSData, password: String, fallbackName: String): ImportResult =
        decode(pkcs12, password) { ImportResult.Imported(subjectSummary(it) ?: fallbackName) }

    private inline fun decode(
        pkcs12: NSData,
        password: String,
        onIdentity: CfScope.(SecIdentityRef) -> ImportResult,
    ): ImportResult = cfScoped {
        val items = mem.alloc<CFArrayRefVar>()
        val status = SecPKCS12Import(
            bridge(pkcs12)?.reinterpret(),
            dictionary(kSecImportExportPassphrase to bridge(password)),
            items.ptr,
        )
        own(items.value)
        if (status == errSecAuthFailed) return@cfScoped ImportResult.WrongPassword
        items.value
            ?.takeIf { status == errSecSuccess && CFArrayGetCount(it) > 0 }
            ?.let { CFArrayGetValueAtIndex(it, 0)?.reinterpret<__CFDictionary>() }
            ?.let { CFDictionaryGetValue(it, kSecImportItemIdentity)?.reinterpret<__SecIdentity>() }
            ?.let { onIdentity(it) }
            ?: ImportResult.Failed.also { log.w { "PKCS#12 import failed: $status" } }
    }

    /** The stored identity as a TLS client credential, or null when there is none. */
    fun credential(): NSURLCredential? = cfScoped {
        val result = mem.alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(
            identityQuery(kSecReturnRef to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
            result.ptr,
        )
        own(result.value)
        result.value
            ?.takeIf { status == errSecSuccess }
            ?.let {
                NSURLCredential.credentialWithIdentity(
                    it.reinterpret<__SecIdentity>(),
                    null,
                    NSURLCredentialPersistence.NSURLCredentialPersistenceNone,
                )
            }
    }

    fun clear() {
        cfScoped { SecItemDelete(identityQuery()) }
    }

    private fun CfScope.identityQuery(vararg extra: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef? =
        dictionary(kSecClass to kSecClassIdentity, kSecAttrLabel to bridge(LABEL), *extra)

    private fun CfScope.subjectSummary(identity: SecIdentityRef): String? {
        val certificate = mem.alloc<SecCertificateRefVar>()
        if (SecIdentityCopyCertificate(identity, certificate.ptr) != errSecSuccess) return null
        own(certificate.value)
        return certificate.value
            ?.let { SecCertificateCopySubjectSummary(it) }
            ?.let { CFBridgingRelease(it) as? String }
    }

    private companion object {
        const val LABEL = "io.music_assistant.client.mtls"
    }
}

/** Releases every Core Foundation object it created or was handed, when the block ends. */
@OptIn(ExperimentalForeignApi::class)
private class CfScope(val mem: MemScope) {
    private val owned = mutableListOf<CFTypeRef>()

    fun <T : CPointer<*>> own(ref: T?): T? = ref?.also { owned += it }

    fun bridge(value: Any): CFTypeRef? = own(CFBridgingRetain(value))

    fun dictionary(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef? = own(
        CFDictionaryCreateMutable(
            null,
            pairs.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ),
    )?.also { dict -> pairs.forEach { (key, value) -> CFDictionaryAddValue(dict, key, value) } }

    fun releaseAll() = owned.forEach(::CFRelease)
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <R> cfScoped(block: CfScope.() -> R): R = memScoped {
    val scope = CfScope(this)
    try {
        scope.block()
    } finally {
        scope.releaseAll()
    }
}
