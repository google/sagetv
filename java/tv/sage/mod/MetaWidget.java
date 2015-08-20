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
 * Support for operations accross all Widget.
 * <br>
 * @author 601
 */
public class MetaWidget implements sage.WidgetConstants
{
  public static java.util.Map sourceTranslationMap = null;

  // 601 these need to be final???

  public static byte getTypeForName(String typeName)
  {
    for (byte i = 0; i < TYPES.length; i++)
      if (TYPES[i].equals(typeName))
        return i;
    return -1;
  }
  public static byte getTypeForName(StringBuffer typeName)
  {
    for (byte i = 0; i < TYPES.length; i++)
      if (TYPES[i].contentEquals(typeName))
        return i;
    return -1;
  }

  public static byte getPropForName(String propName)
  {
    for (byte i = 0; i < PROPS.length; i++)
      if (PROPS[i].equals(propName))
        return i;
    return -1;
  }
  public static byte getPropForName(StringBuffer propName)
  {
    for (byte i = 0; i < PROPS.length; i++)
      if (PROPS[i].contentEquals(propName))
        return i;
    return -1;
  }

  public static int getFontStyleForName(String s)
  {
    if ("BoldItalic".equals(s))
      return sage.MetaFont.BOLD | sage.MetaFont.ITALIC;
    else if ("Bold".equals(s))
      return sage.MetaFont.BOLD;
    else if ("Italic".equals(s))
      return sage.MetaFont.ITALIC;
    else
      return sage.MetaFont.PLAIN;
  }

  public boolean isRelationshipAllowed(byte parentType, byte childType)
  {
    // 601 hack
    return (true);

    //throw (new UnsupportedOperationException("MetaWidget"));
    //x
    //            java.util.Set childSet = (java.util.Set)validRelationshipMap.get(new Byte(parentType));
    //            return (childSet != null) && childSet.contains(new Byte(childType));

    //java.util.Set childSet = (java.util.Set)validRelationshipMap.get(new Byte(parentType));
    //return (childSet != null) && childSet.contains(new Byte(childType));
  }

  public static String convertToCleanPropertyName(String s)
  {
    throw (new UnsupportedOperationException("MetaWidget"));
  }

  public static sage.Widget fromXMLWidget(tv.sage.xml.XMLWidget xmlWidget)
  {
    throw (new UnsupportedOperationException("MetaWidget"));
  }
}
