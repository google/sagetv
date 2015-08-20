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
#define MEDIACMD_INIT 0

#define MEDIACMD_DEINIT 1

#define MEDIACMD_OPENURL 16
// length, url

#define MEDIACMD_GETMEDIATIME 17

#define MEDIACMD_SETMUTE 18
// mute

#define MEDIACMD_STOP 19

#define MEDIACMD_PAUSE 20

#define MEDIACMD_PLAY 21

#define MEDIACMD_FLUSH 22

#define MEDIACMD_PUSHBUFFER 23
// size, flags, data

#define MEDIACMD_GETVIDEORECT 24
// TODO

#define MEDIACMD_SETVIDEORECT 25
// x, y, width, height, x, y, width, height

#define MEDIACMD_GETVOLUME 26

#define MEDIACMD_SETVOLUME 27
// volume

#define MEDIACMD_FRAMESTEP 28
// amount

#define MEDIACMD_SEEK 29
// long long millis

#define MEDIACMD_DVD_NEWCELL 32
#define MEDIACMD_DVD_CLUT 33
#define MEDIACMD_DVD_SPUCTRL 34
#define MEDIACMD_DVD_STC 35
#define MEDIACMD_DVD_STREAMS 36
#define MEDIACMD_DVD_FORMAT 37
