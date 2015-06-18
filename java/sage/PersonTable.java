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
 * Override add, remove, and delete from Table so we can do some
 * different indexing.
 *
 * @author codefu@google.com (John McDole)
 */

public class PersonTable extends Table {
  PersonTable(byte inTableCode, Index inPrimary, Index[] inOthers) {
    super(inTableCode, inPrimary, inOthers);
  }

  @Override
  void add(DBObject addMe, boolean logTX) {
    super.add(addMe, logTX);
    Wizard.getInstance().addPersonToLucene((Person)addMe);
  }

  @Override
  boolean remove(DBObject removeMe, boolean logTX) {
    Wizard.getInstance().deletePersonFromLucene((Person)removeMe);
    return super.remove(removeMe, logTX);
  }

  @Override
  void update(DBObject updateMe, DBObject newMe, boolean logTX) {
    Wizard.getInstance().deletePersonFromLucene((Person)updateMe);
    super.update(updateMe, newMe, logTX);
    Wizard.getInstance().addPersonToLucene((Person)updateMe);
  }
}
