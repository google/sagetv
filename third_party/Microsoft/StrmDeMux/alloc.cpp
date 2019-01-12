//==========================================================================;
// Copyright 2015 The SageTV Authors. All Rights Reserved.
//
//  THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
//  KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
//  IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A PARTICULAR
//  PURPOSE.
//
//  Copyright (c) 1996 - 1998  Microsoft Corporation.  All Rights Reserved.
//
//--------------------------------------------------------------------------;
/*  Sequential allocator methods */
#include <streams.h>
#include "alloc.h"
#include "../FileSource/strconv.h"
#include "../../../native/ax/StrmDeMux/SDeMuxFilter.h"

CSequentialAllocator::CSequentialAllocator(
    LPUNKNOWN  pUnk,
    HRESULT   *phr,
	LPCWSTR   pName
) : CMemAllocator(TEXT("Sequential Allocator SageTV"), pUnk, phr),
    m_pbNext(m_pBuffer),
    m_parSamples(NULL),
	m_dwTotalSize( 0 ),
    m_lValid(0),
	m_bStopAlloc( FALSE ),
	m_uFailedNum( 0 ),
	m_pName( NULL ),
    m_pbStartValid(NULL)
{
	m_dwWaitCounter = 0; //for performance checking

	if ( pName != NULL )
	{
		DWORD nameLen = lstrlenW(pName)+1;
		m_pName = new WCHAR[nameLen];
		if (m_pName) 
			CopyMemory(m_pName, pName, nameLen*sizeof(WCHAR));
	} else
	{
		m_pName = new WCHAR[1];
		m_pName[0] = 0x0;
	}
    trace_wait = 0;    
	trace_semaphore = 0;
	trace_release = 0;

}

CSequentialAllocator::~CSequentialAllocator()
{
	IsAllSampleFree( ); 

    if (m_parSamples != NULL) {
        delete [] m_parSamples;
    }

	if ( m_pName )
		delete m_pName;
}

//because a sample shared by different threads that may release at their will, a Release is not thread safe, 
long CSequentialAllocator::SafeRelease( IMediaSample * pSample )
{
	ASSERT( pSample );
	//CAutoLock cObjectLock(this);  //protected Release to make a sample thread safe
	return pSample->Release();
}

long CSequentialAllocator::SafeAddRef( IMediaSample * pSample )
{
	ASSERT( pSample );
	//CAutoLock cObjectLock(this);   //protected AddRef to make a sample thread safe
	return pSample->AddRef();
}

STDMETHODIMP CSequentialAllocator::ReleaseBuffer(IMediaSample * pSample )
{
	CBaseAllocator::ReleaseBuffer( pSample );  //pSample will return to m_lFee list
	return NOERROR;
}

