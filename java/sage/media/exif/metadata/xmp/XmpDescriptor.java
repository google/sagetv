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
package sage.media.exif.metadata.xmp;

import sage.media.exif.metadata.Directory;
import sage.media.exif.metadata.MetadataException;
import sage.media.exif.metadata.TagDescriptor;

/**
 * Provides human-readable string represenations of tag values stored in a
 * <code>XmpDirectory</code>.
 */
public class XmpDescriptor extends TagDescriptor {

    /**
     * @param directory
     */
    public XmpDescriptor(Directory directory) {
        super(directory);

    }

    /**
     * Returns a descriptive value of the the specified tag
     *
     * @param tagType
     *            the tag to find a description for
     * @return a description of the image's value for the specified tag, or
     *         <code>null</code> if the tag hasn't been defined.
     */
    public String getDescription(int tagType) throws MetadataException {
        // XMP only contains XMLDoc\
        return _directory.getString(tagType);
    }
}
