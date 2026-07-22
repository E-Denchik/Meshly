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
  - `RealJamiBridge.kt` — real engine calls (`JamiService.init/fini/addAccount/getAccountList/
    getAccountDetails/getVolatileAccountDetails/registerName/sendAccountTextMessage/
    getLastMessages/setIsComposing/setMessageDisplayed/getMessageStatus/placeCallWithMedia/
    accept/hangUp/hold/resume/muteLocalMedia/getCallList/getCallDetails/
    answerMediaChangeRequest/addContact/removeContact/getContacts/getContactDetails/
    getTrustRequests/acceptTrustRequest/discardTrustRequest/sendTrustRequest/publish/
    subscribeBuddy/getSubscriptions/setSubscriptions/sendFile/downloadFile/
    cancelDataTransfer/fileTransferInfo/setDefaultDevice/getDefaultDevice/addVideoDevice/
    removeVideoDevice/setDeviceOrientation/getSettings/applySettings/
    getDecodingAccelerated/setDecodingAccelerated/getEncodingAccelerated/
    setEncodingAccelerated/startAudioDevice/stopAudioDevice/openVideoInput/closeVideoInput/
    startLocalMediaRecorder/stopLocalRecorder`), matching `net.jami.daemon.JamiService`'s real
    API surface as found in `native/upstream/jami-daemon/bin/jni/*.i`. Includes
    `getJamiId`/`getRegisteredName` convenience wrappers over the raw details maps, and a
    `cameraProvider` hook for `VideoCallback.getCameraInfo` (see `RealVideoDevice.kt` below).
    Deliberately NOT wrapped: `registerSinkTarget` and the Surface-rendering / camera-capture
    natives (`acquireNativeWindow`, `registerVideoCallback`, `captureVideoFrame`, ...) which live
    on `net.jami.daemon.JamiServiceJNI`, a different generated class, and need real Android
    Surface/CameraX integration to mean anything — see the comment block in `RealJamiBridge.kt`'s
    video section for the full list and why.
  - `RealContact.kt` — `RealContact`/`RealTrustRequest` value types mapping the raw
    `StringMap`s `getContacts`/`getContactDetails`/`getTrustRequests` return, keyed exactly as
    `Contact::toMap()` (jami_contact.h) and `libjami::Account::TrustRequest` (account_const.h)
    produce them.
  - `RealChatMessage.kt` — wraps `libjami::Message` (the type `getLastMessages` returns).
  - `RealCallSession.kt` — `RealCallSession`/`RealCallState`/`RealCallType` mapping
    `getCallDetails`'s map, keyed exactly as `Call::getDetails()` builds it (src/call.cpp), plus
    `RealCallState.toSimplified()` bucketing libjami's 11 raw call states down to the 5 states
    Meshly's Phase 1 mock UI already knows.
  - `RealPresence.kt` — `RealPresenceState` (JamiAccount tracked-buddy presence, the one that
    matters here) + `RealSubscription` (legacy SIP-presence `getSubscriptions` shape, kept for
    API completeness).
  - `RealDataTransfer.kt` — `RealDataTransferEventCode`/`RealDataTransferError` enums and
    `RealFileTransferInfo`, matching `libjami::DataTransferEventCode`/`DataTransferError`
    (src/jami/datatransfer_interface.h) exactly (note `DataTransferEventCode` starts at
    `invalid = 0`, `created = 1`, not `created = 0`).
  - `RealVideoDevice.kt` — `RealCameraInfo`/`RealCameraProvider`: `VideoCallback.getCameraInfo`
    is the one signal in the whole API that's architecturally different from every other
    callback here -- it's a synchronous "fill these out-collections" call, not a fire-and-forget
    signal, so it can't be modeled as a `RealJamiEvent`. Answered via `RealJamiBridge.
    cameraProvider` (defaults to empty capability lists) instead.
  - `RealJamiEvent.kt` — sealed class capturing the native signals we care about (registration
    state, incoming call/message, new/hold/mute/media-negotiation call signals, contact
    added/removed, trust requests, name registration result, composing/typing indicator, buddy
    presence, file transfer progress, video capture/decoding signals).
  - `JamiCallbackAdapter.kt` — subclasses of all 5 SWIG director callback classes Meshly consumes
    (`Callback`, `ConfigurationCallback`, `PresenceCallback`, `DataTransferCallback`,
    `VideoCallback` forward into `RealJamiEvent`; `ConversationCallback`/`NetworkServiceCallback`
    are still empty no-op stubs since Meshly doesn't consume those signals yet).

None of this compiles right now — `net.jami.daemon.*` (JamiServiceJNI, Callback,
ConfigurationCallback, ...) only exists after `bin/jni/make-swig.sh` actually runs, which only
happens as part of a real CMake build with `JAMI_JNI=On`. That's fine: since `daemon` isn't in
`settings.gradle.kts`, nothing tries to compile it yet.

## Known uncertainties (verify once you can actually build)

These couldn't be confirmed without running SWIG for real, so they're flagged in code comments
too — double check them against the generated `net/jami/daemon/*.java` files:

- `sendAccountTextMessage`'s `flag` parameter meaning (currently passing `0`)
- `VectMap`'s exact API: assumed index/size-based (`size()`, `get(int)`, `add(T)`) rather than a
  `java.util.List`, based on jni_interface.i's own `toNative()` helper for `vector<map<string,
  string>>` using that shape — but whether it also implements `java.lang.Iterable`/`List` (which
  would additionally allow `for`/`.map{}`) isn't confirmed. `StringMap` is on firmer ground: the
  same file's `map<string, string>` javacode block uses `entrySet()`/`put()`/`get()` directly, so
  it's treated here as a real `java.util.Map<String, String>`, not guessed. Same assumption
  extends to `StringVect` (`RealJamiBridge.setSubscriptions`).
- `fileTransferInfo`'s SWIG `OUTPUT`-typemapped `int64_t& total_out`/`progress_out` params: the
  .i file confirms the pattern generates array-based Java out-params (explicitly shown for the
  `std::string& OUTPUT` one, which becomes `String[]`), but the exact array element type for the
  two `int64_t` ones (`long[]` assumed in `RealJamiBridge.fileTransferInfo`) isn't confirmed.
- `registerName`'s `scheme`/`password` parameters — `RealJamiBridge.registerName` passes empty
  strings for both, which is assumed fine for the default Jami name server but hasn't been
  confirmed against a real running daemon
- `VideoCallback.getCameraInfo`'s director signature (`std::vector<int> *formats,
  std::vector<unsigned> *sizes, std::vector<unsigned> *rates` — raw out-pointers, not the
  `int32_t`/`uint32_t` vector aliases `IntVect`/`UintVect` are templated from) is the one place
  in the whole API surface this pass couldn't cross-check as confidently as everything else;
  `MeshlyVideoCallback.getCameraInfo` assumes SWIG maps it to `IntVect`/`UintVect` params anyway,
  filled via `.add()`

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
   were written against the mock's jamiId-centric model; `RealJamiBridge.getJamiId(accountId)`
   already does the accountId → Jami ID lookup (via `getAccountDetails`'s `Account.username` key)
   needed to bridge the two, but the repositories themselves haven't been touched yet.

## Reference: real libjami API surface used here

Pulled directly from `native/upstream/jami-daemon/bin/jni/*.i` (SWIG module `JamiService`):

| Concern | Real call |
|---|---|
| Start/stop daemon | `JamiService.init(cfgCb, callCb, presCb, dataCb, videoCb, convCb, netCb)` / `JamiService.fini()` |
| Create account | `JamiService.getAccountTemplate("RING")` → fill in `Account.alias` → `JamiService.addAccount(details)` |
| List accounts | `JamiService.getAccountList()` |
| Get the actual Jami ID | `JamiService.getAccountDetails(accountId)`'s `Account.username` key — NOT the `accountId` param itself, that's a separate internal id (`RealJamiBridge.getJamiId`) |
| Registration/device state | `JamiService.getVolatileAccountDetails(accountId)` — `Account.registeredName`/`Account.registrationStatus`/`Account.deviceAnnounced` |
| Register username | `JamiService.registerName(accountId, name, scheme, password)`, result via `ConfigurationCallback.nameRegistrationEnded` |
| Send message | `JamiService.sendAccountTextMessage(accountId, to, StringMap("text/plain" -> body), flag)` |
| Message history | `JamiService.getLastMessages(accountId, baseTimestampMs)` → `MessageVect` of `libjami::Message` (`from`/`payloads`/`received`) |
| Typing indicator | `JamiService.setIsComposing(accountId, conversationUri, bool)`; incoming via `ConfigurationCallback.composingStatusChanged` |
| Mark read / receipts | `JamiService.setMessageDisplayed(accountId, conversationUri, messageId, status)` — status is `libjami::Account::MessageStates` (UNKNOWN=0, SENDING=1, SENT=2, DISPLAYED=3, FAILURE=4, CANCELLED=5) — no separate DELIVERED |
| Message delivery status by id | `JamiService.getMessageStatus(accountId, id)` — CAUTION: not confirmed to share an id space with the string `messageId` used elsewhere |
| Place call | `JamiService.placeCallWithMedia(accountId, to, VectMap of StringMap media attributes)` |
| Accept/reject/hang up | `JamiService.accept/refuse/hangUp(accountId, callId)` |
| Hold/resume | `JamiService.hold`/`resume(accountId, callId)` |
| Mute (local) | `JamiService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO"/"MEDIA_TYPE_VIDEO", bool)` |
| Call state / details | `JamiService.getCallList(accountId)` / `getCallDetails(accountId, callId)` — keys: `CALL_TYPE` ("0"/"1"/"2"), `PEER_NUMBER`, `DISPLAY_NAME`, `CALL_STATE`, `TIMESTAMP_START`, `ACCOUNTID`, `AUDIO_MUTED`/`VIDEO_MUTED`/`AUDIO_ONLY` (`Call::Details`, call_const.h / `Call::getDetails()`, call.cpp) |
| Call state values | `libjami::Call::StateEvent` (call_const.h): `INCOMING`/`CONNECTING`/`RINGING`/`CURRENT`/`HUNGUP`/`BUSY`/`PEER_BUSY`/`FAILURE`/`HOLD`/`INACTIVE`/`OVER` |
| Peer hold/mute events | `Callback.peerHold(callId, holding)` / `audioMuted`/`videoMuted(callId, muted)` — no `accountId` param on these three |
| Media renegotiation | `Callback.mediaNegotiationStatus(callId, event, mediaList)` (event: `NEGOTIATION_SUCCESS`/`NEGOTIATION_FAIL`) / `Callback.mediaChangeRequested(accountId, callId, mediaList)` → answer with `JamiService.answerMediaChangeRequest(accountId, callId, mediaList)` |
| Add/remove contact | `JamiService.addContact(accountId, uri)` / `removeContact(accountId, uri, ban)` |
| List contacts | `JamiService.getContacts(accountId)` / `getContactDetails(accountId, uri)` — keys: `id`, `added`, `removed`, `conversationId`, `confirmed`, `banned` (`Contact::toMap()`, jami_contact.h) |
| Pending incoming requests | `JamiService.getTrustRequests(accountId)` — keys: `from`, `received`, `payload`, `conversationId` (`libjami::Account::TrustRequest`, account_const.h) |
| Accept/reject a request | `JamiService.acceptTrustRequest` / `discardTrustRequest(accountId, from)` |
| (Re-)send a request | `JamiService.sendTrustRequest(accountId, to, Blob payload)` |
| Incoming call/message | Override `Callback.incomingCall` / `Callback.incomingMessage` / `ConfigurationCallback.incomingAccountMessage` |
| Registration state | Override `ConfigurationCallback.registrationStateChanged` |
| Contact added/removed events | Override `ConfigurationCallback.contactAdded` / `contactRemoved` |
| Publish own presence | `JamiService.publish(accountId, online, note)` |
| Track/untrack a buddy | `JamiService.subscribeBuddy(accountId, uri, flag)`; result via `PresenceCallback.newBuddyNotification(accountId, uri, status, lineStatus)` — `status` is `JamiAccount::PresenceState` (jamiaccount.h): DISCONNECTED=0, AVAILABLE=1, CONNECTED=2 |
| Legacy SIP presence list | `JamiService.getSubscriptions`/`setSubscriptions(accountId, ...)` — keys `Buddy`/`Status`/`LineStatus`, values incl. `Online`/`Offline` (`libjami::Presence::*_KEY`, presence_const.h) — not the signal to use for a Jami-only account, see `RealPresence.kt` |
| Send/receive a file | `JamiService.sendFile(accountId, conversationId, path, displayName, replyTo)` / `downloadFile(...)`; progress via `DataTransferCallback.dataTransferEvent(..., eventCode)` — `eventCode` is `libjami::DataTransferEventCode` (datatransfer_interface.h): invalid=0, created=1, unsupported=2, wait_peer_acceptance=3, wait_host_acceptance=4, ongoing=5, finished=6, closed_by_host=7, closed_by_peer=8, invalid_pathname=9, unjoinable_peer=10, timeout_expired=11 |
| Cancel / query a file transfer | `JamiService.cancelDataTransfer(accountId, conversationId, fileId)` / `fileTransferInfo(...)` — both return `libjami::DataTransferError`: success=0, unknown=1, io=2, invalid_argument=3 |
| Camera capability query | `Callback.getCameraInfo(device, formats, sizes, rates)` — synchronous out-param fill, answered via `RealJamiBridge.cameraProvider`, NOT a `RealJamiEvent` (see `RealVideoDevice.kt`) |
| Video capture/decode signals | `VideoCallback.setParameters`/`setBitrate`/`requestKeyFrame`/`startCapture`/`stopCapture`/`decodingStarted`/`decodingStopped` — all fire-and-forget, forwarded to `RealJamiEvent` normally |
| Video device management | `JamiService.setDefaultDevice`/`getDefaultDevice`/`addVideoDevice`/`removeVideoDevice`/`setDeviceOrientation`/`getSettings`/`applySettings`/`get`/`setDecodingAccelerated`/`get`/`setEncodingAccelerated`/`startAudioDevice`/`stopAudioDevice`/`openVideoInput`/`closeVideoInput`/`startLocalMediaRecorder`/`stopLocalRecorder` (all videomanager.i, `namespace libjami`) |
| Rendering a video stream to a Surface | NOT `JamiService.registerSinkTarget` (its `SinkTarget` param isn't SWIG-callable from Java) — instead `net.jami.daemon.JamiServiceJNI.acquireNativeWindow`/`setNativeWindowGeometry`/`registerVideoCallback`/`unregisterVideoCallback`/`releaseNativeWindow`, hand-written JNI natives in videomanager.i, on a different generated class than `JamiService`. Needs a real `Surface` from the UI layer; out of scope for this pass. |
| Feeding camera frames in | `net.jami.daemon.JamiServiceJNI.captureVideoFrame`/`captureVideoPacket`, called from real Android camera capture code (CameraX/Camera2) in response to `VideoCallback.startCapture`; out of scope for this pass |

`"RING"` as the account type string is real and current (`JamiAccount::ACCOUNT_TYPE_JAMI` in
`src/jamidht/jamiaccount_config.h` is literally `"RING"`, kept for on-disk config compatibility
with the project's pre-rebrand history) — not a leftover mistake in this scaffolding.
