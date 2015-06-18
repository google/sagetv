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
package sage;

import sage.media.format.MediaFormat;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Narflex
 */
public final class UserRecord extends DBObject
{

  /** Creates a new instance of UserRecord */
  UserRecord(int inID)
  {
    super(inID);
  }

  UserRecord(DataInput in, byte ver, Map<Integer, Integer> idMap) throws IOException
  {
    super(in, ver, idMap);
    store = in.readUTF().intern();
    key = in.readUTF();

    buildProps(in.readUTF());
  }

  void write(DataOutput out, int flags) throws IOException
  {
    super.write(out, flags);
    out.writeUTF(store);
    out.writeUTF(key);

    if (props == null)
      out.writeUTF("");
    else
    {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> ent : props.entrySet())
      {
        sb.append(MediaFormat.escapeString(ent.getKey().toString()));
        sb.append('=');
        sb.append(MediaFormat.escapeString(ent.getValue().toString()));
        sb.append(';');
      }
      out.writeUTF(sb.toString());
    }
  }

  private void buildProps(String str)
  {
    if (str != null && str.length() > 0)
    {
      if (props == null)
        props = new Properties();
      else
        props.clear();
      int currNameStart = 0;
      int currValueStart = -1;
      for (int i = 0; i < str.length(); i++)
      {
        char c = str.charAt(i);
        if (c == '\\')
        {
          // Escaped character, so skip the next one
          i++;
          continue;
        }
        else if (c == '=')
        {
          // We found the name=value delimeter, set the value start position
          currValueStart = i + 1;
        }
        else if (c == ';' && currValueStart != -1)
        {
          // We're at the end of the name value pair, get their values!
          String name = sage.media.format.ContainerFormat.unescapeString(str.substring(currNameStart, currValueStart - 1));
          String value = sage.media.format.ContainerFormat.unescapeString(str.substring(currValueStart, i));
          currNameStart = i + 1;
          currValueStart = -1;
          props.setProperty(name, value);
        }
      }
    }
    else if (props != null)
      props.clear();
  }

  public String getStore() { return store; }

  public String getKey() { return key; }

  void update(DBObject x)
  {
    UserRecord fromMe = (UserRecord) x;
    store = fromMe.store;
    key = fromMe.key;
    if (fromMe.props != null)
      props = (Properties) fromMe.props.clone();
    else
      props = null;
    super.update(fromMe);
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder("UserRecord[");
    sb.append("store=");
    sb.append(store);
    sb.append(", key=");
    sb.append(key);
    sb.append(", props=");
    sb.append(props == null ? "" : props.toString());
    sb.append(']');
    return sb.toString();
  }

  public String getProperty(String name)
  {
    if (props == null)
      return "";
    String rv = props.getProperty(name);
    return (rv == null) ? "" : rv;
  }

  public synchronized void setProperty(String name, String value)
  {
    if (value == null && (props == null || !props.containsKey(name)))
      return;
    if (value != null && props != null && value.equals(props.getProperty(name)))
      return;
    if (value == null)
    {
      props.remove(name);
    }
    else
    {
      if (props == null)
        props = new Properties();
      props.setProperty(name, value);
    }
    Wizard.getInstance().logUpdate(this, Wizard.USERRECORD_CODE);
  }

  public String[] getNames()
  {
    return (props == null) ? Pooler.EMPTY_STRING_ARRAY : (String[])(props.keySet().toArray(Pooler.EMPTY_STRING_ARRAY));
  }

  String store;
  String key;
  Properties props;

  public static final Comparator<UserRecord> USERRECORD_COMPARATOR =
      new Comparator<UserRecord>()
  {
    public int compare(UserRecord u1, UserRecord u2)
    {
      if (u1 == u2)
        return 0;
      else if (u1 == null)
        return 1;
      else if (u2 == null)
        return -1;

      if (u1.store == null)
        return (u2.store == null) ? 0 : -1;
      else if (u2.store == null)
        return 1;
      int rv = u1.store.compareTo(u2.store);
      if (rv != 0)
        return rv;
      if (u1.key == null)
        return (u2.key == null) ? 0 : -1;
      else if (u2.key == null)
        return 1;
      return u1.key.compareTo(u2.key);
    }
  };
}
