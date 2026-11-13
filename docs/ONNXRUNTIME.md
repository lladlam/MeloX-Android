# ONNX Runtime in MeloX Android

MeloX runs word-level lyric alignment (AutoSync) fully on-device with ONNX
Runtime. The runtime is the single largest native component of the APK, so the
way it is obtained and packaged matters for both install size and supply
chain hygiene.

## How the runtime is obtained

Upstream `microsoft/onnxruntime` publishes **no Android artifact on GitHub
Releases** — the release assets only cover Linux, macOS and Windows. The
Android AAR is distributed through Maven Central only. Because of that, the
Android build does not resolve the runtime as a transitive Maven dependency
anymore. Instead `app/build.gradle.kts` defines a `downloadOnnxRuntime` task
that:

1. downloads a pinned archive (SHA-256 verified before it is used),
2. extracts `classes.jar` for the Java API, and
3. stages the native libraries into `app/build/onnxruntime/jniLibs/<abi>/`.

The archive is cached in `$GRADLE_USER_HOME/melox/onnxruntime/`, so repeated
builds do not re-download it.

Only `classes.jar` is taken from the archive, so the manifest of the upstream
AAR is not merged any more. That manifest registers the
`ai.onnxruntime.TelemetryInitializer` content provider, which loads the native
library at process start and starts the ONNX Runtime telemetry client (it
collects the Android ID, locale, time zone, battery and connectivity state).
Without the provider the telemetry client is never created, while the native
library is still loaded on first use by the Java API
(`ai.onnxruntime.OnnxRuntime` loads it in its static initialiser).

### Pointing the build at a GitHub release asset

The URL and its checksum are build properties, so a GitHub-hosted artifact
(for example a custom runtime published by the project) can be used without
touching the build script:

```bash
./gradlew :app:assembleRelease \
  -PmeloxOnnxRuntimeUrl="https://github.com/<owner>/<repo>/releases/download/<tag>/<file>" \
  -PmeloxOnnxRuntimeSha256="<sha256>"
```

If the checksum does not match, the build fails instead of packaging an
unverified binary.

The reduced libraries produced by `app/onnxruntime/build-onnxruntime.sh` are
build artefacts and are not committed (they are ignored by Git). For
reproducible builds, publish them as a GitHub release asset and point the
Gradle properties at that asset; until then, a clean checkout builds with the
verified upstream runtime from Maven Central.

## Reduced runtime

Shipping the upstream runtime means roughly 32.6 MiB (arm64) of native code in
every APK. MeloX therefore builds a reduced runtime from the upstream Git tag
with only the kernels the AutoSync catalog actually uses:

```bash
ORT_SRC_DIR=/path/to/onnxruntime ./app/onnxruntime/build-onnxruntime.sh
```

The script

* compiles `v1.31.0` from the GitHub source tag (Android NDK, API 24),
* passes `app/onnxruntime/reduced_ops_config.txt` to
  `--include_ops_by_config`, and
* stages the result in `app/onnxruntime/prebuilt/<abi>/libonnxruntime.so`,
  which the Gradle task prefers over the library inside the downloaded
  archive.

`app/onnxruntime/reduced_ops_config.txt` is generated with ONNX Runtime's own
tool from the models published by the AutoSync catalog:

```bash
python tools/python/create_reduced_build_config.py <model-dir> reduced_ops_config.txt
```

Regenerate it whenever the catalog publishes new models, otherwise the
reduced runtime will not contain the kernels the new models need.

### Measured result

| artifact | upstream | reduced | delta |
| --- | --- | --- | --- |
| `lib/arm64-v8a/libonnxruntime.so` | 34,208,680 B (32.63 MiB) | 16,206,208 B (15.46 MiB) | −17.18 MiB |
| `lib/armeabi-v7a/libonnxruntime.so` | 24,182,012 B (23.06 MiB) | 10,446,340 B (9.96 MiB) | −13.09 MiB |

Native libraries are stored uncompressed in modern APKs, so the reduction is
visible one-for-one in the download size.

