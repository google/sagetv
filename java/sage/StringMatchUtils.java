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


/**
 * @author nielm@google.com (Niel Markwick)
 *
 */
public final class StringMatchUtils {


  /**
   * Case-insensitive test to see if match string is a word or word sequence in
   * the source string, testing only at word boundaries. <br/>
   * a word boundary is defined as a non-alpha, non-numeric character as defined
   * by Character.isLetterOrDigit(ch)
   *
   * @param source - String to search
   * @param match - substring to find
   * @return true if match is found withing source
   */
  public static boolean wordMatchesIgnoreCase(String source, String match) {
    match = match.toLowerCase();
    return wordMatchesLowerCase(source, match);
  }

  /**
   * Test to see if match string is a word or word sequence in the source
   * string, testing only at word boundaries. <br/>
   * a word boundary is defined as a non-alpha, non-numeric character as defined
   * by Character.isLetterOrDigit(ch)
   *
   * @param source - String to search
   * @param match - substring to find - must be in lower case
   * @return true if match is found withing source
   */
  public static boolean wordMatchesLowerCase(String source, String match) {
    return wordMatches(true, source, match);
  }

  /**
   * Test to see if match string is a word or word sequence in the source
   * string, testing only at word boundaries, case-sensitive. <br/>
   * a word boundary is defined as a non-alpha, non-numeric character as defined
   * by Character.isLetterOrDigit(ch)
   *
   * @param source - String to search
   * @param match - substring to find
   * @return true if match is found withing source
   */
  public static boolean wordMatches(String source, String match) {
    return wordMatches(false, source, match);
  }


  private static boolean wordMatches(boolean lowerCase, String source, String match) {
    // if empty match string, everything matches
    if ( match.length()==0)
      return true;

    // if match starts with letter or digit, skip by word to get matches
    // -- faster
    if (Character.isLetterOrDigit(match.charAt(0))) {
      int index = 0;
      while (index < source.length()) {
        index = findNextWordStart(source, index);
        if (substringMatches(lowerCase, source, index, match)) {
          return true;
        }

        index = findNextWordEnd(source, index) + 1;
      }
      return false;
    } else {
      // match starts with a space or some punctuation, fallback to
      // String.indexOf function
      // as match itself forces a word boundary
      return source.toLowerCase().indexOf(match, 0) != -1;
    }
  }

  /**
   * case-insensitive test to see if match string is a word or word sequence in
   * the source string, testing only at word boundaries, case-sensitive. <br/>
   * a word boundary is defined as a non-alpha, non-numeric character as defined
   * by Character.isLetterOrDigit(ch)<br>
   * where unicode chars '\u2460' (circle1)-'\u2468' (circle9) and '\u24ea'
   * (circle0) are intended to be interpreted as any of the chars represented by
   * that numeric keypad key
   * <p>
   * Note that before this function is called, it is recommended to call
   * updateNteCharsFromProperties() to take into account any properties changes
   * to the NTE chars.
   *
   * @param source - String to search
   * @param match - substring to find - must be in lower case
   * @return true if match is found within source
   */
  public static boolean wordMatchesNte(String source, String match) {
    // if empty match string, everything matches
    if ( match.length()==0)
      return true;

    return findMatchingWordIndexNTE(source,match)>-1;
  }


  /**
   * case-insensitive search to find the index of the match string in
   * the source string, testing only at word boundaries. <br/>
   * a word boundary is defined as a non-alpha, non-numeric character as defined
   * by Character.isLetterOrDigit(ch)<br>
   * where unicode chars '\u2460' (circle1)-'\u2468' (circle9) and '\u24ea'
   * (circle0) are intended to be interpreted as any of the chars represented by
   * that numeric keypad key
   *
   * @param source - String to search
   * @param match - substring to find - must be in lower case
   * @return integer index if found, -1 if not found
   */
  public static int findMatchingWordIndexNTE(String source, String match){
    // if match starts with letter or digit, skip by word to get matches
    // faster
    char[] firstNteChar = getNteChars(match.charAt(0));
    if ((firstNteChar != null && Character.isLetter(firstNteChar[0]))
        || Character.isLetterOrDigit(match.charAt(0))) {
      int index = 0;
      while (index < source.length()) {
        index = findNextWordStart(source, index);
        if (substringMatchesNte(source, index, match)) {
          return index;
        }

        index = findNextWordEnd(source, index) + 1;
      }
      return -1;
    } else {
      // match starts with a space or some punctuation, cannot skip spaces,
      // use slower method of iterating thro each start char looking for a match
      for (int index = 0; index < (source.length() - match.length()); index++) {
        if (substringMatchesNte(source, index, match)) return index;
      }
      return -1;
    }
  }

