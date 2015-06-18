#ifndef __H_ISTVEVRPRSTR_H
#define __H_ISTVEVRPRSTR_H

#ifdef __cplusplus
extern "C" {
#endif

// {A270055F-7785-4ff7-9761-6EA47CF5DD68}
DEFINE_GUID(IID_ISTVEVRPrstr, 
0xa270055f, 0x7785, 0x4ff7, 0x97, 0x61, 0x6e, 0xa4, 0x7c, 0xf5, 0xdd, 0x68);
  

//----------------------------------------------------------------------------
// ISTVEVRPrstr
//----------------------------------------------------------------------------
DECLARE_INTERFACE_(ISTVEVRPrstr, IUnknown)
{

    STDMETHOD(set_D3DDeviceMgr) (THIS_
    				  IDirect3DDeviceManager9 *pD3DMgr
				 ) PURE;

};
//----------------------------------------------------------------------------

#ifdef __cplusplus
}
#endif

#endif // __H_ISTVEVRPRSTR_H