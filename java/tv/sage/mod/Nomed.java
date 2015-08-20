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
package tv.sage.mod;

/**
 * Nomed Widget have a translated name.
 * Usually ITEM, TEXT, ACTION (dynamic translation)
 * @author 601
 */
public class Nomed extends GenericWidget
{
  private volatile String nomProperty = null;

  public Nomed(RawWidget rawWidget)
  {
    super(rawWidget);

    // 601 Well, what to do?!?
    //retranslate();
  }
  public Nomed(byte inType, String inName, String[] propValz, int inIndex, String inSymbol)
  {
    super(inType, inName, propValz, inIndex, inSymbol);
  }

  // dynamic translation for ACTION

  public String getName()
  {
    if (nomProperty == null)
    {
      retranslate();
    }

    return (nomProperty);
  }

  public void retranslate()
  {
    nomProperty = Translator.translateText(name(), type() == ACTION);
  }

  /**
   *
   * @param name
   */
  public void setName(String name)
  {
    super.setName(name);

    retranslate();
  }
}
