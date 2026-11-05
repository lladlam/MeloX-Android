#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { build } from "esbuild";
import { validateManifest } from "./manifest.mjs";
import { createZip } from "./zip.mjs";
import { packageEntries } from "./package-trust.mjs";

const [command, manifestPath, source, output] = process.argv.slice(2);
const options = process.argv.slice(6);
try {
  if (!manifestPath || !["validate", "pack"].includes(command)) {
    throw new Error("Usage: melox-plugin validate manifest.json | pack manifest.json entry.ts output.melox-plugin");
  }
  const manifest = validateManifest(JSON.parse(await readFile(manifestPath, "utf8")));
  if (command === "pack") {
    if (!source || !output) throw new Error("pack requires entry source and output path");
    const result = await build({ entryPoints: [resolve(source)], absWorkingDir: dirname(resolve(manifestPath)),
      bundle: true, write: false, format: "iife", globalName: "__meloxModule", platform: "browser", target: "es2020",
      footer: { js: "globalThis.meloxPlugin = __meloxModule.default;" } });
    if (result.outputFiles[0].contents.length > 128 * 1024) throw new Error("Bundled script exceeds Android's 128 KiB limit");
    if (options.length && (options.length !== 2 || options[0] !== "--sign-key")) throw new Error("Expected --sign-key private.pem");
    const privateKey = options.length ? await readFile(options[1]) : undefined;
    const bytes = createZip(packageEntries([["manifest.json", JSON.stringify(manifest)], [manifest.entry, result.outputFiles[0].contents]], privateKey));
    if (bytes.length > 4 * 1024 * 1024) throw new Error("Plugin package exceeds host limit");
    await writeFile(output, bytes, { flag: "wx" });
    console.log(`Packed ${manifest.id}@${manifest.version}: ${output}`);
  } else console.log(`Valid: ${manifest.id}@${manifest.version}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
