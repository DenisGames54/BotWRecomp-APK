// Host-side check (x86-64 Linux):
//   g++ -std=c++17 -I native/src native/src/crash_report.cpp native/tests/crash_report_test.cpp -o crash_test
// modes: null | abort | throw | guest | chain | norepeat
#include "crash_report.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <signal.h>
#include <unistd.h>

__attribute__((noinline)) static int deref_null_member(int* base) { return *(base + 0x6c); }  // +0x1b0
__attribute__((noinline)) static int outer(int* b) { return deref_null_member(b) + 1; }

// Handlers installed BEFORE the reporter, to check the chaining rules.
static void prev_exit42(int) { _exit(42); }                                // "chain": must be called
static void prev_marker(int) { (void)!write(1, "PREV\n", 5); _exit(43); }  // "norepeat": must NOT be called

static void install_prev(void (*fn)(int)) {
  struct sigaction sa;
  memset(&sa, 0, sizeof sa);
  sa.sa_handler = fn;
  sigemptyset(&sa.sa_mask);
  sigaction(SIGSEGV, &sa, nullptr);
}

int main(int argc, char** argv) {
  const char* mode = argc > 1 ? argv[1] : "null";
  if (!strcmp(mode, "chain")) install_prev(prev_exit42);
  if (!strcmp(mode, "norepeat")) install_prev(prev_marker);
  botw::crash_report_install("/tmp/crash_test_out.txt");

  if (!strcmp(mode, "abort")) abort();
  if (!strcmp(mode, "throw")) throw 42;
  if (!strcmp(mode, "guest") || !strcmp(mode, "chain")) {
    // Fault inside the "guest range": must NOT be reported, the previous handler must receive it.
    botw::crash_report_set_guest_range(0x7000000000ull, 1ull << 32);
    volatile int* p = (volatile int*)0x7000001000ull; *p = 1; return 0;
  }
  // "null" / "norepeat": host fault -> reported, and the previous handler must NOT run afterwards.
  return outer(nullptr);
}
