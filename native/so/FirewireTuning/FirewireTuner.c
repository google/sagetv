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
#include <stdio.h>
#include <stdarg.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/errno.h>
#include <unistd.h>
#include <features.h> 
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <getopt.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>
#include <linux/types.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <libraw1394/raw1394.h>
#include <libavc1394/avc1394.h>
#include <libiec61883/iec61883.h>
#include <libraw1394/csr.h>
#include "FirewireTuner.h"

// Exposes the devices as remotes instead...
#define RemoteInterface
#define RemoteDebug

static void DebugLogging(const char* cstr, ...)
{
#ifdef RemoteDebug
	va_list args;
	FILE *logfile=fopen("firewire.log","a");
	va_start(args, cstr);
	vfprintf(logfile, cstr, args);
	va_end(args);
	fclose(logfile);
#endif
}

static char *newstr(char *str)
{
    char *str2 = (char *) malloc(strlen(str)+1);
    if(str2==NULL) return NULL;
    memset(str2,0,strlen(str)+1);
    strcpy(str2,str);
    return str2;
}

#ifdef RemoteInterface
octlet_t tunerguid;
#endif

typedef struct FirewireTunerDev
{
	raw1394handle_t handle;
	octlet_t guid;
	int port;
	int node;
} FirewireTunerDev;

#define AVC1394_PASSTHROUGH_COMMAND 0x000007C00   /* PASS THROUGH subunit command */

static octlet_t get_guid(raw1394handle_t handle, nodeid_t node)
{
        quadlet_t       quadlet;
        octlet_t        offset;
        octlet_t    guid = 0;

        offset = CSR_REGISTER_BASE + CSR_CONFIG_ROM + 0x0C;
        raw1394_read(handle, node, offset, sizeof(quadlet_t), &quadlet);
        quadlet = htonl(quadlet);
        guid = quadlet;
        guid <<= 32;
        offset = CSR_REGISTER_BASE + CSR_CONFIG_ROM + 0x10;
        raw1394_read(handle, node, offset, sizeof(quadlet_t), &quadlet);
        quadlet = htonl(quadlet);
        guid += quadlet;

        return guid;
}

#ifdef RemoteInterface
static FirewireTunerDev * createTuner(octlet_t targetguid)
#else
static FirewireTunerDev * createTuner(int tuningdevice)
#endif
{
	struct raw1394_portinfo portinfo[16];
	int nports, port, device;
	//quadlet_t guid_lo, guid_hi;
	FirewireTunerDev *CDev = (FirewireTunerDev *) malloc(sizeof(FirewireTunerDev));
	
	if(!(CDev->handle = raw1394_new_handle())) 
	{
		free(CDev);
		return 0;
	}

	if((nports = raw1394_get_port_info(CDev->handle, portinfo, 16)) < 0)
	{
		raw1394_destroy_handle(CDev->handle);
		free(CDev);
		return 0;
	}

	CDev->node = -1;
	for(port = 0; port < nports; port++)
	{
        raw1394handle_t tmphandle=raw1394_new_handle();
        if(!tmphandle)
        {
            continue;
        }
        if(raw1394_set_port(tmphandle, port) < 0) 
        {
            raw1394_destroy_handle(tmphandle);
            continue;
        }
    
		for(device = 0; device < raw1394_get_nodecount(tmphandle); device++)
		{
#ifdef RemoteInterface
			octlet_t guid = get_guid(tmphandle, 0xffc0 | device);
			if((device&0x3f) != (raw1394_get_local_id(tmphandle) & 0x3f))
			{
				if(targetguid==guid)
				{
					CDev->node=0xffc0|device;
					CDev->port=port;
				}
			}
#else
			//octlet_t guid = get_guid(tmphandle, 0xffc0 | device);
			if((device&0x3f) != (raw1394_get_local_id(tmphandle) & 0x3f))
			{
				if(tuningdevice==0)
				{
					CDev->node=0xffc0|device;
					CDev->port=port;
				}
				tuningdevice-=1;
			}
#endif
		}
        raw1394_destroy_handle(tmphandle);
	}

    // We couldn't find our device
    if(CDev->node == -1)
	{
		raw1394_destroy_handle(CDev->handle);
		free(CDev);
		return 0;
	}
	raw1394_set_port(CDev->handle, CDev->port); 
	return CDev;
}

static void closeTuner(FirewireTunerDev *CDev)
{
    raw1394_destroy_handle(CDev->handle);
    free(CDev);
}

