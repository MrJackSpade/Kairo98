/*
 * Kairo98: a CD kept as a CHD (Kairo98#127), read through the shared CHD reader (kairo_chd.h) as
 * the image it was made from. Its tracks are laid out as opencue lays out a cue sheet's, and the
 * image is read through file_open_rb (sdl2/dosio.c), which opens a CHD as that image.
 */
#include	"compiler.h"
#include	"dosio.h"
#include	"fdd/sxsi.h"

#ifdef SUPPORT_KAI_IMAGES

#include	"diskimage/cddfile.h"
#include	"diskimage/cd/kairo98_chd.h"
#include	"kairo_chd.h"

#if defined(__ANDROID__)
#include	<android/log.h>
#endif

BRESULT openchd(SXSIDEV sxsi, const OEMCHAR *fname) {

	_CDTRK		trk[99];
	char		error[256];
	kairo_chd	*chd;
	int			count;
	int			index;
	UINT32		pregapoffset = 0;

	chd = kairo_chd_open(fname, error, sizeof(error));
	if (chd == NULL) {
#if defined(__ANDROID__)
		__android_log_print(ANDROID_LOG_ERROR, "Kairo98", "Cannot read %s: %s", fname, error);
#endif
		return(FAILURE);
	}
	count = kairo_chd_track_count(chd);
	if (count > (int)NELEMENTS(trk)) {
		kairo_chd_close(chd);
		return(FAILURE);
	}
	ZeroMemory(trk, sizeof(trk));
	for (index = 0; index < count; index++) {
		const kairo_chd_track *track = kairo_chd_track_at(chd, index);
		// A pregap kept in the image is the cue sheet's INDEX 00; one that is not is its PREGAP.
		UINT32 curpregap = track->pregap_stored ? 0 : track->pregap;
		UINT32 index0pos = track->first_sector;
		UINT32 index1pos = track->first_sector + (track->pregap_stored ? track->pregap : 0);

		if (index != 0) {
			pregapoffset += curpregap;
		}
		trk[index].adr_ctl			= track->audio ? TRACKTYPE_AUDIO : TRACKTYPE_DATA;
		trk[index].point			= (UINT8)track->number;
		trk[index].pos				= pregapoffset + index1pos;
		trk[index].pos0				= pregapoffset + index0pos;
		trk[index].sector_size		= (UINT16)track->sector_size;
		trk[index].pregap_sectors	= curpregap + (index1pos - index0pos);
		trk[index].img_pregap_sec	= index0pos;
		trk[index].img_start_sec	= index1pos;
		trk[index].pregap_offset_ex = pregapoffset;
	}
	kairo_chd_close(chd);

	set_secread(sxsi, trk, (UINT)count);
	sxsi->totals = -1;
	return(setsxsidev(sxsi, fname, trk, (UINT)count));
}

#endif