STDMETHODIMP CSequentialAllocator::GetBuffer(  IMediaSample **ppBuffer )
{
    /*  Like the normal version except we will only allocate the NEXT
        buffer
    */

	CMediaSample *pSample = NULL;

    *ppBuffer = NULL;
	for (;!m_bStopAlloc;)
    {
		{  // scope for lock
			CAutoLock cObjectLock(this);

			/* Check we are committed */
			if (!m_bCommitted) {
				return VFW_E_NOT_COMMITTED;
			}


            /* Check if the one we want is there */
            CMediaSample *pSearch = m_lFree.Head();
            while (pSearch) {
                PBYTE pbBuffer;
                pSearch->GetPointer(&pbBuffer);
                if (pbBuffer == m_pbNext) {
                    m_lFree.Remove(pSearch);
                    pSample = pSearch;
                    ASSERT(m_lSize == pSample->GetSize());

                    //  In case an error occurs and GetBuffer is not
                    //  followed by AddBuffer fix m_pbStartValid
                    if (m_lValid == 0) {
                        m_pbStartValid = pbBuffer;
                    }

                    m_pbNext += m_lSize;
                    if (m_pbNext == m_pBuffer + m_dwTotalSize ) {
                        m_pbNext = m_pBuffer;
                    }
					ASSERT(pSample->m_cRef == 0);
					pSample->m_cRef = 1; //protected by lock
	
                    break;
                } else {
                    pSearch = m_lFree.Next(pSearch);
                }

            }
            if (pSample == NULL) {
                /*  If there were some samples but just not ours someone
                    else may be waiting
                */
                if ( m_lFree.GetCount() != 0) {
                    NotifySample();
					//DbgLog((LOG_TRACE, 2, TEXT("----> Alloc missing a next buffer free, (%d)"), m_lFree.GetCount() ));
                }
                SetWaiting();
            }
		}

		/* If we didn't get a sample then wait for the list to signal */
		if (pSample) {
			break;
		}

        ASSERT(m_hSem != NULL);
		WaitForSingleObject(m_hSem, INFINITE); //ZQ.

    }

    *ppBuffer = pSample;
	if ( m_bStopAlloc )
	{
		return VFW_E_NOT_RUNNING;
	}

#ifdef VFW_S_CANT_CUE
    /* Addref the buffer up to one. On release
       back to zero instead of being deleted, it will requeue itself by
       calling the ReleaseBuffer member function. NOTE the owner of a
       media sample must always be derived from CBaseAllocator */
    //ASSERT(pSample->m_cRef == 0);
    //pSample->m_cRef = 1;
#else
    /* This QueryInterface should addref the buffer up to one. On release
       back to zero instead of being deleted, it will requeue itself by
       calling the ReleaseBuffer member function. NOTE the owner of a
       media sample must always be derived from CBaseAllocator */

    HRESULT hr = pSample->QueryInterface(IID_IMediaSample, (void **)ppBuffer);

    /* For each sample outstanding, we need to AddRef ourselves on his behalf
       he cannot do it, as there is no correct ordering of his release and his
       call to ReleaseBuffer as both could destroy him. We release this count
       in ReleaseBuffer, called when the sample's count drops to zero */

    AddRef();
#endif
    return NOERROR;
}

STDMETHODIMP CSequentialAllocator::ReturnBuffer(  IMediaSample *pSample )
{
	PBYTE pbData;
	long nRef;
	pSample->GetPointer( &pbData );
	nRef = SafeRelease( pSample );
	{
		CAutoLock cObjectLock(this);
		if ( m_pbNext > pbData )
			m_pbNext = pbData; 

	}
	ASSERT( nRef == 0 );
	return NOERROR;
}

HRESULT CSequentialAllocator::GetBuffer0(IMediaSample **ppBuffer, DWORD dwFlags )
{
    UNREFERENCED_PARAMETER(dwFlags);
    CMediaSample *pSample=NULL;

    *ppBuffer = NULL;
    for (;!m_bStopAlloc;)
    {
        {  // scope for lock
            CAutoLock cObjectLock(this);

            /* Check we are committed */
            if (!m_bCommitted) {
                return VFW_E_NOT_COMMITTED;
            }


            pSample = (CMediaSample *) m_lFree.RemoveHead();
            if (pSample == NULL) {
if ( m_lWaiting )
	trace_semaphore++;
                SetWaiting();
            } else
			{
				ASSERT(pSample->m_cRef == 0);
				pSample->m_cRef = 1;
			}

        }

        /* If we didn't get a sample then wait for the list to signal */

        if (pSample) {
            break;
        }
        if (dwFlags & AM_GBF_NOWAIT) {
            return VFW_E_TIMEOUT;
        }
        ASSERT(m_hSem != NULL);
		trace_wait++;
		
        WaitForSingleObject(m_hSem, INFINITE);

    }

    *ppBuffer = pSample;
	if ( m_bStopAlloc )
	{
		return VFW_E_NOT_RUNNING;
	}

    /* Addref the buffer up to one. On release
       back to zero instead of being deleted, it will requeue itself by
       calling the ReleaseBuffer member function. NOTE the owner of a
       media sample must always be derived from CBaseAllocator */



    return NOERROR;
}

