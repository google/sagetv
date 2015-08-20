/*
 * hdhomerun_bda_interface.h
 *
 * Copyright 2008 Silicondust Engineering Ltd. <www.silicondust.com>.  All rights reserved.
 */

#ifndef __HDHOMERUN_BDA_INTERFACE_H__
#define __HDHOMERUN_BDA_INTERFACE_H__

/*
 * Get/Set program filter.
 * ProgramNumber = -1 (default): Program filter is disabled. Application may set the PID filter.
 * ProgramNumber = 1 - 65535:    Program filter set to given program number. Application set PID filter is ignored.
 */
typedef interface IHDHomeRun_ProgramFilter IHDHomeRun_ProgramFilter;

#define STATIC_IID_IHDHomeRun_ProgramFilter {0x48f05934, 0xb01e, 0x4218, {0x91, 0xa0, 0xc5, 0xba, 0xe7, 0xc4, 0xf7, 0x8b}}
EXTERN_C const IID IID_IHDHomeRun_ProgramFilter;

MIDL_INTERFACE("48F05934-B01E-4218-91A0-C5BAE7C4F78B")
IHDHomeRun_ProgramFilter : public IUnknown
{
public:
	virtual HRESULT STDMETHODCALLTYPE put_ProgramNumber(long ProgramNumber) = 0;
	virtual HRESULT STDMETHODCALLTYPE get_ProgramNumber(long *pProgramNumber) = 0;
};

#endif 	/* __HDHOMERUN_BDA_INTERFACE_H__ */
