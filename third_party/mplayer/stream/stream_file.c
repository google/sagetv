
#include "config.h"

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include "mp_msg.h"
#include "stream.h"
#include "help_mp.h"
#include "input/input.h"
#include "m_option.h"
#include "m_struct.h"

#define NUM_LOOKS_FOR_DATA 100
#define WAIT_BETWEEN_LOOKS 50

static struct stream_priv_s {
  char* filename;
  char *filename2;
} stream_priv_dflts = {
  NULL, NULL
};

extern mp_cmd_t* mp_input_get_cmd(int time, int paused, int peek_only);

#define ST_OFF(f) M_ST_OFF(struct stream_priv_s,f)
/// URL definition
static m_option_t stream_opts_fields[] = {
  {"string", ST_OFF(filename), CONF_TYPE_STRING, 0, 0 ,0, NULL},
  {"filename", ST_OFF(filename2), CONF_TYPE_STRING, 0, 0 ,0, NULL},
  { NULL, NULL, 0, 0, 0, 0,  NULL }
};
static struct m_struct_st stream_opts = {
  "file",
  sizeof(struct stream_priv_s),
  &stream_priv_dflts,
  stream_opts_fields
};  

static int fill_buffer(stream_t *s, char* buffer, int max_len){
  int r = 0,curr=0;
  int numLooks = 0;
  mp_cmd_t* cmd;
  while (1 /*&& numLooks < NUM_LOOKS_FOR_DATA*/) // Let it loop forever if the file is still maked as an active file since it could be a transcoding pause
  {
	  curr = read(s->fd,buffer,max_len);
	  if (curr >= 0)
		  r += curr;
	  else
		  r = curr;
	  if (curr == max_len || r < 0 || !s->activeFileFlag)
		  break;
	  // Check for an update in the inactive file status, or a load file request
	  // But this is NOT THREAD SAFE!!!
//	  cmd = mp_input_get_cmd(0, 0, 1);
//	  if (cmd->id == MP_CMD_INACTIVE_FILE || cmd->id == MP_CMD_LOADFILE2)
//		  break;
//fprintf(stderr, "WAITING FOR DATA to appear in the file...looks=%d len=%d pos=%d\n", numLooks, max_len, (int)s->pos);
//fflush(stderr);
      max_len -= curr;
	  buffer += curr;
#ifndef WIN32	  
	  usleep(WAIT_BETWEEN_LOOKS * 1000);
#else
	  usec_sleep(WAIT_BETWEEN_LOOKS * 1000);
#endif
	  numLooks++;
  }
  return (r <= 0) ? -1 : r;
}

static int write_buffer(stream_t *s, char* buffer, int len) {
  int r = write(s->fd,buffer,len);
  return (r <= 0) ? -1 : r;
}

