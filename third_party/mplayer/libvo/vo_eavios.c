#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>


#include "config.h"
#include "mp_msg.h"
#include "video_out.h"
#include "video_out_internal.h"

#include "fastmemcpy.h"
#include "sub.h"
#include "aspect.h"

#ifdef HAVE_NEW_GUI
#include "../Gui/interface.h"
#endif

static vo_info_t info = {
    "eavios",
    "eavios",
    "Jean-Francois Thibert",
    ""
};

LIBVO_EXTERN(eavios)

#include <sys/ipc.h>
#include <sys/shm.h>

//#include "/frey/eavios/eavios.h"

#define DLOG(...) 0
#define EAVIOS_FILE "/tmp/eavios"
#define AV_EVENT_QUEUE_SIZE             32
#define AV_CONNECTION_BUFFER_SIZE       1024

typedef struct 
{
    void *dlHandle;    
    int (*eaviosGetPluginInfo)();
    int (*eaviosStartPlugin)();
    int (*eaviosUpdatePlugin)();
    int (*eaviosUpdatePluginFD)();
    int (*eaviosStopPlugin)(); 
    int (*eaviosGetVideoInfo)(int *shmid, int *shmlen, int *offsetY, int *pitchY, 
                              int *offsetU, int *pitchU, int *offsetV, int *pitchV);
    int (*eaviosUpdateVideo)(int width, int height);
    int (*eaviosUpdateOSD)(int videox, int videoy, int videowidth, int videoheight);
    int (*eaviosGetOSDInfo)(int *shmid, int *shmlen, int *pitch);
    int (*eaviosGetKey)();
    unsigned int (*eaviosGetKeyState)();
    unsigned int (*eaviosGetKeyCharacter)();
    unsigned int (*eaviosInitOSD)();
    unsigned int (*eaviosProcessOSDCmd)();
    int (*eaviosStopVideo)();
}VOutputPlugin;

typedef struct
{
    int fd;
    int readCount;
    int writeCount;
    char readBuffer[AV_CONNECTION_BUFFER_SIZE];
    char writeBuffer[AV_CONNECTION_BUFFER_SIZE];
}AVClient;

typedef struct
{
    int event;
    void *data;
}AVEvent;

typedef struct 
{
    int fd; /* File Descriptor */
    VOutputPlugin *VOPlugin;
    /* Event Queue (maybe make that a new struct?) */    
    int head;
    int tail;
    AVEvent queue[AV_EVENT_QUEUE_SIZE];
    AVClient *clients;    
}AVContext;

AVContext AV1;

int eaviosConnectServer();
int eaviosDisconnectServer();
int eaviosSendEvent(AVContext *AVC, unsigned int cmd, void *data, int len);
int eaviosGetReply(AVContext *AVC, unsigned int *reply);


int eaviosConnectServer(AVContext *AVC)
{
    int fd = -1;
    int i;
    struct sockaddr_un sa;
    
    AVC->fd=-1;
    
    /* local unix socket */
    fd = socket(PF_UNIX, SOCK_STREAM, 0);
    
    if(fd < 0)
    {
        DLOG("Error creating socket\n");
        return(-1);
    }

    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    strcpy(sa.sun_path, EAVIOS_FILE);
    
    if(connect(fd, (struct sockaddr *)&sa, sizeof(sa)) != 0) 
    {
        DLOG("Error creating socket\n");
        close(fd);
        return -1;
    }

    AVC->fd=fd;
    return fd;
}

int eaviosDisconnectServer(AVContext *AVC)
{
    if(AVC->fd>=0)
    {
        close(AVC->fd);
    }
    return 0;
}

int eaviosSendEvent(AVContext *AVC, unsigned int cmd, void *data, int len)
{
    write(AVC->fd, &cmd, sizeof(cmd));
    write(AVC->fd, &len, sizeof(len));
    if(len)
    {
        write(AVC->fd, data, len);
    }
}

int eaviosGetReply(AVContext *AVC, unsigned int *reply)
{
    return read(AVC->fd, reply, 4);
}

// FIXME: dynamically allocate this stuff
static uint32_t image_width;
static uint32_t image_height;
static uint32_t image_format;
static int flip_flag;

static uint32_t shmid;
static uint32_t shmlen;
static uint32_t offsetY;
static uint32_t pitchY;
static uint32_t offsetU;
static uint32_t pitchU;
static uint32_t offsetV;
static uint32_t pitchV;

static unsigned char *imagedata;

static int int_pause;

