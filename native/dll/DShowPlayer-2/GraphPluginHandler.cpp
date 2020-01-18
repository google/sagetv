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
#include "StdAfx.h"
#include "GraphPluginHandler.h"

GraphPluginHandler::GraphPluginHandler(void)
{
	TCHAR szModulePath[MAX_PATH + 1] = {0}; 
	TCHAR szPluginSearchPath[MAX_PATH + 1] = {0};
	TCHAR szExePath[MAX_PATH + 1] = {0};
	DWORD dwLen = 0;
	char* pch;
	WIN32_FIND_DATA FindFileData;
	HANDLE hFind;
	//m_szPluginPath[MAX_PATH + 1] = {0}; 
	m_numPlugins = 0;
	
	dwLen = GetModuleFileName(NULL, szModulePath, MAX_PATH);
	pch=strrchr(szModulePath,'\\')+1;
	dwLen = dwLen - (int)strlen(pch);
	strncpy(szExePath, szModulePath, dwLen);
	szExePath[dwLen] = '\0';

	sprintf( m_szPluginPath, "%sGraphPlugin\\\0", szExePath);
	sprintf( m_szPluginSearchPath, "%s*.dll\0", m_szPluginPath);	

	hFind = FindFirstFileEx(m_szPluginSearchPath, FindExInfoStandard, &FindFileData, FindExSearchNameMatch, NULL, 0);
	if (hFind != INVALID_HANDLE_VALUE) 
	{
		do
		{
			TCHAR szDllName[MAX_PATH + 1] = {0};
			sprintf( szDllName, "%s%s\0", m_szPluginPath, FindFileData.cFileName);
			//_tprintf (TEXT("File found: %s\n"), szDllName);
			HINSTANCE hInstLibrary = LoadLibrary(szDllName);

			if (hInstLibrary)
			{
				GraphCreatedFunc grphCreated = (GraphCreatedFunc)GetProcAddress(hInstLibrary, "GraphCreated");
				GraphStoppedFunc grphStopped = (GraphStoppedFunc)GetProcAddress(hInstLibrary, "GraphStopped");
				GraphDestroyedFunc grphDestroyed = (GraphDestroyedFunc)GetProcAddress(hInstLibrary, "GraphDestroyed");

				if (grphCreated && grphStopped && grphDestroyed)
				{
					if(m_numPlugins < 50) //only support 50 plugins
					{
						m_plugins[m_numPlugins] = hInstLibrary;
						m_numPlugins++;
					}
				} 
				else 
				{
					FreeLibrary(hInstLibrary);
				}
			}
		}
		while (FindNextFile(hFind, &FindFileData) != 0);
		FindClose(hFind);
	}
}

GraphPluginHandler::~GraphPluginHandler(void)
{
	for(DWORD i = 0; i < m_numPlugins; i++)
	{
		HINSTANCE hInstLibrary = m_plugins[i];
		if(hInstLibrary)
		{
			FreeLibrary(hInstLibrary);
		}
	}
}

HRESULT GraphPluginHandler::GraphCreated(IGraphBuilder *pGraph, LPCOLESTR szFile)
{	
	HRESULT ret = S_OK;
	
	__try
	{			
		ret = DoGraphCreated(pGraph, szFile);
	}
	__except(1) {
		slog(("DShowPlayer GraphPluginHandler had an exception error in GraphCreated!\r\n"));
	}	
			
	return ret;
}

HRESULT GraphPluginHandler::DoGraphCreated(IGraphBuilder *pGraph, LPCOLESTR szFile)
{	
	HRESULT ret = S_OK;
	
	for(DWORD i = 0; i < m_numPlugins; i++)
	{
		HINSTANCE hInstLibrary = m_plugins[i];
		if(hInstLibrary)
		{
			GraphCreatedFunc grphCreated = (GraphCreatedFunc)GetProcAddress(hInstLibrary, "GraphCreated");
	
			if (grphCreated)
			{
				//TODO: how should failure be handled
				try
				{			
					ret = grphCreated(pGraph, szFile);
				}
				catch(...) {
				slog(("DShowPlayer GraphPluginHandler had an exception error in DoGraphCreated!\r\n"));
				}
			}
		}
	}				
			
	return ret;
}

HRESULT GraphPluginHandler::GraphStopped( IGraphBuilder *pGraph)
{
	HRESULT ret = S_OK;
	
	__try
	{			
		ret = DoGraphStopped(pGraph);
	}
	__except(1) {
		slog(("DShowPlayer GraphPluginHandler had an exception error in GraphStopped!\r\n"));
	}	
			
	return ret;
}

HRESULT GraphPluginHandler::DoGraphStopped( IGraphBuilder *pGraph)
{
	HRESULT ret = S_OK;

	for(DWORD i = 0; i < m_numPlugins; i++)
	{
		HINSTANCE hInstLibrary = m_plugins[i];
		if(hInstLibrary)
		{
			GraphStoppedFunc grphStopped = (GraphStoppedFunc)GetProcAddress(hInstLibrary, "GraphStopped");
	
			if (grphStopped)
			{
				//TODO: how should failure be handled
				try
				{
					ret = grphStopped(pGraph);
				} 
				catch(...) {
					slog(("DShowPlayer GraphPluginHandler had an exception error in DoGraphStopped!\r\n"));
				}
			}
		}
	}					
			
	return ret;
}

HRESULT GraphPluginHandler::GraphDestroyed ()
{
	HRESULT ret = S_OK;
	
	__try
	{			
		ret = DoGraphDestroyed();
	}
	__except(1) {
		slog(("DShowPlayer GraphPluginHandler had an exception error in GraphDestroyed!\r\n"));
	}	
			
	return ret;
}

HRESULT GraphPluginHandler::DoGraphDestroyed ()
{
	HRESULT ret = S_OK;
	
	for(DWORD i = 0; i < m_numPlugins; i++)
	{
		HINSTANCE hInstLibrary = m_plugins[i];
		if(hInstLibrary)
		{
			GraphDestroyedFunc grphDestroyed = (GraphDestroyedFunc)GetProcAddress(hInstLibrary, "GraphDestroyed");
	
			if (grphDestroyed)
			{
				//TODO: how should failure be handled
				try
				{
					ret = grphDestroyed();
				} 
				catch(...) {
					slog(("DShowPlayer GraphPluginHandler had an exception error in DoGraphDestroyed!\r\n"));
				}
			}
		}
	}	
				
	return ret;
}
