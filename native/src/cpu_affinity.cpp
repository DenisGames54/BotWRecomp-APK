#include "cpu_affinity.h"

#include <dirent.h>
#include <pthread.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>

namespace cpuaff {
namespace {

long read_long(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return -1;
    long v = -1;
    if (fscanf(f, "%ld", &v) != 1) v = -1;
    fclose(f);
    return v;
}

// Higher = bigger core. 0xd.. are Arm Ltd. part numbers ("CPU part" in /proc/cpuinfo).
int part_rank(int part) {
    switch (part) {
        case 0xd03: return 5;   // Cortex-A53
        case 0xd05: return 12;  // Cortex-A55
        case 0xd46: return 10;  // Cortex-A510
        case 0xd80: return 11;  // Cortex-A520
        case 0xd07: return 20;  // Cortex-A57
        case 0xd08: return 21;  // Cortex-A72
        case 0xd09: return 22;  // Cortex-A73
        case 0xd0a: return 30;  // Cortex-A75
        case 0xd0b: return 31;  // Cortex-A76
        case 0xd0d: return 32;  // Cortex-A77
        case 0xd41: return 40;  // Cortex-A78
        case 0xd4b: return 40;  // Cortex-A78C
        case 0xd47: return 41;  // Cortex-A710
        case 0xd4d: return 42;  // Cortex-A715
        case 0xd81: return 43;  // Cortex-A720
        case 0xd87: return 44;  // Cortex-A725
        case 0xd44: return 50;  // Cortex-X1
        case 0xd4c: return 50;  // Cortex-X1C
        case 0xd48: return 51;  // Cortex-X2
        case 0xd4e: return 52;  // Cortex-X3
        case 0xd82: return 53;  // Cortex-X4
        case 0xd85: return 54;  // Cortex-X925
        default: return -1;
    }
}

bool parse_cpulist(const std::string& str, std::vector<int>& out) {
    const char* s = str.c_str();
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

// A score vector tells the cores apart when it is fully known and not constant.
bool usable(const std::vector<long>& v) {
    if (v.empty()) return false;
    for (long x : v) if (x < 0) return false;
    return std::any_of(v.begin(), v.end(), [&](long x) { return x != v[0]; });
}

bool all_known(const std::vector<long>& v) {
    if (v.empty()) return false;
    for (long x : v) if (x < 0) return false;
    return true;
}

}  // namespace

Plan choose(const std::vector<CpuInfo>& cpus, const std::string& mode) {
    Plan p;
    const int n = (int)cpus.size();
    if (n == 0 || mode == "off") return p;

    std::vector<long> cap(n), freq(n), rank(n);
    for (int i = 0; i < n; i++) { cap[i] = cpus[i].cap; freq[i] = cpus[i].freq; rank[i] = part_rank(cpus[i].part); }

    // score used to split little / big, and to find the prime core
    const std::vector<long>* score = nullptr;
    if (usable(cap)) { score = &cap; p.source = "cpu_capacity"; }
    else if (usable(freq)) { score = &freq; p.source = "cpuinfo_max_freq"; }
    else if (usable(rank)) { score = &rank; p.source = "cpuinfo part"; }

    auto better = [&](int a, int b) {  // is core a faster than core b?
        if (score && (*score)[a] != (*score)[b]) return (*score)[a] > (*score)[b];
        if (freq[a] != freq[b]) return freq[a] > freq[b];
        return a > b;
    };

    std::vector<int> cpuList;
    if (mode != "auto" && parse_cpulist(mode, cpuList)) {
        cpuList.erase(std::remove_if(cpuList.begin(), cpuList.end(), [&](int c) { return c >= n; }), cpuList.end());
        p.source = "explicit list";
    } else if (score) {
        long minScore = *std::min_element(score->begin(), score->end());
        for (int c = 0; c < n; c++) if ((*score)[c] > minScore) cpuList.push_back(c);
    } else if (all_known(cap) || all_known(freq)) {
        for (int c = 0; c < n; c++) cpuList.push_back(c);  // homogeneous CPU
        p.source = "homogeneous";
    } else if (n >= 8) {
        cpuList = {4, 5, 6, 7};
        p.source = "assumed 4+4";
    }
    if (cpuList.empty()) return p;

    p.bigCpus = cpuList;
    p.prime = *std::min_element(cpuList.begin(), cpuList.end(), [&](int a, int b) { return better(a, b); });
    p.valid = true;
    return p;
}

std::vector<CpuInfo> read_topology() {
    int n = (int)sysconf(_SC_NPROCESSORS_CONF);
    if (n < 1) n = 1;
    std::vector<CpuInfo> v(n);
    for (int c = 0; c < n; c++) {
        char path[96];
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpu_capacity", c);
        v[c].cap = read_long(path);
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", c);
        v[c].freq = read_long(path);
    }
    if (FILE* f = fopen("/proc/cpuinfo", "r")) {
        char line[256];
        int cur = -1;
        while (fgets(line, sizeof line, f)) {
            if (!strncmp(line, "processor", 9)) {
                const char* colon = strchr(line, ':');
                cur = colon ? atoi(colon + 1) : -1;
            } else if (!strncmp(line, "CPU part", 8) && cur >= 0 && cur < n) {
                const char* colon = strchr(line, ':');
                if (colon) v[cur].part = (int)strtol(colon + 1, nullptr, 16);
            }
        }
        fclose(f);
    }
    return v;
}

Plan detect(const std::string& mode) {
    std::vector<CpuInfo> topo = read_topology();
    for (size_t c = 0; c < topo.size(); c++)
        fprintf(stderr, "cpu%zu: capacity %ld, max freq %ld kHz, part 0x%x\n", c, topo[c].cap, topo[c].freq, topo[c].part);
    return choose(topo, mode);
}

namespace {

Plan g_plan;
cpu_set_t g_bigMask;

bool thread_ticks(int tid, unsigned long long& ticks) {  // utime + stime from /proc/self/task/<tid>/stat
    char path[64], buf[1024];
    snprintf(path, sizeof path, "/proc/self/task/%d/stat", tid);
    FILE* f = fopen(path, "r");
    if (!f) return false;
    size_t n = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[n] = 0;
    char* p = strrchr(buf, ')');  // comm may contain spaces and parentheses
    if (!p) return false;
    unsigned long long ut = 0, st = 0;
    // after ") ": state ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt utime stime
    if (sscanf(p + 2, "%*c %*d %*d %*d %*d %*d %*u %*u %*u %*u %*u %llu %llu", &ut, &st) != 2) return false;
    ticks = ut + st;
    return true;
}

std::string thread_name(int tid) {
    char path[64], buf[32] = "?";
    snprintf(path, sizeof path, "/proc/self/task/%d/comm", tid);
    if (FILE* f = fopen(path, "r")) {
        if (fgets(buf, sizeof buf, f)) buf[strcspn(buf, "\n")] = 0;
        fclose(f);
    }
    return buf;
}

std::map<int, unsigned long long> sample_ticks() {
    std::map<int, unsigned long long> m;
    if (DIR* d = opendir("/proc/self/task")) {
        while (dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            int tid = atoi(e->d_name);
            unsigned long long t;
            if (thread_ticks(tid, t)) m[tid] = t;
        }
        closedir(d);
    }
    return m;
}

void pin(int tid, int cpu) {
    cpu_set_t one;
    CPU_ZERO(&one);
    CPU_SET(cpu, &one);
    int rc = sched_setaffinity(tid, sizeof one, &one);
    fprintf(stderr, "affinity: thread %d (%s) -> cpu %d (rc %d)\n", tid, thread_name(tid).c_str(), cpu, rc);
}

// Like the hand-made fix in linux/botw-run.sh (busiest thread -> prime core), but it keeps watching: the busiest
// thread differs between loading, menus and gameplay.
void* monitor(void*) {
    const double window = 2.0;
    const unsigned long long minTicks = (unsigned long long)(0.3 * window * sysconf(_SC_CLK_TCK));  // at least 30% of a core
    int pinned = -1;
    sleep(4);  // let the guest start its threads
    for (;;) {
        auto before = sample_ticks();
        sleep((unsigned)window);
        auto after = sample_ticks();
        const int self = (int)gettid();
        int best = -1;
        unsigned long long bestDelta = 0, pinnedDelta = 0;
        for (auto& kv : after) {
            auto it = before.find(kv.first);
            if (it == before.end() || kv.first == self) continue;
            unsigned long long d = kv.second - it->second;
            if (kv.first == pinned) pinnedDelta = d;
            if (d > bestDelta) { bestDelta = d; best = kv.first; }
        }
        // switch only when another thread is clearly busier than the one already on the prime core
        if (best > 0 && best != pinned && bestDelta >= minTicks && (pinned < 0 || bestDelta * 10 > pinnedDelta * 13)) {
            if (pinned > 0) sched_setaffinity(pinned, sizeof g_bigMask, &g_bigMask);  // give the old one the big cores back
            pin(best, g_plan.prime);
            pinned = best;
        }
        sleep(3);
    }
    return nullptr;
}

}  // namespace

void apply(const Plan& plan) {
    if (!plan.valid) { fprintf(stderr, "affinity: off\n"); return; }
    g_plan = plan;
    CPU_ZERO(&g_bigMask);
    for (int c : plan.bigCpus) CPU_SET(c, &g_bigMask);
    int rc = sched_setaffinity(0, sizeof g_bigMask, &g_bigMask);  // inherited by every thread created from here on
    fprintf(stderr, "affinity: source %s, process on cpus", plan.source.c_str());
    for (int c : plan.bigCpus) fprintf(stderr, " %d", c);
    fprintf(stderr, ", prime %d (rc %d)\n", plan.prime, rc);
    pthread_t t;
    if (pthread_create(&t, nullptr, monitor, nullptr) == 0) pthread_detach(t);
}

}  // namespace cpuaff
