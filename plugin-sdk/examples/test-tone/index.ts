import { definePlugin, type MusicTrack } from "../../src/index.js";

/** Original synthesized test signal, dedicated to CC0-1.0. No platform music is fetched. */
const track: MusicTrack = {
  id: "tone-440hz",
  title: "MeloX 440 Hz Test Tone",
  artists: [{ name: "MeloX SDK Example" }],
  durationMs: 250,
  availability: "playable",
};

function audioUrl(): string {
  const samples = 2000, rate = 8000;
  const bytes = new Uint8Array(44 + samples), view = new DataView(bytes.buffer);
  const text = (offset: number, value: string) => [...value].forEach((c, i) => { bytes[offset + i] = c.charCodeAt(0); });
  text(0, "RIFF"); view.setUint32(4, 36 + samples, true); text(8, "WAVE"); text(12, "fmt ");
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, rate, true); view.setUint32(28, rate, true); view.setUint16(32, 1, true);
  view.setUint16(34, 8, true); text(36, "data"); view.setUint32(40, samples, true);
  for (let i = 0; i < samples; i++) {
    const envelope = Math.min(i / 80, (samples - i) / 80, 1);
    bytes[44 + i] = Math.round(128 + 24 * envelope * Math.sin(2 * Math.PI * 440 * i / rate));
  }
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let encoded = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const word = (bytes[i] << 16) | ((bytes[i + 1] ?? 0) << 8) | (bytes[i + 2] ?? 0);
    encoded += alphabet[(word >>> 18) & 63] + alphabet[(word >>> 12) & 63];
    if (i + 1 < bytes.length) encoded += alphabet[(word >>> 6) & 63];
    if (i + 2 < bytes.length) encoded += alphabet[word & 63];
  }
  while (encoded.length % 4) encoded += "=";
  return `https://httpbingo.org/base64/${encodeURIComponent(encoded)}?content-type=audio%2Fwav`;
}

export default definePlugin({
  async search(query, page, pageSize) {
    const matches = query.trim().length > 0;
    return { items: matches && page === 1 ? [track] : [], page, pageSize, total: matches ? 1 : 0, hasMore: false };
  },
  async resolvePlayback(requested) {
    if (requested.id !== track.id) return { type: "unavailable", reason: "Unknown test signal" };
    return { type: "playable", url: audioUrl(), format: "wav", actualQuality: "standard" };
  },
  async lyrics() {
    return { lines: [{ timeMs: 0, durationMs: 250, text: "440 Hz · CC0 test signal" }] };
  },
  // A state example; account management is not yet connected in Android.
  async authenticationStatus() { return { type: "notRequired" as const }; },
});
