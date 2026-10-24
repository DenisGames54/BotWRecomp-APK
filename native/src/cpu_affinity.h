// Finds the performance cores of the device and keeps the game on them.
//
// Detection order (first source that is readable for every core and tells the cores apart):
//   1. /sys/devices/system/cpu/cpuN/cpu_capacity        (scheduler capacity, 0..1024)
//   2. /sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq
//   3. "CPU part" of each core in /proc/cpuinfo          (always readable; mapped to a Cortex-A/X rank)
// The slowest class of cores (the little cluster) is left out; the fastest core is the "prime" core.
// No source readable on a device with 8 cores: assume 4 little + 4 big (cores 4-7).
//
// Portable (Linux / Android); written to be reusable by the main engine instead of the taskset in botw-run.sh.
#pragma once
#include <sched.h>

#include <string>
#include <vector>

namespace cpuaff {

struct CpuInfo {  // -1 = unknown
    long cap = -1;
    long freq = -1;
    int part = -1;
};

struct Plan {
    std::vector<int> bigCpus;
    int prime = -1;
    bool valid = false;
    std::string source;  // which detection source decided (for logs)
};

// Pure selection logic (unit tested). mode: "auto" or an explicit CPU list such as "4-7".
Plan choose(const std::vector<CpuInfo>& cpus, const std::string& mode);

// Reads the topology from sysfs and /proc/cpuinfo.
std::vector<CpuInfo> read_topology();

// mode: "auto" | "off" | cpu list. Returns an invalid plan for "off".
Plan detect(const std::string& mode);

// Keeps the calling thread (and every thread it creates from now on) on the plan's cores and starts a monitor
// that pins the busiest thread (the guest main thread) to the prime core, re-evaluating it as the load moves.
void apply(const Plan& plan);

}  // namespace cpuaff
