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
// TunerStubDLL.cpp : Defines the entry point for the DLL application.
//

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <malloc.h>
#include <sys/socket.h>
#include <sys/un.h>
#include "LIRCTuner.h"

#define Line_Length 200
#define MAXDATASIZE 512
#define RC_CMD "/dev/remoted"

command*  CreateCommand(unsigned char *Name)
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

int NeedBitrate() { return 0; }
int NeedCarrierFrequency() { return 0; }

const char* DeviceName() { 
	return "USB-UIRT Transceiver";
}
int OpenDevice(int ComPort) 
{
	return 1; 
}
void CloseDevice(int devHandle)
{
}
unsigned long FindBitRate(int devHandle) { return 0; }
unsigned long FindCarrierFrequency(int devHandle) { return 0; }
void  AddRemote(struct remote *Remote, struct remote **head)
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
void  AddCommand(struct command *Command, struct command **Command_List)
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
/*void SaveRemotes(remote *head, const char* pszPathName)
{
	// We don't actually do anything here
}*/
remote*  LoadRemotes(const char* pszPathName)
{
	remote *head = NULL;

	int s, len;
	struct sockaddr_un saun;
	char buf[MAXDATASIZE];

	// Grab a socket. (Solaris style socket OK for intramachine
	// communication, as opposed to the method in server.c)

	if ((s = socket(AF_UNIX, SOCK_STREAM, 0)) < 0) {
		printf("Couldn't get a socket\n");
		return NULL;
	}

	// Create the address to communicate with, and direct it to the lircd device
	saun.sun_family = AF_UNIX;
	strcpy(saun.sun_path, RC_CMD);

	len = sizeof(saun.sun_family) + strlen(saun.sun_path);

	// Try to connect to lircd daemon
	if(connect(s,(struct sockaddr *)&saun, len) < 0) {
		printf("Couldn't connect to lircd daemon\n");
		return NULL;
	}

	struct timeval tv;
	tv.tv_sec = 3;
	tv.tv_usec = 0;
	setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
	setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

	if (pszPathName)
		sprintf(buf, "LIST %s\n", pszPathName);
	else
		sprintf(buf, "LIST\n");
	//  Send the command
	if(send(s, buf, strlen(buf), 0) < 0) {
		printf("Error sending %s\n", buf);
		close(s);
		return NULL;
	}

	int bufOffset = 0;
	int foundData = 0;
	int numDataLines = -1;
	int bufLen = 0;
	do
	{
		if (bufOffset > 0)
		{
			bufLen -= bufOffset;
			memcpy(buf, buf + bufOffset, bufLen);
			bufOffset = 0;
		}
		len = recv(s, buf + bufLen, MAXDATASIZE/2, 0);
		bufLen += len;
		bufOffset = 0;
		buf[bufLen] = 0;
		if (len == -1)
		{
			close(s);
			printf("Error receiving response from lircd daemon\n");
			return NULL;
		}
		if(strstr(buf, "ERROR") != NULL) {
			printf("Error received from lircd daemon\n");
			close(s);
			return NULL;
		}
		if (!foundData)
		{
			// Need to find the DATA indicator first
			char* dataPtr = strstr(buf + bufOffset, "DATA\n");
			if (dataPtr)
			{
				foundData = 1;
				dataPtr[4] = 0;
				bufOffset += strlen(buf + bufOffset) + 1;
			}
		}
		if (foundData && numDataLines < 0 && bufOffset < bufLen)
		{
			char* nextLine = strchr(buf + bufOffset, '\n');
			if (nextLine)
			{
				nextLine[0] = 0;
				numDataLines = atoi(buf + bufOffset);
				bufOffset += strlen(buf + bufOffset) + 1;
			}
		}
		while (numDataLines > 0 && bufOffset < bufLen)
		{
			char* nextLine = strchr(buf + bufOffset, '\n');
			if (nextLine)
			{
				numDataLines--;
				nextLine[0] = 0;
				if (pszPathName) // skip the code and get tot he name
				{
					nextLine = strchr(buf + bufOffset, ' ');
					if (nextLine)
					{
						nextLine[0] = 0;
						bufOffset += strlen(buf + bufOffset) + 1;
					}
				}
					
				char* newName = (char*)malloc(strlen(buf + bufOffset) + 1);
				strcpy(newName, buf + bufOffset);
				// Add the remote for the name , or the commands if it's that one
				if (pszPathName && !head)
				{
					// Create the base remote
					char* otherName = (char*)malloc(strlen(pszPathName) + 1);
					strcpy(otherName, pszPathName);
					head = CreateRemote(otherName);
					head->command = CreateCommand(newName);
				}
				else if (!head)
				{
					head = CreateRemote(newName);
				}
				else if (pszPathName)
				{
					AddCommand(CreateCommand(newName), &(head->command));
				}
				else
				{
					AddRemote(CreateRemote(newName), &head);
				}

				bufOffset += strlen(buf + bufOffset) + 1;
			}
			else
				break;
		}
		if (numDataLines == 0 || strstr(buf + bufOffset, "END") == buf + bufOffset)
			break;
	} while (1);

	close(s);

	return head;
	
}    	

remote* CreateRemote(unsigned char *Name)
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

void InitDevice() {}
command* RecordCommand(int devHandle, unsigned char *Name) { return 0; }
void send_command(char *cmd, char* recvBuf, int* recvSize) 
{
	int s, len;
	struct sockaddr_un saun;
	char buf[MAXDATASIZE];

	// Grab a socket. (Solaris style socket OK for intramachine
	// communication, as opposed to the method in server.c)

	if ((s = socket(AF_UNIX, SOCK_STREAM, 0)) < 0) {
		printf("Couldn't get a socket\n");
		return;
	}

	// Create the address to communicate with, and direct it to the lircd device
	saun.sun_family = AF_UNIX;
	strcpy(saun.sun_path, RC_CMD);

	len = sizeof(saun.sun_family) + strlen(saun.sun_path);

	// Try to connect to lircd daemon
	if(connect(s,(struct sockaddr *)&saun, len) < 0) {
		printf("Couldn't connect to lircd daemon\n");
		return;
	}
	//  Send the command
	if(send(s, cmd, strlen(cmd), 0) < 0) {
		printf("Error sending %s\n", cmd);
		return;
	}
	// Get the response...don't bother, it's a waste of time
/*	if (recvBuf)
	{
		*recvSize = recv(s, recvBuf, *recvSize, 0);
		if (*recvSize == -1) {
		printf("Error receiving response from lircd daemon\n");
		return;
		}
	}
	else if (recv(s, buf, MAXDATASIZE, 0) == -1) {
		printf("Error receiving response from lircd daemon\n");
		return;
	}

	if(strstr(buf, "ERROR") != NULL) {
		printf("Error received from lircd daemon\n");
		return;
	}
*/
	printf("Sent %s\n", cmd);

	close(s);
}
void PlayCommand (int devHandle, remote *remote, unsigned char *name, int tx_repeats)
{
	char cmd[1024];
	if (tx_repeats)
		sprintf(cmd, "SEND_ONCE %s %s %d\n", remote->name, name, tx_repeats);
	else
		sprintf(cmd, "SEND_ONCE %s %s\n", remote->name, name);
	send_command(cmd, 0, 0);
}
void FreeRemotes(remote **head)
{
	command *Temp_Com;		//temporary command pointer
	remote *Temp_Rem;		//temporary remote pointer
	pattern *temp_pat;		//temporary pattern pointer
	
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

int CanMacroTune(void) { return 0; }
void MacroTune(int devHandle, int channel){}

