/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the TUNERSTUBDLL_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// TUNERSTUBDLL_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef TUNERSTUBDLL_EXPORTS
#define TUNERSTUBDLL_API __declspec(dllexport)
#else
#define TUNERSTUBDLL_API __declspec(dllimport)
#endif

struct pattern
{
	unsigned bit_length;
	unsigned length;
	char r_flag;
	unsigned char *bytes;
	struct pattern *next;
};
typedef struct pattern pattern;	

struct command 
{
	unsigned char *name;
	struct pattern *pattern;
	struct command *next;
};      	
typedef struct command command;

struct remote 
{
//	unsigned int devOverride;
	unsigned char *name;
	unsigned long carrier_freq;
	unsigned bit_time;
	struct command *command;
	struct remote *next;
};
typedef struct remote remote;

#ifdef __cplusplus
extern "C" {
#endif


TUNERSTUBDLL_API bool CanMacroTune(void);
TUNERSTUBDLL_API void MacroTune(int);

TUNERSTUBDLL_API bool NeedBitrate(void);
TUNERSTUBDLL_API bool NeedCarrierFrequency(void);
TUNERSTUBDLL_API const char* DeviceName();
TUNERSTUBDLL_API bool OpenDevice(int ComPort);
TUNERSTUBDLL_API void CloseDevice();
TUNERSTUBDLL_API unsigned long FindBitRate(void);
TUNERSTUBDLL_API unsigned long FindCarrierFrequency(void);
TUNERSTUBDLL_API struct remote *CreateRemote(unsigned char *Name, unsigned long Carrier_Freq, unsigned long Bit_Time, command *Comm_List);
TUNERSTUBDLL_API struct command *CreateCommand(unsigned char *Name, struct pattern *pat_list);
TUNERSTUBDLL_API void AddRemote(struct remote *Remote, struct remote **head);
TUNERSTUBDLL_API void AddCommand(struct command *Command, struct command **Command_List);
TUNERSTUBDLL_API void SaveRemotes(remote *head, const char* pszPathName);
TUNERSTUBDLL_API struct remote *LoadRemotes(const char* pszPathName);
TUNERSTUBDLL_API void InitDevice();
TUNERSTUBDLL_API command* RecordCommand(unsigned char *Name);
TUNERSTUBDLL_API void PlayCommand (remote *remote, unsigned char *name, int tx_repeats);
TUNERSTUBDLL_API void FreeRemotes(remote **head);

#ifdef __cplusplus
}
#endif