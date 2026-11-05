/** Version-one JSON transport types. See README for the implemented capability subset. */
export type AudioQualityTier = "standard" | "high" | "lossless" | "hiResolution" | "immersive" | "master";
export type Availability = "unknown" | "playable" | "previewOnly" | "loginRequired" |
  "subscriptionRequired" | "regionRestricted" | "copyrightRestricted" | "unavailable";

/** `id` is opaque within the declaring plugin; duration is in milliseconds. */
export interface MusicTrack {
  id: string;
  title: string;
  artists: Array<{ id?: string; name: string }>;
  album?: { id?: string; name: string; artworkUrl?: string };
  artworkUrl?: string;
  durationMs?: number;
  availability?: Availability;
  bestQuality?: AudioQualityTier;
  /** Bits per second. */
  bestBitrate?: number;
}

export interface MusicPage<T> {
  items: T[];
  /** One-based page number. */
  page: number;
  pageSize: number;
  total?: number;
  hasMore: boolean;
}

export type PlaybackResolution =
  | { type: "playable"; url: string; headers?: Record<string, string>; actualQuality?: AudioQualityTier;
      bitrate?: number; format?: string; expiresAtEpochMs?: number; credential?: "active" }
  | { type: "preview"; url: string; durationMs?: number }
  | { type: "loginRequired" | "subscriptionRequired" | "regionRestricted" | "copyrightRestricted" }
  | { type: "unavailable"; reason?: string };

export interface LyricLine {
  /** Absolute milliseconds from start of track. */
  timeMs: number;
  durationMs?: number;
  text: string;
  translation?: string;
  romanization?: string;
}
export interface LyricsResult { lines: LyricLine[]; isUnsynchronized?: boolean }

export interface HttpResponse { status: number; headers: Record<string, string>; body: string }
export interface HostContext {
  readonly apiVersion: 1;
  readonly platform: "android" | "windows" | "web";
  /** Opaque host account identity. No raw credentials are exposed. */
  readonly account: AccountSummary | null;
  http: { request(request: { url: string; method?: "GET" | "POST"; headers?: Record<string, string>; body?: string; credential?: "active" }): Promise<HttpResponse> };
  storage: { get(key: string): Promise<string | null>; set(key: string, value: string): Promise<void>; remove(key: string): Promise<void> };
  log: { info(message: string): void; warn(message: string): void; error(message: string): void };
}

export interface AccountSummary { id: string; displayName: string; avatarUrl?: string; subtitle?: string }
export type AuthenticationState =
  | { type: "notRequired" }
  | { type: "signedOut" }
  | { type: "signedIn"; account: AccountSummary }
  | { type: "expired"; accountId?: string };

export type PluginErrorCode = "UNSUPPORTED_OPERATION" | "INVALID_ARGUMENT" | "LOGIN_REQUIRED" | "AUTH_EXPIRED" |
  "PERMISSION_DENIED" | "RATE_LIMITED" | "NETWORK_REQUEST_FAILED" | "NOT_FOUND" | "PLUGIN_ERROR";
export class PluginError extends Error {
  constructor(readonly code: PluginErrorCode, message = code) { super(message); this.name = "PluginError"; }
}

export interface PlaylistSummary { id: string; title: string; artworkUrl?: string; creatorName?: string; description?: string; trackCount?: number; playCount?: number }
export interface AlbumSummary { id: string; title: string; artists?: Array<{ id?: string; name: string }>; artworkUrl?: string; trackCount?: number; releaseDate?: string }
export interface ArtistSummary { id: string; name: string; artworkUrl?: string; description?: string; songCount?: number; albumCount?: number }
export interface RankingSummary { id: string; title: string; subtitle?: string; artworkUrl?: string }
export interface CollectionDetail<T> { summary: T; tracks: MusicPage<MusicTrack> }
export interface HomeFeed { recommendedPlaylists: PlaylistSummary[]; newSongs: MusicTrack[]; rankings: RankingSummary[] }

export interface MusicPlugin {
  search?(query: string, page: number, pageSize: number, context: HostContext): Promise<MusicPage<MusicTrack>>;
  resolvePlayback?(track: MusicTrack, quality: AudioQualityTier, context: HostContext): Promise<PlaybackResolution>;
  resolveDownload?(track: MusicTrack, quality: AudioQualityTier, context: HostContext): Promise<PlaybackResolution>;
  lyrics?(track: MusicTrack, context: HostContext): Promise<LyricsResult>;
  playlists?(page: number, pageSize: number, context: HostContext): Promise<MusicPage<PlaylistSummary>>;
  searchPlaylists?(query: string, page: number, pageSize: number, context: HostContext): Promise<MusicPage<PlaylistSummary>>;
  playlistDetail?(id: string, page: number, pageSize: number, context: HostContext): Promise<CollectionDetail<PlaylistSummary>>;
  searchAlbums?(query: string, page: number, pageSize: number, context: HostContext): Promise<MusicPage<AlbumSummary>>;
  albumDetail?(id: string, page: number, pageSize: number, context: HostContext): Promise<CollectionDetail<AlbumSummary>>;
  searchArtists?(query: string, page: number, pageSize: number, context: HostContext): Promise<MusicPage<ArtistSummary>>;
  artistDetail?(id: string, page: number, pageSize: number, context: HostContext): Promise<CollectionDetail<ArtistSummary>>;
  rankings?(page: number, pageSize: number, context: HostContext): Promise<MusicPage<RankingSummary>>;
  rankingTracks?(id: string, page: number, pageSize: number, context: HostContext): Promise<MusicPage<MusicTrack>>;
  homeFeed?(playlistLimit: number, newSongLimit: number, rankingLimit: number, context: HostContext): Promise<HomeFeed>;
  accountSummary?(context: HostContext): Promise<AccountSummary | null>;
  userPlaylists?(page: number, pageSize: number, context: HostContext): Promise<MusicPage<PlaylistSummary>>;
  savedAlbums?(page: number, pageSize: number, context: HostContext): Promise<MusicPage<AlbumSummary>>;
  followedArtists?(page: number, pageSize: number, context: HostContext): Promise<MusicPage<ArtistSummary>>;
  isFavorite?(track: MusicTrack, context: HostContext): Promise<boolean>;
  setFavorite?(track: MusicTrack, favorite: boolean, context: HostContext): Promise<void>;
  /** Requires authentication capability and a matching Manifest authentication policy. */
  authenticationStatus?(context: HostContext): Promise<AuthenticationState>;
}

export function definePlugin<T extends MusicPlugin>(plugin: T): T { return plugin; }
