package sage;

import java.io.IOException;
import java.io.RandomAccessFile;

public class TestDataUtils
{
  /**
   * Compare to byte values and throw assert error if they do not match.
   *
   * @param offset The offset of this comparison within the file. This is for display purposes only.
   * @param expected The expected result.
   * @param actual The actual result to be compared against the expected result.
   */
  public static void verifyBytes(long offset, byte expected, byte actual)
  {
    assert expected == actual :
        "File offset " + offset + " contains " + (actual & 0xff) + ", expected " + (expected & 0xff);
  }

  /**
   * Compare two arrays with assert and clear the <code>actual</code> array.
   *
   * @param filePos The actual position in the file that this byte array starts at. If
   *                <code>pos</code> is not 0, this value should still align with <code>pos</code>
   *                at 0. Otherwise, the offset in the assert error will not be accurate.
   * @param pos The position in the array to start comparing.
   * @param len The number if bytes to compare starting from <code>pos</code>.
   * @param expected An array of expected values.
   * @param actual An array to compare the expected values against.
   */
  public static void verifyAndClear(long filePos, int pos, long len, byte[] expected, byte[] actual)
  {
    for (int i = pos; i < pos + len; i++)
    {
      verifyBytes(filePos + i, expected[i], actual[i]);
      actual[i] = 0;
    }
  }

  /**
   * Create file is it doesn't exist, then fill is looping over the provided byte array to the
   * provided size.
   *
   * @param filename The full path and name of the file to create.
   * @param data The byte array to use to fill the file. Starting at 0, the array will be written
   *             into the file. If the array length is smaller than the desired file size, it will
   *             append from 0 again in a loop until the file size is reached.
   * @param size The desired file size.
   * @throws IOException If there is an I/O related error.
   */
  public static void fillLocalFile(String filename, byte[] data, long size) throws IOException
  {
    RandomAccessFile raf = new RandomAccessFile(filename, "rw");
    raf.setLength(size);
    raf.seek(0);
    while (size > 0)
    {
      int length = (int)Math.min(size, (long)data.length);
      raf.write(data, 0, length);
      size -= length;
    }
    // If we don't do this sometimes the file lengths will be incorrect which will cause tests to
    // return a false failure.
    raf.getFD().sync();
    raf.close();
  }
}
