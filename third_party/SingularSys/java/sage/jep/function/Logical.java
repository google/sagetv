/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/

package sage.jep.function;

import sage.jep.*;

public class Logical extends PostfixMathCommand
{
  int id;

  public Logical(int id_in)
  {
    id = id_in;
    numberOfParameters = 2;
  }

  public void run(sage.Catbert.FastStack inStack)
      throws ParseException {

    Object param2 = inStack.pop();
    Object param1 = inStack.pop();

    boolean x, y;
    if ((param1 instanceof Boolean) && (param2 instanceof Boolean))
    {
      x = ((Boolean)param1).booleanValue();
      y = ((Boolean)param2).booleanValue();
    }
    else
    {
      x = (param1 == null) ? false : param1.toString().equalsIgnoreCase("true");
      y = (param2 == null) ? false : param2.toString().equalsIgnoreCase("true");
    }
    boolean r;

    switch (id)
    {
      case 0:
        // AND
        r = x && y;
        break;
      case 1:
        // OR
        r = x || y;
        break;
      default:
        r = false;
    }

    inStack.push(r ? Boolean.TRUE : Boolean.FALSE); // push the result on the inStack
    return;
  }

  public boolean isAnd()
  {
    return id == 0;
  }

  public boolean isOr()
  {
    return id == 1;
  }
}
