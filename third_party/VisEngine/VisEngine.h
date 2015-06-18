#ifdef __cplusplus
extern "C" {
#endif

extern int sagevis_init(int fps);
extern int sagevis_deinit();
extern int sagevis_loadpreset(const char *name);
extern int sagevis_update(short *pcmdata, int *decay, int *coord,
    int *wavepoints, int *waveflag, int *wavecolor);
#ifdef __cplusplus
}
#endif
