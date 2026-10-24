// Host-side test of the core selection:  g++ -std=c++20 -I native/src native/src/cpu_affinity.cpp native/tests/cpu_affinity_test.cpp
#include <stdio.h>
#include "cpu_affinity.h"
using namespace cpuaff;

static int fails = 0;
static void expect(const char* name, const Plan& p, std::vector<int> big, int prime) {
    bool ok = p.valid && p.bigCpus == big && p.prime == prime;
    printf("%-34s %s (source %s, prime %d)\n", name, ok ? "ok" : "FAIL", p.source.c_str(), p.prime);
    fails += !ok;
}

int main() {
    // Snapdragon 7+ Gen 2: 4x A510 (0-3), 3x A710 (4-6), 1x X2 (7)
    auto mk = [](bool cap, bool freq, bool part) {
        std::vector<CpuInfo> v(8);
        for (int c = 0; c < 8; c++) {
            if (c < 4) { if (cap) v[c].cap = 400; if (freq) v[c].freq = 2000000; if (part) v[c].part = 0xd46; }
            else if (c < 7) { if (cap) v[c].cap = 800; if (freq) v[c].freq = 2496000; if (part) v[c].part = 0xd47; }
            else { if (cap) v[c].cap = 1024; if (freq) v[c].freq = 2918400; if (part) v[c].part = 0xd48; }
        }
        return v;
    };
    expect("SD7+G2 via cpu_capacity", choose(mk(true, true, true), "auto"), {4, 5, 6, 7}, 7);
    expect("SD7+G2 via max freq only", choose(mk(false, true, true), "auto"), {4, 5, 6, 7}, 7);
    expect("SD7+G2 via /proc/cpuinfo only", choose(mk(false, false, true), "auto"), {4, 5, 6, 7}, 7);
    expect("SD7+G2 nothing readable", choose(mk(false, false, false), "auto"), {4, 5, 6, 7}, 7);
    expect("explicit list 5-7", choose(mk(true, true, true), "5-7"), {5, 6, 7}, 7);
    {   // Snapdragon 8 Gen 3: 2x A520 (0-1), 5x A720 (2-6), 1x X4 (7)
        std::vector<CpuInfo> v(8);
        for (int c = 0; c < 8; c++) v[c].part = c < 2 ? 0xd80 : (c < 7 ? 0xd81 : 0xd82);
        expect("SD8G3 via /proc/cpuinfo", choose(v, "auto"), {2, 3, 4, 5, 6, 7}, 7);
    }
    {   // homogeneous 4 cores
        std::vector<CpuInfo> v(4);
        for (int c = 0; c < 4; c++) v[c].freq = 2000000;
        expect("homogeneous", choose(v, "auto"), {0, 1, 2, 3}, 3);
    }
    Plan off = choose(mk(true, true, true), "off");
    printf("%-34s %s\n", "off", off.valid ? "FAIL" : "ok"); fails += off.valid;
    printf(fails ? "CPU AFFINITY TEST FAILED\n" : "cpu affinity test OK\n");
    return fails;
}
