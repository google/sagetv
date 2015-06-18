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
package sage.api;

import sage.*;

/**
 * Calls for dealing with permission based access to various capabilities in the SageTV platform and UI.
 */
public class Security
{
  private Security() {}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Security", "GetActiveSecurityProfile")
    {
      /**
       * Returns the name of the current security profile for the UI client making the API call.
       * @return the name of the current security profile for the UI client making the API call, returns null if there's no valid UI context for this call
       * @since 7.1
       *
       * @declaration public String GetActiveSecurityProfile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null) return null;
        return uiMgr.getActiveSecurityProfile();
      }});
    rft.put(new PredefinedJEPFunction("Security", "GetDefaultSecurityProfile", true)
    {
      /**
       * Returns the name of the default security profile to use when a new client connects that does not have an associated security profile.
       * @return the name of the default security profile to use when a new client connects that does not have an associated security profile
       * @since 7.1
       *
       * @declaration public String GetDefaultSecurityProfile();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Permissions.getDefaultSecurityProfile();
      }});
    rft.put(new PredefinedJEPFunction("Security", "SetActiveSecurityProfile", new String[] { "Profile" })
    {
      /**
       * Sets the name of the current security profile for the UI client making the API call.
       * @param Profile the name of the security profile
       * @return true if the call succeeded; false if the specified profile does not exist or there is no valid UI context for this call
       * @since 7.1
       *
       * @declaration public boolean SetActiveSecurityProfile(String Profile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String profile = getString(stack);
        UIManager uiMgr = stack.getUIMgr();
        if (uiMgr == null) return Boolean.FALSE;
        // NOTE: We cannot disallow changing the active security profile based on permissions. If we did so, then there would be no way of changing
        // the profile under a UI that does not have security permissions.
        return uiMgr.setActiveSecurityProfile(profile) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Security", "SetDefaultSecurityProfile", new String[] { "Profile" }, true)
    {
      /**
       * Sets the name of the default security profile to use when a new client connects that does not have an associated security profile.
       * @param Profile the name of the default security profile to use when a new client connects that does not have an associated security profile
       * @return true if the call succeeds, false if the specified profile does not exist
       * @since 7.1
       *
       * @declaration public boolean SetDefaultSecurityProfile(String Profile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String profile = getString(stack);
        return (Permissions.hasPermission(Permissions.PERMISSION_SECURITY, stack.getUIMgr()) &&
            Permissions.setDefaultSecurityProfile(profile)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Security", "GetSecurityProfiles", true)
    {
      /**
       * Returns the names of the different security profiles.
       * @return the names of the different security profiles
       * @since 7.1
       *
       * @declaration public String[] GetSecurityProfiles();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Permissions.getSecurityProfiles();
      }});
    rft.put(new PredefinedJEPFunction("Security", "AddSecurityProfile", new String[] { "Profile" }, true)
    {
      /**
       * Adds a new security profile with the specified name. Unless this profile existed before; all permissions will default to false. If the
       * profile existed before; it's old settings will be the initial settings for this new profile.
       * @param Profile the name for the new security profile
       * @return true if this was added as a new security profile, false otherwise (it'll only fail if the name is already in use)
       * @since 7.1
       *
       * @declaration public boolean AddSecurityProfile(String Profile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String profile = getString(stack);
        return (Permissions.hasPermission(Permissions.PERMISSION_SECURITY, stack.getUIMgr()) &&
            Permissions.addSecurityProfile(profile)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Security", "RemoveSecurityProfile", new String[] { "Profile" }, true)
    {
      /**
       * Removes the security profile with the specified name. You cannot remove the Administrator profile. If a user session is currently
       * active under the profile being removed; it will continue to remain active under that profile with the current permissions until that
       * user session expires.
       * @param Profile the name for the security profile to remove
       * @return true if this was removed as a security profile, false otherwise (it'll only fail if the name isn't in use or is Administrator)
       * @since 7.1
       *
       * @declaration public boolean RemoveSecurityProfile(String Profile);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String profile = getString(stack);
        return (Permissions.hasPermission(Permissions.PERMISSION_SECURITY, stack.getUIMgr()) &&
            Permissions.removeSecurityProfile(profile)) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Security", "GetPredefinedPermissions")
    {
      /**
       * Returns a list of all the predefined permission names. Plugins are free to define their own new permissions using any string they like;
       * but those will not be returned from this API call.
       * @return a list of all the predefined permission names
       * @since 7.1
       *
       * @declaration public String[] GetPredefinedPermissions();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Permissions.PREDEFINED_PERMISSIONS.clone(); // to protect against modifications
      }});
    rft.put(new PredefinedJEPFunction("Security", "SetPermission", new String[] { "Permission", "Profile", "Allowed" }, true)
    {
      /**
       * Sets whether or not a permission is allowed under a specific security profile.
       * @param Permission the name of the permission to set
       * @param Profile the name of the security profile this permission applies to
       * @param Allowed true if the permission should be granted, false if it should be denied
       * @since 7.1
       *
       * @declaration public void SetPermission(String Permission, String Profile, boolean Allowed);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean allowed = getBool(stack);
        String profile = getString(stack);
        String perm = getString(stack);
        if (Permissions.hasPermission(Permissions.PERMISSION_SECURITY, stack.getUIMgr()))
          Permissions.setPermission(perm, profile, allowed);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("Security", "HasPermission", -1, new String[] { "Permission", "Profile" })
    {
      /**
       * Returns true if the specified permission is allowed under the specified security profile. If this permission has not
       * been explicitly set to false for that profile; this method will return true. The return value is undefined if the specified profile is invalid.
       * @param Permission the name of the permission
       * @param Profile the of the security profile
       * @return false if the specified permission is denied under the specified profile, true otherwise
       * @since 7.1
       *
       * @declaration public boolean HasPermission(String Permission, String Profile);
       */

      /**
       * Returns true if the specified permission is allowed under the security profile active for the UI making this API call. If this permission has not
       * been explicitly set to false for that profile; this method will return true.
       * @param Permission the name of the permission
       * @return false if the specified permission is denied under the active security profile or if the current context has no security profile, true otherwise
       * @since 7.1
       *
       * @declaration public boolean HasPermission(String Permission);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String profile = null;
        if (curNumberOfParameters == 2)
        {
          profile = getString(stack);
        }
        else
        {
          UIManager uiMgr = stack.getUIMgr();
          profile = uiMgr.getActiveSecurityProfile();
        }
        String perm = getString(stack);
        if (profile == null)
          return Boolean.FALSE; // undefined profile or context
        return Permissions.hasPermission(perm, profile) ? Boolean.TRUE : Boolean.FALSE;
      }});
  }
}
