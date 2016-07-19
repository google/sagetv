package sage.nio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class LocalFileChannel extends FileChannel implements SageFileChannel
{
  private String localFilename;
  private FileChannel fileChannel;
  private final boolean readonly;

  public LocalFileChannel(File file, boolean readonly) throws IOException
  {
    this(file.getPath(), readonly);
  }

  public LocalFileChannel(String name, boolean readonly) throws IOException
  {
    this.readonly = readonly;
    this.localFilename = name;
    open();
  }

  private void open() throws IOException
  {
    if (localFilename == null)
      return;

    if (readonly)
      fileChannel = FileChannel.open(Paths.get(localFilename), StandardOpenOption.READ);
    else
      fileChannel = FileChannel.open(Paths.get(localFilename), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);

    fileChannel.position(0);
  }

  @Override
  public void openFile(File file) throws IOException
  {
    openFile(file.getPath());
  }

  @Override
  public void openFile(String name) throws IOException
  {
    try
    {
      if (fileChannel != null)
        fileChannel.close();
    } catch (Exception e) {}
    // Change the file name after closing the file channel.
    localFilename = name;
    open();
  }

  ByteBuffer singleByte;
  @Override
  public int readUnsignedByte() throws IOException
  {
    if (singleByte == null)
    {
      singleByte = ByteBuffer.allocate(1);
    }
    singleByte.clear();
    int bytes = read(singleByte);
    singleByte.flip();

    if (bytes == -1)
      throw new java.io.EOFException();

    return singleByte.get() & 0xFF;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    return fileChannel.read(dst);
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException
  {
    return fileChannel.read(dsts, offset, length);
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    return fileChannel.write(src);
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException
  {
    return fileChannel.write(srcs, offset, length);
  }

  @Override
  public long position()
  {
    try
    {
      return fileChannel.position();
    }
    catch (IOException e)
    {
      System.out.println("Error: unable to get file channel position");
      e.printStackTrace(System.out);
    }

    return 0;
  }

  @Override
  public FileChannel position(long newPosition) throws IOException
  {
    fileChannel.position(newPosition);
    return this;
  }

  @Override
  public long skip(long n) throws IOException
  {
    if (n <= 0)
      return 0;

    long oldOffset = fileChannel.position();
    long newOffset = Math.min(oldOffset + n, size());
    fileChannel.position(newOffset);

    return newOffset - oldOffset;
  }

  @Override
  public long size() throws IOException
  {
    return fileChannel.size();
  }

  @Override
  public FileChannel truncate(long size) throws IOException
  {
    fileChannel.truncate(size);
    return this;
  }

  @Override
  public void force(boolean metaData) throws IOException
  {
    fileChannel.force(metaData);
  }

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) throws IOException
  {
    return fileChannel.transferTo(position, count, target);
  }

  @Override
  public long transferTo(long count, WritableByteChannel target) throws IOException
  {
    long position = fileChannel.position();
    long returnValue = fileChannel.transferTo(position, count, target);
    fileChannel.position(position + returnValue);
    return returnValue;
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException
  {
    return fileChannel.transferFrom(src, position, count);
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long count) throws IOException
  {
    long position = fileChannel.position();
    long returnValue = fileChannel.transferFrom(src, position, count);
    fileChannel.position(position + returnValue);
    return returnValue;
  }

  @Override
  public boolean isActiveFile()
  {
    // TODO: Can we look this up somewhere?
    return false;
  }

  @Override
  public boolean isReadOnly()
  {
    return readonly;
  }

  @Override
  public int read(ByteBuffer dst, long position) throws IOException
  {
    return fileChannel.read(dst, position);
  }

  @Override
  public int write(ByteBuffer src, long position) throws IOException
  {
    return fileChannel.write(src, position);
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException
  {
    return fileChannel.map(mode, position, size);
  }

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException
  {
    return fileChannel.lock(position, size, shared);
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException
  {
    return fileChannel.tryLock(position, size, shared);
  }

  @Override
  protected void implCloseChannel() throws IOException
  {
    fileChannel.close();
  }
}