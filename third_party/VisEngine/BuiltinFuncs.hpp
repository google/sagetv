//
// C++ Interface: BuiltinFuncs
//
// Description: 
//
//
// Author: Carmelo Piccione <carmelo.piccione@gmail.com>, (C) 2007
//
// Copyright: See COPYING file that comes with this distribution
//
//

#ifndef _BUILTIN_FUNCS_HPP
#define _BUILTIN_FUNCS_HPP

#include "Common.hpp"
#include "Func.hpp"
#include <cmath>
#include <cstdlib>
#include <cassert>

#include "RandomNumberGenerators.hpp"

#ifdef FIXEDPOINT
// Taken from fixed_func.cpp by Markus Trenkwalder

static const unsigned short sin_tab[] = {
#include "fixsintab.h"
};

static const int math2PI	= mathval(6.28318530717958647692f);
static const int mathR2PI = mathval(1.0f/6.28318530717958647692f);

static int fixcos16(int a) 
{
    int v;
    /* reduce to [0,1) */
    while (a < 0) a += math2PI;
    a = mathmul(a, mathR2PI);
    a += 0x4000;

    /* now in the range [0, 0xffff], reduce to [0, 0xfff] */
    a >>= 4;

    v = (a & 0x400) ? sin_tab[0x3ff - (a & 0x3ff)] : sin_tab[a & 0x3ff];
	v = mathmul(v, 1 << 16);
    return (a & 0x800) ? -v : v;
}

static int fixsin16(int a)
{
    int v;

    /* reduce to [0,1) */
    while (a < 0) a += math2PI;
    a = mathmul(a, mathR2PI);

    /* now in the range [0, 0xffff], reduce to [0, 0xfff] */
    a >>= 4;

    v = (a & 0x400) ? sin_tab[0x3ff - (a & 0x3ff)] : sin_tab[a & 0x3ff];
    v = mathmul(v, 1 << 16);
    return (a & 0x800) ? -v : v;
}

/* exp/ln based on :
 * fixedpoint.c - Fixed point library routines to perform
 *                16.16 fixed point math.
 *
 * Copyright (C) 2003 David A. Smith
*/

#define mathln2        45426

static int fixexp16(int x)
{
  int x_div_ln2;
  int int_x;
  int frac_x;
  int int_exp;
  int frac_exp;

  if (x > 0)
  {
    x_div_ln2 = mathdiv(x, mathln2);
    int_x = x_div_ln2 >> 16;
    frac_x = x_div_ln2 & 0x0000FFFF;

    int_exp = mathone;
    while (int_x)
    {
      int_exp <<= 1;
      int_x--;
    }

    // Pade approximation for exp2(x)
    // exp2(x) = (3 + x)/(3 - x)
    // accurate to within 1% over [0, 1]

    frac_exp = mathdiv((mathval(3) + frac_x),
                     (mathval(3) - frac_x));

    return mathmul(int_exp, frac_exp);
  }
  else if (x < 0)
  {
    return mathdiv(mathone, fixexp16(-x));
  }
  else
  {
    return mathone;
  }
}

static int fixln16(int x)
{
  int numerator;
  int denominator;
  int a = 0;

  // if x = y * 2^a then
  // ln(x) = ln(y * 2^a)
  // ln(x) = ln(y) + ln(2^a)
  // ln(x) = ln(y) + a * ln(2)

  if (x <= 0)
  {
    return 0;
  }
  else if (x > mathone)
  {
    while (x & 0xFFFF0000)
    {
      x >>= 1;
      a++;
    }
  }
  else if (x < mathone)
  {
    while (!(x & 0xFFFF0000))
    {
      x <<= 1;
      a--;
    }

    x >>= 1;
    a++;
  }

  // Pade approximation for ln(x+1)
  // ln(x+1) = x(6+x)/(6+4x)

  x -= mathone;

  numerator = mathmul(x, mathval(6) + x);

  denominator = mathval(6) + mathmul(mathval(4), x);

  return mathdiv(numerator, denominator) + mathmul(mathfromint(a), mathln2);
}

static int fixpow16(int x, int y)
{
    int yisint = ((y&0xFFFF)==0);
    // Special cases
    if(y==0) return mathone; // x**0 = 1
    if(x==0) return 0;
    if(y==mathone) return x;
    if(y==-mathone) return mathdiv(mathone, x);
    if(x<0 && !yisint) return 0; //invalid
    // If x is negative int we should add a int mode for y?
    int u = fixln16(x);
    int v = mathmul(u, y);
    return fixexp16(v);
}

#endif


/* Wrappers for all the builtin functions
   The arg_list pointer is a list of mathtypes. Its
   size is equal to the number of arguments the parameter
   takes */
