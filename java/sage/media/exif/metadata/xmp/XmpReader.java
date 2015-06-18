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


import java.io.UnsupportedEncodingException;

import sage.media.exif.metadata.Metadata;
import sage.media.exif.metadata.MetadataReader;


/**
 * Decodes XMP data, populating a <code>Metadata</code> object with tag values
 * in <code>XmpDirectory</code>
 *
 * @author Niel Markwick
 */
public class XmpReader implements MetadataReader {

    private static final String XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/\0";
    /**
     * The Xmp segment as an array of bytes.
     */
    private final byte[] _data;

    /**
     * Bean instance to store information about the image and
     * camera/scanner/capture device.
     */
    private Metadata _metadata;

    /**
     * The Xmp directory used (loaded lazily)
     */
    private XmpDirectory _xmpDirectory = null;

    /**
     * Creates an XmpReader for the given Xmp data segment.
     */
    public XmpReader(byte[] data) {
        _data = data;
    }

    /**
     * Performs the Xmp data extraction, returning a new instance of
     * <code>Metadata</code>.
     */
    public Metadata extract() {
        return extract(new Metadata());
    }

    /**
     * Performs the Xmp data extraction, adding found values to the specified
     * instance of <code>Metadata</code>.
     */
    public Metadata extract(Metadata metadata) {
        _metadata = metadata;
        if (_data == null)
            return _metadata;

        XmpDirectory directory = getXmpDirectory();
        // check for the header length
        if (_data.length <= 30)
            return _metadata;

        if (!isXmpSegment(_data)) {
            // check for the header preamble
            directory.addError("XMP data segment doesn't begin with "
                    + XMP_IDENTIFIER.substring(0, XMP_IDENTIFIER.length() - 1));
            return _metadata;
        }

        // TODO extract tags from XML. For now we just store the XML text
        try {
        	directory.setString(XmpDirectory.TAG_XML_DOC, new String(_data, 29,
                _data.length - 29,"UTF-8"));
        } catch (UnsupportedEncodingException e){
        	directory.addError(e.toString());
        }
        return metadata;
    }

    public static boolean isXmpSegment(byte[] data) {
        if (data == null || data.length <= 30) {
            return false;
        }
        if (!XMP_IDENTIFIER.equals(new String(data, 0, 29))) {
            return false;
        }
        return true;
    }

    private XmpDirectory getXmpDirectory() {
        if (_xmpDirectory == null) {
            _xmpDirectory = (XmpDirectory) _metadata
                    .getDirectory(XmpDirectory.class);
        }
        return _xmpDirectory;
    }

}
