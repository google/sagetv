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
#include "PVR150Tuner.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/i2c-dev.h>
#include <linux/i2c.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

typedef struct {
  int i2cfile;
  unsigned char IRData[0x60 * 83];
} IRBlasterData;

typedef struct {
  char *keyname;
  int keynum;
} KeyMaps;

static KeyMaps Keys[] = {{"0", 0},  {"1", 1}, {"2", 2}, {"3", 3}, {"4", 4},
                         {"5", 5}, {"6", 6}, {"7", 7}, {"8", 8},  {"9", 9},
                         {"POWER", 10}, {"ENTER", 15},
                         {NULL, -1}};

static unsigned char IRBlasterInit[] = {
    0x60, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x0C, // 0x00
    0x00, 0x0C, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0x10
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0x20
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0x30
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0x40
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0x50
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00                                // 0x60
};

static void DebugLogging(const char* cstr, ...)
{
  // TODO implement logic to forward to log system
}

static void ACL_Delay(unsigned int delay) {
  struct timeval tv;
  int rv = 1;
  tv.tv_sec = delay / 1000000;
  tv.tv_usec = (delay % 1000000);
  errno = EINTR;
  while (rv != 0 && (errno == EINTR)) {
    errno = 0;
    rv = select(0, NULL, NULL, NULL, &tv);
  }
}


static int i2creadarray(int i2cfile, int addr, int len, unsigned char *array) {
  struct i2c_msg i2cmsg[2];
  struct i2c_rdwr_ioctl_data i2cmsgdata;

  i2cmsg[0].addr = 0x70;
  i2cmsg[0].flags = 0;
  i2cmsg[0].buf = (unsigned char *)&addr;
  i2cmsg[0].len = 1;

  i2cmsg[1].addr = 0x70;
  i2cmsg[1].flags = I2C_M_RD;
  i2cmsg[1].buf = array;
  i2cmsg[1].len = len;

  i2cmsgdata.msgs = i2cmsg;
  i2cmsgdata.nmsgs = 2;

  return ioctl(i2cfile, I2C_RDWR, &i2cmsgdata);
}

static int i2cwritearray(int i2cfile, int addr, int len, unsigned char *array) {
  unsigned char buf2[257];
  int i;
  buf2[0] = addr;
  for (i = 0; i < len; i++) buf2[i + 1] = array[i];

  struct i2c_msg i2cmsg[1];
  struct i2c_rdwr_ioctl_data i2cmsgdata;

  i2cmsg[0].addr = 0x70;
  i2cmsg[0].flags = 0;
  i2cmsg[0].buf = (unsigned char *)&buf2;
  i2cmsg[0].len = len + 1;

  i2cmsgdata.msgs = i2cmsg;
  i2cmsgdata.nmsgs = 1;

  return ioctl(i2cfile, I2C_RDWR, &i2cmsgdata);
}

static int writeI2CDataArray(int i2cfile, unsigned char *array, int len) {
  int i, j;
  for (i = 0; i < len; i += 4) {
    for (j = 0; j < 5; j++) {
      int shortlen = ((len - i) > 4) ? 4 : (len - i);
      if (i2cwritearray(i2cfile, 1 + i, shortlen, &array[i]) >= 0) {
        break;
      }
    }
  }
  return 0;
}

int NeedBitrate(void) { return 0; }

int NeedCarrierFrequency(void) { return 0; }

const char *DeviceName() { return "Infrared Blaster"; }

void *OpenDevice(int ComPort) {
  int i2cfile;
  int i2cnumber = ComPort;
  char i2cfilename[256];
  int res;

  snprintf(i2cfilename, sizeof(i2cfilename), "/dev/i2c/%d", i2cnumber);

  if ((i2cfile = open(i2cfilename, O_RDWR)) < 0) {
    snprintf(i2cfilename, sizeof(i2cfilename), "/dev/i2c-%d", i2cnumber);
    if ((i2cfile = open(i2cfilename, O_RDWR)) < 0) {
      DebugLogging("Could not open IrBlaster %d\n", ComPort);
      return (0);
    }
  }
  int addr = 0x70;
  if (ioctl(i2cfile, I2C_SLAVE, addr) < 0) {
    DebugLogging("error setting slave address\n");
    close(i2cfile);
    return (0);
  }

  writeI2CDataArray(i2cfile, &IRBlasterInit[0], 0x63);
  ACL_Delay(10 * 1000);
  int data = 0x20;
  res = i2cwritearray(i2cfile, 0, 1, (unsigned char *)&data);
  ACL_Delay(10 * 1000);
  res = 0;
  if (i2creadarray(i2cfile, 0, 4, (unsigned char *)&res) < 0) {
    DebugLogging("error reading back status\n");
    close(i2cfile);
    return (0);
  }
  DebugLogging("Initialized IRBlaster %08X\n", res);
  IRBlasterData *irb = malloc(sizeof(IRBlasterData));
  if (irb == NULL) return (void *)irb;
  memset(irb, 0, sizeof(*irb));

  irb->i2cfile = i2cfile;
  return (void *)irb;
}

