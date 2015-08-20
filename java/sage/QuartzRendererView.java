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

// WARNING: Do NOT include this file on non-Mac platforms! The eAWT classes are Mac specific.

package sage;

public class QuartzRendererView
	extends com.apple.eawt.CocoaComponent
{
	// if we ever need to communicate with the native NSView:
	// final static int SOME_MESSAGE 1
	// final static int ANOTHER_MESSAGE 2
	// and in some method:
	// sendMessage(SOME_MESSAGE, message object);
	// in the NSView, implement an Objective-C method:
	// - (void) awtMessage:(jint)messageID message:(jobject)message env:(JNIEnv*)env

	public QuartzRendererView() {
		super();
	}

	public void setBounds(int x, int y, int width, int height) {
//		System.out.println("QuartzRendererView setBounds("+x+","+y+","+width+","+height+")");
		super.setBounds(x,y,width,height);
	}

	public void setBounds(java.awt.Rectangle r) {
//		System.out.println("QuartzRendererView setBounds("+r+")");
		super.setBounds(r);
	}

	// CocoaComponent methods that we must implement
	public native long createNSViewLong0(); // returns a NSView* cast as a long, this will be our Cocoa counterpart

	public long createNSViewLong() {
		long v = createNSViewLong0();
		nativeView = v; // intercept the value so we can... you know... do something with it...
		return v;
	}

	// *sigh* ignore the deprecation warnings
	public int createNSView() {
		return (int)createNSViewLong();
	}

	/*
	 Implement removeNotify if we ever get global references to Java objects that need to be released
	 public void removeNotify() {
		sendMessage(REMOVE_MESSAGE, null);
		super.removeNotify();
	 }

		Then in awtMessage:message:env, use DeleteGlobalRef to release the Java objects
	 */

	// CocaComponent abstracts
	final static java.awt.Dimension PREF_SIZE = new java.awt.Dimension(720,480);
	final static java.awt.Dimension MIN_SIZE = new java.awt.Dimension(20,20);
	final static java.awt.Dimension MAX_SIZE = new java.awt.Dimension(4096,4096); // Baud help us if we ever see this size...

	public java.awt.Dimension getPreferredSize() {
		return PREF_SIZE;
	}

	public java.awt.Dimension getMinimumSize() {
		return MIN_SIZE;
	}

	public java.awt.Dimension getMaximumSize() {
		return MAX_SIZE;
	}

	public void update(java.awt.Graphics g) {
//		System.out.println("QuartzRendererView update");
//		super.update(g);
	}
	public void paint(java.awt.Graphics g) {
//		System.out.println("QuartzRendererView paint");
//		super.paint(g);
	}

	public void sendMouseMoved(int x, int y, int modifiers, long when) {
		int awtModifiers = 0;

		if((modifiers & (1<<17)) != 0) awtModifiers |= java.awt.event.InputEvent.SHIFT_DOWN_MASK; // shift key
		if((modifiers & (1<<18)) != 0) awtModifiers |= java.awt.event.InputEvent.CTRL_DOWN_MASK; // control key
		if((modifiers & (1<<19)) != 0) awtModifiers |= java.awt.event.InputEvent.ALT_DOWN_MASK; // alternate/option
		if((modifiers & (1<<20)) != 0) awtModifiers |= java.awt.event.InputEvent.META_DOWN_MASK; // meta/command

		// recvMouse(id, mods, x, y, clickCount, button)
		UIManager.getLocalUI().getRouter().recvMouse(java.awt.event.MouseEvent.MOUSE_MOVED, awtModifiers, x, y, 1, 0);
	}

	public long nativeView = 0;
}
