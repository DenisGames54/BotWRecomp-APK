# BotWRecomp-APK

Android launcher and packaging for [BotWRecomp-Android](https://github.com/DenisGames54/BotWRecomp-Android)
(the Android/Vulkan fork of [BotWRecomp](https://github.com/IshaanKalra2103/BotWRecomp)).

> [!IMPORTANT]
> **This project does NOT provide the game or any kind of protected file** (ROMs, keys, dumps, game code,
> or binaries derived from it). It is only a tool. You need your own legally obtained dump of
> *The Legend of Zelda: Breath of the Wild* (Wii U, USA, update 1.6.0 / v208), made from your own console.
> The APK is **generated on your own machine from your own dump** and **must not be distributed**.
> Do not ask for game files in issues or discussions.

> [!WARNING]
> Early stage. The launcher works; the native engine build (phase 2) is new and untested on device.

## What the launcher does
- Picks the game folder (`code/ content/ meta/`) **without copying anything**: the game is read straight from
  internal storage (needs "All files access").
- Verifies the SHA-256 of `U-King.rpx` (USA v208).
- Edits the runtime's real options by writing the same `settings.ini` the in-game menu uses:
  frame rate, resolution, display mode, upscale filter, touch controls, button layout, FPS counter, gyro,
  rumble, volume. It reloads the file when you come back, so changes made in-game show up here.
- User data (saves, cache, log, settings) lives in `/storage/emulated/0/BotWRecomp/user`.

## Repository layout
```
engine/    submodule: BotWRecomp-Android (runtime, Cemu GPU layer, recompiler)
app/       Kotlin + Compose launcher, GameActivity (SDL3)
native/    NDK CMake build of libmain.so, Android entry point, compat headers
scripts/   setup_wsl.sh, fetch_deps.sh, recompile_dump.sh, build_native.sh, build_apk.sh
patches/   small guarded changes the engine needs for Android (see below)
```

## Build (WSL2 on Windows, x86-64)
```bash
git clone --recurse-submodules https://github.com/DenisGames54/BotWRecomp-APK
cd BotWRecomp-APK
scripts/setup_wsl.sh                 # once: JDK 17, Android SDK, NDK 27, Gradle
source .botw-apk-env
scripts/build_apk.sh /path/to/U-King.rpx
# -> app/build/outputs/apk/debug/app-debug.apk
```
`U-King.rpx` is the only game file the build needs (the game folder itself is read on the phone at runtime).
The recompiled code goes to `work/generated/` and the APK that contains it stays on your own devices.

Engine patch: `patches/0001-engine-android-vulkan-surface.patch` removes the X11/Wayland surface code from
the Vulkan renderer on Android. Apply it once to the engine fork (it is guarded by `__ANDROID__`, so Linux
builds are unaffected):
```bash
cd engine && git apply ../patches/0001-engine-android-vulkan-surface.patch
git commit -am "android: Vulkan surface via SDL" && git push origin android-vulkan
cd .. && git add engine && git commit -m "Update engine"
```

## Roadmap
1. Launcher (done).
2. NDK build of the runtime + `GameActivity` (SDL3) + touch controls (in progress).
3. Personal-APK build script (`scripts/build_apk.sh`).
4. Import custom Turnip drivers (performance testing).

See `NOTICE` for credits.
