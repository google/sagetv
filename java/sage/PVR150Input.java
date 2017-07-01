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

public class PVR150Input implements SageTVInputPlugin
{
  private static boolean loadedLib = false;
  private SageTVInputCallback evtRtr;
  private Thread InputThread;

  public PVR150Input()
  {
    if (!loadedLib)
    {
      sage.Native.loadLibrary("PVR150Input");
      loadedLib = true;
    }
  }

  public boolean openInputPlugin(SageTVInputCallback callback)
  {
    evtRtr = callback;
    if(!setupPVR150Input(Sage.getInt("linux/ir_input_pvr150", 0))) return false;

    InputThread = new Thread(new Runnable()
    {
      public void run()
      {
        PVR150Input.this.PVR150InputThread(Sage.getInt("linux/ir_input_pvr150", 0),
            evtRtr);
      }
    }, "PVR150Input");
    InputThread.setDaemon(true);
    // If this is too low in priority we can miss
    // the repeat timing of a button press, so we need
    // accurate timing here to some degree.
    InputThread.setPriority(Thread.MAX_PRIORITY - 3);
    InputThread.start();
    return true;
  }

  public void closeInputPlugin()
  {
    closePVR150Input();
  }

  private native boolean setupPVR150Input(int i2cdev);
  private native void closePVR150Input();
  private native void PVR150InputThread(int i2cdev, SageTVInputCallback evtRtr);
}
