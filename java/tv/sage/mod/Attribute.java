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
 * @author 601
 */
public class Attribute extends GenericWidget
{
  /** sage.Widget.VALUE property translated */
  String translatedValue;

  public Attribute(RawWidget rawWidget)
  {
    super(rawWidget);

    retranslate();
  }
  public Attribute(byte inType, String inName, String[] propValz, int inIndex, String inSymbol)
  {
    super(inType, inName, propValz, inIndex, inSymbol);
    retranslate();
  }

  /**
   * @return null only for ignored Exception, "" or a value
   */
  public String getStringProperty(byte prop, sage.Catbert.Context evalContext, sage.ZPseudoComp uiContext)
  {
    if (prop == VALUE)
    {
      // 601 translatedValue can be null?!?

      return (translatedValue);
    }

    return (super.getStringProperty(prop, evalContext, uiContext));
  }

  public void setProperty(byte prop, String value)
  {
    super.setProperty(prop, value);

    if (prop == VALUE)
    {
      // 601 ONLY if actually changed?!?

      retranslate();
    }
  }

  public void retranslate()
  {
    //translatedValue = Translator.translateText(properties().getProperty(PROPS[VALUE]), true);

    translatedValue = Translator.translateText(getValue(VALUE), true);

    // 601 sure about this?
    if (translatedValue == null) translatedValue = "";
  }
}
