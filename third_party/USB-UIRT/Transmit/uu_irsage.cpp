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

// uu_irsage.cpp : Defines the entry point for the DLL application.
//

#include "stdafx.h"
#include <stdio.h>
#include <malloc.h>
#include "uu_irsage.h"

#define LEARN_TIMEOUT 10000 // 10 seconds

#define Line_Length 2048

BOOL APIENTRY DllMain( HANDLE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
           )
{
    switch (ul_reason_for_call)
  {
    case DLL_PROCESS_ATTACH:
    case DLL_THREAD_ATTACH:
    case DLL_THREAD_DETACH:
    case DLL_PROCESS_DETACH:
      break;
    }
    return TRUE;
}


//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************

#include "uuirtdrv.h"


// Driver handle for UUIRT device
#define MAX_DEVICES 8
HUUHANDLE   hDrvHandle[MAX_DEVICES] = {NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL};
char *devNamePtr[MAX_DEVICES] = {NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL};
int devNum[MAX_DEVICES] = {0,0,0,0,0,0,0,0};
int loadCount = 0;
HINSTANCE   hinstLib = NULL;
unsigned  drvVersion;
unsigned  dllVersion;
char  gLearnBuffer[2048];
int Repeat_Override = 0;
int Device_Override = 0;

// UUIRT .dll funtion pointers. These will be assigned when calling LoadDLL()
pfn_UUIRTOpen       fnUUIRTOpen;
pfn_UUIRTClose        fnUUIRTClose;
pfn_UUIRTGetDrvInfo     fn_UUIRTGetDrvInfo;
pfn_UUIRTGetUUIRTInfo   fn_UUIRTGetUUIRTInfo;
pfn_UUIRTGetUUIRTConfig   fn_UUIRTGetUUIRTConfig;
pfn_UUIRTSetUUIRTConfig   fn_UUIRTSetUUIRTConfig;
pfn_UUIRTSetReceiveCallback fn_UUIRTSetReceiveCallback;
pfn_UUIRTTransmitIR     fn_UUIRTTransmitIR;
pfn_UUIRTLearnIR      fn_UUIRTLearnIR;
pfn_UUIRTGetDrvVersion    fn_UUIRTGetDrvVersion;
pfn_UUIRTOpenEx       fnUUIRTOpenEx;


/*****************************************************************************/
/* unLoadDLL: Disconnects from .DLL and unloads it from memory         */
/*                                       */
/* returns: none                               */
/*                                       */
/*****************************************************************************/
void unLoadDLL(void)
{
  if (hinstLib)
  {
    loadCount--;

    if (!loadCount)
    {
      FreeLibrary(hinstLib);
      hinstLib = NULL;
    }
  }
}

