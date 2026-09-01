package io.music_assistant.client.auto

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.items.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAutoArtworkUriTest {
    @Before
    fun initializeArtworkBridge() {
        AndroidAutoArtwork.initialize(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `media description routes remote artwork through the resolver`() {
        val remoteUrl = "https://tailnet.invalid/imageproxy/opaque-id?size=512"
        val localUri = Uri.parse("content://io.music_assistant.client.autoartwork/art/local-id")

        val description = trackWithArtwork(remoteUrl).toMediaDescription(
            defaultIconUri = DEFAULT_ICON,
            artworkUri = { source ->
                assertEquals(remoteUrl, source)
                localUri
            },
        )

        assertEquals(localUri, description.iconUri)
    }

    @Test
    fun `media description carries only a local uri and no bitmap`() {
        val remoteUrl = "https://tailnet.invalid/imageproxy/default-path"
        val iconUri = requireNotNull(trackWithArtwork(remoteUrl).toMediaDescription(DEFAULT_ICON).iconUri)

        assertEquals("content", iconUri.scheme)
        assertEquals(AndroidAutoArtwork.authority, iconUri.authority)
        assertEquals(
            "${ApplicationProvider.getApplicationContext<Context>().packageName}.autoartwork",
            iconUri.authority,
        )
        assertEquals(remoteUrl, AndroidAutoArtwork.sourceFor(iconUri))
        assertFalse(iconUri.toString().contains("tailnet.invalid"))
        assertNull(trackWithArtwork(remoteUrl).toMediaDescription(DEFAULT_ICON).iconBitmap)
    }

    @Test
    fun `media description keeps the default icon when artwork is absent`() {
        val description = trackWithArtwork(null).toMediaDescription(
            defaultIconUri = DEFAULT_ICON,
            artworkUri = { error("resolver must not run without artwork") },
        )

        assertEquals(DEFAULT_ICON, description.iconUri)
    }

    // The host caches images by URI. A nondeterministic token would mint a fresh URI on every
    // browse response and force a refetch each time, so this is a behavioural guarantee, not an
    // implementation detail.
    @Test
    fun `the same artwork url always maps to the same uri`() {
        val source = "https://tailnet.invalid/imageproxy/stable?size=512"
        val codec = AutoArtworkTokenCodec(ByteArray(32) { it.toByte() })

        assertEquals(codec.uriFor(AUTHORITY, source), codec.uriFor(AUTHORITY, source))
        assertEquals(AndroidAutoArtwork.uriFor(source), AndroidAutoArtwork.uriFor(source))
    }

    @Test
    fun `distinct artwork urls map to distinct uris`() {
        val codec = AutoArtworkTokenCodec(ByteArray(32) { it.toByte() })

        assertFalse(
            codec.uriFor(AUTHORITY, "https://host.invalid/a") ==
                codec.uriFor(AUTHORITY, "https://host.invalid/b"),
        )
    }

    @Test
    fun `codec emits opaque uris that survive process recreation and reject tampering`() {
        val key = ByteArray(32) { it.toByte() }
        val codec = AutoArtworkTokenCodec(key)
        val restoredCodec = AutoArtworkTokenCodec(key)
        val source = "https://tailnet.invalid/imageproxy/first?token=secret"
        val uri = requireNotNull(codec.uriFor(AUTHORITY, source))

        assertFalse(uri.toString().contains("tailnet.invalid"))
        assertFalse(uri.toString().contains("secret"))
        assertEquals(source, codec.sourceFor(AUTHORITY, uri))
        assertEquals(source, restoredCodec.sourceFor(AUTHORITY, uri))
        assertNull(AutoArtworkTokenCodec(ByteArray(32)).sourceFor(AUTHORITY, uri))

        val token = requireNotNull(uri.lastPathSegment)
        val index = token.length / 2
        val replacement = if (token[index] == 'A') "B" else "A"
        val tampered = uri.buildUpon().path("/art/${token.replaceRange(index, index + 1, replacement)}").build()
        assertNull(codec.sourceFor(AUTHORITY, tampered))
    }

    @Test
    fun `codec rejects local credential-bearing and oversized sources`() {
        val codec = AutoArtworkTokenCodec(ByteArray(32))

        assertNull(codec.uriFor(AUTHORITY, "file:///data/user/0/private.jpg"))
        assertNull(codec.uriFor(AUTHORITY, "content://private.provider/image"))
        assertNull(codec.uriFor(AUTHORITY, "https://user:password@example.com/image.jpg"))
        assertNull(codec.uriFor(AUTHORITY, "https://example.com/${"x".repeat(4_097)}"))
        assertNotNull(codec.uriFor(AUTHORITY, "http://192.168.1.2:8095/imageproxy/id"))
        assertNotNull(codec.uriFor(AUTHORITY, "mawebrtc://proxy/imageproxy/id?size=512&checksum="))
    }

    @Test
    fun `manifest keeps the artwork provider private and grantable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider(
            AndroidAutoArtwork.authority,
            PackageManager.GET_META_DATA,
        )

        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun `provider exposes only opaque read metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = requireNotNull(AndroidAutoArtwork.uriFor("https://tailnet.invalid/imageproxy/private"))

        assertEquals("image/jpeg", context.contentResolver.getType(uri))
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            assertTrue(name.endsWith(".jpg"))
            assertFalse(name.contains("tailnet.invalid"))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
    }

    @Test
    fun `provider refuses an unknown uri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val unknown = Uri.parse("content://${AndroidAutoArtwork.authority}/art/not-a-token")

        assertNull(context.contentResolver.getType(unknown))
        assertNull(context.contentResolver.query(unknown, null, null, null, null))
    }

    @Test
    fun `artwork grant accepts the trusted app uid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(AndroidAutoArtwork.grantReadAccess(context, context.packageName, Process.myUid()))
        AndroidAutoArtwork.revokeReadAccess(context, context.packageName)
    }

    @Test
    fun `artwork grant rejects a package that does not own the caller uid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(
            AndroidAutoArtwork.grantReadAccess(context, context.packageName, Process.myUid() + 1),
        )
    }

    private fun trackWithArtwork(url: String?): Track = Track(
        itemId = "track-1",
        provider = "library",
        name = "Track",
        providerMappings = null,
        metadata = null,
        favorite = false,
        uri = "library://track/1",
        images = url?.let {
            mapOf(
                ImageType.THUMB to ImageInfo(
                    type = ImageType.THUMB,
                    path = "cover.jpg",
                    isRemotelyAccessible = false,
                    provider = "library",
                    url = it,
                ),
            )
        }.orEmpty(),
        duration = 180.0,
        isPlayable = true,
        artists = emptyList(),
        album = null,
        discNumber = 1,
        trackNumber = 1,
        position = null,
        version = null,
    )

    private companion object {
        const val AUTHORITY = "io.music_assistant.client.autoartwork"
        val DEFAULT_ICON: Uri = Uri.parse("android.resource://io.music_assistant.client/drawable/default")
    }
}
