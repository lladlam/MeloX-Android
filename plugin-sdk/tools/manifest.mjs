import Ajv2020 from "ajv/dist/2020.js";
import { readFileSync } from "node:fs";

const schema = JSON.parse(readFileSync(new URL("../schema/manifest.schema.json", import.meta.url), "utf8"));
const ajv = new Ajv2020({ allErrors: true });
ajv.addKeyword("x-capability-methods");
const validateSchema = ajv.compile(schema);
const segment = /^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}$/;
const version = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/;

export function validateManifest(value) {
  if (["manifest.json", "integrity.json", "publisher.json"].includes(value.entry)) throw new Error("Reserved plugin entry");
  if (value.homepage !== undefined) {
    const url = new URL(value.homepage);
    if (url.protocol !== "https:" || url.username || url.password || !url.hostname) throw new Error("Invalid plugin homepage");
  }
  if (!validateSchema(value)) throw new Error(`Invalid manifest: ${JSON.stringify(validateSchema.errors)}`);
  if (Buffer.byteLength(JSON.stringify(value)) > 32768) throw new Error("Manifest too large");
  if (/[^a-z0-9.-]/.test(value.id)) throw new Error("Invalid plugin ID");
  if (value.name.trim().length === 0 || value.name.trim().length > 120) throw new Error("Invalid name");
  const match = version.exec(value.version);
  if (!match || match[0] !== value.version || (match[4] ?? "").split(".").some(x => /^0[0-9]+$/.test(x))) throw new Error("Invalid semantic version");
  if (!value.entry.endsWith(".js") || !value.entry.split("/").every(x => x !== "." && x !== ".." && segment.exec(x)?.[0] === x)) throw new Error("Invalid entry");
  for (const host of value.networkDomains ?? []) {
    if (host.length > 253 || /[^a-z0-9.-]/.test(host) || !host.includes(".") || !/[a-z]/.test(host) ||
        !host.split(".").every(label => label.length <= 63 && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label))) {
      throw new Error("Invalid exact ASCII hostname");
    }
  }
  if (value.authentication?.type === "apiToken") {
    const auth = value.authentication;
    if (["host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection", "upgrade", "cookie"].includes(auth.header.toLowerCase()) ||
        /[^A-Za-z0-9-]/.test(auth.header) || /[^ -~]/.test(auth.prefix) ||
        !auth.domains.every(host => (value.networkDomains ?? []).includes(host))) throw new Error("Invalid credential policy");
  }
  if (value.authentication?.type === "oauth2Pkce") {
    const auth = value.authentication;
    if (["clientSecret", "client_secret", "redirectUri", "grantType"].some(key => key in auth)) throw new Error("Unsupported OAuth option");
    for (const raw of [auth.authorizationEndpoint, auth.tokenEndpoint]) {
      const url = new URL(raw);
      if (url.protocol !== "https:" || (url.port && url.port !== "443") || url.username || url.password || url.search || url.hash ||
          !(value.networkDomains ?? []).includes(url.hostname)) throw new Error("Invalid OAuth endpoint");
    }
    if (!auth.domains.every(host => (value.networkDomains ?? []).includes(host)) || auth.scopes.some(scope => /["\\]/.test(scope))) throw new Error("Invalid OAuth policy");
  }
  return value;
}
