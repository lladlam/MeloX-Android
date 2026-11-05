import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import { build } from "esbuild";
import { validateManifest } from "../tools/manifest.mjs";
import { createZip } from "../tools/zip.mjs";
import Ajv2020 from "ajv/dist/2020.js";

const example = JSON.parse(await readFile(new URL("../examples/test-tone/manifest.json", import.meta.url), "utf8"));
const fixtures = JSON.parse(await readFile(new URL("./fixtures/manifest-cases.json", import.meta.url), "utf8"));
const protocol = JSON.parse(await readFile(new URL("../schema/protocol-v1.schema.json", import.meta.url), "utf8"));
const ajv = new Ajv2020();
ajv.addSchema(protocol);
const valid = name => ajv.compile({ $ref: `${protocol.$id}#/$defs/${name}` });

test("SDK accepts and rejects the shared Android manifest contract fixtures", () => {
  for (const fixture of fixtures.cases) {
    const value = { ...fixtures.base, ...fixture.patch };
    if (fixture.valid) assert.doesNotThrow(() => validateManifest(value), fixture.name);
    else assert.throws(() => validateManifest(value), undefined, fixture.name);
  }
});

test("manifest validation rejects unsafe paths, version aliases and wildcard permissions", () => {
  assert.equal(validateManifest(example).id, example.id);
  for (const change of [ { entry: "../x.js" }, { entry: "/x.js" }, { version: "01.0.0" },
    { version: "1.0.0-01" }, { apiVersion: "1" }, { capabilities: ["search", "search"] },
    { capabilities: ["unknown"] }, { networkDomains: ["*.example.com"] }, { networkDomains: ["EXAMPLE.COM"] } ]) {
    assert.throws(() => validateManifest({ ...example, ...change }));
  }
});

test("ZIP writer emits local and central records with standard CRC32", () => {
  const zip = createZip([["index.js", "123456789"]]);
  assert.equal(zip.readUInt32LE(0), 0x04034b50);
  assert.equal(zip.readUInt32LE(14), 0xcbf43926);
  assert.equal(zip.readUInt32LE(zip.length - 22), 0x06054b50);
  assert.equal(zip.readUInt16LE(zip.length - 12), 1);
});

test("bundled example returns standard search, actual WAV resolution, lyrics and no-login state", async () => {
  const result = await build({ entryPoints: ["examples/test-tone/index.ts"], bundle: true, write: false,
    format: "iife", globalName: "__meloxModule", platform: "browser", target: "es2020",
    footer: { js: "globalThis.meloxPlugin = __meloxModule.default;" } });
  const context = vm.createContext({});
  vm.runInContext(result.outputFiles[0].text, context, { timeout: 1000 });
  const plugin = context.meloxPlugin;
  const page = await plugin.search("tone", 1, 30);
  assert.equal(valid("searchPage")(page), true);
  assert.equal(page.items[0].id, "tone-440hz");
  assert.equal(page.pageSize, 30);
  assert.equal((await plugin.search("tone", 2, 30)).items.length, 0);
  const resolution = await plugin.resolvePlayback(page.items[0]);
  assert.equal(resolution.type, "playable");
  assert.equal(valid("playbackResolution")(resolution), true);
  assert.ok(resolution.url.length < 4096);
  const bytes = Buffer.from(decodeURIComponent(new URL(resolution.url).pathname.split("/").at(-1)), "base64");
  assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
  assert.equal(bytes.readUInt32LE(24), 8000);
  assert.equal(bytes.length, 2044);
  assert.equal((await plugin.lyrics(page.items[0])).lines[0].timeMs, 0);
  assert.equal(valid("lyrics")(await plugin.lyrics(page.items[0])), true);
  assert.equal((await plugin.authenticationStatus()).type, "notRequired");
});
