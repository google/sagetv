/* this ALWAYS GENERATED file contains the definitions for the interfaces */


/* File created by MIDL compiler version 5.01.0164 */
/* at Sun Nov 17 01:24:07 2002
 */
/* Compiler settings for C:\SageWorkspace\DeinterlaceUpdates\Deinterlace.idl:
    Oicf (OptLev=i2), W1, Zp8, env=Win32, ms_ext, c_ext
    error checks: allocation ref bounds_check enum stub_data 
*/
//@@MIDL_FILE_HEADING(  )


/* verify that the <rpcndr.h> version is high enough to compile this file*/
#ifndef __REQUIRED_RPCNDR_H_VERSION__
#define __REQUIRED_RPCNDR_H_VERSION__ 440
#endif

#include "rpc.h"
#include "rpcndr.h"

#ifndef __RPCNDR_H_VERSION__
#error this stub requires an updated version of <rpcndr.h>
#endif // __RPCNDR_H_VERSION__

#ifndef COM_NO_WINDOWS_H
#include "windows.h"
#include "ole2.h"
#endif /*COM_NO_WINDOWS_H*/

#ifndef __Deinterlace_h__
#define __Deinterlace_h__

#ifdef __cplusplus
extern "C"{
#endif 

/* Forward Declarations */ 

#ifndef __IDeinterlace_FWD_DEFINED__
#define __IDeinterlace_FWD_DEFINED__
typedef interface IDeinterlace IDeinterlace;
#endif 	/* __IDeinterlace_FWD_DEFINED__ */


#ifndef __IDeinterlace2_FWD_DEFINED__
#define __IDeinterlace2_FWD_DEFINED__
typedef interface IDeinterlace2 IDeinterlace2;
#endif 	/* __IDeinterlace2_FWD_DEFINED__ */


#ifndef __Deinterlace_FWD_DEFINED__
#define __Deinterlace_FWD_DEFINED__

#ifdef __cplusplus
typedef class Deinterlace Deinterlace;
#else
typedef struct Deinterlace Deinterlace;
#endif /* __cplusplus */

#endif 	/* __Deinterlace_FWD_DEFINED__ */


#ifndef __DeinterlacePropertyPage_FWD_DEFINED__
#define __DeinterlacePropertyPage_FWD_DEFINED__

#ifdef __cplusplus
typedef class DeinterlacePropertyPage DeinterlacePropertyPage;
#else
typedef struct DeinterlacePropertyPage DeinterlacePropertyPage;
#endif /* __cplusplus */

#endif 	/* __DeinterlacePropertyPage_FWD_DEFINED__ */


#ifndef __DeinterlaceAboutPage_FWD_DEFINED__
#define __DeinterlaceAboutPage_FWD_DEFINED__

#ifdef __cplusplus
typedef class DeinterlaceAboutPage DeinterlaceAboutPage;
#else
typedef struct DeinterlaceAboutPage DeinterlaceAboutPage;
#endif /* __cplusplus */

#endif 	/* __DeinterlaceAboutPage_FWD_DEFINED__ */


/* header files for imported files */
#include "oaidl.h"
#include "ocidl.h"

void __RPC_FAR * __RPC_USER MIDL_user_allocate(size_t);
void __RPC_USER MIDL_user_free( void __RPC_FAR * ); 

#ifndef __IDeinterlace_INTERFACE_DEFINED__
#define __IDeinterlace_INTERFACE_DEFINED__

/* interface IDeinterlace */
/* [unique][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDeinterlace;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("463D645C-48F7-11d4-8464-0008C782A257")
    IDeinterlace : public IUnknown
    {
    public:
        virtual /* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE get_DeinterlaceType( 
            /* [retval][out] */ int __RPC_FAR *pVal) = 0;
        
        virtual /* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE put_DeinterlaceType( 
            /* [in] */ int newVal) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDeinterlaceVtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE __RPC_FAR *QueryInterface )( 
            IDeinterlace __RPC_FAR * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ void __RPC_FAR *__RPC_FAR *ppvObject);
        
        ULONG ( STDMETHODCALLTYPE __RPC_FAR *AddRef )( 
            IDeinterlace __RPC_FAR * This);
        
        ULONG ( STDMETHODCALLTYPE __RPC_FAR *Release )( 
            IDeinterlace __RPC_FAR * This);
        
        /* [helpstring][id][propget] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *get_DeinterlaceType )( 
            IDeinterlace __RPC_FAR * This,
            /* [retval][out] */ int __RPC_FAR *pVal);
        
        /* [helpstring][id][propput] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *put_DeinterlaceType )( 
            IDeinterlace __RPC_FAR * This,
            /* [in] */ int newVal);
        
        END_INTERFACE
    } IDeinterlaceVtbl;

    interface IDeinterlace
    {
        CONST_VTBL struct IDeinterlaceVtbl __RPC_FAR *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDeinterlace_QueryInterface(This,riid,ppvObject)	\
    (This)->lpVtbl -> QueryInterface(This,riid,ppvObject)

#define IDeinterlace_AddRef(This)	\
    (This)->lpVtbl -> AddRef(This)

#define IDeinterlace_Release(This)	\
    (This)->lpVtbl -> Release(This)


#define IDeinterlace_get_DeinterlaceType(This,pVal)	\
    (This)->lpVtbl -> get_DeinterlaceType(This,pVal)

#define IDeinterlace_put_DeinterlaceType(This,newVal)	\
    (This)->lpVtbl -> put_DeinterlaceType(This,newVal)

#endif /* COBJMACROS */


#endif 	/* C style interface */



/* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE IDeinterlace_get_DeinterlaceType_Proxy( 
    IDeinterlace __RPC_FAR * This,
    /* [retval][out] */ int __RPC_FAR *pVal);