  /**
   * Given a String and a start index, returns the next index where there is a
   * alpha or numeric character indicating the start of a word -- including the
   * current string position
   *
   */
  public static int findNextWordStart(String s, int startIndex) {
    int i = startIndex;
    for (; i < s.length(); i++) {
      char currChar = s.charAt(i);
      if (Character.isLetterOrDigit(currChar)) {
        return i;
      }
    }
    return i;
  }

  /**
   * Given a String and a start index, returns the next index where there is a
   * non-alpha non-numeric character indicating the end of a word
   */
  public static int findNextWordEnd(String s, int startIndex) {
    int i = startIndex;
    for (; i < s.length(); i++) {
      char currChar = s.charAt(i);
      if (!Character.isLetterOrDigit(currChar)) {
        return i;
      }
    }
    return i;
  }

  /**
   * Given a String and a start index, returns the next index where there is a
   * alpha or numeric character indicating the start of a word -- including the
   * current string position
   *
   */
  public static int findNextWordStartNTE(String s, int startIndex) {
    int i = startIndex;
    for (; i < s.length(); i++) {
      char currChar = s.charAt(i);
      if (Character.isLetterOrDigit(currChar) || getNteChars(currChar) != null) {
        return i;
      }
    }
    return i;
  }

  /**
   * Given a String and a start index, returns the next index where there is a
   * non-alpha non-numeric character indicating the end of a word
   */
  public static int findNextWordEndNTE(String s, int startIndex) {
    int i = startIndex;
    for (; i < s.length(); i++) {
      char currChar = s.charAt(i);
      if (!(Character.isLetterOrDigit(currChar) || (getNteChars(currChar) != null))) {
        return i;
      }
    }
    return i;
  }

  /**
   * utility function to check for a substring match, not intended for public
   * use.
   *
   *
   * @param source -- string to test
   * @param startIndex -- index to test from
   * @param match -- must be in lower case
   * @return true if match
   */
  static boolean substringMatches(boolean lowerCase, String source, int startIndex, String match) {
    // check we have enough chars left to match to
    if (startIndex + match.length() > source.length()) return false;

    for (int i = 0; i < match.length(); i++) {
      char s = source.charAt(startIndex + i);
      char m = match.charAt(i);
      if (s == m) continue;

      if (lowerCase && Character.toLowerCase(s) == m) continue;

      // no match, bail out
      return false;
    }

    // all chars match
    return true;
  }

  /**
   * utility function to check for a substring match, where unicode chars
   * '\u2460' (circle1)-'\u2468' (circle9) and '\u24ea' (circle0) are intended
   * to be interpreted as any of the chars represented by that numeric keypad
   * key <br>
   * not intended for public use.
   *
   *
   * @param source -- string to test
   * @param startIndex -- index to test from
   * @param match -- must be in lower case
   * @return true if match
   */
  public static boolean substringMatchesNte(String source, int startIndex, String match) {
    // check we have enough chars left to match to
    if (startIndex + match.length() > source.length()) return false;

    sourceStringLoop: for (int i = 0; i < match.length(); i++) {
      char s = source.charAt(startIndex + i);
      char m = match.charAt(i);
      char[] nteChars = getNteChars(m);


      if (nteChars != null) {
        // nteCharLoop:
        for (int j = 0; j < nteChars.length; j++) {
          if (s == nteChars[j]) continue sourceStringLoop;
          if (Character.toLowerCase(s) == nteChars[j]) continue sourceStringLoop;
        }
        // no match for any NTE chars, bail out
        return false;

      } else {
        if (s == m) continue sourceStringLoop;
        if (Character.toLowerCase(s) == m) continue sourceStringLoop;

        // no match, bail out
        return false;
      }
    }

    // all chars match
    return true;
  }

  public static boolean substringMatchesNteCase(String source, int startIndex, String match) {
    if (match.length() > source.length()) return false;
    sourceStringLoop: for (int i = 0; i < match.length(); i++) {
      char s = source.charAt(i);
      char m = match.charAt(i);
      char[] nteChars;
      switch (m) {
        case '\u24EA':
          nteChars = allNteChars[0];
          break;
        case '\u2460':
        case '\u2461':
        case '\u2462':
        case '\u2463':
        case '\u2464':
        case '\u2465':
        case '\u2466':
        case '\u2467':
        case '\u2468':
          nteChars = allNteChars[(m - '\u2460') + 1];
          break;
        default:
          if (s == m) continue;
          return false;
      }
      for(int j = 0; j < nteChars.length; j++) {
        if(nteChars[j] == s) continue sourceStringLoop;
      }
      return false;
    }
    return true; /* defaults to '*' at the end */
  }

