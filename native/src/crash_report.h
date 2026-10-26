// Crash reporter for the Android build: logs PC/LR/SP, registers and a symbolized
// backtrace (module + offset, resolved from /proc/self/maps) for fatal signals,
// then, for faults it does not report (guest memory), chains to whatever handler
// was installed before — so the engine's own guest-memory fault handling keeps
// working. Faults it did report end with the default action (debuggerd tombstone).
#pragma once
#include <cstddef>
#include <cstdint>

namespace botw {

// Install (or re-arm) the reporter. Safe to call several times: if another
// component replaced our handlers in the meantime, they are chained again.
// `log_path` (optional): extra file that receives the report (e.g.
// "/storage/emulated/0/BotWRecomp/user/crash.txt"); the report always goes to
// logcat (tag "BotW") as well.
void crash_report_install(const char* log_path);

// Optional: tell the reporter where guest memory lives so that SIGSEGVs inside
// it (handled by the engine) are not reported. Without it only faults on
// addresses < 0x10000 (null derefs) and non-SEGV signals are reported.
void crash_report_set_guest_range(uintptr_t base, size_t size);

}  // namespace botw
