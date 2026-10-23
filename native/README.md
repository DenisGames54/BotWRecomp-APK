# native/

Android (arm64-v8a) build of the runtime: `libmain.so` (+ `libSDL3.so`), built with the NDK by
`scripts/build_native.sh` and copied to `app/src/main/jniLibs/arm64-v8a/`.

- `CMakeLists.txt`  mirrors `engine/linux/CMakeLists.txt`; builds SDL3, fmt, zstd and glslang from source.
- `src/android_main.cpp`  `SDL_main` entry point (stdout/stderr -> logcat tag `BotW`; `--user-root`).
- `compat/`  `execinfo.h` (bionic only has `backtrace` from API 33), `openssl/` shim (SHA-1/SHA-256) and an
  empty `vk_platform_compat.h` (no X11/XCB/Wayland on Android).
- `tests/hash_test.cpp`  host-side check of the hash shim.

The guest code (`work/generated/`) is produced locally from your own dump by `scripts/recompile_dump.sh`
and is never committed (see `.gitignore`).
