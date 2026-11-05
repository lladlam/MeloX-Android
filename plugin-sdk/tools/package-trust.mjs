import { createHash, createPrivateKey, createPublicKey, sign, verify } from "node:crypto";

export const packageDomain = Buffer.from("MeloX-package-v1\n", "ascii");
export const sha256 = bytes => createHash("sha256").update(bytes).digest("hex");

/** Envelope v1 covers every payload entry, including the exact manifest bytes. */
export function packageEntries(payload, privateKeyPem) {
  if (payload.some(([name]) => ["integrity.json", "publisher.json"].includes(name))) throw new Error("Reserved package path");
  const entries = payload.map(([name, data]) => [name, Buffer.from(data)]);
  const integrity = Buffer.from(JSON.stringify({version: 1, files: Object.fromEntries(
    [...entries].sort(([a], [b]) => a.localeCompare(b, "en")).map(([name, data]) => [name, sha256(data)]))}));
  entries.push(["integrity.json", integrity]);
  if (privateKeyPem) {
    const key = createPrivateKey(privateKeyPem);
    if (key.asymmetricKeyType !== "rsa" || key.asymmetricKeyDetails.modulusLength < 2048 || key.asymmetricKeyDetails.modulusLength > 4096)
      throw new Error("Publisher key must be RSA 2048–4096 bits");
    const publicKey = createPublicKey(key).export({type: "spki", format: "der"});
    const signature = sign("sha256", Buffer.concat([packageDomain, integrity]), key);
    if (!verify("sha256", Buffer.concat([packageDomain, integrity]), createPublicKey(key), signature)) throw new Error("Signature self-check failed");
    entries.push(["publisher.json", Buffer.from(JSON.stringify({algorithm: "RSA-SHA256", publicKey: publicKey.toString("base64"), signature: signature.toString("base64")}))]);
  }
  return entries;
}
