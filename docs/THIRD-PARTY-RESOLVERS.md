# Configured third-party resolvers

Meting, CHKSZ-compatible APIs and UNM-compatible `/match` APIs are optional
resolvers at the existing final playback fallback stage. Official provider APIs,
quality selection, decoders, lyrics and plugin isolation remain unchanged.
Configure only services you are authorized to use; the existing third-party
consent and terms still apply.

## Configuration

Enable third-party music sources under **Music Services**, then configure and
enable each desired service:

| Service | Endpoint | Supported source IDs |
| --- | --- | --- |
| Meting | Complete API URL, such as `https://host/api.php` or `https://host/meting/` | NetEase, QQ Music, Kugou |
| CHKSZ | Server root or `/api`, plus the personal API Key | NetEase, QQ Music, Kugou |
| UNM | HTTP `/match?id=…&server=…` API, not an ordinary UNM proxy port | NetEase only |

Meting uses the configured path as entered and sends `server`, `type=url`, `id`,
`br` and `level`; it never guesses an `/api` suffix. Optional `auth` is a
service-specific token, not an official music-account cookie. CHKSZ retains the
`163_music`, `qq_music` and `kugou_music` request contracts, existing local API
keys, and the default `https://api.chksz.com` endpoint. UNM requires a server
implementing the `/match` contract; the upstream UNM proxy alone does not expose
that API.

Addresses, keys and explicit enabled flags remain local. A new installation has
no enabled service by default. Clearing a service disables it. Settings changes
are read for each resolution and invalidate earlier playback cache identities;
they do not interrupt or replace an already playing track.

## Playback and resource boundaries

1. Official current source.
2. Strict built-in cross-provider fallback, when enabled.
3. Configured Meting → CHKSZ → UNM → installed LX scripts.

The existing membership-only setting still applies to step 3. Terminal
no-source exceptions from built-in providers enter the same fallback stage when
consent is enabled. Authentication, network, timeout and cancellation failures
are not classified as no-source results. The adapter runs inside the existing
bounded provider resolution.

A legacy NetEase URI without title metadata can use its numeric song ID for a
configured API; matching stages can still reject missing metadata. Plugin sources
never enter built-in/LX resolvers. Without installed LX scripts, no LX matching
request or runtime starts. LX headers and reported quality fields are preserved.

Configured API traffic uses the cookie-free isolated transport, not the retrying
official API client. Connect/read/whole-call timeouts are 3/5/8 seconds with
connection retries disabled. Concurrent identical requests are coalesced;
positive results last at most two minutes, negative results last 30 seconds,
and network/authentication/rate-limit/server failures cool down the service.

Text bodies are bounded to 64 KiB before parsing. Only explicit audio URL fields
in known containers are accepted; artwork and error-page links are rejected.
Direct or redirected audio responses are closed after checking their headers,
without downloading a whole song as text. Cache identities hash local settings,
and diagnostics omit raw credentials and complete request URLs.

## Testing

**Test resolver** prefills the editable NetEase song ID `22676167` (DJ OKAWARI,
“Flower Dance”), selected from an actual Meting search and an audio-header check.
Only the selected service is requested, and service availability can still
change. Receiving an audio URL does **not** prove that it was downloaded,
decoded or played, and does not verify battery use or temperature.

Failed tests show the HTTP status and response body as selectable plain text.
Empty responses are explicit, non-text bodies are not rendered, and previews
are limited to 4,096 characters with configured credentials redacted. Response
bodies never enter playback logs. Explicit tests always make fresh requests,
without reusing playback caches or service cooldowns.

Regression tests cover protocol paths and source IDs, the preset test ID,
disabled configuration, bounded response parsing, direct/redirected audio,
cache expiry and cooldown, concurrent requests, fallback order, cancellation,
quality preservation, dynamic LX installation/removal, response previews,
truncation, and credential redaction.

Run Android builds and tests in GitHub Actions. Real-service playback and
real-device UI evidence remain separate acceptance checks.