/*
 * connect to server, create and map window,
 * allocate colors and (shared) memory
 */
static int config(uint32_t width, uint32_t height, uint32_t d_width,
                       uint32_t d_height, uint32_t flags, char *title,
                       uint32_t format)
{
    // Connect to EAVIOS server
    //return -1;
    int reply;
    eaviosConnectServer(&AV1);
    printf("Connected with fd %d\n",AV1.fd);
    if(AV1.fd<0) return -1;
    eaviosSendEvent(&AV1, 0, 0, 0);
    eaviosGetReply(&AV1, &shmid);
    eaviosGetReply(&AV1, &shmlen);
    eaviosGetReply(&AV1, &offsetY);
    eaviosGetReply(&AV1, &pitchY);
    eaviosGetReply(&AV1, &offsetU);
    eaviosGetReply(&AV1, &pitchU);
    eaviosGetReply(&AV1, &offsetV);
    eaviosGetReply(&AV1, &pitchV);
    imagedata = (unsigned char *) shmat(shmid, 0, 0);
    printf("config reply %08X %08X %08X %08X %08X %08X %08X %08X\n",shmid, shmlen, 
        offsetY, pitchY, offsetU, pitchU, offsetV, pitchV);
    return 0;
}

static void draw_osd(void)
{
//    printf("draw osd\n");
}

static void flip_page(void)
{
//    printf("flip page\n");
}

static void check_events(void)
{
//    printf("check events\n");
}

static int draw_slice(uint8_t * image[], int stride[], int w, int h,
                           int x, int y)
{
    uint8_t *dst;

    dst = imagedata+ offsetY+
        pitchY * y + x;
    memcpy_pic(dst, image[0], w, h, pitchY,
               stride[0]);

    x /= 2;
    y /= 2;
    w /= 2;
    h /= 2;

    dst = imagedata + offsetU +
        pitchU * y + x;
    memcpy_pic(dst, image[2], w, h, pitchU,
                stride[2]);

    dst = imagedata + offsetV +
        pitchV * y + x;
    memcpy_pic(dst, image[1], w, h, pitchV,
               stride[1]);

    return 0;
}

static int draw_frame(uint8_t * src[])
{
    printf("draw_frame() called!!!!!!");
    return -1;
}

static int draw_image(mp_image_t * mpi)
{
    int i;
    int data[2] = { mpi->w, mpi->h };
    eaviosSendEvent(&AV1, 1, data, 8);
    eaviosGetReply(&AV1, &i);
//    printf("draw_image\n");
    return VO_FALSE;            // not (yet) supported
}

static int get_image(mp_image_t * mpi)
{
//    printf("get_image\n");
    return VO_FALSE;
}

static int query_format(uint32_t format)
{
    uint32_t i;
    int flag = VFCAP_CSP_SUPPORTED | VFCAP_CSP_SUPPORTED_BY_HW | VFCAP_HWSCALE_UP | VFCAP_HWSCALE_DOWN | VFCAP_OSD | VFCAP_ACCEPT_STRIDE;       // FIXME! check for DOWN

    if(format==IMGFMT_YV12)
        return flag;
    
    return 0;
}

static void uninit(void)
{
    int tmp;
    eaviosSendEvent(&AV1, 7, 0, 0);
    eaviosGetReply(&AV1, &tmp);
    shmdt(imagedata);
    close(AV1.fd);
//    eaviosDisconnectServer(&AV1);
}

static int preinit(const char *arg)
{
    return 0;
}

static int control(uint32_t request, void *data, ...)
{
    switch (request)
    {
        case VOCTRL_PAUSE:
            return (int_pause = 1);
        case VOCTRL_RESUME:
            return (int_pause = 0);
        case VOCTRL_QUERY_FORMAT:
            return query_format(*((uint32_t *) data));
        case VOCTRL_GET_IMAGE:
            return get_image(data);
        case VOCTRL_DRAW_IMAGE:
            return draw_image(data);
        case VOCTRL_GUISUPPORT:
            return VO_TRUE;
        case VOCTRL_GET_PANSCAN:
            return VO_TRUE;
        case VOCTRL_FULLSCREEN:
            return VO_TRUE;
        case VOCTRL_SET_PANSCAN:
            return VO_TRUE;
        case VOCTRL_SET_EQUALIZER:
            return VO_TRUE;
        case VOCTRL_GET_EQUALIZER:
            return VO_TRUE;
        case VOCTRL_ONTOP:
            return VO_TRUE;
    }
    return VO_NOTIMPL;
}
