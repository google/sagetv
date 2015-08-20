/*****************************************************************************

Copyright 2015 The SageTV Authors. All Rights Reserved.

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/

package sage.jep.function;

import sage.jep.*;

public class Add extends PostfixMathCommand
{

  public Add()
  {
    numberOfParameters = -1;
  }

  /**
   * Calculates the result of applying the "+" operator to the arguments from
   * the stack and pushes it back on the stack.
   */
  public void run(sage.Catbert.FastStack stack) throws ParseException {
    Object sum = stack.pop();
    Object param;
    int i = 1;

    // repeat summation for each one of the current parameters
    while (i < curNumberOfParameters) {
      // get the parameter from the stack
      param = stack.pop();

      // add it to the sum (order is important for String arguments)
      sum = add(param, sum);

      i++;
    }

    stack.push(sum);

    return;
  }

  public Object add(Object param1, Object param2) throws ParseException
  {
    if ((param1 instanceof String) || (param2 instanceof String)) {
      return ((param1 == null) ? "null" : param1.toString()) +
          ((param2 == null) ? "null" : param2.toString());
    }
    else if (param1 instanceof Number && param2 instanceof Number)
    {
      if (param1 instanceof Double || param2 instanceof Double)
        return new Double(((Number)param1).doubleValue() + ((Number)param2).doubleValue());
      else if (param1 instanceof Float || param2 instanceof Float)
        return new Float(((Number)param1).floatValue() + ((Number)param2).floatValue());
      else if (param1 instanceof Long || param2 instanceof Long)
        return new Long(((Number)param1).longValue() + ((Number)param2).longValue());
      else
        return new Integer(((Number)param1).intValue() + ((Number)param2).intValue());
    }
    else if (param1 == null && param2 == null)
      return null;
    throw new ParseException("Invalid parameter type");
  }

}
