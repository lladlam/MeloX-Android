import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import Ajv2020 from 'ajv/dist/2020.js';

const fixture = JSON.parse(readFileSync(new URL('./fixtures/wire-cases.json', import.meta.url)));
const schema = JSON.parse(readFileSync(new URL('../schema/protocol-v1.schema.json', import.meta.url)));
const models = fixture.models;
const page = item => ({items:[structuredClone(item)], page:1, pageSize:10, total:1, hasMore:false});
const values = {...models, searchPage:page(models.track), playlistPage:page(models.playlist), albumPage:page(models.album),
  artistPage:page(models.artist), rankingPage:page(models.ranking),
  playlistDetail:{summary:models.playlist,tracks:page(models.track)}, albumDetail:{summary:models.album,tracks:page(models.track)},
  artistDetail:{summary:models.artist,tracks:page(models.track)},
  homeFeed:{recommendedPlaylists:[models.playlist],newSongs:[models.track],rankings:[models.ranking]},
  accountSummary:models.account, authenticationStatus:{type:'signedIn',account:models.account}, favoriteState:true,writeAcknowledgement:null};

function* leaves(value, path=[]) {
  if (value === null || typeof value !== 'object') { yield [path,value]; return; }
  for (const [key, child] of Object.entries(value)) {
    yield [[...path,key],child];
    if (child && typeof child === 'object') yield* leavesChildren(child,[...path,key]);
  }
}
function* leavesChildren(value,path) {
  for (const [key,child] of Object.entries(value)) {
    yield [[...path,key],child];
    if (child && typeof child === 'object') yield* leavesChildren(child,[...path,key]);
  }
}
const kind = value => value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value;
test('every declared method and populated response field rejects wrong JSON types', () => {
  const ajv = new Ajv2020({strict:false}); ajv.addSchema(schema);
  for (const [method,def] of Object.entries(fixture.methods)) {
    const validate = ajv.compile({$ref:`${schema.$id}#/$defs/${def}`});
    assert.ok(validate(values[def]), `${method}: ${JSON.stringify(validate.errors)}`);
    for (const [path,original] of leaves(values[def])) {
      for (const wrong of [null,'wrong',123,true,{},[]]) {
        if (kind(wrong) === kind(original)) continue;
        const mutated=structuredClone(values[def]);
        let target=mutated;
        for (const key of path.slice(0,-1)) target=target[key];
        if (!path.length) { assert.equal(validate(wrong),false,method); continue; }
        target[path.at(-1)]=wrong;
        assert.equal(validate(mutated),false,`${method}.${path.join('.')}: ${kind(wrong)}`);
      }
    }
  }
});

test('fixture covers exactly all capability-required method names', () => {
  const manifestSchema = JSON.parse(readFileSync(new URL('../schema/manifest.schema.json', import.meta.url)));
  assert.deepEqual([...new Set(Object.values(manifestSchema['x-capability-methods']).flat())].sort(),Object.keys(fixture.methods).sort());
});