/*****************************************************************************/
/* loadDLL: Establish contact with the UUIRTDRV dll and assign function      */
/*      entry points                           */
/*                                       */
/* returns: TRUE on success, FALSE on failure                */
/*                                       */
/*****************************************************************************/
BOOL loadDLL(void)
{
    // Get a handle to the DLL module.

  if (hinstLib)
  {
    loadCount++;
    return TRUE;
  }
  loadCount = 0;

    hinstLib = LoadLibrary("uuirtdrv");

    // If the handle is valid, try to get the function address.

    if (hinstLib != NULL)
    {
        fnUUIRTOpen = (pfn_UUIRTOpen) GetProcAddress(hinstLib, "UUIRTOpen");
        fnUUIRTClose = (pfn_UUIRTClose) GetProcAddress(hinstLib, "UUIRTClose");
    fn_UUIRTGetDrvInfo  = (pfn_UUIRTGetDrvInfo) GetProcAddress(hinstLib, "UUIRTGetDrvInfo");
    fn_UUIRTGetUUIRTInfo = (pfn_UUIRTGetUUIRTInfo) GetProcAddress(hinstLib, "UUIRTGetUUIRTInfo");
    fn_UUIRTGetUUIRTConfig = (pfn_UUIRTGetUUIRTConfig) GetProcAddress(hinstLib, "UUIRTGetUUIRTConfig");
    fn_UUIRTSetUUIRTConfig = (pfn_UUIRTSetUUIRTConfig) GetProcAddress(hinstLib, "UUIRTSetUUIRTConfig");
    fn_UUIRTSetReceiveCallback = (pfn_UUIRTSetReceiveCallback) GetProcAddress(hinstLib, "UUIRTSetReceiveCallback");
    fn_UUIRTTransmitIR = (pfn_UUIRTTransmitIR) GetProcAddress(hinstLib, "UUIRTTransmitIR");
    fn_UUIRTLearnIR = (pfn_UUIRTLearnIR) GetProcAddress(hinstLib, "UUIRTLearnIR");
    fn_UUIRTGetDrvVersion = (pfn_UUIRTGetDrvVersion) GetProcAddress(hinstLib, "UUIRTGetDrvVersion");
    fnUUIRTOpenEx = NULL;
        fnUUIRTOpenEx = (pfn_UUIRTOpenEx) GetProcAddress(hinstLib, "UUIRTOpenEx");

    if (!fnUUIRTOpen ||
      !fnUUIRTClose ||
      !fn_UUIRTGetDrvInfo ||
      !fn_UUIRTGetUUIRTInfo ||
      !fn_UUIRTGetUUIRTConfig ||
      !fn_UUIRTSetUUIRTConfig ||
      !fn_UUIRTSetReceiveCallback ||
      !fn_UUIRTTransmitIR ||
      !fn_UUIRTLearnIR ||
      !fn_UUIRTGetDrvVersion
)
    {
      unLoadDLL();
      return FALSE;
    }

    return TRUE;
  }
  return FALSE;
}

//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************
//************************************************************************************************************






void Add_Remote(remote **head, FILE *fp, char *Temp_String);
command* Create_Command_List(FILE *fp);
pattern* create_pat_list(FILE *fp, char *pat_str);


FILE *logfp=NULL;
static void DbgLog(const char* cstr, ...)
{
  va_list args;

  if ( logfp==NULL ) return;

  va_start(args, cstr);
  vfprintf( logfp, cstr, args );
  va_end(args);
  fflush(logfp);
}


int getDevNo(remote *rmt)
{
  int i;

  for (i=0; i<MAX_DEVICES; i++)
  {
    if ((devNamePtr[i]) && (!strcmp(devNamePtr[i], (char *)rmt->name)))
    {
      DbgLog("GetDevNo(%s)=%d\n",rmt->name,devNum[i]);
      return devNum[i];
    }
  }
  DbgLog("GetDevNo(%s)=NOT FOUND\n",rmt->name);
  return 0;
}

void setDevNo(remote *rmt, int devNo)
{
  int i;

  DbgLog("SetDevNo(%s,%d)\n", (char *)rmt->name, devNo);

  for (i=0; i<MAX_DEVICES; i++)
  {
    if ((devNamePtr[i]) && (!strcmp(devNamePtr[i], (char *)rmt->name)))
    {
      devNum[i] = devNo;
      return;
    }
  }
  for (i=0; i<MAX_DEVICES; i++)
  {
    if (!devNamePtr[i])
    {
      devNamePtr[i] = _strdup((char *)rmt->name);
      devNum[i] = devNo;
      return;
    }
  }
}

void clearDevNo(remote *rmt)
{
  int i;

  for (i=0; i<MAX_DEVICES; i++)
  {
    if ((devNamePtr[i]) && (!strcmp(devNamePtr[i], (char *)rmt->name)))
    {
      free(devNamePtr[i]);
      devNamePtr[i] = NULL;
      return;
    }
  }
}


command* CreateCommand(unsigned char *Name, struct pattern *pat_list)
{
  struct command *Com;   //pointer to new command structure

  Com = (struct command*) malloc(sizeof(struct command));//allocate space for a command structure
  if (Com != NULL)
  {
    Com->name = Name;                                     //copy values
    Com->pattern = pat_list;
  }

  return Com;                                           //return pointer to new command structure
}

TUNERSTUBDLL_API bool NeedBitrate(void)
{
  return false;
}

