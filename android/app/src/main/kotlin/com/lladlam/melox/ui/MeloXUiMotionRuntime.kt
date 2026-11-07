package com.lladlam.melox.ui

import android.os.SystemClock

/**
 * 播放页「正在运动」的运行期总闸（2026-10-22）。
 *
 * ### 为什么要有它
 * 播放页有**三条**互不相干的时间线，各自在自己的 composable 里算 `isRunning`：
 *   ① 打开 / 收起播放页（`MeloXIOSNowPlayingSharedHost.transitionInFlight`）；
 *   ② 封面 / 歌词 / 队列三页互相 morph（`MeloXIOSNowPlayingScene.pageTransition`）；
 *   ③ 横竖屏切换（同一个 Scene 里的 `orientTransition`，竖横两套布局同场 300ms）。
 *
 * 但有两处「付钱方」**不在**这三条时间线的 composable 里，拿不到它们的布尔：
 *   · 流光背景网格（`MeloXFlowingLightBackdrop`，挂在 SharedHost）；
 *   · 回前台管线预热（`MeloXGlassPipelinePrewarm`，挂在 MeloXApp 根）。
 * 没有这个闸，它们只能靠各自猜时长（猜短了没覆盖，猜长了观感被削）。所以把三个布尔
 * 落成一个进程内读数，谁需要谁读。
 *
 * ### 三条硬约束（别违反）
 * 1. **只写布尔、只读布尔**：不存 fraction、不存进度、不进 `mutableStateOf`。
 *    存进度就等于把消费方拖进每帧重组 —— 本仓库已经在
 *    `SeekableTransitionState.fraction` 和 `expansionProgress` 上各踩过一次。
 *    `@Volatile` + 普通读写即可：消费方都是在自己的帧回调里取值，不需要订阅。
 * 2. **每个写者只写自己那一格**，读的时候 OR。三个写者分处三个 composable，
 *    共用一格会互相清掉（A 落定时把 B 的 true 写成 false）。
 * 3. **必须在离开组合时复位**（`DisposableEffect.onDispose`）。转屏 / 页面被移出组合时
 *    协程不会跑完，漏复位就是「背景永久降频」「预热永久不跑」这类难查的慢性病。
 */
internal object MeloXUiMotionRuntime {
    /** ① 打开 / 收起播放页的外壳转场。写者：`MeloXIOSNowPlayingSharedHost`。 */
    @Volatile
    var shellTransition: Boolean = false

    /** ② 播放页内三页互相 morph。写者：`MeloXIOSNowPlayingScene`。 */
    @Volatile
    var pageTransition: Boolean = false

    /** ③ 播放页横竖屏切换（竖横两套布局同场）。写者：`MeloXIOSNowPlayingScene`。 */
    @Volatile
    var orientationTransition: Boolean = false

    /** 三条时间线只要有一条在跑，就算「播放页正在运动」。 */
    val anyPlayerMotion: Boolean
        get() = shellTransition || pageTransition || orientationTransition

    // ── 回前台「结算窗口」（2026-10-22 第四轮）────────────────────────────────
    //
    // 用户口径：「退后台一段时间，打开迷你栏动画直接卡炸，第二次不卡」。
    // 实测（第 3 段 FrameMetrics）：回前台后的那段窗口 115 帧 / 79fps / 掉 45 帧，
    // 其中一帧 `commandIssue` 89.55ms；紧随其后的 `shell-transition` 是 121fps 掉 0。
    // ⇒ 坏的不是动画，是**回前台这一小段时间里别的东西在抢**：流光网格每帧重算、
    //   管线预热块（回前台 500ms 后画 120ms 的玻璃 + 驱动同步编译）。
    //
    // 所以开一个 1.5s 的「结算窗口」：这段时间里两个付钱方主动退到最低档，
    // 把帧让给用户真正要看的那一下（点迷你栏 → 展开播放页）。
    // ⚠ 这不是猜时长：窗口**只覆盖回前台后的第一小段**，过了就自动失效，
    //   而且消费方都是「降档」不是「冻结」，最长错过一帧网格更新。

    /** 结算窗口长度。实测坏窗口 1~1.4s，取 1.5s 整段盖住。 */
    private const val FOREGROUND_SETTLE_MS = 1_500L

    /** 结算窗口截止时刻（`elapsedRealtime`，含深睡，不会像 `uptimeMillis` 那样睡过去不算）。 */
    @Volatile
    private var settleUntilElapsed = 0L

    /**
     * 由 `MeloXAppVisibility` 在「本次启动来自一次真实的退后台」时调用。
     *
     * ⚠ 只由那**一个**写者调用：三个运动布尔已各有写者，这里再多人写就会互相截断。
     */
    fun markForegroundReturn() {
        settleUntilElapsed = SystemClock.elapsedRealtime() + FOREGROUND_SETTLE_MS
    }

    /** 是否还在回前台结算窗口内（读的是时间，不需要任何订阅/重组）。 */
    val foregroundSettling: Boolean
        get() = SystemClock.elapsedRealtime() < settleUntilElapsed
}
