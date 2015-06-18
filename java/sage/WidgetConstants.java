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
 * @author 601
 */
public interface WidgetConstants
{
  // types
  public static final byte MENU = 0;
  public static final byte OPTIONSMENU = 1;
  public static final byte PANEL = 2;
  public static final byte THEME = 3;
  public static final byte ACTION = 4;
  public static final byte CONDITIONAL = 5;
  public static final byte BRANCH = 6;
  public static final byte LISTENER = 7;
  public static final byte ITEM = 8;
  public static final byte TABLE = 9;
  public static final byte TABLECOMPONENT = 10;
  public static final byte TEXT = 11;
  public static final byte IMAGE = 12;
  public static final byte TEXTINPUT = 13;
  public static final byte VIDEO = 14;
  public static final byte SHAPE = 15;
  public static final byte ATTRIBUTE = 16;
  public static final byte HOOK = 17;
  public static final byte EFFECT = 18;

  public static final byte MAX_TYPE_NUM = 18;
  public static final String[] TYPES = {
    "Menu", "OptionsMenu", "Panel", "Theme", "Action",
    "Conditional", "Branch", "Listener", "Item", "Table",
    "TableComponent", "Text", "Image", "TextInput", "Video",
    "Shape", "Attribute", "Hook", "Effect" };


  // properties
  public static final byte FILE = 0;
  public static final byte PRESSED_FILE = 1;
  public static final byte BACKGROUND_IMAGE = 2;
  public static final byte BACKGROUND_SELECTED_IMAGE = 3;
  public static final byte TILE_BACKGROUND_IMAGE = 4;
  public static final byte STRETCH_BACKGROUND_IMAGE = 5;
  public static final byte BACKGROUND_COLOR = 6;
  public static final byte BACKGROUND_ALPHA = 7;
  public static final byte BACKGROUND_SELECTED_COLOR = 8;
  public static final byte BACKGROUND_SELECTED_ALPHA = 9;
  public static final byte FOREGROUND_COLOR = 10;
  public static final byte FOREGROUND_ALPHA = 11;
  public static final byte FOREGROUND_SELECTED_COLOR = 12;
  public static final byte FOREGROUND_SELECTED_ALPHA = 13;
  public static final byte FONT_FACE = 14;
  public static final byte FONT_SIZE = 15;
  public static final byte FONT_STYLE = 16;
  public static final byte LAYOUT = 17;
  public static final byte ANCHOR_X = 18;
  public static final byte ANCHOR_Y = 19;
  public static final byte ANCHOR_POINT_X = 20;
  public static final byte ANCHOR_POINT_Y = 21;
  public static final byte FIXED_WIDTH = 22;
  public static final byte FIXED_HEIGHT = 23;
  public static final byte PAD_X = 24;
  public static final byte PAD_Y = 25;
  public static final byte INSETS = 26;
  public static final byte HALIGNMENT = 27;
  public static final byte VALIGNMENT = 28;
  public static final byte USER_EVENT = 29;
  public static final byte TABLE_SUBCOMP = 30;
  public static final byte LISTENER_EVENT = 31;
  public static final byte VALUE = 32;
  public static final byte FOCUS_CHANGE_SOUND = 33;
  public static final byte ITEM_SELECT_SOUND = 34;
  public static final byte MENU_CHANGE_SOUND = 35;
  public static final byte USER_ACTION_SOUND = 36;
  public static final byte SHAPE_TYPE = 37;
  public static final byte SHAPE_FILL = 38;
  public static final byte AUTOSIZE_TEXT = 39;
  public static final byte WRAP_TEXT = 40;
  public static final byte TEXT_ALIGNMENT = 41;
  public static final byte THICKNESS = 42;
  public static final byte GRADIENT_ANGLE = 43;
  public static final byte GRADIENT_AMOUNT = 44;
  public static final byte CORNER_ARC = 45;
  public static final byte HIDE_TEXT = 46;
  public static final byte VIDEO_BACKGROUND = 47;
  public static final byte ANIMATION = 48;
  public static final byte TEXT_SHADOW = 49;
  public static final byte PRESERVE_ASPECT_RATIO = 50;
  public static final byte RESIZE_IMAGE = 51;
  public static final byte DISABLE_FONT_SCALING = 52;
  public static final byte TABLE_WRAPPING = 53;
  public static final byte SCROLLING = 54;
  public static final byte WRAP_HORIZONTAL_NAVIGATION = 55;
  public static final byte WRAP_VERTICAL_NAVIGATION = 56;
  public static final byte IGNORE_THEME_PROPERTIES = 57;
  public static final byte NUM_ROWS = 58;
  public static final byte NUM_COLS = 59;
  public static final byte DIMENSIONS = 60;
  public static final byte RENDER_SCALE_X = 61;
  public static final byte RENDER_SCALE_Y = 62;
  public static final byte FOREGROUND_SHADOW_COLOR = 63;
  public static final byte FOREGROUND_SHADOW_ALPHA = 64;
  public static final byte FOREGROUND_SHADOW_SELECTED_COLOR = 65;
  public static final byte FOREGROUND_SHADOW_SELECTED_ALPHA = 66;
  public static final byte AUTO_REPEAT_ACTION = 67;
  public static final byte BACKGROUND_COMPONENT = 68;
  public static final byte SCALING_INSETS = 69;
  public static final byte Z_OFFSET = 70;
  public static final byte MOUSE_TRANSPARENCY = 71;
  public static final byte HOVER_FILE = 72;
  public static final byte CROP_TO_FILL = 73;
  public static final byte DIFFUSE_FILE = 74;
  public static final byte FOCUSABLE_CONDITION = 75;
  public static final byte RENDER_ROTATE_X = 76;
  public static final byte RENDER_ROTATE_Y = 77;
  public static final byte RENDER_ROTATE_Z = 78;
  public static final byte EFFECT_TRIGGER = 79;
  public static final byte DELAY = 80;
  public static final byte DURATION = 81;
  public static final byte LOOP = 82;
  public static final byte REVERSIBLE = 83;
  public static final byte TIMESCALE = 84;
  public static final byte EASING = 85;
  public static final byte MENU_RELATIVE_OFFSETS = 86;
  public static final byte START_RENDER_OFFSET_X = 87;
  public static final byte START_RENDER_OFFSET_Y = 88;
  public static final byte START_RENDER_ROTATE_X = 89;
  public static final byte START_RENDER_ROTATE_Y = 90;
  public static final byte START_RENDER_ROTATE_Z = 91;
  public static final byte START_RENDER_SCALE_X = 92;
  public static final byte START_RENDER_SCALE_Y = 93;
  public static final byte SCALE_DIFFUSE = 94;
  public static final byte BACKGROUND_LOAD = 95;
  public static final byte CLIPPED = 96;
  public static final byte KEY = 97;

