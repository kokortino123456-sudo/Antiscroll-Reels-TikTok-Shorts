# Klid

A free Android app that pulls you out of short-video feeds. No subscription, no ads,
no account, and no internet permission, so nothing can leave your phone.

- **YouTube Shorts** and **Instagram Reels**: Klid presses Back the moment the feed opens.
  The rest of the app keeps working.
- **TikTok**: a daily allowance in minutes (0 by default). When it is used up, Klid sends you Home.

## Install

1. Open the newest entry under **Releases** on your phone and download `klid.apk`.
2. Install it (allow your browser to install apps when asked).
3. Open Klid and tap **Turn on in Accessibility settings**, then enable Klid.
4. On Android 13 and newer the switch may be greyed out as a "restricted setting".
   Open Klid's App info, tap the three dots, choose **Allow restricted settings**, and try again.

## How it works

Klid is an accessibility service limited to YouTube, Instagram and TikTok. It recognises
the feeds by the view ids of their players (see `BlockerService.kt`). Those ids belong to
the other apps and can change when they update. If blocking stops working after an update,
the id lists in that file are the place to fix.

## Build

Every push to `main` builds the APK with GitHub Actions and publishes it as a release.
Builds are signed with the `debug.keystore` in this repo so each one installs over the last.
That key is public, so only install APKs from this repo's releases.
