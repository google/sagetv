/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep.function;

import sage.jep.*;

public class Not extends PostfixMathCommand
{
  public Not()
  {
    numberOfParameters = 1;

  }

  public void run(sage.Catbert.FastStack inStack)
      throws ParseException {

    Object param = inStack.pop();
    if (param instanceof Boolean)
    {
      inStack.push(!((Boolean)param).booleanValue() ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
    }
    else if (param != null)
    {
      inStack.push(param.toString().equalsIgnoreCase("true") ? Boolean.FALSE : Boolean.TRUE);
    }
    else
      inStack.push(Boolean.TRUE);
    //throw new ParseException("Invalid parameter type");
    return;
  }

}
