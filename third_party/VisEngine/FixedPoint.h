#ifndef __FIXED_POINT_H__
#define __FIXED_POINT_H__

#ifdef FIXEDPOINT
// Use 16.16 math
#define mathtype int
#define mathval(x) ((int)((x)*65536.0f))
#define mathmul(x,y) ((((long long)x)*((long long) y))>>16LL)
#define mathadd(x,y) (x+y)
#define mathdiv(x,y) (((long long)x<<16LL)/((long long) y))
#define mathtofloat(x) ((float)x/65536.0f)
#define mathtoint(x) (x>>16)
#define mathone (65536)
#define mathzero (0)
#define mathfromint(x) (x<<16)
#define mathabs(x) ((x<0) ? -x : x)
#else
#define mathtype float
#define mathval(x) ((float)(x))
#define mathmult(x,y) (x*y)
#define mathadd(x,y) (x+y)
#define mathdiv(x,y) (x/y)
#define mathtofloat(x) (x)
#define mathone (1.0f)
#define mathzero (0.0f)
#define mathtoint(x) ((int)x)
#endif

#endif // __FIXED_POINT_H__
