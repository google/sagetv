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

import sage.SageConstants;
import sage.io.BufferedSageFile;
import sage.io.EncryptedSageFile;
import sage.io.LocalSageFile;

/**
 * Read a classic STV file of {@link tv.sage.ws.STVWidget}.
 * <br>
 * @author 601
 */
public class STVReader
{
  // operation codes
  static final byte ADD = 0x01;
  static final byte UPDATE = 0x02;
  static final byte REMOVE = 0x03;
  static final byte SIZE = 0x04;
  static final byte FULL_DATA = 0x05;
  static final byte XCTS_DONE = 0x10;

  // table codes
  static final byte NETWORK_CODE = 0x01;
  static final byte CHANNEL_CODE = 0x02;
  static final byte TITLE_CODE = 0x03;
  static final byte PEOPLE_CODE = 0x04;
  static final byte CATEGORY_CODE = 0x05;
  static final byte SUBCATEGORY_CODE = 0x06;
  static final byte RATED_CODE = 0x07;
  static final byte PR_CODE = 0x08;
  static final byte ER_CODE = 0x09;
  static final byte YEAR_CODE = 0x0A;
  static final byte SHOW_CODE = 0x0B;
  static final byte AIRING_CODE = 0x0C;
  static final byte WATCH_CODE = 0x0D;
  static final byte BONUS_CODE = 0x0E;
  static final byte PRIME_TITLE_CODE = 0x0F;
  static final byte AGENT_CODE = 0x10;
  static final byte IDMAP_CODE = 0x11;
  static final byte MEDIAFILE_CODE = 0x12;
  static final byte MANUAL_CODE = 0x13;
  static final byte WASTED_CODE = 0x14;
  static final byte WIDGET_CODE = 0x15;
  static final byte PLAYLIST_CODE = 0x16;


  // instance

  protected final java.io.File file;

  // Integer(raw-id), STVWidget
  public final java.util.Map idMap = new java.util.HashMap();
  private StringBuffer sb = new StringBuffer();

  public STVReader(java.io.File file)
  {
    this.file = file;
  }

  //    public java.util.Iterator iterator() throws java.io.IOException
  //    {
  //        return (new Iterator());
  //    }

  //    class Iterator implements java.util.Iterator
  //    {
  //        final sage.FastRandomFile frf;
  //
  //        protected Iterator() throws java.io.IOException
  //        {
  //            frf = new sage.FastRandomFile(file, "rc", sage.Sage.I18N_CHARSET); // UTF-8
  //
  //            try
  //            {
  //                byte b1 = frf.readUnencryptedByte();
  //                byte b2 = frf.readUnencryptedByte();
  //                byte b3 = frf.readUnencryptedByte();
  //
  //                if ((b1 != 'W') || (b2 != 'I') || (b3 != 'Z'))
  //                {
  //                    throw new java.io.IOException("Invalid DB file format!");
  //                }
  //
  //                byte version = frf.readUnencryptedByte();
  //
  //                System.out.println("W I Z 0x" + Integer.toHexString(version));
  //
  //                if (version < 0x2F)
  //                {
  //                    // unencrypted DB file
  //
  //                    // reopen with mode "r"
  //
  //                    throw (new java.io.IOException("Unencrypted, reopen"));
  //                }
  //
  //                if (version < 0x35)
  //                {
  //                    // Switch to byte chars
  //                    frf.setCharset(sage.Sage.BYTE_CHARSET);
  //                }
  //            }
  //            finally
  //            {
  //            }
  //        }
  //
  //
  //        public boolean hasNext()
  //        {
  //            throw new UnsupportedOperationException();
  //        }
  //
  //        public Object next()
  //        {
  //            throw new UnsupportedOperationException();
  //        }
  //
  //        public void remove()
  //        {
  //            throw new UnsupportedOperationException();
  //        }
  //
  //        protected void finalize() throws Throwable
  //        {
  //            try
  //            {
  //                frf.close();
  //            }
  //            catch (Exception x)
  //            {
  //                // ignore
  //            }
  //
  //            super.finalize();
  //        }
  //    }


