#!/bin/bash
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
rm -rf miniTempClassFiles
mkdir miniTempClassFiles
rm -rf miniTempBuildImages
mkdir miniTempBuildImages
cd miniTempBuildImages
rm -rf images
mkdir images
cd images
cp ../../../images/MiniClient/images/*.gif .
cp ../../../images/MiniClient/images/*.jpg .
cp ../../../images/MiniClient/images/*.png .
cd ..
cd ..
rm -rf minirelease
mkdir minirelease
javac -deprecation -O -cp ../third_party/JOGL/Linux/gluegen-rt.jar:../third_party/JOGL/Linux/jogl.all.jar:../third_party/JOGL/Linux/nativewindow.all.jar -sourcepath ../java:../third_party/jtux/java:../third_party/jcraft/java -d miniTempClassFiles ../java/sage/miniclient/MiniClient.java  ../java/sage/PowerManagement.java ../java/sage/UIUtils.java ../java/sage/miniclient/OpenGLGFXCMD.java
#javac -source 1.4 -target 1.4 -deprecation -O -cp /sage/jogl2/jar/gluegen-rt.jar:/sage/jogl2/jar/jogl.all.jar:/sage/jogl2/jar/nativewindow.all.jar -sourcepath ../java -d miniTempClassFiles ../java/sage/miniclient/MiniClient.java  ../java/sage/PowerManagement.java ../java/sage/UIUtils.java ../java/sage/miniclient/OpenGLGFXCMD.java
if [[ $? -gt 0 ]]
then
   echo "Error compiling source code"
   exit 1
fi
jar -cf0 minirelease/MiniClient.jar -C miniTempClassFiles sage
jar -uf0 minirelease/MiniClient.jar -C miniTempBuildImages images
jar -uf0 minirelease/MiniClient.jar -C miniTempClassFiles jtux
jar -uf0 minirelease/MiniClient.jar -C miniTempClassFiles com

rm -rf miniTempClassFiles
rm -rf miniTempBuildImages
