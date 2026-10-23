#include <new>

#include "botw_hash.h"
#include "openssl/evp.h"
#include "openssl/sha.h"

struct botw_evp_md { int id; };  // 1 = SHA-1, 2 = SHA-256
struct botw_evp_md_ctx {
    int id = 1;
    botw_hash::Sha1 s1;
    botw_hash::Sha256 s256;
};

extern "C" {
unsigned char* SHA1(const unsigned char* d, size_t n, unsigned char* md) {
    botw_hash::Sha1 s; s.update(d, n); s.final(md); return md;
}
unsigned char* SHA256(const unsigned char* d, size_t n, unsigned char* md) {
    botw_hash::Sha256 s; s.update(d, n); s.final(md); return md;
}
const EVP_MD* EVP_sha1(void) { static botw_evp_md m{1}; return &m; }
const EVP_MD* EVP_sha256(void) { static botw_evp_md m{2}; return &m; }
EVP_MD_CTX* EVP_MD_CTX_new(void) { return new (std::nothrow) botw_evp_md_ctx; }
void EVP_MD_CTX_free(EVP_MD_CTX* c) { delete c; }
int EVP_DigestInit_ex(EVP_MD_CTX* c, const EVP_MD* t, void*) {
    if (!c || !t) return 0;
    c->id = t->id; c->s1 = botw_hash::Sha1(); c->s256 = botw_hash::Sha256();
    return 1;
}
int EVP_DigestInit(EVP_MD_CTX* c, const EVP_MD* t) { return EVP_DigestInit_ex(c, t, nullptr); }
int EVP_DigestUpdate(EVP_MD_CTX* c, const void* d, size_t n) {
    if (c->id == 1) c->s1.update(d, n); else c->s256.update(d, n);
    return 1;
}
int EVP_DigestFinal_ex(EVP_MD_CTX* c, unsigned char* md, unsigned int* s) {
    if (c->id == 1) { c->s1.final(md); if (s) *s = 20; }
    else { c->s256.final(md); if (s) *s = 32; }
    return 1;
}
}
