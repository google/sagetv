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
package sage.epg.sd;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DeserializeTest
{
  public <T> T deserialize(String inString, Class<T> classType)
  {
    byte encoded[] = inString.getBytes(StandardCharsets.ISO_8859_1);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(encoded);
    InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1);
    return SDUtils.GSON.fromJson(reader, classType);
  }
}
