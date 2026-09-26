/*
 * Idle-loop detection for the interpreted IA-32 core.
 *
 * Called from the short and near jump macros when a jump goes backward. See
 * kairo98_idle.c for the rule that skips complete iterations of a loop that
 * cannot change state, then executes the residual part of the slice.
 */

#ifndef	IA32_CPU_KAIRO98_IDLE_H__
#define	IA32_CPU_KAIRO98_IDLE_H__

#ifdef __cplusplus
extern "C" {
#endif

void kairo98_idle_check(void);

#ifdef __cplusplus
}
#endif

#endif	/* !IA32_CPU_KAIRO98_IDLE_H__ */
