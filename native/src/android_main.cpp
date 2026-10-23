// Android entry point. SDL3's SDLActivity loads libmain.so and calls SDL_main(argc, argv); the runtime's
// own main() is compiled as botw_main (-Dmain=botw_main, see CMakeLists.txt), same trick as the iOS build.
//   argv: --game <dir with code/ content/ meta/>  --user-root <dir for saves/cache/log/settings.ini>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

int botw_main(int argc, char** argv);

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
}  // namespace

extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char** argv) {
    redirect_stdio();
    std::vector<char*> args;
    args.push_back(argv[0]);
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--user-root") && i + 1 < argc) setenv("BOTW_USER_ROOT", argv[++i], 1);
        else args.push_back(argv[i]);
    }
    args.push_back(nullptr);
    int rc = botw_main((int)args.size() - 1, args.data());
    __android_log_print(ANDROID_LOG_INFO, "BotW", "botw_main returned %d", rc);
    return rc;
}
