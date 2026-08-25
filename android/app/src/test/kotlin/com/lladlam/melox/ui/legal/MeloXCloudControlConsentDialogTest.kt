package com.lladlam.melox.ui.legal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXCloudControlConsentDialogTest {
    @Test
    fun disclosureStatesScopeChoiceAndNoPenaltyForRefusal() {
        assertTrue(MELOX_CLOUD_CONTROL_MESSAGE.contains("只用于控制音乐源及其下属兼容功能"))
        assertTrue(MELOX_CLOUD_CONTROL_MESSAGE.contains("每次应用进入前台时检查一次"))
        assertTrue(MELOX_CLOUD_CONTROL_MESSAGE.contains("每两小时检查一次"))
        assertTrue(MELOX_CLOUD_CONTROL_MESSAGE.contains("拒绝不会影响未依赖云控的功能"))
    }

    @Test
    fun dedicatedPolicyDocumentsNetworkDataAndRevocation() {
        val policy = File("src/main/assets/legal/cloud-control-privacy-zh-CN.md").readText()

        assertTrue(policy.contains("DNS-over-HTTPS"))
        assertTrue(policy.contains("GhFast、GhProxy 和 GhProxy.org"))
        assertTrue(policy.contains("不会在测速或云控请求中加入账号凭据"))
        assertTrue(policy.contains("撤回后"))
        assertTrue(policy.contains("不能下发任意 API"))
    }

    @Test
    fun legalDocumentRowsHaveNoRightSideAnnotations() {
        val settings = File("src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt").readText()

        assertFalse(settings.contains("账号、权限与本地数据"))
        assertFalse(settings.contains("第三方服务、版权与风险"))
        assertFalse(settings.contains("音乐源兼容性、签名配置与选择权"))
    }
}
