# innertube

Vendored from **Metrolist** — <https://github.com/mostafaalagamy/Metrolist> —
licensed GPL-3.0, the same licence this project carries. Copyright remains with
the Metrolist project and its contributors; see that repository's git history.

## What it is

A client for YouTube's private InnerTube API: the one `music.youtube.com`
itself talks to. It is what makes a *signed-in* YouTube Music experience
possible — the account's own library, its playlists, its home page, its
listening history — none of which an anonymous client can read.

## Why it is vendored rather than depended on

Metrolist does not publish this module to any repository. Copying it is the only
way to use it.

## Why the package is still `com.metrolist.innertube`

Deliberately not renamed. YouTube changes InnerTube's shape without warning and
upstream is where those repairs land first; keeping the package identical is
what makes it possible to diff against upstream and pull a fix in, rather than
re-deriving it here.

The corollary: **treat this directory as read-only.** Anything this app needs
that the module does not do belongs in `app/`, in
`dev.lelonio.square.backend.youtube`, not in edits made here — an edit made here
is an edit that has to be re-applied by hand every time upstream is pulled.

## Local changes

Kept to the minimum, and listed here because each one has to be re-applied by
hand whenever this module is refreshed from upstream.

**`build.gradle.kts`**

- Java 17 and `jvmTarget` 17, matching the rest of this project (upstream is 21).
- the Kotlin Android plugin declared via this project's own version catalog.
- a `dev` build type, because the app has one and a library with no matching
  variant cannot be resolved against it.
- **NewPipeExtractor upstream instead of Metrolist's fork.** The fork is built
  against a much older extractor — `getThumbnailUrl()` rather than
  `getThumbnails()`, a different `Downloader` — and both publish the same
  `org.schabi.newpipe.extractor` packages, so the two cannot coexist in one APK.
  This app's anonymous path was written and verified against upstream v0.26.4,
  where the fork's vintage is close to the version that could search but could
  not resolve a single stream.

**`pages/NewPipe.kt` — deleted**, and its one caller in `YouTube.kt`
(`getNewPipeStreamUrls`) reduced to `emptyList()`. That file is upstream's
NewPipe-based stream extractor and is what would not compile against the newer
extractor. Nothing here needs it: the branch calling it is behind
`ENABLE_NEWPIPE_STREAM_INFO_EXTRACTOR`, which is `false` upstream as well, and
stream URLs in this app come from `YouTubeStreamResolver`.

## Updating

Copy the module over from a newer Metrolist checkout, then re-apply everything
under "Local changes" above.
