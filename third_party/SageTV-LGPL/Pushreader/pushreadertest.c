/*
 *    Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *   Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include <stdio.h>
#include <stdlib.h>
#include "avio.h"
#include "pushreader.h"

int main(int argc, char **argv)
{
    unsigned char *decbuffer_video, *decbuffer_audio, *decbuffer_sub;
    int videobytes=0;
    int audiobytes=0;
	int spubytes=0;
    int dectype;
    int haspts;
    int duration;
    long long pts;
    struct PushReader * pr;
	int buffer_size=(32*1024*4);
	long count;
	FILE *audio_dump=NULL, *video_dump=NULL, *spu_dump=NULL;
	//unsigned char *decbuffer = decbuffer_video=(unsigned char *) malloc(buffer_size);
    decbuffer_video=(unsigned char *) malloc(buffer_size);
	decbuffer_audio=(unsigned char *) malloc(buffer_size);
	decbuffer_sub=(unsigned char *) malloc(2048);
    fprintf(stderr, "Opening %s with PushReader\n",argv[1]);
    pr=OpenPushReader(argv[1], NULL, 0 );
    disableDecodeAudio( pr );
    fprintf(stderr, "Opened pull reader retval 0x%p\n",((void*)pr));
    if(pr!=NULL)
    {
        int width, height, miscv;
        int channels, bps, samplerate, bitrate, blockalign, misca;
        int videocodec = getVideoCodecSettings(pr, &width, &height, &miscv);
        int audiocodec = getAudioCodecSettings(pr, &channels, &bps, &samplerate, &bitrate, &blockalign, &misca);
        fprintf(stderr, "codec settings : V(%d): %dX%d %d  A(%d): %d %d %d %d %d %d\n", 
            videocodec, width, height, miscv, audiocodec,
            channels, bps, samplerate, bitrate, blockalign, misca);

		audio_dump=fopen( "audio_dump.bin","wb"  );
		video_dump=fopen( "video_dump.bin", "wb" );
		spu_dump=fopen( "spu_dump.bin", "wb" );
		
    }
//	printf( "hit Enter to continue\n" );
//	getchar();
//    prSetSpuStream(pr, 0);
    //prSelectAudioStream( pr, 1 );

//    prSeek(pr, 30*1000);
    //setFixH264( pr, 0 );
	prSeek(pr, 0);
	count=0;
	//setDebugTrace( "DebugTrace.log", 4 );
    while(!getEOF(pr))
    {
        int datalen;
        pts=0;

if (0){
		unsigned char *decbuffer =  decbuffer_video;
        datalen = ProcessFrames(pr, &dectype, decbuffer, 32768, &haspts, &pts, &duration);
		if(dectype==0) 
		{
			//fprintf(stdout,"Process video 0x%x bytes @%d\n", datalen, count++ );
			videobytes+=datalen;
			if ( video_dump != NULL )
				fwrite( decbuffer, datalen, 1, video_dump );
		}
		if(dectype==1) 
		{  
			//fprintf(stdout,"Process audio 0x%x bytes @%d\n", datalen, count++ );
			audiobytes+=datalen;
			if ( audio_dump != NULL )
				fwrite( decbuffer, datalen, 1, audio_dump );
		}
		if(dectype==2) 
		{  
			spubytes+=datalen;
			if ( spu_dump != NULL )
				fwrite( decbuffer, datalen, 1, spu_dump );
		}
}

 if (1){
        dectype = 0;
		datalen = ProcessVideoFrames(pr, decbuffer_video, buffer_size, &haspts, &pts, &duration);
		if ( datalen > 0 )
		{
			fprintf(stdout,"Process video 0x%x bytes @%d .\n", datalen, count++ );
			videobytes+=datalen;
			if ( video_dump != NULL )
				fwrite( decbuffer_video, datalen, 1, video_dump );
		}

		dectype = 1;
		datalen = ProcessAudioFrames(pr, decbuffer_audio, buffer_size, &haspts, &pts, &duration);
		if ( datalen > 0 )
		{
			fprintf(stdout,"Process audio 0x%x bytes @%d .\n", datalen, count++ );
			audiobytes+=datalen;
			if ( audio_dump != NULL )
				fwrite( decbuffer_audio, datalen, 1, audio_dump );
		}
		dectype = 2;
		datalen = ProcessSubPictureFrames(pr, decbuffer_sub, 2048, &haspts, &pts, &duration);
		if ( datalen > 0 )
		{
			spubytes+=datalen;
			fprintf(stdout,"Process SPU 0x%x bytes @%d .\n", datalen, count++ );
			if ( spu_dump != NULL )
				fwrite( decbuffer_sub, datalen, 1, spu_dump );
		}
		if (datalen < 0)
			break;
}
       
		//fprintf(stderr,"Process frame returned %d bytes, type %d, haspts %d pts %lld duration %d\n",
        //                 datalen, dectype, haspts, pts, duration);


  	
      //fprintf(stderr, "videobytes %d\n audiobytes %d\n",videobytes, audiobytes);
    }
    fprintf(stderr, "Done\n");
	fprintf(stderr, "videobytes %d\n audiobytes %d spubytes%d\n", videobytes, audiobytes, spubytes);
    CloseReader(pr);
    free( decbuffer_video );
	free( decbuffer_audio );
	free( decbuffer_sub );
	//free( decbuffer );
    if ( audio_dump != NULL )
		fclose( audio_dump );
	if ( video_dump != NULL )
		fclose( video_dump );
	if ( spu_dump != NULL )
		fclose( spu_dump );
	setDebugTrace( "", 0 ); //close debug log
}



