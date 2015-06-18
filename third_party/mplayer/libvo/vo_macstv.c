/* SageTV for Mac OS X video out module */

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


/* Mach IPC calls to communicate with the miniclient/placeshifter */
#include <mach/mach_types.h>
#include <mach/mach_error.h>
#include <mach/mach_port.h>
#include <mach/task.h>
#include <mach/mach_init.h>
#include <mach/mach_host.h>
#include <mach/mach_vm.h>
#include <mach/vm_map.h>
#include <servers/bootstrap.h>

#include "vo_macstv_ipc.h"

#ifdef HAVE_NEW_GUI
#include "../Gui/interface.h"
#endif

#define kDefaultClientName "SageTV"

static vo_info_t info = {
	"SageTV Mac",
	"macstv",
	"David DeHaven",
	""
};

LIBVO_EXTERN(macstv)

static int int_pause;

// IPC/shared memory stuff
char *client_name = NULL;
mach_port_t server_port = 0;
vm_address_t shared_address = 0;
mach_port_t shared_object = 0;
vm_size_t shared_size = 0;			// total size of shared memory buffer

static unsigned char *image_data = NULL;
static unsigned char *image_planes[4] = {NULL, NULL, NULL, NULL};	// convenience pointers to each plane
static uint32_t image_stride[4] = {0,0,0,0};		// rowbytes for each plane
static uint32_t image_width = 0;
static uint32_t image_height = 0;
static uint32_t image_format = 0;
static uint32_t image_rowbytes = 0;			// if planar, only use for allocating space and doing bulk memcpy's
static int send_frame_flag = 0;
static int send_frame_fields = 0;

// external settings
extern int vo_rootwin;
extern int vo_ontop;
extern int vo_fs;
//static int isFullscreen;
//static int isOntop;
//static int isRootwin;
extern float monitor_aspect;
extern int vo_keepaspect;
extern float movie_aspect;
//static float old_movie_aspect;
extern float vo_panscan;

