/*
 * TLB layout and fast lookups shared between paging.c and the inline
 * virtual-address fast paths in cpu_mem.c. Moved out of paging.c so the
 * segment-level read and write functions can resolve a linear address to a
 * host pointer without a call into the paging layer. Behavior is unchanged:
 * the same entries, flags, and lookup rules apply.
 */

#ifndef	IA32_CPU_KAIRO98_TLB_H__
#define	IA32_CPU_KAIRO98_TLB_H__

#if defined(USE_LEGACY_MEMORY_ACCESS)
#define	IA32_MEMORY_FAST_PATH	0
#else
#define	IA32_MEMORY_FAST_PATH	1
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* TLB */
struct tlb_entry {
	UINT32	tag;	/* linear address */
#define	TLB_ENTRY_TAG_VALID		(1 << 0)
/*	pde & pte & CPU_PTE_WRITABLE	(1 << 1)	*/
/*	pde & pte & CPU_PTE_USER_MODE	(1 << 2)	*/
#define	TLB_ENTRY_TAG_DIRTY		CPU_PTE_DIRTY		/* (1 << 6) */
#define	TLB_ENTRY_TAG_GLOBAL		CPU_PTE_GLOBAL_PAGE	/* (1 << 8) */
#define	TLB_ENTRY_TAG_MAX_SHIFT		12
	UINT32	paddr;	/* physical address */
#if IA32_MEMORY_FAST_PATH
	UINT8	*host_page;	/* direct host pointer for ordinary RAM page */
	UINT32	fast_flags;	/* predecoded access/direct flags */
#endif
};
/*
 * TLB fast path
 */
#define	TLB_TAG_SHIFT		TLB_ENTRY_TAG_MAX_SHIFT
#define	TLB_TAG_MASK		(~((1 << TLB_TAG_SHIFT) - 1))
#define	TLB_GET_TAG_ADDR(ep)	((ep)->tag & TLB_TAG_MASK)
#define	TLB_SET_TAG_ADDR(ep, addr) \
do { \
	(ep)->tag &= ~TLB_TAG_MASK; \
	(ep)->tag |= (addr) & TLB_TAG_MASK; \
} while (/*CONSTCOND(*/ 0)

#define	TLB_IS_VALID(ep)	((ep)->tag & TLB_ENTRY_TAG_VALID)
#define	TLB_SET_VALID(ep)	((ep)->tag = TLB_ENTRY_TAG_VALID)
#define	TLB_SET_INVALID(ep)	((ep)->tag = 0)

#define	TLB_IS_WRITABLE(ep)	((ep)->tag & CPU_PTE_WRITABLE)
#define	TLB_IS_USERMODE(ep)	((ep)->tag & CPU_PTE_USER_MODE)
#define	TLB_IS_DIRTY(ep)	((ep)->tag & TLB_ENTRY_TAG_DIRTY)
#if (CPU_FEATURES_ALL & CPU_FEATURE_PGE) == CPU_FEATURE_PGE
#define	TLB_IS_GLOBAL(ep)	((ep)->tag & TLB_ENTRY_TAG_GLOBAL)
#else
#define	TLB_IS_GLOBAL(ep)	0
#endif

#define	TLB_SET_TAG_FLAGS(ep, entry, bit) \
do { \
	(ep)->tag |= (entry) & (CPU_PTE_GLOBAL_PAGE|CPU_PTE_DIRTY); \
	(ep)->tag |= (bit) & (CPU_PTE_WRITABLE|CPU_PTE_USER_MODE); \
} while (/*CONSTCOND*/ 0)

#if IA32_MEMORY_FAST_PATH
// ƒy[ƒW‚‘¬ƒAƒNƒZƒX‚ÌŠÈˆÕ”»’è‚Ì‚½‚ß‚Ìƒtƒ‰ƒO
// ‚±‚Ìƒtƒ‰ƒO”»’è‚Å’e‚©‚ê‚½ê‡‚Í’Êí‚ÌpagingŠÖ”‚ÅÚ×”»’è‚·‚é
#define	TLBF_SUPER_READ		0x00000001UL // ƒX[ƒp[ƒoƒCƒUƒ‚[ƒh“Ç‚İæ‚è‹–‰Â
#define	TLBF_USER_READ		0x00000002UL // ƒ†[ƒU[ƒ‚[ƒh“Ç‚İæ‚è‹–‰Â
#define	TLBF_SUPER_WRITE	0x00000004UL // CR0.WP==0‚Ìê‡‚ÌƒX[ƒp[ƒoƒCƒUƒ‚[ƒh‘‚«‚İ‹–‰Â
#define	TLBF_SUPER_WRITE_WP	0x00000008UL // CR0.WP==1‚Ìê‡‚ÌƒX[ƒp[ƒoƒCƒUƒ‚[ƒh‘‚«‚İ‹–‰Â
#define	TLBF_USER_WRITE		0x00000010UL // ƒ†[ƒU[ƒ‚[ƒh‘‚«‚İ‹–‰Â
#define	TLBF_CODE_SUPER		0x00000020UL // ƒX[ƒp[ƒoƒCƒUƒ‚[ƒh–½—ßƒtƒFƒbƒ`‹–‰Â
#define	TLBF_CODE_USER		0x00000040UL // ƒ†[ƒU[ƒ‚[ƒh–½—ßƒtƒFƒbƒ`‹–‰Â
#define	TLBF_DIRECT_READ	0x00000100UL // host_pageƒ|ƒCƒ“ƒ^‚Ö’¼Ú“Ç‚İæ‚è‰Â”
#define	TLBF_DIRECT_WRITE	0x00000200UL // host_pageƒ|ƒCƒ“ƒ^‚Ö’¼Ú‘‚«‚İ‰Â”
#endif

