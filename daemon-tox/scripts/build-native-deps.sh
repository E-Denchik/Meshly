#!/usr/bin/env bash
#
# Copyright (C) 2026 The Meshly Project Authors
#
# This file is part of Meshly, a decentralized peer-to-peer messenger
# built on top of Tox (c-toxcore + ToxAV).
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Cross-compiles libsodium/opus/libvpx for arm64-v8a and stages the results into
# daemon-tox/deps/arm64-v8a/{include,lib,lib/pkgconfig} - the tree
# daemon-tox/CMakeLists.txt's PKG_CONFIG_LIBDIR wiring expects so c-toxcore's own
# pkg-config-based dependency discovery (cmake/Dependencies.cmake) resolves all three.
# See README.md's "Building" section for the reasoning behind each step.
#
# Usage: ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018 ./build-native-deps.sh [--force]
#
# Idempotent by default: skips a library that already has a staged .pc file. Pass
# --force to rebuild everything from scratch (e.g. after bumping a pinned version below).

set -euo pipefail

ABI="arm64-v8a"
NDK_PLATFORM_LEVEL="24"
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "ANDROID_NDK_HOME must be set (e.g. \$ANDROID_HOME/ndk/27.2.12479018)." >&2
  exit 1
fi
if [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "ANDROID_NDK_HOME ($ANDROID_NDK_HOME) does not exist." >&2
  exit 1
fi

for tool in git cmake ninja pkg-config autoconf automake libtoolize; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Required tool not found: $tool" >&2; exit 1; }
done

# Pinned upstream release tags - bump deliberately, not automatically, so a CI run
# doesn't silently start building a different (possibly broken) upstream version.
LIBSODIUM_TAG="1.0.22"
OPUS_TAG="v1.5.2"
LIBVPX_TAG="v1.16.0"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DAEMON_TOX_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPS_DIR="$DAEMON_TOX_DIR/deps/$ABI"
BUILD_ROOT="$DAEMON_TOX_DIR/deps/.build/$ABI"

mkdir -p "$DEPS_DIR/include" "$DEPS_DIR/lib/pkgconfig" "$BUILD_ROOT"

TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "$NDK_BIN" ]; then
  # Non-Linux hosts (macOS) use a differently-named prebuilt dir; this script has only
  # been run/verified on Linux, so fail loudly rather than guess.
  echo "Expected NDK toolchain dir not found at $NDK_BIN (only linux-x86_64 hosts verified)." >&2
  exit 1
fi

echo "=== Building native deps for $ABI into $DEPS_DIR ==="

# --- libsodium ---------------------------------------------------------------------
if [ "$FORCE" = "1" ] || [ ! -f "$DEPS_DIR/lib/libsodium.a" ]; then
  echo "--- libsodium $LIBSODIUM_TAG ---"
  SODIUM_SRC="$BUILD_ROOT/libsodium"
  if [ ! -d "$SODIUM_SRC" ]; then
    git clone --branch "$LIBSODIUM_TAG" --depth 1 https://github.com/jedisct1/libsodium.git "$SODIUM_SRC"
  fi
  (
    cd "$SODIUM_SRC"
    ./autogen.sh -s
    export ANDROID_NDK_HOME
    export NDK_PLATFORM="android-$NDK_PLATFORM_LEVEL"
    export LIBSODIUM_FULL_BUILD=1
    ./dist-build/android-armv8-a.sh
  )
  SODIUM_OUT="$SODIUM_SRC/libsodium-android-armv8-a+crypto"
  cp -a "$SODIUM_OUT/include/." "$DEPS_DIR/include/"
  cp -a "$SODIUM_OUT/lib/." "$DEPS_DIR/lib/"
  # dist-build/android-build.sh bakes its own scratch PREFIX (inside deps/.build/,
  # gitignored/not cached) into libsodium.pc's `prefix=` line - rewrite it to point at
  # the final staged location, or pkg-config resolves a lib/include path that doesn't
  # exist once the scratch build tree is gone (e.g. on a CI cache-hit run, which skips
  # rebuilding the scratch tree entirely).
  sed -i "s#^prefix=.*#prefix=$DEPS_DIR#" "$DEPS_DIR/lib/pkgconfig/libsodium.pc"
  # Both a .a and a .so get produced; keep only the .a so the final link is static -
  # otherwise the linker prefers the .so and the shipped daemon-tox .so ends up
  # depending on a libsodium.so that isn't bundled and isn't guaranteed on-device.
  rm -f "$DEPS_DIR/lib/libsodium.so" "$DEPS_DIR/lib"/libsodium.so.* "$DEPS_DIR/lib/libsodium.la"