  /**
   * utility function to check a substring for unicode chars
   * '\u2460' (circle1)-'\u2468' (circle9) and '\u24ea' (circle0), which are intended
   * to be interpreted as any of the chars represented by that numeric keypad
   * key <br>
   * not intended for public use.
   *
   *
   * @param source -- string to test
   * @param start -- index to test from
   * @param end -- index to stop
   * @return true if NTE characters detected
   */
  public static boolean substringContainsNte(String source, int start, int end) {
    end = (end > source.length()) ? source.length() : end;
    while (start < end) {
      char c = source.charAt(start++);
      if (c == '\u24EA') {
        // CIRCLED DIGIT ZERO
        return true;
      }
      if (c >= '\u2460' && c <= '\u2468') {
        // >= CIRCLED DIGIT ONE <= CIRCLED DIGIT NINE
        return true;
      }
    }
    return false;
  }

  public static boolean stringContainsNte(String source) {
    return substringContainsNte(source, 0, source.length());
  }

  /**
   * given a NTE key char 'c' (Circled Digits) returns the array of characters
   * mapped to that NTE key
   *
   * @param c
   * @return char[] - the chars matching that NTE key or null if none match
   */
  private static char[] getNteChars(char c) {
    if (c == '\u24EA') { // CIRCLED DIGIT ZERO
      return allNteChars[0];
    }
    if (c >= '\u2460' && c <= '\u2468')
      // >= CIRCLED DIGIT ONE <= CIRCLED DIGIT NINE
      return allNteChars[(c - '\u2460' + 1)];

    return null;
  }


  // for test purposes only - converts a text string into its
  // NTE key char representation
  public static String convertToNte(String string) {
    StringBuffer retval = new StringBuffer();
    string = string.toLowerCase();

    charLoop: for (int index = 0; index < string.length(); index++) {
      char c = string.charAt(index);
      for (int i = 0; i < allNteChars.length; i++) {
        char[] nteChars = allNteChars[i];
        for (int j = 0; j < nteChars.length; j++) {
          if (c == nteChars[j]) {
            if (i == 0)
              retval.append('\u24ea');
            else
              System.out.print((char) ('\u2460' + i - 1));
            continue charLoop;
          }
        }

      }
    }
    return retval.toString();
  }

  /**
   * converts a string of NTE characters (and normal characters) into their
   * default character representation - given by the first character in the
   * array
   *
   * @param nteString
   * @return converted String
   */
  public static String convertFromNte(String nteString) {
    StringBuffer retval = new StringBuffer();
    for (int index = 0; index < nteString.length(); index++) {
      char c = nteString.charAt(index);
      char[] nteChars = getNteChars(c);
      if (nteChars == null) {
        retval.append(c);
      } else {
        retval.append(nteChars[0]);
      }
    }
    return retval.toString();
  }

  /**
   * Update the cached NTE chars definitons from sage.properties
   */
  public static void updateNteCharsFromProperties() {
    String propertyRoot =
        "ui/numeric_text_input_" + Sage.get("ui/translation_language_code", "en") + "_";

    for (int i = 0; i < 10; i++) {
      String currNteChars = new String(allNteChars[i]);
      String charString = Sage.get(propertyRoot + i + "_lower", currNteChars);
      if (!charString.equals(currNteChars))
        // should be lower case anyway, but force it just in case...
        allNteChars[i] = charString.toLowerCase().toCharArray();
    }
  }

  /**
   * chars representing the NTE keys. These are the default values which are
   * replaced with values from the properties file. This array is kept as a
   * cache which is checked every NTE_CHARS_UPDATE_TIMEOUT to prevent constant
   * props lookup in performance-dependent code.
   *
   */
  static private char[][] allNteChars = new char[][] {" 0".toCharArray(),
    "1*?\"\\/'`.,:;@$&!-_()".toCharArray(), "abc2".toCharArray(), "def3".toCharArray(),
    "ghi4".toCharArray(), "jkl5".toCharArray(), "mno6".toCharArray(), "pqrs7".toCharArray(),
    "tuv8".toCharArray(), "wxyz9".toCharArray()};


  /**
   * Private constructor to prevent instantiation
   */
  private StringMatchUtils() {

  }
}