HRESULT CSequentialAllocator::Alloc()
{
    CAutoLock lck(this);
    if (m_parSamples != NULL) {
        delete [] m_parSamples;
    }
    m_parSamples = new LPCMEDIASAMPLE[m_lCount];
    if (m_parSamples == NULL) {
        return E_OUTOFMEMORY;
    }
    HRESULT hr = CMemAllocator::Alloc();

	/*
	 * Here we go through the memory that CMemAllocator has allocated for us,
	 * which is contiguous, and then setup our m_parSamples array to point
	 * to the memory address of the data pointer for each of those samples.
	 */
    if (S_OK == hr) {
        ASSERT(m_lCount == m_lFree.GetCount());

        /* Find the smallest */
        CMediaSample *pSample = m_lFree.Head();

        m_pBuffer = (PBYTE)-1;
        for (; pSample != NULL; pSample = m_lFree.Next(pSample)) {
            PBYTE pbTemp;
            pSample->GetPointer(&pbTemp);
            if (m_pBuffer > pbTemp) {
                m_pBuffer = pbTemp;
            }
        }
        pSample = m_lFree.Head();
        for ( ;pSample != NULL; pSample = m_lFree.Next(pSample)) {
            PBYTE pbTemp;
            pSample->GetPointer(&pbTemp);
            m_parSamples[BufferIndex(pbTemp)] = pSample;
        }
		
    }
    m_pbStartValid = m_pBuffer;
    m_pbNext       = m_pBuffer;
	m_dwTotalSize  = m_lSize * m_lCount;
    ASSERT(m_lValid == 0);
    return hr;
}


/*  Get buffer index */
int CSequentialAllocator::BufferIndex(PBYTE pbBuffer)
{
    int iPos = (pbBuffer - m_pBuffer) / m_lSize;
    ASSERT(iPos <= m_lCount && iPos >= 0);
    return iPos;
}
/*  Given an address get the IMediaSample pointer -
    NB needs optimizing
*/
CMediaSample *CSequentialAllocator::SampleFromBuffer(PBYTE pBuffer)
{
    return m_parSamples[BufferIndex(pBuffer)];
}

/*  Add a buffer to the valid list (open list)*/
void CSequentialAllocator::OpenBuffer( CMediaSample *pSample )
{
    /*  Don't get fooled by 0 length buffers ! */
    if (pSample->GetActualDataLength() == 0) {
        return;
    }
    //pSample->AddRef(); //ZQ
	SafeAddRef( pSample );

    m_lValid += pSample->GetActualDataLength(); 
}

/*  Step through valid data */
HRESULT CSequentialAllocator::CloseBuffer(LONG lAdvance)
{
    ASSERT(lAdvance <= m_lValid);

    /*  For every sample boundary we step over we should Release()
        a buffer
    */
    //DbgLog((LOG_TRACE, 4, TEXT("Advance(%d) - valid %d"), lAdvance, m_lValid));
    int iStart = BufferIndex(m_pbStartValid);
    int iEnd   = BufferIndex(m_pbStartValid + lAdvance);
    m_lValid -= lAdvance;
    m_pbStartValid += lAdvance;

    /*  If we're at the end and the last buffer wasn't full move on
        to the next
    */
    if (m_lValid == 0 && (m_pbStartValid - m_pBuffer) % m_lSize != 0) {
        iEnd++;
        m_pbStartValid = m_pBuffer + m_lSize * iEnd;
    }
    while (iStart != iEnd) {
        //m_parSamples[iStart]->Release(); //ZQ
		SafeRelease( m_parSamples[iStart] );
        iStart++;
    }


    ASSERT(m_lValid <= (m_lCount * m_lSize) / 2);

    //if (m_pbStartValid + m_lValid == m_pBuffer + m_dwTotalSize ) 
	if ( m_pbStartValid + m_lValid > m_pBuffer + m_dwTotalSize - m_lSize ) 
	{
		if ( m_pbStartValid + m_lValid != m_pBuffer + m_dwTotalSize  ) 
			DbgLog((LOG_TRACE, 3, TEXT("------> Wrapped not at bottom(0x%x) - "), m_pBuffer + m_dwTotalSize-(m_pbStartValid + m_lValid)));

        return Wrap();
    } else {
        return S_OK;
    }
}

/*  Get the valid part */
PBYTE CSequentialAllocator::GetOpenBufferData(LONG *plValid)
{
    *plValid = m_lValid;
    return m_pbStartValid;
}

