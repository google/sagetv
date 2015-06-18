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
 * This interface is used for the first argument passed to the constructor of a SageTVPlugin implementation.
 * It is also the interface returned from the Plugin API call GetSageTVPluginRegistry() which can be used to subscribe/unsubscribe to
 * SageTV events from outside of the plugin framework.
 */
public interface SageTVPluginRegistry
{
  // Call this method to subscribe to a specific event
  public void eventSubscribe(SageTVEventListener listener, String eventName);

  // Call this method to unsubscribe from a specific event
  public void eventUnsubscribe(SageTVEventListener listener, String eventName);

  // This will post the event asynchronously to SageTV's plugin event queue and return immediately
  public void postEvent(String eventName, java.util.Map eventVars);

  // This will post the event asynchronously and return immediately; unless waitUntilDone is true,
  // and then it will not return until all the subscribed plugins have received the event.
  public void postEvent(String eventName, java.util.Map eventVars, boolean waitUntilDone);
}
