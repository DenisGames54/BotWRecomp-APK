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
- Launcher-only options (not part of `settings.ini`): screen orientation (landscape / portrait / auto) and
  CPU affinity. The runtime keeps the game on the device's performance cores and pins its busiest thread (the
  guest main thread) to the fastest one, re-checking every few seconds. Cores are recognised from
  `cpu_capacity`, then `cpuinfo_max_freq`, then the CPU part in `/proc/cpuinfo` (so it still works when sysfs is
  not readable); the little cluster is left out. See `native/src/cpu_affinity.*` (unit test:
  `native/tests/cpu_affinity_test.cpp`). The decision is logged: `adb logcat -s BotW | grep -i -E "affinity|cpu[0-9]"`.
- The game window is fullscreen/immersive and extends under the camera cutout. The Vulkan swapchain is not
  pre-rotated: in landscape the compositor rotates the image (`patches/0003-*`).

## Custom GPU drivers (Turnip)
The launcher can import a custom Vulkan driver and use it instead of the phone's own (useful to compare FPS between
Mesa/Turnip builds on Adreno GPUs). Tap **Import driver (.zip)** in the *GPU driver* card:

- Package format: a `.zip` with `meta.json` (`name`, `vendor`, `driverVersion`, `libraryName`) and the driver library
  named by `libraryName` (e.g. `vulkan.ad07XX.so`, arm64). A zip with a single `.so` and no `meta.json` also works.
- Drivers are copied to the app's **private** storage (Android refuses to load libraries from shared storage).
- The selection takes effect on the next start. The loader is [libadrenotools](https://github.com/bylaws/libadrenotools)
  (BSD-2-Clause), which opens a private copy of the system `libvulkan.so` that runs the custom driver; the Vulkan
  surface is created through that same loader (`patches/0005-*`).
- Selecting a different driver clears the `cache` folder (compiled shaders) the next time you press PLAY; saves are not touched.
- Check which driver is in use in `runtime.log`: `Using GPU:` / `Driver version:`. The `BotW` logcat tag also has
  `gpu driver:` lines. If a custom driver cannot be opened the app falls back to the system driver (and logs it);
  if the game dies right after a driver change, the launcher offers **Use system driver**.
- Driver families are recognised from the package (Adreno/Turnip, Xclipse/RADV, Mali/PanVK); the launcher warns when
  a driver does not look right for the device or needs a newer Android (`minApi`).
- **Samsung Xclipse:** [JimVulkan/radv-xclipse](https://github.com/JimVulkan/radv-xclipse) releases. Import the plain
  `radv-xclipse-<version>.zip` (`vulkan.radeon.so`), not the `-wayland` one (that one is for Bannerlator's Wayland mode).
- **Mali:** [FristOneRR/FristOneRR-Panvk-Driver](https://github.com/FristOneRR/FristOneRR-Panvk-Driver) (Valhall G57/G68/G77/G78 and
  Bifrost; import the driver zip itself, `libvulkan_panfrost.so`) and [JimVulkan/mali-panvk](https://github.com/JimVulkan/mali-panvk)
  (Bifrost). Newer CSF GPUs (G610, G710, G715, Immortalis) are not supported by either. FristOneRR's variables: `PANVK_HEAP_MB`,
  `PANVK_TILER_HEAP_MB`, `PANVK_POLY_HEAP_MB`, `PANVK_STENCIL_LATE`, `PANVK_TRACE`.
- **Driver environment:** the selected driver has a *KEY=VALUE per line* box, applied to the game process
  (for example `RADV_XCLIPSE_LOG=1`, `PANVK_DEBUG=startup`, `TU_DEBUG=...`). `LD_*` variables are ignored.
- The Xclipse and PanVK paths use the same loader as Turnip but are untested by the author (no such device):
  please send `diagnostics.txt` (it lists the selected driver and the device's GPU properties) when reporting problems.

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

## Reading a native crash
A fatal signal prints `host frames (base 0x...)` in `runtime.log` / `diagnostics.txt`. After a build, the linker output with
symbols is kept as `build/native/libmain.unstripped.so` (the APK has a stripped copy). Turn those frames into functions with:
```bash
scripts/symbolize.sh diagnostics.txt     # must be the build that crashed
```

The Android entry point also arms its own reporter (`native/src/crash_report.cpp`) before `botw_main`, so faults during
SDL/JNI/driver setup and uncaught C++ exceptions are reported too. It prints `=== BotW crash report #N ===` with
PC/LR/SP/FP, x0-x29, a system unwind plus a frame-pointer walk (each frame as `module+0xoffset`), and appends the same
text to `<user root>/crash.txt` (included in `diagnostics.txt`). The runtime replaces the handlers a few ms later, so the
reporter re-arms itself and chains guest-memory faults back to the runtime, which keeps handling them as before.

## Roadmap
1. Launcher (done).
2. NDK build of the runtime + `GameActivity` (SDL3) + touch controls (in progress).
3. Personal-APK build script (`scripts/build_apk.sh`).
4. Import custom Turnip drivers (performance testing).

See `NOTICE` for credits.