TUNERSTUBDLL_API bool NeedCarrierFrequency(void)
{
  return false;
}

TUNERSTUBDLL_API const char* DeviceName()
{
  return "USB-UIRT Transceiver";
}

bool openHelper(int deviceNum)
{
  char *errStr = NULL;

  char devName[256];

  DbgLog("OpenDevice: dev #%d\n",deviceNum);

  if ((deviceNum < 0) || (deviceNum > 7))
    return false;

  if ((hDrvHandle[deviceNum]) && (hDrvHandle[deviceNum] != INVALID_HANDLE_VALUE))
    return true;

  while (1)
  {
    if (!loadDLL())
    {
      errStr = "Error: Unable to find USB-UIRT Driver. Please make sure driver is Installed!";
      break;
    }

    if (!fn_UUIRTGetDrvInfo(&drvVersion))
    {
      errStr = "Error: Unable to retrieve USB-UIRT Driver Version!";
      unLoadDLL();
      break;
    }

    if ((!fn_UUIRTGetDrvInfo(&drvVersion)) || (drvVersion != 0x0100))
    {
      errStr = "Error: USB-UIRT Driver Version (uuirtdrv.dll) is incompatible with this plugin. Please make sure you are using the latest version of plugin and driver!";
      unLoadDLL();
      break;
    }

    dllVersion = 0;
    if (fnUUIRTOpenEx)
      fn_UUIRTGetDrvVersion(&dllVersion);
    if ((!fnUUIRTOpenEx) || (dllVersion < ((deviceNum > 0) ? (unsigned) 2670 : (unsigned) 2640)))
    {
      errStr = "Error: USB-UIRT Driver Version (uuirtdrv.dll) is incompatible with this plugin. Please make sure you are using version 2.6.4 or later of the USB-UIRT API driver!";
      MessageBox(0, errStr, "Error", MB_OK);
      unLoadDLL();
      break;
    }


    if (deviceNum)
      sprintf_s(devName, 256, "USB-UIRT-%d", deviceNum+1);
    else
      strcpy_s(devName, 256, "USB-UIRT");

    hDrvHandle[deviceNum] = fnUUIRTOpenEx(devName, 0, NULL, NULL);
    if (hDrvHandle[deviceNum] == INVALID_HANDLE_VALUE)
    {
      DWORD err;

      err = GetLastError();

      if (err == UUIRTDRV_ERR_NO_DLL)
      {
        errStr = "Error: Unable to find USB-UIRT Driver. Please make sure driver is Installed!";
      }
      else if (err == UUIRTDRV_ERR_NO_DEVICE)
      {
        errStr = "Error: Unable to connect to USB-UIRT device!  Please ensure device is connected to the computer!\n";
      }
      else if (err == UUIRTDRV_ERR_NO_RESP)
      {
        errStr = "Error: Unable to communicate with USB-UIRT device!  Please check connections and try again.  If you still have problems, try unplugging and reconnecting your USB-UIRT.  If problem persists, contact Technical Support!\n";
      }
      else if (err == UUIRTDRV_ERR_VERSION)
      {
        errStr = "Error: Your USB-UIRT's firmware is not compatible with the USB-UIRT driver installed on this machine. Please verify you are running the latest API DLL and that you're using the latest version of USB-UIRT firmware!  If problem persists, contact Technical Support!\n";
      }
      else
      {
        static char myStr[256];

        sprintf_s(myStr,256, "Error: Unable to initialize USB-UIRT (unknown error) [%08lx]!\n",err);
        DbgLog(myStr);
        errStr = myStr;
      }

      unLoadDLL();

      break;
    }

    break;
  }

  if (errStr)
  {
    return false;
  }

  return true;
}


TUNERSTUBDLL_API bool OpenDevice(int ComPort)
{
  FILE *fp = fopen("UU_LOG.ENABLE", "r"); // check if logging is enabled
  if (fp != NULL)
  {
    fclose(fp);
    logfp = fopen("uuirtlog.txt", "w");
  }

  return openHelper(0);
}



