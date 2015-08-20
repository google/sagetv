/*****************************************************************************

Copyright 2015 The SageTV Authors. All Rights Reserved.

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/

package sage.jep.function;

import sage.jep.*;

public class Comparative extends PostfixMathCommand
{
  int id;
  double tolerance;

  public Comparative(int id_in)
  {
    id = id_in;
    numberOfParameters = 2;
    tolerance = 1e-6;
  }

  public void run(sage.Catbert.FastStack inStack)
      throws ParseException {

    Object param2 = inStack.pop();
    Object param1 = inStack.pop();

    if ((param1 instanceof Number) && (param2 instanceof Number))
    {
      if ((param1 instanceof Double) || (param2 instanceof Double) || (param1 instanceof Float) ||
          (param2 instanceof Float))
      {
        double x = ((Number)param1).doubleValue();
        double y = ((Number)param2).doubleValue();
        int r;

        switch (id)
        {
          case 0:
            r = (x<y) ? 1 : 0;
            break;
          case 1:
            r = (x>y) ? 1 : 0;
            break;
          case 2:
            r = (x<=y) ? 1 : 0;
            break;
          case 3:
            r = (x>=y) ? 1 : 0;
            break;
          case 4:
            r = (x!=y) ? 1 : 0;
            break;
          case 5:
            r = (x==y) ? 1 : 0;
            break;
          default:
            throw new ParseException("Unknown relational operator");
        }

        inStack.push(r==1 ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
      }
      else
      {
        long x = ((Number)param1).longValue();
        long y = ((Number)param2).longValue();
        int r;

        switch (id)
        {
          case 0:
            r = (x<y) ? 1 : 0;
            break;
          case 1:
            r = (x>y) ? 1 : 0;
            break;
          case 2:
            r = (x<=y) ? 1 : 0;
            break;
          case 3:
            r = (x>=y) ? 1 : 0;
            break;
          case 4:
            r = (x!=y) ? 1 : 0;
            break;
          case 5:
            r = (x==y) ? 1 : 0;
            break;
          default:
            throw new ParseException("Unknown relational operator");
        }

        inStack.push(r==1 ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
      }
    }
    else if (id == 4 || id == 5)//default to Object.equals if ((param1 instanceof String) && (param2 instanceof String))
    {
      boolean r = testBranchValue(param1, param2);
      if (id == 4)
        r = !r;

      inStack.push(r ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
    }
    else if (param1 != null && param2 != null)
    {
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
      if ((param1 instanceof Double) || (param2 instanceof Double) || (param1 instanceof Float) ||
          (param2 instanceof Float))
      {
        double x = ((Number)param1).doubleValue();
        double y = ((Number)param2).doubleValue();
        int r;

        switch (id)
        {
          case 0:
            r = (x<y) ? 1 : 0;
            break;
          case 1:
            r = (x>y) ? 1 : 0;
            break;
          case 2:
            r = (x<=y) ? 1 : 0;
            break;
          case 3:
            r = (x>=y) ? 1 : 0;
            break;
          case 4:
            r = (x!=y) ? 1 : 0;
            break;
          case 5:
            r = (x==y) ? 1 : 0;
            break;
          default:
            throw new ParseException("Unknown relational operator");
        }

        inStack.push(r==1 ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
      }
      else
      {
        long x = ((Number)param1).longValue();
        long y = ((Number)param2).longValue();
        int r;

        switch (id)
        {
          case 0:
            r = (x<y) ? 1 : 0;
            break;
          case 1:
            r = (x>y) ? 1 : 0;
            break;
          case 2:
            r = (x<=y) ? 1 : 0;
            break;
          case 3:
            r = (x>=y) ? 1 : 0;
            break;
          case 4:
            r = (x!=y) ? 1 : 0;
            break;
          case 5:
            r = (x==y) ? 1 : 0;
            break;
          default:
            throw new ParseException("Unknown relational operator");
        }

        inStack.push(r==1 ? Boolean.TRUE : Boolean.FALSE);//push the result on the inStack
      }
    }
    else
      throw new ParseException("Relational operator type error, null argument");

    return;
  }
  protected static boolean testBranchValue(Object condRes, Object branchRes)
  {
    if (condRes == branchRes) return true;
    if (condRes == null || branchRes == null) return false;
    if (condRes.equals(branchRes)) return true;
    // DBObjects must be the same actual object handle...String comparison shouldn't affect equality testing and this
    // also helps with performance
    if (condRes instanceof sage.DBObject && branchRes instanceof sage.DBObject) return false;
    return branchRes.toString().equals(condRes.toString());
  }

}
