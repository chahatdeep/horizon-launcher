# Horizon

An Android home-screen launcher (`com.praiselab.horizon`): clock and agenda, live tiles, folders, widgets, an app drawer with search, and optional Microsoft To Do / calendar sign-in (you enter your own client ID; no secret ships in the app).

<img src="docs/home.jpg" alt="Horizon home screen with a clock, weather, music controls and app folders (personal details blurred)" width="300">

Download the APK from [my-apps releases](https://github.com/chahatdeep/my-apps/releases?q=horizon).

## Build

No Gradle. From PowerShell:

```powershell
.\android\build.ps1
```

It copies `web/index.html` into the app's assets, compiles, and signs `Horizon.apk`. Signing uses a local keystore that is not in this repo; updates must be signed with the same key to install over an existing copy.

Requires Android 11 (API 30) or later.
