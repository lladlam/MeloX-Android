# Resolver plugin template

`resolver` is a separate API-v1 capability: the host passes a track owned by
another source and the plugin returns a playback resolution. It is used only in
the final third-party fallback stage, after the current provider and the strict
built-in cross-provider match fail.

The template calls a UNM-compatible `/match` service:

```sh
# Replace unm.example.com with your own HTTPS service, then:
node tools/cli.mjs pack examples/resolver/manifest.json examples/resolver/index.ts /tmp/opencode/resolver.melox-plugin
```

Install the resulting package in **Settings → Music source plugins**, enable it,
then turn on **Third-party music sources**. Resolver-only plugins are not offered
as a main music source or in catalog search.

Important differences from a normal provider:

- The input `track.source` is a built-in source value such as `netease`, not this
  plugin's source ID.
- `resolveFallback` can return short-lived `http://` or `https://` progressive
  audio URLs, including CDN hosts outside `networkDomains`; this matches the
  existing third-party resolver contract.
- `credential: "active"` is rejected for resolver results. Use a signed URL or
  return request headers if the service requires authentication.
- HLS/DASH and DRM are rejected. Preview results are skipped by the host.
- The plugin still cannot bypass the host's 8-second execution/HTTP bounds.

The `/match` service in this repository (`melox-unm-match/`) is a working
adapter for Dolby/UnblockNeteaseMusic. For a local HTTP service, use the
built-in **UNM** resolver instead; the plugin sandbox only calls HTTPS/public
domains.
