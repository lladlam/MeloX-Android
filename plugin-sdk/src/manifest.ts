/** Manifest accepted by Android's PluginManifestParser (API version 1). */
export type PluginCapability = "search" | "playback" | "lyrics" | "playlists" | "albums" | "artists" |
  "rankings" | "homeFeed" | "userLibrary" | "favorites" | "authentication" | "download";

export interface PluginManifest {
  /** Lowercase dotted identifier; the host prepends `plugin:`. */
  id: string;
  name: string;
  version: string;
  apiVersion: 1;
  /** Relative archive path to the JavaScript entry file. */
  entry: string;
  capabilities: PluginCapability[];
  /** Exact lowercase ASCII hostnames. Android permits public HTTPS targets on port 443 only. */
  networkDomains?: string[];
  author?: string;
  homepage?: string;
  authentication?: { type: "none" } | { type: "apiToken"; header: string; prefix: string; domains: string[] } |
    { type: "oauth2Pkce"; authorizationEndpoint: string; tokenEndpoint: string; clientId: string; scopes: string[]; domains: string[] };
}
