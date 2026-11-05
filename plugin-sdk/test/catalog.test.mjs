import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import { build } from "esbuild";
import { readFile } from "node:fs/promises";
import { validateManifest } from "../tools/manifest.mjs";
import Ajv2020 from "ajv/dist/2020.js";

async function load(name) {
  const manifest = JSON.parse(await readFile(`examples/${name}/manifest.json`, "utf8"));
  validateManifest(manifest);
  const result = await build({ entryPoints: [`examples/${name}/index.ts`], bundle: true, write: false,
    format: "iife", globalName: "pluginModule", platform: "browser", target: "es2020" });
  const context = vm.createContext({});
  vm.runInContext(result.outputFiles[0].text, context, { timeout: 1000 });
  const plugin = context.pluginModule.default;
  const schema = JSON.parse(await readFile("schema/manifest.schema.json", "utf8"));
  for (const capability of manifest.capabilities) {
    for (const method of schema["x-capability-methods"][capability]) assert.equal(typeof plugin[method], "function", `${name}: ${method}`);
  }
  return plugin;
}

test("catalog example serves collections, details, pagination, home and library", async () => {
  const plugin = await load("catalog");
  const playlist = (await plugin.playlists(1, 30)).items[0];
  const album = (await plugin.searchAlbums("", 1, 30)).items[0];
  const artist = (await plugin.searchArtists("", 1, 30)).items[0];
  const ranking = (await plugin.rankings(1, 30)).items[0];
  assert.equal((await plugin.playlistDetail(playlist.id, 1, 30)).tracks.total, 1);
  assert.equal((await plugin.albumDetail(album.id, 1, 30)).tracks.items[0].id, "tone-440hz");
  assert.equal((await plugin.artistDetail(artist.id, 1, 30)).tracks.items.length, 1);
  assert.equal((await plugin.rankingTracks(ranking.id, 2, 30)).items.length, 0);
  assert.equal((await plugin.homeFeed(0, 1, 0)).newSongs.length, 1);
  assert.equal((await plugin.userPlaylists(1, 30)).items[0].id, playlist.id);
  assert.equal((await plugin.savedAlbums(1, 30)).items[0].id, album.id);
  assert.equal((await plugin.followedArtists(1, 30)).items[0].id, artist.id);
  assert.equal((await plugin.resolveDownload({id:"tone-440hz"})).type, "playable");
  const schema = JSON.parse(await readFile("schema/protocol-v1.schema.json", "utf8"));
  const ajv = new Ajv2020({ allErrors: true }); ajv.addSchema(schema);
  const check = (name, value) => {
    const valid = ajv.compile({ $ref: `${schema.$id}#/$defs/${name}` });
    assert.equal(valid(value), true, JSON.stringify(valid.errors));
  };
  check("playlistPage", await plugin.playlists(1, 30));
  check("albumPage", await plugin.searchAlbums("", 1, 30));
  check("artistPage", await plugin.searchArtists("", 1, 30));
  check("rankingPage", await plugin.rankings(1, 30));
  check("playlistDetail", await plugin.playlistDetail(playlist.id, 1, 30));
  check("albumDetail", await plugin.albumDetail(album.id, 1, 30));
  check("artistDetail", await plugin.artistDetail(artist.id, 1, 30));
  check("homeFeed", await plugin.homeFeed(1, 1, 1));
  check("authenticationStatus", await plugin.authenticationStatus());
});

test("OAuth manifests require explicit HTTPS endpoints and a public PKCE client", () => {
  const manifest = { id:"org.example.oauth", name:"OAuth", version:"1.0.0", apiVersion:1, entry:"index.js", capabilities:["authentication"],
    networkDomains:["auth.example.com","api.example.com"], authentication: { type:"oauth2Pkce", authorizationEndpoint:"https://auth.example.com/authorize",
      tokenEndpoint:"https://auth.example.com/token", clientId:"public", scopes:["music.read"], domains:["api.example.com"] } };
  validateManifest(manifest);
  for (const change of [{tokenEndpoint:"http://auth.example.com/token"}, {tokenEndpoint:"https://other.example.com/token"},
    {clientSecret:"unsupported"}, {scopes:["invalid scope"]}, {domains:["other.example.com"]}]) {
    assert.throws(() => validateManifest({...manifest, authentication:{...manifest.authentication,...change}}));
  }
});

test("catalog favorites are scoped through host storage and writes persist", async () => {
  const plugin = await load("catalog"), values = new Map();
  const host = { storage: { get: async key => values.get(key) ?? null, set: async (key,value) => { values.set(key,value); } } };
  const track = { id: "tone-440hz" };
  assert.equal(await plugin.isFavorite(track, host), false);
  await plugin.setFavorite(track, true, host);
  assert.equal(await plugin.isFavorite(track, host), true);
  await plugin.setFavorite(track, false, host);
  assert.equal(await plugin.isFavorite(track, host), false);
});

test("token adapter requests a credential reference, not raw credentials, and reports expiry", async () => {
  const plugin = await load("api-token");
  assert.equal((await plugin.authenticationStatus({account:null})).type, "signedOut");
  const host = { account: { id:"opaque-account", displayName:"Test" }, http: { request: async request => {
    assert.equal(request.credential, "active"); assert.equal(request.headers, undefined); return {status:401};
  } } };
  assert.equal((await plugin.authenticationStatus(host)).type, "expired");
  host.http.request = async () => ({status:200, body:'{"token":"not-returned"}'});
  const status = await plugin.authenticationStatus(host);
  assert.equal(status.type, "signedIn");
  assert.equal(JSON.stringify(status).includes("not-returned"), false);
});
