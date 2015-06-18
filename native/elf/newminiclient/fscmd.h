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
#define FS_RV_SUCCESS 0
#define FS_RV_PATH_EXISTS 1
#define FS_RV_NO_PERMISSIONS 2
#define FS_RV_PATH_DOES_NOT_EXIST 3
#define FS_RV_NO_SPACE_ON_DISK 4
#define FS_RV_ERROR_UNKNOWN 5

#define FSCMD_CREATE_DIRECTORY 64
// pathlen, path

#define FS_PATH_HIDDEN 0x01
#define FS_PATH_DIRECTORY 0x02
#define FS_PATH_FILE 0x04

#define FSCMD_GET_PATH_ATTRIBUTES 65
// pathlen, path

#define FSCMD_GET_FILE_SIZE 66
// pathlen, path
// 64-bit return value

#define FSCMD_GET_PATH_MODIFIED_TIME 67
// pathlen, path
// 64-bit return value

#define FSCMD_DIR_LIST 68
// pathlen, path
// 16-bit numEntries, *(16-bit pathlen, path)

#define FSCMD_LIST_ROOTS 69
// pathlen, path
// 16-bit numEntries, *(16-bit pathlen, path)

#define FSCMD_DOWNLOAD_FILE 70
// secureID[4], offset[8], size[8], pathlen, path

#define FSCMD_UPLOAD_FILE 71
// secureID[4], offset[8], size[8], pathlen, path
