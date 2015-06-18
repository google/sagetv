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
 *
 * @author Narflex
 */
public class Permissions
{
  public static final String ADMIN_PROFILE = "Administrator";

  // Predefined Permissions
  public static final String PERMISSION_WATCHEDTRACKING = "WatchedTracking";
  public static final String PERMISSION_RECORDINGSCHEDULE = "RecordingSchedule";
  public static final String PERMISSION_DELETE = "Delete";
  public static final String PERMISSION_SYSTEMMESSAGE = "SystemMessage";
  public static final String PERMISSION_EDITMETADATA = "EditMetadata";
  public static final String PERMISSION_ARCHIVE = "Archive";
  public static final String PERMISSION_PLAYLIST = "Playlist";
  public static final String PERMISSION_CONVERSION = "Conversion";
  public static final String PERMISSION_SAVEONLINEVIDEO = "SaveOnlineVideo";
  public static final String PERMISSION_UICONFIGURATION = "UIConfiguration";
  public static final String PERMISSION_SECURITY = "Security";
  public static final String PERMISSION_GENERALSETUP = "GeneralSetup";
  public static final String PERMISSION_FILESYSTEM = "Filesystem";
  public static final String PERMISSION_STUDIO = "Studio";
  public static final String PERMISSION_LIVETV = "LiveTV";
  public static final String PERMISSION_PICTUREROTATION = "PictureRotation";

  public static final String[] PREDEFINED_PERMISSIONS = new String[] {
    PERMISSION_WATCHEDTRACKING, PERMISSION_RECORDINGSCHEDULE, PERMISSION_DELETE, PERMISSION_SYSTEMMESSAGE,
    PERMISSION_EDITMETADATA, PERMISSION_ARCHIVE, PERMISSION_PLAYLIST, PERMISSION_CONVERSION,
    PERMISSION_SAVEONLINEVIDEO, PERMISSION_UICONFIGURATION, PERMISSION_SECURITY, PERMISSION_GENERALSETUP, PERMISSION_FILESYSTEM,
    PERMISSION_STUDIO, PERMISSION_LIVETV, PERMISSION_PICTUREROTATION
  };

  private Permissions()
  {
  }

  public static String getDefaultSecurityProfile()
  {
    return Sage.get("security/default_profile", "Administrator");
  }

  public static boolean setDefaultSecurityProfile(String profile)
  {
    if (!isValidSecurityProfile(profile))
      return false;
    Sage.put("security/default_profile", profile);
    NetworkClient.distributePropertyChange("security/default_profile");
    return true;
  }

  public static boolean isValidSecurityProfile(String profile)
  {
    if (ADMIN_PROFILE.equals(profile))
      return true;
    java.util.Set profileSet = Sage.parseCommaDelimSet(Sage.get("security/profiles", ""));
    return profileSet.contains(profile);
  }

  public static String[] getSecurityProfiles()
  {
    java.util.Set profileSet = Sage.parseCommaDelimSet(Sage.get("security/profiles", ""));
    String[] rv = new String[profileSet.size() + 1];
    System.arraycopy(profileSet.toArray(), 0, rv, 1, rv.length - 1);
    rv[0] = ADMIN_PROFILE;
    return rv;
  }

  public static boolean addSecurityProfile(String profile)
  {
    if (ADMIN_PROFILE.equals(profile))
      return false;
    java.util.Set profileSet = Sage.parseCommaDelimSet(Sage.get("security/profiles", ""));
    if (profileSet.contains(profile))
      return false;
    profileSet.add(profile);
    Sage.put("security/profiles", Sage.createCommaDelimSetString(profileSet));
    NetworkClient.distributePropertyChange("security/profiles");
    return true;
  }

  public static boolean removeSecurityProfile(String profile)
  {
    if (ADMIN_PROFILE.equals(profile))
      return false;
    java.util.Set profileSet = Sage.parseCommaDelimSet(Sage.get("security/profiles", ""));
    if (!profileSet.contains(profile))
      return false;
    profileSet.remove(profile);
    Sage.put("security/profiles", Sage.createCommaDelimSetString(profileSet));
    NetworkClient.distributePropertyChange("security/profiles");
    return true;
  }

  public static boolean hasPermission(String permission, String profile)
  {
    if (ADMIN_PROFILE.equals(profile))
      return true;
    return Sage.getBoolean("security/profile/" + profile + "/" + permission, true);
  }

  // NOTE: This will return true if UIManager is null because then there is no valid security context!
  public static boolean hasPermission(String permission, UIManager uiMgr)
  {
    if (uiMgr == null) return true;
    return hasPermission(permission, uiMgr.getActiveSecurityProfile());
  }

  public static void setPermission(String permission, String profile, boolean allowed)
  {
    if (ADMIN_PROFILE.equals(profile))
      return;
    String prop = "security/profile/" + profile + "/" + permission;
    Sage.putBoolean(prop, allowed);
    NetworkClient.distributePropertyChange(prop);
  }
}