/*  Wrap end to go back to start */
HRESULT CSequentialAllocator::Wrap(void)
{
    if (m_lValid != 0) {

        /*  Make sure the copy will work */
        ASSERT((DWORD)m_lValid <= (m_dwTotalSize) / 2);
        IMediaSample *pSample;

        /*  These samples will be AddRef'd already */
        int nBuffers = (m_lValid + m_lSize - 1) / m_lSize;
        int iFirst = BufferIndex(m_pbStartValid);
        int iLast = BufferIndex(m_pbStartValid + m_lValid);
        if (0 == (m_pbStartValid + m_lValid - m_pBuffer) % m_lSize) {
            iLast--;
        }
        ASSERT(nBuffers > 0);
        ASSERT(iFirst <= iLast);

        ASSERT(nBuffers <= m_lCount / 2);
		int i;
        for (i = 0; i < nBuffers; i++) {
            HRESULT hr = GetBuffer(&pSample);
            if (FAILED(hr)) {
				DbgLog((LOG_TRACE, 1, TEXT("ERROR: Failed to get buffer to Wrap data hr=0x%x"), hr ));
				m_uFailedNum++;
                return hr;
            }
            ASSERT(pSample == m_parSamples[i]);
        }

        /*  Now copy the data back to the start */
        CopyMemory((PVOID)(m_pBuffer + m_lSize * nBuffers - m_lValid),
                   (PVOID)m_pbStartValid,
                   m_lValid);
        m_pbStartValid = m_pBuffer + m_lSize * nBuffers - m_lValid;

        /*  Release the last buffers since we've effectively
            transferred the ref count to the new buffers with GetBuffer
        */
        for ( i = iFirst; i <= iLast; i++) {
            //m_parSamples[i]->Release(); 
			SafeRelease( m_parSamples[i] );			
        }
    } else {
        m_pbStartValid = m_pBuffer;
        m_pbNext = m_pBuffer;
    }
    return S_OK;
}


/*  Flush the allocator - just discard all the data in it */
void CSequentialAllocator::Flush()
{
	if ( m_pbStartValid == NULL )
		return;
	CloseBuffer(m_lValid);
}

void CSequentialAllocator::Stop( ) 
{
	m_bStopAlloc = TRUE; 

	//ReleaseSemaphore(m_hSem, m_lWaiting, NULL); 
	//m_lWaiting = 0;

	if ( m_lWaiting )
	{
		long semaphre_count;
		while ( m_lWaiting-- )
		{
			ReleaseSemaphore( m_hSem, 1, &semaphre_count ); 
			if ( semaphre_count >= 0 ) break;
		}
		m_lWaiting = 0;
	}
//	DbgLog((LOG_TRACE, 2, TEXT("Allocator <%s> Stop"), LPCTSTR(CWtoT(m_pName)) ));
}

void CSequentialAllocator::Run( )
{
	m_uFailedNum = 0;
	if ( m_uFailedNum )
	{
		DbgLog((LOG_TRACE, 1, TEXT("ERROR: Failed to get buffer to Wrap data %d"), m_uFailedNum ));
	}
	m_bStopAlloc = FALSE;
	Flush( );
//	DbgLog((LOG_TRACE, 2, TEXT("Allocator <%s> (free:%d) Run"), LPCTSTR(CWtoT(m_pName)), m_lFree.GetCount() ));

	if ( 0 && m_lFree.GetCount() < m_lCount )
	{
		int i;
		for ( i = 0; i<m_lCount; i++ )
		{
			if ( m_parSamples[i]->m_cRef )
			{
				DbgLog((LOG_TRACE, 2, TEXT("!!!Catch an unfree sample %d, force to release it."), i ));
				SafeRelease( m_parSamples[i] );
			}
		}
		for ( i = 0; i<m_lCount; i++ )
		{
			if ( !IsFreeSample( m_parSamples[i] ) )
			{
				int ref = m_parSamples[i]->AddRef()-1;
				m_parSamples[i]->Release();
				DbgLog((LOG_TRACE, 2, TEXT("!!!Catch a lost sample %d 0x%x, %d, force to release it."), i, ref,  m_parSamples[i]->GetActualDataLength(), ref ));
			}
		}
	}
}

