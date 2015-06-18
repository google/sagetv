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
#include <netinet/in.h>
#include <netinet/tcp.h>


#include "config.h"
#include "subopt-helper.h"
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
    "SageTV Miniclient",
    "stv",
    "Jean-Francois Thibert",
    ""
};

LIBVO_EXTERN(stv)

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

static int serverfd = -1;
static int mappedfd = -1;

static unsigned char *imagedata = NULL;
static unsigned char mappedfname[108]; // TODO: find better way to get value

int readfully(int fd, char *buf, int count)
{
    int pos=0;
    while(pos<count)
    {
        int retval=read(fd, &buf[pos], count-pos);
        if(retval<0) return -1;
        pos+=retval;        
    }
    return pos;
}

static int connectServer(char *socketname)
{
    int fd = -1;
    int i;
    struct sockaddr_un sa;
    
    /* local unix socket */
    fd = socket(PF_UNIX, SOCK_STREAM, 0);
    
    if(fd < 0)
    {
        DLOG("Error creating socket\n");
        return(-1);
    }

    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    strncpy(sa.sun_path, socketname, 108); // TODO: find better way to get value
    
    if(connect(fd, (struct sockaddr *)&sa, sizeof(sa)) != 0) 
    {
        DLOG("Error creating socket\n");
        close(fd);
        return -1;
    }

    return fd;
}


static int config(uint32_t width, uint32_t height, uint32_t d_width,
                       uint32_t d_height, uint32_t flags, char *title,
                       uint32_t format)
{
    uint32_t cmd[5];
    uint32_t reply[6];
    uint32_t strlen;
    
    cmd[0]=htonl((0x80<<24)|12); // CreateVideo
    cmd[1]=htonl(width);
    cmd[2]=htonl(height);
    cmd[3]=htonl(0); // Color format YV12
    write(serverfd, &cmd[0], 4+12);

    mp_msg(MSGT_VO, MSGL_INFO, "sent command 128");

    fflush(stdout);        
    fflush(stderr);
    readfully(serverfd, &strlen, 4);
    strlen=ntohl(strlen);
    readfully(serverfd, &mappedfname[0], strlen);
    mappedfname[strlen]=0;
    
    readfully(serverfd, &reply[0], 24);
    // receive
    offsetY=ntohl(reply[0]);
    pitchY=ntohl(reply[1]);
    offsetU=ntohl(reply[2]);
    pitchU=ntohl(reply[3]);
    offsetV=ntohl(reply[4]);
    pitchV=ntohl(reply[5]);

    mp_msg(MSGT_VO, MSGL_INFO, "config reply %s %08X %08X %08X %08X %08X %08X\n",
        mappedfname, offsetY, pitchY, offsetU, pitchU, offsetV, pitchV);

    if((mappedfd=open(mappedfname, O_RDWR))<0)
    {
        mp_msg(MSGT_VO, MSGL_FATAL, "Could not open file %s\n", mappedfname);
        return 1;
    }
        
    imagedata = mmap(NULL, 1920*540*3, PROT_READ|PROT_WRITE, MAP_SHARED, mappedfd, 0);
    
    if(imagedata == (unsigned char *) -1)
    {
        mp_msg(MSGT_VO, MSGL_FATAL, "could not mmap\n");
        return 1;
    }
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
    uint32_t cmd[5];
    uint32_t reply[6];
    uint32_t strlen;
    
    cmd[0]=htonl((0x81<<24)|4); // PutVideo
    cmd[1]=htonl(mpi->fields); // progressive frame
    write(serverfd, &cmd[0], 8);
    
    readfully(serverfd, &reply[0], 24);
    // receive
    offsetY=ntohl(reply[0]);
    pitchY=ntohl(reply[1]);
    offsetU=ntohl(reply[2]);
    pitchU=ntohl(reply[3]);
    offsetV=ntohl(reply[4]);
    pitchV=ntohl(reply[5]);
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
    if(IMGFMT_IS_XVMC(format))
        return flag;
    
    return 0;
}

static void uninit(void)
{
    uint32_t cmd[1];
    cmd[0]=htonl((0x82<<24)|0); // CreateVideo
    write(serverfd, &cmd[0], 4);
    
    if(mappedfd>=0) close(mappedfd);
    if(serverfd>=0) close(serverfd);
}


static int preinit(const char *arg)
{
    strarg_t socket_str = {0, NULL};
    opt_t subopts[] = {
        {"socket",  OPT_ARG_STR,  &socket_str,   NULL},
        {NULL}
    };

    if (subopt_parse(arg, subopts) != 0) {
      mp_msg(MSGT_VO, MSGL_FATAL,
              "\n-vo stv command line help:\n"
              "Example: mplayer -vo stv:socket=/tmp/videosocket\n"
              "\nOptions:\n"
              "  socket=<socket>\n"
              "    socket filename\n"
              "\n" );
      return -1;
    }
    if(socket_str.len <= 0)
      return -1;

    mp_msg(MSGT_VO, MSGL_INFO, "socket : %s\n", socket_str.str);

    serverfd = connectServer(socket_str.str);
    if(serverfd < 0)
    {
        mp_msg(MSGT_VO, MSGL_ERR,"STV: could not open socket!");
        return -1;
    }
    
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
