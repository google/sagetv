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
package sage;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import java.io.IOException;

/**
 * Convert a token stream into its NTE form for faster NTE searching later.
 */
public final class SageNTEFilter extends TokenFilter {
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final PositionIncrementAttribute posIncAtt =
      addAttribute(PositionIncrementAttribute.class);
  private char[] buffer = null; // To keep multiple tokens per input token.
  private int buffLength;

  public SageNTEFilter(TokenStream input) {
    super(input);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (buffer != null) {
      buffer = convertInPlaceToNTE(buffer, 0, buffLength);
      posIncAtt.setPositionIncrement(0);
      // termAtt.copyBuffer(buffer, 0, buffer.length);
      // termAtt.setLength(buffer.length);
      buffer = null;
      return true;
    }

    if (!input.incrementToken()) {
      return false;
    }

    // termAtt has the token for us, we need to keep a copy of it for later transmutation,
    // then just return as normal.
    buffer = termAtt.buffer();
    buffLength = termAtt.length();
    return true;
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    buffer = null;
  }

  private char[] convertInPlaceToNTE(char[] buff, int offset, int length) {
    // BIG-NOTE(codefu): We're skipping " " being converted since that turns into a very ugly
    // polynomial solution. You should be using the whitespace tokenizer, in which case you'll only
    // encounter zero's here.
    for (int i = offset; i < length; i++) {
      switch (buff[i]) {
        case '0': // no space!
          buff[i] = '\u24EA';
          break;
        case 'a':
        case 'b':
        case 'c':
        case '2':
        case 'A':
        case 'B':
        case 'C':
          buff[i] = '\u2461';
          break;
        case 'd':
        case 'e':
        case 'f':
        case '3':
        case 'D':
        case 'E':
        case 'F':
          buff[i] = '\u2462';
          break;
        case 'g':
        case 'h':
        case 'i':
        case '4':
        case 'G':
        case 'H':
        case 'I':
          buff[i] = '\u2463';
          break;
        case 'j':
        case 'k':
        case 'l':
        case '5':
        case 'J':
        case 'K':
        case 'L':
          buff[i] = '\u2464';
          break;
        case 'm':
        case 'n':
        case 'o':
        case '6':
        case 'M':
        case 'N':
        case 'O':
          buff[i] = '\u2465';
          break;
        case 'p':
        case 'q':
        case 'r':
        case 's':
        case '7':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
          buff[i] = '\u2466';
          break;
        case 't':
        case 'u':
        case 'v':
        case '8':
        case 'T':
        case 'U':
        case 'V':
          buff[i] = '\u2467';
          break;
        case 'w':
        case 'x':
        case 'y':
        case 'z':
        case '9':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
          buff[i] = '\u2468';
          break;

        case '1':
        case '*':
        case '?':
        case '\\':
        case '"':
        case '/':
        case '\'':
        case '`':
        case '.':
        case ',':
        case ':':
        case ';':
        case '@':
        case '$':
        case '&':
        case '!':
        case '-':
        case '_':
        case '(':
        case ')':
          buff[i] = '\u2460';
          break;
        default:
          break; // do nothing
      }
    }
    return buff;
  }
}
