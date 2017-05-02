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
package sage.util;

/**
 * Do NOT use this class as a key on anything ever.
 */
public class MutableFloat
{
  private float value;

  public MutableFloat()
  {
    this.value = 0.0f;
  }

  public MutableFloat(float value)
  {
    this.value = value;
  }

  public float getValue()
  {
    return value;
  }

  public void setValue(float value)
  {
    this.value = value;
  }

  public void increment(float increment)
  {
    this.value += increment;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (!(o instanceof MutableFloat)) return false;

    MutableFloat that = (MutableFloat) o;

    return Float.compare(that.value, value) == 0;
  }

  @Override
  public int hashCode()
  {
    return (value != +0.0f ? Float.floatToIntBits(value) : 0);
  }
}
