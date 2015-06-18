#pragma once
#include <strsafe.h>

#ifdef _DEBUG
#include <crtdbg.h>
#include <assert.h>
#endif

namespace MediaFoundationSamples
{
    //--------------------------------------------------------------------------------------
    // Debug logging functions
    // Description: Contains debug logging functions.
    //
    //     Initialize: Opens a logging file with the specified file name.
    //     Trace: Writes a sprintf-formatted string to the logging file.
    //     Close: Closes the logging file and reports any memory leaks.
    //
    // To enable logging in debug builds, #define USE_LOGGING.
    // The TRACE_INIT, TRACE, and TRACE_CLOSE macros are mapped to the logging functions.
    // 
    // In retail builds, these macros are mapped to nothing.
    //--------------------------------------------------------------------------------------

#ifdef _DEBUG

    class DebugLog
    {
    private:
        static HANDLE& File()
        {
            static HANDLE hFile = INVALID_HANDLE_VALUE;

            return hFile;
        }

    public:
        static HRESULT Initialize(const WCHAR *sFileName)
        {
            // Close any existing file.
            if (File() != INVALID_HANDLE_VALUE)
            {
                CloseHandle(File());
                File() = INVALID_HANDLE_VALUE;
            }

            // Open the new logging file.

            HRESULT hr = S_OK;
            if (sFileName)
            {
                File() = CreateFile(
                    sFileName, 
                    GENERIC_WRITE,
                    FILE_SHARE_READ,
                    NULL,
                    CREATE_ALWAYS,
                    0, 
                    NULL);

                if (File() == INVALID_HANDLE_VALUE)
                {
                    hr = HRESULT_FROM_WIN32(GetLastError());
                }
                else
                {
                    // Redirect debug messages to the logging file.
                    _CrtSetReportMode(_CRT_WARN, _CRTDBG_MODE_FILE);
                    _CrtSetReportFile(_CRT_WARN, File());
                }
            }
            else
            {
                // No file name, send debug messages to the debug window.
                _CrtSetReportMode(_CRT_WARN, _CRTDBG_MODE_DEBUG);
            }
            return hr;

        }

        static void  Trace(const WCHAR *sFormatString, ...)
        {
            HRESULT hr = S_OK;
            va_list va;

            const DWORD TRACE_STRING_LEN = 512;

            WCHAR message[TRACE_STRING_LEN];

            va_start(va, sFormatString);
            hr = StringCchVPrintf(message, TRACE_STRING_LEN, sFormatString, va);
            va_end(va);

            if (SUCCEEDED(hr))
            {
                _CrtDbgReport(_CRT_WARN, NULL, NULL, NULL, "%S\r\n", message);
            }
        }

        static void Close()
        {
            int bLeak = _CrtDumpMemoryLeaks();
            assert( bLeak == FALSE );

            CloseHandle(File());
        }
    };

#else
// For retail builds, turn off USE_LOGGING
#undef USE_LOGGING
#endif

#ifdef USE_LOGGING
    #define TRACE_INIT(x) DebugLog::Initialize(x)
    #define TRACE(x) DebugLog::Trace x
    #define TRACE_CLOSE() DebugLog::Close()

    // Log HRESULTs on failure.
    inline HRESULT _LOG_HRESULT(HRESULT hr, const char* sFileName, long lLineNo)
    {
        if (FAILED(hr))
        {
            TRACE((L"%S", sFileName)); 
            TRACE((L"Line: %d hr=0x%X", lLineNo, hr));
        }
        return hr;
    }

    #define LOG_HRESULT(hr) _LOG_HRESULT(hr, __FILE__, __LINE__)
    #define LOG_MSG_IF_FAILED(msg, hr) if (FAILED(hr)) { TRACE((msg)); }

#else
    #define TRACE_INIT(x) 
    #define TRACE(x) 
    #define TRACE_CLOSE()
    #define LOG_MSG_IF_FAILED(x, hr)
    #define LOG_HRESULT(hr)
    #define LOG_MSG_IF_FAILED(msg, hr)
#endif


} // namespace MediaFoundationSamples