void closeHelper(int deviceNum)
{
  DbgLog("CloseDevice: dev #%d\n", deviceNum);

  if ((hDrvHandle[deviceNum]) && (hDrvHandle[deviceNum] != INVALID_HANDLE_VALUE))
    fnUUIRTClose(hDrvHandle[deviceNum]);
  {
    hDrvHandle[deviceNum] = NULL;

    unLoadDLL();
  }
}

TUNERSTUBDLL_API void CloseDevice()
{
  int i;

  for (i=0; i<MAX_DEVICES; i++)
    closeHelper(i);
  if (logfp) fclose(logfp);
}

TUNERSTUBDLL_API unsigned long FindBitRate(void)
{
  DbgLog("FindBitRate:\n");

  return 0;
}

TUNERSTUBDLL_API unsigned long FindCarrierFrequency(void)
{
  DbgLog("FindCarrierFrequency:\n");

  return 0;
}

TUNERSTUBDLL_API void  AddRemote(struct remote *Remote, struct remote **head)
{
  struct remote *Temp;           //Local remote structure

  DbgLog("AddRemote:\n");
//return;
  if (!(*head))
  {                              //if there are no structures in the list
  DbgLog("AddRemote -- First\n");
    *head = Remote;            //then assign this one to head.
    (*head)->next = NULL;
  }
  else                           //otherwise, add to end of list
  {
  DbgLog("AddRemote -- !First\n");
    Temp = *head;
    while (Temp->next)
    {
      Temp = Temp->next;     //find the last structure in list
    }
    Temp->next=Remote;        //assign the next field to the new structure
    Temp->next->next = NULL;  //assign the next field of the new structure to NULL
  }
  DbgLog("AddRemote -- Return\n");
}

TUNERSTUBDLL_API void AddCommand(struct command *Command, struct command **Command_List)
{
  struct command *Temp;            //temporary command structure pointer

  DbgLog("AddCommand: %s\n", (char *)Command->name);

    if (!(*Command_List))
  {                                //if no commands in list, assign Command_List
    (*Command_List) = Command;     //to the command structure
    (*Command_List)->next = NULL;
  }
  else
  {
    Temp = (*Command_List);        //ELSE add to end of list of commands
    while (Temp->next)
    {
      Temp = Temp->next;
    }
    Temp->next=Command;
    Temp->next->next = NULL;
  }
}

TUNERSTUBDLL_API void SaveRemotes(remote *head, const char* pszPathName)
{
  command *Comm_List; //local copy of the pointer to the command list
                      //for each remote
  pattern *pat_list;  //temporary pointer to pattern nodes
  FILE *fp;

  DbgLog("SaveRemotes: %s\n", pszPathName);

  fp = fopen(pszPathName, "wt");//opening output file
  if(fp == NULL)
  {
    return;
  }
  while(head)
  {
    fprintf(fp,"%s ", head->name);          //write remote data
    fprintf(fp,"%lu ", head->carrier_freq);
    fprintf(fp,"%u\n", head->bit_time);
    if ((Device_Override > 0) && (Device_Override <= 8))
      fprintf(fp,"DeviceNumber %d\n",Device_Override);
    if ((Repeat_Override > 0) && (Repeat_Override < 64))
      fprintf(fp,"RepeatCount %d\n",Repeat_Override);
    Comm_List=head->command;
    while(Comm_List)
    {
      fprintf(fp,"%s", Comm_List->name);
      pat_list = Comm_List->pattern;
      while(pat_list)
      {
        fprintf(fp," %s", pat_list->bytes);
        fprintf(fp,"\n");
        pat_list = pat_list->next;
      }
      Comm_List = Comm_List->next;
    }
    fprintf(fp,"\n");
    head = head->next;
  }
  fclose(fp);
}