static int setChannel(FirewireTunerDev *CDev, int channel)
{
    DebugLogging("before power\n");
	{
		quadlet_t request[3];
		quadlet_t *response;
        
		request[0] = AVC1394_CTYPE_CONTROL | AVC1394_SUBUNIT_TYPE_UNIT | 
			AVC1394_SUBUNIT_ID_IGNORE | AVC1394_COMMAND_POWER | AVC1394_CMD_OPERAND_POWER_ON;
		// power up the unit        
        DebugLogging("avc1394 transaction %X %X %X\n",CDev->handle, CDev->node&0x3F,request[0]);
		response = avc1394_transaction_block(CDev->handle, CDev->node&0x3F, request, 1, 1);
		if(response!=NULL)
		DebugLogging("power response %08X\n",response[0]);
	}
    DebugLogging("after power\nbefore press\n");
	{
		quadlet_t request[3];
		quadlet_t *response;
        
		request[0] = AVC1394_CTYPE_CONTROL | AVC1394_SUBUNIT_TYPE_PANEL | 
			AVC1394_SUBUNIT_ID_0 | AVC1394_PASSTHROUGH_COMMAND | 
			0x67; // Press
		request[1] = 0x040000FF | (channel << 8);
		request[2] = 0xFF000000;
		// set the channel
		response = avc1394_transaction_block(CDev->handle, CDev->node&0x3F, request, 3, 1);
		if(response!=NULL)
		DebugLogging("67 push response %08X\n",response[0]);
        
        DebugLogging("after push \n");
		request[0] = AVC1394_CTYPE_CONTROL | AVC1394_SUBUNIT_TYPE_PANEL | 
			AVC1394_SUBUNIT_ID_0 | AVC1394_PASSTHROUGH_COMMAND | 
			0x67|0x80; // Release
		request[1] = 0x040000FF | (channel << 8);
		request[2] = 0xFF000000;
		// set the channel
		response = avc1394_transaction_block(CDev->handle, CDev->node&0x3F, request, 3, 1);
		if(response!=NULL)
		DebugLogging("67 release response %08X\n",response[0]);
	}
    DebugLogging("set channel done\n");
	return 1;
}


static int AddFirewireNodes(remote **head)
{
	raw1394handle_t handle;
	int device;
	int nports, port;
	struct raw1394_portinfo portinfo[16];
	char nodename[256];

	if(!(handle = raw1394_new_handle())) 
	{
		return 0;
	}

	if((nports = raw1394_get_port_info(handle, portinfo, 16)) < 0)
	{
		raw1394_destroy_handle(handle);
		return 0;
	}

	for(port = 0; port < nports; port++)
	{
        raw1394handle_t tmphandle=raw1394_new_handle();
        if(!tmphandle)
        {
            continue;
        }
		if(raw1394_set_port(tmphandle, port) < 0) 
		{
            raw1394_destroy_handle(tmphandle);
            continue;
		}

		for(device = 0; device < raw1394_get_nodecount(tmphandle); device++)
		{
			octlet_t guid = get_guid(tmphandle, 0xffc0 | device);
			if((device&0x3f) != (raw1394_get_local_id(tmphandle) & 0x3f))
			{
				sprintf(nodename, "Firewire STB %08X%08X", 
					(quadlet_t) (guid>>32), (quadlet_t) (guid & 0xffffffff));
				DebugLogging("Adding remote %s\n", nodename);
				AddRemote(CreateRemote(newstr(nodename)), head);
			}
		}
        raw1394_destroy_handle(tmphandle);
	}
	raw1394_destroy_handle(handle);
	return 0;
}

typedef struct {
    int devicenum;
}FirewireData;

int NeedBitrate(void)
{
    return 0;
}

int NeedCarrierFrequency(void)
{
    return 0;
}

const char* DeviceName() 
{ 
    return "Firewire Tuner";
}

int OpenDevice(int ComPort) 
{
    FirewireData *irb = malloc(sizeof(FirewireData));
    if(irb==NULL) return (int) irb;
    DebugLogging("Opening with comport %d\n",ComPort);
    irb->devicenum=ComPort;
    return (int) irb; 
}

void CloseDevice(int devHandle) 
{
    if(devHandle==0) return;
    FirewireData *irb=(FirewireData *) devHandle;
    free(irb);
}

unsigned long FindBitRate(int devhandle)
{
    return 0;
}

unsigned long FindCarrierFrequency(int devHandle)
{
    return 0;
}

remote* CreateRemote(char *Name)
{
    remote *Remote;
    
    Remote = (struct remote*)malloc(sizeof(struct remote));  //allocate space for a remote structure
    if (Remote == NULL)
    {
        ////TRACE("malloc failed in CREATE_REMOTE()...Exiting!!!\n");
        //PostQuitMessage(0);    
        return Remote;
    }    
    Remote->name = Name;                 //copy values
    Remote->carrier_freq = 0;
    Remote->bit_time = 0;
    Remote->command = NULL;
    Remote->next = NULL;
    return Remote;                       //return pointer to remote structure    
}    