BOOL CSequentialAllocator::IsAllSampleFree(  )
{
	if ( !m_bCommitted )
		return TRUE;

	if ( m_lFree.GetCount() < m_lCount )
	{
		BOOL ret = TRUE;
		int i;
		for ( i = 0; i<m_lCount; i++ )
		{
			if ( m_parSamples[i]->m_cRef )
			{
				DbgLog((LOG_TRACE, 2, TEXT("!!!Catch an unfree sample %d, force to release it."), i ));
				SafeRelease( m_parSamples[i] );
				ret = FALSE;
			}
		}
		for ( i = 0; i<m_lCount; i++ )
		{
			if ( !IsFreeSample( m_parSamples[i] ) )
			{
				int ref = m_parSamples[i]->AddRef()-1;
				m_parSamples[i]->Release();
				DbgLog((LOG_TRACE, 2, TEXT("!!!Catch a lost sample %d 0x%x, %d, force to release it."), i, ref,  m_parSamples[i]->GetActualDataLength(), ref ));
				ret = FALSE;
			}
		}

		return ret;

	} else
		return TRUE;
}

BOOL CSequentialAllocator::IsFreeSample( IMediaSample * pCheckSample )
{
	CMediaSample *pSample;
	pSample = m_lFree.Head();
	for ( ;pSample != NULL; pSample = m_lFree.Next(pSample)) 
	{
		if ( pSample == pCheckSample )
			return TRUE;
    }

	return FALSE;
}

void CSequentialAllocator::_TraceFreeBufferNum()
{
//	DbgLog((LOG_TRACE, 2, TEXT(">>>Allocator <%s> (free:%d)"), LPCTSTR(CWtoT(m_pName)), m_lFree.GetCount() ));
}

//////////////////////////////////////////////////////////////////////////

CSubAllocator::CSubAllocator(  LPUNKNOWN  pUnk, HRESULT   *phr, CSequentialAllocator *pAlloc ) : 
	CBaseAllocator(NAME("CSubAllocator SageTV"), pUnk, phr),
    m_pAlloc(pAlloc)
{
	m_pDeMuxFilter = NULL;
	DbgLog((LOG_TRACE, 2, TEXT("CSubAllocator SageTV created") ));
}
	
CSubAllocator::~CSubAllocator( )
{
	DbgLog((LOG_TRACE, 2, TEXT("CSubAllocator SageTV released") ));
}

CMediaSample* CSubAllocator::GetSample(PBYTE pbData, DWORD dwLen)
{
    HRESULT hr = S_OK;
    CMediaSample *pSample = new CMediaSample(
                                    NAME("SubSample"),
                                    this,
                                    &hr,
                                    pbData,
                                    dwLen);
    if (pSample != NULL) {

        //long ref = m_pAlloc->SampleFromBuffer(pbData)->AddRef();
		long ref = m_pAlloc->SafeAddRef( m_pAlloc->SampleFromBuffer(pbData) );
		//DbgLog((LOG_TRACE, 2, TEXT("==========> super allocator AddRef ref:%d"), ref ));

        /*  AddRef() ourselves too to conform to the rules */
        pSample->AddRef();

        /*  Make sure WE don't go away too ! */
        AddRef();

    }
    return pSample;
}

STDMETHODIMP CSubAllocator::ReleaseBuffer(IMediaSample * pSample)
{
    /*  Free the superallocator's buffer */
    CMediaSample *pMediaSample = (CMediaSample *)pSample;
    PBYTE pBuffer;
    pSample->GetPointer(&pBuffer);

	if ( m_pDeMuxFilter != NULL )
		m_pDeMuxFilter->ReleasedSample( pSample );

	long ref = m_pAlloc->SafeRelease( m_pAlloc->SampleFromBuffer(pBuffer) );

    delete pMediaSample;

    Release();

    return NOERROR;
}

//////////////////////////////////////////////////////////////////////////

CLocalAllocator::CLocalAllocator( PBYTE &pbBuffer, int cbBuffer, HRESULT   *phr ) 
: CBaseAllocator( TEXT("Local Allocator"), NULL, phr, FALSE, FALSE ),
m_pBuffer( pbBuffer ),
m_dwSize( cbBuffer )
{
}

CLocalAllocator::~CLocalAllocator( )
{
}

