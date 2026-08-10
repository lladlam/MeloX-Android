from pathlib import Path
root=Path(__file__).resolve().parents[1]
for rel in [
    'android/app/src/main/kotlin/com/lladlam/melox/ui/library/MeloXPlaylistActionsOverlay.kt',
    'android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXSongActionsOverlay.kt',
]:
    p=root/rel
    t=p.read_text()
    t=t.replace('NeteaseLibraryClient{NeteaseSessionStore.readCookie(app)}','NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })')
    t=t.replace('NeteaseMusicOperationsClient{NeteaseSessionStore.readCookie(app)}','NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })')
    t=t.replace('NeteaseLibraryClient { NeteaseSessionStore.readCookie(app) }','NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })')
    t=t.replace('NeteaseMusicOperationsClient { NeteaseSessionStore.readCookie(app) }','NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(app) })')
    p.write_text(t)
(root/'tools/one_shot_constructor_fix.py').unlink(missing_ok=True)
(root/'.github/workflows/one-shot-constructor-fix.yml').unlink(missing_ok=True)
