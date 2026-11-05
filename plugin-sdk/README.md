# MeloX Plugin SDK — development preview

RC package/security details and device automation: [RC acceptance](../docs/plugin-system/rc-acceptance.md).
API v1 is unchanged. Newly packed archives include an SHA-256 integrity file; optionally append
`--sign-key /path/to/private.pem` to `pack` to sign it with an RSA 2048–4096-bit PKCS#8 PEM key.
Keep private keys outside the repository and plugin ZIP. The host displays a public-key fingerprint;
first-time trust requires independent verification, and a signature does not certify plugin safety.

**Progressive audio only.** HLS (`.m3u8`/`hls`), DASH (`.mpd`/`dash`) and DRM/license configurations
are unsupported and explicitly rejected by the Android host. Do not declare a playable resolution for
content the host cannot decode; return an appropriate unavailable/rights state. Standard response schemas
check structure; DNS, actual content MIME, account/domain authorization and resource limits are host policy.

The SDK builds and packages API-v1 JavaScript plugins. Android host integration
is under development: **a successful SDK build does not establish that the plugin
works on a device**. See [verification](../docs/plugin-system/verification.md).

## Supported host surface

The Android source implements `search`, `playback`, `lyrics`, `playlists`, `albums`,
`artists`, `rankings`, `homeFeed`, `userLibrary`, `favorites`, `download`, and
`authentication`. These paths are connected to the existing Search, song-action,
download and plugin Settings UI. API 35 emulator checks now cover runtime/Keystore and a synthetic
Coil/Media3 path; **full capability UI, API 24, physical-device and OAuth acceptance remain incomplete**.
The complete required-method table is `schema/manifest.schema.json` →
`x-capability-methods`; Kotlin and SDK tests check this same contract.

`examples/catalog` demonstrates every catalog capability using the CC0 test tone;
`examples/api-token` demonstrates opaque credential requests using a **disposable**
test token. See [catalog/authentication integration](../docs/plugin-system/05-capabilities-and-authentication.md)
for method signatures, account management, OAuth2/PKCE setup and limits.

## Build a plugin

Node.js 20+ is recommended. From this directory:

```sh
npm ci
npm run typecheck
npm run build
npm test
node tools/cli.mjs validate examples/test-tone/manifest.json
node tools/cli.mjs pack examples/test-tone/manifest.json examples/test-tone/index.ts /tmp/test-tone.melox-plugin
```

The pack command bundles the entry as a browser-targeted ES2020 IIFE, installs the
default export as `globalThis.meloxPlugin`, and writes a ZIP containing
`manifest.json` and the manifest's entry path. Existing output files are not
overwritten. Node built-ins, Android classes and app internals are not plugin APIs.
The output script must fit the Android runtime's 128 KiB limit; the ZIP has a
4 MiB limit. Both the packer and Android check the script size.

## Write an adapter

This is the actual SDK calling convention: positional method arguments followed
by a host context. IDs are opaque strings local to your plugin. The host assigns
the source ID; never put a third-party platform's raw response into `items`.