  /**
   * Widget data layout:
   *
   * Int-ID, UTF-type, Int-propCount, {UTF-propName, UTF-propValue}, Int-contentsCount, {Int-contents}, Int-containersCount, {Int-containers}
   */
  protected STVWidget stvWidget(java.io.DataInput in, byte version) throws java.io.IOException
  {
    int id = in.readInt();

    String type = in.readUTF();

    String name = null;
    java.util.Properties properties = new java.util.Properties();

    int propCount = in.readInt();
    for (int i = 0; i < propCount; i++) properties.setProperty(in.readUTF(), in.readUTF());

    int num = in.readInt();
    int [] contents = num == 0 ? sage.Pooler.EMPTY_INT_ARRAY : new int[num];
    for (int i = 0; i < contents.length; i++) contents[i] = in.readInt();

    num = in.readInt();
    int [] containers = num == 0 ? sage.Pooler.EMPTY_INT_ARRAY : new int[num];
    for (int i = 0; i < containers.length; i++) containers[i] = in.readInt();

    STVWidget sw = new STVWidget(type, properties, contents, containers);

    idMap.put(new Integer(id), sw);

    return (sw);
  }

  protected STVWidget loadDBObject(byte code, java.io.DataInput in, byte ver) throws java.io.IOException
  {
    switch (code)
    {
      case WIDGET_CODE:
        return stvWidget(in, ver);

      case CHANNEL_CODE:
      case SHOW_CODE:
      case AIRING_CODE:
      case WATCH_CODE:
      case AGENT_CODE:
      case MEDIAFILE_CODE:
      case MANUAL_CODE:
      case WASTED_CODE:
      case PLAYLIST_CODE:
      default:
        throw (new tv.sage.SageRuntimeException("NOT a WIDGET_CODE", tv.sage.SageExceptable.INTEGRITY));
    }
  }


  public STVWidget[] load() throws java.io.IOException
  {
    java.util.ArrayList widgetv = new java.util.ArrayList();

    sage.io.SageDataFile frf = new sage.io.SageDataFile(new EncryptedSageFile(new BufferedSageFile(new LocalSageFile(file, true), 16384)), sage.Sage.I18N_CHARSET); // UTF-8

    try
    {
      byte b1 = frf.readUnencryptedByte();
      byte b2 = frf.readUnencryptedByte();
      byte b3 = frf.readUnencryptedByte();

      if ((b1 != 'W') || (b2 != 'I') || (b3 != 'Z'))
      {
        throw new java.io.IOException("Invalid DB file format!");
      }

      byte version = frf.readUnencryptedByte();

      if (sage.Sage.DBG) System.out.println("Modules: STVReader load W I Z 0x" + Integer.toHexString(version));

      if (version < 0x2F)
      {
        // unencrypted DB file

        // reopen with mode "r"

        throw (new java.io.IOException("Unencrypted, reopen"));
      }

      if (version < 0x35)
      {
        // Switch to byte chars
        frf.setCharset(sage.Sage.BYTE_CHARSET);
      }

      // last SIZE OPCODE value
      int size = -1;
      byte typecode = 0;

      while (file.length() > frf.position())
      {
        int cmdLength = frf.readInt();

        // System.out.println("ws load cmdLength = " + cmdLength);

        //                assert cmdLength >= 4;

        byte opcode = frf.readByte();

        if (opcode == XCTS_DONE) break; // ???

        byte tc = frf.readByte();

        // System.out.println("  TYPE " + sage.Wizard.getNameForCode(tc));

        if (opcode == SIZE)
        {
          size = frf.readInt();

          typecode = tc;

          if (sage.Sage.DBG) System.out.println("Modules: STVReader load SIZE " + size);

          continue;
        }
        else if (opcode == FULL_DATA)
        {
          if (sage.Sage.DBG) System.out.println("Modules: STVReader load FULL_DATA");

          int loop = 0;
          while (loop++ < size)
          {
            STVWidget dbw = loadDBObject(typecode, frf, version);

            if (dbw != null)
            {
              widgetv.add(dbw);
            }
          }

          continue;
        }
        else switch (opcode)
        {
          case ADD:
            System.out.println("STVReader load ADD");
            break;

          case UPDATE:
            System.out.println("STVReader load UPDATE");
            break;

          case REMOVE:
            System.out.println("STVReader load REMOVE");
            break;

          default:
            System.out.println("STVReader load Unrecognized OPCODE:" + opcode);
        }

        break;

      }
    }
    finally
    {
      frf.close();
    }

    return ((STVWidget [])widgetv.toArray(new STVWidget[widgetv.size()]));
  }