static char loadedDevName[256];
TUNERSTUBDLL_API remote*  LoadRemotes(const char* pszPathName)
{
  char Temp_String[Line_Length];
  remote *head;
  FILE *fp;
  char *p;

  DbgLog("LoadRemotes: %s\n", pszPathName);

  loadedDevName[0] = '\0';
  if(pszPathName == NULL)
    return NULL;

  fp = fopen(pszPathName, "r");       //open data file
  if (fp == NULL)
  {
    fp = fopen(pszPathName, "w");
    if (fp == NULL)
    {
      return NULL;
    }
    fclose(fp);
    fp = fopen(pszPathName, "r");
    if (fp == NULL)
    {
      return NULL;
    }
  }

  const char* devNameStart = pszPathName;
  const char* slashIdx = strstr(devNameStart, "\\");
  while (slashIdx)
  {
    devNameStart = slashIdx + 1;
    slashIdx = strstr(devNameStart, "\\");
  }
  strcpy_s(loadedDevName, 256, devNameStart);
  p=strrchr(loadedDevName, '.');
  if (p) *p = '\0'; // remove the file extension (ir)

  head = NULL;                                    //initialize head of list
  while (!feof(fp))                               //while not end of file
  {
    if (fgets((char*)Temp_String, sizeof(Temp_String), fp) != NULL) //get a line from file with remote name, carrier freq. & bit time
      Add_Remote(&head, fp, Temp_String);         //add a remote to list
  }
  fclose(fp);

  if (Device_Override)
  {
    // Make sure this device exists and is operational!
    if (!openHelper(Device_Override-1))
    {
      DbgLog("openHelper FAILED! -- returning NULL\n");
      FreeRemotes(&head);
      head = NULL;
    }
  }

  DbgLog("Head = %p\n",head);

  return head;                                    //return pointer to list of remotes
}


remote* CreateRemote(unsigned char *Name, unsigned long Carrier_Freq, unsigned long Bit_Time, struct command *Comm_List)
{
  remote *Remote;

  DbgLog("CreateRemote:\n");

  Remote = (struct remote*) malloc(sizeof(struct remote));  //allocate space for a remote structure
  if (Remote != NULL)
  {
    Remote->name = Name;                 //copy values
    Remote->carrier_freq = Carrier_Freq;
    Remote->bit_time = Bit_Time;
    Remote->command = Comm_List;
    Remote->next = NULL;
    setDevNo(Remote,0);
  }

  return Remote;                       //return pointer to remote structure

}


//%%%%%%%%%%%%%%%%%%%Add_Remote%%%%%%%%%%%%%%%%%%//
//Accepts:  Pointer to Pointer to remote structure.
//      File pointer to data file. Pointer to
//      array containing a line from the data file.
//Function: Calls Create_Remote to allocate space for a
//      remote structure with data elements read in.
//      Calls Add_Remote to add structure to
//      a linked list of Remotes.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
void Add_Remote(remote **head, FILE *fp, char *remote_str)
{
  unsigned char *Name;       //name of remote
  unsigned long Carrier_Freq=0;
  unsigned Bit_Time=0;
  command *Comm_List;                 //local pointer to list of commands for remote
  remote *Remote;                     //local pointer to a remote structure
  char temp_name[256];

  DbgLog("Add_Remote: %s\n", remote_str);

  if (sscanf(remote_str, "%255s %lu %u", temp_name, &Carrier_Freq, &Bit_Time) != 3)
  {
    DbgLog("Improper remote definition: %s\n", remote_str);
    return;
  }
  DbgLog("Add_Remote: name=%s Carrier_Freq=%lu Bit_time=%u\n", temp_name, Carrier_Freq, Bit_Time);

  Name = (unsigned char *)_strdup(temp_name);

  Repeat_Override = 0;
  Device_Override = 0;

  Comm_List = Create_Command_List(fp); //get a pointer to list of commands for remote
                                       //space for commands will be allocated on heap
  Remote = CreateRemote(Name, Carrier_Freq, Bit_Time, Comm_List); //allocate space for a remote and copy values
  AddRemote(Remote, head);    //add remote to list of remotes

  setDevNo(Remote, Device_Override);
}


