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

/*
 * This represents a user interface instance which may be remote or local. This is implemented
 * by the UIManaged for local UIs and by an inner class of SageTVConnection for remote UIs
 */
public interface UIClient
{
  public static final int LOCAL = 1;
  public static final int REMOTE_CLIENT = 2;
  public static final int REMOTE_UI = 3;
  public String getFullUIClientName();
  public String getLocalUIClientName();
  // Hooks processed through here are NOT rethreaded
  public Object processUIClientHook(String hookName, Object[] hookVars);
  public int getUIClientType();
  // This is the original clientName we used to use and is specific to a SageTVClient connection
  public String getUIClientHostname();

  // Allows to query the client for capabilities
  public String getCapability(String capability);
}
