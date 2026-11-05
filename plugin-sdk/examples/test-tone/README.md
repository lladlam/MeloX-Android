# CC0 test-tone plugin

This plugin synthesizes its own 250 ms, 440 Hz, low-amplitude mono PCM WAV.
The signal and example source are dedicated to **CC0-1.0**. No commercial music,
DRM, subscription API or third-party account is involved.

`httpbingo.org` is a public HTTP testing service. Its documented `/base64/`
endpoint decodes the waveform supplied by this plugin. It is not a music service
and has no uptime guarantee. The short duration keeps the URL within the host's
4096-character limit. Use a public HTTPS server you control for longer fixtures.

From `plugin-sdk/`:

```sh
npm ci
npm run build
node tools/cli.mjs validate examples/test-tone/manifest.json
node tools/cli.mjs pack examples/test-tone/manifest.json examples/test-tone/index.ts test-tone.melox-plugin
```

On Android: Settings → Extensions → Music source plugins → Import, then Enable.
On Search, select the test-tone source and enter any nonblank query. Tap the
result to enqueue it in Media3. The standard lyric line is supplied by the plugin.
These are intended integration steps, **not a claim of successful device testing**.

The optional `authenticationStatus` example reports `notRequired`. Android does
not yet expose that method through an Authentication Capability or account UI.
