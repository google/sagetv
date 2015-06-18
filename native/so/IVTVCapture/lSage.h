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
// lSage.h contains global defines to avoid clutter in the main code.
#define LSAGEVERSION "0.0.1"

// Hard code the v4l2 ioctl value for setting the tuner card
// frequency.  I'm not sure if this is even needed, but the sample code
// had it.
#define VIDIOC_S_FREQUENCY2 0x402c5639

// IVTV Broadcast standard defines
#define sPAL 255
#define sNTSC 12288
#define sSECAM 83223072

// Define the maximum expected number of encoder quality types.
#define MAX_QUALITIES 64

// Define the maximum expected number of capture ports on the tuner card
#define MAXINPUTS 16

// Define the default capture port on the tuner card
#define DEFAULTPORT 4

// Define how many pending connections from the calling client that will be 
// queued
#define BACKLOG 10

// Define max number of bytes we can get at once on socket
#define MAXDATASIZE 512

#define SHMSZ     27

//  Again, this was in the sample code, probably not needed since it should
//  be defined in the v4l2 headers
struct v4l2_frequency2
{
	__u32                 tuner;
	__u32		      type;
	__u32                 frequency;
	__u32                 reserved[8];
};

// Structure containing all of the encoder quality settings received from the
// server.
struct vid_quality
{
	char name[256];
	long bitrate;
	long pbitrate;
	int vbr;
	int width;
	int height;
	int abitrate;
	int stype;
	int asampling;
};

struct input_type
{
	char port[8];
	char name[64];
	int irflag;
};
