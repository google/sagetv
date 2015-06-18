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
package sage.msg;

public class SageMsg
{

  /** Creates a new instance of SageMsg */
  public SageMsg()
  {
  }

  public SageMsg(int type, Object source, Object data, int priority)
  {
    this.type = type;
    this.source = source;
    this.data = data;
    this.priority = priority;
    timestamp = sage.Sage.time();
  }

  public int getType()
  {
    return type;
  }
  public long getTimestamp()
  {
    return timestamp;
  }
  public Object getSource()
  {
    return source;
  }
  public Object getData()
  {
    return data;
  }
  public int getPriority()
  {
    return priority;
  }

  public String toString()
  {
    return "SageMsg[type=" + type + " prior=" + priority + " src=" + source + " data=" +
        ((data instanceof byte[]) ? new String((byte[])data) : data) + " timestamp=" + sage.Sage.df(timestamp) + "]";
  }

  protected int type;
  protected long timestamp;
  protected Object source;
  protected Object data;
  protected int priority;
}
