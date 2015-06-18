#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

static int exit_flag = 0;

void *pt_func(void *ptr)
{
	printf("Hello, World!\n");
	exit_flag++;
}

main()
{
	pthread_t foo_thread;
#if defined(PTW32_STATIC_LIB)
	pthread_win32_process_attach_np();
#endif
	
	fprintf(stderr, "spawing thread\n");
	pthread_create(&foo_thread, NULL, pt_func, NULL);
	fprintf(stderr, "spawned thread, waiting...\n");
	while(!exit_flag) { usleep(100); }
	fprintf(stderr, "done!\n");

#if defined(PTW32_STATIC_LIB)
	pthread_win32_process_detach_np();
#endif
	exit(0);
}