void CloseDevice(void *devHandle) {
  if (devHandle == 0) {
    DebugLogging("CloseDevice handle is NULL\n");
    return;
  }
  IRBlasterData *irb = (IRBlasterData *)devHandle;
  close(irb->i2cfile);
  free(irb);
}

unsigned long FindBitRate(void *devhandle) { return 0; }

unsigned long FindCarrierFrequency(void *devHandle) { return 0; }

remote *CreateRemote(char *Name) {
  remote *Remote;

  Remote = (struct remote *)malloc(
      sizeof(struct remote));  // allocate space for a remote structure
  if (Remote == NULL) {
    return NULL;
  }
  Remote->name = Name;  // copy values
  Remote->carrier_freq = 0;
  Remote->bit_time = 0;
  Remote->command = NULL;
  Remote->next = NULL;
  return Remote;  // return pointer to remote structure
}

command *CreateCommand(char *Name) {
  struct command *Com;  // pointer to new command structure

  Com = (struct command *)malloc(
      sizeof(struct command));  // allocate space for a command structure
  if (Com == NULL) {
    return NULL;
  }
  Com->name = Name;  // copy values
  Com->next = NULL;
  Com->pattern = NULL;
  return Com;  // return pointer to new command structure
}

void AddRemote(struct remote *Remote, struct remote **head) {
  struct remote *Temp;  // Local remote structure

  if (!(*head)) {    // if there are no structures in the list
    *head = Remote;  // then assign this one to head.
    (*head)->next = NULL;
  } else  // otherwise, add to end of list
  {
    Temp = *head;
    while (Temp->next) {
      Temp = Temp->next;  // find the last structure in list
    }
    Temp->next = Remote;  // assign the next field to the new structure
    Temp->next->next =
        NULL;  // assign the next field of the new structure to NULL
  }
}

void AddCommand(struct command *Command, struct command **Command_List) {
  struct command *Temp;  // temporary command structure pointer

  if (!(*Command_List)) {       // if no commands in list, assign Command_List
    (*Command_List) = Command;  // to the command structure
    (*Command_List)->next = NULL;
  } else {
    Temp = (*Command_List);  // ELSE add to end of list of commands
    while (Temp->next) {
      Temp = Temp->next;
    }
    Temp->next = Command;
    Temp->next->next = NULL;
  }
}

static char *newstr(char *str) {
  char *str2 = (char *)malloc(strlen(str) + 1);
  if (str2 == NULL) return NULL;
  strcpy(str2, str);
  return str2;
}

remote *LoadRemotes(const char *pszPathName) {
  DebugLogging("LoadRemotes\n");
  remote *head = NULL;
  if (pszPathName) {
    AddRemote(CreateRemote(newstr((char *)pszPathName)), &head);
    AddCommand(CreateCommand(newstr("0")), &(head->command));
    AddCommand(CreateCommand(newstr("1")), &(head->command));
    AddCommand(CreateCommand(newstr("2")), &(head->command));
    AddCommand(CreateCommand(newstr("3")), &(head->command));
    AddCommand(CreateCommand(newstr("4")), &(head->command));
    AddCommand(CreateCommand(newstr("5")), &(head->command));
    AddCommand(CreateCommand(newstr("6")), &(head->command));
    AddCommand(CreateCommand(newstr("7")), &(head->command));
    AddCommand(CreateCommand(newstr("8")), &(head->command));
    AddCommand(CreateCommand(newstr("9")), &(head->command));
    AddCommand(CreateCommand(newstr("POWER")), &(head->command));
    AddCommand(CreateCommand(newstr("ENTER")), &(head->command));
    return head;
  }
  struct dirent *remoteentry;
  DIR *remotedir = opendir("remotes");

  if (remotedir == NULL) remotedir = opendir("/opt/sagetv/server/remotes");

  if (remotedir == NULL) return NULL;
  while ((remoteentry = readdir(remotedir)) != NULL) {
    if (strcmp(remoteentry->d_name, ".") != 0 &&
        strcmp(remoteentry->d_name, "..") != 0) {
      DebugLogging("adding remote %s\n", remoteentry->d_name);
      AddRemote(CreateRemote(newstr(remoteentry->d_name)), &head);
    }
  }
  closedir(remotedir);
  remotedir = NULL;
  return head;
}

