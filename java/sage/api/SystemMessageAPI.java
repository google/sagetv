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
import sage.msg.SystemMessage;

/**
 *
 * @author Narflex
 */
public class SystemMessageAPI
{

  private SystemMessageAPI(){	}

  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemAlertLevel", true)
    {
      /**
       * Gets the global alert level in the system.
       * @return a value from 0-3; with 0=No Alert, 1=Info Alert, 2=Warning Alert, 3=Error Alert
       * @since 6.6
       *
       * @declaration public int GetSystemAlertLevel();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return new Integer(sage.msg.MsgManager.getInstance().getAlertLevel());
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessages", true)
    {
      /**
       * Returns the list of SystemMessage objects currently in the queue.
       * @return an array of SystemMessage objects currently in the queue
       * @since 6.6
       *
       * @declaration public SystemMessage[] GetSystemMessages();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return (sage.msg.SystemMessage[]) sage.msg.MsgManager.getInstance().getSystemMessages().clone();
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "ResetSystemAlertLevel", true)
    {
      /**
       * Resets the global alert level in the system back to zero.
       * @since 6.6
       *
       * @declaration public void ResetSystemAlertLevel();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Permissions.hasPermission(Permissions.PERMISSION_SYSTEMMESSAGE, stack.getUIMgr()))
        {
          sage.msg.MsgManager.getInstance().clearAlertLevel();
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.SYSTEM_ALERT_LEVEL_RESET, (Object[])null);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "DeleteAllSystemMessages", true)
    {
      /**
       * Deletes all the SystemMessages from the queue. This will not have any effect on the global alert level.
       * @since 6.6
       *
       * @declaration public void DeleteAllSystemMessages();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        if (Permissions.hasPermission(Permissions.PERMISSION_SYSTEMMESSAGE, stack.getUIMgr()))
        {
          sage.msg.MsgManager.getInstance().clearSystemMessages();
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.SYSTEM_ALERT_LEVEL_RESET, (Object[])null);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.ALL_SYSTEM_MESSAGES_REMOVED, (Object[])null);
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "DeleteSystemMessage", new String[] { "SystemMessage" }, true)
    {
      /**
       * Deletes the specified SystemMessage from the queue. This will not have any effect on the global alert level.
       * @param message the SystemMessage object to delete
       * @since 6.6
       *
       * @declaration public void DeleteSystemMessage(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sysMsg = getSysMsg(stack);
        if (sysMsg != null && Permissions.hasPermission(Permissions.PERMISSION_SYSTEMMESSAGE, stack.getUIMgr()))
        {
          sage.msg.MsgManager.getInstance().removeSystemMessage(sysMsg);
          sage.plugin.PluginEventManager.postEvent(sage.plugin.PluginEventManager.SYSTEM_MESSAGE_REMOVED,
              new Object[] { sage.plugin.PluginEventManager.VAR_SYSTEMMESSAGE, sysMsg });
        }
        return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageString", new String[] { "SystemMessage" })
    {
      /**
       * Gets the 'message string' associated with this SystemMessage. This is the same result as converting the object to a String.
       * @param message the SystemMessage object to get the 'message string' for
       * @return the 'message string' for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public String GetSystemMessageString(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return sm.getMessageText();
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageTime", new String[] { "SystemMessage" })
    {
      /**
       * Gets the time when this SystemMessage was first posted.
       * @param message the SystemMessage object to get the time of
       * @return the time for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public long GetSystemMessageTime(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return new Long(sm.getTimestamp());
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageEndTime", new String[] { "SystemMessage" })
    {
      /**
       * Gets the time when this SystemMessage was last posted. For messages that did not repeat this will be the same as
       * GetSystemMessageTime. For messages that repeated; this will be the time of the last repeating occurence.
       * @param message the SystemMessage object to get the end time of
       * @return the end time for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public long GetSystemMessageEndTime(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return new Long(sm.getEndTimestamp());
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageRepeatCount", new String[] { "SystemMessage" })
    {
      /**
       * Gets the number of times this message was repeated. For a message that repeated once (i.e. it had 2 occurences), this
       * method will return 2.
       * @param message the SystemMessage object to get the repeat count for
       * @return the repeat count for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public int GetSystemMessageRepeatCount(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return new Integer(sm.getRepeatCount());
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageTypeName", new String[] { "SystemMessage" })
    {
      /**
       * Returns a localized string which represents the type of SystemMessage that was specified.
       * @param message the SystemMessage object to get the type of
       * @return the type for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public String GetSystemMessageTypeName(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
        {
          String rv = sage.msg.SystemMessage.getNameForMsgType(sm.getType());
          if (rv != null && rv.length() > 0)
            return rv;
          rv = sm.getMessageVarValue("typename");
          if (rv != null)
            return rv;
          else
            return "";
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageTypeCode", new String[] { "SystemMessage" })
    {
      /**
       * Returns an integer which represents the type of SystemMessage that was specified.
       * @param message the SystemMessage object to get the type of
       * @return the type for the specified SystemMessage
       * @since 6.6
       *
       * @declaration public int GetSystemMessageTypeCode(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return new Integer(sm.getType());
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageLevel", new String[] { "SystemMessage" })
    {
      /**
       * Returns the alert level for the SystemMessage that was specified.
       * @param message the SystemMessage object to get the alert level of
       * @return a value from 0-3; with 0=No Alert, 1=Info Alert, 2=Warning Alert, 3=Error Alert
       * @since 6.6
       *
       * @declaration public int GetSystemMessageLevel(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return new Integer(sm.getPriority());
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageVariable", new String[] { "SystemMessage", "VarName" })
    {
      /**
       * Returns the SystemMessage variable property associated with the specified SystemMessage. Depending
       * upon the type of message; different variables will be assigned that can be used to do further analysis/processing
       * on the message or to guide the user through resolution steps.
       * @param message the SystemMessage object to lookup the variable in
       * @param VarName the name of the variable to lookup in this SystemMessage (string based values)
       * @return a String that corresponds to the requested variable or null if it does not exist
       * @since 6.6
       *
       * @declaration public String GetSystemMessageVariable(SystemMessage message, String VarName);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String varName = getString(stack);
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
          return sm.getMessageVarValue(varName);
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "PostSystemMessage", new String[] { "MessageCode", "MessageLevel", "MessageString", "MessageVariables" })
    {
      /**
       * Creates a new SystemMessage and posts it to the message queue.
       * Predefined message codes of interest for posting messages are:
       * <br>
       * SOFTWARE_UPDATE_MSG = 1202<br>
       * STORAGE_MONITOR_MSG = 1203<br>
       * GENERAL_MSG = 1204<br>
       * <br> You may also use other user-defined message codes which should be greater than 9999. To give those messages a 'type name' which
       * will be visible by the user; you can defined a message variable with the name 'typename' and then that will be displayed.
       * @param MessageCode the integer code that specifies the type of message
       * @param MessageLevel the integer code specifying the level of the message; 0=Status(does not raise global level),1=Info, 2=Warning, 3=Error
       * @param MessageString a localized message string that explains what the message is in detail
       * @param MessageVariables a java.util.Properties object which has name->value pairs that represent variables corresponding to the details of this message
       * @since 6.6
       *
       * @declaration public void PostSystemMessage(int MessageCode, int MessageLevel, String MessageString, java.util.Properties MessageVariables);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object msgVars = stack.pop();
        String msgString = getString(stack);
        int level = getInt(stack);
        int code = getInt(stack);
        if (Sage.client)
        {
          // We create the string which represents the desired SystemMessage object and then make a server API call w/ that
          // as the MessageString and -1 for the MessageCode. Then the server will turn that String into the actual object.
          // This gets around the problem of the C/S protocol not being able to serialize java.util.Properties objects.
          sage.msg.SystemMessage newMsg = new sage.msg.SystemMessage(code, level, msgString, (msgVars instanceof java.util.Properties) ?
              ((java.util.Properties) msgVars) : null);
          stack.push(new Integer(-1));
          stack.push(new Integer(-1));
          stack.push(newMsg.getPersistentString());
          stack.push(null);
          return makeNetworkedCall(stack);
        }
        if (code < 0)
        {
          // API call from the client w/ the object encoded in the message string
          sage.msg.SystemMessage sysMsg = sage.msg.SystemMessage.buildMsgFromString(msgString);
          sage.msg.MsgManager.postMessage(sysMsg);
          return null;
        }
        sage.msg.SystemMessage newMsg = new sage.msg.SystemMessage(code, level, msgString, (msgVars instanceof java.util.Properties) ?
            ((java.util.Properties) msgVars) : null);
        sage.msg.MsgManager.postMessage(newMsg);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "IsSystemMessageObject", 1, new String[] { "SystemMessage" })
    {
      /**
       * Returns true if the passed in argument is a SystemMessage object
       * @param SystemMessage the object to test to see if it is a SystemMessage object
       * @return true if the passed in argument is a SystemMessage object, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsSystemMessageObject(Object SystemMessage);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object p = stack.pop();
        if (p instanceof sage.vfs.MediaNode)
          p = ((sage.vfs.MediaNode) p).getDataObject();
        return Boolean.valueOf(p instanceof SystemMessage);
      }});
    rft.put(new PredefinedJEPFunction("SystemMessage", "GetSystemMessageVariableNames", new String[] { "SystemMessage" })
    {
      /**
       * Returns the names of the variables associated with the specified SystemMessage. Depending
       * upon the type of message; different variables will be assigned that can be used to do further analysis/processing
       * on the message or to guide the user through resolution steps. The actual values can be retrieved with
       * GetSystemMessageVariable.
       * @param message the SystemMessage object to get the variable names of
       * @return a String array with all the names of the variables for the specified SystemMessage
       * @since 7.0
       *
       * @declaration public String[] GetSystemMessageVariableNames(SystemMessage message);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        sage.msg.SystemMessage sm = getSysMsg(stack);
        if (sm != null)
        {
          return sm.getMessageVarNames();
        }
        else
          return null;
      }});
    /*
		rft.put(new PredefinedJEPFunction("SystemMessage", "", 0, new String[] {  })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return null;
			}});
     */
  }
}
