package com.lladlam.melox.core.autosync

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.network.MeloXGitHubRouting
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cloud distribution for AutoSync model packs.
 *
 * Everything goes through the app's existing update infrastructure
 * ([MeloXGitHubRouting]) rather than a private downloader: the same mirror
 * candidates, the same per-route OkHttp clients, the same host allow-list that
 * the app update check already uses. Only files whose size and SHA-256 match
 * the published index are ever installed, and installation is an atomic
 * directory swap, so an interrupted download can never leave a half model.
 *
 * Nothing is fetched until the user explicitly asks for a language.
 */
internal class AutoSyncModelRepository(
    private val context: Context,
    private val routing: MeloXGitHubRouting,
) {

    data class FileSpec(
        val name: String,
        val role: String,
        val url: String,
        val bytes: Long,
        val sha256: String,
        val note: String = "",
    ) {
        /** `audit-only` artefacts are provenance evidence, not runtime inputs. */
        val required: Boolean get() = role != "audit-only"
    }

    data class Pack(
        val language: String,
        val engine: String,
        val packVersion: String,
        val sourceModel: String,
        val license: String,
        val sampleRate: Int,
        val frameHopSeconds: Double,
        val inputNames: List<String>,
        val outputNames: List<String>,
        val blankId: Int,
        val wordDelimiterId: Int?,
        val tokensFile: String?,
        val tokensCount: Int?,
        val normalization: String,
        val accuracyStatus: String,
        val postprocess: JSONObject?,
        val files: List<FileSpec>,
        /** Published "precision first" model for this language, when one exists. */
        val optionalModel: FileSpec? = null,
    ) {
        val requiredFiles: List<FileSpec> get() = files.filter { it.required }
        val totalBytes: Long get() = requiredFiles.sumOf { it.bytes }
    }

    data class Index(
        val packVersion: String,
        val releaseTag: String,
        val releaseUrl: String,
        val minBackendProtocol: Int,
        val accuracyNotice: String,
        val packs: Map<String, Pack>,
        /**
         * Optional "precision first" models published next to the packs, keyed by
         * the language their file name starts with (`en-…`). They are never
         * installed implicitly: the user picks one, and the base model is then
         * replaced so only one copy is kept on disk.
         */
        val optionalModels: Map<String, FileSpec>,
    )

    /**
     * Fetches and parses the published model index, trying every mirror
     * candidate in the order the update check would use them.
     */
    suspend fun fetchIndex(): Result<Index> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = fetchThroughCandidates(INDEX_URL, maxBytes = MAX_INDEX_BYTES)
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            check(actual.equals(INDEX_SHA256, ignoreCase = true)) {
                "model index signature mismatch: $actual"
            }
            val index = parseIndex(bytes.decodeToString())
            check(index.releaseTag == RELEASE_TAG) { "unexpected model release ${index.releaseTag}" }
            migrateLegacyPacks(index)
            index
        }.onFailure { Log.w(TAG, "model index unavailable", it) }
    }

    /**
     * Downloads and installs one language pack. Cancelling halfway leaves the
     * previously installed pack (if any) untouched.
     *
     * @param onProgress fraction in `0f..1f` over the pack's total required bytes.
     */
    suspend fun install(
        pack: Pack,
        knownIndex: Index,
        precision: FileSpec? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        installLock(pack.language).withLock {
            runCatching {
                check(knownIndex.minBackendProtocol <= BACKEND_PROTOCOL) {
                    "model pack requires backend protocol ${knownIndex.minBackendProtocol}"
                }
                check(knownIndex.releaseTag == RELEASE_TAG) { "refusing to install a non-pinned model release" }
                val modelRoot = root(context)
                modelRoot.mkdirs()
                // Partial downloads are never resumable: remove their staging
                // bytes before resolving only URLs from the pinned index.
                val staging = File(modelRoot, "${pack.language}.staging")
                staging.deleteRecursively()
                check(staging.mkdirs()) { "cannot create staging directory" }
                try {
                val total = (pack.totalBytes + (precision?.bytes ?: 0L)).coerceAtLeast(1L)
                var done = 0L
                val tokens = LinkedHashMap<String, String>()
                val candidates = routing.candidates()
                for (spec in pack.requiredFiles) {
                    currentCoroutineContext().ensureActive()
                    val target = File(staging, spec.name)
                    val reusable = existingVerifiedFile(pack.language, spec)
                    if (reusable != null) reusable.copyTo(target, overwrite = true)
                    else downloadVerified(spec, target, candidates) { written ->
                        onProgress?.invoke(((done + written).coerceAtMost(total)).toFloat() / total)
                    }
                    done += spec.bytes
                    onProgress?.invoke(done.toFloat().coerceAtMost(total.toFloat()) / total)
                    if (spec.name == pack.tokensFile) readTokens(target, tokens)
                }
                precision?.let { spec ->
                    currentCoroutineContext().ensureActive()
                    val target = File(staging, spec.name)
                    val reusable = existingVerifiedFile(pack.language, spec)
                    if (reusable != null) reusable.copyTo(target, overwrite = true)
                    else downloadVerified(spec, target, candidates) { written ->
                        onProgress?.invoke(((done + written).coerceAtMost(total)).toFloat() / total)
                    }
                    done += spec.bytes
                    onProgress?.invoke(done.toFloat().coerceAtMost(total.toFloat()) / total)
                    // Only one model variant is kept on disk: the larger one replaces
                    // the quantised file instead of living next to it.
                    pack.files.filter { it.role == "model" && it.name != spec.name }.forEach { stale ->
                        File(staging, stale.name).delete()
                    }
                }
                File(staging, "manifest.json").writeText(
                    localManifest(pack, knownIndex.releaseTag, tokens, precision).toString(),
                )
                publish(pack.language, staging)
                onProgress?.invoke(1f)
                Unit
            } catch (error: Throwable) {
                staging.deleteRecursively()
                throw error
            }
            }.onFailure { Log.w(TAG, "install failed for ${pack.language}", it) }
        }
    }


    /** Removes an installed pack; installation and deletion share the language lock. */
    suspend fun delete(language: String) = withContext(Dispatchers.IO) {
        installLock(language).withLock {
            File(root(context), language).deleteRecursively()
            AutoSyncTimelineStore.clearLanguage(context, language)
        }
    }

    // ------------------------------------------------------------------ fetch

    private suspend fun fetchThroughCandidates(url: String, maxBytes: Long): ByteArray {
        var lastError: Throwable? = null
        for (source in routing.candidates()) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(routing.routedUrl(source, url))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .build()
                routing.client(source).newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body
                    val declared = body.contentLength()
                    check(declared < 0 || declared <= maxBytes) { "index too large" }
                    body.byteStream().readBounded(maxBytes)
                }
            }
            result.onSuccess { return it }
            lastError = result.exceptionOrNull()
            Log.i(TAG, "route $source failed for index: ${lastError?.message}")
        }
        throw lastError ?: IllegalStateException("no GitHub route available")
    }

    /**
     * Streams one file through the mirror candidates, then verifies byte length
     * and SHA-256 before the caller is allowed to see it.
     */
    private suspend fun downloadVerified(
        spec: FileSpec,
        target: File,
        candidates: List<com.lladlam.melox.core.network.MeloXGitHubSource>,
        onBytes: (Long) -> Unit,
    ) {
        var lastError: Throwable? = null
        for (source in candidates) {
            if (target.isFile) target.delete()
            val result = runCatching {
                val request = Request.Builder()
                    .url(routing.routedUrl(source, spec.url))
                    .header("User-Agent", USER_AGENT)
                    .build()
                routing.client(source).newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L
                    response.body.byteStream().use { input ->
                        target.outputStream().buffered(1 shl 16).use { output ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                written += read
                                onBytes(written)
                            }
                        }
                    }
                    check(written == spec.bytes) { "size mismatch: $written != ${spec.bytes}" }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    check(actual.equals(spec.sha256, ignoreCase = true)) { "sha256 mismatch" }
                }
            }
            result.onSuccess { return }
            lastError = result.exceptionOrNull()
            if (lastError is CancellationException) throw lastError
            Log.i(TAG, "route $source failed for ${spec.name}: ${lastError?.message}")
        }
        throw lastError ?: IllegalStateException("no GitHub route available for ${spec.name}")
    }

    private suspend fun existingVerifiedFile(language: String, spec: FileSpec): File? {
        val roots = listOfNotNull(context.getExternalFilesDir(null)?.let { File(it, "autosync") }, root(context))
        for (base in roots) {
            val file = File(File(base, language), spec.name)
            if (!file.isFile || file.length() != spec.bytes) continue
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual.equals(spec.sha256, ignoreCase = true)) return file
        }
        return null
    }

    /** Promote byte-identical beta.1 packs to beta.2 metadata; discard partial staging. */
    private suspend fun migrateLegacyPacks(index: Index) {
        val base = root(context)
        // Never delete an install that another coroutine is currently writing;
        // every install removes and recreates its own language staging folder.
        base.listFiles()?.filter { it.name.endsWith(".old") }
            ?.forEach { it.deleteRecursively() }
        for (pack in index.packs.values) {
            currentCoroutineContext().ensureActive()
            if (!File(File(base, pack.language), "manifest.json").isFile) continue
            installLock(pack.language).withLock {
                // A pack whose bytes no longer match the published index (for example
                // the beta.2 Japanese model) is left installed and usable: it is only
                // replaced when the user explicitly updates, so a flaky network can
                // never take a working model away.
                val verified = pack.requiredFiles.map { spec -> spec to existingVerifiedFile(pack.language, spec) }
                if (verified.any { it.second == null }) return@withLock
                val staging = File(base, "${pack.language}.staging")
                staging.deleteRecursively()
                check(staging.mkdirs()) { "cannot create migration staging directory" }
                try {
                    val tokens = LinkedHashMap<String, String>()
                    verified.forEach { (spec, source) ->
                        val target = File(staging, spec.name)
                        source!!.copyTo(target, overwrite = true)
                        if (spec.name == pack.tokensFile) readTokens(target, tokens)
                    }
                    File(staging, "manifest.json").writeText(localManifest(pack, index.releaseTag, tokens).toString())
                    publish(pack.language, staging)
                    Log.i(TAG, "migrated ${pack.language} model metadata to ${index.releaseTag}; model bytes reused")
                } catch (error: Throwable) {
                    staging.deleteRecursively()
                    throw error
                }
            }
        }
    }

    private fun installLock(language: String): Mutex =
        installLocks.getOrPut(language) { Mutex() }

    private fun InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            check(total <= maxBytes) { "response too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    // ---------------------------------------------------------------- install

    /** Atomic swap: the new pack replaces the old one in a single rename. */
    private fun publish(language: String, staging: File) {
        val root = root(context)
        val live = File(root, language)
        val previous = File(root, "$language.old")
        previous.deleteRecursively()
        if (live.exists()) check(live.renameTo(previous)) { "cannot park previous pack" }
        if (!staging.renameTo(live)) {
            previous.renameTo(live)
            error("cannot publish pack")
        }
        previous.deleteRecursively()
    }

    /**
     * Rewrites the published pack into the small local manifest the on-device
     * engine already understands, embedding the token list so inference needs
     * no extra file lookup.
     */
    private fun localManifest(
        pack: Pack,
        releaseTag: String,
        tokens: Map<String, String>,
        precision: FileSpec? = null,
    ): JSONObject {
        val tokenArray = JSONArray()
        tokens.entries.sortedBy { it.value.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { tokenArray.put(it.key) }
        val inputs = JSONArray()
        pack.inputNames.forEach { name ->
            inputs.put(JSONObject().put("name", name))
        }
        val outputs = JSONArray()
        pack.outputNames.forEach { name ->
            outputs.put(JSONObject().put("name", name))
        }
        return JSONObject()
            .put("schema", "melox.autosync.onnx.1")
            .put("language", pack.language)
            .put("engine", pack.engine)
            .put("pack_version", pack.packVersion)
            .put("release_tag", releaseTag)
            .put("source_model", pack.sourceModel)
            .put("license", pack.license)
            .put("sample_rate", pack.sampleRate)
            .put("frame_hop_seconds", pack.frameHopSeconds)
            .put("inputs", inputs)
            .put("outputs", outputs)
            .put("blank_id", pack.blankId)
            .apply {
                pack.wordDelimiterId?.let { put("word_delimiter_id", it) }
                pack.normalization.takeIf { it.isNotBlank() }?.let { put("normalization", it) }
                pack.postprocess?.let { put("postprocess", it) }
                put("accuracy_status", pack.accuracyStatus)
                val files = JSONObject()
                val base = pack.files.firstOrNull { it.role == "model" }
                base?.let { model ->
                    files.put("int8", JSONObject().put("name", model.name).put("bytes", model.bytes)
                        .put("sha256", model.sha256))
                }
                precision?.let { model ->
                    files.put("precision", JSONObject().put("name", model.name).put("bytes", model.bytes)
                        .put("sha256", model.sha256))
                }
                files.put("variant", if (precision != null) "precision" else "int8")
                // Required runtime files actually on disk, for cheap update checks.
                val required = JSONArray()
                val active = precision ?: base
                active?.let { model ->
                    required.put(JSONObject().put("name", model.name).put("bytes", model.bytes)
                        .put("sha256", model.sha256))
                }
                pack.files.filter { it.required && it.role != "model" }.forEach { spec ->
                    required.put(JSONObject().put("name", spec.name).put("bytes", spec.bytes)
                        .put("sha256", spec.sha256))
                }
                files.put("required", required)
                put("g2p_available", pack.language != "ja")
                put("files", files)
                put("tokens", tokenArray)
            }
    }

    /** `en-vocab.json` is `token -> id`; `zh-tokens.json` is `id -> token`. */
    private fun readTokens(file: File, into: MutableMap<String, String>) {
        if (!file.isFile) return
        runCatching {
            val text = file.readText().trim()
            when {
                text.startsWith("{") -> {
                    val root = JSONObject(text)
                    root.keys().forEach { key -> into[key] = root.optString(key) }
                }
                text.startsWith("[") -> {
                    val array = JSONArray(text)
                    for (index in 0 until array.length()) {
                        val value = array.optString(index)
                        if (value.isNotEmpty()) into[value] = index.toString()
                    }
                }
            }
        }.onFailure { Log.w(TAG, "token file unreadable: ${file.name}", it) }
    }

    private fun root(context: Context): File = File(context.filesDir, "autosync")

    companion object {
        private const val TAG = "MeloXAutoSync"
        private const val USER_AGENT = "MeloX-Android"
        private const val BACKEND_PROTOCOL = 1
        /** Serializes install/migration work for one language across instances. */
        private val installLocks = ConcurrentHashMap<String, Mutex>()

        /**
         * Pinned release. The index itself is verified by SHA-256 against
         * [INDEX_SHA256] so a moved tag cannot silently swap the payload.
         */
        const val RELEASE_TAG = "android-models-v0.1.0-beta.4"
        const val RELEASE_PREFIX =
            "https://github.com/lladlam/MeloX-AutoSync/releases/download/android-models-v0.1.0-beta.4/"
        const val INDEX_URL = RELEASE_PREFIX + "android-models.json"

        /**
         * Pinned hash of the published index (`android-models.json`,
         * 14 093 bytes). A moved or rewritten tag therefore cannot silently
         * change which weights this build is willing to install.
         */
        const val INDEX_SHA256 = "4b7b54a6f436b4f74ac6028f3876477a2d7dffe13fba557f9649577354a0f0ed"
        private const val MAX_INDEX_BYTES = 1L shl 20

        /**
         * Only files under the pinned release are installable. The index is
         * data, and data must never be able to point the downloader at an
         * arbitrary host or at a path inside the app.
         */
        private fun requireTrusted(spec: FileSpec, language: String) {
            require(spec.url.startsWith(RELEASE_PREFIX)) {
                "pack $language file ${spec.name} points outside the pinned release"
            }
            require(!spec.name.contains('/') && !spec.name.contains("..")) {
                "pack $language file name ${spec.name} is not a plain file name"
            }
            require(spec.bytes > 0L && spec.sha256.length == 64) {
                "pack $language file ${spec.name} has no size/checksum"
            }
        }

    /**
     * Cheap update check: compares the digests recorded at install time with the
     * published index. No file is hashed, so it is safe to call from the UI.
     */
        fun matchesIndex(manifest: AutoSyncManifest, pack: Pack): Boolean {
        val expected = buildList {
            val active = if (manifest.modelVariant == "precision") pack.optionalModel else null
            val base = pack.files.firstOrNull { it.role == "model" }
            (active ?: base)?.let { add(AutoSyncFileDigest(it.name, it.bytes, it.sha256.lowercase())) }
            pack.files.filter { it.required && it.role != "model" }
                .forEach { add(AutoSyncFileDigest(it.name, it.bytes, it.sha256.lowercase())) }
        }
        if (manifest.requiredFiles.isEmpty()) return false
        return manifest.requiredFiles.map { it.copy(sha256 = it.sha256.lowercase()) }.toSet() == expected.toSet()
    }

        fun parseIndex(text: String): Index {
            val root = JSONObject(text)
            val languages = root.optJSONObject("languages") ?: JSONObject()
            val packs = LinkedHashMap<String, Pack>()
            val optionalModels = LinkedHashMap<String, FileSpec>()
            root.optJSONArray("extra_assets")?.let { array ->
                for (index in 0 until array.length()) {
                    val file = array.optJSONObject(index) ?: continue
                    val spec = FileSpec(
                        name = file.optString("name"),
                        role = file.optString("role", "optional-model"),
                        url = file.optString("download_url"),
                        bytes = file.optLong("bytes"),
                        sha256 = file.optString("sha256"),
                        note = file.optString("note"),
                    )
                    if (spec.name.isBlank() || spec.url.isBlank()) continue
                    val language = languages.keys().asSequence()
                        .firstOrNull { spec.name.startsWith("$it-") } ?: continue
                    runCatching { requireTrusted(spec, language) }.getOrNull()?.let {
                        optionalModels[language] = spec
                    }
                }
            }

            languages.keys().forEach { language ->
                val pack = languages.optJSONObject(language) ?: return@forEach
                val inputNames = ArrayList<String>()
                pack.optJSONArray("inputs")?.let { array ->
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                            ?.let(inputNames::add)
                    }
                }
                if (inputNames.isEmpty()) {
                    pack.optJSONObject("input")?.optString("name")?.takeIf { it.isNotBlank() }
                        ?.let(inputNames::add)
                }
                pack.optJSONArray("aux_inputs")?.let { array ->
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                            ?.let(inputNames::add)
                    }
                }
                val outputNames = ArrayList<String>()
                pack.optJSONArray("outputs")?.let { array ->
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                            ?.let(outputNames::add)
                    }
                }
                val text = pack.optJSONObject("text")
                val files = ArrayList<FileSpec>()
                pack.optJSONArray("files")?.let { array ->
                    for (index in 0 until array.length()) {
                        val file = array.optJSONObject(index) ?: continue
                        files.add(
                            FileSpec(
                                name = file.optString("name"),
                                role = file.optString("role", "asset"),
                                url = file.optString("download_url"),
                                bytes = file.optLong("bytes"),
                                sha256 = file.optString("sha256"),
                                note = file.optString("note"),
                            ),
                        )
                    }
                }
                files.forEach { requireTrusted(it, language) }
                packs[language] = Pack(
                    language = language,
                    engine = pack.optString("engine"),
                    packVersion = pack.optString("pack_version"),
                    sourceModel = pack.optString("source_model"),
                    license = pack.optString("license"),
                    sampleRate = pack.optInt("sample_rate", 16_000),
                    frameHopSeconds = packFrameHop(pack),
                    inputNames = inputNames,
                    outputNames = outputNames,
                    blankId = pack.optInt("blank_id", 0),
                    wordDelimiterId = text?.optInt("word_delimiter_id", -1)?.takeIf { it >= 0 },
                    tokensFile = text?.optString("tokens_file")?.takeIf { it.isNotBlank() },
                    tokensCount = text?.optInt("tokens_count", -1)?.takeIf { it > 0 },
                    normalization = text?.optString("normalization").orEmpty(),
                    accuracyStatus = pack.optJSONObject("accuracy")?.optString("status").orEmpty(),
                    postprocess = pack.optJSONObject("postprocess"),
                    files = files.filter { it.name.isNotBlank() && it.url.isNotBlank() },
                    optionalModel = optionalModels[language],
                )
            }
            return Index(
                packVersion = root.optString("pack_version"),
                releaseTag = root.optString("release_tag", RELEASE_TAG),
                releaseUrl = root.optString("release_url"),
                minBackendProtocol = root.optInt("min_backend_protocol", 1),
                accuracyNotice = root.optString("accuracy_notice"),
                packs = packs,
                optionalModels = optionalModels,
            )
        }

        /** Chinese packs publish no frame hop; their 20 ms decoded rate is implied. */
        private fun packFrameHop(pack: JSONObject): Double {
            pack.optDouble("frame_hop_seconds", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?.let { return it }
            pack.optDouble("timestamp_frame_seconds", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?.let { return it }
            return 0.02
        }
    }
}
