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
package sage.locator;

/**
 *
 * @author Narflex
 */
public interface LocatorConstants
{
  public static final int DATA_OFFSET = 12;

  public static final int LOCATOR_PORT = 8018;
  public static final String LOCATOR_SERVER_PROP = "locator_server";
  public static final String BACKUP_LOCATOR_SERVER_PROP = "locator_server_backup";
  public static final String LOCATOR_SERVER = "locator.mu3d.com";
  public static final String BACKUP_LOCATOR_SERVER = "locator.mu3d.com";

  public static final int MAX_HANDLE_SIZE = 64;
  public static final int MAX_RELATIVE_PATH_SIZE = 1024;
  public static final int MAX_MSG_TEXT_SIZE = 65536;

  public static final int PING_SUCCEED = 10;
  public static final int PING_FAILED_BAD_SERVICE = 3;
  public static final int PING_FAILED_BAD_PORT = 2;
  public static final int PING_FAILED_BAD_IP = 1;
  public static final int PING_FAILED_BAD_GUID = 0;

  public static final int CHALLENGE_LENGTH = 16;

  public static final int STANDARD_CLIENT_UPDATE_REQUEST = 0;
  public static final int CHALLENGE_RESPONSE = 1;
  public static final int NEW_CLIENT_AUTH = 2;
  public static final int CLIENT_DISCONNECT = 5;
  public static final int LOOKUP_IP_FOR_GUID = 8;
  public static final int PING_TEST_GUID = 16;

  // New User oriented locator calls
  public static final int USER_UPDATE_REQUEST = 32;
  public static final int REQUEST_HANDLE = 33;
  public static final int DELETE_MESSAGE = 34;
  public static final int GET_FRIEND_LIST = 36;
  public static final int KILL_FRIENDSHIP = 37;
  public static final int LOOKUP_IP_FOR_FRIEND = 38;
  public static final int GET_NEW_MESSAGES = 39;
  public static final int SEND_MESSAGE = 40;
  public static final int GET_FRIEND_AVATAR = 41;
  public static final int SUBMIT_AVATAR = 42;

  // Reply codes
  public static final int SUCCESS_REPLY = 0;
  public static final int HANDLE_ALREADY_USED = 100;
  public static final int HANDLE_NAME_NOT_ALLOWED = 101;
  public static final int FRIEND_REQUEST_DENIED_ABUSE = 102;
  public static final int TARGET_IS_NOT_A_FRIEND = 103;
  public static final int MALFORMED_REQUEST = 104;
  public static final int CHALLENGE_RESPONSE_FAILED = 106;
  public static final int LOCATOR_ID_NOT_FOUND = 107;
  public static final int LOCATOR_ALREADY_ASSOCIATED_WITH_HANDLE = 108;
  public static final int REQUESTOR_HANDLE_NOT_IN_DB = 109;
  public static final int DUPLICATE_FRIEND_REQUEST = 110;
  public static final int TARGET_HANDLE_NOT_IN_DB = 111;
  public static final int FRIEND_REQUEST_NOT_FOUND = 112;

  // locator media types
  public static final int PICTURE_TYPE = 1;
  public static final int VIDEO_TYPE = 2;
  public static final int AUDIO_TYPE = 3;
  public static final int UNKNOWN_TYPE = 4;

  // Locator message types
  public static final int FRIEND_REQUEST_MSG = 1;
  public static final int FRIEND_REPLY_ACCEPT_MSG = 2;
  public static final int FRIEND_REPLY_REJECT_MSG = 3;
  public static final int NORMAL_MSG = 5;

  public static final int RELATIONSHIP_REQUESTED = 1;
  public static final int RELATIONSHIP_ACCEPTED = 2;
  public static final int RELATIONSHIP_REJECTED = 3;

  public static final int UPDATED_MSGS_MASK = 1;
  public static final int UPDATED_FRIENDS_MASK = 2;
}