void __RPC_STUB IDeinterlace_get_DeinterlaceType_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE IDeinterlace_put_DeinterlaceType_Proxy( 
    IDeinterlace __RPC_FAR * This,
    /* [in] */ int newVal);


void __RPC_STUB IDeinterlace_put_DeinterlaceType_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);



#endif 	/* __IDeinterlace_INTERFACE_DEFINED__ */


#ifndef __IDeinterlace2_INTERFACE_DEFINED__
#define __IDeinterlace2_INTERFACE_DEFINED__

/* interface IDeinterlace2 */
/* [unique][helpstring][uuid][object] */ 


EXTERN_C const IID IID_IDeinterlace2;

#if defined(__cplusplus) && !defined(CINTERFACE)
    
    MIDL_INTERFACE("7402D283-1AA1-4bbb-B2D7-9B677270D531")
    IDeinterlace2 : public IDeinterlace
    {
    public:
        virtual /* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE get_IsOddFieldFirst( 
            /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal) = 0;
        
        virtual /* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE put_IsOddFieldFirst( 
            /* [in] */ VARIANT_BOOL newVal) = 0;
        
        virtual /* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE get_DScalerPluginName( 
            /* [retval][out] */ BSTR __RPC_FAR *pVal) = 0;
        
        virtual /* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE put_DScalerPluginName( 
            /* [in] */ BSTR newVal) = 0;
        
        virtual /* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE get_RefreshRateDouble( 
            /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal) = 0;
        
        virtual /* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE put_RefreshRateDouble( 
            /* [in] */ VARIANT_BOOL newVal) = 0;
        
    };
    
#else 	/* C style interface */

    typedef struct IDeinterlace2Vtbl
    {
        BEGIN_INTERFACE
        
        HRESULT ( STDMETHODCALLTYPE __RPC_FAR *QueryInterface )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [in] */ REFIID riid,
            /* [iid_is][out] */ void __RPC_FAR *__RPC_FAR *ppvObject);
        
        ULONG ( STDMETHODCALLTYPE __RPC_FAR *AddRef )( 
            IDeinterlace2 __RPC_FAR * This);
        
        ULONG ( STDMETHODCALLTYPE __RPC_FAR *Release )( 
            IDeinterlace2 __RPC_FAR * This);
        
        /* [helpstring][id][propget] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *get_DeinterlaceType )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [retval][out] */ int __RPC_FAR *pVal);
        
        /* [helpstring][id][propput] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *put_DeinterlaceType )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [in] */ int newVal);
        
        /* [helpstring][id][propget] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *get_IsOddFieldFirst )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal);
        
        /* [helpstring][id][propput] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *put_IsOddFieldFirst )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [in] */ VARIANT_BOOL newVal);
        
        /* [helpstring][id][propget] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *get_DScalerPluginName )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [retval][out] */ BSTR __RPC_FAR *pVal);
        
        /* [helpstring][id][propput] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *put_DScalerPluginName )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [in] */ BSTR newVal);
        
        /* [helpstring][id][propget] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *get_RefreshRateDouble )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal);
        
        /* [helpstring][id][propput] */ HRESULT ( STDMETHODCALLTYPE __RPC_FAR *put_RefreshRateDouble )( 
            IDeinterlace2 __RPC_FAR * This,
            /* [in] */ VARIANT_BOOL newVal);
        
        END_INTERFACE
    } IDeinterlace2Vtbl;

    interface IDeinterlace2
    {
        CONST_VTBL struct IDeinterlace2Vtbl __RPC_FAR *lpVtbl;
    };

    

#ifdef COBJMACROS


#define IDeinterlace2_QueryInterface(This,riid,ppvObject)	\
    (This)->lpVtbl -> QueryInterface(This,riid,ppvObject)

#define IDeinterlace2_AddRef(This)	\
    (This)->lpVtbl -> AddRef(This)

