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

import java.util.HashMap;

import sage.media.exif.metadata.Directory;


public class XmpDirectory extends Directory {

    public static final int TAG_XML_DOC = 1;

    protected static final HashMap tagNameMap = new HashMap();

    static {
        tagNameMap.put(new Integer(TAG_XML_DOC), "XML Document");
    }

    public String getName() {
        return "Xmp";
    }

    protected HashMap getTagNameMap() {
        return tagNameMap;
    }

    public XmpDirectory() {
        super();
        this.setDescriptor(new XmpDescriptor(this));
    }

}
