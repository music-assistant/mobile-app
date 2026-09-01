package io.music_assistant.client.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.media.MediaSessionManager
import coil3.BitmapImage
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import io.music_assistant.client.imageloader.ARTWORK_DECODE_SIZE
import io.music_assistant.client.imageloader.artworkImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val JPEG_QUALITY = 88
private const val MAX_CONCURRENT_FETCHES = 4
private const val MAX_SOURCE_URL_LENGTH = 4_096
private const val MAX_ENCODED_TOKEN_LENGTH = 8_192
private const val AES_KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val TOKEN_PREFERENCES = "android_auto_artwork"
private const val TOKEN_KEY = "token_key"
private const val ARTWORK_PATH = "art"
private const val DISPLAY_NAME_PREFIX_LENGTH = 16
private const val UNKNOWN_CALLER_PID = -1
private const val BASE64_URL_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

/**
 * Maps an artwork URL to a self-contained authenticated token, and back.
 *
 * The source URL never appears in plaintext in the URI handed to a media host. Because the
 * installation key is persisted, a host can reuse a cached content URI after our process is
 * recreated, and no lookup table has to survive with it.
 *
 * The GCM nonce is *derived from the plaintext* rather than drawn at random, so the same artwork
 * URL always maps to the same URI. That is what makes the host's own URI-keyed image cache work: a
 * random nonce would mint a fresh URI on every browse response and force a refetch each time.
 * Nonce reuse is safe here precisely because it only recurs for an identical plaintext (the
 * synthetic-IV construction). The single thing this leaks is URL equality, which anyone reading the
 * browse list already sees. Encryption and derivation use separate subkeys.
 */
internal class AutoArtworkTokenCodec(masterKey: ByteArray) {
    init {
        require(masterKey.size == AES_KEY_BYTES) { "Artwork token key must be 256 bits" }
    }

    private val encryptionKey = SecretKeySpec(masterKey.derive(ENCRYPTION_LABEL), "AES")
    private val nonceKey = masterKey.derive(NONCE_LABEL)

    fun uriFor(authority: String, sourceUrl: String): Uri? {
        if (!isSupportedSource(sourceUrl)) return null
        val plaintext = sourceUrl.toByteArray(Charsets.UTF_8)
        val iv = hmac(nonceKey, plaintext).copyOf(GCM_IV_BYTES)
        val encrypted = runCatching {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(plaintext)
            }
        }.getOrNull() ?: return null
        val token = Base64.encodeToString(iv + encrypted, BASE64_URL_FLAGS)
        return Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(ARTWORK_PATH)
            .appendPath(token)
            .build()
    }

    fun sourceFor(authority: String, uri: Uri): String? {
        if (uri.scheme != "content" || uri.authority != authority) return null
        if (uri.pathSegments.size != 2 || uri.pathSegments.first() != ARTWORK_PATH) return null
        val token = uri.pathSegments[1].takeIf { it.length <= MAX_ENCODED_TOKEN_LENGTH } ?: return null
        val payload = runCatching { Base64.decode(token, BASE64_URL_FLAGS) }.getOrNull() ?: return null
        if (payload.size <= GCM_IV_BYTES) return null
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val sourceUrl = runCatching {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(encrypted).toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return sourceUrl.takeIf(::isSupportedSource)
    }

    // Only schemes we ourselves mint. `file://` and `content://` are rejected so a leaked key can
    // never be turned into a local-file read through this provider.
    private fun isSupportedSource(sourceUrl: String): Boolean {
        if (sourceUrl.isBlank() || sourceUrl.length > MAX_SOURCE_URL_LENGTH) return false
        val uri = runCatching { sourceUrl.toUri() }.getOrNull() ?: return false
        if (uri.userInfo != null) return false
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> !uri.host.isNullOrBlank()
            WEBRTC_SCHEME -> !uri.authority.isNullOrBlank() || uri.pathSegments.isNotEmpty()
            else -> false
        }
    }

    private fun ByteArray.derive(label: String): ByteArray =
        hmac(this, label.toByteArray(Charsets.UTF_8))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(key, HMAC_ALGORITHM))
            doFinal(data)
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val ENCRYPTION_LABEL = "ma-auto-artwork-enc"
        const val NONCE_LABEL = "ma-auto-artwork-nonce"

        // Synthetic scheme minted by KtorServiceClient for WebRTC-proxied artwork. Only
        // WebRTCImageFetcher can resolve it, which is exactly why it has to be proxied here.
        const val WEBRTC_SCHEME = "mawebrtc"
    }
}

