// Host-side check (x86-64 Linux): g++ -std=c++17 -I native/src native/src/crash_report.cpp native/tests/crash_report_test.cpp -o crash_test
#include "crash_report.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/mman.h>
#include <signal.h>
#include <unistd.h>

__attribute__((noinline)) static int deref_null_member(int* base) { return *(base + 0x6c); }  // +0x1b0
__attribute__((noinline)) static int outer(int* b) { return deref_null_member(b) + 1; }

int main(int argc, char** argv) {
  botw::crash_report_install("/tmp/crash_test_out.txt");
  const char* mode = argc > 1 ? argv[1] : "null";
  if (!strcmp(mode, "abort")) abort();
  if (!strcmp(mode, "throw")) throw 42;
  if (!strcmp(mode, "guest")) {  // fault inside "guest range" must NOT be reported, previous handler (ours below) handles it
    botw::crash_report_set_guest_range(0x7000000000ull, 1ull << 32);
    volatile int* p = (volatile int*)0x7000001000ull; *p = 1; return 0;
  }
  return outer(nullptr);
}
