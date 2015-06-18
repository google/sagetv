/*****************************************************************************

JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/
package sage.jep;

public interface SymbolTable
{
  public boolean containsKey(Object o);
  public Object get(Object o);
}