/**
 * Process-wide bridge between media-description building and [AndroidAutoArtworkProvider].
 *
 * Initialized from the provider's `onCreate`, which the framework runs before
 * `Application.onCreate`, so every caller in this process sees a ready bridge.
 */
internal object AndroidAutoArtwork {
    @Volatile
    private var configuredAuthority: String? = null

    @Volatile
    private var codec: AutoArtworkTokenCodec? = null

    val authority: String
        get() = requireNotNull(configuredAuthority) { "Artwork bridge is not initialized" }

    val rootUri: Uri
        get() = "content://$authority/$ARTWORK_PATH".toUri()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        val applicationAuthority = authorityOf(applicationContext)
        if (codec != null && configuredAuthority == applicationAuthority) return
        synchronized(this) {
            if (codec != null && configuredAuthority == applicationAuthority) return
            check(configuredAuthority == null || configuredAuthority == applicationAuthority) {
                "Artwork bridge cannot change authority within one process"
            }
            configuredAuthority = applicationAuthority
            codec = loadOrCreateKey(applicationContext)?.let(::AutoArtworkTokenCodec)
        }
    }

    /**
     * Local, host-resolvable stand-in for [sourceUrl]. Null when the bridge failed to initialize or
     * the URL is not one we proxy; callers fall back to the default icon.
     */
    fun uriFor(sourceUrl: String): Uri? = codec?.uriFor(authority, sourceUrl)

    fun sourceFor(uri: Uri): String? = codec?.sourceFor(authority, uri)

    /**
     * Grants [packageName] read access to every artwork URI, after checking that the package really
     * belongs to [uid] and that the platform trusts it for media control. Returns false when the
     * caller fails either check, in which case it simply sees the default icon.
     */
    @Suppress("DEPRECATION")
    fun grantReadAccess(context: Context, packageName: String, uid: Int): Boolean {
        if (packageName !in context.packageManager.getPackagesForUid(uid).orEmpty()) return false
        val trusted = uid == context.applicationInfo.uid || MediaSessionManager
            .getSessionManager(context)
            .isTrustedForMediaControl(
                MediaSessionManager.RemoteUserInfo(packageName, UNKNOWN_CALLER_PID, uid),
            )
        if (!trusted) return false
        // A prefix grant on the root covers every item, so browse responses never have to carry
        // per-URI grants.
        context.grantUriPermission(
            packageName,
            rootUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        return true
    }

    fun revokeReadAccess(context: Context, packageName: String) {
        context.revokeUriPermission(packageName, rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun authorityOf(context: Context) = "${context.packageName}.$AUTHORITY_SUFFIX"

    private fun loadOrCreateKey(context: Context): ByteArray? {
        val preferences = context.getSharedPreferences(TOKEN_PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(TOKEN_KEY, null)
            ?.let { stored -> runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.size == AES_KEY_BYTES }
            ?.let { return it }
        val generated = ByteArray(AES_KEY_BYTES).also(SecureRandom()::nextBytes)
        // commit(), not apply(): a token minted before the key reaches disk would be unresolvable
        // after the next process start.
        val persisted = preferences.edit()
            .putString(TOKEN_KEY, Base64.encodeToString(generated, Base64.NO_WRAP))
            .commit()
        if (!persisted) {
            androidAutoLog.e { "Unable to persist Android Auto artwork token key" }
            return null
        }
        return generated
    }

    private const val AUTHORITY_SUFFIX = "autoartwork"
}

/**
 * Read-only, grant-scoped artwork provider for Android Auto and other MediaBrowser hosts.
 *
 * A media host that fetches an artwork URL itself does so in its own process and its own UID. That
 * fails whenever the server is only reachable through routing the app has and the host does not (a
 * split-tunnel VPN), and it always fails for `mawebrtc://` URLs, which only this process can
 * resolve. So hosts get an opaque local URI instead: this provider decodes the token, fetches under
 * the Music Assistant UID, downsamples, and streams a bounded JPEG. No bitmap is ever placed in a
 * MediaBrowser Binder transaction.
 */
class AndroidAutoArtworkProvider : ContentProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchSlots = Semaphore(MAX_CONCURRENT_FETCHES)
    private val logger = androidAutoLog.withTag("ArtworkProvider")

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        AndroidAutoArtwork.initialize(providerContext)
        return true
    }

    override fun getType(uri: Uri): String? = AndroidAutoArtwork.sourceFor(uri)?.let { MIME_TYPE }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Android Auto artwork is read-only")
        val sourceUrl = AndroidAutoArtwork.sourceFor(uri)
            ?: throw FileNotFoundException("Unknown Android Auto artwork")
        val providerContext = context ?: throw FileNotFoundException("Provider unavailable")
        val (readSide, writeSide) = runCatching { ParcelFileDescriptor.createReliablePipe() }
            .getOrElse { error ->
                throw FileNotFoundException("Unable to create artwork pipe").apply { initCause(error) }
            }
        scope.launch {
            try {
                // Bounded, but queued rather than rejected: a host that is refused an image does not
                // reliably retry, so dropping under a fast scroll would leave permanent holes.
                val jpeg = fetchSlots.withPermit { loadArtwork(providerContext, sourceUrl) }
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { it.write(jpeg) }
            } catch (error: Throwable) {
                runCatching { writeSide.closeWithError("Artwork unavailable") }
                logger.w { "Unable to serve Android Auto artwork (${error::class.simpleName})" }
            }
        }
        return readSide
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (AndroidAutoArtwork.sourceFor(uri) == null) return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        // Size is unknown until the artwork is fetched and encoded, and the name must not leak the
        // source, so it is a prefix of the opaque token.
        val displayName = uri.lastPathSegment.orEmpty().take(DISPLAY_NAME_PREFIX_LENGTH) + ".jpg"
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> displayName
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    // Shares artworkImageRequest's fixed decode size and memory-cache key with the phone UI, so a
    // row served to the car reuses a bitmap the app has already decoded.
    private suspend fun loadArtwork(context: Context, sourceUrl: String): ByteArray {
        val result = SingletonImageLoader.get(context)
            .execute(artworkImageRequest(context, sourceUrl)) as? SuccessResult
        val bitmap = (result?.image ?: throw FileNotFoundException("Artwork fetch failed"))
            .toArtworkBitmap()
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Artwork encode failed"
            }
            output.toByteArray()
        }
    }

    // Vector sources (SVG artwork) arrive as a drawable-backed Image, not a BitmapImage. Rasterize
    // those onto an opaque canvas, since the JPEG target has no alpha channel anyway.
    private fun Image.toArtworkBitmap(): Bitmap {
        if (this is BitmapImage) return bitmap
        val outputWidth = width.takeIf { it > 0 }?.coerceAtMost(ARTWORK_DECODE_SIZE) ?: ARTWORK_DECODE_SIZE
        val outputHeight = height.takeIf { it > 0 }?.coerceAtMost(ARTWORK_DECODE_SIZE) ?: ARTWORK_DECODE_SIZE
        return createBitmap(outputWidth, outputHeight).also { output ->
            Canvas(output).let { canvas ->
                canvas.drawColor(Color.BLACK)
                draw(canvas)
            }
        }
    }

    private companion object {
        const val MIME_TYPE = "image/jpeg"
    }
}
