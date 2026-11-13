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
    /** Which account the stored librespot credentials belong to. */
    private val accountMarkerFile = File(credentialsDir, "account.id")
    @Volatile private var session: Session? = null
    @Volatile private var sessionAccountId: String? = null
    private val connectionLock = Any()
    private val trackLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    val isConnected: Boolean get() = session != null

    /**
     * Connect to Spotify's Access Point. Uses stored credentials first, falling
     * back to the OAuth access token for first login. Returns the session.
     */
    suspend fun connect(accessToken: String, accountId: String = ""): Session = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
        val existing = session
        if (existing != null) {
            val current = sessionAccountId.orEmpty()
            if (accountId.isNotBlank() && current == accountId) return@withContext existing
            // The account changed (or was logged out): the old session must not
            // be reused, otherwise the previous account keeps playing.
            invalidate()
        }
        // Stored credentials belong to an account and may only be reused for it.
        val storedAccount = runCatching { accountMarkerFile.readText() }.getOrNull().orEmpty()
        val useStored = credentialsFile.exists() && storedAccount.isNotBlank() && storedAccount == accountId
        if (!useStored) {
            runCatching { credentialsFile.delete() }
        }
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
        if (useStored) {
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
        sessionAccountId = accountId
        if (accountId.isNotBlank()) runCatching { accountMarkerFile.writeText(accountId) }
        created
        }
    }

    /** Disconnect and clear the in-memory session. */
    fun disconnect() {
        synchronized(connectionLock) {
            val existing = session ?: return
            session = null
            sessionAccountId = null
            runCatching { existing.close() }
        }
    }

    /**
     * Fully invalidate the playback identity: close the session and delete the
     * stored credentials plus the account scoped audio cache. Used on logout
     * and when the Spotify account changes.
     */
    fun invalidate() {
        synchronized(connectionLock) {
            val existing = session
            session = null
            sessionAccountId = null
            runCatching { existing?.close() }
            runCatching { credentialsFile.delete() }
            runCatching { accountMarkerFile.delete() }
            runCatching { playbackCacheDir.listFiles()?.forEach { it.delete() } }
        }
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
            val trackLock = trackLocks.getOrPut(trackId) { Any() }
            synchronized(trackLock) {
                // Another resolver may have completed this exact track meanwhile.
                if (destination.isFile && destination.length() > 0L) return@synchronized destination
                val loaded = s.contentFeeder().load(
                    playableId,
                    AudioQualityPicker { files -> files.firstOrNull() },
                    false,
                    null,
                )
                val temporary = File.createTempFile("$trackId.", ".part", playbackCacheDir)
                try {
                    temporary.outputStream().use { output ->
                        loaded.`in`.stream().use { input -> input.copyTo(output) }
                    }
                    if (!temporary.renameTo(destination)) {
                        throw IOException("cannot publish Spotify audio cache")
                    }
                } finally {
                    temporary.delete()
                    trackLocks.remove(trackId, trackLock)
                }
                destination
            }
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
