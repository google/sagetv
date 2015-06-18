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
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <netinet/in.h>
#include <arpa/nameser.h>
#include <resolv.h>

#include "sage_CVMUtils.h"

/*
 * Class:     sage_CVMUtils
 * Method:    reloadNameserverCache
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sage_CVMUtils_reloadNameserverCache
  (JNIEnv * env, jclass jc)
{
	return res_init();
}