//%%%%%%%%%%%%%%%%%%%%%Create_Command_List%%%%%%%%%%%%%%//
//Accepts:  FILE pointer to data file.                                   
//Returns:  Pointer to a linked list of commands
//Function: Gets a line from the data file and reads the command
//      name.  The command bytes are copied to a temporary array
//      until Ptr==NULL. The actual command bytes are read into the
//      array. Calls Create_Command which returns a pointer to a
//      command structure on the heap.  Then Add_Command_To_List
//      is called to add the command to linked list of commands.
//      Lines are read from the data file and put into the list
//      of Commands until a blank line is encountered.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
command* Create_Command_List(FILE *fp)
{
  char *Ptr;                     //temporary pointer
  char Temp_String[Line_Length]; //buffer for lines of data file
  struct pattern *pat_list;
  unsigned char *Name;           //name of command
  struct command *Command_List;  //command list pointer
  struct command *Com;           //temporary command pointer

  Command_List = NULL;
  fgets(Temp_String, sizeof(Temp_String), fp);
  while (!feof(fp) && (strlen(Temp_String) > 1))
  {
    if (_strnicmp(Temp_String,"DeviceNumber",12) == 0)
    {
      sscanf(&Temp_String[12], "%u", &Device_Override);
      fgets(Temp_String, sizeof(Temp_String), fp);
      continue;
    }
    if (_strnicmp(Temp_String,"RepeatCount",11) == 0)
    {
        sscanf(&Temp_String[11], "%u", &Repeat_Override);
      fgets(Temp_String, sizeof(Temp_String), fp);
      continue;
    }
    Ptr = strtok(Temp_String, " \t");   //get line with command name and first command pattern
    Name = (unsigned char *)_strdup(Ptr);

    pat_list = create_pat_list(fp, Temp_String);

    Com = CreateCommand(Name, pat_list);

    AddCommand(Com, &Command_List);
  }

  return Command_List;
}


//%%%%%%%%%%%create_pat_list%%%%%%%%%%%//
//Accepts:  Pointer to file, pointer to first string in a byte buffer
//Returns:  Pointer to head pattern node.
//Function: Reads command bytes until the end
//      of line is reached. Keeps reading
//      lines of command bytes if the first
//      character is a blank. If it is not
//      a blank then it has encountered a
//      blank line (and the end of the command
//      list) or the first character of
//      the name of the next command.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
pattern* create_pat_list(FILE *fp, char *pat_str)
{
  struct pattern *pat_list;   //pointer to list of command patterns
  struct pattern *temp_list;  //temporary pointer to nodes in pattern list
  int bit_length=0;           //length of pattern in bits including carrier off sequence
  char r_flag='n';            //repeated pattern flag "r" yes, "n" no
  char *Ptr;

  Ptr = strtok(NULL, "\n\r");
  pat_list = (struct pattern*) malloc(sizeof(struct pattern));       //allocate for byte pattern structure
  temp_list = pat_list;                 //save head of pat list

  temp_list->bytes = (unsigned char *)_strdup(Ptr);       //allocate for command bytes
  temp_list->length = (unsigned) strlen((char *)temp_list->bytes)+1;
  temp_list->next = NULL;
  fgets(pat_str, Line_Length, fp); //get next line
  DbgLog("%s\n", temp_list->bytes);

  while(pat_str[0]==' ')          //get rest of command patterns (continued on additional lines)
  {
    Ptr = strtok(&pat_str[1], "\n\r");
    temp_list->next = (struct pattern*) malloc(sizeof(struct pattern));
    temp_list = temp_list->next;

    temp_list->bytes = (unsigned char *)_strdup(Ptr);           //allocate for command bytes
    temp_list->length = (unsigned) strlen((char *)temp_list->bytes)+1;
    temp_list->next = NULL;
    fgets(pat_str, Line_Length, fp);
    DbgLog("%s\n", temp_list->bytes);
  }
  return pat_list;
}

TUNERSTUBDLL_API void InitDevice()
{
  DbgLog("InitDevice:\n");
}

