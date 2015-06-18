#include <stdio.h>
#include "VisEngine.h"

int main(int argc, char **argv)
{
    int i;
    int decay;
    int coords[17*17*2];
    short pcmdata[1024];
    sagevis_init(100);
    sagevis_loadpreset("test.milk");
    fprintf(stderr, "loaded, starting 200 frames test\n");

    for(i=0;i<200;i++)
    {
        sagevis_update(pcmdata, &decay, &coords[0]);
    }
    fprintf(stderr, "done test, doing deinit\n");
    sagevis_deinit();
    fprintf(stderr, "finished test\n");
    return 0;
}
