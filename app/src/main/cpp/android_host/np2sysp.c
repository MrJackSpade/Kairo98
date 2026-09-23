#include "compiler.h"
#include "pccore.h"
#include "iocore.h"

/* NP2 guest service commands are outside the first-boot machine profile. */
void np2sysp_reset(const NP2CFG *config) {
    (void)config;
    ZeroMemory(&np2sysp, sizeof(np2sysp));
}

void np2sysp_bind(void) {
}

void np2sysp_outstr(const void *arg1, long arg2) {
    (void)arg1;
    (void)arg2;
}
