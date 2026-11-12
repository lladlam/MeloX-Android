package com.lladlam.melox.core.provider.bilibili

import android.content.Context
import com.lladlam.melox.core.audio.MeloXNetworkQualityPolicy
import java.net.URI

/**
 * 实验性「定向流量」支持（Bilibili）。
 *
 * 国内运营商的定向流量包通常只对 B 站自有域名（`*.bilivideo.com` 等）免流，
 * 第三方 PCDN / 裸 IP 线路既不免流，也可能被限速或拒绝第三方客户端。
 *
 * 这里仅在**移动数据**下、按用户开关，优先挑选 B 站官方 CDN 线路：
 * 1. 同一音质的多条候选地址里优先选官方 CDN 且不是 PCDN 的地址；
 * 2. 全部都是 PCDN 时，参考 BiliPai 的做法：路径可跨 host 复用时
 *    （`/upgcxcode/`、`/v1/resource`）把 host 换成官方镜像兜底；
 * 3. 其余情况保持原样，不做任何改写。
 *
 * ⚠️ **重要**：是否免流由运营商计费策略决定，Android 没有通用接口可以查询或保证。
 * 本策略只能优化「走哪条线路」，无法验证计费结果；开关默认关闭。
 *
 * 参考实现：BiliPai `pickNonPcdnUrl` / `rewriteBilivideoHost`（仅保留媒体地址处理部分，
 * 不改动 WBI 请求的平台身份）。
 */
object BilibiliDirectedTrafficPolicy {
    private const val PREFS_NAME = "melox_bilibili_directed_traffic"
    private const val KEY_ENABLED = "enabled"

    /** 兜底官方线路（与 BiliPai 的 `PREMIUM_UPGRADE_FALLBACK_CDN_HOST` 保持一致）。 */
    const val OfficialCdnFallbackHost = "upos-sz-mirrorali.bilivideo.com"

    // ---------------------------------------------------------------- 开关状态

    /** 用户是否打开了「B 站定向流量（实验性）」。 */
    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 开关打开且当前处于移动数据网络时才生效。 */
    fun isActive(context: Context): Boolean =
        isEnabled(context) && MeloXNetworkQualityPolicy.isCellular(context, allowMeteredFallback = false) == true

    // ---------------------------------------------------------------- 选线逻辑

    /**
     * 从候选地址中挑一条最适合当前场景的播放地址。
     *
     * @param candidates 候选地址，按服务端下发顺序排列（`base_url` 在前，其后是各 `backup_url`）。
     * @param directedTrafficActive 是否处于「移动数据 + 已开启定向流量」状态。
     * @return 选中的地址；候选为空时返回 `null`。
     */
    fun selectMediaUrl(candidates: List<String>, directedTrafficActive: Boolean): String? {
        val valid = candidates.asSequence()
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .distinct()
            .toList()
        if (valid.isEmpty()) return null
        if (!directedTrafficActive) return valid.first()

        // ① 官方 CDN 且不是 PCDN。
        valid.firstOrNull { url ->
            val host = hostOf(url)
            isOfficialCdnHost(host) && !isPcdnHost(host)
        }?.let { return it }

        // ② 其它非 PCDN 线路（可能仍是第三方 CDN，但至少不是已知 PCDN）。
        valid.firstOrNull { url -> !isPcdnHost(hostOf(url)) }?.let { return it }

        // ③ 全部是 PCDN：路径可跨 host 复用时换成官方镜像兜底。
        return valid.firstNotNullOfOrNull(::rewriteToOfficialHost) ?: valid.first()
    }

    /**
     * 该音质候选里是否存在官方 CDN（非 PCDN）地址。
     *
     * 用于在多个同音质候选之间优先选择官方线路。
     */
    fun hasOfficialCandidate(candidates: List<String>): Boolean = candidates.any { url ->
        val host = hostOf(url)
        isOfficialCdnHost(host) && !isPcdnHost(host)
    }

    // ---------------------------------------------------------------- 主机判定

    /** B 站自有域名（官方 CDN）。 */
    fun isOfficialCdnHost(host: String): Boolean {
        val normalized = host.lowercase().trim()
        if (normalized.isBlank()) return false
        return OFFICIAL_CDN_SUFFIXES.any { normalized == it || normalized.endsWith(".$it") }
    }

    /**
     * 已知 PCDN / 第三方 / 裸 IP 主机。
     *
     * 口径与 BiliPai `isRiskyCdnHost` 保持一致，避免把 PCDN 地址当成官方线路捡回来。
     */
    fun isPcdnHost(host: String): Boolean {
        val normalized = host.lowercase().trim()
        if (normalized.isBlank()) return true
        if (normalized.contains("mcdn") || normalized.contains("pcdn") || normalized.contains("gotcha")) return true
        if (RISKY_CDN_SUFFIXES.any { normalized == it || normalized.endsWith(".$it") }) return true
        return IPV4_REGEX.matches(normalized)
    }

    /**
     * 把地址的 host 换成官方镜像。
     *
     * 能否换 host 取决于路径：`upgcxcode` 与 `v1/resource` 的路径和签名在不同 host
     * 之间通用；其他路径是 host 相关的，必须原样保留。
     *
     * @return 换写后的地址；路径不允许换 host 时返回 `null`。
     */
    fun rewriteToOfficialHost(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val originalHost = uri.host ?: return null
        if (originalHost.equals(OfficialCdnFallbackHost, ignoreCase = true)) return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawUserInfo != null || uri.port != -1) return null

        val path = uri.rawPath.orEmpty()
        if (!path.startsWith("/upgcxcode/") && !path.startsWith("/v1/resource/")) return null

        val scheme = uri.scheme
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "$scheme://$OfficialCdnFallbackHost$path$query$fragment"
    }

    /** 取地址里的 host（小写）。解析失败时返回空串。 */
    fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    /** B 站官方域名（与 BiliPai `BilibiliRequestHeaderPolicy` 的口径一致）。 */
    private val OFFICIAL_CDN_SUFFIXES = listOf(
        "bilivideo.com",
        "bilivideo.cn",
        "bilibili.com",
        "hdslb.com",
    )

    /**
     * 已知的第三方 PCDN 域名后缀（B 站会动态下发，逐个补齐）。
     *
     * 与 BiliPai `pcdnHostSuffixes` / `riskyCdnHostSuffixes` 同口径。
     */
    private val RISKY_CDN_SUFFIXES = listOf(
        "szbdyd.com",
        "mountaintoys.cn",
        "qrstuvwxyzab.com",
        "zhenidc.cn",
    )

    private val IPV4_REGEX = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
}
