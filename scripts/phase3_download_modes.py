from pathlib import Path
ROOT=Path('android/app/src/main/kotlin/com/lladlam/melox')

def patch(path, old, new, label):
    p=ROOT/path
    s=p.read_text()
    if old not in s:
        raise SystemExit('missing '+label)
    p.write_text(s.replace(old,new,1))

patch(
    'core/download/MeloXDownloadStore.kt',
    '''            val source = withContext(Dispatchers.IO) {\n                qualityClient.downloadSourceBlocking(song.id, quality)\n            }\n            val request = Request.Builder()\n                .url(source.url)\n''',
    '''            val resolvedSource = withContext(Dispatchers.IO) {\n                qualityClient.downloadSourceBlocking(song.id, quality)\n            }\n            val request = Request.Builder()\n                .url(resolvedSource.url)\n''',
    'download outer source',
)
patch(
    'core/download/MeloXDownloadStore.kt',
    '                            Triple(received, expected, source)\n',
    '                            Triple(received, expected, resolvedSource)\n',
    'download triple source',
)

patch(
    'playback/MeloXPlaybackService.kt',
    '''    private fun buildPlayer(managesAudioFocus: Boolean): ExoPlayer =\n        ExoPlayer.Builder(this)\n            .setMediaSourceFactory(mediaSourceFactory)\n            .setWakeMode(C.WAKE_MODE_LOCAL)\n            .build()\n            .apply {\n                setAudioAttributes(audioAttributes, managesAudioFocus)\n                setHandleAudioBecomingNoisy(managesAudioFocus)\n                addListener(playerListener)\n            }\n''',
    '''    private fun buildPlayer(\n        managesAudioFocus: Boolean,\n        observesSession: Boolean = managesAudioFocus,\n    ): ExoPlayer =\n        ExoPlayer.Builder(this)\n            .setMediaSourceFactory(mediaSourceFactory)\n            .setWakeMode(C.WAKE_MODE_LOCAL)\n            .build()\n            .apply {\n                setAudioAttributes(audioAttributes, managesAudioFocus)\n                setHandleAudioBecomingNoisy(managesAudioFocus)\n                if (observesSession) addListener(playerListener)\n            }\n''',
    'buildPlayer listener lifecycle',
)
patch(
    'playback/MeloXPlaybackService.kt',
    '        val incoming = buildPlayer(managesAudioFocus = false)\n',
    '        val incoming = buildPlayer(managesAudioFocus = false, observesSession = false)\n',
    'incoming no session listener',
)
patch(
    'playback/MeloXPlaybackService.kt',
    '''        incoming.setAudioAttributes(audioAttributes, true)\n        incoming.setHandleAudioBecomingNoisy(true)\n        mediaSession?.setPlayer(incoming)\n''',
    '''        incoming.setAudioAttributes(audioAttributes, true)\n        incoming.setHandleAudioBecomingNoisy(true)\n        incoming.addListener(playerListener)\n        mediaSession?.setPlayer(incoming)\n''',
    'incoming listener at promotion',
)
print('phase3 repair applied')