  public static final byte MAX_PROP_NUM = 97;

  // property names
  public static final String[] PROPS = {
    "File", "PressedFile", "BackgroundImage", "BackgroundSelectedImage", "TileBackgroundImage",
    "StretchBackgroundImage", "BackgroundColor", "BackgroundAlpha", "BackgroundSelectedColor", "BackgroundSelectedAlpha",
    "ForegroundColor", "ForegroundAlpha", "ForegroundSelectedColor", "ForegroundSelectedAlpha", "FontFace",
    "FontSize", "FontStyle", "Layout", "AnchorX", "AnchorY",
    "AnchorPointX", "AnchorPointY", "FixedWidth", "FixedHeight", "PadX",
    "PadY", "Insets", "HorizontalAlignment", "VerticalAlignment", "UserEvent",
    "TableSubcomp", "ListenerEvent", "Value", "FocusChangeSound", "ItemSelectSound",
    "MenuChangeSound", "UserActionSound", "ShapeType", "ShapeFill", "AutosizeText",
    "WrapText", "TextAlignment", "Thickness", "GradientAngle", "GradientAmount",
    "CornerArc", "HideText", "VideoBackground", "Animation", "TextShadow",
    "PreserveAspectRatio", "ResizeImage", "DisableFontScaling", "TableWrapping", "Scrolling",
    "WrapHNav", "WrapVNav", "IgnoreThemeProps", "NumRows", "NumCols",
    "Dimensions", "RenderXFormScaleX", "RenderXFormScaleY",
    "ForegroundShadowColor", "ForegroundShadowAlpha", "ForegroundShadowSelectedColor", "ForegroundShadowSelectedAlpha",
    "AutoRepeatAction", "BackgroundComponent", "ScalingInsets", "ZOffset", "MouseTransparency",
    "HoverFile", "CropToFill", "DiffuseFile", "FocusableCondition", "RenderRotateX", "RenderRotateY", "RenderRotateZ",
    "EffectTrigger", "Delay", "Duration", "Loop", "Reversible", "Timescale", "Easing", "MenuRelativeOffsets",
    "StartRenderOffsetX", "StartRenderOffsetY", "StartRenderRotateX", "StartRenderRotateY", "StartRenderRotateZ",
    "StartRenderScaleX", "StartRenderScaleY", "ScaleDiffuse", "BGLoad", "Clipped", "Key"
  };

