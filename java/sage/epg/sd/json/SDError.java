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
package sage.epg.sd.json;

/**
 * This interface is here to allow a deserializer return a Schedules Direct related error message
 * since the deserializer would not be able to throw the correct exception while deserializing. If
 * the code is anything other than 0, throw an <code>SDException</code> with the value of
 * <code>message</code>.
 *
 * Classes that implement this interface also must implement their own deserializer to handle if an
 * error was returned or the expected data, unless the expected data will also contain the
 * <code>code</code> JSON element and the <code>message</code> JSON element if the code is not 0 as
 * a part of the root object.
 */
public interface SDError
{
  /**
   * The returned code.
   * <p/>
   * Any value other than 0 indicates an error. If there isn't a code, this must always return 0.
   *
   * @return The returned code.
   */
  public int getCode();

  /**
   * The message returned associated with the code to be displayed in the UI.
   * <p/>
   * This can be <code>null</code> if the code is 0.
   *
   * @return Return a message associated with the error code.
   */
  public String getMessage();
}
