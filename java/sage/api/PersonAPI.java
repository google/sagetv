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
 * Person is an object that corresponds to an actual Person (or Team). It depends on the EPG implementation
 * for whether this is solely based on a name or an actual name + ID set which would then possibly also
 * include all the additional information about a Person as well as imagery for them.
 * <p>
 * SageTV will automatically convert the following types to Person if used for a parameter that requires the Person type:<p>
 * String - this may not be exact since multiple Person objects can have the same name, but it will resolve to one<p>
 */
public class PersonAPI {
  private PersonAPI() {}
  public static void init(Catbert.ReflectionFunctionTable rft)
  {
    rft.put(new PredefinedJEPFunction("Person", "HasPersonImage", new String[] { "Person" })
    {
      /**
       * Returns true if the passed in Person has an image associated with them
       * @param Person the Person object
       * @return true if the passed in Person has an image associated with them, false otherwise
       * @since 8.0
       *
       * @declaration public boolean HasPersonImage(Person Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        return (p != null && p.hasImage()) ? Boolean.TRUE : Boolean.FALSE;
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonImage", new String[] { "Person", "Thumb" })
    {
      /**
       * Returns the image for the specified person
       * @param Person the Person object
       * @param Thumb true if a thumbnail is desired, false if a full size image is desired
       * @return a MetaImage object representing the requested image, null if one does not exist
       * @since 8.0
       *
       * @declaration public MetaImage GetPersonImage(Person Person, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Person p = getPerson(stack);
        if (p != null && p.hasImage())
        {
          return MetaImage.getMetaImage(p.getImageURL(thumb), stack.getUIComponent());
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonImageURL", new String[] { "Person", "Thumb" })
    {
      /**
       * Returns the image URL for the specified person
       * @param Person the Person object
       * @param Thumb true if a thumbnail is desired, false if a full size image is desired
       * @return a URL representing the requested image, null if one does not exist
       * @since 8.0
       *
       * @declaration public String GetPersonImageURL(Person Person, boolean Thumb);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        boolean thumb = evalBool(stack.pop());
        Person p = getPerson(stack);
        if (p != null && p.hasImage())
        {
          return p.getImageURL(thumb);
        }
        else
          return null;
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonDateOfBirth", new String[] { "Person" })
    {
      /**
       * Returns a String representing the birthdate of the specified person, empty string if unknown
       * @param Person the Person object
       * @return a String representing the birthdate of the specified person, empty string if unknown
       * @since 8.0
       *
       * @declaration public String GetPersonDateOfBirth(Person Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        return (p != null ) ? p.getDateOfBirth() : "";
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonDateOfDeath", new String[] { "Person" })
    {
      /**
       * Returns a String representing the date of the specified person's death, empty string if unknown
       * @param Person the Person object
       * @return a String representing the date of the specified person's death, empty string if unknown
       * @since 8.0
       *
       * @declaration public String GetPersonDateOfDeath(Person Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        return (p != null ) ? p.getDateOfDeath() : "";
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonBirthplace", new String[] { "Person" })
    {
      /**
       * Returns a String representing the birthplace of the specified person, empty string if unknown
       * @param Person the Person object
       * @return a String representing the birthplace of the specified person, empty string if unknown
       * @since 8.0
       *
       * @declaration public String GetPersonBirthplace(Person Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        return (p != null ) ? p.getBirthplace() : "";
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonID", new String[] { "Person" })
    {
      /**
       * Returns the unique ID used to identify this Person. Can get used later on a call to {@link #GetPersonForID GetPersonForID()}
       * @param Person the Person object
       * @return the unique ID used to identify this Person
       * @since 8.1
       *
       * @declaration public int GetPersonID(Person Person);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Person p = getPerson(stack);
        return (p == null) ? null : new Integer(p.getID());
      }});
    rft.put(new PredefinedJEPFunction("Person", "GetPersonForID", 1, new String[] { "PersonID" })
    {
      /**
       * Returns the Person object that corresponds to the passed in ID. The ID should have been obtained from a call to {@link #GetPersonID GetPersonID()}
       * @param PersonID the Person id
       * @return the Person object that corresponds to the passed in ID
       * @since 8.1
       *
       * @declaration public Person GetPersonForID(int PersonID);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        int i = getInt(stack);
        return Wizard.getInstance().getPersonForID(i);
      }});
    rft.put(new PredefinedJEPFunction("Person", "IsPersonObject", 1, new String[] { "Object" })
    {
      /**
       * Returns true if the specified object is a Person object. No automatic type conversion will be performed on the argument.
       * @param Object the object to test to see if it is a Person object
       * @return true if the argument is a Person object, false otherwise
       * @since 9.0
       *
       * @declaration public boolean IsPersonObject(Object Object);
       */
      public Object runSafely(Catbert.FastStack stack) throws Exception{
        Object o = stack.pop();
        if (o instanceof sage.vfs.MediaNode)
          o = ((sage.vfs.MediaNode) o).getDataObject();
        return Boolean.valueOf(o instanceof Person);
      }});


    /*
		rft.put(new PredefinedJEPFunction("Person", "", 1, new String[] { "Person" })
		{public Object runSafely(Catbert.FastStack stack) throws Exception{
			 return getShow(stack).;
			}});
     */
  }
}
