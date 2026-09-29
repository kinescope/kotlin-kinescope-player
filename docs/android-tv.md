# Android TV (Google Cast receiver)

The Kinescope Android SDK runs in your **phone / tablet** app and can **cast** playback to an **Android TV** (or other Google Cast) device. The TV acts as a **Cast receiver**; this SDK is not a Leanback / Android TV app that runs on the TV itself.

Verified against **NVIDIA SHIELD** with the Kinescope custom Cast receiver.

## What you get

- Cast button in `KinescopePlayerView` (shown only while a Cast device is reachable)
- Device picker via Google Cast / MediaRouter
- Playback on the TV: DASH / HLS manifests with correct MIME types, Widevine license when the video is DRM-protected
- On-phone cast overlay: play / pause / replay, seek, stop
- Position sync when joining or leaving a Cast session
- New `loadVideo` while casting sends the video to the receiver

Integration steps and API: [Chromecast](chromecast.md).

## Requirements

1. Google Play services on the **sender** (phone/tablet).
2. TV / box with **Google Cast** (Chromecast built-in, Android TV with Cast, SHIELD, etc.).
3. Phone and TV on the **same LAN** (multicast / mDNS must work).

## Common setup issues on Android TV

| Symptom | Likely cause |
|---------|----------------|
| Empty device picker | VPN on the TV or phone; guest Wi‑Fi / client isolation; different subnet |
| Cast connects but splash / no video | Fixed in 0.1.18+ (manifest MIME / HLS fMP4 / DRM license routing) — use current SDK |
| Cast works in YouTube but not in your app | Network OK; check receiver app id / library Cast options |

Detailed network troubleshooting (VPN, mDNS, default receiver test): [Chromecast → Troubleshooting](chromecast.md#).

## Not in scope

- Native **Android TV / Leanback** UI running on the TV
- D-pad-first chrome without a phone sender
- Casting from Shorts feed (use the main player + `KinescopeCastSession`)

If you need a full leanback experience on the TV, that is a separate product surface from this Cast sender SDK.
