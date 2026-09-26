/*
 * Inline instruction-fetch fast path for the Android build.
 *
 * The paging layer keeps one cached code page: the linear page base plus the
 * access key of the most recent successful fetch, and a direct host pointer
 * to that page's RAM. Under paging, nearly every fetch hits that page. This
 * header checks the cache at the call site so the interpreter's dispatch loop
 * and every instruction handler read opcode and immediate bytes without a
 * function call. A miss, real mode, or a segment-limit violation falls
 * through to the original out-of-line routine, which refills the cache and
 * raises exceptions exactly as before.
 *
 * Guest-visible behavior is unchanged: the same bytes are returned, the same
 * limit check applies, and the cache is invalidated by the same TLB events.
 */

#ifndef	IA32_CPU_KAIRO98_FETCH_H__
#define	IA32_CPU_KAIRO98_FETCH_H__

#if defined(KAIRO98_ANDROID_FETCH_FAST)

#ifdef __cplusplus
extern "C" {
#endif

STATIC_INLINE PF_UINT8 MEMCALL
cpu_codefetch(UINT32 offset)
{
	/* Paging implies protected mode, so no separate mode test is needed. */
	if (CPU_STAT_PAGING) {
		const descriptor_t *sdp = &CPU_CS_DESC;
		UINT8 *page = kairo98_codefetch_cache.host_page;
		UINT32 addr = sdp->u.seg.segbase + offset;
		UINT32 key = (addr & ~CPU_PAGE_MASK) |
		    (UINT32)(CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE);

		if (page != NULL && kairo98_codefetch_cache.key == key &&
		    offset <= sdp->u.seg.limit) {
			return page[addr & CPU_PAGE_MASK];
		}
	}
	return cpu_codefetch_slow(offset);
}

STATIC_INLINE PF_UINT16 MEMCALL
cpu_codefetch_w(UINT32 offset)
{
	if (CPU_STAT_PAGING) {
		const descriptor_t *sdp = &CPU_CS_DESC;
		UINT8 *page = kairo98_codefetch_cache.host_page;
		UINT32 addr = sdp->u.seg.segbase + offset;
		UINT32 key = (addr & ~CPU_PAGE_MASK) |
		    (UINT32)(CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE);

		/* Both bytes must sit in the cached page. The limit test matches
		 * the out-of-line routine. */
		if (page != NULL && kairo98_codefetch_cache.key == key &&
		    (addr & CPU_PAGE_MASK) <= CPU_PAGE_MASK - 1 &&
		    offset <= sdp->u.seg.limit - 1) {
			return LOADINTELWORD(page + (addr & CPU_PAGE_MASK));
		}
	}
	return cpu_codefetch_w_slow(offset);
}

STATIC_INLINE PF_UINT32 MEMCALL
cpu_codefetch_d(UINT32 offset)
{
	if (CPU_STAT_PAGING) {
		const descriptor_t *sdp = &CPU_CS_DESC;
		UINT8 *page = kairo98_codefetch_cache.host_page;
		UINT32 addr = sdp->u.seg.segbase + offset;
		UINT32 key = (addr & ~CPU_PAGE_MASK) |
		    (UINT32)(CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE);

		if (page != NULL && kairo98_codefetch_cache.key == key &&
		    (addr & CPU_PAGE_MASK) <= CPU_PAGE_MASK - 3 &&
		    offset <= sdp->u.seg.limit - 3) {
			return LOADINTELDWORD(page + (addr & CPU_PAGE_MASK));
		}
	}
	return cpu_codefetch_d_slow(offset);
}

#ifdef __cplusplus
}
#endif

#endif	/* KAIRO98_ANDROID_FETCH_FAST */

#endif	/* !IA32_CPU_KAIRO98_FETCH_H__ */
