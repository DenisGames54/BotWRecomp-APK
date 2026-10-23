// Android build only: the EVP_* subset FetchShader.cpp uses (SHA-1), over botw_hash.h.
#pragma once
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct botw_evp_md EVP_MD;
typedef struct botw_evp_md_ctx EVP_MD_CTX;
const EVP_MD* EVP_sha1(void);
const EVP_MD* EVP_sha256(void);
EVP_MD_CTX* EVP_MD_CTX_new(void);
void EVP_MD_CTX_free(EVP_MD_CTX* ctx);
int EVP_DigestInit(EVP_MD_CTX* ctx, const EVP_MD* type);
int EVP_DigestInit_ex(EVP_MD_CTX* ctx, const EVP_MD* type, void* impl);
int EVP_DigestUpdate(EVP_MD_CTX* ctx, const void* d, size_t cnt);
int EVP_DigestFinal_ex(EVP_MD_CTX* ctx, unsigned char* md, unsigned int* s);
#ifdef __cplusplus
}
#endif
