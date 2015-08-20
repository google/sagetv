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

public interface SageTVInputCallback2 extends SageTVInputCallback
{
  public static final int MOUSE_WHEEL = 999;
  /*
   * Call this method to send SageTV a mouse event.
   *
   * eventID - type of mouse event that occured
   * modifiers - mask of which buttons & modifier keys were pressed during the event
   * x - the x position for the mouse event
   * y - the y position for the mouse event
   * clickCount - the click count for the button click/press/release
   *
   * event IDs:
   * MOUSE_CLICKED 500
   * MOUSE_PRESSED 501
   * MOUSE_RELEASED 502
   * MOUSE_MOVED 503
   * MOUSE_DRAGGED 506
   * MOUSE_WHEEL 507
   *
   * modifiers:
   * ALT_DOWN_MASK 512
   * CTRL_DOWN_MASK 128
   * SHIFT_DOWN_MASK 64
   * BUTTON1_DOWN_MASK 1024
   * BUTTON2_DOWN_MASK 2048
   * BUTTON3_DOWN_MASK 4096
   *
   * buttons:
   * BUTTON1 1
   * BUTTON2 2
   * BUTTON3 3
   *
   */
  public void recvMouse(int eventID, int modifiers, int x, int y, int clickCount, int button);

}
