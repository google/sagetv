#!/bin/sh
#
# Copyright 2015 The SageTV Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

echo Build the Sage.jar file
./buildsage.sh || { echo "Build failed, exiting."; exit 1; }
echo  Build the Miniclient jar file
./buildmini.sh || { echo "Build failed, exiting."; exit 1; }
echo  Build the shared libraries
./buildso.sh || { echo "Build failed, exiting."; exit 1; }
echo  Build the 3rdparty binaries
./build3rdparty.sh || { echo "Build failed, exiting."; exit 1; }
echo  Copy the files for the server install
./copyserverfiles.sh || { echo "Build failed, exiting."; exit 1; }
echo  Copy the files for the client install
./copyclientfiles.sh || { echo "Build failed, exiting."; exit 1; }
echo  Build the tarballs and debian packages
./buildtarballs.sh || { echo "Build failed, exiting."; exit 1; }
./buildubuntu.sh || { echo "Build failed, exiting."; exit 1; }
