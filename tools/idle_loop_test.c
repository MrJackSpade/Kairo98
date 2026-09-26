/* Generated/compiled by test_idle_loop.ps1 against the actual idle checker. */
#include <stdio.h>
#include <stdint.h>
#include <string.h>
typedef uint32_t UINT32;
typedef int32_t SINT32;
typedef uint16_t UINT16;
#define CPU_REG_NUM 8
#define CPU_SEGREG_NUM 6
static UINT32 CPU_EIP, CPU_EFLAG, regs[8];
static UINT16 sregs[6];
static SINT32 CPU_OV, CPU_REMCLOCK;
static int CPU_TRAP;
static struct { int working; } dmac;
unsigned long long kairo98_counter_slices;
#define CPU_REGS_DWORD(i) regs[i]
#define CPU_REGS_SREG(i) sregs[i]

/* KAIRO98_IDLE_IMPLEMENTATION */

typedef struct { UINT32 ax, ip, flags; SINT32 left; unsigned long long skips; } result_t;
static result_t run(int enabled, int budget, int barrier) {
    result_t result;
    memset(&snapshot, 0, sizeof(snapshot));
    memset(regs, 0, sizeof(regs));
    memset(sregs, 0, sizeof(sregs));
    CPU_EIP = 0; CPU_EFLAG = 0x246; CPU_OV = 0; CPU_REMCLOCK = budget;
    CPU_TRAP = barrier == 2; dmac.working = barrier == 3;
    kairo98_side_effects = kairo98_counter_skips = 0;
    ++kairo98_counter_slices;
    while (CPU_REMCLOCK > 0) {
        switch (CPU_EIP) {
        case 0: /* INC AX, two clocks in the portable core. */
            regs[0]++; CPU_EFLAG = 0x202; CPU_EIP = 1; CPU_REMCLOCK -= 2; break;
        case 1: /* DEC AX, two clocks. */
            regs[0]--; CPU_EFLAG = 0x246; CPU_EIP = 2; CPU_REMCLOCK -= 2; break;
        case 2: /* JMP short, seven clocks, then the actual idle checker. */
            CPU_EIP = 0; CPU_REMCLOCK -= 7;
            if (barrier == 1) ++kairo98_side_effects;
            if (enabled) kairo98_idle_check();
            break;
        }
    }
    result.ax = regs[0]; result.ip = CPU_EIP; result.flags = CPU_EFLAG;
    result.left = CPU_REMCLOCK; result.skips = kairo98_counter_skips;
    return result;
}
int main(void) {
    int budget, barrier, bad = 0, checked = 0;
    unsigned long long skips = 0;
    for (barrier = 0; barrier < 4; ++barrier) {
        for (budget = 1; budget <= 1024; ++budget) {
            result_t normal = run(0, budget, barrier);
            result_t optimized = run(1, budget, barrier);
            ++checked;
            if (normal.ax != optimized.ax || normal.ip != optimized.ip ||
                normal.flags != optimized.flags || normal.left != optimized.left ||
                (barrier && optimized.skips)) {
                if (bad++ < 4) printf("budget=%d barrier=%d: normal %u/%u/%x/%d, optimized %u/%u/%x/%d\n",
                    budget, barrier, normal.ax, normal.ip, normal.flags, normal.left,
                    optimized.ax, optimized.ip, optimized.flags, optimized.left);
            }
            skips += optimized.skips;
        }
    }
    printf("Idle boundary cases=%d mismatches=%d skips=%llu\n", checked, bad, skips);
    return bad || skips == 0;
}
