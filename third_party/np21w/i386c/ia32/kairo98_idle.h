/*
 * Idle-loop detection for the interpreted IA-32 core.
 *
 * Called from the short and near jump macros when a jump goes backward. See
 * kairo98_idle.c for the rule that lets the rest of an interpreter slice
 * elapse without re-executing a loop that cannot change state.
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
