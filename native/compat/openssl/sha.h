// Android build only: drop-in for the two OpenSSL one-shot digests Cemu's Vulkan layer uses.
#pragma once
#include <stddef.h>
#define SHA_DIGEST_LENGTH 20
#define SHA256_DIGEST_LENGTH 32
#ifdef __cplusplus
extern "C" {
#endif
unsigned char* SHA1(const unsigned char* d, size_t n, unsigned char* md);
unsigned char* SHA256(const unsigned char* d, size_t n, unsigned char* md);
#ifdef __cplusplus
}
#endif