class FuncWrappers {

/* Values to optimize the sigmoid function */
static const int R =  32767;
static const int RR = 65534;

public:

static inline mathtype int_wrapper(mathtype * arg_list) {

return mathval(floorf(mathtofloat(arg_list[0])));

}


static inline mathtype sqr_wrapper(mathtype * arg_list) {

return mathval(powf(2, mathtofloat(arg_list[0])));
}


static inline mathtype sign_wrapper(mathtype * arg_list) {

return -arg_list[0];
}

static inline mathtype min_wrapper(mathtype * arg_list) {

if (arg_list[0] > arg_list[1])
return arg_list[1];

return arg_list[0];
}

static inline mathtype max_wrapper(mathtype * arg_list) {

if (arg_list[0] > arg_list[1])
return arg_list[0];

return arg_list[1];
}

/* consult your AI book */
static inline mathtype sigmoid_wrapper(mathtype * arg_list) {
return mathval((RR / (1 + expf( -(((mathtype)(mathtofloat(arg_list[0]))) * mathtofloat(arg_list[1])) / R) - R)));
}


static inline mathtype bor_wrapper(mathtype * arg_list) {

return (mathtype)((int)arg_list[0] || (int)arg_list[1]) ? mathone : mathzero;
}

static inline mathtype band_wrapper(mathtype * arg_list) {
return (mathtype)((int)arg_list[0] && (int)arg_list[1])? mathone : mathzero;
}

static inline mathtype bnot_wrapper(mathtype * arg_list) {
return (mathtype)(!(int)arg_list[0])? mathone : mathzero;
}

static inline mathtype if_wrapper(mathtype * arg_list) {

if ((int)arg_list[0] == 0)
return arg_list[2];
return arg_list[1];
}


static inline mathtype rand_wrapper(mathtype * arg_list) {
mathtype l=mathone;

//  printf("RAND ARG:(%d)\n", (int)arg_list[0]);
if ((int)arg_list[0] > 0)
	l  = (mathtype) mathval(RandomNumberGenerators::uniformInteger((int)mathtofloat(arg_list[0])));

return l;
}

static inline mathtype equal_wrapper(mathtype * arg_list) {
	return (arg_list[0] == arg_list[1]) ? mathone : mathzero;
}


static inline mathtype above_wrapper(mathtype * arg_list) {

return (arg_list[0] > arg_list[1]) ? mathone : mathzero;
}


static inline mathtype below_wrapper(mathtype * arg_list) {

return (arg_list[0] < arg_list[1]) ? mathone : mathzero;
}

static mathtype sin_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
    return fixsin16(*arg_list);
#else
return mathval(sinf(mathtofloat(*arg_list)));
#endif
}


static inline mathtype cos_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
    return fixcos16(*arg_list);
#else
return mathval((cosf (mathtofloat(arg_list[0]))));
#endif
}

static inline mathtype tan_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
    return mathdiv(fixsin16(*arg_list),fixcos16(*arg_list));
#else
return mathval((tanf(mathtofloat(arg_list[0]))));
#endif
}

static inline mathtype asin_wrapper(mathtype * arg_list) {
return mathval((asinf (mathtofloat(arg_list[0]))));
}

static inline mathtype acos_wrapper(mathtype * arg_list) {
return mathval((acosf (mathtofloat(arg_list[0]))));
}

static inline mathtype atan_wrapper(mathtype * arg_list) {
return mathval((atanf (mathtofloat(arg_list[0]))));
}

static inline mathtype atan2_wrapper(mathtype * arg_list) {
return mathval((atan2f (mathtofloat(arg_list[0]), mathtofloat(arg_list[1]))));
}

static inline mathtype pow_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
return fixpow16(arg_list[0], arg_list[1]);
#else
return mathval((powf (mathtofloat(arg_list[0]), mathtofloat(arg_list[1]))));
#endif
}

static inline mathtype exp_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
return fixexp16(arg_list[0]);
#else
return mathval((expf(mathtofloat(arg_list[0]))));
#endif
}

static inline mathtype abs_wrapper(mathtype * arg_list) {
#ifdef FIXEDPOINT
return (arg_list[0]<0) ? -arg_list[0] : arg_list[0];
#else
return mathval((fabsf(mathtofloat(arg_list[0]))));
#endif
}

static inline mathtype log_wrapper(mathtype* arg_list) {
return mathval((logf (mathtofloat(arg_list[0]))));
}

static inline mathtype log10_wrapper(mathtype * arg_list) {
return mathval((log10f (mathtofloat(arg_list[0]))));
}

static inline mathtype sqrt_wrapper(mathtype * arg_list) {
return mathval((sqrtf (mathtofloat(arg_list[0]))));
}


static inline mathtype nchoosek_wrapper(mathtype * arg_list) {
unsigned long cnm = 1UL;
int i, f;
int n, m;

n = (int)mathtoint(arg_list[0]);
m = (int)mathtoint(arg_list[1]);

if (m*2 >n) m = n-m;
for (i=1 ; i <= m; n--, i++)
{
if ((f=n) % i == 0)
f   /= i;
else  cnm /= i;
cnm *= f;
}
return (mathtype)mathfromint(cnm);
}


static inline mathtype fact_wrapper(mathtype * arg_list) {


int result = 1;

int n = (int)mathtoint(arg_list[0]);

while (n > 1) {
result = result * n;
n--;
}
return (mathtype)mathfromint(result);
}
};

#include <map>
class BuiltinFuncs {

public:
    
    static int init_builtin_func_db();
    static int destroy_builtin_func_db();
    static int load_all_builtin_func();
    static int load_builtin_func( const std::string & name, mathtype (*func_ptr)(mathtype*), int num_args );

    static int insert_func( Func *func );
    static int remove_func( Func *func );
    static Func *find_func( const std::string & name );
private:
     static std::map<std::string, Func*> builtin_func_tree;
};

#endif
