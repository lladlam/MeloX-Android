from pathlib import Path
root=Path(__file__).resolve().parents[1]

ops=root/'android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseMusicOperationsClient.kt'
t=ops.read_text()
if 'const val NETEASE_CHECK_TOKEN' not in t:
    t=t.replace(
        '    private fun ensureLoggedIn() {',
        '    private companion object {\n'
        '        const val NETEASE_CHECK_TOKEN = "9ca17ae2e6ffcda170e2e6ee8af14fbabdb988f225b3868eb2c15a879b9a83d274a790ac8ff54a97b889d5d42af0feaec3b92af58cff99c470a7eafd88f75e839a9ea7c14e909da883e83fb692a3abdb6b92adee9e"\n'
        '    }\n\n'
        '    private fun ensureLoggedIn() {'
    )
ops.write_text(t)

lib=root/'android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt'
t=lib.read_text().replace(
    '            withContext(kotlinx.coroutines.Dispatchers.IO) {',
    '            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {'
)
lib.write_text(t)

(root/'tools/one_shot_preflight_fix.py').unlink(missing_ok=True)
(root/'.github/workflows/one-shot-preflight-fix.yml').unlink(missing_ok=True)
