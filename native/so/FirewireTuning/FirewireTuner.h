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

#ifdef __cplusplus
extern "C" {
#endif

struct pattern {
  unsigned bit_length;
  unsigned length;
  char r_flag;
  unsigned char *bytes;
  struct pattern *next;
};
typedef struct pattern pattern;

struct command {
  char *name;
  struct pattern *pattern;
  struct command *next;
};
typedef struct command command;

struct remote {
  char *name;
  unsigned long carrier_freq;
  unsigned bit_time;
  struct command *command;
  struct remote *next;
};

typedef struct remote remote;

int CanMacroTune(void);
void MacroTune(int, int);

int NeedBitrate(void);
int NeedCarrierFrequency(void);
const char *DeviceName();
int OpenDevice(int ComPort);
void CloseDevice(void *);
unsigned long FindBitRate(int);
unsigned long FindCarrierFrequency(int);
struct remote *CreateRemote(char *Name);
void AddRemote(struct remote *Remote, struct remote **head);
void AddCommand(struct command *Command, struct command **Command_List);
void SaveRemotes(remote *head, const char *pszPathName);
struct remote *LoadRemotes(const char *pszPathName);
void InitDevice();
command *RecordCommand(int devHandle, unsigned char *Name);
void PlayCommand(int, remote *remote, unsigned char *name, int tx_repeats);
void FreeRemotes(remote **head);

#ifdef __cplusplus
}
#endif
