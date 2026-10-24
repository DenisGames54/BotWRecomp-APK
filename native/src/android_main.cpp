// Android entry point. SDL3's SDLActivity loads libmain.so and calls SDL_main(argc, argv); the runtime's
// own main() is compiled as botw_main (-Dmain=botw_main, see CMakeLists.txt), same trick as the iOS build.
//   argv from the launcher:
//     --game <dir>            game folder (code/ content/ meta/)       -> passed through to the runtime
//     --user-root <dir>       saves/cache/log/settings.ini            -> BOTW_USER_ROOT
//     --orientation <o>       landscape | portrait | auto              -> SDL_HINT_ORIENTATIONS
//     --affinity <a>          auto | off | <cpu list, e.g. 4-7>        -> CPU affinity (see below)
#include <SDL3/SDL_hints.h>
#include <android/log.h>
#include <dirent.h>
#include <pthread.h>
#include <sched.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
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

// ------------------------------------------------------------------ CPU affinity
// Same idea as linux/botw-run.sh: keep the whole process on the big cores (taskset -c 4-7 on a Snapdragon
// 7+ Gen 2) and, a few seconds in, pin the busiest thread (the guest main thread) to the prime core (7).
struct CpuPlan {
    cpu_set_t mask;
    int prime = -1;
    bool valid = false;
};

long read_long(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return -1;
    long v = -1;
    if (fscanf(f, "%ld", &v) != 1) v = -1;
    fclose(f);
    return v;
}

bool parse_cpulist(const char* s, std::vector<int>& out) {
    while (*s) {
        char* end;
        long a = strtol(s, &end, 10);
        if (end == s) return false;
        long b = a;
        if (*end == '-') { s = end + 1; b = strtol(s, &end, 10); }
        for (long c = a; c <= b && c < CPU_SETSIZE; c++) out.push_back((int)c);
        s = end;
        if (*s == ',') s++;
    }
    return !out.empty();
}

CpuPlan make_plan(const std::string& mode) {
    CpuPlan p;
    CPU_ZERO(&p.mask);
    if (mode == "off") return p;
    int n = (int)sysconf(_SC_NPROCESSORS_CONF);
    std::vector<long> freq(n, -1);
    for (int c = 0; c < n; c++) {
        char path[96];
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", c);
        freq[c] = read_long(path);
    }
    std::vector<int> cpus;
    if (mode != "auto" && parse_cpulist(mode.c_str(), cpus)) {
        // explicit list
    } else {
        long minf = -1;
        bool all = n > 0;
        for (int c = 0; c < n; c++) { if (freq[c] <= 0) all = false; else if (minf < 0 || freq[c] < minf) minf = freq[c]; }
        if (all) {
            for (int c = 0; c < n; c++) if (freq[c] > minf) cpus.push_back(c);   // everything but the little cluster
        } else if (n >= 8) {
            cpus = {4, 5, 6, 7};                                                 // cpufreq not readable: assume 4 little + 4 big
        }
    }
    if (cpus.empty()) return p;
    for (int c : cpus) CPU_SET(c, &p.mask);
    p.prime = cpus.back();
    long best = -1;
    for (int c : cpus) if (freq[c] >= best && freq[c] > 0) { best = freq[c]; p.prime = c; }  // highest max frequency (ties: highest index)
    p.valid = true;
    return p;
}

bool thread_ticks(int tid, unsigned long long& ticks) {  // utime + stime from /proc/self/task/<tid>/stat
    char path[64], buf[1024];
    snprintf(path, sizeof path, "/proc/self/task/%d/stat", tid);
    FILE* f = fopen(path, "r");
    if (!f) return false;
    size_t n = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[n] = 0;
    char* p = strrchr(buf, ')');                  // comm may contain spaces/parentheses
    if (!p) return false;
    unsigned long long ut = 0, st = 0;
    // after ") ": state(3) ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt utime(14) stime(15)
    if (sscanf(p + 2, "%*c %*d %*d %*d %*d %*d %*u %*u %*u %*u %*u %llu %llu", &ut, &st) != 2) return false;
    ticks = ut + st;
    return true;
}

std::vector<int> list_threads() {
    std::vector<int> v;
    if (DIR* d = opendir("/proc/self/task")) {
        while (dirent* e = readdir(d)) if (e->d_name[0] != '.') v.push_back(atoi(e->d_name));
        closedir(d);
    }
    return v;
}

CpuPlan g_plan;

void* heavy_thread_pinner(void*) {
    sleep(4);  // let the guest start its threads, like the sleep in botw-run.sh
    std::vector<int> tids = list_threads();
    std::vector<unsigned long long> t0(tids.size(), 0);
    for (size_t i = 0; i < tids.size(); i++) thread_ticks(tids[i], t0[i]);
    sleep(2);
    int self = (int)gettid(), best_tid = -1;
    unsigned long long best = 0;
    for (size_t i = 0; i < tids.size(); i++) {
        unsigned long long t1;
        if (tids[i] == self || !thread_ticks(tids[i], t1)) continue;
        if (t1 - t0[i] > best) { best = t1 - t0[i]; best_tid = tids[i]; }
    }
    if (best_tid > 0) {
        cpu_set_t one;
        CPU_ZERO(&one);
        CPU_SET(g_plan.prime, &one);
        int rc = sched_setaffinity(best_tid, sizeof one, &one);
        fprintf(stderr, "affinity: busiest thread %d (%llu ticks in 2 s) -> cpu %d (rc %d)\n", best_tid, best, g_plan.prime, rc);
    } else {
        fprintf(stderr, "affinity: no busy thread found\n");
    }
    return nullptr;
}

void apply_affinity(const std::string& mode) {
    g_plan = make_plan(mode);
    if (!g_plan.valid) { fprintf(stderr, "affinity: off\n"); return; }
    // The mask is inherited by every thread the runtime creates from here on, like `taskset -c` on the whole process.
    int rc = sched_setaffinity(0, sizeof g_plan.mask, &g_plan.mask);
    fprintf(stderr, "affinity: process on cpus");
    for (int c = 0; c < CPU_SETSIZE; c++) if (CPU_ISSET(c, &g_plan.mask)) fprintf(stderr, " %d", c);
    fprintf(stderr, " (prime %d, rc %d)\n", g_plan.prime, rc);
    pthread_t t;
    if (pthread_create(&t, nullptr, heavy_thread_pinner, nullptr) == 0) pthread_detach(t);
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
        else args.push_back(argv[i]);
    }
    args.push_back(nullptr);

    // SDLActivity otherwise allows every orientation (the window is resizable)
    const char* hint = orientation == "portrait" ? "Portrait"
                     : orientation == "auto"     ? "LandscapeLeft LandscapeRight Portrait"
                                                 : "LandscapeLeft LandscapeRight";
    SDL_SetHint(SDL_HINT_ORIENTATIONS, hint);

    apply_affinity(affinity);

    int rc = botw_main((int)args.size() - 1, args.data());
    __android_log_print(ANDROID_LOG_INFO, "BotW", "botw_main returned %d", rc);
    return rc;
}
