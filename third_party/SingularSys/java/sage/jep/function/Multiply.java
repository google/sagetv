/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep.function;

import sage.jep.*;

public class Multiply extends PostfixMathCommand
{

  public Multiply() {
    numberOfParameters = -1;
  }

  public void run(sage.Catbert.FastStack stack) throws ParseException
  {
    Object product = stack.pop();
    Object param;
    int i = 1;

    // repeat summation for each one of the current parameters
    while (i < curNumberOfParameters) {
      // get the parameter from the stack
      param = stack.pop();

      // multiply it with the product
      product = mul(product, param);

      i++;
    }

    stack.push(product);

    return;
  }

  public Object mul(Object param1, Object param2)
      throws ParseException {

    if (!(param1 instanceof Number))
    {
      try{
        param1 = new Long(param1.toString());
      }
      catch (Exception x)
      {
        try{
          param1 = new Double(param1.toString());
        }catch (Exception e){ throw new ParseException("Invalid parameter type"); }
      }
    }
    if (!(param2 instanceof Number))
    {
      try{
        param2 = new Long(param2.toString());
      }
      catch (Exception x)
      {
        try{
          param2 = new Double(param2.toString());
        }catch (Exception e){ throw new ParseException("Invalid parameter type"); }
      }
    }
    if (param1 instanceof Number && param2 instanceof Number)
    {
      if (param1 instanceof Double || param2 instanceof Double)
        return new Double(((Number)param1).doubleValue() * ((Number)param2).doubleValue());
      else if (param1 instanceof Float || param2 instanceof Float)
        return new Float(((Number)param1).floatValue() * ((Number)param2).floatValue());
      else if (param1 instanceof Long || param2 instanceof Long)
        return new Long(((Number)param1).longValue() * ((Number)param2).longValue());
      else
        return new Integer(((Number)param1).intValue() * ((Number)param2).intValue());
    }
    throw new ParseException("Invalid parameter type");
  }

}
