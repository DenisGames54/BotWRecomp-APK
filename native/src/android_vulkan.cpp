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
#include <android/api-level.h>
#include <dlfcn.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>

#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <string>

extern "C" void* botw_android_open_vulkan() {
    static std::once_flag once;
    static void* handle = nullptr;
    std::call_once(once, [] {
        const char* dir = getenv("BOTW_GPU_DRIVER_DIR");
        const char* name = getenv("BOTW_GPU_DRIVER_NAME");
        const char* nativeDir = getenv("BOTW_NATIVE_LIB_DIR");
        const char* tmpDir = getenv("BOTW_TMP_LIB_DIR");
        if (dir && *dir && name && *name && nativeDir && *nativeDir) {
            // libadrenotools builds the driver path as (customDriverDir + customDriverName): the directory MUST end with '/'
            // (without it the stat() inside adrenotools_open_libvulkan fails and the custom driver is silently skipped).
            std::string driverDir = dir;
            if (driverDir.back() != '/') driverDir += '/';
            std::string hookDir = nativeDir;
            const std::string driverPath = driverDir + name;
            fprintf(stderr, "gpu driver: loading custom driver %s (api %d)\n", driverPath.c_str(), android_get_device_api_level());

            // Pre-flight, so a failure says what is wrong instead of just "returned null"
            struct stat st {};
            if (stat(driverPath.c_str(), &st) != 0) fprintf(stderr, "gpu driver: driver file not accessible: %s (errno %d: %s)\n", driverPath.c_str(), errno, strerror(errno));
            else fprintf(stderr, "gpu driver: driver file ok, %lld bytes\n", (long long)st.st_size);
            for (const char* lib : {"libhook_impl.so", "libmain_hook.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so"}) {
                std::string p = hookDir + "/" + lib;
                if (stat(p.c_str(), &st) != 0) fprintf(stderr, "gpu driver: hook library missing: %s (errno %d)\n", p.c_str(), errno);
            }

            handle = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM,
                                                (tmpDir && *tmpDir) ? tmpDir : nullptr, hookDir.c_str(),
                                                driverDir.c_str(), name, nullptr, nullptr);
            if (handle) fprintf(stderr, "gpu driver: custom libvulkan opened (confirm with the 'Using GPU' / 'Driver version' lines)\n");
            else fprintf(stderr, "gpu driver: adrenotools_open_libvulkan returned null: could not open the custom driver, using the SYSTEM driver\n");
        } else {
            fprintf(stderr, "gpu driver: system driver\n");
        }
        if (!handle) handle = dlopen("libvulkan.so", RTLD_NOW);
    });
    return handle;
}
