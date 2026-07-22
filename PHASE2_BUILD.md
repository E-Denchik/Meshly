# Phase 2: Real libjami Native Integration

Status: **infrastructure only, not yet built or compiled**. This document exists so that
whoever has a machine with enough disk/RAM/time can pick this up and run the actual native
build without having to rediscover the daemon's build system from scratch.

## Why this wasn't compiled yet

At the time this scaffolding was written, the dev machine had only 16GB of free disk (94%
full) and swap was completely exhausted. A real jami-daemon Android build compiles its own
`contrib/` tree from source — OpenDHT, PJSIP, GnuTLS, FFmpeg, jsoncpp, libgit2, secp256k1,
restinio, and ~30 more libraries (`native/upstream/jami-daemon/contrib/src/`) — once per ABI.
That realistically needs:

- **Disk:** 30-50GB+ (contrib source + object files, per ABI; more if building more than one ABI)
- **RAM:** several GB free; the C++ compiles (especially FFmpeg, WebRTC APM) are memory-hungry
- **Time:** on the order of hours on a normal dev machine, even building a single ABI
- **Tools:** Android NDK r25+ (r27.2.12479018 chosen here — see below), CMake ≥ 3.16, Ninja, SWIG ≥ 4.0

None of the above was available/feasible on this machine, so this pass focused on getting the
Gradle/CMake wiring and the real JNI contract right — using the actual upstream source as the
reference, not guesswork — so a future build attempt (on a beefier machine, or CI) can just run.

## What's already in place

- `native/upstream/jami-daemon/` — shallow clone (`--depth=1`) of
  https://github.com/savoirfairelinux/jami-daemon, used purely as a build/reference source. It's
  ~18MB, so keeping it around is cheap regardless of when the real build happens.
- `daemon/` — a Gradle Android library module with `externalNativeBuild.cmake` pointed at
  `native/upstream/jami-daemon/CMakeLists.txt`, passing the same arguments the daemon's own
  `BUILD.md` documents for Android (`-DJAMI_JNI=On`, `-DBUILD_CONTRIB=On`, etc.). **It is not
  included in `settings.gradle.kts`, and `:app` does not depend on it** — this was deliberate, so
  the existing Phase 1 mock build (`./gradlew assembleDebug`) is completely unaffected by
  anything under `daemon/`. Verified: `./gradlew assembleDebug test` still passes after adding it.
- `daemon/src/main/java/org/meshly/app/daemon/`:
  - `RealJamiBridge.kt` — real engine calls (`JamiService.init/fini/addAccount/
    sendAccountTextMessage/placeCallWithMedia/accept/hangUp/muteLocalMedia`), matching
    `net.jami.daemon.JamiService`'s real API surface as found in
    `native/upstream/jami-daemon/bin/jni/*.i`.
  - `RealJamiEvent.kt` — sealed class capturing the native signals we care about
    (registration state, incoming call/message, contact added/removed, trust requests).
  - `JamiCallbackAdapter.kt` — subclasses of the real `Callback` / `ConfigurationCallback`
    SWIG director classes that forward into `RealJamiEvent`, plus no-op stubs for the other
    five callback interfaces `JamiService.init()` requires (Presence, DataTransfer, Video,
    Conversation, NetworkService) since Meshly doesn't consume those signals yet.

None of this compiles right now — `net.jami.daemon.*` (JamiServiceJNI, Callback,
ConfigurationCallback, ...) only exists after `bin/jni/make-swig.sh` actually runs, which only
happens as part of a real CMake build with `JAMI_JNI=On`. That's fine: since `daemon` isn't in
`settings.gradle.kts`, nothing tries to compile it yet.

## Known uncertainties (verify once you can actually build)

These couldn't be confirmed without running SWIG for real, so they're flagged in code comments
too — double check them against the generated `net/jami/daemon/*.java` files:

- `sendAccountTextMessage`'s `flag` parameter meaning (currently passing `0`)
- `time_t received` parameter type in `ConfigurationCallback.incomingTrustRequest` — assumed
  `Long` in `JamiCallbackAdapter.kt`
- Exact SWIG-generated method names on `StringMap`/`VectMap` (assumed `set`/`get`/`add`, matching
  SWIG's default `std_map.i`/`std_vector.i` Java proxies — this is NOT `java.util.Map`'s
  `put`/`containsKey`)