Release APKs built with the reduced runtime and without the bundled MiLan
font (ABI splits, `assembleRelease`):

| APK | size |
| --- | --- |
| `app-arm64-v8a-release.apk` | 32,473,174 B (30.97 MiB) |
| `app-armeabi-v7a-release.apk` | 25,489,628 B (24.31 MiB) |

The arm64 release APK was 64,748,270 B (61.75 MiB) before this work, so the
combined runtime reduction and font removal shrink it by 32,275,096 B
(−30.78 MiB, −49.9 %). The remaining large entries are `classes.dex`
(5.31 MiB compressed), the SF Symbols font `res/TI.ttf` (3.04 MiB compressed)
and the Japanese G2P library (1.83 MiB).

### Verification performed

* `llvm-readelf` on both reduced libraries: `OrtGetApiBase`,
  `OrtSessionOptionsAppendExecutionProvider_CPU` and
  `OrtSessionOptionsAppendExecutionProvider_Nnapi` are exported with the
  `VERS_1.31.0` tag the upstream JNI library imports, and the only `NEEDED`
  entries are `libdl`, `liblog`, `libm` and `libc` (libc++ is linked
  statically).
* Every `Ort*` symbol the upstream `libonnxruntime4j_jni.so` imports is
  present in both reduced libraries.
* `app/onnxruntime/verify-runtime.cpp` loads each published AutoSync model
  (English, Japanese, Chinese) with `ORT_ENABLE_ALL` and runs one inference
  pass against a host build of the same reduced operator set:

  ```
  OK   en-wav2vec2-base-960h-int8.onnx inputs=1 outputs=2
  OK   ja-hubert-phoneme-ctc-int8.onnx inputs=1 outputs=2
  OK   zh-fa-zh-int8.onnx inputs=2 outputs=2
  ```

  The host build uses the same `reduced_ops_config.txt` as the Android build.
  The reduced runtime needs the `com.microsoft` kernels that the graph
  optimizers create while the session is built (`SkipLayerNormalization`,
  `FusedMatMul`, `Gelu`, `DynamicQuantizeMatMul`,
  `MatMulIntegerToFloat`); a reduced runtime without
  `com.microsoft.SkipLayerNormalization` fails to create a session for all
  three models, which is why the operator list has to be re-checked whenever
  the models change.

A real device run is still required before shipping: the reduced runtime has
not been exercised on a physical phone, and the models themselves are still
downloaded on demand.

### Constraints

* The JNI library (`libonnxruntime4j_jni.so`) is taken from the verified
  upstream archive. It imports `OrtGetApiBase`,
  `OrtSessionOptionsAppendExecutionProvider_CPU` and
  `OrtSessionOptionsAppendExecutionProvider_Nnapi` with eager binding, so the
  reduced runtime has to be built with `--use_nnapi` (see the build script).
* The reduced runtime only supports the operators listed in the config. A
  model that uses anything else fails to load, so the config must be kept in
  sync with the AutoSync catalog. The list covers the operators of the models
  **and** the `com.microsoft` kernels the graph optimizers introduce when a
  session is created with `ORT_ENABLE_ALL` (for example
  `com.microsoft.SkipLayerNormalization`, which is created by the layer norm
  fusion and is not present in the model file).
* The upstream `onnxruntime-mobile` package (6.9 MiB AAR) is **not** usable
  here: it ships no `ai.onnx::LSTM` kernel, which the Chinese alignment model
  needs, its documented operator coverage stops at opset 15 while the shipped
  models import opset 17, and the package stopped at 1.18.0 (December 2023).

## Runtime on-demand loading is not possible with the upstream Java API

The upstream Java binding loads the native library with
`System.loadLibrary("onnxruntime")` on Android and ignores the
`onnxruntime.native.path` system property (that property is only honoured on
desktop platforms). There is therefore no supported way to fetch
`libonnxruntime.so` at runtime and load it from the app-private directory,
which is why the runtime stays packaged in the APK.
