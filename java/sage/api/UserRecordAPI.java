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
 * API for plugins and other utilities to be able to store arbitrary name-value pairs in the database under a keyed object system.
 */
public class UserRecordAPI {
  private UserRecordAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("UserRecord", "AddUserRecord", new String[] { "Store", "Key" }, true)
    {
      /**
       * Creates a new UserRecord object in the database under the specified data 'Store' and with the
       * specified 'Key'. If a UserRecord already exists with that Store/Key combination, it will be returned instead.
       * @param Store the data store name to add the user record to
       * @param Key the unique key to use for indexing this record
       * @return the newly created UserRecord object, or if one already exists with this Store/Key combination, that is returned, null is returned if any parameters are null or the empty string
       * @since 7.0
       *
       * @declaration public UserRecord AddUserRecord(String Store, String Key);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String key = getString(stack);
        String store = getString(stack);
        if (key == null || store == null || key.length() == 0 || store.length() == 0)
          return null;
        return Wizard.getInstance().addUserRecord(store, key);
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "GetUserRecordData", new String[] { "UserRecord", "Name" })
    {
      /**
       * Gets the Value from the specified Name that's stored in the given UserRecord object.
       * @param UserRecord the UserRecord object
       * @param Name the name to retrieve the corresponding value for from the specified UserRecord object, must not be null or the empty String
       * @return the Value from the specified Name that's stored in the given UserRecord object., null will be returned if the Name has no defined value
       * @since 7.0
       *
       * @declaration public String GetUserRecordData(UserRecord UserRecord, String Name);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String name = getString(stack);
        UserRecord rec = getUserRecord(stack);
        if (name == null || rec == null)
          return null;
        return rec.getProperty(name);
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "SetUserRecordData", new String[] { "UserRecord", "Name", "Value" }, true)
    {
      /**
       * Sets the Value for the specified Name that's stored in the given UserRecord object
       * @param UserRecord the UserRecord object
       * @param Name the Name to set the corresponding Value for in the specified UserRecord object, must not be null or the empty String
       * @param Value the Value to set, use null to clear the existing setting for the specified Name
       * @since 7.0
       *
       * @declaration public void SetUserRecordData(UserRecord UserRecord, String Name, String Value);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String value = getString(stack);
        String name = getString(stack);
        UserRecord rec = getUserRecord(stack);
        if (name == null || rec == null || name.length() == 0)
          return null;
        rec.setProperty(name, value);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "GetUserRecord", new String[] { "Store", "Key" })
    {
      /**
       * Gets an existing UserRecord object in the database under the specified data 'Store' and with the
       * specified 'Key'.
       * @param Store the data store name to retrieve the UserRecord from
       * @param Key the unique key that was used when creating the UserRecord
       * @return the requested UserRecord object, or null if no matching record was found
       * @since 7.0
       *
       * @declaration public UserRecord GetUserRecord(String Store, String Key);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String key = getString(stack);
        String store = getString(stack);
        if (key == null || store == null || key.length() == 0 || store.length() == 0)
          return null;
        return Wizard.getInstance().getUserRecord(store, key);
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "DeleteUserRecord", new String[] { "UserRecord" }, true)
    {
      /**
       * Deletes the specified UserRecord object from the database.
       * @param UserRecord the UserRecord object to remove from the database
       * @return true if the specified UserRecord was removed from the database, false if it no longer exists in the database
       * @since 7.0
       *
       * @declaration public boolean DeleteUserRecord(UserRecord UserRecord);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UserRecord rec = getUserRecord(stack);
        return Wizard.getInstance().removeUserRecord(rec) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "GetAllUserRecords", new String[] { "Store" })
    {
      /**
       * Gets all existing UserRecord objects that exist under the specified data Store.
       * @param Store the data store name to retrieve the UserRecords from
       * @return an array of all UserRecord objects in the database under the specified Store
       * @since 7.0
       *
       * @declaration public UserRecord[] GetAllUserRecords(String Store);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        String store = getString(stack);
        return Wizard.getInstance().getAllUserRecords(store);
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "GetAllUserStores")
    {
      /**
       * Gets all existing Stores that UserRecord objects have been created under in the database.
       * @return an array of all the Store names that exist for UserRecords in the database
       * @since 7.0
       *
       * @declaration public String[] GetAllUserStores();
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        return Wizard.getInstance().getAllUserStores();
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "DeleteAllUserRecords", new String[] { "Store" }, true)
    {
      /**
       * Deletes the all the UserRecords from the database under the specified Store.
       * @param Store the Store name that should have all corresponding UserRecords deleted
       * @since 7.0
       *
       * @declaration public void DeleteAllUserRecords(String Store);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Wizard wiz = Wizard.getInstance();
        UserRecord[] allRecs = wiz.getAllUserRecords(getString(stack));
        for (int i = 0; i < allRecs.length; i++)
          wiz.removeUserRecord(allRecs[i]);
        return null;
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "IsUserRecordObject", 1, new String[] { "UserRecord" })
    {
      /**
       * Returns true if the passed in argument is a UserRecord object
       * @param UserRecord the object to test to see if it is a UserRecord object
       * @return true if the passed in argument is a UserRecord object, false otherwise
       * @since 7.0
       *
       * @declaration public boolean IsUserRecordObject(Object UserRecord);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object p = stack.pop();
        if (p instanceof sage.vfs.MediaNode)
          p = ((sage.vfs.MediaNode) p).getDataObject();
        return Boolean.valueOf(p instanceof UserRecord);
      }});
    rft.put(new PredefinedJEPFunction("UserRecord", "GetUserRecordNames", new String[] { "UserRecord" })
    {
      /**
       * Gets a list of all the 'Name' values used in name->value pairs in this UserRecord object
       * @param UserRecord the UserRecord object to get the list of Names stored in
       * @return an array of all the names used to store data within this UserRecord object
       * @since 7.0
       *
       * @declaration public String[] GetUserRecordNames(UserRecord UserRecord);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        UserRecord rec = getUserRecord(stack);
        return rec == null ? Pooler.EMPTY_STRING_ARRAY : rec.getNames();
      }});
  }
}
