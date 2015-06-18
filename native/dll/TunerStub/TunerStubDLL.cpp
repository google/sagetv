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

#include "stdafx.h"
#include <stdio.h>
#include <malloc.h>
#include "TunerStubDLL.h"

// Enable this define to build the dll which controls the Hauppauge IR blaster (HCWIRBlaster.dll)
//#define HCW_BLASTER

#ifdef HCW_BLASTER
#include "../../../third_party/Hauppauge/hcwIRBlast.h"
#endif

#define Line_Length 200

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
void  Add_Remote(remote **head, FILE *fp, unsigned char *Temp_String);
command*  Create_Command_List(FILE *fp);
pattern*  create_pat_list(unsigned char Temp_String[],
						 unsigned char *Ptr,
						 FILE *fp);

unsigned char*  byte_space(unsigned Length)
{
   unsigned char* Space;   	//pointer to allocated space
   
   Space = (unsigned char *)malloc(Length*sizeof(unsigned char));
			    			//allocating space
   	if (Space == NULL)
	{
		////TRACE("malloc failed in BYTE_SPACE...Exiting!!!\n");
		PostQuitMessage(0);	
	}	
   return Space;            // returning the pointer
}

struct pattern*  create_pat_node(void)
{
	struct pattern *node;
	
	node = (struct pattern*)malloc(sizeof(struct pattern));
	if (node ==NULL)
	{
		////TRACE("malloc failed in CREATE_PAT_NODE()...Exiting!!!\n");
		PostQuitMessage(0);	
	}
	return node;
}		   
unsigned char*  String_Space(unsigned char *String)
{
   unsigned int Length;   //Length of string "String"
   unsigned char *Space;  //pointer to allocated space

   Length = strlen((char*)String) + 1;
   Space = (unsigned char *)malloc(Length*sizeof(unsigned char));
   if (Space == NULL)
   {  
		////TRACE("malloc failed in STRING_SPACE()...Exiting!!!\n");
		PostQuitMessage(0);	
	}	    	 
   strcpy((char*)Space, (char*)String);	//allocating space and copying the value
   return Space;            // onto the allocated space
}

command*  CreateCommand(unsigned char *Name, struct pattern *pat_list)
{
	struct command *Com;   //pointer to new command structure
	
	Com = (struct command*)malloc(sizeof(struct command));//allocate space for a command structure
	if (Com == NULL)
	{
		////TRACE("malloc failed in CREATE_COMMAND()...Exiting!!!\n");
		PostQuitMessage(0);	
	}	
	Com->name = Name;                                     //copy values
    Com->pattern = pat_list;
    return Com;                                           //return pointer to new command structure
}    	

TUNERSTUBDLL_API bool NeedBitrate(void) { return false; }
TUNERSTUBDLL_API bool NeedCarrierFrequency(void) { return false; }

#ifdef HCW_BLASTER
WORD			m_IRBlasterPort;  // IRblaster port,
UIR_CFG		m_IRBlasterCFG; // IRblaster config data
#endif

