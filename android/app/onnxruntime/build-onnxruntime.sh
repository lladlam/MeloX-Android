#!/usr/bin/env bash
# Builds a size-reduced ONNX Runtime for Android from the GitHub source tag.
#
# Upstream does not publish Android artifacts on GitHub Releases (the release
# assets only cover Linux/macOS/Windows), so the Android runtime is compiled
# from the official git tag instead of being resolved as a Maven artifact.
# The operator set is reduced to the kernels the AutoSync catalog actually uses
# (see reduced_ops_config.txt), which removes ~30 MiB per ABI from the APK.
#
# Requirements:
#   - git, cmake >= 3.28, ninja
#   - python3 with the `onnx` and `flatbuffers` packages (ONNX Runtime's build
#     tooling imports `flatbuffers` when the operator list is reduced)
#   - Android NDK (default: 29.0.14206865) and an Android SDK
#
# Usage:
#   ORT_SRC_DIR=/path/to/onnxruntime ./app/onnxruntime/build-onnxruntime.sh
#
# Set ORT_PYTHON to use a specific interpreter, for example a virtualenv that
# has the packages above:
#   ORT_PYTHON=/path/to/venv/bin/python3 ./app/onnxruntime/build-onnxruntime.sh
#
# The resulting libraries are staged in app/onnxruntime/prebuilt/<abi>/ and are
# picked up by the Gradle build (see app/build.gradle.kts).

set -euo pipefail

ORT_VERSION="${ORT_VERSION:-1.31.0}"
ORT_TAG="v${ORT_VERSION}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ANDROID_DIR="$(cd "${APP_DIR}/.." && pwd)"

SRC_DIR="${ORT_SRC_DIR:-${ORT_BUILD_ROOT:-/mnt/ntfs_files_sda3/ort-build}/onnxruntime}"
BUILD_ROOT="${ORT_BUILD_ROOT:-/mnt/ntfs_files_sda3/ort-build}/build-${ORT_TAG}"
PREBUILT_DIR="${SCRIPT_DIR}/prebuilt"

ANDROID_SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/mnt/ntfs_files_sda4/android-sdk}}"
ANDROID_NDK_PATH="${ANDROID_NDK_HOME:-${ANDROID_SDK_PATH}/ndk/29.0.14206865}"
JOBS="${ORT_BUILD_JOBS:-3}"
ABIS="${ORT_BUILD_ABIS:-arm64-v8a armeabi-v7a}"

CONFIG_FILE="${SCRIPT_DIR}/reduced_ops_config.txt"

# ONNX Runtime's build tooling is executed with the `python3` found on PATH,
# so a specific interpreter can be selected through ORT_PYTHON.
PYTHON_BIN="${ORT_PYTHON:-python3}"
if [[ -x "${PYTHON_BIN}" ]]; then
  PYTHON_DIR="$(cd "$(dirname "${PYTHON_BIN}")" && pwd)"
  export PATH="${PYTHON_DIR}:${PATH}"
fi

if [[ ! -d "${SRC_DIR}" ]]; then
  echo "ONNX Runtime sources not found at ${SRC_DIR}." >&2
  echo "Clone them first:" >&2
  echo "  git clone --depth 1 --branch ${ORT_TAG} --recurse-submodules --shallow-submodules \\" >&2
  echo "    https://github.com/microsoft/onnxruntime.git ${SRC_DIR}" >&2
  exit 1
fi

if [[ ! -d "${ANDROID_NDK_PATH}" ]]; then
  echo "Android NDK not found at ${ANDROID_NDK_PATH} (set ANDROID_NDK_HOME)." >&2
  exit 1
fi

cd "${SRC_DIR}"

for ABI in ${ABIS}; do
  echo "==> Building ONNX Runtime ${ORT_TAG} for ${ABI}"
  # --use_nnapi keeps OrtSessionOptionsAppendExecutionProvider_Nnapi exported:
  # the JNI library imports that symbol and the linker resolves it eagerly.
  # --target onnxruntime skips the unit test targets, which do not compile
  # with the reduced operator set.
  ./build.sh \
    --config Release \
    --build_dir "${BUILD_ROOT}/${ABI}" \
    --android \
    --android_abi "${ABI}" \
    --android_api 24 \
    --android_sdk_path "${ANDROID_SDK_PATH}" \
    --android_ndk_path "${ANDROID_NDK_PATH}" \
    --use_nnapi \
    --nnapi_min_api 27 \
    --build_shared_lib \
    --skip_tests \
    --skip_pip_install \
    --target onnxruntime \
    --parallel "${JOBS}" \
    --cmake_generator Ninja \
    --include_ops_by_config "${CONFIG_FILE}" \
    --disable_ml_ops
done

for ABI in ${ABIS}; do
  SOURCE="${BUILD_ROOT}/${ABI}/Release/libonnxruntime.so"
  if [[ ! -f "${SOURCE}" ]]; then
    echo "expected library missing: ${SOURCE}" >&2
    exit 1
  fi
  mkdir -p "${PREBUILT_DIR}/${ABI}"
  cp -f "${SOURCE}" "${PREBUILT_DIR}/${ABI}/libonnxruntime.so"
  echo "staged ${PREBUILT_DIR}/${ABI}/libonnxruntime.so ($(stat -c %s "${PREBUILT_DIR}/${ABI}/libonnxruntime.so") bytes)"
done

echo
echo "Done. Rebuild the app so Gradle picks up the reduced runtime:"
echo "  cd ${REPO_ANDROID_DIR} && ./gradlew :app:assembleRelease"
