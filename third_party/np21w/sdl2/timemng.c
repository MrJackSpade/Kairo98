#include	"compiler.h"
#include	<time.h>
#include	"timemng.h"


/* Kairo98#136: when 0 or more, the time (seconds since 1970, UTC) a run that must repeat exactly
 * reads instead of the host's; set before the machine resets. */
long long kairo98_fixed_time = -1;

BRESULT timemng_gettime(_SYSTIME *systime) {

	time_t	long_time;
struct tm	*now_time;

	if (kairo98_fixed_time >= 0) {
		long_time = (time_t)kairo98_fixed_time;
		now_time = gmtime(&long_time);
	}
	else {
		time(&long_time);
		now_time = localtime(&long_time);
	}
	if (now_time != NULL) {
		systime->year = now_time->tm_year + 1900;
		systime->month = now_time->tm_mon + 1;
		systime->week = now_time->tm_wday;
		systime->day = now_time->tm_mday;
		systime->hour = now_time->tm_hour;
		systime->minute = now_time->tm_min;
		systime->second = now_time->tm_sec;
		systime->milli = 0;
		return(SUCCESS);
	}
	else {
		return(FAILURE);
	}
}

