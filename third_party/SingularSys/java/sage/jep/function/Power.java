/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep.function;

import sage.jep.*;

public class Power extends PostfixMathCommand
{
  public Power()
  {
    numberOfParameters = 2;
  }

  public void run(sage.Catbert.FastStack inStack)
      throws ParseException {

    Object param2 = inStack.pop();
    Object param1 = inStack.pop();

    inStack.push(power(param1, param2));
  }

  public Object power(Object param1, Object param2)
      throws ParseException {

    if (param1 instanceof Number) {
      if (param2 instanceof Number) {
        return power((Number)param1, (Number)param2);
      }
    }
    throw new ParseException("Invalid parameter type");
  }


  public Object power(Number d1, Number d2)
  {
    return new Double(Math.pow(d1.doubleValue(),d2.doubleValue()));
  }
}