  public AbstractWidget[] loadOpt() throws java.io.IOException
  {
    sage.io.SageDataFile frf = new sage.io.SageDataFile(new sage.io.BufferedSageFile(new sage.io.LocalSageFile(file, true), 16384), sage.Sage.I18N_CHARSET); // UTF-8
    // This is a 2 pass process. We go through once to create all of the Widget objects
    // and then we go through again to setup all the contents and containers
    AbstractWidget[] rv = null;
    try
    {
      byte b1 = frf.readUnencryptedByte();
      byte b2 = frf.readUnencryptedByte();
      byte b3 = frf.readUnencryptedByte();
      boolean optimizeRead = b3 == 'X';
      if ((b1 != 'W') || (b2 != 'I') || (b3 != 'Z' && b3 != 'X'))
      {
        throw new java.io.IOException("Invalid DB file format!");
      }

      byte version = frf.readUnencryptedByte();

      if (sage.Sage.DBG) System.out.println("Modules: STVReader load W I Z 0x" + Integer.toHexString(version));

      if (version >= 0x2F)
      {
        // encrypted DB file

        // Re-wrap with encryption.
        frf = new sage.io.SageDataFile(new EncryptedSageFile(frf.getSource()), sage.Sage.I18N_CHARSET);
      }

      if (version < 0x35 && version > 0x20)
      {
        // Switch to byte chars
        frf.setCharset(sage.Sage.BYTE_CHARSET);
      }

      // last SIZE OPCODE value
      int size = -1;
      byte typecode = 0;

      while (file.length() > frf.position())
      {
        int cmdLength = frf.readInt();

        // System.out.println("ws load cmdLength = " + cmdLength);

        //                assert cmdLength >= 4;

        byte opcode = frf.readByte();

        if (opcode == XCTS_DONE) break; // ???

        byte tc = frf.readByte();

        // System.out.println("  TYPE " + sage.Wizard.getNameForCode(tc));

        if (opcode == SIZE)
        {
          size = frf.readInt();

          typecode = tc;

          if (sage.Sage.DBG) System.out.println("Modules: STVReader load SIZE " + size);
          rv = new AbstractWidget[size];
          continue;
        }
        else if (opcode == FULL_DATA)
        {
          if (sage.Sage.DBG) System.out.println("Modules: STVReader load FULL_DATA");

          int loop = 0;
          while (loop < size)
          {
            rv[loop] = loadWidget(typecode, frf, optimizeRead);
            loop++;
          }

          continue;
        }
        else switch (opcode) // we don't handle transactions
        {
          case ADD:
            System.out.println("STVReader load ADD");
            break;

          case UPDATE:
            System.out.println("STVReader load UPDATE");
            break;

          case REMOVE:
            System.out.println("STVReader load REMOVE");
            break;

          default:
            System.out.println("STVReader load Unrecognized OPCODE:" + opcode);
        }

        break;

      }
      java.util.Arrays.sort(rv, idsorter);
      for (int i = 0; i < rv.length; i++)
      {
        setupCC(rv, rv[i]);
      }
    }
    finally
    {
      frf.close();
    }

    return rv;
  }

