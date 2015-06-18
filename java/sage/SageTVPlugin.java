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
 * This interface should be implemented by a Java class to act as a generalized SageTV plugin
 *
 * IMPORTANT: The implementation class MUST have a single argument constructor that takes a sage.SageTVPluginRegistry object
 * as its only argument. And/or it may also have a two argument constructor which takes a boolean as the second argument.
 * The boolean argument will be set to true if the plugin should reset itself to the default configuration at the time of instantiation.
 */
public interface SageTVPlugin extends SageTVEventListener
{
  // This method is called when the plugin should startup
  public void start();

  // This method is called when the plugin should shutdown
  public void stop();

  // This method is called after plugin shutdown to free any resources used by the plugin
  public void destroy();

  // These methods are used to define any configuration settings for the plugin that should be
  // presented in the UI. If your plugin does not need configuration settings; you may simply return null or zero from these methods.

  // Returns the names of the settings for this plugin
  public String[] getConfigSettings();

  // Returns the current value of the specified setting for this plugin
  public String getConfigValue(String setting);

  // Returns the current value of the specified multichoice setting for this plugin
  public String[] getConfigValues(String setting);

  // Constants for different types of configuration values
  public static final int CONFIG_BOOL = 1;
  public static final int CONFIG_INTEGER = 2;
  public static final int CONFIG_TEXT = 3;
  public static final int CONFIG_CHOICE = 4;
  public static final int CONFIG_MULTICHOICE = 5;
  public static final int CONFIG_FILE = 6;
  public static final int CONFIG_DIRECTORY = 7;
  public static final int CONFIG_BUTTON = 8;
  public static final int CONFIG_PASSWORD = 9;

  // Returns one of the constants above that indicates what type of value is used for a specific settings
  public int getConfigType(String setting);

  // Sets a configuration value for this plugin
  public void setConfigValue(String setting, String value);

  // Sets a configuration values for this plugin for a multiselect choice
  public void setConfigValues(String setting, String[] values);

  // For CONFIG_CHOICE settings; this returns the list of choices
  public String[] getConfigOptions(String setting);

  // Returns the help text for a configuration setting
  public String getConfigHelpText(String setting);

  // Returns the label used to present this setting to the user
  public String getConfigLabel(String setting);

  // Resets the configuration of this plugin
  public void resetConfig();
}
