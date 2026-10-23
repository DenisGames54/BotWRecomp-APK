// Host-side check of native/compat/botw_hash.h:  g++ -I native/compat native/tests/hash_test.cpp && ./a.out
#include <stdio.h>
#include <string.h>
#include <string>
#include "botw_hash.h"
static std::string hex(const uint8_t* p, int n) { std::string s; char b[3]; for (int i = 0; i < n; i++) { snprintf(b, 3, "%02x", p[i]); s += b; } return s; }
int main() {
    int bad = 0;
    uint8_t d[32];
    { botw_hash::Sha1 s; s.update("abc", 3); s.final(d); bad += hex(d, 20) != "a9993e364706816aba3e25717850c26c9cd0d89d"; }
    { botw_hash::Sha256 s; s.update("abc", 3); s.final(d); bad += hex(d, 32) != "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"; }
    { std::string m(1000000, 'a'); botw_hash::Sha256 s; s.update(m.data(), m.size()); s.final(d); bad += hex(d, 32) != "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"; }
    { std::string m(1000000, 'a'); botw_hash::Sha1 s; for (size_t i = 0; i < m.size(); i += 777) s.update(m.data() + i, std::min<size_t>(777, m.size() - i)); s.final(d); bad += hex(d, 20) != "34aa973cd4c4daa4f61eeb2bdbad27316534016f"; }
    puts(bad ? "HASH TEST FAILED" : "hash test OK");
    return bad;
}
