#include "crash_report.h"

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <ucontext.h>
#include <unistd.h>
#include <unwind.h>

#include <atomic>
#include <exception>

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace botw {
namespace {

constexpr int kSignals[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT, SIGTRAP};
constexpr int kNumSignals = sizeof(kSignals) / sizeof(kSignals[0]);

struct sigaction g_old[kNumSignals];
bool g_have_old[kNumSignals];
char g_log_path[256];
int g_log_fd = -1;
std::atomic<uintptr_t> g_guest_base{0};
std::atomic<size_t> g_guest_size{0};
std::atomic<int> g_reports{0};
std::atomic<int> g_in_report{0};
std::terminate_handler g_old_terminate = nullptr;

// ---- tiny async-signal-safe formatting helpers -------------------------------------------
struct Out {
  char buf[512];
  size_t n = 0;
  void flush() {
    if (!n) return;
#ifdef __ANDROID__
    size_t len = (buf[n - 1] == '\n') ? n - 1 : n;  // logcat adds its own newline
    char saved = buf[len];
    buf[len] = 0;
    __android_log_write(ANDROID_LOG_FATAL, "BotW", buf);
    buf[len] = saved;
#else
    (void)!write(2, buf, n);
#endif
    if (g_log_fd >= 0) {
      size_t off = 0;
      while (off < n) {
        ssize_t w = write(g_log_fd, buf + off, n - off);
        if (w <= 0) break;
        off += (size_t)w;
      }
    }
    n = 0;
  }
  void str(const char* s) {
    while (*s && n < sizeof(buf) - 2) buf[n++] = *s++;
  }
  void hex(uint64_t v) {
    static const char d[] = "0123456789abcdef";
    str("0x");
    bool started = false;
    for (int i = 60; i >= 0; i -= 4) {
      int x = (int)((v >> i) & 0xf);
      if (x || started || i == 0) {
        started = true;
        if (n < sizeof(buf) - 2) buf[n++] = d[x];
      }
    }
  }
  void dec(long v) {
    char t[24];
    int i = 0;
    bool neg = v < 0;
    unsigned long u = neg ? (unsigned long)(-v) : (unsigned long)v;
    do { t[i++] = (char)('0' + u % 10); u /= 10; } while (u);
    if (neg) str("-");
    while (i) { if (n < sizeof(buf) - 2) buf[n++] = t[--i]; }
  }
  void line() {
    if (n < sizeof(buf) - 1) buf[n++] = '\n';
    flush();
  }
};

// ---- /proc/self/maps lookup (open/read only) ---------------------------------------------
static bool parse_hex(const char*& p, uint64_t& out) {
  out = 0;
  bool any = false;
  for (;; ++p) {
    char c = *p;
    int v;
    if (c >= '0' && c <= '9') v = c - '0';
    else if (c >= 'a' && c <= 'f') v = 10 + c - 'a';
    else break;
    out = (out << 4) | (uint64_t)v;
    any = true;
  }
  return any;
}

static void skip_field(const char*& p) {
  while (*p && *p != ' ') ++p;
  while (*p == ' ') ++p;
}

// One /proc/self/maps line: "lo-hi perms offset dev inode   path". Fills out with path+0xfileoffset.
static bool match_line(const char* p, uintptr_t addr, char* out, size_t outsz) {
  uint64_t lo, hi, off;
  if (!parse_hex(p, lo) || *p != '-') return false;
  ++p;
  if (!parse_hex(p, hi) || addr < lo || addr >= hi) return false;
  skip_field(p);  // up to perms
  skip_field(p);  // perms -> offset
  if (!parse_hex(p, off)) return false;
  skip_field(p);  // offset -> dev
  skip_field(p);  // dev -> inode
  skip_field(p);  // inode -> path
  const char* name = *p ? p : "[anon]";
  size_t k = 0;
  while (name[k] && k < outsz - 24) { out[k] = name[k]; ++k; }
  static const char d[] = "0123456789abcdef";
  out[k++] = '+'; out[k++] = '0'; out[k++] = 'x';
  uint64_t rel = off + (addr - lo);
  bool started = false;
  for (int s = 60; s >= 0; s -= 4) {
    int x = (int)((rel >> s) & 0xf);
    if (x || started || s == 0) { started = true; out[k++] = d[x]; }
  }
  out[k] = 0;
  return true;
}

static bool resolve(uintptr_t addr, char* out, size_t outsz) {
  int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
  if (fd < 0) return false;
  char chunk[1024], line[512];
  size_t ll = 0;
  bool found = false;
  for (;;) {
    ssize_t r = read(fd, chunk, sizeof(chunk));
    if (r <= 0) break;
    for (ssize_t i = 0; i < r && !found; ++i) {
      if (chunk[i] == '\n') {
        line[ll] = 0;
        found = match_line(line, addr, out, outsz);
        ll = 0;
      } else if (ll < sizeof(line) - 1) {
        line[ll++] = chunk[i];
      }
    }
    if (found) break;
  }
  close(fd);
  return found;
}

// ---- unwinding ---------------------------------------------------------------------------
struct BtState { uintptr_t pcs[48]; int n; };
static _Unwind_Reason_Code unwind_cb(struct _Unwind_Context* c, void* arg) {
  auto* s = static_cast<BtState*>(arg);
  uintptr_t pc = _Unwind_GetIP(c);
  if (pc && s->n < 48) s->pcs[s->n++] = pc;
  return s->n >= 48 ? _URC_END_OF_STACK : _URC_NO_REASON;
}

static void print_frame(Out& o, int idx, uintptr_t pc) {
  char m[320];
  o.str("  #"); o.dec(idx); o.str(" pc "); o.hex(pc); o.str("  ");
  if (resolve(pc, m, sizeof(m))) o.str(m); else o.str("(unmapped)");
  o.line();
}

static void report(int sig, siginfo_t* info, void* uctx) {
  Out o;
  int seq = g_reports.fetch_add(1);
  o.str("=== BotW crash report #"); o.dec(seq); o.str(" ==="); o.line();
  o.str("signal "); o.dec(sig);
  if (info) { o.str(" code "); o.dec(info->si_code); o.str(" fault_addr "); o.hex((uint64_t)(uintptr_t)info->si_addr); }
  o.str(" tid "); o.dec((long)syscall(SYS_gettid)); o.line();

  uintptr_t pc = 0, lr = 0, sp = 0, fp = 0;
#if defined(__aarch64__)
  if (uctx) {
    auto* uc = static_cast<ucontext_t*>(uctx);
    const auto& m = uc->uc_mcontext;
    pc = (uintptr_t)m.pc; sp = (uintptr_t)m.sp; lr = (uintptr_t)m.regs[30]; fp = (uintptr_t)m.regs[29];
    o.str("pc "); o.hex(pc); o.str("  lr "); o.hex(lr); o.str("  sp "); o.hex(sp); o.str("  fp "); o.hex(fp); o.line();
    for (int i = 0; i < 30; i += 3) {
      o.str(" x"); o.dec(i); o.str("="); o.hex(m.regs[i]);
      o.str(" x"); o.dec(i + 1); o.str("="); o.hex(m.regs[i + 1]);
      o.str(" x"); o.dec(i + 2); o.str("="); o.hex(m.regs[i + 2]);
      o.line();
    }
  }
#elif defined(__x86_64__)
  if (uctx) {  // only used by the host-side unit test
    auto* uc = static_cast<ucontext_t*>(uctx);
    pc = (uintptr_t)uc->uc_mcontext.gregs[REG_RIP];
    sp = (uintptr_t)uc->uc_mcontext.gregs[REG_RSP];
    fp = (uintptr_t)uc->uc_mcontext.gregs[REG_RBP];
    o.str("pc "); o.hex(pc); o.str("  sp "); o.hex(sp); o.line();
  }
#endif
  int idx = 0;
  if (pc) { o.str("faulting frame:"); o.line(); print_frame(o, idx++, pc); }
  if (lr) print_frame(o, idx++, lr);

  // 1) system unwinder (uses .eh_frame; may stop early in generated code)
  BtState st; st.n = 0;
  _Unwind_Backtrace(unwind_cb, &st);
  o.str("unwinder frames ("); o.dec(st.n); o.str("):"); o.line();
  for (int i = 0; i < st.n; ++i) print_frame(o, i, st.pcs[i]);

  // 2) frame-pointer walk from the faulting context (works if code keeps frame pointers)
  if (fp && sp && fp >= sp && fp - sp < (1u << 20)) {
    o.str("frame-pointer walk:"); o.line();
    uintptr_t cur = fp;
    for (int i = 0; i < 24; ++i) {
      if ((cur & 7) || cur < sp || cur - sp > (8u << 20)) break;
      uintptr_t* f = reinterpret_cast<uintptr_t*>(cur);
      uintptr_t next = f[0], ret = f[1];
      if (!ret) break;
      print_frame(o, i, ret);
      if (next <= cur) break;
      cur = next;
    }
  }
  o.str("=== end of crash report ==="); o.line();
}

static int index_of(int sig) {
  for (int i = 0; i < kNumSignals; ++i) if (kSignals[i] == sig) return i;
  return -1;
}

static bool should_report(int sig, siginfo_t* info, bool has_prev_fn) {
  if (sig != SIGSEGV && sig != SIGBUS) return true;
  uintptr_t a = info ? (uintptr_t)info->si_addr : 0;
  if (a < 0x10000) return true;  // null deref: never a valid guest access
  uintptr_t gb = g_guest_base.load(); size_t gs = g_guest_size.load();
  if (gs) return !(a >= gb && a < gb + gs);
  return !has_prev_fn;  // nobody else will handle it -> we are the last chance
}

static void handler(int sig, siginfo_t* info, void* uctx) {
  int i = index_of(sig);
  if (i < 0) return;
  const struct sigaction& prev = g_old[i];
  bool has_prev_fn = g_have_old[i] && prev.sa_handler != SIG_DFL && prev.sa_handler != SIG_IGN;

  // Re-entrancy: a crash inside the reporter must not loop.
  if (g_in_report.exchange(1) == 0) {
    int saved_errno = errno;
    if (g_reports.load() < 32 && should_report(sig, info, has_prev_fn)) report(sig, info, uctx);
    errno = saved_errno;
    g_in_report.store(0);
  } else {
    signal(sig, SIG_DFL);
    return;
  }

  if (has_prev_fn) {
    if (prev.sa_flags & SA_SIGINFO) prev.sa_sigaction(sig, info, uctx);
    else prev.sa_handler(sig);
    return;
  }
  if (g_have_old[i] && prev.sa_handler == SIG_IGN) return;
  // Default action: let debuggerd write a full tombstone (visible via `adb logcat -b crash`).
  signal(sig, SIG_DFL);
  if (sig == SIGABRT || sig == SIGILL || sig == SIGTRAP || sig == SIGFPE) raise(sig);
  // For SIGSEGV/SIGBUS returning re-executes the faulting instruction -> default action.
}

static void terminate_hook() {
  Out o;
  o.str("=== std::terminate: uncaught C++ exception ==="); o.line();
  try {
    std::exception_ptr ep = std::current_exception();
    if (ep) std::rethrow_exception(ep);
  } catch (const std::exception& e) {
    o.str("what(): "); o.str(e.what()); o.line();
  } catch (...) {
    o.str("(non-std exception)"); o.line();
  }
  if (g_old_terminate) g_old_terminate();
  abort();
}

}  // namespace

void crash_report_set_guest_range(uintptr_t base, size_t size) {
  g_guest_base.store(base);
  g_guest_size.store(size);
}

void crash_report_install(const char* log_path) {
  if (log_path && *log_path && g_log_fd < 0) {
    strncpy(g_log_path, log_path, sizeof(g_log_path) - 1);
    g_log_fd = open(g_log_path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
  }
  // Alternate stack so stack-overflow crashes can still be reported (per calling thread).
  static thread_local bool alt_done = false;
  if (!alt_done) {
    static constexpr size_t kAlt = 64 * 1024;
    stack_t ss; ss.ss_sp = malloc(kAlt); ss.ss_size = kAlt; ss.ss_flags = 0;
    if (ss.ss_sp) sigaltstack(&ss, nullptr);
    alt_done = true;
  }
  for (int i = 0; i < kNumSignals; ++i) {
    struct sigaction cur;
    if (sigaction(kSignals[i], nullptr, &cur) != 0) continue;
    if ((cur.sa_flags & SA_SIGINFO) && cur.sa_sigaction == handler) continue;  // already ours
    g_old[i] = cur; g_have_old[i] = true;
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_NODEFER;
    sigemptyset(&sa.sa_mask);
    sigaction(kSignals[i], &sa, nullptr);
  }
  std::terminate_handler cur_t = std::get_terminate();
  if (cur_t != terminate_hook) { g_old_terminate = cur_t; std::set_terminate(terminate_hook); }
}

}  // namespace botw
