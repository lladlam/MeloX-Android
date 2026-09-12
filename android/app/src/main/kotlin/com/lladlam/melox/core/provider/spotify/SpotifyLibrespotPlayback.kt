package com.lladlam.melox.core.provider.spotify

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.protobuf.ByteString
import com.spotify.Authentication
import com.spotify.connectstate.Connect
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.TrackId

/**
 * Wraps librespot-java for MeloX: Spotify playback over the Access Point.
 *
 * librespot-java is deprecated upstream but still fully functional for the
 * playback path MeloX needs. The session is created lazily on first connect
 * and reused for the lifetime of the process. Credentials are persisted so
 * a reconnect after process death does not require re-OAuth.
 *
 */
class SpotifyLibrespotPlayback(
    private val context: Context,
    private val clientId: String,
) {
    private val credentialsDir: File = File(context.filesDir, "librespot").apply { mkdirs() }
    private val credentialsFile = File(credentialsDir, "credentials.json")
    private val playbackCacheDir = File(credentialsDir, "tracks").apply { mkdirs() }
    @Volatile private var session: Session? = null

    val isConnected: Boolean get() = session != null

    /**
     * Connect to Spotify's Access Point. Uses stored credentials first, falling
     * back to the OAuth access token for first login. Returns the session.
     */
    suspend fun connect(accessToken: String): Session = withContext(Dispatchers.IO) {
        val existing = session
        if (existing != null) return@withContext existing
        val config = Session.Configuration.Builder()
            .setCacheEnabled(true)
            .setCacheDir(credentialsDir)
            .setStoreCredentials(true)
            .setStoredCredentialsFile(credentialsFile)
            .build()
        val builder = Session.Builder(config)
            .setDeviceType(Connect.DeviceType.SMARTPHONE)
            .setDeviceName("MeloX")
            .setDeviceId(deviceId)
        if (credentialsFile.exists()) {
            builder.stored(credentialsFile)
        } else if (accessToken.isNotBlank()) {
            builder.credentials(
                Authentication.LoginCredentials.newBuilder()
                    .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                    .setAuthData(ByteString.copyFromUtf8(accessToken))
                    .build(),
            )
        } else {
            throw IOException("Spotify session 未登录")
        }
        val created = builder.create()
        session = created
        created
    }

    /** Disconnect and clear the in-memory session. */
    fun disconnect() {
        val existing = session ?: return
        session = null
        runCatching { existing.close() }
    }

    /**
     * Returns the decoded audio stream for a Spotify track. The caller is
     * responsible for consuming and closing the returned stream.
     */
    suspend fun resolveToFile(trackId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val s = session ?: throw IOException("Spotify session 未连接")
            val playableId = TrackId.fromUri("spotify:track:$trackId")
            val destination = File(playbackCacheDir, "$trackId.audio")
            if (destination.isFile && destination.length() > 0L) return@runCatching destination
            val loaded = s.contentFeeder().load(
                playableId,
                AudioQualityPicker { files -> files.firstOrNull() },
                false,
                null,
            )
            destination.outputStream().use { output ->
                loaded.`in`.stream().use { input -> input.copyTo(output) }
            }
            destination
        }
    }

    /**
     * Download a Spotify track to [destination]. Returns bytes written.
     */
    suspend fun downloadTrack(
        trackId: String,
        destination: File,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val source = resolveToFile(trackId).getOrThrow()
            destination.parentFile?.mkdirs()
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            destination.length()
        }
    }

    private val deviceId: String by lazy {
        MessageDigest.getInstance("SHA-1")
            .digest("melox:$clientId".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