static int seek(stream_t *s,off_t newpos) {
  s->pos = newpos;
#if defined(CONFIG_WIN32) && !defined(__CYGWIN__) 
	if(_lseeki64(s->fd,s->pos,SEEK_SET)<0) {
#else
  if(lseek(s->fd,s->pos,SEEK_SET)<0) {
#endif
    s->eof=1;
    return 0;
  }
  return 1;
}

static int seek_forward(stream_t *s,off_t newpos) {
  if(newpos<s->pos){
    mp_msg(MSGT_STREAM,MSGL_INFO,"Cannot seek backward in linear streams!\n");
    return 0;
  }
  while(s->pos<newpos){
    int len=s->fill_buffer(s,s->buffer,stream_buffer_size);
    if(len<=0){ s->eof=1; s->buf_pos=s->buf_len=0; break; } // EOF
    s->buf_pos=0;
    s->buf_len=len;
    s->pos+=len;
  }
  return 1;
}

static int control(stream_t *s, int cmd, void *arg) {
  switch(cmd) {
    case STREAM_CTRL_GET_SIZE: {
      off_t size;

      size = lseek(s->fd, 0, SEEK_END);
      lseek(s->fd, s->pos, SEEK_SET);
      if(size != (off_t)-1) {
        *((off_t*)arg) = size;
        return 1;
      }
    }
  }
  return STREAM_UNSUPORTED;
}

static off_t size(stream_t *stream, off_t *availSize)
{
	struct stat fileStats;
	memset(&fileStats, 0, sizeof(fileStats));
	if (!fstat(stream->fd, &fileStats))
	{
//		printf("File size is %lld\n", fileStats.st_size);
		if (availSize)
			*availSize = fileStats.st_size;
		return fileStats.st_size;
	}
	else
		return 0;
}

static int open_f(stream_t *stream,int mode, void* opts, int* file_format) {
  int f;
  mode_t m = 0;
  off_t len;
  unsigned char *filename;
  struct stream_priv_s* p = (struct stream_priv_s*)opts;

  if(mode == STREAM_READ)
    m = O_RDONLY;
  else if(mode == STREAM_WRITE)
    m = O_RDWR|O_CREAT|O_TRUNC;
  else {
    mp_msg(MSGT_OPEN,MSGL_ERR, "[file] Unknown open mode %d\n",mode);
    m_struct_free(&stream_opts,opts);
    return STREAM_UNSUPORTED;
  }

  if(p->filename)
    filename = p->filename;
  else if(p->filename2)
    filename = p->filename2;
  else
    filename = NULL;
  if(!filename) {
    mp_msg(MSGT_OPEN,MSGL_ERR, "[file] No filename\n");
    m_struct_free(&stream_opts,opts);
    return STREAM_ERROR;
  }

#if defined(__CYGWIN__)|| defined(__MINGW32__)
  m |= O_BINARY;
#endif    

  if(!strcmp(filename,"-")){
    if(mode == STREAM_READ) {
      // read from stdin
      mp_msg(MSGT_OPEN,MSGL_INFO,MSGTR_ReadSTDIN);
      f=0; // 0=stdin
#ifdef __MINGW32__
	  setmode(fileno(stdin),O_BINARY);
#endif
    } else {
      mp_msg(MSGT_OPEN,MSGL_INFO,"Writing to stdout\n");
      f=1;
#ifdef __MINGW32__
	  setmode(fileno(stdout),O_BINARY);
#endif
    }
  } else {
    if(mode == STREAM_READ)
      f=open(filename,m);
    else {
      mode_t openmode = S_IRUSR|S_IWUSR;
#ifndef __MINGW32__
      openmode |= S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH;
#endif
      f=open(filename,m, openmode);
    }
    if(f<0) {
      mp_msg(MSGT_OPEN,MSGL_ERR,MSGTR_FileNotFound,filename);
      m_struct_free(&stream_opts,opts);
      return STREAM_ERROR;
    }
  }

#if defined(CONFIG_WIN32) && !defined(__CYGWIN__) 
    len=_lseeki64(f,0,SEEK_END); _lseeki64(f,0,SEEK_SET);
#else
  len=lseek(f,0,SEEK_END); lseek(f,0,SEEK_SET);
#endif
	
#ifdef __MINGW32__
  if(f==0 || len == -1) {
#else
  if(len == -1) {
#endif
fprintf(stderr, "FAILED seeking to end of file, using it as a stream\n");
fflush(stderr);
    if(mode == STREAM_READ) stream->seek = seek_forward;
    stream->type = STREAMTYPE_STREAM; // Must be move to STREAMTYPE_FILE
    stream->flags |= STREAM_SEEK_FW;
  } else if(len >= 0) {
    stream->seek = seek;
    stream->end_pos = len;
    stream->type = STREAMTYPE_FILE;
  }

  mp_msg(MSGT_OPEN,MSGL_V,"[file] File size is %"PRId64" bytes\n", (int64_t)len);

  stream->fd = f;
  stream->size = size;
  stream->fill_buffer = fill_buffer;
  stream->write_buffer = write_buffer;
  stream->control = control;

  m_struct_free(&stream_opts,opts);
  return STREAM_OK;
}

stream_info_t stream_info_file = {
  "File",
  "file",
  "Albeu",
  "based on the code from ??? (probably Arpi)",
  open_f,
  { "file", "", NULL },
  &stream_opts,
  1 // Urls are an option string
};