  public static final byte PROPERTY_TYPE_UNKNOWN = 0;
  public static final byte PROPERTY_TYPE_BOOL = 1;
  public static final byte PROPERTY_TYPE_FLOAT = 2;
  public static final byte PROPERTY_TYPE_INT = 3;
  public static final byte PROPERTY_TYPE_STRING = 4;
  public static final byte PROPERTY_TYPE_COLOR = 5;
  public static final byte PROPERTY_TYPE_DYNAMIC = 6;
  public static final byte PROPERTY_TYPE_NUMERIC_ARRAY = 7;

  public static final String RAW_KB = "RawKeyboard";
  public static final String RAW_IR = "RawInfrared";
  public static final String NUMBERS = "Numbers";
  public static final String MOUSE_CLICK = "MouseClick";
  public static final String MOUSE_DRAG = "MouseDrag";
  public static final String MOUSE_MOVE = "MouseMove";
  public static final String MOUSE_ENTER = "MouseEnter";
  public static final String MOUSE_EXIT = "MouseExit";
  public static final String MOUSE_CLICK_COUNT = "ClickCount";
  public static final char[] MNEMONICS = { 'M', 'O', 'P', 'H', 'A', 'C', 'B',
    'L', 'I', 'T', 'N', 'X', 'G', 'U',
    'V', 'S', 'R', 'K', 'E'};

  public static final String[] FONT_STYLE_CHOICES = { "", "Plain", "Bold", "Italic", "BoldItalic" };

  public static final String STATIC_EFFECT = "Static";
  public static final String CONDITIONAL_EFFECT = "Conditional";
  public static final String FOCUSGAINED_EFFECT = "FocusGained";
  public static final String FOCUSLOST_EFFECT = "FocusLost";
  public static final String MENULOADED_EFFECT = "MenuLoaded";
  public static final String MENUUNLOADED_EFFECT = "MenuUnloaded";
  public static final String SHOWN_EFFECT = "Shown";
  public static final String HIDDEN_EFFECT = "Hidden";
  public static final String VISIBLECHANGE_EFFECT = "VisibleChange";
  public static final String SMOOTHTRACKER_EFFECT = "SmoothTracker";
  public static final String FOCUSTRACKER_EFFECT = "FocusTracker";
  public static final String ITEMSELECTED_EFFECT = "ItemSelected";
  public static final String[] EFFECT_TRIGGER_NAMES = { STATIC_EFFECT, CONDITIONAL_EFFECT, FOCUSGAINED_EFFECT, FOCUSLOST_EFFECT, MENULOADED_EFFECT, MENUUNLOADED_EFFECT, SHOWN_EFFECT, HIDDEN_EFFECT,
    VISIBLECHANGE_EFFECT, SMOOTHTRACKER_EFFECT, FOCUSTRACKER_EFFECT, ITEMSELECTED_EFFECT };

  public static final String[] EFFECT_TIMESCALES = { "Linear", "Quadratic", "Cubic", "Bounce", "Rebound", "Sine", "Circle", "Curl" };

  public static final String[] EFFECT_EASING = { "None", "In", "Out", "InOut" };
}
