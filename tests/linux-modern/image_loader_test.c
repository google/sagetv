#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "imageload.h"

typedef RawImage_t *(*load_png_fn)(FILE *, int, int);

int main(int argc, char **argv)
{
    if (argc < 4) return 2;
    if (!dlopen("libpng16.so.16", RTLD_NOW | RTLD_GLOBAL)) return 3;
    void *image = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!image) { fprintf(stderr, "%s\n", dlerror()); return 4; }
    load_png_fn load_png = (load_png_fn)dlsym(image, "LoadPNG");
    if (!load_png) return 5;
    for (int i = 2; i < argc; i++) {
        FILE *fp = fopen(argv[i], "rb");
        if (!fp) return 6;
        RawImage_t *decoded = load_png(fp, 0, 0);
        fclose(fp);
        int malformed = strstr(argv[i], "malformed") != NULL;
        if (malformed ? decoded != NULL : decoded == NULL) return 10 + i;
        if (decoded) { free(decoded->pPlane); free(decoded); }
    }
    return 0;
}
