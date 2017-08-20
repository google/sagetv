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
public class MutableInteger
{
  private int value;

  public MutableInteger()
  {
    this.value = 0;
  }

  public MutableInteger(int value)
  {
    this.value = value;
  }

  public int getValue()
  {
    return value;
  }

  public void setValue(int value)
  {
    this.value = value;
  }

  public void increment(int increment)
  {
    this.value += increment;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (!(o instanceof MutableInteger)) return false;

    MutableInteger that = (MutableInteger) o;

    return !(value != that.value);
  }

  @Override
  public int hashCode()
  {
    return value;
  }

  @Override
  public String toString()
  {
    return Integer.toString(value);
  }
}
