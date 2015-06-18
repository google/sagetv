/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
//#include <alsa/asoundlib.h>
#include "sage_VideoFrame.h"
#include <dlfcn.h>

// NOTE: If we don't dlopen libasound we can end up getting
// some weird library errors and a nasty crash in here. This looks
// VERY similar to what we ran into with the SigmaDesigns libraries.
// Looks like a good lesson for dealing with .so's on Linux from Java
static int openedAsoundLib = 0;

/*
 * Class:     sage_VideoFrame
 * Method:    goodbye0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sage_VideoFrame_goodbye0(JNIEnv *env, jobject jo)
{
}


/*
 * Class:     sage_VideoFrame
 * Method:    getFileDuration0
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sage_VideoFrame_getFileDuration0(JNIEnv *env, jobject jo,
																   jstring jname)
{
	return 0;
}

/*snd_mixer_t* openMixer(snd_mixer_elem_t **elem)
{
	int err;
	snd_mixer_t *handle;
	snd_mixer_selem_id_t *sid = 0;
	
	static char *mix_name = "Master";
	static char *card = "default";
	static int mix_index = 0;
	
	//allocate simple id
	snd_mixer_selem_id_alloca(&sid);
	if (!sid)
	{
		printf("failed allocating alsa\n");
		return 0;
	}
	
	//sets simple-mixer index and name
	snd_mixer_selem_id_set_index(sid, mix_index);
	snd_mixer_selem_id_set_name(sid, mix_name);
	
	if ((err = snd_mixer_open(&handle, 0)) < 0) {
		printf("alsa-control: mixer open error: %s\n", snd_strerror(err));
		return 0;
	}
	
	if ((err = snd_mixer_attach(handle, card)) < 0) {
		printf("alsa-control: mixer attach %s error: %s\n", card, snd_strerror(err));
		snd_mixer_close(handle);
		return 0;
	}
	
	if ((err = snd_mixer_selem_register(handle, NULL, NULL)) < 0) {
		printf("alsa-control: mixer register error: %s\n", snd_strerror(err));
		snd_mixer_close(handle);
		return 0;
	}
	err = snd_mixer_load(handle);
	if (err < 0) {
		printf("alsa-control: mixer load error: %s\n", snd_strerror(err));
		snd_mixer_close(handle);
		return 0;
	}
	
	*elem = snd_mixer_find_selem(handle, sid);
	if (!*elem) {
		printf("alsa-control: unable to find simple control '%s',%i\n",
			snd_mixer_selem_id_get_name(sid), snd_mixer_selem_id_get_index(sid));
		snd_mixer_close(handle);
		return 0;
	}
	
	return handle;
	
}*/

/*
 * Class:     sage_VideoFrame
 * Method:    setSystemVolume0
 * Signature: (F)F
 */
JNIEXPORT jfloat JNICALL Java_sage_VideoFrame_setSystemVolume0
  (JNIEnv *env, jclass jc, jfloat vol)
{
/*	if (!openedAsoundLib)
	{
		dlopen("libasound.so", RTLD_LAZY|RTLD_GLOBAL);
		openedAsoundLib = 1;
	}
printf("Setvolf=%f\n", vol);
	int err;
	long pmin, pmax;
	float f_multi;
	snd_mixer_elem_t* elem;
	snd_mixer_t* handle = openMixer(&elem);
	if (!handle)
		return 0;
	snd_mixer_selem_get_playback_volume_range(elem,&pmin,&pmax);
	f_multi = (100 / (float)(pmax - pmin));
	long set_vol = ((long) (vol*100)) / f_multi + pmin + 0.5;

	//setting channels
	if ((err = snd_mixer_selem_set_playback_volume(elem, 0, set_vol)) < 0) {
	  printf("alsa-control: error setting left channel, %s\n", 
		 snd_strerror(err));
	}

	if ((err = snd_mixer_selem_set_playback_volume(elem, 1, set_vol)) < 0) {
	  printf("alsa-control: error setting right channel, %s\n", 
		 snd_strerror(err));
	}
    snd_mixer_close(handle);*/
	return vol;
}

/*
 * Class:     sage_VideoFrame
 * Method:    getSystemVolume0
 * Signature: ()F
 */
JNIEXPORT jfloat JNICALL Java_sage_VideoFrame_getSystemVolume0
  (JNIEnv *env, jclass jc)
{
/*	if (!openedAsoundLib)
	{
		dlopen("libasound.so", RTLD_LAZY|RTLD_GLOBAL);
		openedAsoundLib = 1;
	}
	long pmin, pmax;
	long get_voll, get_volr;
	float f_multi;
	snd_mixer_elem_t* elem;
	snd_mixer_t* handle = openMixer(&elem);
	if (!handle)
		return 0;
	snd_mixer_selem_get_playback_volume_range(elem,&pmin,&pmax);
	f_multi = (1.0f / (float)(pmax - pmin));
	snd_mixer_selem_get_playback_volume(elem, 0, &get_voll);
	snd_mixer_selem_get_playback_volume(elem, 1, &get_volr);
	float rv = ((get_voll + get_volr)/2 - pmin) * f_multi;

    snd_mixer_close(handle);
	return rv;*/
	return 1.0f;
}

