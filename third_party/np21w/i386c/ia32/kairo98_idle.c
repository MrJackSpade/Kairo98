/*
 * Idle-loop detection for the interpreted IA-32 core.
 *
 * Games wait for the vertical-sync or timer interrupt in a tight loop such
 * as STI / CLI / CMP mem,reg / Jcc. Inside one interpreter slice nothing
 * outside the CPU can change guest memory: device events run only between
 * slices, DMA disables this check, and I/O, interrupts, and writes advance
 * the side-effect counter. So when a backward jump lands on an address the
 * CPU already jumped to earlier in the same slice, with identical general,
 * segment, and flag registers and no side effects since, every remaining
 * iteration in this slice would repeat the same reads and produce the same
 * state. The remaining clocks of the slice are allowed to elapse instead,
 * exactly as HLT does. Event and interrupt timing is unchanged: the guest
 * clock still advances by the full slice, the loop resumes at its head, and
 * any interrupt is delivered at the same slice boundary it would have been.
 */

#include "compiler.h"
#include "cpu.h"
#include "pccore.h"
#include "iocore.h"
#include "kairo98_idle.h"

unsigned long long kairo98_side_effects;
unsigned long long kairo98_counter_skips;

typedef struct {
	UINT32 eip;
	UINT32 eflags;
	SINT32 ov;
	UINT32 regs[CPU_REG_NUM];
	UINT16 sregs[CPU_SEGREG_NUM];
	unsigned long long side_effects;
	unsigned long long slice;
	int valid;
} idle_snapshot_t;

static idle_snapshot_t snapshot;

void
kairo98_idle_check(void)
{
	int i;

	if (CPU_REMCLOCK <= 0 || CPU_TRAP || dmac.working) {
		snapshot.valid = 0;
		return;
	}

	if (snapshot.valid &&
	    snapshot.slice == kairo98_counter_slices &&
	    snapshot.side_effects == kairo98_side_effects &&
	    snapshot.eip == CPU_EIP &&
	    snapshot.eflags == CPU_EFLAG &&
	    snapshot.ov == CPU_OV) {
		for (i = 0; i < CPU_REG_NUM; i++) {
			if (snapshot.regs[i] != CPU_REGS_DWORD(i))
				goto record;
		}
		for (i = 0; i < CPU_SEGREG_NUM; i++) {
			if (snapshot.sregs[i] != CPU_REGS_SREG(i))
				goto record;
		}
		/* Fixed point: let the rest of the slice elapse. */
		CPU_REMCLOCK = 0;
		kairo98_counter_skips++;
		snapshot.valid = 0;
		return;
	}

record:
	snapshot.slice = kairo98_counter_slices;
	snapshot.side_effects = kairo98_side_effects;
	snapshot.eip = CPU_EIP;
	snapshot.eflags = CPU_EFLAG;
	snapshot.ov = CPU_OV;
	for (i = 0; i < CPU_REG_NUM; i++)
		snapshot.regs[i] = CPU_REGS_DWORD(i);
	for (i = 0; i < CPU_SEGREG_NUM; i++)
		snapshot.sregs[i] = CPU_REGS_SREG(i);
	snapshot.valid = 1;
}