TUNERSTUBDLL_API const char* DeviceName() { 
#ifdef HCW_BLASTER
	return "Hauppauge IR Blaster";
#else
	return "Stub Transmitter";
#endif
}
TUNERSTUBDLL_API bool OpenDevice(int ComPort) 
{
#ifdef HCW_BLASTER
	m_IRBlasterPort = 0;
	//Use try/catch block in case delayload of hcwblast.dll fails
	try {
		m_IRBlasterPort = UIR_Open(FALSE, 0); 
		//Try to open default IRBlaster device
		m_IRBlasterCFG.cfgDataSize = sizeof(UIR_CFG);
		// Load the previously saved configuration params
		UIRError uerr = UIR_GetConfig(-1, -1, &m_IRBlasterCFG);
		if(uerr != UIRError_Success)
		{
			m_IRBlasterPort = 0;
		}
	}
	catch(...)
	{
		// hcwblast.dll not found, or some other critical problem
	}
	if(m_IRBlasterPort == 0)
	{
		return false;
	}
#endif
	return true; 
}
TUNERSTUBDLL_API void CloseDevice() 
{
//	UIR_Close();
}
TUNERSTUBDLL_API unsigned long FindBitRate(void) { return 0; }
TUNERSTUBDLL_API unsigned long FindCarrierFrequency(void) { return 0; }
TUNERSTUBDLL_API void  AddRemote(struct remote *Remote, struct remote **head)
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
TUNERSTUBDLL_API void  AddCommand(struct command *Command, struct command **Command_List)
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
TUNERSTUBDLL_API void SaveRemotes(remote *head, const char* pszPathName)
{
	unsigned int i;     //index variable
	command *Comm_List; //local copy of the pointer to the command list
	                    //for each remote
	pattern *pat_list;	//temporary pointer to pattern nodes
	FILE *fp;
	
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
		Comm_List=head->command;
		while(Comm_List)
		{                                       
			fprintf(fp,"%s", Comm_List->name);
			pat_list = Comm_List->pattern;
			while(pat_list)
			{
				fprintf(fp," %u", pat_list->bit_length);
				fprintf(fp," %c", pat_list->r_flag);
				for(i=0; i<(pat_list->length);i++)  //write command list
					fprintf(fp," %02x", pat_list->bytes[i]);
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
	unsigned char Temp_String[Line_Length];
	remote *head;
	FILE *fp;

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
	strcpy(loadedDevName, devNameStart);
	loadedDevName[strlen(loadedDevName) - 3] = '\0'; // remove the .ir
    fgets((char*)Temp_String, sizeof(Temp_String), fp);	//get a line from the file
                                                    //with remote name, carrier freq. & bit time
	head = NULL;                                    //initialize head of list
    while (!feof(fp))                               //while not end of file
    {
    	Add_Remote(&head, fp, Temp_String);         //add a remote to list
    	fgets((char*)Temp_String, sizeof(Temp_String), fp);//get a line from file with
    												//remote name, carrier freq. & bit time
    }
    fclose(fp);
    return head;                                    //return pointer to list of remotes
}    	

remote* CreateRemote(unsigned char *Name, unsigned long Carrier_Freq, unsigned long Bit_Time, struct command *Comm_List)    
{
	remote *Remote;
#ifdef _NULL
   return 0;
#endif
	
	Remote = (struct remote*)malloc(sizeof(struct remote));  //allocate space for a remote structure
	if (Remote == NULL)
	{
		////TRACE("malloc failed in CREATE_REMOTE()...Exiting!!!\n");
		PostQuitMessage(0);	
	}	
	Remote->name = Name;                 //copy values
	Remote->carrier_freq = Carrier_Freq;
	Remote->bit_time = Bit_Time;
	Remote->command = Comm_List;
	Remote->next = NULL;
	return Remote;                       //return pointer to remote structure
	
}	

//%%%%%%%%%%%%%%%%%%%Add_Remote%%%%%%%%%%%%%%%%%%//
//Accepts:	Pointer to Pointer to remote structure.                      
//			File pointer to data file. Pointer to     
//			array containing a line from the data file.                                     
//Function:	Calls Create_Remote to allocate space for a 
//			remote structure with data elements read in.
//			Calls Add_Remote to add structure to 
//			a linked list of Remotes.                
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
void  Add_Remote(remote **head, FILE *fp, unsigned char *Temp_String)
{
	unsigned char *Ptr;                 //temporary pointer
	unsigned char *Name;                //name of remote
	unsigned long Carrier_Freq;
	unsigned Bit_Time;
	command *Comm_List;                 //local pointer to list of commands for remote
	remote *Remote;                     //local pointer to a remote structure
	
	Ptr = (unsigned char*)strtok((char*)Temp_String, " \t");   //assign the pointer to the first character on the fetched line
    Name = String_Space(Ptr);           //allocate space and copy string into it, String_Space returns pointer to string
    Ptr = (unsigned char*)strtok(NULL, " \t");          //get next string after blank space
    sscanf((char*)Ptr, "%lu", &Carrier_Freq);
    Ptr = (unsigned char*)strtok(NULL, " \t");
    sscanf((char*)Ptr, "%u", &Bit_Time);
    Comm_List = Create_Command_List(fp); //get a pointer to list of commands for remote
                                         //space for commands will be allocated on heap   
    Remote = CreateRemote(Name, Carrier_Freq, Bit_Time, Comm_List); //allocate space for a remote
    																 //and copy values
    AddRemote(Remote, head);    //add remote to list of remotes
}

//%%%%%%%%%%%%%%%%%%%%%Create_Command_List%%%%%%%%%%%%%%//
//Accepts:	FILE pointer to data file.                                   
//Returns:	Pointer to a linked list of commands                         
//Function:	Gets a line from the data file and reads the command         
//			name.  The command bytes are copied to a temporary array     
//			until Ptr==NULL. The length of the command in bytes is       
//			tracked and used for allocating the correct number of bytes  
//			in byte_space.  The actual command bytes are read into the
//			array. Calls Create_Command which returns a pointer to a     
//			command structure on the heap.  Then Add_Command_To_List     
//			is called to add the command to linked list of commands.     
//			Lines are read from the data file and put into the list      
//			of Commands until a blank line is encountered.               
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//			
command*  Create_Command_List(FILE *fp)
{
	unsigned char *Ptr;                     //temporary pointer
	unsigned char Temp_String[Line_Length]; //buffer for lines of data file
	struct pattern *pat_list;
	unsigned char *Name;                    //name of command
	struct command *Command_List;           //command list pointer
	struct command *Com;                    //temporary command pointer
	
	Command_List = NULL;                         
	fgets((char*)Temp_String, sizeof(Temp_String), fp);
	while ((!feof(fp))&&(strlen((char*)Temp_String)>1)) 
	{
		Ptr = (unsigned char*)strtok((char*)Temp_String, " \t");   //get line with command name and
											//first command pattern
		Name = String_Space(Ptr);
		pat_list = create_pat_list(Temp_String, Ptr, fp);
		Com = CreateCommand(Name, pat_list);		
		AddCommand(Com, &Command_List);
	}	
	return Command_List;		
	
}		
						
//%%%%%%%%%%%create_pat_list%%%%%%%%%%%//
//Accepts:	Pointer to first string in a byte buffer,
//			pointer to a file, pointer to 
//			the byte buffer
//Returns:	Pointer to head pattern node.
//Function:	Reads command bytes until the end
//			of line is reached. Keeps reading
//			lines of command bytes if the first
//			character is a blank. If it is not
//			a blank then it has encountered a
//			blank line (and the end of the command
//			list) or the first character of
//			the name of the next command.
//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%//
pattern*  create_pat_list(unsigned char Temp_String[],
						 unsigned char *Ptr,
						 FILE *fp)
{
	struct pattern *pat_list;				//pointer to list of command patterns
	struct pattern *temp_list;				//temporary pointer to nodes in pattern list
	unsigned char blank = ' ';				//to check for blanks
	unsigned char Temp_Command[Line_Length];//the temporary command buffer
	unsigned int length;                    //length of command pattern in bytes
	unsigned int bit_length;				//length of pattern in bits including
											//carrier off sequence
	char r_flag;							//repeated pattern flag "r" yes, "n" no											
							 
    Ptr = (unsigned char*)strtok(NULL, " \t");              //point to length in bits
	sscanf((char*)Ptr, "%u", &bit_length);			//get length of command in bits,
											//including carrier off sequence
	Ptr = (unsigned char*)strtok(NULL, " \t");
	sscanf((char*)Ptr, " %c", &r_flag);
		
   	Ptr = (unsigned char*)strtok(NULL, " \t");
   	length = 0;
   	while(Ptr)                 //get command pattern bytes
	{                                            
		sscanf((char*)Ptr,"%x", &Temp_Command[length]);
		Ptr = (unsigned char*)strtok(NULL, " \t");
		length++;
	}
	pat_list = create_pat_node(); 			//allocate for byte pattern structure
	pat_list->bit_length = bit_length;
	pat_list->r_flag = r_flag;
	pat_list->length = length;
	temp_list = pat_list;	               	//save head of pat list
	temp_list->bytes = byte_space(length);	         	//allocate for command bytes 	
   	memcpy(temp_list->bytes, Temp_Command, length);
   	temp_list->next = NULL;
	fgets((char*)Temp_String, Line_Length, fp);	//get next line
	
	while(Temp_String[0] == blank)          //get rest of command patterns
	{
		temp_list->next = create_pat_node();
		temp_list = temp_list->next;
		Ptr = (unsigned char*)strtok((char*)Temp_String, " \t");       //point to length in bits
		sscanf((char*)Ptr, "%u", &bit_length);			//get length of command in bits,
												//including carrier off sequence
		Ptr = (unsigned char*)strtok(NULL, " \t");
		sscanf((char*)Ptr, "%c", &r_flag);				//get repeat flag
		temp_list->bit_length = bit_length;
		temp_list->r_flag = r_flag;
	    Ptr = (unsigned char*)strtok(NULL, " \t");
		length = 0;
		while(Ptr)                 //get command pattern bytes
		{                                            
			sscanf((char*)Ptr,"%x", &Temp_Command[length]);
			Ptr = (unsigned char*)strtok(NULL, " \t");    
			length++;
		}
	    temp_list->bytes = byte_space(length);            	
   		temp_list->length = length;
   		temp_list->next = NULL;
	   	memcpy(temp_list->bytes, Temp_Command, length);
   		fgets((char*)Temp_String, Line_Length, fp);
   	}
	return pat_list;	
}

TUNERSTUBDLL_API void InitDevice() {}
TUNERSTUBDLL_API command* RecordCommand(unsigned char *Name) { return 0; }
TUNERSTUBDLL_API void PlayCommand (remote *remote, unsigned char *name, int tx_repeats) { }
TUNERSTUBDLL_API void FreeRemotes(remote **head)
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

#ifdef HCW_BLASTER
TUNERSTUBDLL_API bool CanMacroTune(void) { return true; }
TUNERSTUBDLL_API void MacroTune(int channel)
{
	if(m_IRBlasterPort)
	{
		UIRError uerr = UIR_GotoChannel(m_IRBlasterCFG.cfgDevice, m_IRBlasterCFG.cfgCodeset,
			channel);
		if(uerr != UIRError_Success)
		{
			//DPF(0, "UIR_GotoChannel()=%d, failed\n", uerr);
		}
	}
}
#else
TUNERSTUBDLL_API bool CanMacroTune(void) { return false; }
TUNERSTUBDLL_API void MacroTune(int channel){}
#endif

/*
TUNERSTUBDLL_API void MacroTune(int newNum)
{
	STARTUPINFO si; // Startup Info Structure 
	PROCESS_INFORMATION pi; // Process Info Structure - Contains Process ID Information 
	HKEY newKey;
	char cmdCopy[512];
	if (RegCreateKeyEx(HKEY_LOCAL_MACHINE, "Software\\Frey Technologies\\Common\\EXEMultiTunerPlugin",
		0, 0, REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, 0, &newKey, 0) == ERROR_SUCCESS)
	{
		char holder[512];
		DWORD readType;
		DWORD hsize = sizeof(holder);
		if (RegQueryValueEx(newKey, "command", 0, &readType, (LPBYTE)holder, &hsize) == ERROR_SUCCESS)
		{
			RegCloseKey(newKey);
			strcpy(cmdCopy, holder);
			char* chanIdx = strstr(holder, "%CHANNEL%");
			if (!chanIdx)
				return;
			sprintf(cmdCopy + (chanIdx - holder), "%d%s", newNum, chanIdx + 9);
			char* devIdx = strstr(cmdCopy, "%DEVICE%");
			if (devIdx)
			{
				strcpy(holder, cmdCopy);
				devIdx = strstr(holder, "%DEVICE%"); // we need to copy the string, we can't modify in place
				sprintf(cmdCopy + (devIdx - holder), "%s%s", loadedDevName, devIdx + 8); 
			}
		}
		else
		{
			RegCloseKey(newKey);
			return;
		}
	}
	else
		return;
	//const char* procCmd = "dtvcmd #";
	//strcpy(cmdCopy, procCmd);
	//sprintf(cmdCopy + strlen(cmdCopy), "%d", newNum);
	ZeroMemory(&si, sizeof(si)); // Zero Startup Info
	si.cb = sizeof(si); // Set Size

	CreateProcess(0, // module name
		cmdCopy, // cmd line
		0, // process attr 
		0, // thread attr
		FALSE, // inherit handles
		0, // no create flags
		0, // use parent env
		0, // curr dir
		&si, &pi);
	CloseHandle(&pi);
}

*/