else
  echo "--- libsodium already staged, skipping (use --force to rebuild) ---"
fi

# --- opus ----------------------------------------------------------------------------
if [ "$FORCE" = "1" ] || [ ! -f "$DEPS_DIR/lib/libopus.a" ]; then
  echo "--- opus $OPUS_TAG ---"
  OPUS_SRC="$BUILD_ROOT/opus"
  if [ ! -d "$OPUS_SRC" ]; then
    git clone --branch "$OPUS_TAG" --depth 1 https://github.com/xiph/opus.git "$OPUS_SRC"
  fi
  cmake -S "$OPUS_SRC" -B "$OPUS_SRC/build-$ABI" -GNinja \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$NDK_PLATFORM_LEVEL" \
    -DCMAKE_INSTALL_PREFIX="$DEPS_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DOPUS_BUILD_TESTING=OFF \
    -DOPUS_BUILD_PROGRAMS=OFF
  cmake --build "$OPUS_SRC/build-$ABI"
  cmake --install "$OPUS_SRC/build-$ABI"
else
  echo "--- opus already staged, skipping (use --force to rebuild) ---"
fi

# --- libvpx ----------------------------------------------------------------------------
if [ "$FORCE" = "1" ] || [ ! -f "$DEPS_DIR/lib/libvpx.a" ]; then
  echo "--- libvpx $LIBVPX_TAG ---"
  VPX_SRC="$BUILD_ROOT/libvpx"
  if [ ! -d "$VPX_SRC" ]; then
    git clone --branch "$LIBVPX_TAG" --depth 1 https://github.com/webmproject/libvpx.git "$VPX_SRC"
  fi
  VPX_BUILD="$VPX_SRC/build-$ABI"
  mkdir -p "$VPX_BUILD"
  (
    cd "$VPX_BUILD"
    export CC="$NDK_BIN/aarch64-linux-android${NDK_PLATFORM_LEVEL}-clang"
    export CXX="$NDK_BIN/aarch64-linux-android${NDK_PLATFORM_LEVEL}-clang++"
    export AR="$NDK_BIN/llvm-ar"
    export AS="$CC"
    export LD="$CC"
    export NM="$NDK_BIN/llvm-nm"
    export STRIP="$NDK_BIN/llvm-strip"
    export RANLIB="$NDK_BIN/llvm-ranlib"
    "$VPX_SRC/configure" \
      --target=arm64-android-gcc \
      --prefix="$DEPS_DIR" \
      --disable-examples \
      --disable-docs \
      --disable-unit-tests \
      --disable-tools \
      --enable-pic \
      --enable-vp8 \
      --enable-vp9
    make -j"$(nproc)"
    make install
  )
else
  echo "--- libvpx already staged, skipping (use --force to rebuild) ---"
fi

echo "=== Verifying pkg-config resolution ==="
PKG_CONFIG_LIBDIR="$DEPS_DIR/lib/pkgconfig" PKG_CONFIG_PATH="" pkg-config --print-errors --exists libsodium opus vpx
echo "OK: libsodium, opus, and vpx all resolve via pkg-config from $DEPS_DIR/lib/pkgconfig"