command* CreateCommand(char *Name)
{
    struct command *Com;   //pointer to new command structure
    
    Com = (struct command*)malloc(sizeof(struct command));//allocate space for a command structure
    if (Com == NULL)
    {
        ////TRACE("malloc failed in CREATE_COMMAND()...Exiting!!!\n");
        //PostQuitMessage(0);    
        return Com;
    }    
    Com->name = Name;                                     //copy values
    Com->next = NULL;
    Com->pattern = NULL;
    return Com;                                           //return pointer to new command structure
}        

void AddRemote(struct remote *Remote, struct remote **head)
{
    struct remote *Temp;           //Local remote structure
    
    if (!(*head))
    {                              //if there are no structures in the list
        *head = Remote;            //then assign this one to head.
        (*head)->next = NULL;
    }
    else                           //otherwise, add to end of list
    {
        Temp = *head;
        while (Temp->next)
        {
            Temp = Temp->next;     //find the last structure in list
        }
        Temp->next=Remote;        //assign the next field to the new structure
        Temp->next->next = NULL;  //assign the next field of the new structure to NULL
    }
}

void AddCommand(struct command *Command, struct command **Command_List)
{
    struct command *Temp;            //temporary command structure pointer
    
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


remote* LoadRemotes(const char* pszPathName)
{
    DebugLogging("LoadRemotes\n");
    remote *head = NULL;
    if (pszPathName)
    {
        DebugLogging("LoadRemotes %s\n",pszPathName);
        AddRemote(CreateRemote(newstr((char *)pszPathName)), &head);
        AddCommand(CreateCommand(newstr("0")),&(head->command));
        AddCommand(CreateCommand(newstr("1")),&(head->command));
        AddCommand(CreateCommand(newstr("2")),&(head->command));
        AddCommand(CreateCommand(newstr("3")),&(head->command));
        AddCommand(CreateCommand(newstr("4")),&(head->command));
        AddCommand(CreateCommand(newstr("5")),&(head->command));
        AddCommand(CreateCommand(newstr("6")),&(head->command));
        AddCommand(CreateCommand(newstr("7")),&(head->command));
        AddCommand(CreateCommand(newstr("8")),&(head->command));
        AddCommand(CreateCommand(newstr("9")),&(head->command));
        AddCommand(CreateCommand(newstr("POWER")),&(head->command));
        AddCommand(CreateCommand(newstr("ENTER")),&(head->command));
#ifdef RemoteInterface
        {
            // We assume that the server won't issue more than 1 at a time
            quadlet_t quadlethi, quadletlo;
            sscanf(pszPathName, "%*s %*s %8x %8x", &quadlethi, &quadletlo);
            tunerguid=quadlethi;
            tunerguid<<=32LL;
            tunerguid|=quadletlo;
            DebugLogging("Found device guid %08X %08X\n",
                (quadlet_t) (tunerguid>>32), (quadlet_t) (tunerguid & 0xffffffff));
        }
#endif
        return head;
    }
#ifdef RemoteInterface
    // We must list the available devices as remotes
    AddFirewireNodes(&head);
#else
    AddRemote(CreateRemote(newstr("Firewire STB")), &head);
#endif
    return head;
}

void InitDevice()
{
}

command* RecordCommand(int devHandle, unsigned char *Name)
{
    return 0;
}

void send_command(char *cmd, char* recvBuf, int* recvSize) 
{
    DebugLogging("Send command %s\n",cmd);
}

void PlayCommand (int devHandle, remote *remote, unsigned char *name, int tx_repeats)
{
}

void FreeRemotes(remote **head)
{
    command *Temp_Com;        //temporary command pointer
    remote *Temp_Rem;        //temporary remote pointer
    pattern *temp_pat;        //temporary pattern pointer
    
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
        free(Temp_Rem->name);                         //free remote data
        free(Temp_Rem);    
    }
    *head = NULL;
}

int CanMacroTune(void)
{
    return 1;
}

void MacroTune(int devHandle, int channel)
{
    FirewireTunerDev *CDev;
    FirewireData *irb=(FirewireData *) devHandle;
    DebugLogging("MacroTune %d\n", channel);
    DebugLogging("devHandle %d\n", devHandle);
    if(devHandle==NULL) return;

#ifdef RemoteInterface
    CDev = createTuner(tunerguid);
#else
    CDev = createTuner(irb->devicenum);
#endif
    if(CDev==NULL)
    {
        DebugLogging("CDev is NULL\n");
        // TODO: We could try to reset firewire here...
        return;
    }
    setChannel(CDev,channel);
    closeTuner(CDev);
}

//#define TESTAPP
#ifdef TESTAPP
main(int argc, char **argv)
{
    int device=OpenDevice(0);
    LoadRemotes(argv[1]);
    MacroTune(device, 123);
    return 0;
}
#endif
