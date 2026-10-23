// bionic only has backtrace() from API 33; minSdk here is 29, so provide it on top of the unwinder.
#pragma once
#include <stdint.h>
#include <unwind.h>
#ifdef __cplusplus
extern "C" {
#endif
struct botw_bt_state { void** cur; void** end; };
static inline _Unwind_Reason_Code botw_bt_cb(struct _Unwind_Context* ctx, void* arg) {
    struct botw_bt_state* s = (struct botw_bt_state*)arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc) {
        if (s->cur == s->end) return _URC_END_OF_STACK;
        *s->cur++ = (void*)pc;
    }
    return _URC_NO_REASON;
}
static inline int backtrace(void** buf, int size) {
    struct botw_bt_state s = {buf, buf + size};
    _Unwind_Backtrace(botw_bt_cb, &s);
    return (int)(s.cur - buf);
}
#ifdef __cplusplus
}
#endif
