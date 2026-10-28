"""Kairo98's local changes to the Neko Project 21/W snapshot in third_party/np21w, which shared/tools/local_patches.py applies and checks. Recorded from the snapshot as committed; docs/source-import.md describes each change."""

ENCODING = 'cp932'

# The app's own files beside the code it patches.
ADDED = ['np21w/i386c/ia32/kairo98_fetch.h', 'np21w/i386c/ia32/kairo98_idle.c', 'np21w/i386c/ia32/kairo98_idle.h', 'np21w/i386c/ia32/kairo98_tlb.h']

# Files taken out of the snapshot.
REMOVED = ['np21w/win9x/resources/nekop2.bmp.bak']

# One group per changed file: (path under third_party/, old text, new text) in order.
PATCHES = {
    'np21w/bios/bios18.c': [
        ('np21w/bios/bios18.c',
         '''#include	"iocore.h"
#include	"gdc_sub.h"
#include	"bios.h"
#include	"biosmem.h"
#include	"font/font.h"
#if defined(SUPPORT_TEXTHOOK)
''',
         '''#include	"iocore.h"
#include	"gdc_sub.h"
#include	"bios.h"
#include	"input_telemetry.h"
#include	"biosmem.h"
#include	"font/font.h"
#if defined(SUPPORT_TEXTHOOK)
'''),
        ('np21w/bios/bios18.c',
         '''	switch(CPU_AH) {
		case 0x00:						// キー・データの読みだし
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				CPU_AX = keyget();
			}
			else {
				CPU_IP--;
				CPU_REMCLOCK = -1;
				break;
''',
         '''	switch(CPU_AH) {
		case 0x00:						// キー・データの読みだし
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				kairo98_observe_keyboard_read();
				CPU_AX = keyget();
			}
			else {
				kairo98_observe_keyboard_wait();
				CPU_IP--;
				CPU_REMCLOCK = -1;
				break;
'''),
        ('np21w/bios/bios18.c',
         '''			break;

   		case 0x01:						// キー・バッファ状態のセンス
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				tmp.d = bioskbd_read16(MEMW_KB_BUF_HEAD);
				CPU_AX = bioskbd_read16(tmp.d);
''',
         '''			break;

   		case 0x01:						// キー・バッファ状態のセンス
			kairo98_observe_keyboard_poll();
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				tmp.d = bioskbd_read16(MEMW_KB_BUF_HEAD);
				CPU_AX = bioskbd_read16(tmp.d);
'''),
        ('np21w/bios/bios18.c',
         ''' 			break;

   		case 0x05:						// キー入力センス
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				CPU_AX = keyget();
				CPU_BH = 1;
''',
         ''' 			break;

   		case 0x05:						// キー入力センス
			kairo98_observe_keyboard_poll();
			if (bioskbd_read8(MEMB_KB_COUNT)) {
				CPU_AX = keyget();
				CPU_BH = 1;
'''),
    ],
    'np21w/diskimage/fddfile.h': [
        ('np21w/diskimage/fddfile.h',
         '''
#define	MAX_FDDFILE		4							// ver0.31

#include	"DiskImage/FD/fdd_head_d88.h"
#include	"DiskImage/FD/fdd_head_dcp.h"	//	追加(Kai1)
#include	"DiskImage/FD/fdd_head_nfd.h"	//	追加(Kai1)
#include	"DiskImage/FD/fdd_head_vfdd.h"	//	追加(Kai1)

enum {
	FDDFILE_MF			= 0x80,
''',
         '''
#define	MAX_FDDFILE		4							// ver0.31

#include	"diskimage/fd/fdd_head_d88.h"
#include	"diskimage/fd/fdd_head_dcp.h"	//	追加(Kai1)
#include	"diskimage/fd/fdd_head_nfd.h"	//	追加(Kai1)
#include	"diskimage/fd/fdd_head_vfdd.h"	//	追加(Kai1)

enum {
	FDDFILE_MF			= 0x80,
'''),
    ],
    'np21w/fdd/sxsicd.c': [
        ('np21w/fdd/sxsicd.c',
         '''#endif

#ifdef SUPPORT_KAI_IMAGES
#include	"DiskImage/cddfile.h"
#include	"DiskImage/CD/cdd_iso.h"
#include	"DiskImage/CD/cdd_cue.h"
#include	"DiskImage/CD/cdd_ccd.h"
#include	"DiskImage/CD/cdd_mds.h"
#include	"DiskImage/CD/cdd_nrg.h"
#include	"DiskImage/CD/cdd_real.h"

BRESULT sxsicd_open(SXSIDEV sxsi, const OEMCHAR *fname) {

''',
         '''#endif

#ifdef SUPPORT_KAI_IMAGES
#include	"diskimage/cddfile.h"
#include	"diskimage/cd/cdd_iso.h"
#include	"diskimage/cd/cdd_cue.h"
#include	"diskimage/cd/cdd_ccd.h"
#include	"diskimage/cd/cdd_mds.h"
#include	"diskimage/cd/cdd_nrg.h"
#include	"diskimage/cd/cdd_real.h"

BRESULT sxsicd_open(SXSIDEV sxsi, const OEMCHAR *fname) {

'''),
    ],
    'np21w/i386c/cpumem.c': [
        ('np21w/i386c/cpumem.c',
         '''		memvram0_wr32,	memnc_wr32,		memnc_wr32,		memnc_wr32,		// e0
		memmain_wr32,	memmain_wr32}};

static const MMAPTBL mmaptbl[2] = {
		   {memmain_rd8,	memf800_rd8,	memnc_wr8,
			memmain_rd16,	memf800_rd16,	memnc_wr16,
''',
         '''		memvram0_wr32,	memnc_wr32,		memnc_wr32,		memnc_wr32,		// e0
		memmain_wr32,	memmain_wr32}};

/* Word access through the bank table for a physical address below 1 MB whose
 * word does not cross a 32 KB bank. This is the handler memp_read16 and
 * memp_write16 reach after their range tests; callers must apply those tests. */
REG16 MEMCALL memp_bank_read16(UINT32 address) {
	return memfn0.rd16[address >> 15](address);
}

void MEMCALL memp_bank_write16(UINT32 address, REG16 value) {
	memfn0.wr16[address >> 15](address, value);
}

static const MMAPTBL mmaptbl[2] = {
		   {memmain_rd8,	memf800_rd8,	memnc_wr8,
			memmain_rd16,	memf800_rd16,	memnc_wr16,
'''),
        ('np21w/i386c/cpumem.c',
         '''	   {memnc_wr16,		memnc_wr16,		memnc_wr16,		memnc_wr16,
		memnc_wr16,		memsys_wr16,	memsys_wr16,	memsys_wr16},

	   {memvgaf_rd32,	memvgaf_rd32,	memvgaf_rd32,	memvgaf_rd32,
		memnc_rd32,		memsys_rd32,	memsys_rd32,	memsys_rd32},
	   {memvgaf_wr32,	memvgaf_wr32,	memvgaf_wr32,	memvgaf_wr32,
		memnc_wr32,		memsys_wr32,	memsys_wr32,	memsys_wr32}};
#endif

// ----- slow版 fastで処理される部分は省略されているので、fast版からの呼び出しのみ可能

static REG8 MEMCALL memp_read8_slow(UINT32 address) {
#if defined(SUPPORT_WAB_GA1280A)
	{
		REG8 retValue;
''',
         '''	   {memnc_wr16,		memnc_wr16,		memnc_wr16,		memnc_wr16,
		memnc_wr16,		memsys_wr16,	memsys_wr16,	memsys_wr16},

	   {memnc_rd32,	memnc_rd32,	memnc_rd32,	memnc_rd32,
		memnc_rd32,		memsys_rd32,	memsys_rd32,	memsys_rd32},
	   {memnc_wr32,	memnc_wr32,	memnc_wr32,	memnc_wr32,
		memnc_wr32,		memsys_wr32,	memsys_wr32,	memsys_wr32}};
#endif

// ----- slow版 fastで処理される部分は省略されているので、fast版からの呼び出しのみ可能

static REG8 MEMCALL memp_read8_slow(UINT32 address) {
	KAIRO98_SIDE_EFFECT();
#if defined(SUPPORT_WAB_GA1280A)
	{
		REG8 retValue;
'''),
        ('np21w/i386c/cpumem.c',
         '''}

static REG16 MEMCALL memp_read16_slow(UINT32 address) {
	REG16 ret;

	if ((address + 1) & 0x7fff) {			// non 32kb boundary
''',
         '''}

static REG16 MEMCALL memp_read16_slow(UINT32 address) {
	KAIRO98_SIDE_EFFECT();
	REG16 ret;

	if ((address + 1) & 0x7fff) {			// non 32kb boundary
'''),
        ('np21w/i386c/cpumem.c',
         '''}

static UINT32 MEMCALL memp_read32_slow(UINT32 address) {
	UINT32 ret;

	if ((address & 0x7fff) <= 0x8000 - 4) {			// non 32kb boundary
''',
         '''}

static UINT32 MEMCALL memp_read32_slow(UINT32 address) {
	KAIRO98_SIDE_EFFECT();
	UINT32 ret;

	if ((address & 0x7fff) <= 0x8000 - 4) {			// non 32kb boundary
'''),
        ('np21w/i386c/cpumem.c',
         '''
// ---- 通常メモリ読み込み関数
REG8 MEMCALL memp_read8(UINT32 address) {
	
#ifdef MEM_BDA_TRACEOUT
	if(0x400 <= address && address < 0x600){
''',
         '''
// ---- 通常メモリ読み込み関数
REG8 MEMCALL memp_read8(UINT32 address) {
	KAIRO98_SIDE_EFFECT();
	
#ifdef MEM_BDA_TRACEOUT
	if(0x400 <= address && address < 0x600){
'''),
        ('np21w/i386c/cpumem.c',
         '''}

REG16 MEMCALL memp_read16(UINT32 address) {

	REG16	ret;
	
''',
         '''}

REG16 MEMCALL memp_read16(UINT32 address) {
	KAIRO98_SIDE_EFFECT();

	REG16	ret;
	
'''),
        ('np21w/i386c/cpumem.c',
         '''}

UINT32 MEMCALL memp_read32(UINT32 address) {

	//UINT32	pos;
	UINT32	ret;
''',
         '''}

UINT32 MEMCALL memp_read32(UINT32 address) {
	KAIRO98_SIDE_EFFECT();

	//UINT32	pos;
	UINT32	ret;
'''),
        ('np21w/i386c/cpumem.c',
         '''
// ---- 通常メモリ書き込み関数
void MEMCALL memp_write8(UINT32 address, REG8 value) {
	
#ifdef MEM_BDA_TRACEOUT
	if(0x400 <= address && address < 0x600){
''',
         '''
// ---- 通常メモリ書き込み関数
void MEMCALL memp_write8(UINT32 address, REG8 value) {
	KAIRO98_SIDE_EFFECT();
	
#ifdef MEM_BDA_TRACEOUT
	if(0x400 <= address && address < 0x600){
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write16(UINT32 address, REG16 value) {

	
#ifdef MEM_BDA_TRACEOUT
''',
         '''}

void MEMCALL memp_write16(UINT32 address, REG16 value) {
	KAIRO98_SIDE_EFFECT();

	
#ifdef MEM_BDA_TRACEOUT
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write32(UINT32 address, UINT32 value) {

	//UINT32	pos;
	
''',
         '''}

void MEMCALL memp_write32(UINT32 address, UINT32 value) {
	KAIRO98_SIDE_EFFECT();

	//UINT32	pos;
	
'''),
        ('np21w/i386c/cpumem.c',
         '''
// ---- 高速版書き込み　普通のメモリを優先的に処理する
void MEMCALL memp_write8_fast(UINT32 address, REG8 value) {
	UINT32 raw = address;

	if (address == 0x0457) return;
''',
         '''
// ---- 高速版書き込み　普通のメモリを優先的に処理する
void MEMCALL memp_write8_fast(UINT32 address, REG8 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address == 0x0457) return;
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write16_fast(UINT32 address, REG16 value) {
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 1)) {
''',
         '''}

void MEMCALL memp_write16_fast(UINT32 address, REG16 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 1)) {
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write32_fast(UINT32 address, UINT32 value) {
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 3)) {
''',
         '''}

void MEMCALL memp_write32_fast(UINT32 address, UINT32 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 3)) {
'''),
        ('np21w/i386c/cpumem.c',
         '''
// ---- 通常メモリ書き込み関数（paging用）
void MEMCALL memp_write8_paging(UINT32 address, REG8 value) {
	
	//if (address==0x0457) return; // XXX: IDEのデータ破壊回避のための暫定
	if (address < I286_MEMWRITEMAX) {
''',
         '''
// ---- 通常メモリ書き込み関数（paging用）
void MEMCALL memp_write8_paging(UINT32 address, REG8 value) {
	KAIRO98_SIDE_EFFECT();
	
	//if (address==0x0457) return; // XXX: IDEのデータ破壊回避のための暫定
	if (address < I286_MEMWRITEMAX) {
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write16_paging(UINT32 address, REG16 value) {
	
	if (address < (I286_MEMWRITEMAX - 1)) {
		STOREINTELWORD(mem + address, value);
''',
         '''}

void MEMCALL memp_write16_paging(UINT32 address, REG16 value) {
	KAIRO98_SIDE_EFFECT();
	
	if (address < (I286_MEMWRITEMAX - 1)) {
		STOREINTELWORD(mem + address, value);
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write32_paging(UINT32 address, UINT32 value) {
	
	if (address < (I286_MEMWRITEMAX - 3)) {
		STOREINTELDWORD(mem + address, value);
''',
         '''}

void MEMCALL memp_write32_paging(UINT32 address, UINT32 value) {
	KAIRO98_SIDE_EFFECT();
	
	if (address < (I286_MEMWRITEMAX - 3)) {
		STOREINTELDWORD(mem + address, value);
'''),
        ('np21w/i386c/cpumem.c',
         '''
// ---- 高速版書き込み（paging用）　普通のメモリを優先的に処理する
void MEMCALL memp_write8_paging_fast(UINT32 address, REG8 value) {
	UINT32 raw = address;

	if (address < I286_MEMWRITEMAX) {
''',
         '''
// ---- 高速版書き込み（paging用）　普通のメモリを優先的に処理する
void MEMCALL memp_write8_paging_fast(UINT32 address, REG8 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address < I286_MEMWRITEMAX) {
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write16_paging_fast(UINT32 address, REG16 value) {
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 1)) {
''',
         '''}

void MEMCALL memp_write16_paging_fast(UINT32 address, REG16 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 1)) {
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_write32_paging_fast(UINT32 address, UINT32 value) {
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 3)) {
''',
         '''}

void MEMCALL memp_write32_paging_fast(UINT32 address, UINT32 value) {
	KAIRO98_SIDE_EFFECT();
	UINT32 raw = address;

	if (address < (I286_MEMWRITEMAX - 3)) {
'''),
        ('np21w/i386c/cpumem.c',
         '''

void MEMCALL memp_reads(UINT32 address, void *dat, UINT leng) {

	UINT8 *out = (UINT8 *)dat;
	UINT diff;
''',
         '''

void MEMCALL memp_reads(UINT32 address, void *dat, UINT leng) {
	KAIRO98_SIDE_EFFECT();

	UINT8 *out = (UINT8 *)dat;
	UINT diff;
'''),
        ('np21w/i386c/cpumem.c',
         '''}

void MEMCALL memp_writes(UINT32 address, const void *dat, UINT leng) {

	const UINT8 *out = (UINT8 *)dat;
	UINT diff;
''',
         '''}

void MEMCALL memp_writes(UINT32 address, const void *dat, UINT leng) {
	KAIRO98_SIDE_EFFECT();

	const UINT8 *out = (UINT8 *)dat;
	UINT diff;
'''),
    ],
    'np21w/i386c/cpumem.h': [
        ('np21w/i386c/cpumem.h',
         '''UINT32 MEMCALL memp_read32(UINT32 address);
REG8 MEMCALL memp_read8_fast(UINT32 address);
REG16 MEMCALL memp_read16_fast(UINT32 address);
UINT32 MEMCALL memp_read32_fast(UINT32 address);
void MEMCALL memp_write8(UINT32 address, REG8 value);
void MEMCALL memp_write16(UINT32 address, REG16 value);
''',
         '''UINT32 MEMCALL memp_read32(UINT32 address);
REG8 MEMCALL memp_read8_fast(UINT32 address);
REG16 MEMCALL memp_read16_fast(UINT32 address);
REG16 MEMCALL memp_bank_read16(UINT32 address);
void MEMCALL memp_bank_write16(UINT32 address, REG16 value);
UINT32 MEMCALL memp_read32_fast(UINT32 address);
void MEMCALL memp_write8(UINT32 address, REG8 value);
void MEMCALL memp_write16(UINT32 address, REG16 value);
'''),
    ],
    'np21w/i386c/ia32/cpu.c': [
        ('np21w/i386c/ia32/cpu.c',
         '''	int prefix;
	UINT32 op;

	CPU_PREV_EIP = CPU_EIP;
	CPU_STATSAVE.cpu_inst = CPU_STATSAVE.cpu_inst_default;

''',
         '''	int prefix;
	UINT32 op;

	kairo98_counter_insts++;
	CPU_PREV_EIP = CPU_EIP;
	CPU_STATSAVE.cpu_inst = CPU_STATSAVE.cpu_inst_default;

'''),
        ('np21w/i386c/ia32/cpu.c',
         '''	
	do {

		CPU_PREV_EIP = CPU_EIP;
		CPU_STATSAVE.cpu_inst = CPU_STATSAVE.cpu_inst_default;

''',
         '''	
	do {

		kairo98_counter_insts++;
		CPU_PREV_EIP = CPU_EIP;
		CPU_STATSAVE.cpu_inst = CPU_STATSAVE.cpu_inst_default;

'''),
    ],
    'np21w/i386c/ia32/cpu.h': [
        ('np21w/i386c/ia32/cpu.h',
         '''extern const UINT8 iflags[];
#define	szpcflag	iflags
extern UINT8 szpflag_w[0x10000];

#if defined(USE_CPU_EIPMASK)
extern UINT32 cpu_eipMask;
#endif
''',
         '''extern const UINT8 iflags[];
#define	szpcflag	iflags
extern UINT8 szpflag_w[0x10000];
#if defined(USE_CPU_EIPMASK)
extern UINT32 cpu_eipMask;
#endif
'''),
        ('np21w/i386c/ia32/cpu.h',
         '''#include "cpu_mem.h"
#include "exception.h"
#include "paging.h"
#include "resolve.h"
#include "task.h"

''',
         '''#include "cpu_mem.h"
#include "exception.h"
#include "paging.h"
#include "kairo98_tlb.h"
#include "kairo98_fetch.h"
#include "kairo98_idle.h"
#include "resolve.h"
#include "task.h"

'''),
    ],
    'np21w/i386c/ia32/cpu_mem.c': [
        ('np21w/i386c/ia32/cpu_mem.c',
         '''/*
 * code fetch
 */
PF_UINT8 MEMCALL
cpu_codefetch(UINT32 offset)
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
''',
         '''/*
 * code fetch
 */
#if defined(KAIRO98_ANDROID_FETCH_FAST)
/* The inline wrappers in kairo98_fetch.h handle the cached-page hit; these
 * bodies run on a miss, refill the cache through the paging layer, and raise
 * the segment-limit exception. */
PF_UINT8 MEMCALL
cpu_codefetch_slow(UINT32 offset)
#else
PF_UINT8 MEMCALL
cpu_codefetch(UINT32 offset)
#endif
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
'''),
        ('np21w/i386c/ia32/cpu_mem.c',
         '''	return 0;	/* compiler happy */
}

PF_UINT16 MEMCALL
cpu_codefetch_w(UINT32 offset)
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
''',
         '''	return 0;	/* compiler happy */
}

#if defined(KAIRO98_ANDROID_FETCH_FAST)
PF_UINT16 MEMCALL
cpu_codefetch_w_slow(UINT32 offset)
#else
PF_UINT16 MEMCALL
cpu_codefetch_w(UINT32 offset)
#endif
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
'''),
        ('np21w/i386c/ia32/cpu_mem.c',
         '''	return 0;	/* compiler happy */
}

PF_UINT32 MEMCALL
cpu_codefetch_d(UINT32 offset)
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
''',
         '''	return 0;	/* compiler happy */
}

#if defined(KAIRO98_ANDROID_FETCH_FAST)
PF_UINT32 MEMCALL
cpu_codefetch_d_slow(UINT32 offset)
#else
PF_UINT32 MEMCALL
cpu_codefetch_d(UINT32 offset)
#endif
{
	const int ucrw = CPU_PAGE_READ_CODE | CPU_STAT_USER_MODE;
	descriptor_t *sdp;
'''),
        ('np21w/i386c/ia32/cpu_mem.c',
         '#define\tCHOOSE_EXCEPTION(sreg) \\\n\t(((sreg) == CPU_SS_INDEX) ? SS_EXCEPTION : GP_EXCEPTION)\n\n#include "cpu_mem.mcr"\n\nstatic void MEMCALL\n',
         '#define\tCHOOSE_EXCEPTION(sreg) \\\n\t(((sreg) == CPU_SS_INDEX) ? SS_EXCEPTION : GP_EXCEPTION)\n\n/*\n * Android fast paths used by the templates in cpu_mem.mcr.\n *\n * KAIRO98_LIMIT_OK is the expand-up segment limit test from\n * check_limit_upstairs written inline. It answers only the common case; any\n * other case (expand-down data, failure) falls back to the full routine so\n * exceptions are raised exactly as before.\n *\n * KAIRO98_HOST_* read or write ordinary RAM through the data TLB\'s host\n * pointer when the entry already permits direct access and the operand does\n * not cross a page. These mirror cpu_linear_memory_read_* and _write_* in\n * paging.c, minus the calls.\n */\n#if defined(KAIRO98_ANDROID_FETCH_FAST)\n#define\tKAIRO98_LIMIT_OK(sdp, offset, len) \\\n\t(!(sdp)->u.seg.ec && (offset) <= (sdp)->u.seg.limit && \\\n\t (UINT32)((len) - 1) <= (sdp)->u.seg.limit - (offset))\n#define\tKAIRO98_HOST_FAST_b\t1\n#define\tKAIRO98_HOST_FAST_w\t1\n#define\tKAIRO98_HOST_FAST_d\t1\n#define\tKAIRO98_HOST_FAST_q\t0\n#define\tKAIRO98_HOST_LOAD_b(p)\t(*(const UINT8 *)(p))\n#define\tKAIRO98_HOST_LOAD_w(p)\tLOADINTELWORD(p)\n#define\tKAIRO98_HOST_LOAD_d(p)\tLOADINTELDWORD(p)\n#define\tKAIRO98_HOST_LOAD_q(p)\t0\n#define\tKAIRO98_HOST_STORE_b(p, v)\tdo { KAIRO98_SIDE_EFFECT(); *(UINT8 *)(p) = (UINT8)(v); } while (0)\n#define\tKAIRO98_HOST_STORE_w(p, v)\tdo { KAIRO98_SIDE_EFFECT(); STOREINTELWORD((p), (v)); } while (0)\n#define\tKAIRO98_HOST_STORE_d(p, v)\tdo { KAIRO98_SIDE_EFFECT(); STOREINTELDWORD((p), (v)); } while (0)\n#define\tKAIRO98_HOST_STORE_q(p, v)\tdo { } while (0)\n/*\n * Word access to the PC-98 graphics VRAM windows (A8000-BFFFF, E0000-E7FFF).\n * memp_read16 and memp_write16 route every such word to the bank table after\n * their range tests; the physical address is below 1 MB, so the A20 mask does\n * not apply, and a word inside one 4 KB page never crosses a 32 KB bank.\n * Calling the bank handler directly skips the generic dispatch above it.\n */\n#define\tKAIRO98_VRAM_WORD_b\t0\n#define\tKAIRO98_VRAM_WORD_w\t1\n#define\tKAIRO98_VRAM_WORD_d\t0\n#define\tKAIRO98_VRAM_WORD_q\t0\n#define\tKAIRO98_IS_VRAM_WORD(pa) \\\n\t((((pa) >= 0xa8000UL) && ((pa) < 0xc0000UL)) || \\\n\t (((pa) >= 0xe0000UL) && ((pa) < 0xe8000UL)))\n#define\tKAIRO98_VRAM_LOAD_w(pa)\tmemp_bank_read16(pa)\n#define\tKAIRO98_VRAM_LOAD_b(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_d(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_q(pa)\t0\n#define\tKAIRO98_VRAM_STORE_w(pa, v)\tmemp_bank_write16((pa), (v))\n#define\tKAIRO98_VRAM_STORE_b(pa, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_STORE_d(pa, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_STORE_q(pa, v)\tdo { } while (0)\n#else\n#define\tKAIRO98_LIMIT_OK(sdp, offset, len)\t0\n#define\tKAIRO98_HOST_FAST_b\t0\n#define\tKAIRO98_HOST_FAST_w\t0\n#define\tKAIRO98_HOST_FAST_d\t0\n#define\tKAIRO98_HOST_FAST_q\t0\n#define\tKAIRO98_HOST_LOAD_b(p)\t0\n#define\tKAIRO98_HOST_LOAD_w(p)\t0\n#define\tKAIRO98_HOST_LOAD_d(p)\t0\n#define\tKAIRO98_HOST_LOAD_q(p)\t0\n#define\tKAIRO98_HOST_STORE_b(p, v)\tdo { } while (0)\n#define\tKAIRO98_HOST_STORE_w(p, v)\tdo { } while (0)\n#define\tKAIRO98_HOST_STORE_d(p, v)\tdo { } while (0)\n#define\tKAIRO98_HOST_STORE_q(p, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_WORD_b\t0\n#define\tKAIRO98_VRAM_WORD_w\t0\n#define\tKAIRO98_VRAM_WORD_d\t0\n#define\tKAIRO98_VRAM_WORD_q\t0\n#define\tKAIRO98_IS_VRAM_WORD(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_b(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_w(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_d(pa)\t0\n#define\tKAIRO98_VRAM_LOAD_q(pa)\t0\n#define\tKAIRO98_VRAM_STORE_b(pa, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_STORE_w(pa, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_STORE_d(pa, v)\tdo { } while (0)\n#define\tKAIRO98_VRAM_STORE_q(pa, v)\tdo { } while (0)\n#endif\n\n#include "cpu_mem.mcr"\n\nstatic void MEMCALL\n'),
    ],
    'np21w/i386c/ia32/cpu_mem.h': [
        ('np21w/i386c/ia32/cpu_mem.h',
         '''/*
 * code fetch
 */
PF_UINT8 MEMCALL cpu_codefetch(UINT32 offset);
PF_UINT16 MEMCALL cpu_codefetch_w(UINT32 offset);
PF_UINT32 MEMCALL cpu_codefetch_d(UINT32 offset);
#if defined(USE_CPU_MODRMPREFETCH)
PF_UINT8 MEMCALL cpu_opcodefetch(UINT32 offset);
#endif
''',
         '''/*
 * code fetch
 */
#if defined(KAIRO98_ANDROID_FETCH_FAST)
/* Out-of-line fetch used when the inline code-page cache in
 * kairo98_fetch.h misses. The inline wrappers keep the public names. */
PF_UINT8 MEMCALL cpu_codefetch_slow(UINT32 offset);
PF_UINT16 MEMCALL cpu_codefetch_w_slow(UINT32 offset);
PF_UINT32 MEMCALL cpu_codefetch_d_slow(UINT32 offset);
#else
PF_UINT8 MEMCALL cpu_codefetch(UINT32 offset);
PF_UINT16 MEMCALL cpu_codefetch_w(UINT32 offset);
PF_UINT32 MEMCALL cpu_codefetch_d(UINT32 offset);
#endif
#if defined(USE_CPU_MODRMPREFETCH)
PF_UINT8 MEMCALL cpu_opcodefetch(UINT32 offset);
#endif
'''),
    ],
    'np21w/i386c/ia32/cpu_mem.mcr': [
        ('np21w/i386c/ia32/cpu_mem.mcr',
         '\t\tcpu_memoryread_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\treturn cpu_lmemoryread_##width(addr, CPU_PAGE_READ_DATA | CPU_STAT_USER_MODE); \\\n\\\nrange_failure: \\\n',
         '\t\tcpu_memoryread_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!KAIRO98_LIMIT_OK(sdp, offset, (length)) && \\\n\t\t    !check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\tif (KAIRO98_HOST_FAST_##width && CPU_STAT_PAGING) { \\\n\t\tstruct tlb_entry *__ep = tlb_lookup_data_read_fast(addr, \\\n\t\t    CPU_PAGE_READ_DATA | CPU_STAT_USER_MODE); \\\n\t\tif (__ep != NULL && \\\n\t\t    (addr & CPU_PAGE_MASK) <= CPU_PAGE_MASK - ((length) - 1)) { \\\n\t\t\tif (__ep->fast_flags & TLBF_DIRECT_READ) { \\\n\t\t\t\treturn KAIRO98_HOST_LOAD_##width(__ep->host_page + (addr & CPU_PAGE_MASK)); \\\n\t\t\t} \\\n\t\t\tif (KAIRO98_VRAM_WORD_##width) { \\\n\t\t\t\tUINT32 __pa = __ep->paddr + (addr & CPU_PAGE_MASK); \\\n\t\t\t\tif (KAIRO98_IS_VRAM_WORD(__pa)) { \\\n\t\t\t\t\tKAIRO98_SIDE_EFFECT(); \\\n\t\t\t\t\treturn KAIRO98_VRAM_LOAD_##width(__pa); \\\n\t\t\t\t} \\\n\t\t\t} \\\n\t\t} \\\n\t} \\\n\treturn cpu_lmemoryread_##width(addr, CPU_PAGE_READ_DATA | CPU_STAT_USER_MODE); \\\n\\\nrange_failure: \\\n'),
        ('np21w/i386c/ia32/cpu_mem.mcr',
         '\t\tcpu_memorywrite_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\tcpu_lmemorywrite_##width(addr, value, CPU_PAGE_WRITE_DATA | CPU_STAT_USER_MODE); \\\n\treturn; \\\n\\\n',
         '\t\tcpu_memorywrite_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!KAIRO98_LIMIT_OK(sdp, offset, (length)) && \\\n\t\t    !check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\tif (KAIRO98_HOST_FAST_##width && CPU_STAT_PAGING) { \\\n\t\tstruct tlb_entry *__ep = tlb_lookup_data_write_fast(addr, \\\n\t\t    CPU_PAGE_WRITE_DATA | CPU_STAT_USER_MODE); \\\n\t\tif (__ep != NULL && \\\n\t\t    (addr & CPU_PAGE_MASK) <= CPU_PAGE_MASK - ((length) - 1)) { \\\n\t\t\tif (__ep->fast_flags & TLBF_DIRECT_WRITE) { \\\n\t\t\t\tKAIRO98_HOST_STORE_##width(__ep->host_page + (addr & CPU_PAGE_MASK), value); \\\n\t\t\t\treturn; \\\n\t\t\t} \\\n\t\t\tif (KAIRO98_VRAM_WORD_##width) { \\\n\t\t\t\tUINT32 __pa = __ep->paddr + (addr & CPU_PAGE_MASK); \\\n\t\t\t\tif (KAIRO98_IS_VRAM_WORD(__pa)) { \\\n\t\t\t\t\tKAIRO98_SIDE_EFFECT(); \\\n\t\t\t\t\tKAIRO98_VRAM_STORE_##width(__pa, value); \\\n\t\t\t\t\treturn; \\\n\t\t\t\t} \\\n\t\t\t} \\\n\t\t} \\\n\t} \\\n\tcpu_lmemorywrite_##width(addr, value, CPU_PAGE_WRITE_DATA | CPU_STAT_USER_MODE); \\\n\treturn; \\\n\\\n'),
        ('np21w/i386c/ia32/cpu_mem.mcr',
         '\t\tcpu_memorywrite_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\treturn cpu_lmemory_RMW_##width(addr, func, arg); \\\n',
         '\t\tcpu_memorywrite_check(sdp, offset, (length), \\\n\t\t    CHOOSE_EXCEPTION(idx)); \\\n\t} else if (!(sdp->flag & CPU_DESC_FLAG_WHOLEADR)) { \\\n\t\tif (!KAIRO98_LIMIT_OK(sdp, offset, (length)) && \\\n\t\t    !check_limit_upstairs(sdp, offset, (length), SEG_IS_32BIT(sdp))) \\\n\t\t\tgoto range_failure; \\\n\t} \\\n\treturn cpu_lmemory_RMW_##width(addr, func, arg); \\\n'),
    ],
    'np21w/i386c/ia32/exception.c': [
        ('np21w/i386c/ia32/exception.c',
         '''void CPUCALL
exception(int num, int error_code)
{
#if defined(DEBUG)
	extern int cpu_debug_rep_cont;
	extern CPU_REGS cpu_debug_rep_regs;
''',
         '''void CPUCALL
exception(int num, int error_code)
{
	KAIRO98_SIDE_EFFECT();
#if defined(DEBUG)
	extern int cpu_debug_rep_cont;
	extern CPU_REGS cpu_debug_rep_regs;
'''),
        ('np21w/i386c/ia32/exception.c',
         '''void CPUCALL
interrupt(int num, int intrtype, int errorp, int error_code)
{
	descriptor_t gsd;
	UINT idt_idx;
	UINT32 new_ip;
''',
         '''void CPUCALL
interrupt(int num, int intrtype, int errorp, int error_code)
{
	KAIRO98_SIDE_EFFECT();
	descriptor_t gsd;
	UINT idt_idx;
	UINT32 new_ip;
'''),
    ],
    'np21w/i386c/ia32/ia32.c': [
        ('np21w/i386c/ia32/ia32.c',
         '''void CPUCALL
change_pm(BOOL onoff)
{

	if (onoff) {
		VERBOSE(("change_pm: Entering to Protected-Mode..."));
''',
         '''void CPUCALL
change_pm(BOOL onoff)
{
	KAIRO98_SIDE_EFFECT();

	if (onoff) {
		VERBOSE(("change_pm: Entering to Protected-Mode..."));
'''),
        ('np21w/i386c/ia32/ia32.c',
         '''void CPUCALL
change_pg(BOOL onoff)
{

	if (onoff) {
		VERBOSE(("change_pg: Entering to Paging-Mode..."));
''',
         '''void CPUCALL
change_pg(BOOL onoff)
{
	KAIRO98_SIDE_EFFECT();

	if (onoff) {
		VERBOSE(("change_pg: Entering to Paging-Mode..."));
'''),
        ('np21w/i386c/ia32/ia32.c',
         '''{
	int i;

	CPU_STAT_VM86 = onoff;
	if (onoff) {
		VERBOSE(("change_vm: Entering to Virtual-8086-Mode..."));
''',
         '''{
	int i;

	KAIRO98_SIDE_EFFECT();
	CPU_STAT_VM86 = onoff;
	if (onoff) {
		VERBOSE(("change_vm: Entering to Virtual-8086-Mode..."));
'''),
        ('np21w/i386c/ia32/ia32.c',
         '''set_cr3(UINT32 new_cr3)
{

	VERBOSE(("set_CR3: old = %08x, new = 0x%08x", CPU_CR3, new_cr3));

	CPU_CR3 = new_cr3 & CPU_CR3_MASK;
''',
         '''set_cr3(UINT32 new_cr3)
{

	KAIRO98_SIDE_EFFECT();
	VERBOSE(("set_CR3: old = %08x, new = 0x%08x", CPU_CR3, new_cr3));

	CPU_CR3 = new_cr3 & CPU_CR3_MASK;
'''),
    ],
    'np21w/i386c/ia32/ia32.mcr': [
        ('np21w/i386c/ia32/ia32.mcr',
         '\tCPU_REMCLOCK = -1; \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tIRQCHECKTERM() \\\ndo { \\\n\tif (CPU_REMCLOCK > 0) { \\\n\t\tCPU_BASECLOCK -= CPU_REMCLOCK; \\\n\t\tCPU_REMCLOCK = 0; \\\n\t} \\\n} while (/*CONSTCOND*/ 0)\n\n\n/*\n',
         '\tCPU_REMCLOCK = -1; \\\n} while (/*CONSTCOND*/ 0)\n\n/* Keep running after flag-control instructions when no interrupt is pending. */\n#if defined(KAIRO98_ANDROID_IRQ_FAST)\n#define IRQCHECKTERM() \\\ndo { \\\n    if (CPU_REMCLOCK > 0 && \\\n        (CPU_TRAP || CPU_RESETREQ || dmac.working || PICEXISTINTR)) { \\\n        CPU_BASECLOCK -= CPU_REMCLOCK; \\\n        CPU_REMCLOCK = 0; \\\n    } \\\n} while (/*CONSTCOND*/ 0)\n#else\n#define IRQCHECKTERM() \\\ndo { \\\n    if (CPU_REMCLOCK > 0) { \\\n        CPU_BASECLOCK -= CPU_REMCLOCK; \\\n        CPU_REMCLOCK = 0; \\\n    } \\\n} while (/*CONSTCOND*/ 0)\n#endif\n\n\n\n\n/*\n'),
        ('np21w/i386c/ia32/ia32.mcr',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNEAR(clock) \\\n',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n\tif ((SINT32)__dest < 0) { \\\n\t\tkairo98_idle_check(); \\\n\t} \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNEAR(clock) \\\n'),
        ('np21w/i386c/ia32/ia32.mcr',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNEAR32(clock) \\\n',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n\tif (__dest < 0) { \\\n\t\tkairo98_idle_check(); \\\n\t} \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNEAR32(clock) \\\n'),
        ('np21w/i386c/ia32/ia32.mcr',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNOP(clock, d) \\\n',
         '\t\tEXCEPTION(GP_EXCEPTION, 0); \\\n\t} \\\n\tCPU_EIP = __new_ip; \\\n\tif ((SINT32)__dest < 0) { \\\n\t\tkairo98_idle_check(); \\\n\t} \\\n} while (/*CONSTCOND*/ 0)\n\n#define\tJMPNOP(clock, d) \\\n'),
    ],
    'np21w/i386c/ia32/instructions/ctrl_trans.c': [
        ('np21w/i386c/ia32/instructions/ctrl_trans.c',
         '''#include "compiler.h"
#include "ia32/cpu.h"
#include "ia32/ia32.mcr"
#include "ia32/ctrlxfer.h"

#include "ctrl_trans.h"
''',
         '''#include "compiler.h"
#include "ia32/cpu.h"
#include "ia32/ia32.mcr"
#include "pccore.h"
#include "iocore.h"
#include "ia32/ctrlxfer.h"

#include "ctrl_trans.h"
'''),
    ],
    'np21w/i386c/ia32/instructions/flag_ctrl.c': [
        ('np21w/i386c/ia32/instructions/flag_ctrl.c',
         '''#include "compiler.h"
#include "ia32/cpu.h"
#include "ia32/ia32.mcr"

#include "flag_ctrl.h"

''',
         '''#include "compiler.h"
#include "ia32/cpu.h"
#include "ia32/ia32.mcr"
#include "pccore.h"
#include "iocore.h"

#include "flag_ctrl.h"

'''),
        ('np21w/i386c/ia32/instructions/flag_ctrl.c',
         '''		}
	}
	CPU_FLAG |= I_FLAG;
	CPU_TRAP = (CPU_FLAG & (T_FLAG)) == (T_FLAG);
	exec_1step();
	if (CPU_TRAP) {
''',
         '''		}
	}
	CPU_FLAG |= I_FLAG;
	kairo98_counter_sti++;
	CPU_TRAP = (CPU_FLAG & (T_FLAG)) == (T_FLAG);
	exec_1step();
	if (CPU_TRAP) {
'''),
    ],
    'np21w/i386c/ia32/instructions/sse2/sse2.c': [
        ('np21w/i386c/ia32/instructions/sse2/sse2.c',
         '''
#else

/*
 * SSE2 interface
 */
''',
         '''
#else

void SSE2_PSxxWimm(void) { EXCEPTION(UD_EXCEPTION, 0); }
void SSE2_PSxxDimm(void) { EXCEPTION(UD_EXCEPTION, 0); }
void SSE2_PSxxQimm(void) { EXCEPTION(UD_EXCEPTION, 0); }

/*
 * SSE2 interface
 */
'''),
    ],
    'np21w/i386c/ia32/instructions/system_inst.c': [
        ('np21w/i386c/ia32/instructions/system_inst.c',
         '''{
	int idx;

	if (CPU_STAT_PM && (CPU_STAT_VM86 || CPU_STAT_CPL != 0)) {
		VERBOSE(("RDMSR: VM86(%s) or CPL(%d) != 0", CPU_STAT_VM86 ? "true" : "false", CPU_STAT_CPL));
		EXCEPTION(GP_EXCEPTION, 0);
''',
         '''{
	int idx;

	KAIRO98_SIDE_EFFECT();
	if (CPU_STAT_PM && (CPU_STAT_VM86 || CPU_STAT_CPL != 0)) {
		VERBOSE(("RDMSR: VM86(%s) or CPL(%d) != 0", CPU_STAT_VM86 ? "true" : "false", CPU_STAT_CPL));
		EXCEPTION(GP_EXCEPTION, 0);
'''),
        ('np21w/i386c/ia32/instructions/system_inst.c',
         '''void
RDTSC(void)
{
#if defined(SUPPORT_IA32_HAXM)&&defined(_WIN32)
	LARGE_INTEGER li = {0};
	LARGE_INTEGER qpf;
''',
         '''void
RDTSC(void)
{
	KAIRO98_SIDE_EFFECT();
#if defined(SUPPORT_IA32_HAXM)&&defined(_WIN32)
	LARGE_INTEGER li = {0};
	LARGE_INTEGER qpf;
'''),
        ('np21w/i386c/ia32/instructions/system_inst.c',
         '''{
	int idx;

	if(!(CPU_CR4 & CPU_CR4_PCE)){
		if (CPU_STAT_PM && (CPU_STAT_VM86 || CPU_STAT_CPL != 0)) {
			VERBOSE(("RDPMC: VM86(%s) or CPL(%d) != 0", CPU_STAT_VM86 ? "true" : "false", CPU_STAT_CPL));
''',
         '''{
	int idx;

	KAIRO98_SIDE_EFFECT();
	if(!(CPU_CR4 & CPU_CR4_PCE)){
		if (CPU_STAT_PM && (CPU_STAT_VM86 || CPU_STAT_CPL != 0)) {
			VERBOSE(("RDPMC: VM86(%s) or CPL(%d) != 0", CPU_STAT_VM86 ? "true" : "false", CPU_STAT_CPL));
'''),
    ],
    'np21w/i386c/ia32/interface.c': [
        ('np21w/i386c/ia32/interface.c',
         '''void
ia32(void)
{
	switch (sigsetjmp(exec_1step_jmpbuf, 1)) {
	case 0:
		break;

''',
         '''void
ia32(void)
{
	/* Emulated exceptions and panic do not change the host signal mask. */
	switch (sigsetjmp(exec_1step_jmpbuf, 0)) {
	case 0:
		break;

'''),
        ('np21w/i386c/ia32/interface.c',
         '''void
ia32_step(void)
{
	switch (sigsetjmp(exec_1step_jmpbuf, 1)) {
	case 0:
		break;

''',
         '''void
ia32_step(void)
{
	/* Emulated exceptions and panic do not change the host signal mask. */
	switch (sigsetjmp(exec_1step_jmpbuf, 0)) {
	case 0:
		break;

'''),
        ('np21w/i386c/ia32/interface.c',
         '''{
	UINT32 adrs;

	if (!CPU_STAT_PM || CPU_STAT_VM86) {
#if 1
		adrs = CPU_PREV_EIP + (CPU_CS << 4);
''',
         '''{
	UINT32 adrs;

	KAIRO98_SIDE_EFFECT();
	if (!CPU_STAT_PM || CPU_STAT_VM86) {
#if 1
		adrs = CPU_PREV_EIP + (CPU_CS << 4);
'''),
    ],
    'np21w/i386c/ia32/paging.c': [
        ('np21w/i386c/ia32/paging.c',
         ' * |\n * +- CR3(物理アドレス)\n */\n/* TLB */\nstruct tlb_entry {\n\tUINT32\ttag;\t/* linear address */\n#define\tTLB_ENTRY_TAG_VALID\t\t(1 << 0)\n/*\tpde & pte & CPU_PTE_WRITABLE\t(1 << 1)\t*/\n/*\tpde & pte & CPU_PTE_USER_MODE\t(1 << 2)\t*/\n#define\tTLB_ENTRY_TAG_DIRTY\t\tCPU_PTE_DIRTY\t\t/* (1 << 6) */\n#define\tTLB_ENTRY_TAG_GLOBAL\t\tCPU_PTE_GLOBAL_PAGE\t/* (1 << 8) */\n#define\tTLB_ENTRY_TAG_MAX_SHIFT\t\t12\n\tUINT32\tpaddr;\t/* physical address */\n#if IA32_MEMORY_FAST_PATH\n\tUINT8\t*host_page;\t/* direct host pointer for ordinary RAM page */\n\tUINT32\tfast_flags;\t/* predecoded access/direct flags */\n#endif\n};\n/*\n * TLB fast path\n */\n#define\tTLB_TAG_SHIFT\t\tTLB_ENTRY_TAG_MAX_SHIFT\n#define\tTLB_TAG_MASK\t\t(~((1 << TLB_TAG_SHIFT) - 1))\n#define\tTLB_GET_TAG_ADDR(ep)\t((ep)->tag & TLB_TAG_MASK)\n#define\tTLB_SET_TAG_ADDR(ep, addr) \\\ndo { \\\n\t(ep)->tag &= ~TLB_TAG_MASK; \\\n\t(ep)->tag |= (addr) & TLB_TAG_MASK; \\\n} while (/*CONSTCOND(*/ 0)\n\n#define\tTLB_IS_VALID(ep)\t((ep)->tag & TLB_ENTRY_TAG_VALID)\n#define\tTLB_SET_VALID(ep)\t((ep)->tag = TLB_ENTRY_TAG_VALID)\n#define\tTLB_SET_INVALID(ep)\t((ep)->tag = 0)\n\n#define\tTLB_IS_WRITABLE(ep)\t((ep)->tag & CPU_PTE_WRITABLE)\n#define\tTLB_IS_USERMODE(ep)\t((ep)->tag & CPU_PTE_USER_MODE)\n#define\tTLB_IS_DIRTY(ep)\t((ep)->tag & TLB_ENTRY_TAG_DIRTY)\n#if (CPU_FEATURES_ALL & CPU_FEATURE_PGE) == CPU_FEATURE_PGE\n#define\tTLB_IS_GLOBAL(ep)\t((ep)->tag & TLB_ENTRY_TAG_GLOBAL)\n#else\n#define\tTLB_IS_GLOBAL(ep)\t0\n#endif\n\n#define\tTLB_SET_TAG_FLAGS(ep, entry, bit) \\\ndo { \\\n\t(ep)->tag |= (entry) & (CPU_PTE_GLOBAL_PAGE|CPU_PTE_DIRTY); \\\n\t(ep)->tag |= (bit) & (CPU_PTE_WRITABLE|CPU_PTE_USER_MODE); \\\n} while (/*CONSTCOND*/ 0)\n\n#if IA32_MEMORY_FAST_PATH\n// ページ高速アクセスの簡易判定のためのフラグ\n// このフラグ判定で弾かれた場合は通常のpaging関数で詳細判定する\n#define\tTLBF_SUPER_READ\t\t0x00000001UL // スーパーバイザモード読み取り許可\n#define\tTLBF_USER_READ\t\t0x00000002UL // ユーザーモード読み取り許可\n#define\tTLBF_SUPER_WRITE\t0x00000004UL // CR0.WP==0の場合のスーパーバイザモード書き込み許可\n#define\tTLBF_SUPER_WRITE_WP\t0x00000008UL // CR0.WP==1の場合のスーパーバイザモード書き込み許可\n#define\tTLBF_USER_WRITE\t\t0x00000010UL // ユーザーモード書き込み許可\n#define\tTLBF_CODE_SUPER\t\t0x00000020UL // スーパーバイザモード命令フェッチ許可\n#define\tTLBF_CODE_USER\t\t0x00000040UL // ユーザーモード命令フェッチ許可\n#define\tTLBF_DIRECT_READ\t0x00000100UL // host_pageポインタへ直接読み取り可能\n#define\tTLBF_DIRECT_WRITE\t0x00000200UL // host_pageポインタへ直接書き込み可能\n#endif\n\n#define\tNTLB\t\t2\t/* 0: DTLB, 1: ITLB */\n#define\tNENTRY\t\t(1 << 6)\n#define\tTLB_ENTRY_SHIFT\t12\n#define\tTLB_ENTRY_MASK\t(NENTRY - 1)\n\ntypedef struct {\n\tstruct tlb_entry entry[NENTRY];\n} tlb_t;\nstatic tlb_t tlb[NTLB];\n\n#if IA32_MEMORY_FAST_PATH\n#if defined(__GNUC__)\n#define TLB_FAST_INLINE static __inline__ __attribute__((always_inline))\n#elif defined(_MSC_VER)\n#define TLB_FAST_INLINE static __inline\n#else\n#define TLB_FAST_INLINE static INLINE\n#endif\n\n#define\tTLB_USER_INDEX(ucrw)\t(((ucrw) & CPU_PAGE_USER_MODE) >> 3)\n\nstatic UINT32 tlb_data_read_fast_flags[2] = {\n\tTLBF_SUPER_READ,\n\tTLBF_USER_READ\n};\nstatic UINT32 tlb_data_write_fast_flags[2] = {\n\tTLBF_SUPER_WRITE,\n\tTLBF_USER_WRITE\n};\nstatic UINT32 tlb_code_read_fast_flags[2] = {\n\tTLBF_CODE_SUPER,\n\tTLBF_CODE_USER\n};\n\nTLB_FAST_INLINE struct tlb_entry *\ntlb_lookup_data_read_fast(UINT32 laddr, int ucrw)\n{\n\tstruct tlb_entry *ep;\n\tUINT32 flag;\n\tint idx;\n\n\tidx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;\n\tep = &tlb[0].entry[idx];\n\tflag = tlb_data_read_fast_flags[TLB_USER_INDEX(ucrw)];\n\n\tif (TLB_IS_VALID(ep) &&\n\t    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&\n\t    (ep->fast_flags & flag)) {\n\t\treturn ep;\n\t}\n\treturn NULL;\n}\n\nTLB_FAST_INLINE struct tlb_entry *\ntlb_lookup_data_write_fast(UINT32 laddr, int ucrw)\n{\n\tstruct tlb_entry *ep;\n\tUINT32 flag;\n\tint idx;\n\n\tidx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;\n\tep = &tlb[0].entry[idx];\n\tflag = tlb_data_write_fast_flags[TLB_USER_INDEX(ucrw)];\n\n\tif (TLB_IS_VALID(ep) &&\n\t    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&\n\t    (ep->fast_flags & flag)) {\n\t\treturn ep;\n\t}\n\treturn NULL;\n}\n\nTLB_FAST_INLINE struct tlb_entry *\ntlb_lookup_code_fast(UINT32 laddr, int ucrw)\n{\n\tstruct tlb_entry *ep;\n\tUINT32 flag;\n\tint idx;\n\n\tidx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;\n\tep = &tlb[1].entry[idx];\n\tflag = tlb_code_read_fast_flags[TLB_USER_INDEX(ucrw)];\n\n\tif (TLB_IS_VALID(ep) &&\n\t    ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) &&\n\t    (ep->fast_flags & flag)) {\n\t\treturn ep;\n\t}\n\treturn NULL;\n}\n\nTLB_FAST_INLINE struct tlb_entry *\ntlb_lookup_fast(UINT32 laddr, int ucrw)\n{\n\t/* Preserve the original precedence for the unlikely WRITE|CODE case. */\n\tif (ucrw & CPU_PAGE_WRITE) {\n\t\treturn tlb_lookup_data_write_fast(laddr, ucrw);\n\t}\n\tif (ucrw & CPU_PAGE_CODE) {\n\t\treturn tlb_lookup_code_fast(laddr, ucrw);\n\t}\n\treturn tlb_lookup_data_read_fast(laddr, ucrw);\n}\n\n\nTLB_FAST_INLINE UINT32\ntlb_make_fast_flags(UINT entry, int bit, int n, int direct)\n{\n\tUINT32 flags;\n\tint writable;\n\tint user;\n\n\tflags = TLBF_SUPER_READ;\n\twritable = (bit & CPU_PTE_WRITABLE) != 0;\n\tuser = (bit & CPU_PTE_USER_MODE) != 0;\n\n\tif (user) {\n\t\tflags |= TLBF_USER_READ;\n\t}\n\tif (n == 1) {\n\t\tflags |= TLBF_CODE_SUPER;\n\t\tif (user) {\n\t\t\tflags |= TLBF_CODE_USER;\n\t\t}\n\t}\n\n\tif (entry & CPU_PTE_DIRTY) {\n\t\tflags |= TLBF_SUPER_WRITE;\n\t\tif (writable) {\n\t\t\tflags |= TLBF_SUPER_WRITE_WP;\n\t\t\tif (user) {\n\t\t\t\tflags |= TLBF_USER_WRITE;\n\t\t\t}\n\t\t}\n\t}\n\n\tif (direct) {\n\t\tflags |= TLBF_DIRECT_READ;\n\t\tif (flags & (TLBF_SUPER_WRITE|TLBF_SUPER_WRITE_WP|TLBF_USER_WRITE)) {\n\t\t\tflags |= TLBF_DIRECT_WRITE;\n\t\t}\n\t}\n\treturn flags;\n}\n#endif\t/* IA32_MEMORY_FAST_PATH */\n\nvoid MEMCALL\ntlb_update_access_flags(void)\n{\n#if IA32_MEMORY_FAST_PATH\n\ttlb_data_read_fast_flags[0] = TLBF_SUPER_READ;\n\ttlb_data_read_fast_flags[1] = TLBF_USER_READ;\n\ttlb_data_write_fast_flags[0] = CPU_STAT_WP ? TLBF_SUPER_WRITE_WP : TLBF_SUPER_WRITE;\n\ttlb_data_write_fast_flags[1] = TLBF_USER_WRITE;\n\ttlb_code_read_fast_flags[0] = TLBF_CODE_SUPER;\n\ttlb_code_read_fast_flags[1] = TLBF_CODE_USER;\n#endif\n}\n\n',
         ''' * |
 * +- CR3(物理アドレス)
 */
#include "kairo98_tlb.h"

tlb_t kairo98_tlb[NTLB];
#if IA32_MEMORY_FAST_PATH
UINT32 kairo98_tlb_data_read_fast_flags[2] = {
	TLBF_SUPER_READ,
	TLBF_USER_READ
};
UINT32 kairo98_tlb_data_write_fast_flags[2] = {
	TLBF_SUPER_WRITE,
	TLBF_USER_WRITE
};
UINT32 kairo98_tlb_code_read_fast_flags[2] = {
	TLBF_CODE_SUPER,
	TLBF_CODE_USER
};
#endif

void MEMCALL
tlb_update_access_flags(void)
{
#if IA32_MEMORY_FAST_PATH
	kairo98_tlb_data_read_fast_flags[0] = TLBF_SUPER_READ;
	kairo98_tlb_data_read_fast_flags[1] = TLBF_USER_READ;
	kairo98_tlb_data_write_fast_flags[0] = CPU_STAT_WP ? TLBF_SUPER_WRITE_WP : TLBF_SUPER_WRITE;
	kairo98_tlb_data_write_fast_flags[1] = TLBF_USER_WRITE;
	kairo98_tlb_code_read_fast_flags[0] = TLBF_CODE_SUPER;
	kairo98_tlb_code_read_fast_flags[1] = TLBF_CODE_USER;
#endif
}

'''),
        ('np21w/i386c/ia32/paging.c',
         '''#define TLB_PAGE_OFFSET(laddr)	((laddr) & CPU_PAGE_MASK)
#define TLB_PAGE_BASE(laddr)	((laddr) & ~CPU_PAGE_MASK)

typedef struct codefetch_cache_entry {
	UINT32	lpage;		/* キャッシュ対象の線形ページ先頭アドレス　linear page base */
	int	ucrw;		/* アクセス種別　permission key used to create this cache */
	UINT8	*host_page;	/* 直接アクセス可能な場合のポインタ　direct host pointer for this linear page */
} codefetch_cache_entry_t;

static codefetch_cache_entry_t codefetch_cache;

static void MEMCALL
codefetch_cache_invalidate(void)
''',
         '''#define TLB_PAGE_OFFSET(laddr)	((laddr) & CPU_PAGE_MASK)
#define TLB_PAGE_BASE(laddr)	((laddr) & ~CPU_PAGE_MASK)

#if defined(KAIRO98_ANDROID_FETCH_FAST)
__attribute__((visibility("hidden")))
kairo98_codefetch_cache_entry_t kairo98_codefetch_cache;
#define codefetch_cache kairo98_codefetch_cache
#else
typedef struct codefetch_cache_entry {
	UINT32 key; /* page base plus ucrw in the unused low 12 bits */
	UINT8	*host_page;	/* 直接アクセス可能な場合のポインタ　direct host pointer for this linear page */
} codefetch_cache_entry_t;

static codefetch_cache_entry_t codefetch_cache;
#endif

static void MEMCALL
codefetch_cache_invalidate(void)
'''),
        ('np21w/i386c/ia32/paging.c',
         '''codefetch_cache_invalidate_page(UINT32 laddr)
{
	if (codefetch_cache.host_page != NULL &&
	    codefetch_cache.lpage == TLB_PAGE_BASE(laddr)) {
		codefetch_cache.host_page = NULL;
	}
}
''',
         '''codefetch_cache_invalidate_page(UINT32 laddr)
{
	if (codefetch_cache.host_page != NULL &&
	    (codefetch_cache.key & ~CPU_PAGE_MASK) == TLB_PAGE_BASE(laddr)) {
		codefetch_cache.host_page = NULL;
	}
}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''codefetch_cache_lookup(UINT32 laddr, int ucrw)
{
	if (codefetch_cache.host_page != NULL &&
	    codefetch_cache.lpage == TLB_PAGE_BASE(laddr) &&
	    codefetch_cache.ucrw == ucrw) {
		return codefetch_cache.host_page;
	}
	return NULL;
''',
         '''codefetch_cache_lookup(UINT32 laddr, int ucrw)
{
	if (codefetch_cache.host_page != NULL &&
	    codefetch_cache.key == (TLB_PAGE_BASE(laddr) | (UINT32)ucrw)) {
		return codefetch_cache.host_page;
	}
	return NULL;
'''),
        ('np21w/i386c/ia32/paging.c',
         '''codefetch_cache_update(UINT32 laddr, int ucrw, struct tlb_entry *ep)
{
	if (ep != NULL && (ep->fast_flags & TLBF_DIRECT_READ)) {
		codefetch_cache.lpage = TLB_PAGE_BASE(laddr);
		codefetch_cache.ucrw = ucrw;
		codefetch_cache.host_page = ep->host_page;
		return ep->host_page;
	}
''',
         '''codefetch_cache_update(UINT32 laddr, int ucrw, struct tlb_entry *ep)
{
	if (ep != NULL && (ep->fast_flags & TLBF_DIRECT_READ)) {
		codefetch_cache.key = TLB_PAGE_BASE(laddr) | (UINT32)ucrw;
		codefetch_cache.host_page = ep->host_page;
		return ep->host_page;
	}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''	if (ep != NULL) {
		offset = TLB_PAGE_OFFSET(laddr);
		if (ep->fast_flags & TLBF_DIRECT_WRITE) {
			ep->host_page[offset] = value;
			return;
		}
''',
         '''	if (ep != NULL) {
		offset = TLB_PAGE_OFFSET(laddr);
		if (ep->fast_flags & TLBF_DIRECT_WRITE) {
			KAIRO98_SIDE_EFFECT();
			ep->host_page[offset] = value;
			return;
		}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''			offset = TLB_PAGE_OFFSET(laddr);
			if ((laddr + 1) & CPU_PAGE_MASK) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					STOREINTELWORD(ep->host_page + offset, value);
					return;
				}
''',
         '''			offset = TLB_PAGE_OFFSET(laddr);
			if ((laddr + 1) & CPU_PAGE_MASK) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					KAIRO98_SIDE_EFFECT();
					STOREINTELWORD(ep->host_page + offset, value);
					return;
				}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''			remain = CPU_PAGE_SIZE - offset;
			if (remain >= sizeof(value)) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					STOREINTELDWORD(ep->host_page + offset, value);
					return;
				}
''',
         '''			remain = CPU_PAGE_SIZE - offset;
			if (remain >= sizeof(value)) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					KAIRO98_SIDE_EFFECT();
					STOREINTELDWORD(ep->host_page + offset, value);
					return;
				}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''			if (remain >= sizeof(value)) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					host = ep->host_page + offset;
					STOREINTELDWORD(host, (UINT32)value);
					STOREINTELDWORD(host + 4, (UINT32)(value >> 32));
					return;
				}
''',
         '''			if (remain >= sizeof(value)) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					host = ep->host_page + offset;
					KAIRO98_SIDE_EFFECT();
					STOREINTELDWORD(host, (UINT32)value);
					KAIRO98_SIDE_EFFECT();
					STOREINTELDWORD(host + 4, (UINT32)(value >> 32));
					return;
				}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''			if (remain >= size) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					host = ep->host_page + offset;
					STOREINTELDWORD(host, value->d.l[0]);
					STOREINTELDWORD(host + 4, value->d.l[1]);
					STOREINTELWORD(host + 8, value->d.h);
					return;
				}
''',
         '''			if (remain >= size) {
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					host = ep->host_page + offset;
					KAIRO98_SIDE_EFFECT();
					STOREINTELDWORD(host, value->d.l[0]);
					KAIRO98_SIDE_EFFECT();
					STOREINTELDWORD(host + 4, value->d.l[1]);
					KAIRO98_SIDE_EFFECT();
					STOREINTELWORD(host + 8, value->d.h);
					return;
				}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''			if (ep != NULL) {
				offset = TLB_PAGE_OFFSET(laddr);
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					CopyMemory(ep->host_page + offset, p, inPageSize);
				} else {
					memp_writes(ep->paddr + offset, p, inPageSize);
''',
         '''			if (ep != NULL) {
				offset = TLB_PAGE_OFFSET(laddr);
				if (ep->fast_flags & TLBF_DIRECT_WRITE) {
					KAIRO98_SIDE_EFFECT();
					CopyMemory(ep->host_page + offset, p, inPageSize);
				} else {
					memp_writes(ep->paddr + offset, p, inPageSize);
'''),
        ('np21w/i386c/ia32/paging.c',
         '''void
tlb_init(void)
{
	memset(tlb, 0, sizeof(tlb));
	tlb_update_access_flags();
#if IA32_MEMORY_FAST_PATH && !defined(SUPPORT_IA32_HAXM)
	codefetch_cache_invalidate();
''',
         '''void
tlb_init(void)
{
	memset(kairo98_tlb, 0, sizeof(kairo98_tlb));
	tlb_update_access_flags();
#if IA32_MEMORY_FAST_PATH && !defined(SUPPORT_IA32_HAXM)
	codefetch_cache_invalidate();
'''),
        ('np21w/i386c/ia32/paging.c',
         '''
	for (n = 0; n < NTLB; n++) {
		for (i = 0; i < NENTRY ; i++) {
			ep = &tlb[n].entry[i];
			if (TLB_IS_VALID(ep) && !TLB_IS_GLOBAL(ep)) {
				TLB_SET_INVALID(ep);
			}
''',
         '''
	for (n = 0; n < NTLB; n++) {
		for (i = 0; i < NENTRY ; i++) {
			ep = &kairo98_tlb[n].entry[i];
			if (TLB_IS_VALID(ep) && !TLB_IS_GLOBAL(ep)) {
				TLB_SET_INVALID(ep);
			}
'''),
        ('np21w/i386c/ia32/paging.c',
         '''	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;

	for (n = 0; n < NTLB; n++) {
		ep = &tlb[n].entry[idx];
		if (TLB_IS_VALID(ep)) {
			if ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) {
				TLB_SET_INVALID(ep);
''',
         '''	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;

	for (n = 0; n < NTLB; n++) {
		ep = &kairo98_tlb[n].entry[idx];
		if (TLB_IS_VALID(ep)) {
			if ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) {
				TLB_SET_INVALID(ep);
'''),
        ('np21w/i386c/ia32/paging.c',
         '''
	n = (ucrw & CPU_PAGE_CODE) ? 1 : 0;
	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &tlb[n].entry[idx];

	if (TLB_IS_VALID(ep)) {
		if ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) {
''',
         '''
	n = (ucrw & CPU_PAGE_CODE) ? 1 : 0;
	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &kairo98_tlb[n].entry[idx];

	if (TLB_IS_VALID(ep)) {
		if ((laddr & TLB_TAG_MASK) == TLB_GET_TAG_ADDR(ep)) {
'''),
        ('np21w/i386c/ia32/paging.c',
         '''
	n = bit & 1;
	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &tlb[n].entry[idx];

#if IA32_MEMORY_FAST_PATH && !defined(SUPPORT_IA32_HAXM)
	if (n == 1) {
''',
         '''
	n = bit & 1;
	idx = (laddr >> TLB_ENTRY_SHIFT) & TLB_ENTRY_MASK;
	ep = &kairo98_tlb[n].entry[idx];

#if IA32_MEMORY_FAST_PATH && !defined(SUPPORT_IA32_HAXM)
	if (n == 1) {
'''),
    ],
    'np21w/i386c/ia32/paging.h': [
        ('np21w/i386c/ia32/paging.h',
         '''
#define CPU_PAGE_SIZE      	 0x1000
#define CPU_PAGE_MASK       	(CPU_PAGE_SIZE - 1)

/* ucrw */
#define	CPU_PAGE_WRITE		(1 << 0)
''',
         '''
#define CPU_PAGE_SIZE      	 0x1000
#define CPU_PAGE_MASK       	(CPU_PAGE_SIZE - 1)
#if defined(KAIRO98_ANDROID_FETCH_FAST)
typedef struct {
    UINT32 key;
    UINT8 *host_page;
} kairo98_codefetch_cache_entry_t;
extern __attribute__((visibility("hidden")))
    kairo98_codefetch_cache_entry_t kairo98_codefetch_cache;
#endif

/* ucrw */
#define	CPU_PAGE_WRITE		(1 << 0)
'''),
    ],
    'np21w/io/iocore.c': [
        ('np21w/io/iocore.c',
         '''}

void IOOUTCALL iocore_out8(UINT port, REG8 dat) {

	IOFUNC	iof;

''',
         '''}

void IOOUTCALL iocore_out8(UINT port, REG8 dat) {
	KAIRO98_SIDE_EFFECT();

	IOFUNC	iof;

'''),
        ('np21w/io/iocore.c',
         '''}

REG8 IOINPCALL iocore_inp8(UINT port) {

	IOFUNC	iof;
	REG8	ret;
''',
         '''}

REG8 IOINPCALL iocore_inp8(UINT port) {
	KAIRO98_SIDE_EFFECT();

	IOFUNC	iof;
	REG8	ret;
'''),
        ('np21w/io/iocore.c',
         '''}

void IOOUTCALL iocore_out16(UINT port, REG16 dat) {

	IOFUNC	iof;

''',
         '''}

void IOOUTCALL iocore_out16(UINT port, REG16 dat) {
	KAIRO98_SIDE_EFFECT();

	IOFUNC	iof;

'''),
        ('np21w/io/iocore.c',
         '''}

REG16 IOINPCALL iocore_inp16(UINT port) {

	IOFUNC	iof;
	REG8	ret;
''',
         '''}

REG16 IOINPCALL iocore_inp16(UINT port) {
	KAIRO98_SIDE_EFFECT();

	IOFUNC	iof;
	REG8	ret;
'''),
        ('np21w/io/iocore.c',
         '''}

void IOOUTCALL iocore_out32(UINT port, UINT32 dat) {

	CPU_REMCLOCK -= iocore.busclock;
#if defined(SUPPORT_PC9821)
''',
         '''}

void IOOUTCALL iocore_out32(UINT port, UINT32 dat) {
	KAIRO98_SIDE_EFFECT();

	CPU_REMCLOCK -= iocore.busclock;
#if defined(SUPPORT_PC9821)
'''),
        ('np21w/io/iocore.c',
         '''}

UINT32 IOINPCALL iocore_inp32(UINT port) {

	UINT32	ret;

''',
         '''}

UINT32 IOINPCALL iocore_inp32(UINT port) {
	KAIRO98_SIDE_EFFECT();

	UINT32	ret;

'''),
    ],
    'np21w/io/mouseif.c': [
        ('np21w/io/mouseif.c',
         '''#include	"compiler.h"
#include	"mousemng.h"
#include	"cpucore.h"
#include	"pccore.h"
#include	"iocore.h"
''',
         '''#include	"compiler.h"
#include	"mousemng.h"
#include	"input_telemetry.h"
#include	"cpucore.h"
#include	"pccore.h"
#include	"iocore.h"
'''),
        ('np21w/io/mouseif.c',
         '''	REG8	portc;

	if (mouseif.upd8255.mode & uPD8255_PORTA) {
		calc_mousexy();
		ret = mouseif.b;
		if (np2cfg.MOUSERAPID) {
''',
         '''	REG8	portc;

	if (mouseif.upd8255.mode & uPD8255_PORTA) {
		kairo98_observe_mouse_port_read();
		calc_mousexy();
		ret = mouseif.b;
		if (np2cfg.MOUSERAPID) {
'''),
    ],
    'np21w/io/printif.c': [
        ('np21w/io/printif.c',
         '''		COMMNG	prt;
		prt = cm_prt;
		if (prt != NULL) {
			prt->msg(prt, COMMSG_REOPEN, NULL);
		}
	}

''',
         '''		COMMNG	prt;
		prt = cm_prt;
		if (prt != NULL) {
			prt->msg(prt, COMMSG_REOPEN, (INTPTR)0);
		}
	}

'''),
        ('np21w/io/printif.c',
         '''void printif_finishjob(void) {

	if (cm_prt) {
		cm_prt->msg(cm_prt, COMMSG_REOPEN, NULL);
	}
}

''',
         '''void printif_finishjob(void) {

	if (cm_prt) {
		cm_prt->msg(cm_prt, COMMSG_REOPEN, (INTPTR)0);
	}
}

'''),
    ],
    'np21w/mem/memegc.c': [
        ('np21w/mem/memegc.c',
         '''static	EGCQUAD		egc_src;
static	EGCQUAD		egc_data;

static const UINT planead[4] = {VRAM_B, VRAM_R, VRAM_G, VRAM_E};


''',
         '''static	EGCQUAD		egc_src;
static	EGCQUAD		egc_data;

/* Diagnostic counters for the Android debug status line. */
unsigned long long kairo98_egc_writes;
unsigned long long kairo98_egc_reads;
unsigned long long kairo98_egc_fast_reads;
unsigned long long kairo98_egc_mismatch;
unsigned long long kairo98_egc_dec_reads;
unsigned long long kairo98_egc_cpu_reads;
unsigned int kairo98_egc_snap[10];
int kairo98_egc_snap_pending;


static const UINT planead[4] = {VRAM_B, VRAM_R, VRAM_G, VRAM_E};


'''),
        ('np21w/mem/memegc.c',
         '''	}
}

REG16 MEMCALL egc_readword(UINT32 addr) {
/*	
	UINT32	ad;
	int pl;
''',
         '''	}
}

/* Original ascending-direction shifter input for one word of egc.lastvram. */
static void MEMCALL kairo98_egc_read_inc_slow(void) {
	egc.inptr[ 0] = egc.lastvram._b[0][EGCADDR_L];
	egc.inptr[ 1] = egc.lastvram._b[0][EGCADDR_H];
	egc.inptr[ 4] = egc.lastvram._b[1][EGCADDR_L];
	egc.inptr[ 5] = egc.lastvram._b[1][EGCADDR_H];
	egc.inptr[ 8] = egc.lastvram._b[2][EGCADDR_L];
	egc.inptr[ 9] = egc.lastvram._b[2][EGCADDR_H];
	egc.inptr[12] = egc.lastvram._b[3][EGCADDR_L];
	egc.inptr[13] = egc.lastvram._b[3][EGCADDR_H];
	shiftinput_incw();
}

#if defined(KAIRO98_ANDROID_FETCH_FAST)
/*
 * Direct equivalents of kairo98_egc_read_inc_slow for two shifter states
 * that games reach on every word of a blit. Each case performs exactly the
 * stores and register updates that shiftinput_incw and the egcsftw handler
 * would, so the shifter state afterwards is identical; anything else falls
 * back to the original code. KAIRO98_EGC_VERIFY runs both and counts any
 * divergence in kairo98_egc_mismatch.
 */
static int MEMCALL kairo98_egc_read_inc_fast_apply(void) {
	UINT8 *in = egc.inptr;

	if (egc.stack != 0 || egc.srcbit != 0 || in != egc.outptr) {
		return 0;
	}
	if (egc.func == 0 && egc.dstbit == 0 && egc.remain >= 16) {
		/*
		 * No shift, whole word: shiftinput_incw advances inptr by two and
		 * raises stack to 16; egcsftw_upn0 consumes all 16 bits, copies
		 * the eight bytes at outptr into egc_src (advancing outptr by two),
		 * takes 16 off remain, and resets the shifter when remain hits 0.
		 */
		in[ 0] = egc.lastvram._b[0][EGCADDR_L];
		in[ 1] = egc.lastvram._b[0][EGCADDR_H];
		in[ 4] = egc.lastvram._b[1][EGCADDR_L];
		in[ 5] = egc.lastvram._b[1][EGCADDR_H];
		in[ 8] = egc.lastvram._b[2][EGCADDR_L];
		in[ 9] = egc.lastvram._b[2][EGCADDR_H];
		in[12] = egc.lastvram._b[3][EGCADDR_L];
		in[13] = egc.lastvram._b[3][EGCADDR_H];
		egc.inptr = in + 2;
		egc.outptr = in + 2;
		egc.srcmask.w = 0xffff;
		egc_src.d[0] = egc.lastvram.d[0];
		egc_src.d[1] = egc.lastvram.d[1];
		egc.remain -= 16;
		if (!egc.remain) {
			egcshift();
		}
		return 1;
	}
	if (egc.func == 2 && egc.dstbit >= 8 && (egc.dstbit & 7) != 0 &&
	    egc.sft8bitr == (egc.dstbit & 7) && egc.remain > (UINT)(8 - (egc.dstbit & 7))) {
		/*
		 * Destination offset of 8 or more with no source offset, fresh
		 * word: shiftinput_incw advances inptr by two and sets stack to 16;
		 * egcsftw_upr0 lowers stack by 16 - dstbit, the low-byte step only
		 * drops dstbit by 8 and clears the low source mask, and the
		 * high-byte step writes each plane's low input byte shifted right
		 * by the remaining offset into the high source byte, masks it,
		 * takes 8 - offset off remain, and zeroes dstbit. outptr is not
		 * advanced and no reset occurs because remain stays above zero.
		 */
		UINT sub = egc.dstbit & 7;

		in[ 0] = egc.lastvram._b[0][EGCADDR_L];
		in[ 1] = egc.lastvram._b[0][EGCADDR_H];
		in[ 4] = egc.lastvram._b[1][EGCADDR_L];
		in[ 5] = egc.lastvram._b[1][EGCADDR_H];
		in[ 8] = egc.lastvram._b[2][EGCADDR_L];
		in[ 9] = egc.lastvram._b[2][EGCADDR_H];
		in[12] = egc.lastvram._b[3][EGCADDR_L];
		in[13] = egc.lastvram._b[3][EGCADDR_H];
		egc.inptr = in + 2;
		egc.stack = egc.dstbit;
		egc.srcmask._b[EGCADDR_L] = 0;
		egc.srcmask._b[EGCADDR_H] = bytemask_u0[sub + (7*8)];
		egc.remain -= (8 - sub);
		egc.dstbit = 0;
		egc_src._b[0][EGCADDR_H] = (UINT8)(egc.lastvram._b[0][EGCADDR_L] >> sub);
		egc_src._b[1][EGCADDR_H] = (UINT8)(egc.lastvram._b[1][EGCADDR_L] >> sub);
		egc_src._b[2][EGCADDR_H] = (UINT8)(egc.lastvram._b[2][EGCADDR_L] >> sub);
		egc_src._b[3][EGCADDR_H] = (UINT8)(egc.lastvram._b[3][EGCADDR_L] >> sub);
		return 1;
	}
	return 0;
}

static int MEMCALL kairo98_egc_read_inc_fast(void) {
#if defined(KAIRO98_EGC_VERIFY)
	_EGC saved_egc = egc;
	EGCQUAD saved_src = egc_src;
	_EGC fast_egc;
	EGCQUAD fast_src;

	if (!kairo98_egc_read_inc_fast_apply()) {
		return 0;
	}
	fast_egc = egc;
	fast_src = egc_src;
	egc = saved_egc;
	egc_src = saved_src;
	kairo98_egc_read_inc_slow();
	if (memcmp(&fast_egc, &egc, sizeof(egc)) != 0 ||
	    memcmp(&fast_src, &egc_src, sizeof(egc_src)) != 0) {
		kairo98_egc_mismatch++;
	}
	kairo98_egc_fast_reads++;
	return 1;
#else
	if (kairo98_egc_read_inc_fast_apply()) {
		kairo98_egc_fast_reads++;
		return 1;
	}
	return 0;
#endif
}
#endif

REG16 MEMCALL egc_readword(UINT32 addr) {
	kairo98_egc_reads++;
/*	
	UINT32	ad;
	int pl;
'''),
        ('np21w/mem/memegc.c',
         '''	egc.lastvram.w[2] = *(UINT16 *)(&mem[ad + VRAM_G]);
	egc.lastvram.w[3] = *(UINT16 *)(&mem[ad + VRAM_E]);

	// shift input
	if (!(egc.ope & 0x400)) {
		if (!(egc.sft & 0x1000)) {
			egc.inptr[ 0] = egc.lastvram._b[0][EGCADDR_L];
			egc.inptr[ 1] = egc.lastvram._b[0][EGCADDR_H];
			egc.inptr[ 4] = egc.lastvram._b[1][EGCADDR_L];
			egc.inptr[ 5] = egc.lastvram._b[1][EGCADDR_H];
			egc.inptr[ 8] = egc.lastvram._b[2][EGCADDR_L];
			egc.inptr[ 9] = egc.lastvram._b[2][EGCADDR_H];
			egc.inptr[12] = egc.lastvram._b[3][EGCADDR_L];
			egc.inptr[13] = egc.lastvram._b[3][EGCADDR_H];
			shiftinput_incw();
		}
		else {
			egc.inptr[-1] = egc.lastvram._b[0][EGCADDR_L];
''',
         '''	egc.lastvram.w[2] = *(UINT16 *)(&mem[ad + VRAM_G]);
	egc.lastvram.w[3] = *(UINT16 *)(&mem[ad + VRAM_E]);

	if (kairo98_egc_snap_pending && !(egc.ope & 0x400) && !(egc.sft & 0x1000) &&
	    (egc.stack != 0 || egc.srcbit != 0 || egc.inptr != egc.outptr ||
	     !((egc.func == 0 && egc.dstbit == 0 && egc.remain >= 16) ||
	       (egc.func == 2 && egc.dstbit >= 8)))) {
		kairo98_egc_snap_pending = 0;
		kairo98_egc_snap[0] = egc.sft;
		kairo98_egc_snap[1] = egc.leng;
		kairo98_egc_snap[2] = egc.ope;
		kairo98_egc_snap[3] = (unsigned int)egc.func;
		kairo98_egc_snap[4] = egc.stack;
		kairo98_egc_snap[5] = egc.srcbit;
		kairo98_egc_snap[6] = egc.dstbit;
		kairo98_egc_snap[7] = egc.remain;
		kairo98_egc_snap[8] = (unsigned int)(egc.inptr - egc.outptr);
		kairo98_egc_snap[9] = egc.fgbg;
	}
	if (egc.ope & 0x400) {
		kairo98_egc_cpu_reads++;
	}
	else if (egc.sft & 0x1000) {
		kairo98_egc_dec_reads++;
	}
	// shift input
	if (!(egc.ope & 0x400)) {
		if (!(egc.sft & 0x1000)) {
#if defined(KAIRO98_ANDROID_FETCH_FAST)
			if (!kairo98_egc_read_inc_fast()) {
				kairo98_egc_read_inc_slow();
			}
#else
			kairo98_egc_read_inc_slow();
#endif
		}
		else {
			egc.inptr[-1] = egc.lastvram._b[0][EGCADDR_L];
'''),
        ('np21w/mem/memegc.c',
         '''const EGCQUAD	*data;

	__ASSERT(!(addr & 1));
	addr = LOW15(addr);
	if (!gdcs.access) {
		gdcs.grphdisp |= 1;
''',
         '''const EGCQUAD	*data;

	__ASSERT(!(addr & 1));
	kairo98_egc_writes++;
	addr = LOW15(addr);
	if (!gdcs.access) {
		gdcs.grphdisp |= 1;
'''),
    ],
    'np21w/mem/memvga.c': [
        ('np21w/mem/memvga.c',
         '''#include	"iocore.h"
#include	"memvga.h"
#include	"vram.h"
#if defined(SUPPORT_IA32_HAXM)
#include	"i386hax/haxfunc.h"
#include	"i386hax/haxcore.h"
''',
         '''#include	"iocore.h"
#include	"memvga.h"
#include	"vram.h"
#include	"pegc.h"
#if defined(SUPPORT_IA32_HAXM)
#include	"i386hax/haxfunc.h"
#include	"i386hax/haxcore.h"
'''),
    ],
    'np21w/pccore.c': [
        ('np21w/pccore.c',
         '''		}
	}
	if (pcstat.screenupdate) {
		pcstat.screenupdate = scrndraw_draw((UINT8)(pcstat.screenupdate & 2));
		drawcount++;
	}
}
''',
         '''		}
	}
	if (pcstat.screenupdate) {
		unsigned long long __t2 = kairo98_now_ns();
		pcstat.screenupdate = scrndraw_draw((UINT8)(pcstat.screenupdate & 2));
		kairo98_time_draw_ns += kairo98_now_ns() - __t2;
		kairo98_counter_draws++;
		drawcount++;
	}
}
'''),
        ('np21w/pccore.c',
         '''//	nevent_get1stevent();
	
	while(pcstat.screendispflag) {
		//lastclock = CPU_REMCLOCK;
#if defined(TRACE)
		resetcnt++;
''',
         '''//	nevent_get1stevent();
	
	while(pcstat.screendispflag) {
		kairo98_counter_slices++;
		//lastclock = CPU_REMCLOCK;
#if defined(TRACE)
		resetcnt++;
'''),
        ('np21w/pccore.c',
         '''		{
#if !defined(SINGLESTEPONLY)
			if (CPU_REMCLOCK > 0) {
				if (!(CPU_TYPE & CPUTYPE_V30)) {
					CPU_EXEC();
				}
				else {
					CPU_EXECV30();
				}
			}
#else
			while(CPU_REMCLOCK > 0) {
''',
         '''		{
#if !defined(SINGLESTEPONLY)
			if (CPU_REMCLOCK > 0) {
				unsigned long long __t0 = kairo98_now_ns();
				if (!(CPU_TYPE & CPUTYPE_V30)) {
					CPU_EXEC();
				}
				else {
					CPU_EXECV30();
				}
				kairo98_time_cpu_ns += kairo98_now_ns() - __t0;
			}
#else
			while(CPU_REMCLOCK > 0) {
'''),
        ('np21w/pccore.c',
         '''#if defined(SUPPORT_IDEIO)
		atapi_dataread_asyncwait(0);
#endif
		nevent_progress();
#if defined(SUPPORT_ASYNC_CPU)
		pccore_asynccpu();
#endif
''',
         '''#if defined(SUPPORT_IDEIO)
		atapi_dataread_asyncwait(0);
#endif
		{
			unsigned long long __t1 = kairo98_now_ns();
			nevent_progress();
			kairo98_time_event_ns += kairo98_now_ns() - __t1;
		}
#if defined(SUPPORT_ASYNC_CPU)
		pccore_asynccpu();
#endif
'''),
    ],
    'np21w/sdl2/dosio.c': [
        ('np21w/sdl2/dosio.c',
         '''#endif
#else
#include <dirent.h>
#include <unistd.h>
#include <sys/time.h>
#endif
''',
         '''#endif
#else
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/time.h>
#endif
'''),
        ('np21w/sdl2/dosio.c',
         '''}

short file_sync(FILEH handle) {
	if (fflush(handle) != 0)
		return -1;
#if defined(WIN32)
''',
         '''}

short file_sync(FILEH handle) {
#if defined(__ANDROID__)
	int flags = fcntl(fileno(handle), F_GETFL);
	if (flags < 0)
		return -1;
	if ((flags & O_ACCMODE) == O_RDONLY)
		return 0;
#endif
	if (fflush(handle) != 0)
		return -1;
#if defined(WIN32)
'''),
        ('np21w/sdl2/dosio.c',
         '''	tv[0].tv_usec = 0;
	tv[1].tv_sec = mtime;
	tv[1].tv_usec = 0;
	return (futimes(fileno(handle), tv) == 0) ? 0 : -1;
#endif
}

''',
         '''	tv[0].tv_usec = 0;
	tv[1].tv_sec = mtime;
	tv[1].tv_usec = 0;
	#if defined(__ANDROID__)
    struct timespec times[2] = {{tv[0].tv_sec, 0}, {tv[1].tv_sec, 0}};
    return (futimens(fileno(handle), times) == 0) ? 0 : -1;
#else
    return (futimes(fileno(handle), tv) == 0) ? 0 : -1;
#endif
#endif
}

'''),
    ],
    'np21w/sound/adpcmg.c': [
        ('np21w/sound/adpcmg.c',
         '''
#include "compiler.h"
#include "adpcm.h"

#define	ADPCM_NBR	0x80000000

''',
         '''
#include "compiler.h"
#include "adpcm.h"
#if defined(SUPPORT_YMFM)
#include "ymfm_bridge.h"
#endif

#define	ADPCM_NBR	0x80000000

'''),
        ('np21w/sound/adpcmg.c',
         '''	pos = ad->pos & 0x1fffff;
	if (!(ad->reg.ctrl2 & 2)) {
		ad->buf[pos >> 3] = data;
		pos += 8;
	}
	else {
''',
         '''	pos = ad->pos & 0x1fffff;
	if (!(ad->reg.ctrl2 & 2)) {
		ad->buf[pos >> 3] = data;
#if defined(SUPPORT_YMFM)
		kairo_ymfm_ram_changed(ad->buf, pos >> 3, ad->buf[pos >> 3]);
#endif
		pos += 8;
	}
	else {
'''),
        ('np21w/sound/adpcmg.c',
         '''		if (data & 0x80) {
			ptr[0x38000] |= bit;
		}
		pos++;
	}
	if (pos == ad->stop) {
''',
         '''		if (data & 0x80) {
			ptr[0x38000] |= bit;
		}
#if defined(SUPPORT_YMFM)
		{
			UINT32 base = (pos >> 3) & 0x7fff;
			UINT plane;
			for (plane = 0; plane < 8; plane++) {
				kairo_ymfm_ram_changed(ad->buf, base + plane * 0x8000, ptr[plane * 0x8000]);
			}
		}
#endif
		pos++;
	}
	if (pos == ad->stop) {
'''),
    ],
    'np21w/sound/fmboard.c': [
        ('np21w/sound/fmboard.c',
         '''#include	"soundrom.h"
#include	"keydisp.h"
#include	"keystat.h"


	SOUNDID g_nSoundID;
''',
         '''#include	"soundrom.h"
#include	"keydisp.h"
#include	"keystat.h"
#if defined(SUPPORT_YMFM)
#include "ymfm_bridge.h"
#endif


	SOUNDID g_nSoundID;
'''),
        ('np21w/sound/fmboard.c',
         '''	for (i = 0; i < _countof(g_opna); i++)
	{
		rhythm_update(&g_opna[i].rhythm);
	}

	oplgen_setvol(np2cfg.vol_fm * np2cfg.vol_master / 100);
''',
         '''	for (i = 0; i < _countof(g_opna); i++)
	{
		rhythm_update(&g_opna[i].rhythm);
#if defined(SUPPORT_YMFM)
		kairo_ymfm_set_volume((void *)g_opna[i].userdata,
			np2cfg.vol_fm * volex / 15 * np2cfg.vol_master / 100,
			np2cfg.vol_ssg * volex / 15 * np2cfg.vol_master / 100);
#endif
	}

	oplgen_setvol(np2cfg.vol_fm * np2cfg.vol_master / 100);
'''),
    ],
    'np21w/sound/fmboard.h': [
        ('np21w/sound/fmboard.h',
         '''#endif
extern	_PCM86		g_pcm86;
extern	_CS4231		cs4231;
extern	SB16		g_sb16;

REG8 fmboard_getjoy(POPNA opna);

''',
         '''#endif
extern	_PCM86		g_pcm86;
extern	_CS4231		cs4231;
#if defined(SUPPORT_SOUND_SB16)
extern	SB16		g_sb16;
#endif

REG8 fmboard_getjoy(POPNA opna);

'''),
    ],
    'np21w/sound/opna.c': [
        ('np21w/sound/opna.c',
         '''#include "sound.h"
#include "s98.h"
#include "generic/keydisp.h"

static void writeRegister(POPNA opna, UINT nAddress, REG8 cData);
static void writeExtendedRegister(POPNA opna, UINT nAddress, REG8 cData);
''',
         '''#include "sound.h"
#include "s98.h"
#include "generic/keydisp.h"
#if defined(SUPPORT_YMFM)
#include "ymfm_bridge.h"

static void SOUNDCALL ymfm_getpcm(void *hdl, SINT32 *pcm, UINT count)
{
	unsigned long long t0 = kairo98_now_ns();
	kairo_ymfm_mix(hdl, pcm, count);
	kairo98_time_fm_ns += kairo98_now_ns() - t0;
}
#endif

static void writeRegister(POPNA opna, UINT nAddress, REG8 cData);
static void writeExtendedRegister(POPNA opna, UINT nAddress, REG8 cData);
'''),
        ('np21w/sound/opna.c',
         '''void opna_construct(POPNA opna)
{
	memset(opna, 0, sizeof(*opna));
}

/**
''',
         '''void opna_construct(POPNA opna)
{
	memset(opna, 0, sizeof(*opna));
#if defined(SUPPORT_YMFM)
	opna->userdata = (INTPTR)kairo_ymfm_create();
#endif
}

/**
'''),
        ('np21w/sound/opna.c',
         ''' */
void opna_destruct(POPNA opna)
{
}

/**
''',
         ''' */
void opna_destruct(POPNA opna)
{
#if defined(SUPPORT_YMFM)
	kairo_ymfm_destroy((void *)opna->userdata);
	opna->userdata = 0;
#endif
}

/**
'''),
        ('np21w/sound/opna.c',
         '''	psggen_reset(&opna->psg);
	rhythm_reset(&opna->rhythm);
	adpcm_reset(&opna->adpcm);
}

/**
''',
         '''	psggen_reset(&opna->psg);
	rhythm_reset(&opna->rhythm);
	adpcm_reset(&opna->adpcm);
#if defined(SUPPORT_YMFM)
	if (cCaps & OPNA_HAS_FM)
	{
		OEMCHAR rhythm_rom_path[MAX_PATH];
		getbiospath(rhythm_rom_path, OEMTEXT("ym2608_adpcm_rom.bin"),
			NELEMENTS(rhythm_rom_path));
		kairo_ymfm_reset((void *)opna->userdata,
			(cCaps & OPNA_HAS_EXTENDEDFM) != 0, soundcfg.rate,
			opna->adpcm.buf, sizeof(opna->adpcm.buf), rhythm_rom_path);
		kairo_ymfm_set_volume((void *)opna->userdata,
			np2cfg.vol_fm * np2cfg.vol_master / 100,
			np2cfg.vol_ssg * np2cfg.vol_master / 100);
	}
#endif
}

/**
'''),
        ('np21w/sound/opna.c',
         '''
	if (cCaps & OPNA_HAS_PSG)
	{
		sound_streamregist(&opna->psg, (SOUNDCB)psggen_getpcm);
	}
	if (cCaps & OPNA_HAS_VR)
	{
		sound_streamregist(&opna->opngen, (SOUNDCB)opngen_getpcmvr);
''',
         '''
	if (cCaps & OPNA_HAS_PSG)
	{
#if !defined(SUPPORT_YMFM)
		sound_streamregist(&opna->psg, (SOUNDCB)psggen_getpcm);
#endif
	}
#if defined(SUPPORT_YMFM)
	if (cCaps & OPNA_HAS_FM)
	{
		sound_streamregist((void *)opna->userdata, ymfm_getpcm);
	}
#else
	if (cCaps & OPNA_HAS_VR)
	{
		sound_streamregist(&opna->opngen, (SOUNDCB)opngen_getpcmvr);
'''),
        ('np21w/sound/opna.c',
         '''	{
		sound_streamregist(&opna->opngen, (SOUNDCB)opngen_getpcm);
	}
	if (cCaps & OPNA_HAS_RHYTHM)
	{
		rhythm_bind(&opna->rhythm);
	}
	if (cCaps & OPNA_HAS_ADPCM)
	{
		sound_streamregist(&opna->adpcm, (SOUNDCB)adpcm_getpcm);
	}
}

''',
         '''	{
		sound_streamregist(&opna->opngen, (SOUNDCB)opngen_getpcm);
	}
#endif
	if ((cCaps & OPNA_HAS_RHYTHM)
#if defined(SUPPORT_YMFM)
		&& !kairo_ymfm_has_rhythm_rom((void *)opna->userdata)
#endif
	)
	{
		rhythm_bind(&opna->rhythm);
	}
	if (cCaps & OPNA_HAS_ADPCM)
	{
#if !defined(SUPPORT_YMFM)
		sound_streamregist(&opna->adpcm, (SOUNDCB)adpcm_getpcm);
#endif
	}
}

'''),
        ('np21w/sound/opna.c',
         '''	const UINT8 cCaps = opna->s.cCaps;
	REG8 cChannel;

	if (nAddress < 0x10)
	{
		if (cCaps & OPNA_HAS_PSG)
''',
         '''	const UINT8 cCaps = opna->s.cCaps;
	REG8 cChannel;

#if defined(SUPPORT_YMFM)
	if (cCaps & OPNA_HAS_FM)
	{
		sound_sync();
		kairo_ymfm_write((void *)opna->userdata, 0, nAddress, cData);
	}
#endif

	if (nAddress < 0x10)
	{
		if (cCaps & OPNA_HAS_PSG)
'''),
        ('np21w/sound/opna.c',
         '''{
	const UINT8 cCaps = opna->s.cCaps;

	if (nAddress < 0x12)
	{
		if (cCaps & OPNA_HAS_ADPCM)
''',
         '''{
	const UINT8 cCaps = opna->s.cCaps;

#if defined(SUPPORT_YMFM)
	if (cCaps & OPNA_HAS_EXTENDEDFM)
	{
		sound_sync();
		kairo_ymfm_write((void *)opna->userdata, 1, nAddress, cData);
	}
#endif

	if (nAddress < 0x12)
	{
		if (cCaps & OPNA_HAS_ADPCM)
'''),
    ],
    'np21w/sound/sound.c': [
        ('np21w/sound/sound.c',
         '''#include "sndcsec.h"
#include "beep.h"
#include "soundmng.h"
#if defined(SUPPORT_WAVEREC)
#include "common/wavefile.h"
#endif	/* defined(SUPPORT_WAVEREC) */
''',
         '''#include "sndcsec.h"
#include "beep.h"
#include "soundmng.h"
#if defined(SUPPORT_YMFM)
#include "ymfm_bridge.h"
#endif
#if defined(SUPPORT_WAVEREC)
#include "common/wavefile.h"
#endif	/* defined(SUPPORT_WAVEREC) */
'''),
        ('np21w/sound/sound.c',
         '''
void sound_destroy(void) {

	if (sndstream.buffer) {
#if defined(SUPPORT_WAVEREC)
		sound_recstop();
''',
         '''
void sound_destroy(void) {

#if defined(SUPPORT_YMFM)
	kairo_ymfm_drain_all();
#endif
	if (sndstream.buffer) {
#if defined(SUPPORT_WAVEREC)
		sound_recstop();
'''),
        ('np21w/sound/sound.c',
         '''
void sound_reset(void) {

	if (sndstream.buffer) {
		soundmng_reset();
		streamreset();
''',
         '''
void sound_reset(void) {

#if defined(SUPPORT_YMFM)
	kairo_ymfm_drain_all();
#endif
	if (sndstream.buffer) {
		soundmng_reset();
		streamreset();
'''),
    ],
    'np21w/statsave.c': [
        ('np21w/statsave.c',
         '''
#include "compiler.h"
#include "statsave.h"
#include "strres.h"
#include "dosio.h"
#include "commng.h"
''',
         '''
#include "compiler.h"
#include "statsave.h"
#include "kairo/state_paths.h"
#include "strres.h"
#include "dosio.h"
#include "commng.h"
'''),
        ('np21w/statsave.c',
         '''	for (i=0; i<4; i++) {
		ret |= statflag_read(sfh, &sp, sizeof(sp));
		if (sp.path[0]) {
			fdd_set(i, sp.path, sp.ftype, sp.readonly);
		}
	}
	(void)tbl;
''',
         '''	for (i=0; i<4; i++) {
		ret |= statflag_read(sfh, &sp, sizeof(sp));
		if (sp.path[0]) {
			fdd_set(i, kairo_state_path(sp.path), sp.ftype, sp.readonly);
		}
	}
	(void)tbl;
'''),
        ('np21w/statsave.c',
         '''		sxsi_setdevtype(drv, sds.ide[i]);
		if (sds.ide[i] != SXSIDEV_NC) {
			ret |= statflag_read(sfh, &sp, sizeof(sp));
			sxsi_devopen(drv, sp.path);
		}
	}
	for (i=0; i<NELEMENTS(sds.scsi); i++) {
''',
         '''		sxsi_setdevtype(drv, sds.ide[i]);
		if (sds.ide[i] != SXSIDEV_NC) {
			ret |= statflag_read(sfh, &sp, sizeof(sp));
			sxsi_devopen(drv, kairo_state_path(sp.path));
		}
	}
	for (i=0; i<NELEMENTS(sds.scsi); i++) {
'''),
        ('np21w/statsave.c',
         '''		sxsi_setdevtype(drv, sds.scsi[i]);
		if (sds.scsi[i] != SXSIDEV_NC) {
			ret |= statflag_read(sfh, &sp, sizeof(sp));
			sxsi_devopen(drv, sp.path);
		}
	}
	(void)tbl;
''',
         '''		sxsi_setdevtype(drv, sds.scsi[i]);
		if (sds.scsi[i] != SXSIDEV_NC) {
			ret |= statflag_read(sfh, &sp, sizeof(sp));
			sxsi_devopen(drv, kairo_state_path(sp.path));
		}
	}
	(void)tbl;
'''),
    ],
    'np21w/vram/scrndraw.c': [
        ('np21w/vram/scrndraw.c',
         '''#include	"sdraw.h"
#include	"dispsync.h"
#include	"palettes.h"
#ifdef SUPPORT_WAB
#include	"wab/wab.h"
#endif
''',
         '#include\t"sdraw.h"\n#include\t"dispsync.h"\n#include\t"palettes.h"\n\n#if defined(KAIRO98_ANDROID_FETCH_FAST)\n/* Android GPU screen path, see android_host/gpudraw.h. */\nint kairo98_gpudraw_begin(const SCRNSURF *surf, int variant, SDRAW sdraw);\nvoid kairo98_gpudraw_range(SDRAW sdraw, int maxy);\nstatic int kairo98_gpu_frame = 0;\n#if defined(KAIRO98_GPU_VERIFY)\n/* Verification: record for the GPU and also draw on the CPU so the two can be compared. */\n#define\tKAIRO98_SDRAW(fn, sd, maxy) \\\n\tdo { \\\n\t\tif (kairo98_gpu_frame) { \\\n\t\t\t_SDRAW __copy = *(sd); \\\n\t\t\tkairo98_gpudraw_range(&__copy, (maxy)); \\\n\t\t} \\\n\t\t(*(fn))((sd), (maxy)); \\\n\t} while (0)\n#else\n#define\tKAIRO98_SDRAW(fn, sd, maxy) \\\n\tdo { \\\n\t\tif (kairo98_gpu_frame) { \\\n\t\t\tkairo98_gpudraw_range((sd), (maxy)); \\\n\t\t} \\\n\t\telse { \\\n\t\t\t(*(fn))((sd), (maxy)); \\\n\t\t} \\\n\t} while (0)\n#endif\n#else\n#define\tKAIRO98_SDRAW(fn, sd, maxy)\t(*(fn))((sd), (maxy))\n#endif\n#ifdef SUPPORT_WAB\n#include\t"wab/wab.h"\n#endif\n'),
        ('np21w/vram/scrndraw.c',
         '''				np2_pal16[0] = np2_pal16[NP2PAL_SKIP];
#endif
			}
			(*sdrawfn)(sdraw, y);
			nextupdate = y;
			// お弁当を食べる
			while(clk < event->clock) {
''',
         '''				np2_pal16[0] = np2_pal16[NP2PAL_SKIP];
#endif
			}
			KAIRO98_SDRAW(sdrawfn, sdraw, y);
			nextupdate = y;
			// お弁当を食べる
			while(clk < event->clock) {
'''),
        ('np21w/vram/scrndraw.c',
         '''			np2_pal16[0] = np2_pal16[NP2PAL_SKIP];
#endif
		}
		(*sdrawfn)(sdraw, maxy);
	}
	if (palevent.vsyncpal) {
		return(2);
''',
         '''			np2_pal16[0] = np2_pal16[NP2PAL_SKIP];
#endif
		}
		KAIRO98_SDRAW(sdrawfn, sdraw, maxy);
	}
	if (palevent.vsyncpal) {
		return(2);
'''),
        ('np21w/vram/scrndraw.c',
         '''	sdraw.y = 0;
	sdraw.xalign = surf->xalign;
	sdraw.yalign = surf->yalign;
	if (((gdc.analog & 3) != 1) || (palevent.events >= PALEVENTMAX)) {
		(*(*sdrawfn))(&sdraw, height);
	}
	else {
		ret = rasterdraw(*sdrawfn, &sdraw, height);
''',
         '''	sdraw.y = 0;
	sdraw.xalign = surf->xalign;
	sdraw.yalign = surf->yalign;
#if defined(KAIRO98_ANDROID_FETCH_FAST)
	{
		int variant = -1;
#if defined(SUPPORT_PC9821)
		if (!(gdc.analog & 2))
#endif
		{
			variant = (int)(sdrawfn - sdraw_getproctbl(surf));
		}
		kairo98_gpu_frame = kairo98_gpudraw_begin(surf, variant, &sdraw);
	}
#endif
	if (((gdc.analog & 3) != 1) || (palevent.events >= PALEVENTMAX)) {
		KAIRO98_SDRAW(*sdrawfn, &sdraw, height);
	}
	else {
		ret = rasterdraw(*sdrawfn, &sdraw, height);
'''),
    ],
}