void InitDevice() {}

command *RecordCommand(void *devHandle, char *Name) { return 0; }

void send_command(char *cmd, char *recvBuf, int *recvSize) {
  DebugLogging("Send command %s unimplemented\n", cmd);
}

void PlayCommand(void *devHandle, remote *remote, char *name,
                 int tx_repeats) {
  IRBlasterData *irb = (IRBlasterData *)devHandle;
  FILE *remotefile;

  int i2cfile;
  int codenum = -1;
  int pos = 0;
  int res, data;
  int repeat_instance;
  unsigned char ircode[0x63];
  char remotefilename[512];
  int maxcount = 0;
  if (devHandle == 0) return;

  i2cfile = irb->i2cfile;
  DebugLogging("PlayCommand %s\n", name);

  memset(ircode, 0, 0x63);

  while (Keys[pos].keyname != NULL) {
    if (strcmp(Keys[pos].keyname, name) == 0) {
      codenum = Keys[pos].keynum;
      break;
    }
    pos += 1;
  }

  if (codenum == -1) {
    DebugLogging("Could not find key name in remote list\n");
    return;
  }

  snprintf(remotefilename, 512, "remotes/%s", remote->name);
  remotefile = fopen(remotefilename, "rb");

  if (remotefile == NULL) {
    DebugLogging("Could not open remote file\n");
    return;
  }

  fseek(remotefile, 0x60 * codenum, SEEK_SET);

  if (fread(ircode, 0x60, 1, remotefile) != 1) {
    DebugLogging("Could not read ir code from remote file\n");
    fclose(remotefile);
    return;
  }

  fclose(remotefile);

  for (repeat_instance = 0; repeat_instance < tx_repeats; repeat_instance++) {
    writeI2CDataArray(i2cfile, &ircode[0], 0x63);

    ACL_Delay(10 * 1000);
    data = 0x40;
    res = i2cwritearray(i2cfile, 0, 1, (unsigned char *)&data);
    ACL_Delay(10 * 1000);
    res = 0;
    if (i2creadarray(i2cfile, 0, 1, (unsigned char *)&res) < 0) {
      DebugLogging("error writing i2c remote command\n");
    }
    ACL_Delay(10 * 1000);
    data = 0x80;
    res = i2cwritearray(i2cfile, 0, 1, (unsigned char *)&data);

    maxcount = 250;
    res = 0;
    while (i2creadarray(i2cfile, 0, 1, (unsigned char *)&res) < 0 && maxcount) {
      ACL_Delay(50 * 1000);
      maxcount -= 1;
    }
  }
}

void FreeRemotes(remote **head) {
  command *Temp_Com;  // temporary command pointer
  remote *Temp_Rem;   // temporary remote pointer
  pattern *temp_pat;  // temporary pattern pointer

  while (*head) {
    Temp_Rem = *head;
    Temp_Com = (*head)->command;
    while (Temp_Com) {
      (*head)->command = (*head)->command->next;
      temp_pat = Temp_Com->pattern;
      while (temp_pat) {
        Temp_Com->pattern = Temp_Com->pattern->next;
        free(temp_pat->bytes);
        free(temp_pat);
        temp_pat = Temp_Com->pattern;
      }
      free(Temp_Com->name);  // free command list
      free(Temp_Com);
      Temp_Com = (*head)->command;
    }
    (*head) = (*head)->next;
    free(Temp_Rem->name);  // free remote data
    free(Temp_Rem);
  }
  *head = NULL;
}

int CanMacroTune(void) { return 0; }

void MacroTune(int devHandle, int channel) { DebugLogging("MacroTune\n"); }
