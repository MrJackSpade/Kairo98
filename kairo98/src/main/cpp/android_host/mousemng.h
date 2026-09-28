
#ifdef __cplusplus
extern "C" {
#endif

/* Host cursor state that the core's state save carries. Android has no host cursor. */
typedef struct {
    UINT8 unused;
} MOUSEMNGSTAT;
extern MOUSEMNGSTAT mousemngstat;

UINT8 mousemng_getstat(SINT16 *x, SINT16 *y, int clear);
void mousemng_reset(void);
void mousemng_updateautohidecursor(void);

#ifdef __cplusplus
}
#endif