/*****************************************************************************/
/* IRLearnCallback: Learn IR Callback Procedure                    */
/*                                       */
/* This procedure is called by the UUIRT .dll during the LEARN process     */
/* to allow user feedback on Learn progress, signal quality and (if needed)  */
/* carrier frequency.                            */
/*                                       */
/*****************************************************************************/
void WINAPI IRLearnCallback (unsigned int progress, unsigned int sigQuality, unsigned long carrierFreq, void *userData)
{
//  printf("<Learn Progress: %d%%, Signal = %d%%, Freq = %ld, UserData = %08x!!!\n", progress, sigQuality & 0xff, carrierFreq, (UINT32)userData);
}


/*****************************************************************************/
/* LearnThread: Learn IR Thread function                   */
/*                                       */
/* This function executes as a separate thread which calls the UUIRTLearnIR  */
/* function.  In this example, the UUIRTLearnIR function is called from this */
/* separate thread to allow the main console thread to continue monitoring   */
/* the keyboard so that the user may abort the learn process. Depending on   */
/* the application, the UUIRTLearnIR may be called from the main thread if   */
/* an asynchronous method (such as a timer) is available to monitor user     */
/* input.                                  */
/*                                       */
/*****************************************************************************/
DWORD WINAPI LearnThread( LPVOID lpParameter )
{
  BOOL *pbAbortLearn = (BOOL *)lpParameter;

  printf("\nCalling LearnIR...");

#ifdef _WIN64
  #define LEARN_DATA 0x5a5a5a5a5a5a5a5a
#else
  #define LEARN_DATA 0x5a5a5a5a
#endif

  if (!fn_UUIRTLearnIR(hDrvHandle[0], UUIRTDRV_IRFMT_PRONTO, gLearnBuffer, IRLearnCallback, (void *)LEARN_DATA, pbAbortLearn, 0, NULL, NULL))
  {
    return 0;
    //printf("\n\t*** ERROR calling UUIRTLearnIR! ***\n");
  }
  else
  {
    if (!*pbAbortLearn)
    {
//      printf("...Done...IRCode = %s\n",gLearnBuffer);
//      strcpy(gIRCode, gLearnBuffer);
//      gIRCodeFormat = gLearnFormat & 0xff;
    }
    else
    {
//      printf("...*** LEARN ABORTED ***\n");
    }
  }
  return 0;
}


TUNERSTUBDLL_API command* RecordCommand(unsigned char *Name)
{
  HANDLE LearnThreadHandle;
  BOOL bLrnAbort = FALSE;
  DWORD dwThreadId;
  command *new_command;
  pattern *pat_list;
  int timeoutVal;

  DbgLog("RecordCommand:\n");

  // Launch Learning thread. We use a thread here so that we can continuously monitor user keyboard
  // input and abort the learn process if the user presses ESC. For a Windows GUI app, a thread
  // may not be necessary if another asynchronous method is available for the user to abort the learn
  // process.

  LearnThreadHandle = CreateThread(NULL,0,&LearnThread,(void *)&bLrnAbort,0,&dwThreadId);

 // printf("<Press ESC to abort Learn>");

  timeoutVal = LEARN_TIMEOUT;
  while (WaitForSingleObject(LearnThreadHandle, 100) == WAIT_TIMEOUT)
  {
    timeoutVal -= 100;
    if (timeoutVal<=0)
      bLrnAbort = TRUE;

 //   if (kbhit())
 //   {
 //     ch = getch();
 //     if (ch == 27) // ESC key
 //       bLrnAbort = TRUE;
 //   }
  }
  CloseHandle(LearnThreadHandle);


  if (!bLrnAbort)
  {
    DbgLog(">>RecordCommand: Command Received\n");

    new_command = (command *) malloc (sizeof (command));
    if (new_command == NULL)
    {
      ////TRACE("malloc failed in GET_COMMAND()...Exiting!!!\n");
      PostQuitMessage(0);
    }

    new_command->name = (unsigned char *)_strdup((char *)Name);
    new_command->next = NULL;

    pat_list = (struct pattern*) malloc(sizeof(struct pattern));
    if (pat_list != NULL)
    {
      pat_list->next = NULL;
      pat_list->bytes = (unsigned char *)_strdup(gLearnBuffer);
      pat_list->length = (int) strlen((char *)pat_list->bytes) + 1;
      new_command->pattern = pat_list;
    }

    return new_command;
  }

  return NULL;
}

