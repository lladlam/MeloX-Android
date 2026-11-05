import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, createPublicKey, verify } from "node:crypto";
import { packageEntries, packageDomain, sha256 } from "../tools/package-trust.mjs";
import { readFileSync } from "node:fs";
import Ajv2020 from "ajv/dist/2020.js";

test("package integrity covers exact manifest and script; optional signature binds the complete list", () => {
  const {privateKey} = generateKeyPairSync("rsa", {modulusLength: 2048});
  const entries = new Map(packageEntries([["manifest.json", "{}"], ["index.js", "script"]], privateKey.export({type:"pkcs8",format:"pem"})));
  const raw = entries.get("integrity.json");
  assert.equal(JSON.parse(raw).files["index.js"], sha256(Buffer.from("script")));
  const publisher = JSON.parse(entries.get("publisher.json"));
  const publicKey = createPublicKey({key:Buffer.from(publisher.publicKey,"base64"),type:"spki",format:"der"});
  const signed = Buffer.concat([packageDomain, raw]);
  assert.ok(verify("sha256", signed, publicKey, Buffer.from(publisher.signature,"base64")));
  assert.equal(verify("sha256", Buffer.concat([signed,Buffer.from("tamper")]), publicKey, Buffer.from(publisher.signature,"base64")), false);
  assert.equal(packageEntries([["index.js","script"]]).length, 2);
  assert.throws(() => packageEntries([["publisher.json","spoof"]]));
});

test("response schemas reject adaptive/DRM declarations and non-string artwork", () => {
  const schema = JSON.parse(readFileSync(new URL("../schema/protocol-v1.schema.json", import.meta.url), "utf8"));
  const ajv = new Ajv2020({strict:false}); ajv.addSchema(schema);
  const playback = ajv.compile({$ref: `${schema.$id}#/$defs/playbackResolution`});
  assert.ok(playback({type:"playable",url:"https://example.com/audio",format:"wav"}));
  for (const extra of [{format:"hls"},{format:"DaSh"},{drm:{}},{licenseUrl:"https://example.com/license"}])
    assert.equal(playback({type:"playable",url:"https://example.com/audio",...extra}),false);
  for (const kind of ["playlist","album","artist","ranking"]) {
    const validate = ajv.compile({$ref:`${schema.$id}#/$defs/${kind}`});
    assert.equal(validate({id:"a",title:"Title",name:"Name",artists:[],artworkUrl:123}),false);
  }
});
