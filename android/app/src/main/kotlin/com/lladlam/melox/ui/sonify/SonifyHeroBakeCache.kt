package com.lladlam.melox.ui.sonify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * 歌手页 hero 倒影「烘焙」位图缓存。烘焙是 CPU/GPU 异步操作，冷启动或回访若每次都重烘，
 * 会呈现「照片先出、倒影随后才浮现」。这里分两级缓存：
 *  - 内存级：同进程回访 → 命中即可落位，近首帧；
 *  - 磁盘级：跨进程/冷启动 → 异步解码 PNG 后换上（不阻塞首帧，先出锐利照片）。
 *
 * 形状对齐 md3Music depth_cover_cache：目录 `<filesDir>/hero_bake_cache/<md5(key)>.png` + LRU。
 * 键含头像 URL / 朝向比例 / 延展模式 / 烘焙几何——换设备或转屏自然失效重烘，不会错位。
 *
 * **内存上限按字节算而不是按条数**：单张手机几何的合成图 1024×1536×4 ≈ 6.3MB，
 * 按条数封 8 条最坏常驻 50MB，与同时存活的 Coil 位图叠加在低端机上有 OOM 风险。
 * 现在条数与字节双闸，取先到者（24MB ≈ 4 张手机图，同屏只需一张，命中率不受影响）。
 */
internal object HeroBakeCache {
    private const val DIR_NAME = "hero_bake_cache"
    private const val MAX_DISK = 24
    private const val MAX_MEM_COUNT = 4
    private const val MAX_MEM_BYTES = 24L * 1024 * 1024

    /** 条数 + 字节双闸的访问序 LRU，算账逻辑见 [BoundedLru]（有单测）。 */
    private val mem = BoundedLru<String, ImageBitmap>(
        maxCount = MAX_MEM_COUNT,
        maxBytes = MAX_MEM_BYTES,
        sizeOf = { it.width.toLong() * it.height * 4L }
    )

    @Volatile
    private var dir: File? = null

    private fun dir(context: Context): File =
        dir ?: synchronized(this) {
            dir ?: File(context.filesDir, DIR_NAME).also { it.mkdirs() }.also { dir = it }
        }

    private fun fileFor(context: Context, key: String): File =
        File(dir(context), md5(key) + ".png")

    /** 仅内存级命中（组合期/主线程可安全调用）。 */
    fun memoryGet(key: String): ImageBitmap? = synchronized(this) { mem.get(key) }

    /** 内存未命中则解码磁盘 PNG（**必须在 IO 线程调用**：文件 IO + 解码）。 */
    fun load(context: Context, key: String): ImageBitmap? {
        synchronized(this) { mem.get(key)?.let { return it } }
        val f = fileFor(context, key)
        if (!f.isFile) return null
        val decoded = runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
        if (decoded == null) {
            // 半截/损坏的 PNG（写入期被杀）会永久占用一个键：每次都解码失败又不被淘汰
            runCatching { f.delete() }
            return null
        }
        return decoded.also { synchronized(this) { mem.put(key, it) } }
    }

    /**
     * 写内存 + 落盘 PNG。内存插入同步完成（调用方立刻可见），**落盘段自行切到 IO 线程**，
     * 因此在任何线程/协程调用都不会阻塞调用方所在线程池。
     */
    suspend fun put(context: Context, key: String, bitmap: Bitmap) {
        synchronized(this) { mem.put(key, bitmap.asImageBitmap()) }
        try {
            withContext(Dispatchers.IO) { writeFile(context, key, bitmap) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 落盘失败不影响内存命中；残留的半截 PNG 会在下次 load 解码失败时被删
        }
    }

    fun clear(context: Context) {
        synchronized(this) { mem.clear() }
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    private fun writeFile(context: Context, key: String, bitmap: Bitmap) {
        // 先写临时文件再 rename：直接覆盖目标文件时被杀会留下半截 PNG
        val target = fileFor(context, key)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) tmp.delete()
        }
        trim(context)
    }

    private fun trim(context: Context) {
        val files = dir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".png") } ?: return
        if (files.size <= MAX_DISK) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_DISK).forEach { it.delete() }
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
