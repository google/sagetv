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

public class PVR350OSDRenderingPlugin implements OSDRenderingPlugin
{
	private static boolean loadedLib = false;
	public PVR350OSDRenderingPlugin()
	{
		if (!loadedLib)
		{
			sage.Native.loadLibrary("PVR350OSDPlugin");
			loadedLib = true;
			Sage.writeDwordValue(Sage.HKEY_LOCAL_MACHINE,
				"SYSTEM\\CurrentControlSet\\Services\\Globespan\\Parameters\\ivac15\\Driver",
				"HcwTVOutColorBars", 0);
		}
	}

	public void closeOSD()
	{
		if (osdHandle != 0)
			closeOSD0(osdHandle);
		osdHandle = 0;
	}

	public boolean openOSD()
	{
		osdHandle = openOSD0();
		return osdHandle != 0;
	}

	public boolean supportsRegionUpdates()
	{
		return Sage.getBoolean("optimize_osd_repaint_area", true);
	}

	public boolean updateOSD(int[] argbBitmapData, int bitmapWidth, int bitmapHeight,
		java.awt.Rectangle updateAreaRectangle, java.awt.Rectangle videoRectangle)
	{
		if (Sage.DBG) System.out.println("PVR350 updateOSD data.length=" + argbBitmapData.length + " w=" + bitmapWidth +
			" h=" + bitmapHeight + " targetRect=" + updateAreaRectangle + " vRect=" + videoRectangle);
		if (osdHandle != 0)
			return updateOSD0(osdHandle, argbBitmapData, bitmapWidth, bitmapHeight, updateAreaRectangle, videoRectangle);
		else
			return false;
	}

	private native long openOSD0();
	private native void closeOSD0(long osdHandle);
	private native boolean updateOSD0(long osdHandle,int[] argbBitmapData, int bitmapWidth, int bitmapHeight,
		java.awt.Rectangle updateAreaRectangle, java.awt.Rectangle videoRectangle);
	private long osdHandle;
}