  private AbstractWidget loadWidget(byte typecode, sage.io.SageDataFile in, boolean optimize) throws java.io.IOException
  {
    int id = in.readInt();

    byte type = optimize ? in.readByte() : MetaWidget.getTypeForName(in.readUTF(sb));
    String name = null;
    String[] values = null;
    String sym = null;

    int propCount = in.readInt();
    if (optimize)
    {
      name = in.readUTF();
      propCount--;
    }
    boolean allowSyms = sage.Sage.getBoolean("studio/preserve_widget_symbols_on_import", true);
    for (int i = 0; i < propCount; i++)
    {
      if (optimize)
      {
        byte propCode = in.readByte();
        if (values == null)
          values = new String[MetaWidget.PROPS.length];
        if (propCode < values.length && propCode >= 0)
          values[propCode] = in.readUTF();
        else if (allowSyms && (propCode & 0xFF) == 0xFF)
          sym = in.readUTF();
        else
          in.readUTF();
      }
      else
      {
        StringBuffer currName = in.readUTF(sb);
        String currVal = in.readUTF();
        if ("Name".contentEquals(currName))
          name = currVal;
        else
        {
          if (values == null)
            values = new String[MetaWidget.PROPS.length];
          int idx = MetaWidget.getPropForName(currName);
          if (idx >= 0 && idx < values.length)
            values[idx] = currVal;
        }
      }
    }

    // This'll reduce memory usage by sharing the common strings that are found in the STV.
    // But it will make the intern table for strings quite large...this may be an issue w/ switching STVs, we'll have to see
    name = name.intern();

    AbstractWidget rv = null;
    switch (type)
    {
      case MetaWidget.ITEM:
      case MetaWidget.TEXT:
      case MetaWidget.ACTION:
        rv = new Nomed(type, name, values, id, sym);
        break;

      case MetaWidget.ATTRIBUTE:
        rv = new Attribute(type, name, values, id, sym);
        break;

      case MetaWidget.MENU:
      case MetaWidget.CONDITIONAL:
      case MetaWidget.BRANCH:
        rv = new Named(type, name, values, id, sym);
        break;

      case MetaWidget.THEME:
      case MetaWidget.PANEL:
      case MetaWidget.SHAPE:
      case MetaWidget.OPTIONSMENU:
      case MetaWidget.TEXTINPUT:
      case MetaWidget.TABLE:
      case MetaWidget.TABLECOMPONENT:
      case MetaWidget.VIDEO:
      case MetaWidget.IMAGE:
      case MetaWidget.EFFECT:
        rv = new Valued(type, name, values, id, sym);
        break;

      case MetaWidget.LISTENER:
      case MetaWidget.HOOK:
        rv = new Simple(type, name, values, id, sym);
        break;

    }

    int n = in.readInt();
    rv.contentz = n == 0 ? sage.Pooler.EMPTY_ABSTRACTWIDGET_ARRAY : new AbstractWidget[n];
    rv.contentzIDs = n == 0 ? sage.Pooler.EMPTY_INT_ARRAY : new int[n];
    for (int i = 0; i < n; i++)
      rv.contentzIDs[i] = in.readInt();

    n = in.readInt();
    rv.containerz = n == 0 ? sage.Pooler.EMPTY_ABSTRACTWIDGET_ARRAY : new AbstractWidget[n];
    rv.containerzIDs = n == 0 ? sage.Pooler.EMPTY_INT_ARRAY : new int[n];
    for (int i = 0; i < n; i++)
      rv.containerzIDs[i] = in.readInt();

    return rv;
  }

  private void setupCC(AbstractWidget[] allWidgs, AbstractWidget myWidg)
  {
    boolean needFixin = false;
    for (int i = 0; i < myWidg.contentz.length; i++)
    {
      myWidg.contentz[i] = lookupWidg(myWidg.contentzIDs[i], allWidgs);
      needFixin = needFixin || myWidg.contentz[i] == null;
    }
    if (needFixin)
    {
      java.util.ArrayList fixedContentz = new java.util.ArrayList();
      for (int i = 0; i < myWidg.contentz.length; i++)
      {
        if (myWidg.contentz[i] != null)
          fixedContentz.add(myWidg.contentz[i]);
      }
      myWidg.contentz = (AbstractWidget[]) fixedContentz.toArray(new AbstractWidget[0]);
    }

    needFixin = false;
    for (int i = 0; i < myWidg.containerz.length; i++)
    {
      myWidg.containerz[i] = lookupWidg(myWidg.containerzIDs[i], allWidgs);
      needFixin = needFixin || myWidg.containerz[i] == null;
    }
    if (needFixin)
    {
      java.util.ArrayList fixedcontainerz = new java.util.ArrayList();
      for (int i = 0; i < myWidg.containerz.length; i++)
      {
        if (myWidg.containerz[i] != null)
          fixedcontainerz.add(myWidg.containerz[i]);
      }
      myWidg.containerz = (AbstractWidget[]) fixedcontainerz.toArray(new AbstractWidget[0]);
    }

    myWidg.contentzIDs = myWidg.containerzIDs = null;
  }

  private AbstractWidget fastLookup = new GenericWidget((byte)0, null, null, 0, null);
  private AbstractWidget lookupWidg(int id, AbstractWidget[] array)
  {
    fastLookup.index = id;
    int idx = java.util.Arrays.binarySearch(array, fastLookup, idsorter);
    if (idx >= 0)
      return array[idx];
    else
      return null;
  }

  public static final java.util.Comparator idsorter =
      new java.util.Comparator()
  {
    public int compare(Object o1, Object o2)
    {
      if (o1 == o2)
        return 0;
      else if (o1 == null)
        return 1;
      else if (o2 == null)
        return -1;

      AbstractWidget s1 = (AbstractWidget) o1;
      AbstractWidget s2 = (AbstractWidget) o2;
      return s1.index - s2.index;
    }
  };
}
