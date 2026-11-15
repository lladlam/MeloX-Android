import { definePlugin, PluginError } from "../../src/index.js";

/**
 * Generic resolver template: receives a track owned by another source and
 * returns a progressive HTTP(S) audio URL. Replace the endpoint with a service
 * you are authorized to use and keep it behind HTTPS.
 */
export default definePlugin({
  async resolveFallback(track, quality, host) {
    if (track.source !== "netease" || !/^\d+$/.test(track.id)) {
      throw new PluginError("UNSUPPORTED_OPERATION");
    }
    const endpoint =
      `https://unm.example.com/match?id=${encodeURIComponent(track.id)}` +
      "&server=kugou,bodian,kuwo,bilibili";
    const response = await host.http.request({ url: endpoint });
    if (response.status === 401 || response.status === 403) throw new PluginError("LOGIN_REQUIRED");
    if (response.status === 429) throw new PluginError("RATE_LIMITED");
    if (response.status < 200 || response.status >= 300) throw new PluginError("NETWORK_REQUEST_FAILED");

    let value: { url?: unknown };
    try {
      value = JSON.parse(response.body);
    } catch (_) {
      throw new PluginError("PLUGIN_ERROR");
    }
    if (typeof value.url !== "string" || !/^https?:\/\//i.test(value.url)) {
      return { type: "unavailable", reason: "no matching audio" };
    }
    return { type: "playable", url: value.url };
  },
});
