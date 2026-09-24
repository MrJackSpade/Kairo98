/**
 * @file	compiler.h
 * @brief	include file for standard system include files,
 *			or project specific include files that are used frequently,
 *			but are changed infrequently
 */

#pragma once

#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include <setjmp.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define TEXT(s) s
#define msgbox(title, message) ((void)0)
#define VK_PAUSE 0
#define GetKeyState(key) 0
typedef uint32_t DWORD;
typedef uint16_t WORD;
typedef uint8_t BYTE;
typedef int32_t INT32;
typedef intptr_t INT_PTR;
#define CPU_MULTIPLE_MAX 2048

static inline uint32_t kairo98_ticks(void) {
	struct timespec now;
	clock_gettime(CLOCK_MONOTONIC, &now);
	return (uint32_t)(now.tv_sec * 1000u + now.tv_nsec / 1000000u);
}

#define	BYTESEX_LITTLE
#define	OSLANG_UTF8
#define	OSLINEBREAK_LF
#define RESOURCE_US

typedef	signed int			SINT;
typedef	unsigned int		UINT;
typedef	signed char			SINT8;
typedef	unsigned char		UINT8;
typedef	signed short		SINT16;
typedef	unsigned short		UINT16;
typedef	signed int			SINT32;
typedef	unsigned int		UINT32;
typedef	signed long long	SINT64;
typedef	unsigned long long	UINT64;

#define	BRESULT				UINT

#if defined(SUPPORT_LARGE_HDD)
typedef	SINT64				FILEPOS;
typedef	SINT64				FILELEN;
#define	NHD_MAXSIZE		8000
#define	NHD_MAXSIZE2	32000
#else
typedef	SINT32				FILEPOS;
typedef	SINT32				FILELEN;
#define	NHD_MAXSIZE		2000
#define	NHD_MAXSIZE2	2000
#endif
#define	OEMCHAR				char
#define	OEMTEXT(string)		string
#define	OEMSPRINTF			sprintf
#define	OEMSTRLEN			strlen

#define SIZE_VGA
#if !defined(SIZE_VGA)
#define	RGB16		UINT32
#define	SIZE_QVGA
#endif

#if !defined(OBJC_BOOL_DEFINED)
typedef signed char BOOL;
#endif

#ifndef	TRUE
#define	TRUE	1
#endif

#ifndef	FALSE
#define	FALSE	0
#endif

#ifndef	MAX_PATH
#define	MAX_PATH	256
#endif

#ifndef _countof
#define _countof(a) (sizeof(a) / sizeof((a)[0]))
#endif

#ifndef	max
#define	max(a,b)	(((a) > (b)) ? (a) : (b))
#endif
#ifndef	min
#define	min(a,b)	(((a) < (b)) ? (a) : (b))
#endif

#ifndef	ZeroMemory
#define	ZeroMemory(d,n)		memset((d), 0, (n))
#endif
#ifndef	CopyMemory
#define	CopyMemory(d,s,n)	memcpy((d), (s), (n))
#endif
#ifndef	FillMemory
#define	FillMemory(a, b, c)	memset((a), (c), (b))
#endif

#include "common.h"
#include "milstr.h"
#include "_memory.h"
#include "rect.h"
#include "lstarray.h"
#include "trace.h"


#define	GETTICK()			kairo98_ticks()
#define	__ASSERT(s)
#define	SPRINTF				sprintf
#define	STRLEN				strlen

#define	SUPPORT_UTF8

#define	SUPPORT_16BPP
#define	MEMOPTIMIZE		2

#define	SOUNDRESERVE	100

#define	SUPPORT_SASI

#define	SCREEN_BPP		16
#define _tcslen strlen
#define MEMORY_MAXSIZE 230
#define INFINITE UINT32_MAX
#define _tcscpy strcpy

/* The portable IA-32 core uses these call and register aliases on ARM64. */
#define REG8 UINT8
#define REG16 UINT16
#define CPUCALL
#define MEMCALL
#define DMACCALL
#define IOOUTCALL
#define IOINPCALL
#define SOUNDCALL
#define VRAMCALL
#define SCRNCALL
#define VERMOUTHCL
#define INLINE inline