- Where the actual Jami ID (public key hash / URI) shows up in account details after
  `addAccount` — `RealJamiBridge.createAccount` currently only returns the daemon's internal
  `accountId`, not the Jami ID itself

## Steps to actually build (once you have the resources)

1. **Install the NDK, CMake, and Ninja** via the SDK manager (these versions were confirmed
   available in this environment's SDK remote list; NDK r25+ is the daemon's stated minimum):
   ```bash
   sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"
   ```
   Ninja isn't distributed by `sdkmanager` — install it via your system package manager
   (`apt install ninja-build` on Debian/Kali) since the Meson contrib build step needs it.

2. **Install SWIG ≥ 4.0** (`apt install swig` or build from source — check `swig -version`).

3. **Wire the module in:**
   - Add `include(":daemon")` to `settings.gradle.kts`.
   - Add `implementation(project(":daemon"))` to `app/build.gradle.kts` (consider gating this
     behind a product flavor, e.g. an `engine` flavor dimension with `mock`/`real` flavors, so
     Phase 1's fast mock build path stays available even after this).

4. **Build just the native side first**, before touching Gradle, to see contrib build errors
   directly instead of through Gradle's log wrapping:
   ```bash
   cd daemon
   mkdir build-arm64 && cd build-arm64
   cmake .. \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a \
     -DANDROID_API=26 \
     -DANDROID_PLATFORM=android-26 \
     -DBUILD_EXTRA_TOOLS=On \
     -DJAMI_JNI=On \
     -DJAMI_JNI_PACKAGEDIR=$(pwd)/java
   make -j$(nproc)
   ```
   Expect this to take a while and to fail at least once on a missing host tool — the contrib
   tree bootstraps a lot of autotools/meson projects and is picky about the host toolchain.

5. **Once that produces `libjami-core-jni.so`**, let Gradle's `externalNativeBuild` take over
   (`./gradlew :daemon:assembleDebug`) so the .so and generated Java land where Gradle expects.

6. **Wiring RealJamiBridge into the app:** `org.meshly.app.core.JamiBridge` (the Phase 1 mock)
   and `org.meshly.app.daemon.RealJamiBridge` currently have similar but not identical shapes
   (accountId vs jamiId being the main one — libjami's accountId is an internal UUID, not the
   Jami public ID). The repositories in `:app` (`AccountRepository`, `ChatRepository`, etc.)
   were written against the mock's jamiId-centric model; expect to add a small mapping layer
   (fetch the Jami ID from `JamiService.getAccountDetails(accountId)` once registered) rather
   than a drop-in swap.

## Reference: real libjami API surface used here

Pulled directly from `native/upstream/jami-daemon/bin/jni/*.i` (SWIG module `JamiService`):

| Concern | Real call |
|---|---|
| Start/stop daemon | `JamiService.init(cfgCb, callCb, presCb, dataCb, videoCb, convCb, netCb)` / `JamiService.fini()` |
| Create account | `JamiService.getAccountTemplate("RING")` → fill in `Account.alias` → `JamiService.addAccount(details)` |
| Register username | `JamiService.registerName(accountId, name, scheme, password)` |
| Send message | `JamiService.sendAccountTextMessage(accountId, to, StringMap("text/plain" -> body), flag)` |
| Place call | `JamiService.placeCallWithMedia(accountId, to, VectMap of StringMap media attributes)` |
| Accept/reject/hang up | `JamiService.accept/refuse/hangUp(accountId, callId)` |
| Mute | `JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO"/"MEDIA_TYPE_VIDEO", bool)` |
| Add contact | `JamiService.addContact` / accept via `acceptTrustRequest` (configurationmanager.i — not yet wrapped in RealJamiBridge) |
| Incoming call/message | Override `Callback.incomingCall` / `Callback.incomingMessage` / `ConfigurationCallback.incomingAccountMessage` |
| Registration state | Override `ConfigurationCallback.registrationStateChanged` |

`"RING"` as the account type string is real and current (`JamiAccount::ACCOUNT_TYPE_JAMI` in
`src/jamidht/jamiaccount_config.h` is literally `"RING"`, kept for on-disk config compatibility
with the project's pre-rebrand history) — not a leftover mistake in this scaffolding.
