/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep.function;

import sage.jep.*;

public class Subtract extends PostfixMathCommand
{
  public Subtract()
  {
    numberOfParameters = 2;
  }

  public void run(sage.Catbert.FastStack inStack)
      throws ParseException {

    Object param2 = inStack.pop();
    Object param1 = inStack.pop();

    inStack.push(sub(param1, param2));

    return;
  }

  public Object sub(Object param1, Object param2)
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
      Number n1 = (Number) param1;
      Number n2 = (Number) param2;
      if (param1 instanceof Double || param2 instanceof Double)
        return new Double(n1.doubleValue() - n2.doubleValue());
      else if (param1 instanceof Float || param2 instanceof Float)
        return new Float(n1.floatValue() - n2.floatValue());
      else if (param1 instanceof Long || param2 instanceof Long)
        return new Long(n1.longValue() - n2.longValue());
      else //if (param1 instanceof Integer && param2 instanceof Integer)
        return new Integer(n1.intValue() - n2.intValue());
    }

    throw new ParseException("Invalid parameter type");
  }


}
