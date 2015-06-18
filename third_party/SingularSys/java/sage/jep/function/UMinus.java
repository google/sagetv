/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep.function;

import sage.jep.*;

public class UMinus extends PostfixMathCommand
{
  public UMinus() {
    numberOfParameters = 1;
  }

  public void run(sage.Catbert.FastStack inStack) throws ParseException {
    inStack.push(umin(inStack.pop()));
    return;
  }

  public Object umin(Object param) throws ParseException
  {
    if (param instanceof Double)
      return new Double(-((Double)param).doubleValue());
    else if (param instanceof Float)
      return new Float(-((Float)param).floatValue());
    else if (param instanceof Long)
      return new Long(-((Number)param).longValue());
    else if (param instanceof Number) {
      return new Integer(-((Number)param).intValue());
    }

    throw new ParseException("Invalid parameter type");
  }
}
