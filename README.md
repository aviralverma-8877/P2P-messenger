# P2P Messenger

A serverless Android messenger: two phones exchange a pairing bundle out-of-band (encrypted
SMS if far apart, Bluetooth LE if nearby), then talk directly over a raw IPv6 socket with
Signal Protocol end-to-end encryption. No accounts, no central server.

See `.claude`-adjacent plan history or ask the assistant that built this for the full design
rationale; the short version is in `TESTING.md` and in doc comments on the riskier classes
(`crypto/RoomSignalProtocolStore.kt`, `call/WebRtcClient.kt`, `media/MediaTransferManager.kt`).

## Status

- **Working**: IPv6 capability check, BLE pairing (advertise/scan/GATT exchange), SMS pairing,
  direct IPv6 P2P connection with reconnect, Signal Protocol (X3DH/PQXDH + Double Ratchet)
  session establishment, end-to-end encrypted text messaging.
- **Scaffolded, not wired up yet**: photo/video sharing (`media/MediaTransferManager.kt`) and
  video calling (`call/WebRtcClient.kt`, `ui/call/CallScreen.kt`). Real UI and data flow exist;
  the actual byte-shuffling is marked with `TODO`.
- **Deliberately out of scope this pass**: standards-compliant Bluetooth Mesh. A basic
  gossip-relay design is documented in `discovery/mesh/BleMeshRelay.kt`.

## Building

Builds clean with `./gradlew assembleDebug` (verified against Android Studio's bundled JDK 21
and Android SDK 36 / build-tools 36). Unit tests (`./gradlew testDebugUnitTest`) pass too. Open
the project root in Android Studio and it should sync without extra setup beyond accepting SDK
license prompts.

Toolchain notes if you're setting this up elsewhere: AGP 8.7.2, Kotlin/KSP 2.2.20, `compileSdk
= 36` (matches what was locally installed -- drop back to 35 if you don't need the newer
platform). Hilt is pinned to 2.58, the last release before the Hilt Gradle plugin required
AGP 9 -- don't bump it without also moving to AGP 9 + Gradle 9. `libsignal-android`'s
`RoomSignalProtocolStore`/`SignalSessionManager` API calls were verified with `javap` against
the actual resolved 0.96.4 jar, not guessed -- if a future version shifts the API again, diff
against the real jar the same way rather than guessing from docs.

## Project layout

```
network/    IPv6 detection, the persistent listening socket, connection framing
discovery/  Pairing payload format + SMS and BLE exchange (+ mesh relay design)
crypto/     Signal Protocol store + session manager, all run offline (no Signal servers)
data/       Room database (contacts, messages, protocol key material)
repository/ Ties the above together for the UI layer
media/      Photo/video sharing (scaffolded)
call/       WebRTC video calling (scaffolded)
ui/         Jetpack Compose screens + navigation
di/         Hilt modules
```
