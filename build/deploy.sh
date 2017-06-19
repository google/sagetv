#!/usr/bin/env bash

#
# Copyright 2017 The SageTV Authors. All Rights Reserved.
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

# Will read the last git commit message and if that message contains '[ci release]' then this script will
# push to Bintray as a release, tagging the release with the version tag
# eg, if the message was "Updated release version [ci release]"
# then this script push the release to Bintray AFTER a successful build that is NOT a pull request.
# It will then git tag the release with the VERSION tag and then push that tag to github.

# Secure Environment Requirements
# BINTRAY_USER  : For releasing
# BINTRAY_API   : For releasing
# GITHUB_KEY    : For tagging (if empty then tagging will not be done)
# GITHUB_USER   : For tagging
# GITHUB_EMAIL  : For tagging

# Just exit if we are pull request
if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
    echo "Pull Request.  Will not deploy."
    exit 0
fi

MAJOR_VERSION=`grep MAJOR_VERSION java/sage/Version.java | grep -o [0-9]*`
MINOR_VERSION=`grep MINOR_VERSION java/sage/Version.java | grep -o [0-9]*`
MICRO_VERSION=`grep MICRO_VERSION java/sage/Version.java | grep -o [0-9]*`
VERSION=${VERSION:-${MAJOR_VERSION}.${MINOR_VERSION}.${MICRO_VERSION}}

if git log -1 --pretty=%B | grep -F "[ci release]" ; then
    echo "Releasing ${VERSION} of SageTV"
else
    echo "Not a release build.  Exiting."
    # just for testing
    if [ -z "${FORCE_RELEASE}" ] ; then
        exit 0
    else
        echo "Force Releasing ${VERSION}"
    fi
fi

TAG=V${VERSION}
if git tag -l "${TAG}" | grep "${TAG}" ; then
    echo "Version ${VERSION} already exists"
else
    echo "Version ${VERSION} Appears to be OK"
fi

if [ -z "${BINTRAY_API}" ] ; then
    echo "Deploy Failed! BINTRAY_API not set"
    exit 1
fi

if [ -z "${BINTRAY_USER}" ] ; then
    echo "Deploy Failed! BINTRAY_USER not set"
    exit 1
fi

echo "Uploading ${VERSION} to Bintray"
if [ -e ../build.gradle ] ; then
    cd ..
fi
./gradlew bintrayUpload || { echo "Upload Failed for ${VERSION}" ; exit 1 ; }
echo "SageTV version ${VERSION} uploaded to Bintray"

# if we have github key configured that now tag and push this release
if [ ! -z "${GITHUB_KEY}" ] ; then
    echo "Tagging Release ${TAG}"
    git config user.email "${GITHUB_EMAIL}"
    git config user.name "${GITHUB_USER}"
    git tag ${TAG}
    git push --quiet https://${GITHUB_KEY}@github.com/google/sagetv ${TAG} > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "Tagged ${VERSION} in GitHub"
    else
        echo "Failed to TAG ${VERSION} and push to GitHub.  Release is still published, though."
    fi
fi
