/*
 * This is public domain software - that is, you can do whatever you want
 * with it, and include it software that is licensed under the GNU or the
 * BSD license, or whatever other licence you choose, including proprietary
 * closed source licenses.  I do ask that you leave this header in tact.
 *
 * If you make modifications to this code that you think would benefit the
 * wider community, please send me a copy and I'll post it on my site.
 *
 * If you make use of this code, I'd appreciate hearing about it.
 *   metadata_extractor [at] drewnoakes [dot] com
 * Latest version of this software kept at
 *   http://drewnoakes.com/
 *
 * Created by dnoakes on 12-Nov-2002 18:51:36 using IntelliJ IDEA.
 */
package sage.media.exif.imaging.jpeg;

import java.io.File;
import java.io.InputStream;

import sage.media.exif.metadata.Metadata;
import sage.media.exif.metadata.exif.ExifReader;
import sage.media.exif.metadata.iptc.IptcReader;
import sage.media.exif.metadata.jpeg.JpegCommentReader;
import sage.media.exif.metadata.jpeg.JpegReader;
import sage.media.exif.metadata.xmp.XmpReader;


/**
 * Obtains all available metadata from Jpeg formatted files.
 */
public class JpegMetadataReader {
    // public static Metadata readMetadata(IIOMetadata metadata) throws
    // JpegProcessingException {}
    // public static Metadata readMetadata(ImageInputStream in) throws
    // JpegProcessingException{}
    // public static Metadata readMetadata(IIOImage image) throws
    // JpegProcessingException{}
    // public static Metadata readMetadata(ImageReader reader) throws
    // JpegProcessingException{}

    public static Metadata readMetadata(InputStream in)
            throws JpegProcessingException {
        JpegSegmentReader segmentReader = new JpegSegmentReader(in);
        return extractMetadataFromJpegSegmentReader(segmentReader);
    }

    public static Metadata readMetadata(File file)
            throws JpegProcessingException {
        JpegSegmentReader segmentReader = new JpegSegmentReader(file);
        return extractMetadataFromJpegSegmentReader(segmentReader);
    }

    public static Metadata extractMetadataFromJpegSegmentReader(
            JpegSegmentReader segmentReader) {
        final Metadata metadata = new Metadata();

        // NIELM handle possibilty of multiple app1 segments
        // APP1 may contain EXIF or XMP metadata
        int numApp1Segments = segmentReader
                .getSegmentCount(JpegSegmentReader.SEGMENT_APP1);
        // iterate backwards so that the first EXIF segment overrides the later
        // ones...
        for (int segNum = numApp1Segments - 1; segNum >= 0; segNum--) {
            JpegSegmentData segment = segmentReader.getSegmentData();

            byte[] segmentBytes = segment.getSegment(
                    JpegSegmentReader.SEGMENT_APP1, segNum);

            if (ExifReader.isExifSegment(segmentBytes))
                new ExifReader(segmentBytes).extract(metadata);
            if (XmpReader.isXmpSegment(segmentBytes))
                new XmpReader(segmentBytes).extract(metadata);
            else 
            	// try exif anyway so that errors are logged
            	new ExifReader(segmentBytes).extract(metadata);
        }

        byte[] iptcSegment = segmentReader
                .readSegment(JpegSegmentReader.SEGMENT_APPD);
        new IptcReader(iptcSegment).extract(metadata);

        byte[] jpegSegment = segmentReader
                .readSegment(JpegSegmentReader.SEGMENT_SOF0);
        new JpegReader(jpegSegment).extract(metadata);

        byte[] jpegCommentSegment = segmentReader
                .readSegment(JpegSegmentReader.SEGMENT_COM);
        new JpegCommentReader(jpegCommentSegment).extract(metadata);

        return metadata;
    }

    private JpegMetadataReader() throws Exception {
        throw new Exception("Not intended for instantiation");
    }

    // This method now replaced by ImageMetadataReader
    // public static void main(String[] args) throws MetadataException,
    // IOException
    // {
    // Metadata metadata = null;
    // try {
    // metadata = JpegMetadataReader.readMetadata(new File(args[0]));
    // } catch (Exception e) {
    // e.printStackTrace(System.err);
    // System.exit(1);
    // }
    //
    // // iterate over the exif data and print to System.out
    // Iterator directories = metadata.getDirectoryIterator();
    // while (directories.hasNext()) {
    // Directory directory = (Directory)directories.next();
    // Iterator tags = directory.getTagIterator();
    // while (tags.hasNext()) {
    // Tag tag = (Tag)tags.next();
    // try {
    // System.out.println("[" + directory.getName() + "] " + tag.getTagName() +
    // " = " + tag.getDescription());
    // } catch (MetadataException e) {
    // System.err.println(e.getMessage());
    // System.err.println(tag.getDirectoryName() + " " + tag.getTagName() + "
    // (error)");
    // }
    // }
    // if (directory.hasErrors()) {
    // Iterator errors = directory.getErrors();
    // while (errors.hasNext()) {
    // System.out.println("ERROR: " + errors.next());
    // }
    // }
    // }
    //
    // if (args.length>1 && args[1].trim().equals("/thumb"))
    // {
    // ExifDirectory directory =
    // (ExifDirectory)metadata.getDirectory(ExifDirectory.class);
    // if (directory.containsThumbnail())
    // {
    // System.out.println("Writing thumbnail...");
    // directory.writeThumbnail(args[0].trim() + ".thumb.jpg");
    // }
    // else
    // {
    // System.out.println("No thumbnail data exists in this image");
    // }
    // }
    // }
}
