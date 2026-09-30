package sage;

import java.io.Closeable;
import java.io.IOException;

/**
 * One bounded transformation session for bytes produced by SageTV's DVD VM.
 *
 * <p>The provider changes only the media representation sent to a MiniClient.
 * SageTV remains responsible for DVD navigation, title/cell state, stream
 * selection, menus, and lifecycle. Implementations must unblock pending reads
 * and release every child process or native resource from {@link #close()}.</p>
 */
public interface DVDStreamTransform extends Closeable
{
  void write(byte[] data, int offset, int length) throws IOException;

  /** Returns one output chunk, or {@code null} when no chunk is ready. */
  byte[] pollOutput(long timeoutMillis) throws IOException;

  /** True only after output has ended and all queued output has been consumed. */
  boolean isOutputEnded();

  /** Closes input so a finite DVD VM segment can drain delayed output. */
  void closeInput();

  void close();
}
