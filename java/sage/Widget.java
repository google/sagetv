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

/**
 * @author brian
 */
public interface Widget extends WidgetConstants
{
  // 601 TEMP?!?
  tv.sage.mod.Module getModule();
  String symbol();


  int id(); // 601 try to remove me!

  byte type();
  boolean isType(byte type);

  String getName();
  String getUntranslatedName();

  boolean isNameTypeMatch(Widget w);

  // contents/containers
  Widget[] contents();
  Widget[] contents(byte type);
  boolean contains(Widget w);
  Widget[] containers();
  Widget[] containers(byte type);
  int numContainers();
  int numContainers(byte type);
  boolean isContainer(Widget w);
  boolean willContain(Widget w);
  int getChildIndex(Widget w);

  // properties
  String getProperty(byte prop);
  boolean hasProperty(byte prop);
  boolean isIdenticalProperties(Widget w);
  Number[] getNumericArrayProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);
  boolean isDynamicProperty(byte prop);

  boolean getBooleanProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);
  int getIntProperty(byte prop, int i, Catbert.Context evalContext, ZPseudoComp uiContext);
  float getFloatProperty(byte prop, float i, Catbert.Context evalContext, ZPseudoComp uiContext);
  String getStringProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);
  Object getObjectProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);
  java.awt.Color getColorProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);
  int getIntColorProperty(byte prop, int i, Catbert.Context evalContext, ZPseudoComp uiContext);
  Number getNumericProperty(byte prop, Catbert.Context evalContext, ZPseudoComp uiContext);

  String searchPropertyValues(String srch, boolean fullMatch);
  Widget contentsSingularName(byte type, String testname);

  boolean isProcessChainType();
  boolean isInProcessChain();
  boolean isInUIHierarchy();
  boolean isUIComponent();
  boolean isInShapeHierarchy();
  boolean isInEffectHierarchy();

  // misc
  void setBreakpointMask(int x);
  int getBreakpointMask();

  String getTempProperty(String prop);
  void setTempProperty(String prop, String val);

  // new
  boolean tempHighlight();
  void tempHighlight(boolean status);

  // caching information, used by Effects only currently
  public static final int HAS_ANCHOR_POINT_PROPS = 0x00000001;
  public static final int HAS_TRANSLATE_PROPS = 0x00000002;
  public static final int HAS_ALPHA_PROPS = 0x00000004;
  public static final int HAS_SCALE_PROPS = 0x00000008;
  public static final int HAS_ROTX_PROPS = 0x00000010;
  public static final int HAS_ROTY_PROPS = 0x00000020;
  public static final int HAS_ROTZ_PROPS = 0x00000040;
  public static final int HAS_CAMERA_PROPS = 0x00000080;
  public static final int HAS_DYNAMIC_ANCHOR_POINT_PROPS = 0x00000100;
  public static final int HAS_DYNAMIC_TRANSLATE_PROPS = 0x00000200;
  public static final int HAS_DYNAMIC_ALPHA_PROPS = 0x00000400;
  public static final int HAS_DYNAMIC_SCALE_PROPS = 0x00000800;
  public static final int HAS_DYNAMIC_ROTX_PROPS = 0x00001000;
  public static final int HAS_DYNAMIC_ROTY_PROPS = 0x00002000;
  public static final int HAS_DYNAMIC_ROTZ_PROPS = 0x00004000;
  public static final int HAS_DYNAMIC_CAMERA_PROPS = 0x00008000;
  public static final int HAS_CENTER_DEPENDENT_PROPS = 0x00000078;
  public static final int HAS_DYNAMIC_CENTER_DEPENDENT_PROPS = 0x00007800;
  boolean hasAnyCacheMask(int masker);
  boolean hasAllCacheMask(int masker);
}
