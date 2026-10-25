// Vulkan loader for the Android build (called from the vendored Cemu code, see patches/0005-*).
//
// Without a custom driver this is just the system libvulkan.so. With one selected in the launcher, the loader is a
// private copy of the system libvulkan opened by libadrenotools, which redirects the driver load to a library in
// the app's private storage (e.g. a Mesa Turnip build for Adreno GPUs).
//
// Environment (set by android_main.cpp from the launcher's arguments):
//   BOTW_GPU_DRIVER_DIR   directory with the driver .so (app-private storage; NOT sdcard)
//   BOTW_GPU_DRIVER_NAME  file name / soname of the driver library, e.g. vulkan.ad07XX.so
//   BOTW_NATIVE_LIB_DIR   applicationInfo.nativeLibraryDir (libadrenotools' hook libraries live there)
//   BOTW_TMP_LIB_DIR      writable directory (only used on API < 29)
#include <adrenotools/driver.h>
#include <dlfcn.h>

#include <cstdio>
#include <cstdlib>
#include <mutex>

extern "C" void* botw_android_open_vulkan() {
    static std::once_flag once;
    static void* handle = nullptr;
    std::call_once(once, [] {
        const char* dir = getenv("BOTW_GPU_DRIVER_DIR");
        const char* name = getenv("BOTW_GPU_DRIVER_NAME");
        const char* nativeDir = getenv("BOTW_NATIVE_LIB_DIR");
        const char* tmpDir = getenv("BOTW_TMP_LIB_DIR");
        if (dir && *dir && name && *name && nativeDir && *nativeDir) {
            fprintf(stderr, "gpu driver: loading custom driver %s/%s\n", dir, name);
            handle = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM,
                                                (tmpDir && *tmpDir) ? tmpDir : nullptr, nativeDir, dir, name, nullptr, nullptr);
            if (handle) fprintf(stderr, "gpu driver: custom libvulkan opened (check the 'Using GPU' / 'Driver version' lines)\n");
            else fprintf(stderr, "gpu driver: could not open the custom driver, using the system driver\n");
        } else {
            fprintf(stderr, "gpu driver: system driver\n");
        }
        if (!handle) handle = dlopen("libvulkan.so", RTLD_NOW);
    });
    return handle;
}
