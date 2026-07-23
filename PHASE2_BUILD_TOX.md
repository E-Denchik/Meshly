# Phase 2: Real c-toxcore/ToxAV Native Integration

Status: **infrastructure only, not yet built or compiled**. This document exists so that
whoever picks this up can run the actual native build without having to rediscover
c-toxcore's build system from scratch.

## Why this wasn't compiled yet

This is a much smaller lift than the jami-daemon Phase 2 scaffold this replaces, but it
still wasn't compiled in this pass -- no NDK-cross-compiled `libsodium`/`opus`/`libvpx`
were available on this machine, and c-toxcore's own CMake build resolves those three via
`pkg-config`/`find_package` (`cmake/Dependencies.cmake`, confirmed by reading it), not by
vendoring or `FetchContent`-ing them itself. Someone still needs to produce
Android-target builds of those three libraries (or point CMake's `find_package` machinery
at prebuilt ones) before `daemon-tox`'s CMake configure step will succeed. That's real,
separate work, just a much smaller amount of it than jami-daemon's Phase 2 needed.

Compare the two dependency footprints directly:

- **jami-daemon** (removed): compiles its own `contrib/` tree from source -- OpenDHT,
  PJSIP, GnuTLS, FFmpeg, jsoncpp, libgit2, secp256k1, restinio, ~30 libraries total, one
  full autotools/meson build per ABI. Estimated 30-50GB disk, several hours of CPU time,
  per ABI.
- **c-toxcore** (this scaffold): depends on `libsodium` (required, for `toxcore` itself)
  plus `opus` and `libvpx` (required only if `BUILD_TOXAV=On`, the default -- see
  `native/upstream/c-toxcore/CMakeLists.txt` lines 172, 200-209). That's it -- no bundled
  contrib tree, no autotools/meson bootstrapping of dozens of dependencies. Building
  those three libraries for Android plus c-toxcore itself realistically needs **low
  single-digit GB of disk and well under an hour of CPU time per ABI** on a normal dev
  machine -- an order of magnitude less than jami-daemon's Phase 2 estimate, in both
  dimensions.

Tools needed: Android NDK (r27.2.12479018, matching what the removed `:daemon` module
pinned -- see `daemon-tox/build.gradle.kts`'s comment on why this wasn't independently
re-verified against this machine's installed NDK), CMake >= 3.22 (c-toxcore's own
`cmake_minimum_required` is 3.16, `daemon-tox/CMakeLists.txt` requires 3.22.1 to match the
Gradle-side `externalNativeBuild.cmake.version` pin). **No SWIG or any other binding
generator is needed** -- this is the single biggest structural difference from the
removed jami-daemon Phase 2 scaffold. jami-daemon generates its whole Java/JNI surface
(`net.jami.daemon.*`) via SWIG as part of its own CMake build (`JAMI_JNI=On`); c-toxcore
is a plain C library with no concept of JNI or Java at all. Every JNI entry point in this
scaffold (`daemon-tox/src/main/cpp/tox_jni.c`) is hand-written against the real `tox_*`/
`toxav_*` C functions, and every Kotlin `external fun` in `ToxNative.kt` is hand-written to
match. This trades SWIG's "run a code generator and get 100% API coverage for free" for
"only what's been hand-wrapped so far exists" -- see "What's already in place" below for
exactly how much of the real API surface that currently covers (a representative slice,
not the whole thing).

## What's already in place

- `native/upstream/c-toxcore/` -- full clone (not shallow; the clone came out to ~7MB, so
  there was no need to bother with `--depth=1` the way the much larger jami-daemon
  checkout needed) of https://github.com/TokTok/c-toxcore, pinned via a normal git
  submodule. Used purely as a build/reference source. Commit
  `1d79022fb4e56dffe0bbd075d47e00f7a0b62ab3` (2026-06-20) at the time this was written.