CMediaSample* CLocalAllocator::GetSample( )
{
	HRESULT hr = S_OK;
    CMediaSample *pSample = new CMediaSample(
                                    NAME("LocalSample"),
                                    this,
                                    &hr,
                                    m_pBuffer,
                                    m_dwSize );
    pSample->AddRef();
	AddRef();
    return pSample;
}

STDMETHODIMP CLocalAllocator::ReleaseBuffer(IMediaSample * pSample)
{
    /*  Free the superallocator's buffer */
    CMediaSample *pMediaSample = (CMediaSample *)pSample;
    delete pMediaSample;
    return NOERROR;
}

STDMETHODIMP CLocalAllocator::SetProperties( ALLOCATOR_PROPERTIES* pRequest,
		ALLOCATOR_PROPERTIES* pActual) 
{ 
	HRESULT hr = CBaseAllocator::SetProperties( pRequest, pActual );
	return hr;
	//if ( hr == S_OK )
	//	*pActual = *pRequest;
	//return S_OK; 
};

void CLocalAllocator::SetupBuffer( PBYTE &pbBuffer, int cbBuffer )
{
	m_pBuffer = pbBuffer;
	m_dwSize = cbBuffer;
}
////////////////////////////////////////////////////////////////////////////////////
CBukAllocator::CBukAllocator(  LPUNKNOWN  pUnk, HRESULT   *phr, CSequentialAllocator *pAlloc ) : 
	CBaseAllocator(NAME("CBukAllocator SageTV"), pUnk, phr),
    m_pAlloc(pAlloc)
{
	m_pDeMuxFilter = NULL;
	DbgLog((LOG_TRACE, 2, TEXT("CBukAllocator SageTV created") ));
}
	
CBukAllocator::~CBukAllocator( )
{
	DbgLog((LOG_TRACE, 2, TEXT("CBukAllocator SageTV released") ));
}

void CBukAllocator::SetProps( )
{
	ALLOCATOR_PROPERTIES Props;
	m_pAlloc->GetProperties( &Props );
	m_dwSampleSize = Props.cbBuffer;
	m_dwSampleNum = Props.cBuffers;
	m_dwTotalSize = m_dwSampleSize * m_dwSampleNum;
}

CMediaSample* CBukAllocator::GetSample(PBYTE pbData, DWORD dwLen)
{
    HRESULT hr = S_OK;
    CMediaSample *pSample = new CMediaSample(
                                    NAME("BukSample"),
                                    this,
                                    &hr,
                                    pbData,
                                    dwLen);
    if (pSample != NULL) 
	{
		long ref;
		DWORD dwActualLen = 0;
		while ( dwActualLen < dwLen )
		{
			ref = m_pAlloc->SafeAddRef( m_pAlloc->SampleFromBuffer(pbData) );
			pbData += m_dwSampleSize;
			dwActualLen += m_dwSampleSize;
		}

        pSample->AddRef();
        AddRef();

    }
    return pSample;
}


STDMETHODIMP CBukAllocator::ReleaseBuffer(IMediaSample * pSample)
{
    /*  Free the superallocator's buffer */
    CMediaSample *pMediaSample = (CMediaSample *)pSample;
    PBYTE pBuffer;
	DWORD dwSize, dwRelaseSize;
	long ref;

	if ( m_pDeMuxFilter != NULL )
		m_pDeMuxFilter->ReleasedSample( pSample );

    pSample->GetPointer(&pBuffer);
	dwSize = pSample->GetSize();

	dwRelaseSize = 0; 
	while ( dwRelaseSize < dwSize )
	{
		ref = m_pAlloc->SafeRelease( m_pAlloc->SampleFromBuffer(pBuffer) );
		dwRelaseSize += m_dwSampleSize;
		pBuffer += m_dwSampleSize;
	}

    delete pMediaSample;

    Release();

    return NOERROR;
}

