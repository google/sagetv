/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tv.sage.mod;

/**
 * Proxy Widget, looks like a Widget but isn't.
 * <br>
 * @author 601
 */
public class Proxy extends AbstractWidget
{
  volatile AbstractWidget original = null;

  // proxy's containment context
  // assume never has a null element

  public Proxy(RawWidget rawWidget)
  {
    index = rawWidget.index();
    symbol = rawWidget.symbol();
  }

  public String toString()
  {
    return ("Proxy of " + original);
  }

  protected void link()
  {
    //Log.ger.fine(module.name + " link " + symbol);

    // 601 FIX global ref
    original = (AbstractWidget)tv.sage.ModuleManager.defaultModuleGroup.symbolMap.get(symbol);

    if (original == null)
    {
      throw (new tv.sage.SageRuntimeException("unresolved symbol " + symbol, tv.sage.SageExceptable.INTEGRITY));
    }
  }

  public boolean isProxy()
  {
    return (true);
  }

  public sage.Widget getOriginal()
  {
    if (original == null) link();

    return (original);
  }

  /*EMBEDDED_SWITCH/
    public byte type()
    {
        if (original == null) link();

        return (original.type());
    }
/**/
  public String name()
  {
    if (original == null) link();

    return (original.name());
  }

  public int index()
  {
    return (index);
  }

  protected AbstractWidget[] contentz()
  {
    if (original == null) link();

    AbstractWidget[] aconz = original.contentz();

    AbstractWidget[] conz = new AbstractWidget[aconz.length + contentz.length];

    System.arraycopy(aconz, 0, conz, 0, aconz.length);
    System.arraycopy(contentz, 0, conz, aconz.length, contentz.length);

    return (conz);
  }

  protected AbstractWidget[] containerz()
  {
    if (original == null) link();

    AbstractWidget[] aconz = original.containerz();

    AbstractWidget[] conz = new AbstractWidget[aconz.length + containerz.length];

    System.arraycopy(aconz, 0, conz, 0, aconz.length);
    System.arraycopy(containerz, 0, conz, aconz.length, containerz.length);

    return (conz);
  }

  protected String getValue(byte prop)
  {
    throw (new UnsupportedOperationException());
  }

  protected void setValue(final byte prop, final String value)
  {
    throw (new UnsupportedOperationException());
  }

  protected Object getResolved(final byte prop)
  {
    throw (new UnsupportedOperationException());
  }

  protected void setResolved(final byte prop, final Object resolved)
  {
    throw (new UnsupportedOperationException());
  }

  public String[] getPropertyValues()
  {
    throw (new UnsupportedOperationException());
  }

  public boolean hasAnyCacheMask(int masker)
  {
    return false;
  }
  public boolean hasAllCacheMask(int masker)
  {
    return false;
  }
}
