// Android entry point. SDL3's SDLActivity loads libmain.so and calls SDL_main(argc, argv); the runtime's
// own main() is compiled as botw_main (-Dmain=botw_main, see CMakeLists.txt), same trick as the iOS build.
//   argv from the launcher:
//     --game <dir>            game folder (code/ content/ meta/)       -> passed through to the runtime
//     --user-root <dir>       saves/cache/log/settings.ini            -> BOTW_USER_ROOT
//     --orientation <o>       landscape | portrait | auto              -> SDL_HINT_ORIENTATIONS
//     --affinity <a>          auto | off | <cpu list, e.g. 4-7>        -> CPU affinity (see cpu_affinity.h)
//     --env KEY=VALUE         extra environment for the process (repeatable)
//     --gpu-driver-dir/-name  custom Vulkan driver (see android_vulkan.cpp), empty = system driver
//     --native-lib-dir, --tmp-lib-dir   paths libadrenotools needs
#include <SDL3/SDL_hints.h>
#include <android/log.h>
#include <pthread.h>
#include <sys/stat.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include "cpu_affinity.h"
#include "crash_report.h"

int botw_main(int argc, char** argv);

// Base of the guest address space, set by the runtime's mem::init() inside botw_main (extern "C" in ppc.h).
// Used only to tell the crash reporter which faults belong to the game and must go to the runtime's handler.
extern "C" uint8_t* g_mem;

namespace {
int g_pipe[2];

void* logcat_thread(void*) {
    char buf[1024];
    std::string line;
    ssize_t n;
    while ((n = read(g_pipe[0], buf, sizeof buf)) > 0) {
        for (ssize_t i = 0; i < n; i++) {
            if (buf[i] == '\n') {
                __android_log_write(ANDROID_LOG_INFO, "BotW", line.c_str());
                line.clear();
            } else line += buf[i];
        }
    }
    return nullptr;
}

// The runtime prints with printf/fprintf(stderr): send both to logcat (adb logcat -s BotW)
void redirect_stdio() {
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    if (pipe(g_pipe) != 0) return;
    dup2(g_pipe[1], STDOUT_FILENO);
    dup2(g_pipe[1], STDERR_FILENO);
    pthread_t t;
    pthread_create(&t, nullptr, logcat_thread, nullptr);
    pthread_detach(t);
}

// The runtime creates <user root> in botw_main, but the crash reporter is armed before that: create the folder
// (and its parent, e.g. /storage/emulated/0/BotWRecomp) now, so the first crash is not lost to a missing path.
std::string ensure_user_root() {
    const char* root = getenv("BOTW_USER_ROOT");
    if (!root || !*root) return std::string();
    std::string dir = root;
    for (size_t i = 1; i <= dir.size(); i++) {
        if (i == dir.size() || dir[i] == '/') mkdir(dir.substr(0, i).c_str(), 0755);
    }
    return dir;
}

}  // namespace

extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char** argv) {
    redirect_stdio();
    std::string orientation = "landscape", affinity = "auto";
    std::vector<char*> args;
    args.push_back(argv[0]);
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--user-root") && i + 1 < argc) setenv("BOTW_USER_ROOT", argv[++i], 1);
        else if (!strcmp(argv[i], "--orientation") && i + 1 < argc) orientation = argv[++i];
        else if (!strcmp(argv[i], "--affinity") && i + 1 < argc) affinity = argv[++i];
        else if (!strcmp(argv[i], "--env") && i + 1 < argc) {  // KEY=VALUE, e.g. driver switches (RADV_XCLIPSE_LOG=1)
            std::string kv = argv[++i];
            size_t eq = kv.find('=');
            if (eq != std::string::npos && eq > 0) setenv(kv.substr(0, eq).c_str(), kv.substr(eq + 1).c_str(), 1);
        }
        else if (!strcmp(argv[i], "--gpu-driver-dir") && i + 1 < argc) setenv("BOTW_GPU_DRIVER_DIR", argv[++i], 1);
        else if (!strcmp(argv[i], "--gpu-driver-name") && i + 1 < argc) setenv("BOTW_GPU_DRIVER_NAME", argv[++i], 1);
        else if (!strcmp(argv[i], "--native-lib-dir") && i + 1 < argc) setenv("BOTW_NATIVE_LIB_DIR", argv[++i], 1);
        else if (!strcmp(argv[i], "--tmp-lib-dir") && i + 1 < argc) setenv("BOTW_TMP_LIB_DIR", argv[++i], 1);
        else args.push_back(argv[i]);
    }
    args.push_back(nullptr);

    // SDLActivity otherwise allows every orientation (the window is resizable)
    const char* hint = orientation == "portrait" ? "Portrait"
                     : orientation == "auto"     ? "LandscapeLeft LandscapeRight Portrait"
                                                 : "LandscapeLeft LandscapeRight";
    SDL_SetHint(SDL_HINT_ORIENTATIONS, hint);

    // Crash reporter: armed before botw_main, so faults while SDL/JNI/the GPU driver are being set up are reported
    // too. botw_main installs the runtime's own handlers a few ms later (they replace ours), so re-arm from a helper
    // thread until that has happened; guest-memory faults are still handed to the runtime handler (see crash_report.h).
    const std::string userRoot = ensure_user_root();
    const std::string crashLog = userRoot.empty() ? std::string() : userRoot + "/crash.txt";
    botw::crash_report_install(crashLog.empty() ? nullptr : crashLog.c_str());
    std::thread([] {
        for (int i = 0; i < 13; i++) {
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
            botw::crash_report_install(nullptr);  // no-op unless the runtime replaced our handlers
            if (g_mem) botw::crash_report_set_guest_range((uintptr_t)g_mem, (1ull << 32) + 0x10000);
        }
    }).detach();

    cpuaff::apply(cpuaff::detect(affinity));

    int rc = botw_main((int)args.size() - 1, args.data());
    __android_log_print(ANDROID_LOG_INFO, "BotW", "botw_main returned %d", rc);
    return rc;
}