static int config(uint32_t width, uint32_t height, uint32_t d_width,
					   uint32_t d_height, uint32_t flags, char *title,
					   uint32_t format)
{
	int plane_count = 1;
	kern_return_t kr;
	uint32_t image_depth = 0;
	
	image_data = NULL;
	image_width = width;
	image_height = height;
	image_format = format;
	movie_aspect = (float)d_width/(float)d_height;
	
	switch(format) {
		case IMGFMT_ARGB:
		case IMGFMT_ABGR:
		case IMGFMT_RGBA:
		case IMGFMT_BGRA:
			image_depth = 32;
			break;
		case IMGFMT_YUY2:
		case IMGFMT_UYVY:
		case IMGFMT_YVYU:
			image_depth = 16;
			break;
		case IMGFMT_YV12:	// planar 4:2:0
			image_depth = 12;
			plane_count = 3;
			break;
	}
		// not really valid for planar formats, but it does allow us to calculate the full buffer size we'll need
	image_rowbytes = (image_width * image_depth) / 8;
	
	// allocate the shared buffer
	shared_size = image_rowbytes * image_height;
	if(plane_count > 1) {
		vm_size_t pageSize = 0;
		if(!host_page_size(mach_host_self(), &pageSize))
			shared_size += pageSize * plane_count;// allow room for page alignment on each plane
	}
	shared_size = round_page(shared_size);	// round up to the nearest page size
	
	shared_address = 0; // or vm_allocate may try to use garbage as an address
	kr = vm_allocate(mach_task_self(), &shared_address, shared_size, 1);
	if(kr) {
		fprintf(stderr, "vo_macstv: vm_allocate failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	image_data = (unsigned char*)shared_address;
	image_planes[0] = image_data;
	
	switch(format) {
		case IMGFMT_ARGB:
		case IMGFMT_ABGR:
		case IMGFMT_RGBA:
		case IMGFMT_BGRA:
		case IMGFMT_YUY2:
		case IMGFMT_UYVY:
		case IMGFMT_YVYU:
			image_stride[0] = image_rowbytes;
			break;
		case IMGFMT_YV12:	// planar 4:2:0
			image_stride[0] = image_width;
			image_stride[2] = image_stride[1] = image_width>>1;
			
			image_planes[1] = image_planes[0] + (image_height * image_stride[0]);
//			image_planes[1] = (unsigned char*)round_page((natural_t)(image_planes[1]));	// start on page boundary
			
			image_planes[2] = image_planes[1] + ((image_height>>1) * image_stride[1]);
//			image_planes[2] = (unsigned char*)round_page((natural_t)(image_planes[2]));
			break;
	}
	// create a memory entry object so the client can map this buffer into their address space
	kr = mach_make_memory_entry(mach_task_self(), &shared_size, shared_address, VM_PROT_READ|VM_PROT_WRITE, &shared_object, 0);
	if(kr) {
		fprintf(stderr, "vo_macstv: mach_make_memory_entry_64 failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	// register the memory object with the client so it can be vm_mapped
	// only one buffer at a time currently, so set the buffer ID to 1
	kr = macstv_register_shared_memory(server_port, shared_object, shared_size, 1);
	if(kr) {
		fprintf(stderr, "vo_macstv: macstv_register_shared_memory failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	// tell the client what the incoming frames will look like
	kr = macstv_set_video_image_params(server_port, width, height, format, (d_width / d_height));
	if(kr) {
		fprintf(stderr, "vo_macstv: macstv_set_video_image_params failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	// finally, set the scaling params
	kr = macstv_set_render_size(server_port, d_width, d_height);
	if(kr) {
		fprintf(stderr, "vo_macstv: macstv_set_render_size failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	// tell the client to get ready to start rendering
	kr = macstv_start(server_port);
	if(kr) {
		fprintf(stderr, "vo_macstv: macstv_start failed (%d): %s\n", kr, mach_error_string(kr));
		return VO_ERROR;
	}
	
	return 0;
}

static void draw_osd(void)
{
//	vo_draw_text(image_width, image_height, draw_alpha);
}

static void flip_page(void)
{
//	  fprintf(stderr, "flip page\n");
//	if(server_port) macstv_render(server_port, 1, 0);
}

static void check_events(void)
{
//	  fprintf(stderr, "check events\n");
}

static int draw_slice(uint8_t *src[], int stride[], int w, int h, int x, int y)
{
	if(send_frame_flag) {
		if(server_port) {
			kern_return_t kr = macstv_render(server_port, 1, send_frame_fields);
			if(kr != KERN_SUCCESS) {
				fprintf(stderr, "vo_macstv:draw_slice: macstv_render returned an error (%d): %s\n", kr, mach_error_string(kr));
				return VO_ERROR;
			}
		}
		send_frame_flag = 0; // drop it if unsuccessful
	}
	
	if(image_format != IMGFMT_YV12) return VO_ERROR;
	
	if(image_data) {
		uint8_t *dst;
		
		dst = image_planes[0] + ((y * image_stride[0]) + x);
		memcpy_pic(dst, src[0], w, h, image_stride[0], stride[0]);

		w >>= 1;
		h >>= 1;
		x >>= 1;
		y >>= 1;
		
		dst = image_planes[2] + ((y * image_stride[2]) + x);
		memcpy_pic(dst, src[1], w, h, image_stride[2], stride[1]);

		dst = image_planes[1] + ((y * image_stride[1]) + x);
		memcpy_pic(dst, src[2], w, h, image_stride[1], stride[2]);
	}
	return VO_TRUE;
}

static int draw_frame(uint8_t * src[])
{
	if(send_frame_flag) {
		if(server_port) {
			kern_return_t kr = macstv_render(server_port, 1, send_frame_fields);
			if(kr != KERN_SUCCESS) {
				fprintf(stderr, "vo_macstv:draw_frame: macstv_render returned an error (%d): %s\n", kr, mach_error_string(kr));
				return VO_ERROR;
			}
		}
		send_frame_flag = 0; // drop it if unsuccessful
	}
	
	switch (image_format)
	{
		case IMGFMT_ARGB:
		case IMGFMT_ABGR:
		case IMGFMT_RGBA:
		case IMGFMT_BGRA:
			memcpy(image_data, src[0], image_height * image_rowbytes);
			break;
		
		case IMGFMT_YUY2:
		case IMGFMT_UYVY:
		case IMGFMT_YVYU:
			memcpy_pic(image_data, src[0], image_rowbytes, image_height, image_rowbytes, image_rowbytes);
			break;
		
		case IMGFMT_YV12:
			memcpy_pic(image_planes[0], src[0], image_stride[0], image_height, image_stride[0], image_stride[0]);
			memcpy_pic(image_planes[1], src[1], image_stride[1], image_height>>1/*/2*/, image_stride[1], image_stride[1]);
			memcpy_pic(image_planes[2], src[2], image_stride[2], image_height>>1/*/2*/, image_stride[2], image_stride[2]);
			break;
		default:
			return VO_ERROR;
	}
	
	return 0;
}

static int draw_image(mp_image_t * mpi)
{
//	if(server_port) macstv_render(server_port, 1, mpi->fields);
	send_frame_flag = 1;
	send_frame_fields = mpi->fields;
	// TODO: update image_stride fields if needed
	return VO_FALSE;
}

static int get_image(mp_image_t * mpi)
{
//	  fprintf(stderr, "get_image\n");
	return VO_FALSE;
}

static int query_format(uint32_t format)
{
	uint32_t result;
	kern_return_t kr;
	
	// FIXME: filter formats we DON'T support!!! else we'll fail when an unsupported format is chosen
	
	if(server_port) {
		kr = macstv_query_format(server_port, format, &result);
		if(kr) {
			fprintf(stderr, "vo_macstv: macstv_query_format failed (%d): %s\n", kr, mach_error_string(kr));
			return 0;
		}
		
		return (int)result;
	}
	
	return 0;
}

static void uninit(void)
{
	if(server_port) macstv_stop(server_port); // client should release all shared memory objects
	
	// clean up, clear things on the off chance that we'll come back (???)
	if(client_name) free(client_name);
	
	if(shared_address) vm_deallocate(mach_task_self(), shared_address, shared_size);
	shared_address = 0;
	shared_size = 0;
	
	if(shared_object) mach_port_destroy(mach_task_self(), shared_object);
	shared_object = 0;
	
	if(server_port) mach_port_destroy(mach_task_self(), server_port);
	server_port = 0;
}

static opt_t subopts[] = {
	{"client", OPT_ARG_MSTRZ, &client_name, NULL, 0},
	{NULL, 0, NULL, NULL, 0}
};

static void term_action(int sig, struct __siginfo *sa, void *arg)
{
	_exit(0);
}

static int preinit(const char *arg)
{
	mach_port_t bp;
	kern_return_t kr;
	struct sigaction sa;
	
	if(subopt_parse(arg, subopts) != 0) {
		mp_msg(MSGT_VO, MSGL_FATAL,
			"\n-vo macstv command line help:\n"
			"Example: mplayer -vo macstv:client=MyClient\n"
			"\nOptions:\n"
			"    client=<name>\n"
			"      Name used to look up the client mach IPC port (required)\n"
			"\n" );
		return -1;
	}
	
	if(!client_name) {
		client_name = (char*)malloc(sizeof(kDefaultClientName));
		strcpy(client_name, kDefaultClientName);
	} else fprintf(stderr, "client name : %s\n", client_name);
	
	// look up the server_port in the bootstrap server
	kr = task_get_bootstrap_port(mach_task_self(), &bp);
	if(kr) {
		fprintf(stderr, "vo_macstv: task_get_bootstrap_port failed (%d): %s\n", kr, mach_error_string(kr));
		return -1;
	}
	
	// do the lookup
	kr = bootstrap_look_up(bp, client_name, &server_port);
	if(kr) {
		fprintf(stderr, "vo_macstv: bootstrap_look_up failed (%d): %s\n", kr, mach_error_string(kr));
		return -1;
	}
	fprintf(stderr, "vo_macstv: Found server port at %p\n", (void*)server_port);
	
	// install our sighandler so we can kill the process completely instead of fixing
	// all the crap the mplayer team thinks needs to be done before termination
	sa.sa_sigaction = term_action;
	sa.sa_flags = SA_SIGINFO;
	sigemptyset(&sa.sa_mask);
	sigaction(SIGINT, &sa, NULL);
	sigaction(SIGTERM, &sa, NULL);
	sigaction(SIGKILL, &sa, NULL);
	
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
		case VOCTRL_GET_PANSCAN: // need to figure this one out
			return VO_TRUE;
		case VOCTRL_FULLSCREEN:
			return VO_TRUE;
		case VOCTRL_SET_PANSCAN: // ditto
			return VO_TRUE;
		case VOCTRL_SET_EQUALIZER: // ???
			return VO_TRUE;
		case VOCTRL_GET_EQUALIZER:
			return VO_TRUE;
		case VOCTRL_ONTOP:
			return VO_TRUE;
		case VOCTRL_RECTANGLES:
			return VO_NOTIMPL;	// FIXME: send this on to the client...
	}
	return VO_NOTIMPL;
}

