/**
 *	@file	sysmng.h
 *	@brief	Interface of the system
 */

#pragma once

// どーでもいい通知系

enum {
	SYS_UPDATECFG		= 0x0001,
	SYS_UPDATEOSCFG		= 0x0002,
	SYS_UPDATECLOCK		= 0x0004,
	SYS_UPDATERATE		= 0x0008,
	SYS_UPDATESBUF		= 0x0010,
	SYS_UPDATEMIDI		= 0x0020,
	SYS_UPDATESBOARD	= 0x0040,
	SYS_UPDATEFDD		= 0x0080,
	SYS_UPDATEHDD		= 0x0100,
	SYS_UPDATEMEMORY	= 0x0200,
	SYS_UPDATESERIAL1	= 0x0400
};

void sysmng_initialize(void);
void sysmng_deinitialize(void);

#ifdef __cplusplus
extern "C" {
#endif

void sysmng_update(UINT update);
void sysmng_cpureset(void);
enum { SYS_UPDATECAPTION_FDD = 0x01 };
void sysmng_requestupdatecaption(UINT8 flag);
void sysmng_updatecaption(UINT8 flag);
/* The guest read or wrote its media: a floppy access, or a hard disk or CD data read or write
 * (patch groups np21w/fdd/sxsi.c, np21w/cbus/atapicmd.c). CD audio does not count. */
void kairo98_media_activity(void);
#define	sysmng_fddaccess(a)	kairo98_media_activity()
#define	sysmng_hddaccess(a)

#ifdef __cplusplus
}
#endif
