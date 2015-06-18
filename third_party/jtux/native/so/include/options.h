#ifndef _OPTIONS_H_
#define _OPTIONS_H_

/*[enum-OPT_RETURN]*/
typedef enum {OPT_NO = 0, OPT_YES = 1, OPT_ERROR = -1} OPT_RETURN;
/*[]*/

OPT_RETURN option_sync_io(const char *path);
OPT_RETURN option_async_io(const char *path);

#endif /* _OPTIONS_H_ */