#define IDeinterlace2_Release(This)	\
    (This)->lpVtbl -> Release(This)


#define IDeinterlace2_get_DeinterlaceType(This,pVal)	\
    (This)->lpVtbl -> get_DeinterlaceType(This,pVal)

#define IDeinterlace2_put_DeinterlaceType(This,newVal)	\
    (This)->lpVtbl -> put_DeinterlaceType(This,newVal)


#define IDeinterlace2_get_IsOddFieldFirst(This,pVal)	\
    (This)->lpVtbl -> get_IsOddFieldFirst(This,pVal)

#define IDeinterlace2_put_IsOddFieldFirst(This,newVal)	\
    (This)->lpVtbl -> put_IsOddFieldFirst(This,newVal)

#define IDeinterlace2_get_DScalerPluginName(This,pVal)	\
    (This)->lpVtbl -> get_DScalerPluginName(This,pVal)

#define IDeinterlace2_put_DScalerPluginName(This,newVal)	\
    (This)->lpVtbl -> put_DScalerPluginName(This,newVal)

#define IDeinterlace2_get_RefreshRateDouble(This,pVal)	\
    (This)->lpVtbl -> get_RefreshRateDouble(This,pVal)

#define IDeinterlace2_put_RefreshRateDouble(This,newVal)	\
    (This)->lpVtbl -> put_RefreshRateDouble(This,newVal)

#endif /* COBJMACROS */


#endif 	/* C style interface */



/* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_get_IsOddFieldFirst_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal);


void __RPC_STUB IDeinterlace2_get_IsOddFieldFirst_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_put_IsOddFieldFirst_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [in] */ VARIANT_BOOL newVal);


void __RPC_STUB IDeinterlace2_put_IsOddFieldFirst_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_get_DScalerPluginName_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [retval][out] */ BSTR __RPC_FAR *pVal);


void __RPC_STUB IDeinterlace2_get_DScalerPluginName_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_put_DScalerPluginName_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [in] */ BSTR newVal);


void __RPC_STUB IDeinterlace2_put_DScalerPluginName_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propget] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_get_RefreshRateDouble_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [retval][out] */ VARIANT_BOOL __RPC_FAR *pVal);


void __RPC_STUB IDeinterlace2_get_RefreshRateDouble_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);


/* [helpstring][id][propput] */ HRESULT STDMETHODCALLTYPE IDeinterlace2_put_RefreshRateDouble_Proxy( 
    IDeinterlace2 __RPC_FAR * This,
    /* [in] */ VARIANT_BOOL newVal);


void __RPC_STUB IDeinterlace2_put_RefreshRateDouble_Stub(
    IRpcStubBuffer *This,
    IRpcChannelBuffer *_pRpcChannelBuffer,
    PRPC_MESSAGE _pRpcMessage,
    DWORD *_pdwStubPhase);



#endif 	/* __IDeinterlace2_INTERFACE_DEFINED__ */



#ifndef __DeinterlaceLib_LIBRARY_DEFINED__
#define __DeinterlaceLib_LIBRARY_DEFINED__

/* library DeinterlaceLib */
/* [helpstring][version][uuid] */ 


EXTERN_C const IID LIBID_DeinterlaceLib;

EXTERN_C const CLSID CLSID_Deinterlace;

#ifdef __cplusplus

class DECLSPEC_UUID("437B0D3A-4689-4fa6-A7DD-EB4928203C2F")
Deinterlace;
#endif

EXTERN_C const CLSID CLSID_DeinterlacePropertyPage;

#ifdef __cplusplus

class DECLSPEC_UUID("E1AA698A-C292-488d-8F3A-29D44FB070CA")
DeinterlacePropertyPage;
#endif

EXTERN_C const CLSID CLSID_DeinterlaceAboutPage;

#ifdef __cplusplus

class DECLSPEC_UUID("E5C790A0-7D55-45d5-8422-06739B8BC2D9")
DeinterlaceAboutPage;
#endif
#endif /* __DeinterlaceLib_LIBRARY_DEFINED__ */

/* Additional Prototypes for ALL interfaces */

unsigned long             __RPC_USER  BSTR_UserSize(     unsigned long __RPC_FAR *, unsigned long            , BSTR __RPC_FAR * ); 
unsigned char __RPC_FAR * __RPC_USER  BSTR_UserMarshal(  unsigned long __RPC_FAR *, unsigned char __RPC_FAR *, BSTR __RPC_FAR * ); 
unsigned char __RPC_FAR * __RPC_USER  BSTR_UserUnmarshal(unsigned long __RPC_FAR *, unsigned char __RPC_FAR *, BSTR __RPC_FAR * ); 
void                      __RPC_USER  BSTR_UserFree(     unsigned long __RPC_FAR *, BSTR __RPC_FAR * ); 

/* end of Additional Prototypes */

#ifdef __cplusplus
}
#endif

#endif
