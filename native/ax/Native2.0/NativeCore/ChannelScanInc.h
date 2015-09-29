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
#ifndef _CHANNEL_SCAN_INC_H_
#define _CHANNEL_SCAN_INC_H_
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif


inline int STR_FREQ( char *p, int n, uint32_t frq )
{
	return snprintf( p, n, "frq:%d ", frq );
}
inline int STR_BAND( char *p, int n, uint16_t band )
{
	if ( band == 0 ) return 0;
	return snprintf( p, n, "band:%d ", band );
}
inline int STR_MODE( char *p, int n, uint8_t mode )
{
	if ( mode == 0 ) return 0;
	return snprintf( p, n, "mode:%d ", mode );
}
inline int STR_SYMBOL_RATE( char *p, int n, uint32_t rate )
{
	if ( rate == 0 ) return 0;
	return snprintf( p, n, "rate:%d ", rate );
}
inline int STR_MODULATION( char *p, int n, uint8_t mod )
{
	if ( mod == 0 ) return 0;
	return snprintf( p, n, "mod:%d ", mod );
}
inline int STR_POL( char *p, int n, uint8_t pol )
{
	if ( pol == 0 ) return 0;
	return snprintf( p, n, "pol:%d ", pol );
}
inline int STR_FEC_IN( char *p, int n, uint8_t fec_in )
{
	if ( fec_in == 0 ) return 0;
	return snprintf( p, n, "fect_in:%d ", fec_in );
}
inline int STR_FEC_RATE_IN( char *p, int n, uint8_t fec_in_rate )
{
	if ( fec_in_rate == 0 ) return 0;
	return snprintf( p, n, "fect_rate_in:%d ", fec_in_rate );
}
inline int STR_FEC_OUT( char *p, int n, uint8_t fec_out )
{
	if ( fec_out == 0 ) return 0;
	return snprintf( p, n, "fect_out:%d ", fec_out );
}
inline int STR_FEC_RATE_OUT( char *p, int n, uint8_t fec_out_rate )
{
	if ( fec_out_rate == 0 ) return 0;
	return snprintf( p, n, "fect_rate_out:%d ", fec_out_rate );
}

#ifdef __cplusplus
}
#endif


#endif

