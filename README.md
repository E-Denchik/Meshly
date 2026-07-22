# Meshly

Meshly is a serverless, end-to-end encrypted peer-to-peer messenger and calling app for
Android, built on top of [GNU Jami](https://jami.net)'s core engine (`libjami` /
`jami-daemon`). No central server: accounts, presence, messaging, and calls are all
carried over OpenDHT with PJSIP/ICE for media negotiation and GnuTLS for transport
security.

> This README is a living document — it's kept in sync with the state of the repo as
> the project moves through phases. If something here looks stale, the code is the
> source of truth; please open an issue/note the discrepancy.

## Status

| Phase | Scope | State |
|---|---|---|
| **Phase 1** | Android app skeleton, full Jetpack Compose UI, Room local cache, Clean Architecture layering, mock/stub JNI engine | ✅ Done. Builds (`assembleDebug`), unit tests pass, manually verified end-to-end on a physical device (onboarding → contacts → chat → calls) |
| **Phase 2** | Real `libjami` native integration (OpenDHT, PJSIP, GnuTLS, FFmpeg via NDK) | 🚧 Scaffolding only — CMake/Gradle wiring and the real JNI contract are in place and reviewed against upstream source, but the native build has not actually been compiled yet (needs ~30-50GB disk + several hours of CPU time; see [`PHASE2_BUILD.md`](PHASE2_BUILD.md)) |

Right now the app runs entirely on a **mock engine** (`org.meshly.app.core.JamiBridge`):
account creation, contacts, messaging, and calls are all simulated in-process so the
full UI is exercisable without the native daemon. This is intentional — see
[Architecture](#architecture) below.

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
│  JamiDaemonService (foreground service) · Repositories       │
│  JamiBridge: maps native/mock events to Kotlin Flow          │
│  Room database (contacts, chat history)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ JNI (mock today, real in Phase 2)
┌──────────────────────────────▼──────────────────────────────┐
│                  Core / Daemon Layer                        │
│  libjami (jami-daemon) · OpenDHT · PJSIP · GnuTLS · FFmpeg   │
└─────────────────────────────────────────────────────────────┘
```

The Bridge Layer only ever talks to a `JamiBridge`-shaped API. Phase 1 backs it with an
in-process mock; Phase 2 will back it with `daemon/`'s real native engine. The UI and
repositories don't need to change when that swap happens.

## Project layout

```
app/                          Android app module (Phase 1, always builds)
  src/main/java/org/meshly/app/
    core/JamiBridge.kt         Mock engine: simulates the daemon in pure Kotlin
    data/model/                Domain models (Account, Contact, ChatMessage, CallSession)
    data/local/                Room entities + DAOs
    data/repository/           Account/Contact/Chat/Call repositories
    service/                   JamiDaemonService (foreground presence), CallService
    ui/                        Compose screens, navigation, ViewModels
  src/test/                    Unit tests (repositories, JamiBridge mock, fakes)

daemon/                       Phase 2 native module (NOT built yet, NOT wired into :app)
  build.gradle.kts             CMake wiring pointed at native/upstream/jami-daemon
  src/main/java/org/meshly/app/daemon/
    RealJamiBridge.kt           Real JamiService calls (source-cited against upstream)
    RealJamiEvent.kt            Sealed class for native signals
    JamiCallbackAdapter.kt      SWIG director callback adapters

native/upstream/jami-daemon/   Git submodule: real GNU Jami daemon source (reference +
                               eventual build target for Phase 2)

PHASE2_BUILD.md                Exact remaining steps to compile the real native engine
```

## Building

```bash
git clone --recurse-submodules git@github.com:E-Denchik/Meshly.git
cd Meshly
./gradlew assembleDebug   # Phase 1 app, mock engine — this is what builds today
./gradlew test            # unit tests
```

`daemon/` is intentionally **not** included in `settings.gradle.kts` yet, so none of the
above touches the native build. See `PHASE2_BUILD.md` before trying to build it.

### Running on a device

The Android emulator needs ~2GB+ RAM to boot; if your machine is memory-constrained,
prefer a physical device over the emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On MIUI/HyperOS devices, USB installs may be silently rejected
(`INSTALL_FAILED_USER_RESTRICTED`) unless "Install via USB" is enabled in Developer
options.

## License

Meshly links against `libjami`, which is GPLv3. Every source file in this repo carries
a GPLv3 header accordingly — see [`COPYING`](native/upstream/jami-daemon/COPYING) (via
the submodule) for the full license text.

## Networking defaults

- Default OpenDHT bootstrap nodes: `bootstrap.jami.net:4222`, `bootstrap.ring.cx:4222`
  (official GNU Jami nodes — configurable per-account in Settings)
- Package name: `org.meshly.app`