- `daemon-tox/` -- a Gradle Android library module, namespace `org.meshly.app.daemontox`,
  `compileSdk = 35` (confirmed matching `app/build.gradle.kts`), `ndkVersion =
  "27.2.12479018"` (carried over from the removed `:daemon` module -- `:app` itself
  doesn't pin an NDK version, see `daemon-tox/build.gradle.kts`'s comment). **Not included
  in `settings.gradle.kts`, and `:app` does not depend on it** -- deliberate, so the
  existing Phase 1 mock build (`./gradlew assembleDebug`) is completely unaffected by
  anything under `daemon-tox/`. `./gradlew :app:tasks` still evaluates cleanly with this
  module present but unwired (verified in this pass).
  - `daemon-tox/CMakeLists.txt` -- **written for this scaffold**, unlike the removed
    `:daemon` module which pointed straight at jami-daemon's own `CMakeLists.txt`.
    `add_subdirectory()`s `native/upstream/c-toxcore` and links a hand-written JNI shared
    library (`toxcore-jni`) against the `toxcore_static` target it produces. See its
    inline comments for exactly which CMake target names were confirmed by reading
    c-toxcore's own `CMakeLists.txt`, and what's still open (the libsodium/opus/libvpx
    resolution question above).
  - `daemon-tox/src/main/cpp/tox_jni.c` -- hand-written JNI glue, NOT SWIG-generated.
    Wraps a representative slice of `tox.h`'s account-lifecycle/bootstrap/friend-
    management/messaging functions (`tox_new`, `tox_kill`, `tox_bootstrap`,
    `tox_self_get_address`, `tox_iteration_interval`, `tox_iterate`, `tox_friend_add`,
    `tox_friend_delete`, `tox_friend_send_message`, `tox_friend_get_connection_status`,
    `tox_self_get_connection_status`) -- 11 functions total. ToxAV wrapper functions
    (`toxav_new`/`toxav_call`/`toxav_answer`/`toxav_call_control`/
    `toxav_audio_send_frame`/`toxav_video_send_frame`/callback registration) are
    documented as following the identical pattern but not yet individually written --
    see the file's closing comment block.
  - `daemon-tox/src/main/java/org/meshly/app/daemontox/ToxNative.kt` -- `external fun`
    declarations matching `tox_jni.c`'s JNI entry points one-for-one, each KDoc citing the
    exact `tox.h` function name and line number read from the real checked-out submodule.
  - `ToxBridge.kt` -- singleton wrapping `ToxNative`, `init { System.loadLibrary
    ("toxcore-jni") }`, exposing a `SharedFlow<ToxDaemonEvent>`. Distinct from
    `org.meshly.app.core.ToxBridge` (the Phase 1 mock, in `:app`'s own package) -- lives in
    `org.meshly.app.daemontox` inside `:daemon-tox`, which `:app` never depends on, so
    there's no real import collision despite the shared simple name.
  - `ToxDaemonEvent.kt` -- sealed class covering the real callback signals this scaffold
    wraps: self/friend connection status, friend request/message/read-receipt, friend
    name/status-message/user-status/typing changes, and the ToxAV call lifecycle (call
    invite, call state bitmask, audio/video bit-rate suggestions, audio/video frame
    receipt). Each variant's KDoc cites the exact `tox_*_cb`/`toxav_*_cb` typedef and line
    number.
  - `ToxCallbackAdapter.kt` -- thin Kotlin-side receiver object with one method per
    `ToxDaemonEvent` variant, documented as the target of hand-written JNI `CallVoidMethod`
    dispatch (there is no SWIG director-class mechanism here -- see the file's doc for the
    full intended registration flow, which is described but NOT yet implemented in
    `tox_jni.c`).
  - `ToxFriendInfo.kt` / `ToxCallSession.kt` -- small value types for a friend's assembled
    state and a call's assembled state, respectively. Both note explicitly that, unlike
    jami-daemon's single "get everything" calls (`getContactDetails`/`getCallDetails`),
    Tox/ToxAV have no equivalent -- these are client-side aggregations of multiple
    separate calls/callbacks, not direct 1:1 wire-format mappings.

None of this compiles right now against a real NDK toolchain -- `daemon-tox/CMakeLists.txt`
needs a working Android cross-compile of `libsodium`/`opus`/`libvpx` visible to
c-toxcore's `find_package`/`pkg-config` lookups before its `add_subdirectory()` call will
even configure, let alone build. That's fine: since `daemon-tox` isn't in
`settings.gradle.kts`, nothing tries to compile it yet.

## Known uncertainties

### Resolved (confirmed by reading the real checked-out `native/upstream/c-toxcore` submodule)

- **Single merged library, no separate `libtoxav`.** `INSTALL.md`'s component table states
  outright that although toxcore/toxav/toxencryptsave are conceptually separate
  libraries, "at the moment, when building the libraries, they are all merged into a
  single `toxcore` library." Confirmed independently in `CMakeLists.txt`: `toxav/*.c` is
  appended to the same `toxcore_SOURCES` list under `if(BUILD_TOXAV)` (lines 424-457).
- **Real CMake target names**: `toxcore_shared` / `toxcore_static` (CMakeLists.txt lines
  520/529), both with `OUTPUT_NAME` set to `toxcore` (so the *file* is
  `libtoxcore.so`/`.a`, but the CMake *target* to `target_link_libraries()` against is
  `toxcore_shared`/`toxcore_static`, not a target literally named `toxcore`).
  `ENABLE_SHARED`/`ENABLE_STATIC` both default `ON` (`cmake/ModulePackage.cmake` lines
  1-2). `daemon-tox/CMakeLists.txt` links `toxcore_static`.
- **Dependency resolution mechanism**: `cmake/Dependencies.cmake` uses
  `pkg_search_module(... IMPORTED_TARGET)` (pkg-config) with a `find_package` fallback for
  `libsodium` (line 23-29), and the same pattern for `opus`/`vpx` (lines 32-52) -- NOT
  `FetchContent`, NOT a vendored/bundled copy anywhere in the submodule. `vcpkg.json` at
  the repo root corroborates this: it lists `libsodium`, `libvpx`, `opus`, `pthreads` as
  external dependencies to be supplied by vcpkg (or, per `Dependencies.cmake`, by
  pkg-config/system `find_package`), not built in-tree.
- **`BUILD_TOXAV` default and hard requirements**: `option(BUILD_TOXAV ... ON)`
  (CMakeLists.txt line 172); if `OPUS_FOUND`/`VPX_FOUND` are false, the build silently
  turns `BUILD_TOXAV` back `OFF` unless `MUST_BUILD_TOXAV` is set, in which case it's a
  hard `SEND_ERROR` (lines 193-209) -- so ToxAV support is opt-in-by-default but silently
  droppable, not a hard failure by default.
- **License**: `native/upstream/c-toxcore/LICENSE` exists at the submodule's repo root and
  is the standard GPLv3 license text (confirmed: starts "GNU GENERAL PUBLIC LICENSE,
  Version 3, 29 June 2007", byte-identical in structure to this repo's own `LICENSE`).
  `toxcore/tox.h` and `toxav/toxav.h` both carry `SPDX-License-Identifier:
  GPL-3.0-or-later` headers (confirmed, line 1 of each file) -- "GPL-3.0-or-later", not a
  bare "GPLv3", is the accurate characterization to use.
- **Every `tox_*`/`toxav_*` function/typedef/constant cited in `ToxNative.kt`,
  `ToxDaemonEvent.kt`, `ToxFriendInfo.kt`, `ToxCallSession.kt`, and the table below** was
  read directly from `toxcore/tox.h` / `toxav/toxav.h` in the checked-out submodule, with
  line numbers recorded at the time of reading (commit
  `1d79022fb4e56dffe0bbd075d47e00f7a0b62ab3`). Line numbers will drift on future upstream
  commits; re-grep the real function names if they stop matching.

### Still open

- **Android cross-compilation of libsodium/opus/libvpx is entirely unaddressed.** Nothing
  in this pass built, vendored, or even located prebuilt Android `.so`/`.a` files or CMake
  package-config files for these three libraries. This is the actual blocking work before
  `daemon-tox`'s CMake configure step can succeed -- see "Steps to actually build" below
  for the realistic options.
- **`tox_options_new`/`Tox_Options` are not wrapped at all.** `ToxNative.toxNew()` always
  passes `NULL` (c-toxcore's documented "use default options" behavior). A real
  integration needs `tox_options.h`'s `Tox_Options` struct/`tox_options_new`/
  `tox_options_free`/setters (proxy config, IPv6, restoring from `savedata`) wrapped,
  which wasn't attempted in this pass.
- **Callback registration (`tox_callback_*`/`toxav_callback_*`) is documented but not
  implemented.** `ToxCallbackAdapter.kt`'s doc describes the intended
  `NewGlobalRef`/`CallVoidMethod` pattern in detail, but no actual
  `toxRegisterCallbacks`-style JNI function exists in `tox_jni.c` yet, and `ToxBridge.
  startDaemon()` has a `TODO` where that call would go. This is real, non-trivial JNI work
  (managing the global ref's lifetime correctly relative to `tox_kill`, caching
  `GetMethodID` results, handling `tox_iterate` calling back into Kotlin from whatever
  thread runs the iterate loop) that a future pass needs to actually do, not just design.
- **ToxAV (`toxav_*`) JNI wrappers don't exist yet**, only `tox_*` (core) ones do.
  `tox_jni.c`'s closing comment names exactly which `toxav_*` functions are needed and
  points at the confirmed `toxav.h` signatures already recorded in `ToxDaemonEvent.kt`'s
  citations and the API table below, but none are wrapped.
- **`Tox_Options` savedata round-tripping** (`tox_get_savedata`/`tox_get_savedata_size`,
  needed to persist an identity across app restarts, analogous to how a real jami-daemon
  account persists to disk on its own) isn't wrapped or even referenced anywhere in this
  scaffold yet.
- **Thread-safety / iterate-loop ownership** isn't scaffolded. `tox_iterate` must run
  repeatedly on some thread, and per tox.h's own threading doc, "no more than one API
  function can operate on a single instance at any given time" across threads -- `
  ToxBridge` doesn't yet have any locking/dispatcher around calls made concurrently with
  an active iterate loop. The removed jami-daemon scaffold didn't have this problem the
  same way since jami-daemon manages its own internal threading.
- **Group chats (`tox_group_*`, `toxcore/group*.c`) and conferences (`tox_conference_*`)
  are entirely out of scope** for this pass, matching Meshly's apparent 1:1-focused
  design (mirroring how the removed jami-daemon scaffold treated swarm *group*
  conversations as in-scope but didn't otherwise expand beyond messaging/calls) -- not
  wrapped, not cited in the API table below beyond incidental mentions.

## Steps to actually build (once you have the resources)

1. **Install the NDK and CMake** via the SDK manager:
   ```bash
   sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"
   ```

2. **Produce Android-target builds of `libsodium`, `opus`, and `libvpx`** (or locate
   already-built ones with correct CMake package-config/pkg-config files for your target
   ABI). Confirmed from `cmake/Dependencies.cmake`: c-toxcore's build does NOT fetch or
   vendor these itself -- it only calls `pkg_search_module`/`find_package` and expects to
   find them already on the system / in `CMAKE_PREFIX_PATH` / `PKG_CONFIG_PATH`. Realistic
   options, roughly in order of least-to-most effort:
   - Use a project that already ships Android builds of these three as AARs/prebuilt
     libraries with correct `.pc`/CMake config files (several exist in the wider Tox
     client ecosystem) and point `PKG_CONFIG_PATH`/`CMAKE_PREFIX_PATH` at them.
   - Cross-compile each of the three yourself with the NDK toolchain (`libsodium` in
     particular documents an Android build process in its own repo) and install into a
     staging sysroot, then point CMake at that sysroot.
   - Use vcpkg with an Android triplet (`vcpkg.json` at the c-toxcore root already lists
     exactly these dependencies), if vcpkg's Android cross-compilation support covers
     your target ABI/API level.

3. **Wire the module in:**
   - Add `include(":daemon-tox")` to `settings.gradle.kts`.
   - Add `implementation(project(":daemon-tox"))` to `app/build.gradle.kts` -- consider
     gating this behind a product flavor dimension, e.g. an `engine` dimension with
     `mock`/`real` flavors, so Phase 1's fast mock build path stays available even after
     this (same suggestion the removed jami-daemon doc made).

4. **Build just the native side first**, before touching Gradle, to see CMake/compiler
   errors directly instead of through Gradle's log wrapping:
   ```bash
   cd daemon-tox
   mkdir build-arm64 && cd build-arm64
   cmake .. \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a \
     -DANDROID_PLATFORM=android-24 \
     -DCMAKE_PREFIX_PATH=/path/to/your/android-libsodium-opus-libvpx/staging \
     -DBUILD_TOXAV=On
   make -j$(nproc)
   ```
   Expect the first attempts to fail on CMake not finding `libsodium`/`opus`/`vpx` until
   step 2's staging sysroot/prefix path is actually correct for cross-compilation --
   c-toxcore's own `find_package`/`pkg-config` calls have no special Android-awareness
   built in.

5. **Once that produces `libtoxcore-jni.so`**, let Gradle's `externalNativeBuild` take
   over (`./gradlew :daemon-tox:assembleDebug`).

6. **Fill in the remaining JNI wrappers** (`tox_options_new`/callback registration/ToxAV
   functions -- see "Still open" above) before attempting to actually wire `ToxBridge`
   into the app; the current scaffold's `startDaemon()` creates an instance with default
   options and no callbacks registered, which isn't useful for a real messenger yet.

7. **Wiring `ToxBridge` into the app**: `org.meshly.app.core.ToxBridge` (the Phase 1 mock)
   and `org.meshly.app.daemontox.ToxBridge` (this module) currently have different shapes
   (friend-number-centric vs. whatever stable-id model the mock uses) -- mapping between
   them (friend number -> stable contact id via `tox_friend_get_public_key`, matching how
   the removed jami-daemon scaffold's `getJamiId(accountId)` bridged accountId -> Jami ID)
   is the next wiring step once this module actually builds.

## Reference: real API surface used here

Pulled directly from `native/upstream/c-toxcore/toxcore/tox.h` and
`native/upstream/c-toxcore/toxav/toxav.h` at commit
`1d79022fb4e56dffe0bbd075d47e00f7a0b62ab3`:

| Concern | Real call / typedef | Source location |
|---|---|---|
| Create/destroy instance | `Tox *tox_new(const Tox_Options *options, Tox_Err_New *error)` / `void tox_kill(Tox *tox)` | tox.h:504, tox.h:513 |
| Bootstrap into the DHT | `bool tox_bootstrap(Tox *tox, const char *host, uint16_t port, const Tox_Dht_Id public_key, Tox_Err_Bootstrap *error)` | tox.h:584 |
| Add a TCP relay | `bool tox_add_tcp_relay(Tox *tox, const char *host, uint16_t port, const Tox_Dht_Id public_key, Tox_Err_Bootstrap *error)` | tox.h:600 |
| Self connection state (getter) | `Tox_Connection tox_self_get_connection_status(const Tox *tox)` -- deprecated in favor of the event below | tox.h:646 |
| Self connection state (event) | `tox_self_connection_status_cb(Tox*, Tox_Connection, void*)` via `tox_callback_self_connection_status` | tox.h:651, tox.h:666 |
| Event loop cadence | `uint32_t tox_iteration_interval(const Tox *tox)` | tox.h:672 |
| Event loop step | `void tox_iterate(Tox *tox, void *user_data)` | tox.h:680 |
| Own Tox address (38 bytes: 32-byte pubkey + 4-byte nospam + 2-byte checksum) | `void tox_self_get_address(const Tox *tox, Tox_Address address)` | tox.h:698, tox.h:271, tox.h:277 |
| Add a friend (sends a request) | `Tox_Friend_Number tox_friend_add(Tox *tox, const Tox_Address address, const uint8_t message[], size_t length, Tox_Err_Friend_Add *error)` | tox.h:935 |
| Add a friend (no request, e.g. accepting) | `Tox_Friend_Number tox_friend_add_norequest(Tox *tox, const Tox_Public_Key public_key, Tox_Err_Friend_Add *error)` | tox.h:958 |
| Remove a friend | `bool tox_friend_delete(Tox *tox, Tox_Friend_Number friend_number, Tox_Err_Friend_Delete *error)` | tox.h:989 |
| Resolve friend number <-> public key | `bool tox_friend_get_public_key(...)` / `Tox_Friend_Number tox_friend_by_public_key(...)` | tox.h:1078, tox.h (Friend list queries section) |
| Friend request received (event) | `tox_friend_request_cb(Tox*, const Tox_Public_Key, const uint8_t*, size_t, void*)` via `tox_callback_friend_request` | tox.h:1477, tox.h:1489 |
| Send a message | `Tox_Friend_Message_Id tox_friend_send_message(Tox *tox, Tox_Friend_Number friend_number, Tox_Message_Type type, const uint8_t message[], size_t length, Tox_Err_Friend_Send_Message *error)` | tox.h:1443 |
| Message type values | `Tox_Message_Type`: NORMAL=0, ACTION=1 | tox.h:403-416 |
| Message received (event) | `tox_friend_message_cb(Tox*, Tox_Friend_Number, Tox_Message_Type, const uint8_t*, size_t, void*)` via `tox_callback_friend_message` | tox.h:1497, tox.h:1508 |
| Read receipt (event) | `tox_friend_read_receipt_cb(Tox*, Tox_Friend_Number, Tox_Friend_Message_Id, void*)` via `tox_callback_friend_read_receipt` | tox.h:1453, tox.h:1464 |
| Friend connection state (getter, deprecated) / event | `Tox_Connection tox_friend_get_connection_status(...)` / `tox_friend_connection_status_cb(...)` via `tox_callback_friend_connection_status` | tox.h:1283, tox.h:1292, tox.h:1306 |
| Connection state values | `Tox_Connection`: NONE=0, TCP=1, UDP=2 | tox.h:605-633 |
| Friend name changed (event) | `tox_friend_name_cb(Tox*, Tox_Friend_Number, const uint8_t*, size_t, void*)` via `tox_callback_friend_name` | tox.h:1177, tox.h:1188 |
| Friend status message changed (event) | `tox_friend_status_message_cb(...)` via `tox_callback_friend_status_message` | tox.h:1224, tox.h:1235 |
| Friend user status changed (event) | `tox_friend_status_cb(Tox*, Tox_Friend_Number, Tox_User_Status, void*)` via `tox_callback_friend_status` | tox.h:1256, tox.h:1266 |
| User status values | `Tox_User_Status`: NONE=0, AWAY=1, BUSY=2 | tox.h:376-395 |
| Friend typing changed (event) | `tox_friend_typing_cb(Tox*, Tox_Friend_Number, bool, void*)` via `tox_callback_friend_typing` | tox.h:1329, tox.h:1339 |
| Create ToxAV instance | `ToxAV *toxav_new(Tox *tox, Toxav_Err_New *error)` | toxav.h:137 |
| ToxAV event loop cadence / step | `uint32_t toxav_iteration_interval(const ToxAV *av)` / `void toxav_iterate(ToxAV *av)` | toxav.h:164, toxav.h:171 |
| Place a call | `bool toxav_call(ToxAV *av, Tox_Friend_Number friend_number, uint32_t audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Call *error)` | toxav.h:272 |
| Incoming call (event) | `toxav_call_cb(ToxAV*, Tox_Friend_Number, bool audio_enabled, bool video_enabled, void*)` via `toxav_callback_call` | toxav.h:282, toxav.h:288 |
| Answer a call | `bool toxav_answer(ToxAV *av, Tox_Friend_Number friend_number, uint32_t audio_bit_rate, uint32_t video_bit_rate, Toxav_Err_Answer *error)` | toxav.h:341 |
| Call control (hangup/pause/resume/mute/...) | `bool toxav_call_control(ToxAV *av, Tox_Friend_Number friend_number, Toxav_Call_Control control, Toxav_Err_Call_Control *error)` | toxav.h:503 |
| Call state changed (event) | `toxav_call_state_cb(ToxAV*, Tox_Friend_Number, uint32_t state, void*)` via `toxav_callback_call_state` | toxav.h:403, toxav.h:409 |
| Call state bitmask values | `Toxav_Friend_Call_State`: NONE=0, ERROR=1, FINISHED=2, SENDING_A=4, SENDING_V=8, ACCEPTING_A=16, ACCEPTING_V=32 (combinable bits) | toxav.h:350-392 |
| Send an audio frame | `bool toxav_audio_send_frame(ToxAV *av, Tox_Friend_Number friend_number, const int16_t pcm[], size_t sample_count, uint8_t channels, uint32_t sampling_rate, Toxav_Err_Send_Frame *error)` | toxav.h:614 |
| Receive an audio frame (event) | `toxav_audio_receive_frame_cb(ToxAV*, Tox_Friend_Number, const int16_t pcm[], size_t, uint8_t, uint32_t, void*)` via `toxav_callback_audio_receive_frame` | toxav.h:714, toxav.h:721 |
| Send a video frame (planar YUV420) | `bool toxav_video_send_frame(ToxAV *av, Tox_Friend_Number friend_number, uint16_t width, uint16_t height, const uint8_t y[], const uint8_t u[], const uint8_t v[], Toxav_Err_Send_Frame *error)` | toxav.h:661 |
| Receive a video frame (event) | `toxav_video_receive_frame_cb(ToxAV*, Tox_Friend_Number, uint16_t, uint16_t, const uint8_t y[], u[], v[], int32_t ystride, ustride, vstride, void*)` via `toxav_callback_video_receive_frame` | toxav.h:744, toxav.h:757 |
| Audio/video bit rate suggestions (events) | `toxav_audio_bit_rate_cb`/`toxav_video_bit_rate_cb` via `toxav_callback_audio_bit_rate`/`toxav_callback_video_bit_rate` | toxav.h:637, toxav.h:688 |

`GPL-3.0-or-later` is the real, current license (SPDX header, confirmed in both `tox.h`
and `toxav.h`) -- not a leftover mistake in this scaffolding, and not the same string as
"GPLv3" used loosely elsewhere in this repo's prose (the license *text* is the same GPLv3
text either way; "-or-later" only affects which future GPL versions also apply).
