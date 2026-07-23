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
| **Phase 1** | Android app skeleton, full Jetpack Compose UI, Room local cache, Clean Architecture layering, mock/stub JNI engine | ✅ Done. Builds (`assembleDebug`), unit tests pass, manually verified end-to-end on a physical device (onboarding → contacts → chat → calls) |
| **Phase 2** | Real `c-toxcore`/`ToxAV` native integration (libsodium, opus, libvpx via NDK) | 🚧 Scaffolding only — CMake/Gradle wiring and the real JNI contract are in place and reviewed against upstream source, but the native build has not actually been compiled yet (needs Android builds of libsodium/opus/libvpx plus low single-digit GB disk and well under an hour of CPU time per ABI — much smaller than a Jami-based engine would have needed; see [`PHASE2_BUILD_TOX.md`](PHASE2_BUILD_TOX.md)) |

Right now the app runs entirely on a **mock engine** (`org.meshly.app.core.ToxBridge`):
account creation, contacts, messaging, and calls are all simulated in-process so the
full UI is exercisable without the native Tox engine. This is intentional — see
[Architecture](#architecture) below.

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
│  ToxDaemonService (foreground service) · Repositories        │
│  ToxBridge: maps native/mock events to Kotlin Flow           │
│  Room database (contacts, chat history)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ JNI (mock today, real in Phase 2)
┌──────────────────────────────▼──────────────────────────────┐
│                  Core / Daemon Layer                        │
│  c-toxcore (DHT, UDP/TCP transport, libsodium) · ToxAV       │
│  (Opus audio, VP8 video via libvpx)                          │
└─────────────────────────────────────────────────────────────┘
```

The Bridge Layer only ever talks to a `ToxBridge`-shaped API. Phase 1 backs it with an
in-process mock; Phase 2 will back it with `daemon-tox/`'s real native engine. The UI and
repositories don't need to change when that swap happens.

## Project layout

```
app/                          Android app module (Phase 1, always builds)
  src/main/java/org/meshly/app/
    core/ToxBridge.kt          Mock engine: simulates the Tox daemon in pure Kotlin
    data/model/                Domain models (Account, Contact, ChatMessage, CallSession)
    data/local/                Room entities + DAOs
    data/repository/           Account/Contact/Chat/Call repositories
    service/                   ToxDaemonService (foreground presence), CallService
    ui/                        Compose screens, navigation, ViewModels
  src/test/                    Unit tests (repositories, ToxBridge mock, fakes)

daemon-tox/                   Phase 2 native module (NOT built yet, NOT wired into :app)
  CMakeLists.txt                Hand-written: add_subdirectory()s c-toxcore + links a
                                 hand-written JNI wrapper (no SWIG on the Tox side)
  build.gradle.kts              Gradle/CMake wiring
  src/main/cpp/tox_jni.c        Hand-written JNI glue against the real tox_*/toxav_* API
  src/main/java/org/meshly/app/daemontox/
    ToxNative.kt                 external fun JNI declarations (source-cited against upstream)
    ToxBridge.kt                 Singleton wrapping ToxNative, SharedFlow<ToxDaemonEvent>
    ToxDaemonEvent.kt            Sealed class for native signals
    ToxCallbackAdapter.kt        Documents the hand-written-JNI callback dispatch pattern
    ToxFriendInfo.kt             Assembled per-friend state
    ToxCallSession.kt            Assembled per-call state

native/upstream/c-toxcore/    Git submodule: real c-toxcore/ToxAV source (reference +
                               eventual build target for Phase 2)

PHASE2_BUILD_TOX.md           Exact remaining steps to compile the real native engine
```

## Building

```bash
git clone --recurse-submodules git@github.com:E-Denchik/Meshly.git
cd Meshly
./gradlew assembleDebug   # Phase 1 app, mock engine — this is what builds today
./gradlew test            # unit tests
```

`daemon-tox/` is intentionally **not** included in `settings.gradle.kts` yet, so none of
the above touches the native build. See `PHASE2_BUILD_TOX.md` before trying to build it.

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

`.github/workflows/android-ci.yml` runs `./gradlew test assembleDebug` on every push/PR
to `main` using GitHub's free hosted runners, and uploads the resulting debug APK and
unit test reports as workflow artifacts. It checks out without submodules, since `:app`
doesn't depend on `:daemon-tox`/`native/upstream/c-toxcore` (Phase 2, not built yet).

## Distribution

Per the project's goal of not depending on a single distribution channel:

- **F-Droid**: `fastlane/metadata/android/en-US/` holds the store listing (title, short/
  full description, per-version changelog) in the format both F-Droid and Google Play's
  `fastlane supply` expect. Actual F-Droid inclusion additionally requires a build recipe
  merged into the separate [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo,
  which hasn't been submitted yet — this repo only prepares the metadata and a
  reproducible Gradle build.
- **Direct APK**: the CI workflow above produces a debug APK on every build; a signed
  release APK can be attached to GitHub Releases once a release signing key exists
  (deliberately not part of this repo — see `.gitignore`'s `*.jks`/`*.keystore` rule).
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

## License

Meshly links against `c-toxcore`, which is GPL-3.0-or-later. Every source file in this
repo carries a GPLv3 header accordingly — see [`LICENSE`](LICENSE) at the repo root for
the full license text. `native/upstream/c-toxcore/LICENSE` (in the submodule, once
checked out) carries the same GPLv3 text.

## Networking defaults

- Default Tox bootstrap nodes (well-known public nodes, `host:port:public-key-hex` —
  configurable per-account in Settings). The list below was spot-checked against
  [nodes.tox.chat](https://nodes.tox.chat)'s live status page while writing this
  document (2026-07-23) — bootstrap node uptime/ownership rotates over time, so
  re-check that page for the current list rather than treating this as permanent:
  - `node.tox.biribiri.org:33445:F404ABAA1C99A9D37D61AB54898F56793E1DEF8BD46B1038B9D822E8460FAB67`
  - `tox1.mf-net.eu:33445:B3E5FA80DC8EBD1149AD2AB35ED8B85BD546DEDE261CA593234C619249419506`
- Package name: `org.meshly.app`
