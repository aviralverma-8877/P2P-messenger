# Testing this build

## Automated

- **Unit tests** (`./gradlew testDebugUnitTest`) run on the desktop JVM and cover pure logic
  only: `Ipv6RoutabilityTest` (IPv6 address classification) and `PairingCodecTest`
  (pairing-payload encode/decode round trips for both the SMS and BLE wire formats).
- **Instrumented test** (`./gradlew connectedAndroidTest`, needs an emulator or device attached)
  runs `SignalSessionRoundTripTest`, which exercises the real Signal Protocol handshake: two
  independent identities exchange a prekey bundle exactly as they would over BLE/SMS, then
  encrypt/decrypt a message in both directions. This can't be a plain unit test because
  `libsignal-android` ships native (.so) code built for Android ABIs, and the Keystore-backed
  encryption wrapping private key material needs a real `AndroidKeyStore`.

Both were actually run (once Android Studio was installed, giving access to a JDK and the
Android SDK): `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass. The
`libsignal-android` API surface initially guessed in `RoomSignalProtocolStore`/
`SignalSessionManager` didn't match reality in several places -- fixed by inspecting the real
resolved jar with `javap` and cross-checking against the `signalapp/libsignal` source on GitHub
for the exact version pulled in (`0.96.4`). Notably: `Curve`/`KeyHelper.generateIdentityKeyPair`
don't exist in this version (use `ECKeyPair.generate()` / `IdentityKeyPair.generate()`
directly); `SessionBuilder`/`SessionCipher` need *both* a local and remote address, in different
argument orders from each other; `saveIdentity` returns an `IdentityKeyStore.IdentityChange`
enum, not `Boolean`; `SignalProtocolStore` also extends `SenderKeyStore` (unused, stubbed
in-memory since this app has no group messaging); and `markKyberPreKeyUsed` takes 3 args and,
per its doc comment, just deletes a one-time Kyber prekey rather than flagging it "used".
`SignalSessionRoundTripTest` (the instrumented test that actually exercises this code) hasn't
been run yet -- that needs `./gradlew connectedAndroidTest` with an emulator/device attached,
which wasn't set up in this environment. Run that next if you want end-to-end confidence in the
crypto layer beyond "it compiles."

The one non-libsignal fix needed: several AndroidX libraries (`hilt-navigation-compose`,
`androidx.lifecycle`, `androidx.camera`, `androidx.navigation` newer releases) now require
AGP 9 / compileSdk 37+; this project intentionally stays on AGP 8.7.2, so those are pinned to
older releases in `gradle/libs.versions.toml` -- don't bump them without also migrating to AGP 9.

## What to check first if the build doesn't compile

In rough order of likelihood, given how this was written:

1. **`libsignal-android` API mismatches** in `crypto/RoomSignalProtocolStore.kt` and
   `crypto/SignalSessionManager.kt` -- if `libsignal-android` gets bumped to a newer version than
   `0.96.4`, re-verify with `javap` against the newly-resolved jar in
   `~/.gradle/caches/modules-2/files-2.1/org.signal/libsignal-client/<version>/` the same way
   this was fixed originally; don't just guess from documentation.
2. Gradle needing to re-resolve the wrapper the first time you open the project (no
   `gradle-wrapper.jar` is checked in since there was no local Gradle/JDK to generate one --
   Android Studio will offer to create it, or run `gradle wrapper` yourself once you have Gradle
   installed).
3. Version catalog (`gradle/libs.versions.toml`) pins that have since been superseded --
   `libsignal-android` in particular ships very frequent point releases.

## Manual end-to-end test plan (needs 2 physical Android devices)

Emulators typically can't do real BLE or send/receive real SMS, and most emulator networks
don't hand out IPv6 at all, so this part genuinely needs two phones.

1. **IPv6 check**: On each phone, connect to a network you expect to support IPv6 (many home
   Wi-Fi networks and most modern cellular carriers do; corporate/public Wi-Fi often doesn't).
   Open the app and confirm the home screen's status banner turns green with an address shown.
   If it's red, that's the app correctly telling you this network won't support a direct
   connection -- try a different network before continuing.
2. **BLE pairing (in range)**: On phone A, tap **+ > Bluetooth (nearby)**. Do the same on phone
   B. Each should see the other appear in the scan list within a few seconds; tap either side to
   pair. Both phones should land on a chat screen for the new contact.
3. **SMS pairing (out of range)**: On phone A, tap **+ > SMS (far away)**, enter phone B's real
   number, and send. Phone B should receive what looks like a normal SMS (it won't render as
   readable text -- that's expected, it's a JSON-ish payload) and the app should silently
   process it and open a chat screen automatically. Repeat in the other direction so both sides
   have each other as a contact (right now pairing is one-directional per send: A adding B does
   not automatically give A's contact info back to B unless B also sends theirs, or unless
   they pair via BLE where the exchange is mutual by design).
4. **Messaging**: Send a few messages each way in the chat screen. Confirm they arrive close to
   instantly when both phones are on networks that allow the direct connection, and confirm the
   "Connected"/"Offline" indicator in the chat header reflects reality.
5. **Reconnect**: Force-close the app on one phone (or toggle its Wi-Fi off/on), then reopen it
   and send another message. Confirm delivery resumes without needing to re-pair.
6. **Cellular caveat**: If step 4 works over Wi-Fi but messages never arrive when either phone
   is on cellular data, that's the known carrier-firewall limitation described in the project
   plan, not a bug to chase -- some carriers block unsolicited inbound connections to phones
   even when the phone has a real IPv6 address.
7. **Not yet functional, by design this pass**: the attach/camera icon in chat and the video
   call icon. Confirm they don't crash the app -- media sharing and video calling are
   intentionally scaffolded-but-not-wired-up in this build (see the plan and the TODOs in
   `media/MediaTransferManager.kt`, `call/WebRtcClient.kt`, and `ui/call/CallScreen.kt`).
