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
 * Enables us to seamlessly switch between {@link Seeker} and {@link Seeker2} + {@link Library}.
 * <p/>
 * Any methods that need to be exposed for compatibility must be added to the Hunter interface. Any
 * new methods specific to Seeker2 or Library must not be added to the interface and must be
 * accessed by first checking <code>instanceof Seeker2Library</code>, then using
 * {@link Seeker2Library#getSeeker2()} or {@link Seeker2Library#getLibrary()}.
 */
public class SeekerSelector
{
  // Use the new splitter. This enables recordings will overlapping padding on the same channel to
  // be recorded on one capture device.
  // TODO: This will be enabled on a later commit.
  public static final boolean USE_BETA_SEEKER = false; //Sage.getBoolean("seeker/use_beta_seeker", false);

  public static class HunterHolder
  {
    // TODO: This will be enabled on a later commit.
    private final static Hunter hunterInstance =
      USE_BETA_SEEKER ? null /*Seeker2Library.getInstance()*/ : Seeker.getInstance();;
  }

  /**
   * Returns {@link Seeker2Library#getSeeker2()} or {@link Seeker2Library#getLibrary()} instance
   * depending on the value of {@link SeekerSelector#USE_BETA_SEEKER}.
   */
  public static Hunter getInstance()
  {
    return HunterHolder.hunterInstance;
  }

  private SeekerSelector()
  {
  }

  private static final Object instanceLock = new Object();
  public static Hunter prime()
  {
    Hunter instance = getInstance();
    synchronized (instanceLock)
    {
      // TODO: This will be enabled on a later commit.
      /*if (USE_BETA_SEEKER)
        Seeker2.prime();
      else*/
        Seeker.prime();
    }
    return instance;
  }

  public static String[] getVideoDiskspaceRules()
  {
    // These return the exact same values, but we need to be consistent until/if we decide to retire
    // the old seeker.
    // TODO: This will be enabled on a later commit.
    /*if (USE_BETA_SEEKER)
      return Seeker2.getVideoDiskspaceRules();
    else*/
      return Seeker.getVideoDiskspaceRules();
  }
}
