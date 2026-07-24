# Meshly

[![Android CI](https://github.com/E-Denchik/Meshly/actions/workflows/android-ci.yml/badge.svg)](https://github.com/E-Denchik/Meshly/actions/workflows/android-ci.yml)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Meshly is a serverless, end-to-end encrypted peer-to-peer messenger and calling app for
Android, built on top of [Tox](https://tox.chat)'s core engine (`c-toxcore` /
`ToxAV`). No central server: peer discovery happens over a Kademlia-like distributed
hash table (DHT), messages and calls travel directly between peers over UDP (falling
back to TCP relays when direct UDP isn't reachable), authenticated and encrypted
end-to-end via `libsodium` with forward secrecy, and calls carry Opus-encoded audio and
VP8-encoded video via ToxAV.

> This README is a living document — it's kept in sync with the state of the repo as
> the project moves through phases. If something here looks stale, the code is the
> source of truth; please open an issue/note the discrepancy.

## Status

| Phase | Scope | State |
|---|---|---|
| **Phase 1** | Android app skeleton, full Jetpack Compose UI, Room local cache, Clean Architecture layering | ✅ Done |
| **Phase 2** | Real `c-toxcore`/`ToxAV` native integration (libsodium, opus, libvpx via NDK), wired end-to-end into `:app` | ✅ Done and **live-verified** between two separate physical Android devices — see [Live verification](#live-verification) below |

The app runs entirely on the **real native Tox engine** — there is no mock/simulation
layer anymore. Account identities, friend requests, presence, and text messages are
genuinely generated, sent, and received over the real Tox network (DHT + onion routing,
with TCP relay fallback). Call *signaling* (ring/accept/reject/hang-up) is likewise real;
live microphone/camera streaming during a call is the one piece still not implemented —
see [Known limitations](#known-limitations).

**Design system**: `ui/theme/` defines a dedicated Meshly color palette (light + dark, full
Material 3 tonal roles rather than a partial scheme), rounder shapes, and light typography
tweaks. `ui/components/` has the shared building blocks (`Avatar`, `EmptyState`) reused across
the chat/contacts/calls lists and the in-call screens so they read as one consistent system
instead of ad hoc per-screen styling.

**Account lifecycle**: Settings has a "Log out" action (with a confirmation dialog, since it's
irreversible without a previously exported backup archive+password) that clears the local
identity and chat/contact history and returns to onboarding, where a different identity can be
created or imported. There's intentionally no separate "login" screen — on this decentralized
model, restoring an identity *is* onboarding's "Import an existing account" flow.

## Live verification

Beyond compiling, the real Tox engine was verified live between two independent physical
Android devices, each running its own genuine Tox identity: real Tox IDs were generated,
a real friend request crossed the actual internet (not a LAN shortcut), and a real text
message was sent, delivered, and confirmed via c-toxcore's own read-receipt callback,
then correctly displayed and persisted in the recipient's chat history.

That pass also caught and fixed three real bugs that unit tests alone couldn't have
caught, since they only show up against a live network and a second device:

- **Friend requests you send could never resolve to `CONFIRMED`.** There was no code
  path promoting an outgoing request once the peer actually connected, so chat access
  stayed permanently locked even after a real, live friendship existed.
- **UDP-only bootstrapping silently failed on UDP-hostile networks.** `tox_add_tcp_relay`
  was never called anywhere, and only 3 bootstrap nodes were configured — no fallback
  path existed for networks that block or degrade outbound UDP (a real-world condition,
  not a hypothetical one). The engine now also registers TCP relays on a larger set of
  well-known nodes.
- **Incoming messages/friend requests were dropped unless the exact matching screen was
  open at the moment they arrived**, because the repositories responsible for persisting
  them were being re-created per screen instead of living for the app's process
  lifetime. They're now app-scoped singletons, constructed once at process start.

A later pass built the actual audio/video media pipeline (mic/camera capture and
speaker/screen playback around the already-working call signaling — see
[`AudioCallEngine`](app/src/main/java/org/meshly/app/media/AudioCallEngine.kt)/
[`VideoCallSession`](app/src/main/java/org/meshly/app/media/VideoCallSession.kt)) and
verified it the same way: a real audio+video call between the same two devices, both
sides showing the other's live camera feed and exchanging real voice, confirmed via
screen capture on both ends. That pass caught two more real, only-live-visible bugs:

- **`CallRepository` had the identical "recreated per screen" bug** described above for
  chat/contacts — an incoming `CallInviteReceived` event fired before `IncomingCallActivity`'s
  fresh `CallViewModel`/`CallRepository` existed, so `acceptCall()` had no session to act
  on and silently no-opped. Fixed the same way: an app-scoped singleton.
- **A real, healthy call could drop after 25-50 seconds with no error, no crash, and no
  network outage visible at the OS/Wi-Fi level.** Traced into c-toxcore itself:
  `toxav.c`'s `iterate_common()` ends a call the instant `tox_friend_get_connection_status()`
  reports the friend offline, with zero grace period — a momentary DHT/UDP keepalive blip
  (plausibly from the extra bandwidth/CPU load of a real audio+video stream) was enough to
  kill an otherwise-fine call outright. Fixed via
  [`native/patches/0001-toxav-call-offline-grace-period.patch`](native/patches/0001-toxav-call-offline-grace-period.patch)
  (a 10-second grace period before actually tearing the call down — see "Project layout"
  above for why this is a patch file, not a submodule commit); re-verified live, the same
  two-device call then ran well past two minutes.

## Architecture

Three layers, top to bottom:

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│  Jetpack Compose + Material 3 + Navigation Compose           │
│  Onboarding · Contacts/Requests · Chat · Calls · Settings    │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / ViewModel
┌──────────────────────────────▼──────────────────────────────┐
│                    Bridge Layer (Kotlin)                    │
│  ToxDaemonService (foreground service, drives the single-    │
│  threaded tox_iterate/toxav_iterate loop) · Repositories,    │
│  each an app-scoped singleton (see Live verification above)  │
│  ToxBridge: maps real native callbacks to Kotlin SharedFlow  │
│  Room database (contacts, chat history)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ hand-written JNI
┌──────────────────────────────▼──────────────────────────────┐
│                  Core / Daemon Layer                        │
│  c-toxcore (DHT, UDP/TCP transport, libsodium) · ToxAV       │
│  (Opus audio, VP8 video via libvpx)                          │
└─────────────────────────────────────────────────────────────┘
```

The Bridge Layer only ever talks to `daemon-tox`'s `ToxBridge`-shaped API, so the UI and
repositories don't need to know anything about JNI or the native build underneath them.

## Project layout

```
app/                          Android app module
  src/main/java/org/meshly/app/
    data/model/                 Domain models (Account, Contact, ChatMessage, CallSession)
    data/local/                 Room entities + DAOs
    data/repository/            Account/Contact/Chat/Call repositories - talk to
                                 daemon-tox's ToxBridge, own app-scoped singletons (see
                                 MeshlyApplication.kt) for Contact/Chat so their event
                                 subscriptions survive regardless of which screen is open
    service/                    ToxDaemonService (drives the real tox_iterate/
                                 toxav_iterate loop on a dedicated single thread), CallService
    ui/                         Compose screens, navigation, ViewModels
  src/test/                     Empty - see "Testing" below for why

daemon-tox/                   Real c-toxcore/ToxAV native module, wired into :app
  CMakeLists.txt                 add_subdirectory()s c-toxcore, links a hand-written JNI
                                  wrapper against it (no SWIG on the Tox side), and wires
                                  PKG_CONFIG_LIBDIR at deps/<abi>/lib/pkgconfig so the
                                  three native dependencies below resolve
  build.gradle.kts               Gradle/CMake wiring; single ABI (arm64-v8a) for now
  deps/<abi>/                    NDK-cross-compiled libsodium/opus/libvpx. Headers, .pc
                                  files, and CMake package configs are checked into git
                                  (small, deterministic, portable); the .a static
                                  archives themselves are gitignored and must be produced
                                  locally - see scripts/build-native-deps.sh and
                                  "Building" below
  scripts/build-native-deps.sh   Cross-compiles libsodium/opus/libvpx for arm64-v8a and
                                  stages them into deps/arm64-v8a/ - idempotent, and the
                                  same script CI uses (see .github/workflows/android-ci.yml)
  src/main/cpp/tox_jni.c         Hand-written JNI glue against the real tox_*/toxav_* API:
                                  account lifecycle, savedata persistence, bootstrap +
                                  TCP relay, friend management, messaging, and the full
                                  ToxAV call-signaling surface, with real callback dispatch
  src/main/java/org/meshly/app/daemontox/
    ToxNative.kt                  external fun JNI declarations, cited against upstream
                                   tox.h/toxav.h line numbers
    ToxBridge.kt                  Singleton wrapping ToxNative; SharedFlow<ToxDaemonEvent>
    ToxDaemonEvent.kt              Sealed class for native signals
    ToxCallbackAdapter.kt          Receives the native callback dispatch (see tox_jni.c's
                                   top-of-file doc on the shared-JNIEnv design and its
                                   single-thread requirement)

native/upstream/c-toxcore/    Git submodule: real c-toxcore/ToxAV source, actually built
                               (not just a reference) as daemon-tox's native dependency
native/patches/                Local patches applied to that submodule at CMake-configure
                                time (daemon-tox/CMakeLists.txt, idempotent via `git apply
                                --reverse --check`) - not committable into the submodule
                                itself since this repo doesn't control its upstream remote,
                                so a tracked .patch file is what survives a fresh clone/CI
                                checkout instead. See "Known limitations" below for what the
                                current patch fixes.

PHASE2_BUILD_TOX.md           Historical planning document written before the native
                               build was completed - useful for the "why" behind the
                               module's structure, but its own "not yet built" status
                               section is now stale. See "Building" below for the current,
                               accurate build recipe.
```

## Building

```bash
git clone --recurse-submodules git@github.com:E-Denchik/Meshly.git
cd Meshly
./gradlew assembleDebug   # builds :app AND the real native :daemon-tox engine
```

Because `:app` depends on `:daemon-tox`, a plain `assembleDebug` now also drives
`daemon-tox`'s CMake/native build — which needs two things a fresh clone doesn't have yet:

1. **Android NDK `27.2.12479018`** (matches `daemon-tox/build.gradle.kts`'s `ndkVersion`),
   installable via `sdkmanager --install "ndk;27.2.12479018"`.
2. **`daemon-tox/deps/arm64-v8a/lib/{libsodium,libopus,libvpx}.a`** — NDK-cross-compiled
   static builds of `libsodium` (full build, `LIBSODIUM_FULL_BUILD=1`, so it includes the
   legacy `crypto_pwhash_scryptsalsa208sha256_*` symbols `toxencryptsave.c` needs), `opus`,
   and `libvpx`, produced by:

   ```bash
   ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018 ./daemon-tox/scripts/build-native-deps.sh
   ```

   The headers, `.pc` files, and CMake package configs these libraries produce are small,
   deterministic, and checked into `daemon-tox/deps/arm64-v8a/` directly so c-toxcore's
   `pkg_search_module`-based dependency discovery (`cmake/Dependencies.cmake`) has
   something to resolve against without a build; only the `.a` static archives themselves
   are gitignored and have to be produced locally (or restored from CI's cache — see
   "Continuous integration" below) before `daemon-tox`'s CMake configure step will
   succeed. The script is idempotent — it skips a library whose `.a` is already staged,
   so re-running it after a fresh clone only rebuilds what's missing; pass `--force` to
   rebuild everything from scratch (e.g. after bumping one of the pinned upstream tags
   at the top of the script). Requires `pkg-config`, `autoconf`, `automake`, `libtool`,
   `cmake`, and `ninja` on the host in addition to the NDK.

`PHASE2_BUILD_TOX.md` covers the reasoning and exact upstream CMake line numbers behind
each of these steps in more depth; treat its own "status" framing as historical, not
current — the recipe above reflects what's actually been built and verified.

### Testing

```bash
./gradlew test
```

`app/src/test/` is currently empty. The previous Phase 1 unit tests (repositories, mock
`ToxBridge`, fakes) were written against the in-process mock engine and don't apply to a
real native `.so`-backed singleton — a plain JVM unit test can't load `libtoxcore-jni.so`
at all. They were deliberately removed rather than left broken; real coverage for this
layer needs Android instrumented tests (`androidTest`, running on-device/emulator against
the actual native library) or an integration-test harness against a real `Tox`/`ToxAV`
instance, neither of which exists yet.

### Running on a device

The Android emulator needs ~2GB+ RAM to boot; if your machine is memory-constrained,
prefer a physical device over the emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On MIUI/HyperOS devices, USB installs may be silently rejected
(`INSTALL_FAILED_USER_RESTRICTED`) unless "Install via USB" is enabled in Developer
options.

### Continuous integration

`.github/workflows/android-ci.yml` builds and unit-tests the app on GitHub's hosted
runners against the real native engine, on every push/PR to `main`:

1. Checks out the repo with `submodules: recursive` (needed for `native/upstream/c-toxcore`
   and its own nested `third_party/cmp` submodule).
2. Installs NDK `27.2.12479018` via `sdkmanager`, plus `ninja`/`autoconf`/`automake`/
   `libtool`/`pkg-config` via `apt`.
3. Restores `daemon-tox/deps/arm64-v8a/lib/{libsodium,libopus,libvpx}.a` from an
   `actions/cache` entry keyed on `build-native-deps.sh`'s contents; on a cache miss it
   runs that script to rebuild them (a full native cross-compile takes several minutes,
   so this only happens again when the script itself changes).
4. Runs `./gradlew test --stacktrace` then `./gradlew assembleDebug --stacktrace`, and
   uploads the resulting debug APK and test reports as workflow artifacts.

Locally reproduce exactly what CI does with `./daemon-tox/scripts/build-native-deps.sh`
followed by `./gradlew test assembleDebug` (see "Building" above).

## Distribution

Per the project's goal of not depending on a single distribution channel:

- **F-Droid**: `fastlane/metadata/android/en-US/` holds the store listing (title, short/
  full description, per-version changelog) in the format both F-Droid and Google Play's
  `fastlane supply` expect. Actual F-Droid inclusion additionally requires a build recipe
  merged into the separate [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo,
  which hasn't been submitted yet — this repo only prepares the metadata and a
  reproducible Gradle build.
- **Direct APK**: CI (see "Continuous integration" above) uploads a debug APK as a
  workflow artifact on every push/PR; a signed *release* APK can be attached to GitHub
  Releases once a release signing key exists (deliberately not part of this repo — see
  `.gitignore`'s `*.jks`/`*.keystore` rule).
- **Google Play**: optional, not set up.

## Localization

All user-facing UI text is externalized to Android string resources
(`app/src/main/res/values*/strings.xml`), not hardcoded in Compose. The app ships resource
folders for several locales:

| Locale | Resource folder |
|---|---|
| English (default) | `values/` |
| Russian | `values-ru/` |
| French | `values-fr/` |
| Turkish | `values-tr/` |
| Arabic | `values-ar/` |
| Chinese | `values-zh/` |

Android picks the matching translation automatically based on the device's system
language, falling back to English otherwise — no in-app language switcher is needed, and
this works on every supported API level (minSdk 24) purely from the resource-qualifier
folder structure above. Locale folders intentionally use bare language qualifiers (`values-ru`,
not `values-ru-rRU`) so any regional variant of a supported language matches - e.g. a device
set to Chinese in any region resolves to `values-zh`, not just `zh-CN` specifically.

`app/src/main/res/xml/locales_config.xml`, wired via `android:localeConfig` in
`AndroidManifest.xml`, additionally declares these same locales for Android 13+'s per-app
language feature - it lets a user override Meshly's language independently of the device's
system language from Settings > Apps > Meshly > Language, without needing an in-app language
switcher UI. On API < 33 this is simply ignored; the base auto-detection above still applies.

New user-facing strings should be added to `values/` first and then mirrored into every
other locale folder to keep them in sync — nothing enforces this automatically today.

## Known limitations

- **No speaker/earpiece toggle UI.** Audio calls default to earpiece, video calls to
  speakerphone (holding a phone to your ear during video makes no sense) - a sensible
  default rather than a user-facing control, since none was asked for.
- **Video capture/send only runs while `CallScreen` is visible.** Audio
  ([`AudioCallEngine`](app/src/main/java/org/meshly/app/media/AudioCallEngine.kt)) is
  headless and survives the screen turning off, matching real phone-call behavior; video
  ([`VideoCallSession`](app/src/main/java/org/meshly/app/media/VideoCallSession.kt))
  needs a visible surface for local self-preview anyway, so it's tied to the screen's own
  lifecycle rather than carrying the Android-14 background-camera foreground-service-type
  plumbing that would otherwise be needed.
- **No message history before a friendship, and no offline delivery.** Plain Tox has no
  store-and-forward: a message sent while the peer isn't connected is marked failed
  immediately rather than queued, matching `tox_friend_send_message`'s own
  `TOX_ERR_FRIEND_SEND_MESSAGE_FRIEND_NOT_CONNECTED` behavior.
- **Single ABI.** `daemon-tox` currently only targets `arm64-v8a`; other ABIs would need
  their own cross-compiled `deps/<abi>/` tree.
- **Blocking is local-only.** Tox has no protocol-level "block" - `blockContact`/
  `unblockContact` only flip local UI state; the underlying friendship and `friendNumber`
  are left untouched.

## License

Meshly links against `c-toxcore`, which is GPL-3.0-or-later. Every source file in this
repo carries a GPLv3 header accordingly — see [`LICENSE`](LICENSE) at the repo root for
the full license text. `native/upstream/c-toxcore/LICENSE` (in the submodule, once
checked out) carries the same GPLv3 text.

## Networking defaults

- Default Tox bootstrap nodes (well-known public nodes, `host:port:public-key-hex`,
  each also registered as a TCP relay so onion routing still works on networks that
  block or degrade outbound UDP — see [Live verification](#live-verification)).
  Configurable per-account in Settings. Spot-checked against
  [nodes.tox.chat](https://nodes.tox.chat)'s live status page while writing this
  document (2026-07-23) — bootstrap node uptime/ownership rotates over time, so
  re-check that page for the current list rather than treating this as permanent:
  - `node.tox.biribiri.org:33445:F404ABAA1C99A9D37D61AB54898F56793E1DEF8BD46B1038B9D822E8460FAB67`
  - `tox.abilinski.com:33445:10C00EB250C3233E343E2AEBA07115A5C28920E9C8D29492F6D00B29049EDC7`
  - `tox.plastiras.org:33445:8E8B63299B3D520FB377FE5100E65E3322F7AE5B20A0ACED2981769FC5B43B4`
  - `205.185.115.131:53:3091C6BEB2A993F1C6300C16549FABA67098FF3D62C6D253828B531470B53D68`
  - `3.0.24.15:33445:E20ABCF38CDBFFD7D04B29C956B33F7B27A3BB7AF0618101617B036E4AEA402D`
  - `tox2.mf-net.eu:33445:70EA214FDE161E7432530605213F18F7427DC773E276B3E317A07531F548545F`
  - `tox3.mf-net.eu:33445:F4FC9398B7167668ED2BCF85634E04D4CDCDD2F95DA5F305BD234888B6E6A771`
  - `tox4.mf-net.eu:33445:DCD342A0D5E2AA8E35C2BD2C7988F906EEB631B35100170A7F30E77D7F596442`
  - `144.172.88.203:33445:2016A0F2797EE3A8B004BA623F11AAFC8146F1B8F45107232A1A1AECCE856674`
  - `119.59.101.63:33445:197F746696062FA3BD07BB3BC0656ABD6692B4DAA27DACF0F474754F2B09B060`
  - `172.86.77.39:33445:AFFD3FAD3460E62A894E439534B27E5A5DCFE379C1C0FB78DEF1B150A87E900F`
  - `144.217.167.73:33445:7E5668E0EE09E19F320AD47902419331FFEE147BB3606769CFBE921A2A2FD34C`
- Package name: `org.meshly.app`
