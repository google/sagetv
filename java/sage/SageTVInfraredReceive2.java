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

/**
 *
 * @author Narflex
 */
public class SageTVInfraredReceive2 extends SageTVInfraredReceive
{
  public SageTVInfraredReceive2()
  {
    super();
  }
  public boolean openInputPlugin(SageTVInputCallback callback)
  {
    irmanPortString = Sage.get(IRMAN_RCV_PORT, "");
    evtRtr = callback;
    setupIRReceivePort();

    if (MMC.getInstance().encoderFeatureSupported(DShowCaptureDevice.SM2210_ENCODER_MASK))
    {
      pvIRThread = new Thread(new Runnable()
      {
        public void run()
        {
          pvIRPortThread0(evtRtr);
        }
      }, "PVIRReceive");
      pvIRThread.setDaemon(true);
      // If this is too low in priority we can miss
      // the repeat timing of a button press, so we need
      // accurate timing here to some degree.
      pvIRThread.setPriority(Thread.MAX_PRIORITY - 3);
      pvIRThread.start();
    }
    if (MMC.getInstance().encoderFeatureSupported(DShowCaptureDevice.VBDVCR_ENCODER_MASK))
    {
      dvcrIRThread = new Thread(new Runnable()
      {
        public void run()
        {
          dvcrIRPortThread0(evtRtr);
        }
      }, "DVCRIRReceive");
      dvcrIRThread.setDaemon(true);
      // If this is too low in priority we can miss
      // the repeat timing of a button press, so we need
      // accurate timing here to some degree.
      dvcrIRThread.setPriority(Thread.MAX_PRIORITY - 3);
      dvcrIRThread.start();
    }
    return true;
  }
  public boolean setIRReceivePort(String portName)
  {
    if (portName == null) portName = "";
    if (portName.equals(irmanPortString)) return true;
    closeIRReceivePort();
    Sage.put(IRMAN_RCV_PORT, irmanPortString = portName);
    return setupIRReceivePort();
  }
}