```ts
import { definePlugin, PluginError } from "@melox/plugin-sdk";

export default definePlugin({
  async search(query, page, pageSize, host) {
    const response = await host.http.request({
      url: `https://api.your-domain.example/tracks?q=${encodeURIComponent(query)}&page=${page}`,
    });
    if (response.status !== 200) throw new PluginError("NETWORK_REQUEST_FAILED");
    const upstream = JSON.parse(response.body);
    // The plugin owns this conversion, including duration units and pagination.
    return {
      items: upstream.records.map((song: any) => ({
        id: String(song.key), title: song.name,
        artists: song.performers.map((name: string) => ({ name })),
        durationMs: song.seconds * 1000,
      })),
      page, pageSize, hasMore: upstream.nextPage !== null,
    };
  },
});
```

Replace the sample endpoint and response fields with your own authorized API.
This snippet demonstrates an adapter; the [test-tone example](examples/test-tone/)
is the complete supplied fixture with an actually exercised public audio URL.
For local SDK use, import `../../src/index.js` as the example does, or build this
SDK and install its local package in your plugin project. The development package
is currently marked private and has not been published to npm.

## Host context

- `apiVersion`: `1`.
- `platform`: `android`, `windows` or `web`; the current Android host supplies
  `android`. Windows/Web transports are not implemented in this repository.
- `http.request({url, method?, headers?, body?, credential?})`: Promise of `{status, headers,
  body}`; body is UTF-8 text, not a browser `Response`. GET/POST only. HTTP error
  statuses are returned; transport/policy failures reject. Cookies are not managed
  globally, and response `Set-Cookie` is not exposed.
- `account`: selected opaque `{id, displayName}` or `null`; never contains tokens.
  `credential: "active"` requests host-side injection on declared credential domains.
  Playable/download resolutions may also request `credential: "active"`.
- `storage.get/set/remove`: per-plugin/account non-secret string storage. Keys match
  `[A-Za-z0-9_.-]{1,128}`; at most 64 keys, 16 KiB/value and 64 KiB total. Use no
  credentials here: use the host-managed Keystore account vault. Storage survives package
  updates and is retained on uninstall in the current implementation.
- `log.info/warn/error`: at most 32 events per call, with at most 100 in-memory
  events retained per runtime. Script message bodies are not retained or sent to
  logcat. The settings diagnostic viewer exposes sanitized event metadata.

Every invocation starts a fresh JS context. Do not rely on module globals
surviving calls. There is no DOM, WebView, `fetch`, Node.js, timers or platform SDK
API. Return Promises; use the host context for asynchronous I/O.

## Data and errors

- Durations and lyric times are milliseconds; bitrate is bits/second.
- Page numbers are one-based; page sizes are 1–100. `hasMore` is explicit.
- Quality values are `standard`, `high`, `lossless`, `hiResolution`, `immersive`,
  `master`. Report the actual resolved quality rather than echoing the request.
- Playback uses `type` discriminants, `headers` and optional `expiresAtEpochMs`.
  Never return a DRM bypass or a platform raw URL object.
- Lyrics are sorted `lines` with `timeMs` and `text`; optional duration,
  translation and romanization use the same line. Plain lyrics set
  `isUnsynchronized: true`.
- Missing methods/unsupported capabilities must be omitted from the manifest.
  Throw `PluginError("UNSUPPORTED_OPERATION")` when applicable; do not fabricate
  empty results to claim support.

See [`schema/`](schema/) and [`src/types.ts`](src/types.ts). Schemas describe the
core wire shape; UTF-8 byte limits, permissions and cross-field invariants are
additional host checks. Kotlin currently uses explicit validators plus shared
manifest fixtures, not generated classes. Full model conformance is still pending.

## Install, update and debug

Android Settings → Extensions → Music source plugins has Import/Update, Enable,
Disable and Uninstall actions. New installs are disabled. Enabling first loads the
entry and checks declared methods in the worker, then registers the Provider.
Select an enabled source in the service center, plugin Settings or existing Search screen.

Updates require the same ID and strictly higher SemVer precedence. The new entry
is prepared before the existing package is replaced using Android `AtomicFile`.
A preparation/write failure keeps the previous package; enabled providers are
swapped in the shared registry. RC builds also verify package integrity and optional publisher signatures;
publisher changes require explicit approval bound to the candidate archive hash. Local TOFU records
survive uninstall; they are not a global publisher certificate or a guarantee that plugin code is safe.

Disable/uninstall removes the Provider, not saved tracks or listening history.
Missing sources retain their IDs and fail resolution rather than becoming
NetEase. Downgrading to older MeloX releases is **not** validated as lossless.

See [protocol](../docs/plugin-system/03-protocol-v1.md),
[runtime/security](../docs/plugin-system/04-runtime-and-security.md) and
[remaining work](../docs/plugin-system/remaining-work.md).