HRESULT CBukAllocator::GetBukBuffer( DWORD dwBlockSize, IMediaSample **ppSample )
{
	HRESULT hr = S_OK;
	IMediaSample *pSample=NULL, *pFirstSample=NULL, *pFailedSmaple=NULL;
	CMediaSample *pBukSample=NULL;
	PBYTE pbData, pbLastData = 0;
	DWORD dwActualSize, dwSize;
	//CGenericList<IMediaSample*> m_lSampleList;
	ASSERT( m_dwTotalSize/2 > dwBlockSize );

	dwActualSize = 0;
    while ( dwActualSize < dwBlockSize )
	{
        HRESULT hr = m_pAlloc->GetBuffer(&pSample);
        if (FAILED(hr)) 
		{
			if ( hr != VFW_E_NOT_RUNNING )
				DbgLog((LOG_TRACE, 1, TEXT("ERROR: Failed to get buffer to Wrap data hr=0x%x"), hr ));
			break;
        }
		if ( pFirstSample == NULL )
			pFirstSample = pSample;

		//data is continue, not wrap
		pSample->GetPointer( &pbData );
		if ( pbData > pbLastData ) 
			pbLastData = pbData;
		else
		{
			//wrap buffer
			pSample->Release();
			break;
		}
		dwSize = pSample->GetSize();
		ASSERT( dwSize == m_dwSampleSize );
		dwActualSize += dwSize;
    }

	if ( pFirstSample )
	{
		pFirstSample->GetPointer( &pbData );
		pBukSample = new CMediaSample(
                                    NAME("BukSample"),
                                    this,
                                    &hr,
                                    pbData,
                                    dwActualSize);
	}
	if ( pBukSample != NULL )
	{
        pBukSample->AddRef();
        AddRef();
	} else
		return hr;

	if ( dwActualSize >= dwBlockSize )
	{
		*ppSample = pBukSample;
		return NOERROR;
	} 

	pbLastData = 0;
	pFailedSmaple = pBukSample;
	pFirstSample = NULL;
	dwActualSize = 0;
    while ( hr == S_OK && dwActualSize < dwBlockSize )
	{
        hr = m_pAlloc->GetBuffer(&pSample);
        if (FAILED(hr)) {
			if ( hr != VFW_E_NOT_RUNNING )
				DbgLog((LOG_TRACE, 1, TEXT("ERROR: Failed to get buffer to Wrap data hr=0x%x"), hr ));
			break;
        }
		if ( pFirstSample == NULL )
			pFirstSample = pSample;

		//data is continue, not wrap
		pSample->GetPointer( &pbData );
		if ( pbData > pbLastData ) 
			pbLastData = pbData;
		else
		{
			ASSERT( 0 );
		}
		dwSize = pSample->GetSize();
		ASSERT( dwSize == m_dwSampleSize );
		dwActualSize += dwSize;
    }
	if ( pFirstSample )
	{
		pFirstSample->GetPointer( &pbData );
		pBukSample = new CMediaSample(
                                    NAME("BukSample"),
                                    this,
                                    &hr,
                                    pbData,
                                    dwActualSize );
	}
	if ( pBukSample != NULL )
	{
        pBukSample->AddRef();
        AddRef();
	}
	*ppSample = pBukSample;
	pFailedSmaple->Release();

	return hr;
}

HRESULT CBukAllocator::ReleaseUnusedBuffer( DWORD dwUsedBytes, IMediaSample *pSample )
{
	PBYTE pBuffer;
	DWORD dwSize, dwRelaseSize, dwUsedSampeSize;
	long ref;
	CMediaSample *pMediaSample = (CMediaSample *)pSample;

    pSample->GetPointer(&pBuffer);
	dwSize = pSample->GetSize();
	dwUsedSampeSize = dwUsedBytes;
	if ( dwUsedBytes%m_dwSampleSize )
		dwUsedSampeSize += m_dwSampleSize - dwUsedBytes%m_dwSampleSize;

	pBuffer += dwUsedSampeSize;
	dwRelaseSize = dwUsedSampeSize; 
	while ( dwRelaseSize < dwSize )
	{
		ref = m_pAlloc->ReturnBuffer( m_pAlloc->SampleFromBuffer(pBuffer) );
		dwRelaseSize += m_dwSampleSize;
		pBuffer += m_dwSampleSize;
	}
	pSample->GetPointer(&pBuffer);
	pMediaSample->SetPointer( pBuffer, dwUsedSampeSize );
	return NOERROR;
}
