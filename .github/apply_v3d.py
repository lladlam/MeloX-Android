from pathlib import Path

path = Path('android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt')
text = path.read_text()
needle = 'import androidx.compose.foundation.layout.width\n'
addition = 'import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.shape.RoundedCornerShape\n'
if 'import androidx.compose.foundation.shape.RoundedCornerShape\n' not in text:
    if needle not in text:
        raise SystemExit('MeloXApp import anchor not found')
    text = text.replace(needle, addition, 1)
path.write_text(text)
print('v3d compile repair applied')
