//------------------------------------------------------------------------------
// Copyright 2015 The SageTV Authors. All Rights Reserved.
// File: Dbg.h
//
// Desc: DirectShow sample code - Helper file for the PSIParser filter.
//
// Copyright (c) Microsoft Corporation.  All rights reserved.
//------------------------------------------------------------------------------

// dump a string to debug output
#define Dump(tsz) \
    OutputDebugString(tsz);

#define DumpAndReturnFalse(tsz) \
    {OutputDebugString(tsz);    \
    return false;}              \

// dump a string with a parameter value to debug output
#define Dump1(tsz, arg)                         \
    { TCHAR dbgsup_tszDump[1024];               \
      wsprintf(dbgsup_tszDump, (tsz), (arg));   \
      OutputDebugString(dbgsup_tszDump); }


#define CHECK_ERROR(tsz,hr)                     \
{   if( !SUCCEEDED(hr)  )                       \
    {                                           \
        TCHAR dbgsup_tszDump[1024];             \
        wsprintf(dbgsup_tszDump, (tsz), (hr));  \
        OutputDebugString(dbgsup_tszDump);      \
        return hr;                              \
    }                                           \
}

#define RETURN_FALSE_IF_FAILED(tsz,hr)          \
{   if( S_OK != hr)                             \
    {                                           \
        TCHAR dbgsup_tszDump[1024];             \
        wsprintf(dbgsup_tszDump, (tsz), (hr));  \
        OutputDebugString(dbgsup_tszDump);      \
        return FALSE;                           \
    }                                           \
}

#define CHECK_BADPTR(tsz,ptr)                   \
{                                               \
    TCHAR dbgsup_tszDump[1024];                 \
    if( ptr == 0)                               \
    {                                           \
        wsprintf(dbgsup_tszDump, (tsz), (ptr)); \
        OutputDebugString(dbgsup_tszDump);      \
        return E_FAIL;                          \
    }                                           \
}

#define RETURN_FALSE_IF_BADPTR(tsz,ptr)         \
{                                               \
    TCHAR dbgsup_tszDump[1024];                 \
    if( ptr == 0)                               \
    {                                           \
        wsprintf(dbgsup_tszDump, (tsz), (ptr)); \
        OutputDebugString(dbgsup_tszDump);      \
        return FALSE;                           \
    }                                           \
}


