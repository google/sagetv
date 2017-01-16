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
package sage.epg.sd.json.images;

import sage.epg.sd.json.SDError;

public class SDProgramImages implements SDError
{
  public static final SDImage[] EMPTY_IMAGES = new SDImage[0];

  private int code;
  private String message;

  private String programID;
  private SDImage data[];

  protected SDProgramImages(int code, String message, String programID, SDImage[] data)
  {
    this.code = code;
    this.message = message;
    this.programID = programID;
    this.data = data != null ? data : EMPTY_IMAGES;
  }

  public String getProgramID()
  {
    return programID;
  }

  public SDImage[] getImages()
  {
    // This is taken care of in construction.
    /*if (data == null)
      return new SDImage[0];*/

    return data;
  }

  @Override
  public int getCode()
  {
    return code;
  }

  @Override
  public String getMessage()
  {
    return message;
  }
}
