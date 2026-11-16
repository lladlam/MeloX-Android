package com.lladlam.melox.ui.sonify

/**
 * 条数 + 字节双闸的访问序 LRU。
 *
 * 存在理由：hero 烘焙合成图单张手机几何就有 1024×1536×4 ≈ 6.3MB，按条数封顶是最坏情况
 * 常驻 50MB 的隐性 OOM 源；但只按字节封顶会让"一张就超限"的极端几何反复把缓存冲空。
 * 两个闸取先到者，并且**字节计数只在 put/淘汰/clear 三处增减**，不在读取路径动。
 *
 * 抽成独立纯类是为了可测：计数漂移会让缓存无界增长或误删热图，这两种都很难在 UI 上发现。
 */
internal class BoundedLru<K, V>(
    private val maxCount: Int,
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long
) {
    // accessOrder = true：get 会把条目移到队尾，淘汰从队首（最久未使用）开始
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var bytes = 0L

    fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
        map[key]?.let { bytes -= sizeOf(it) }
        map[key] = value
        bytes += sizeOf(value)
        trim()
    }

    fun clear() {
        map.clear()
        bytes = 0L
    }

    fun keys(): List<K> = map.keys.toList()

    /** 可见给测试。 */
    fun approxBytes(): Long = bytes

    private fun trim() {
        val iterator = map.entries.iterator()
        // 「至少留一张」：极端几何的单张就可能超过字节闸门，此时若把它也删掉，
        // 该键会永久 miss（每次重烘都白烧一遍 3s 级烘焙），比多占一张的内存更糟。
        while ((map.size > maxCount || bytes > maxBytes) && map.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            bytes -= sizeOf(eldest.value)
            iterator.remove()
        }
    }
}
