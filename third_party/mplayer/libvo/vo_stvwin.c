#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <windows.h>

#include "subopt-helper.h"
#include "mp_msg.h"
#include "video_out.h"
#include "video_out_internal.h"

#include "fastmemcpy.h"
#include "sub.h"
#include "aspect.h"

static vo_info_t info = {
    "SageTV Windows",
    "stvwin",
    "Jeffrey Kardatzke",
    ""
};

LIBVO_EXTERN(stvwin)

#define DLOG(...) 0


// FIXME: dynamically allocate this stuff
static uint32_t image_width;
static uint32_t image_height;
static uint32_t image_format;
static int flip_flag;

static int int_pause;

static uint32_t offsetY;
static uint32_t pitchY;
static uint32_t offsetU;
static uint32_t pitchU;
static uint32_t offsetV;
static uint32_t pitchV;

static int waitForDone = 0;

static HANDLE fileMap;
static HANDLE evtReady;
static HANDLE evtDone;

static uint32_t *cmddata = NULL;
static unsigned char *imagedata = NULL;

static int config(uint32_t width, uint32_t height, uint32_t d_width,
                       uint32_t d_height, uint32_t flags, char *title,
                       uint32_t format)
{
	cmddata[0] = (0x80<<24)|12;
	cmddata[1] = width;
	cmddata[2] = height;
	cmddata[3] = 0;
	SetEvent(evtReady);
	if (WAIT_OBJECT_0 != WaitForSingleObject(evtDone, 15000))
	{
        mp_msg(MSGT_VO, MSGL_FATAL, "No response from video renderer on shared memory details\n");
		return 1;
	}
    mp_msg(MSGT_VO, MSGL_INFO, "sent command 128");

    // receive
    offsetY=cmddata[0];
    pitchY=cmddata[1];
    offsetU=cmddata[2];
    pitchU=cmddata[3];
    offsetV=cmddata[4];
    pitchV=cmddata[5];

    mp_msg(MSGT_VO, MSGL_INFO, "config reply %08X %08X %08X %08X %08X %08X\n",
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
	// Wait for the last video frame to be consumed
	if (waitForDone && WAIT_OBJECT_0 != WaitForSingleObject(evtDone, 100))
	{
		printf("WAIT-0 TIMEOUT EXPIRED!!!\r\n");
		return VO_TRUE;
	}
	waitForDone = 0;

    uint8_t *dst;

    if(imagedata!=NULL)
    {
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
    }
    return VO_TRUE;
}

static int draw_frame(uint8_t * src[])
{
    printf("draw_frame() called!!!!!!");
    return -1;
}

static int draw_image(mp_image_t * mpi)
{
	cmddata[0] = (0x81<<24)|4;
	cmddata[1] = mpi->fields;
	SetEvent(evtReady);
	waitForDone = TRUE;
    return VO_FALSE;
}

static int get_image(mp_image_t * mpi)
{
    printf("get_image\n");
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
	cmddata[0] = (0x82<<24)|0;
	SetEvent(evtReady);
	UnmapViewOfFile((unsigned char*)cmddata);
	CloseHandle(evtReady);
	CloseHandle(evtDone);
	CloseHandle(fileMap);
}


static int preinit(const char *arg)
{
    strarg_t shmemprefix_str = {0, NULL};
    opt_t subopts[] = {
        {"shmemprefix",  OPT_ARG_STR,  &shmemprefix_str,   NULL},
        {NULL}
    };

    if (subopt_parse(arg, subopts) != 0) {
      mp_msg(MSGT_VO, MSGL_FATAL,
              "\n-vo stvwin command line help:\n"
              "Example: mplayer -vo stvwin:shmemprefix=MyVideo\n"
              "\nOptions:\n"
              "  shmemprefix=<SharedMemoryPrefix>\n"
              "    SharedMemoryPrefix prefix for shared memory IDs\n"
              "\n" );
      return -1;
    }
    if(shmemprefix_str.len <= 0)
	{
      mp_msg(MSGT_VO, MSGL_FATAL, "No shared memory prefix specified!\n");
      return -1;
	}

    mp_msg(MSGT_VO, MSGL_INFO, "shmemprefix : %s\n", shmemprefix_str.str);

	fileMap = OpenFileMapping(FILE_MAP_WRITE|FILE_MAP_READ, 0, shmemprefix_str.str);
	if (!fileMap)
	{
	      mp_msg(MSGT_VO, MSGL_FATAL, "Unable to open file mapping for: %s\n", shmemprefix_str.str);
		return -1;
	}
	char buf[256];
	strcpy(buf, shmemprefix_str.str);
	strcat(buf, "FrameReady");
	evtReady = CreateEvent(NULL, FALSE, FALSE, buf);
	strcpy(buf, shmemprefix_str.str);
	strcat(buf, "FrameDone");
	evtDone = CreateEvent(NULL, FALSE, FALSE, buf);
	imagedata = MapViewOfFile(fileMap, FILE_MAP_WRITE|FILE_MAP_READ, 0, 0, 0);
	cmddata = (uint32_t*)imagedata;
printf("stvwin vo is initialized\n");
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
