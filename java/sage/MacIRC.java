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

public class MacIRC implements sage.SageTVInputPlugin
{
  boolean alive=true;
  static boolean available = false;
  sage.SageTVInputCallback myConn1;

  private native void startIRListener();
  private native void stopIRListener();

  /* called from the native code */
  public void SendIRCode(int code)
  {
    //if(Sage.DBG) System.out.println("IR code: " + Integer.toHexString(code));
    if(alive) {
      byte[] irdata = new byte[4];
      irdata[0] = (byte)((code >> 24) & 0xFF);
      irdata[1] = (byte)((code >> 16) & 0xFF);
      irdata[2] = (byte)((code >> 8) & 0xFF);
      irdata[3] = (byte)(code & 0xFF);
      myConn1.recvInfrared(irdata);
    }
  }

  public void SendIREvent(byte[] event)
  {
    //if(Sage.DBG) System.out.println("IR bytes: " + new String(event));
    if(alive) myConn1.recvInfrared(event);
  }

  public MacIRC()
  {
    if(!available) {
      try {
        sage.Native.loadLibrary("MacIRC");
        available = true;
      } catch(Throwable t) {
        //				System.out.println("ERROR loading MacIRC library: "+t);
      }
    }
  }

  public void closeInputPlugin()
  {
    if(!available) return;

    alive=false;
    try {
      stopIRListener();
    }
    catch (Throwable t)
    {
      System.out.println("ERROR stopping MacIRC:" + t);
    }
  }

  public boolean openInputPlugin(sage.SageTVInputCallback callback)
  {
    if(!available) return false;

    System.out.println("Starting MacIRC");
    myConn1 = callback;
    alive = true;
    try {
      startIRListener();
    }
    catch (Throwable t)
    {
      System.out.println("ERROR loading MacIRC:" + t);
      return false;
    } // propagate exceptions to caller?
    return true;
  }
}
