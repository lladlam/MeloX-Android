# Background page restoration — 2026-11-06

## Failure evidence

The daily `.dev` device's lifecycle/events logs showed the same PID surviving
while the system destroyed stopped Activities with reason `low-mem`:

- `MainActivity` stopped/saved at 04:36:05, destroyed at 04:36:06.
- `MeloXSettingsDetailActivity` stopped/saved at 04:36:24, destroyed immediately
  afterward; the next instance was created with `restored=true`.
- The daily-recommendation route restored its cover and count (46 tracks), but
  the actual song rows remained empty. The playlist loader sent supplied lists
  through a process-wide cold-start request gate, which suppressed the second
  composition even though no disk cache existed for these synthetic IDs.

This is Activity destruction, not evidence of process death or a floating-lyrics
setting being enabled. Diagnostic device installation preceded the changes here;
the complete fix was tested on the emulator, not on the user's daily device.

## Changes

- Small routes, selected tabs, queries, download subpages, artist sections,
  podcast IDs and player visibility/page use instance-state saving.
- `MainActivity` saves its inline detail Intent stack. Page and adaptive-slot keys
  remain stable across reconstruction, including the nested page's own state.
- Large model/list states use a bounded (256-entry LRU) process-local store with
  opaque UUIDs in the saved-state Bundle. No Activity, View, job or callback is
  retained by that store. Source/account input matching rejects stale tokens.
  Missing tokens after process death/eviction fall back to normal page loaders;
  this cache is not persistent disk storage or a process-death acceptance claim.
- Loaded provider feeds, search/catalog results, library/playlist/album details,
  rankings, podcasts, account profiles, cloud pages and conversation data are
  reused on Activity restoration. Transient loading jobs/busy flags are restarted,
  not retained. Explicit refresh/retry still reloads the relevant data.
- Supplied song lists bypass the disk/network cold-start gate. A cache miss must
  load even if a previous request already consumed the process-wide gate.
- A complete playlist is distinguished from its provisional first 100 songs, so
  an interrupted progressive load can resume instead of freezing at that prefix.
- `selected_source_id` takes precedence over the legacy built-in-source field.
  The emulator had `selected_source=youtube_music` and `selected_source_id=netease`;
  before this correction, returning to the foreground silently replaced the
  NetEase Home composition with the other provider's page.
- A restored player waits for service metadata instead of interpreting its
  initial empty connection snapshot as a request to close the player.

## Actual emulator checks

Device: `emulator-5554`, 320×640. No user accounts were imported. The existing
synthetic RC plugin was used for plugin-page/player checks.

For reconstruction checks: open/load the page, press Home, wait **4 seconds**,
toggle `cmd uimode night yes/no` while backgrounded (these Activities do not
handle `uiMode` themselves), then return with a launcher-equivalent intent:

```sh
adb -s emulator-5554 shell am start -W \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -f 0x10200000 \
  -n com.lladlam.melox.android.dev/com.lladlam.melox.MainActivity
```

Lifecycle logs confirmed old-instance destruction and new-instance
`created restored=true` in the same process. Merely changing the emulator's
`always_finish_activities` preference did not reliably destroy the Activity, so
that setting alone was **not** counted as reconstruction evidence.

| Path | Observed result |
| --- | --- |
| Home → hot chart (200 songs), scroll, background/reconstruct | Same chart and populated rows restored; not Home root |
| Hot chart → Back → reopen → Home for 4 seconds → return | Populated chart remained available |
| Settings → General, scrolled | Same native detail Activity/section restored |
| Search `hello` | Query, selected result type and populated results restored |
| Search → playlists → playlist detail (349 songs), scroll | Same playlist detail and song rows restored |
| Home → Podcasts (inline detail Intent stack) | Podcast page and loaded feed restored |
| Podcasts → a podcast's episode list | Same podcast/episodes restored inside the restored tool page |
| Synthetic plugin Home → playlist collection | Same collection, page number and track rows restored |
| Full-screen player with paused synthetic track | Full-screen player restored, same track and paused state |

Before/after screenshots and lifecycle logs are held in the local verification
directory `/tmp/opencode` (`chart-fixed-*`, `search-*-restore`,
`search-detail-*`, `settings-general-*`, `tool-podcast-*`, `podcast-detail-*`,
`plugin-detail-*`, `player-*-restore`, `emu-fixed-lifecycle.log`). These are
targeted restoration checks, not full release/plugin acceptance.

JVM regression tests additionally cover large-state tokens, account/source
isolation, process-cache misses, repeated saves and LRU bounds.

Final build: `:app:assembleDebug` and `:app:testDebugUnitTest` passed: **535
tests, 0 failures/errors, 3 existing skips**. The final APK was rechecked with both
a plain four-second background return and an actual Activity reconstruction;
the chart's song rows, scroll position and collapsed bottom chrome were retained
(`complete-chart-before.png`, `complete-chart-after.png`,
`complete-chart-recreated.png`, `complete-restore-lifecycle.log`). Emulator source
preferences and temporary lifecycle-testing settings were restored afterward.