//%%%%%%%%%%%%%%%%%%%Find_Command_By_Name%%%%%%%%%%%%%%%%%//
//Accepts:  Pointer to a list of commands.
//      A command name (char pointer).
//Returns:  Pointer to a command
//Function: Finds the command in the command list by comparing
//      names.  If the command is not found NULL pointer
//      is returned.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
command* Find_Command_By_Name(command *head, unsigned char *Com_Name)
{
  command *Com;     //Temporary command pointer

  Com = head;     //local head of list
  while (Com)      //Look for command in list
  {
    if ((strcmp((char*)Com->name, (char*)Com_Name))==0)
      break;      //Leave loop when command name is matched
    Com = Com->next;
  }
  return Com;
}


TUNERSTUBDLL_API void PlayCommand (remote *remote, unsigned char *name, int tx_repeats)
{

  command *command = NULL;
  struct pattern *temp;
  int devNo;


  if (!remote)
  {
    DbgLog("PlayCommand: NULL remote ptr!\n");
    return;
  }

  if ((!Repeat_Override) && (tx_repeats < 4))          tx_repeats = 4;
  if ((Repeat_Override > 0) && (Repeat_Override < 64)) tx_repeats = Repeat_Override;

  DbgLog("PlayCommand: Remote = %p",remote);
  DbgLog("PlayCommand: [%s:%s], tx_repeats = %d, dev_or = %d\n", remote->name, name, tx_repeats, getDevNo(remote));

  command = Find_Command_By_Name(remote->command, name);  //look for command by name
  if(!command)
  {
    DbgLog("PlayCommand: Command Not Found!\n");
    return;
  }

  devNo = getDevNo(remote);
  if (!hDrvHandle[(devNo)?(devNo-1):0])
  {
    DbgLog("PlayCommand: Null hDrvHandle!\n");
  }

  temp = command->pattern;        //save head of pattern list

  while(temp)
  {
    DbgLog("PlayCommand: Tx_Repeats = %d\n",tx_repeats);

    if (!fn_UUIRTTransmitIR(hDrvHandle[(devNo)?(devNo-1):0],
                (char *)temp->bytes /* IRCode */,
                UUIRTDRV_IRFMT_PRONTO /* codeFormat */,
                tx_repeats /* repeatCount */,
                0 /* inactivityWaitTime */,
                NULL /* hEvent */,
                NULL /* reserved1 */,
                NULL /* reserved2 */
                ))
    {
      DbgLog("PlayCommand: TransmitIR Failed:\n");
      DbgLog("PlayCommand: %s\n", temp->bytes);
    }
    temp = temp->next;
  }
  DbgLog("PlayCommand: DONE\n");
}


TUNERSTUBDLL_API void FreeRemotes(remote **head)
{
  command *Temp_Com;  //temporary command pointer
  remote *Temp_Rem;   //temporary remote pointer
  pattern *temp_pat;  //temporary pattern pointer

  DbgLog("FreeRemotes:\n");

  while(*head)
  {
    Temp_Rem = *head;
    Temp_Com = (*head)->command;
    while(Temp_Com)
    {
      (*head)->command = (*head)->command->next;
      temp_pat = Temp_Com->pattern;
      while(temp_pat)
      {
        Temp_Com->pattern = Temp_Com->pattern->next;
        free(temp_pat->bytes);
        free(temp_pat);
        temp_pat = Temp_Com->pattern;
      }
      free(Temp_Com->name);                     //free command list
      free(Temp_Com);
      Temp_Com = (*head)->command;
    }
    (*head) = (*head)->next;

    clearDevNo(Temp_Rem);
    free(Temp_Rem->name);                         //free remote data
      free(Temp_Rem);
  }
  *head = NULL;
}

TUNERSTUBDLL_API bool CanMacroTune(void)
{
  DbgLog("CanMacroTune:\n");
  return false;
}

TUNERSTUBDLL_API void MacroTune(int) {}
