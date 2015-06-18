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

public class FloatInsets
{
  public FloatInsets()
  {
  }

  public FloatInsets(/*EMBEDDED_SWITCH*/float/*/int/**/ top, /*EMBEDDED_SWITCH*/float/*/int/**/ left,
      /*EMBEDDED_SWITCH*/float/*/int/**/ bottom, /*EMBEDDED_SWITCH*/float/*/int/**/ right)
  {
    this.top = top;
    this.left = left;
    this.bottom = bottom;
    this.right = right;
  }

  public String toString()
  {
    return "FloatInsets[" + top + ", " + left + ", " + bottom + ", " + right + "]";
  }

  public FloatInsets set(/*EMBEDDED_SWITCH*/float/*/int/**/ top, /*EMBEDDED_SWITCH*/float/*/int/**/ left,
      /*EMBEDDED_SWITCH*/float/*/int/**/ bottom, /*EMBEDDED_SWITCH*/float/*/int/**/ right)
  {
    this.top = top;
    this.left = left;
    this.bottom = bottom;
    this.right = right;
    return this;
  }

  public void integerize()
  {
    /*EMBEDDED_SWITCH*/
    top = (int)(top);
    bottom = (int)(bottom);
    left = (int)(left);
    right = (int)(right);
    /**/
  }

  public /*EMBEDDED_SWITCH*/float/*/int/**/ top;
  public /*EMBEDDED_SWITCH*/float/*/int/**/ bottom;
  public /*EMBEDDED_SWITCH*/float/*/int/**/ left;
  public /*EMBEDDED_SWITCH*/float/*/int/**/ right;
}
