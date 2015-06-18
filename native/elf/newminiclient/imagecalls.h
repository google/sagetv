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
#define IMAGE_STATE_FREE 0 
#define IMAGE_STATE_INIT 1
#define IMAGE_STATE_DECODING 2
#define IMAGE_STATE_DONE 3

#define IJ_COUNT 16
// state = 0 means it isn't a valid job
typedef struct {
    int state;
    int priority;
    // If those are not null we must signal the source 
    // when decoding is done or there is an error
    ACL_mutex *jobmutex; // TODO: could we use imagemutex instead?
    ACL_cond *jobcond;

    // Image decoding information
    int handle;
    int offset;
    int length;
    unsigned char name[PATH_MAX];
}ImageJob_t;

extern ACL_mutex *ImageMutex;
extern ACL_cond *ImageCond;
extern ImageJob_t ijobs[IJ_COUNT];

int ImageInit();
void ImageDeinit();