#define	NTLB		2	/* 0: DTLB, 1: ITLB */
#define	NENTRY		(1 << 6)
#define	TLB_ENTRY_SHIFT	12
#define	TLB_ENTRY_MASK	(NENTRY - 1)

typedef struct {
	struct tlb_entry entry[NENTRY];
} tlb_t;
extern __attribute__((visibility("hidden"))) tlb_t kairo98_tlb[NTLB];

#if IA32_MEMORY_FAST_PATH
#if defined(__GNUC__)
#define TLB_FAST_INLINE static __inline__ __attribute__((always_inline))
#elif defined(_MSC_VER)
#define TLB_FAST_INLINE static __inline
#else
#define TLB_FAST_INLINE static INLINE
#endif

#define	TLB_USER_INDEX(ucrw)	(((ucrw) & CPU_PAGE_USER_MODE) >> 3)

extern __attribute__((visibility("hidden"))) UINT32 kairo98_tlb_data_read_fast_flags[2];
extern __attribute__((visibility("hidden"))) UINT32 kairo98_tlb_data_write_fast_flags[2];
extern __attribute__((visibility("hidden"))) UINT32 kairo98_tlb_code_read_fast_flags[2];

TLB_FAST_INLINE struct tlb_entry *
tlb_lookup_data_read_fast(UINT32 laddr, int ucrw)
{
	struct tlb_entry *ep;
	UINT32 flag;
	int idx;

	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &kairo98_tlb[0].entry[idx];
	flag = kairo98_tlb_data_read_fast_flags[TLB_USER_INDEX(ucrw)];

	if (TLB_IS_VALID(ep) &&
	    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&
	    (ep->fast_flags & flag)) {
		return ep;
	}
	return NULL;
}

TLB_FAST_INLINE struct tlb_entry *
tlb_lookup_data_write_fast(UINT32 laddr, int ucrw)
{
	struct tlb_entry *ep;
	UINT32 flag;
	int idx;

	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &kairo98_tlb[0].entry[idx];
	flag = kairo98_tlb_data_write_fast_flags[TLB_USER_INDEX(ucrw)];

	if (TLB_IS_VALID(ep) &&
	    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&
	    (ep->fast_flags & flag)) {
		return ep;
	}
	return NULL;
}

TLB_FAST_INLINE struct tlb_entry *
tlb_lookup_code_fast(UINT32 laddr, int ucrw)
{
	struct tlb_entry *ep;
	UINT32 flag;
	int idx;

	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &kairo98_tlb[1].entry[idx];
	flag = kairo98_tlb_code_read_fast_flags[TLB_USER_INDEX(ucrw)];

	if (TLB_IS_VALID(ep) &&
	    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&
	    (ep->fast_flags & flag)) {
		return ep;
	}
	return NULL;
}

TLB_FAST_INLINE struct tlb_entry *
tlb_lookup_fast(UINT32 laddr, int ucrw)
{
	/* Preserve the original precedence for the unlikely WRITE|CODE case. */
	if (ucrw & CPU_PAGE_WRITE) {
		return tlb_lookup_data_write_fast(laddr, ucrw);
	}
	if (ucrw & CPU_PAGE_CODE) {
		return tlb_lookup_code_fast(laddr, ucrw);
	}
	return tlb_lookup_data_read_fast(laddr, ucrw);
}


TLB_FAST_INLINE UINT32
tlb_make_fast_flags(UINT entry, int bit, int n, int direct)
{
	UINT32 flags;
	int writable;
	int user;

	flags = TLBF_SUPER_READ;
	writable = (bit & CPU_PTE_WRITABLE) != 0;
	user = (bit & CPU_PTE_USER_MODE) != 0;

	if (user) {
		flags |= TLBF_USER_READ;
	}
	if (n == 1) {
		flags |= TLBF_CODE_SUPER;
		if (user) {
			flags |= TLBF_CODE_USER;
		}
	}

	if (entry & CPU_PTE_DIRTY) {
		flags |= TLBF_SUPER_WRITE;
		if (writable) {
			flags |= TLBF_SUPER_WRITE_WP;
			if (user) {
				flags |= TLBF_USER_WRITE;
			}
		}
	}

	if (direct) {
		flags |= TLBF_DIRECT_READ;
		if (flags & (TLBF_SUPER_WRITE|TLBF_SUPER_WRITE_WP|TLBF_USER_WRITE)) {
			flags |= TLBF_DIRECT_WRITE;
		}
	}
	return flags;
}
#endif	/* IA32_MEMORY_FAST_PATH */

#ifdef __cplusplus
}
#endif

#endif	/* !IA32_CPU_KAIRO98_TLB_H__ */
