import { definePlugin, PluginError, type MusicPage, type MusicTrack } from "../../src/index.js";
import tone from "../test-tone/index.js";

// An owned, synthetic CC0 catalog, not an emulation of any third-party service.
const track: MusicTrack = { id: "tone-440hz", title: "MeloX 440 Hz Test Tone", artists: [{ id: "author", name: "MeloX SDK Example" }], durationMs: 250 };
const playlist = { id: "signals", title: "CC0 Test Signals", creatorName: "MeloX SDK", trackCount: 1 };
const album = { id: "signal-album", title: "Test Signals", artists: track.artists, trackCount: 1 };
const artist = { id: "author", name: "MeloX SDK Example", songCount: 1, albumCount: 1 };
const ranking = { id: "signal-ranking", title: "Test Signal Ranking" };
const pageOf = <T>(values: T[], page: number, pageSize: number): MusicPage<T> => ({
  items: values.slice((page - 1) * pageSize, page * pageSize), page, pageSize,
  total: values.length, hasMore: page * pageSize < values.length,
});
const checkId = (actual: string, expected: string) => { if (actual !== expected) throw new PluginError("NOT_FOUND"); };

export default definePlugin({
  ...tone,
  async resolveDownload(requested) { return tone.resolvePlayback(requested); },
  async playlists(page, pageSize) { return pageOf([playlist], page, pageSize); },
  async searchPlaylists(query, page, pageSize) { return pageOf(playlist.title.toLowerCase().includes(query.toLowerCase()) ? [playlist] : [], page, pageSize); },
  async playlistDetail(id, page, pageSize) { checkId(id, playlist.id); return { summary: playlist, tracks: pageOf([track], page, pageSize) }; },
  async searchAlbums(query, page, pageSize) { return pageOf(album.title.toLowerCase().includes(query.toLowerCase()) ? [album] : [], page, pageSize); },
  async albumDetail(id, page, pageSize) { checkId(id, album.id); return { summary: album, tracks: pageOf([track], page, pageSize) }; },
  async searchArtists(query, page, pageSize) { return pageOf(artist.name.toLowerCase().includes(query.toLowerCase()) ? [artist] : [], page, pageSize); },
  async artistDetail(id, page, pageSize) { checkId(id, artist.id); return { summary: artist, tracks: pageOf([track], page, pageSize) }; },
  async rankings(page, pageSize) { return pageOf([ranking], page, pageSize); },
  async rankingTracks(id, page, pageSize) { checkId(id, ranking.id); return pageOf([track], page, pageSize); },
  async homeFeed(playlistLimit, newSongLimit, rankingLimit) {
    return { recommendedPlaylists: [playlist].slice(0, playlistLimit), newSongs: [track].slice(0, newSongLimit), rankings: [ranking].slice(0, rankingLimit) };
  },
  // This demo library is local to the plugin, so no platform account is necessary.
  async accountSummary() { return null; },
  async userPlaylists(page, pageSize) { return pageOf([playlist], page, pageSize); },
  async savedAlbums(page, pageSize) { return pageOf([album], page, pageSize); },
  async followedArtists(page, pageSize) { return pageOf([artist], page, pageSize); },
  async isFavorite(requested, host) { return (await host.storage.get(`favorite.${requested.id}`)) === "true"; },
  async setFavorite(requested, favorite, host) {
    checkId(requested.id, track.id);
    await host.storage.set(`favorite.${requested.id}`, String(favorite));
  },
});
