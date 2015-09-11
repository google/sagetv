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
package sage.media.sub;

import sage.media.sub.DTVCCWindow.Rectangle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 *
 * @author Narflex
 */
public class CCSubtitleHandler extends SubtitleHandler
{
  public static final int TOP_FIELD_CC12 = 0;
  public static final int BOTTOM_FIELD_CC34 = 1;
  public static final int DTVCC_PACKET_DATA_FIELD = 2;
  public static final int DTVCC_PACKET_START_FIELD = 3;

  public static final int CC_ROWS = 15;
  public static final int CC_COLS = 32;
  public static final int CC_HD_COLS = 42; // 708 Captions, 16:9 screen

  public static final int ROLL_UP_CAPTIONS = 1;
  public static final int POP_ON_CAPTIONS = 2;
  public static final int PAINT_ON_CAPTIONS = 3;

  public static final char FORMAT_MASK = 0x8000;
  public static final char UNDERLINE_MASK = 0x0001;
  public static final char ITALICS_MASK = 0x0002;
  public static final char FLASH_MASK = 0x0004;
  public static final char WHITE_MASK = 0x0100;
  public static final char GREEN_MASK = 0x0200;
  public static final char BLUE_MASK = 0x0400;
  public static final char CYAN_MASK = 0x0800;
  public static final char RED_MASK = 0x1000;
  public static final char YELLOW_MASK = 0x2000;
  public static final char MAGENTA_MASK = 0x4000;
  public static final char COLOR_MASK = 0x7F00;

  public static final char TRANSPARENT_SPACE = '\u00ff';
  public static final char TRANSPARENT_SPACE_708 = '\uFFFF'; // reserved

  static char translation708G2table[] = new char[96]; // 16x6 G2 table
  public static final long defaultEmpty708Format = CellFormat.setPen(CellFormat.setForeground(
      CellFormat.setBackground(0, (byte) 0, DTVCCOpacity.TRANSPARENT), (byte) 0,
      DTVCCOpacity.TRANSPARENT), DTVCCFontType.DEFAULT, DTVCCSize.STANDARD,
      DTVCCOffset.NORMALSCRIPT);

  /** Creates a new instance of CCSubtitleHandler */
  public CCSubtitleHandler()
  {
    super(null);
    subLangs = new String[] { "CC1", "CC2", "CC3", "CC4", "DTVCC1", "DTVCC2", "DTVCC3", "DTVCC4", "DTVCC5", "DTVCC6" };

    // We have an extra column at the beginning which stores the formatting code for that row
    display = new char[CC_ROWS][CC_HD_COLS + 1];
    memory = new char[CC_ROWS][CC_HD_COLS + 1];
    displayFormat = new long[CC_ROWS][CC_HD_COLS + 1];
    clearFormat(displayFormat);

    currLang = "CC1"; // select by default so we buffer it

    debugCC = sage.Sage.getBoolean("cc_debug", false);

    // G2 Tablet as of CEA-708 (?); offset 0x20 into G2 table
    for(int i =0; i < translation708G2table.length; i++) translation708G2table[i] = '_';
    translation708G2table[0x0] = TRANSPARENT_SPACE_708; // UNICODE Reserved (but we can use it internally)
    translation708G2table[0x1] = TRANSPARENT_SPACE_708; // UNICODE reserved.  NBTS, but we don't break.
    translation708G2table[0x5] = '\u2026'; // HORIZONTAL ELLIPSIS (U+2026)
    translation708G2table[0xa] = '\u0160'; // LATIN CAPITAL LETTER S WITH CARON (U+0160)
    translation708G2table[0xc] = '\u0152'; // LATIN CAPITAL LIGATURE OE (U+0152)
    translation708G2table[0x10] = '\u2588'; // Unicode Character 'FULL BLOCK' (U+2588)
    translation708G2table[0x11] = '\u2018'; // LEFT SINGLE QUOTATION MARK (U+2018)
    translation708G2table[0x12] = '\u2019'; // RIGHT SINGLE QUOTATION MARK (U+2019)
    translation708G2table[0x13] = '\u201C'; // LEFT DOUBLE QUOTATION MARK (U+201C)
    translation708G2table[0x14] = '\u201D'; // RIGHT DOUBLE QUOTATION MARK (U+201D)
    translation708G2table[0x15] = '\u2022'; // BULLET (U+2022)
    translation708G2table[0x19] = '\u2122'; // TRADE MARK SIGN (U+2122)
    translation708G2table[0x1a] = '\u0161'; // LATIN SMALL LETTER S WITH CARON (U+0161)
    translation708G2table[0x1c] = '\u0153'; // LATIN SMALL LIGATURE OE (U+0153)
    translation708G2table[0x1d] = '\u2120'; // SERVICE MARK (U+2120)
    translation708G2table[0x1f] = '\u0178'; // LATIN CAPITAL LETTER Y WITH DIAERESIS (U+0178)
    translation708G2table[0x56] = '\u215B'; // VULGAR FRACTION ONE EIGHTH (U+215B)
    translation708G2table[0x57] = '\u212C'; // VULGAR FRACTION THREE EIGHTHS (U+212C)
    translation708G2table[0x58] = '\u212D'; // VULGAR FRACTION FIVE EIGHTHS (U+212D)
    translation708G2table[0x59] = '\u212E'; // VULGAR FRACTION SEVEN EIGHTHS (U+212E)
    translation708G2table[0x5a] = '\u2502'; // BOX DRAWINGS LIGHT VERTICLE (U+2502)
    translation708G2table[0x5b] = '\u2510'; // BOX DRAWINGS LIGHT DOWN AND LEFT (U+2510)
    translation708G2table[0x5c] = '\u2514'; // BOX DRAWINGS LIGHT UP AND RIGHT (U+2514)
    translation708G2table[0x5d] = '\u2500'; // BOX DRAWINGS LIGHT HORIZONTAL (U+2500)
    translation708G2table[0x5e] = '\u2518'; // BOX DRAWINGS LIGHT UP AND LEFT (U+2518)
    translation708G2table[0x5f] = '\u250C'; // BOX DRAWINGS LIGHT DOWN AND RIGHT (U+250C)
  }

  @Override
  public void loadSubtitlesFromFiles(sage.MediaFile sourceFile)
  {
    throw new UnsupportedOperationException("CCSubtitleHandler cannot load subtitles from external files!");
  }

  // Text area is 32x15 chars on the screen; monospaced font

  // CC data is done differently because we receive the events in real-time (or close enough to it). So when we receive an update
  // in this method that requires us to redraw the text; we return true and then give out the new subtitle text next
  @Override
  protected synchronized boolean insertEntryForPostedInfo(long time, long dur, byte[] rawData)
  {
    int numPackets = rawData.length / 8;
    int selectedChannel = 1;
    int selectedDataStream = TOP_FIELD_CC12;
    boolean dtvCaptions = false;
    StringBuffer sb = new StringBuffer();

    if (currLang != null)
    {
      // For now, use 608 captions even if 708 are chosen
      if (currLang.startsWith("CC"))
      {
        if (currLang.endsWith("1"))
        {
          selectedChannel = 1;
          selectedDataStream = TOP_FIELD_CC12;
        }
        else if (currLang.endsWith("2"))
        {
          selectedChannel = 2;
          selectedDataStream = TOP_FIELD_CC12;
        }
        else if (currLang.endsWith("3"))
        {
          selectedChannel = 1;
          selectedDataStream = BOTTOM_FIELD_CC34;
        }
        else if (currLang.endsWith("4"))
        {
          selectedChannel = 2;
          selectedDataStream = BOTTOM_FIELD_CC34;
        }
      }
      else if (currLang.startsWith("DTVCC"))
      {
        // NOTE: DTV CC support is not implemented yet
        dtvCaptions = true;
        try
        {
          dtvccSelectedChannel = selectedChannel = Integer.parseInt(currLang.substring(currLang.length() - 1));
        }
        catch (NumberFormatException nfe)
        {
          if (sage.Sage.DBG) System.out.println("ERROR: Could not parse DTVCC service number from lang: " + currLang + " error:" + nfe);
        }
      }
    }
    boolean rebuildText = false;
    char[][] buffer = (captionMode == POP_ON_CAPTIONS) ? memory : display;
    for (int i = 0; i < numPackets; i++)
    {
      int offset = i*8;
      byte dataType = rawData[offset + 4];
      if (debugCC) System.out.println("Got CC data packet of type:" + dataType + " targetType=" + selectedDataStream + " dtv=" + dtvCaptions +
          " selectedChannel=" + selectedChannel + " currLang=" + currLang);
      // We have to parse the top level members of the DTVCC data stream to filter out and render
      // the specified service (dtvccSelectedChannel)
      if(dataType == DTVCC_PACKET_DATA_FIELD || dataType == DTVCC_PACKET_START_FIELD) {
        if(debugDTVCC && os != null) {
          /* Allow for the recording of the entire DTVCC stream for test, etc. */
          synchronized (os) {
            try {
              os.write(rawData, offset+4, 4);
            } catch (IOException e) {
              // TODO(codefu): Auto-generated catch block
              e.printStackTrace();
            }
          }
        }
        if(dtvCaptions && isEnabled()) {
          parse708Datastream(rawData[offset + 4], rawData[offset + 5], rawData[offset + 6], rawData[offset + 7]);
        }
        // always continue because this is 708 data and should not be seen below.
        continue;
      } else if(dtvCaptions) {
        // ignore 608 data when processing 708
        continue;
      }
      if (dataType != selectedDataStream)
      {
        // Selected caption channel does not match this data we just received
        continue;
      }
      long currPts = ((rawData[offset] & 0xFF) << 24) | ((rawData[offset + 1] & 0xFF) << 16) | ((rawData[offset + 2] & 0xFF) << 8) | (rawData[offset + 3] & 0xFF);

      byte b1 = rawData[offset + 5];
      byte b2 = rawData[offset + 6];
      int c1 = b1 & 0xFF;
      int c2 = b2 & 0xFF;
      // Check parity - it should be odd
      int parity = (c1 & 0x01) + ((c1 >> 1) & 0x01) + ((c1 >> 2) & 0x01) + ((c1 >> 3) & 0x01) + ((c1 >> 4) & 0x01) + ((c1 >> 5) & 0x01) +
          ((c1 >> 6) & 0x01) + ((c1 >> 7) & 0x01);
      if ((2 * (parity / 2)) == parity)
      {
        if (debugCC) System.out.println("CC PARITY check failed-1!!!");
        lastc1 = lastc2 = -1;
        continue;
      }
      parity = (c2 & 0x01) + ((c2 >> 1) & 0x01) + ((c2 >> 2) & 0x01) + ((c2 >> 3) & 0x01) + ((c2 >> 4) & 0x01) + ((c2 >> 5) & 0x01) +
          ((c2 >> 6) & 0x01) + ((c2 >> 7) & 0x01);
      if ((2 * (parity / 2)) == parity)
      {
        if (debugCC) System.out.println("CC PARITY check failed-2!!!");
        lastc1 = lastc2 = -1;
        continue;
      }

      c1 = c1 & 0x7F;
      c2 = c2 & 0x7F;
      if (c1 < 0x20 && c1 >= 0x10) // Control character; check channel here
      {
        if (lastc1 == c1 && lastc2 == c2)
        {
          // Redundant control code information
          if (debugCC) System.out.println("Control code redundancy");
          lastc1 = lastc2 = -1;
          continue;
        }
        int newChannel = ((c1 & 0x08) == 0x08) ? 2 : 1;
        if (debugCC && newChannel != activeChannel)
          System.out.println("CC CHANNEL switched to " + newChannel);
        activeChannel = newChannel;
      }
      else
      {
        // Reset control code tracker since we have different data...unless this is empty data, then don't clear it
        // normally we wouldn't need to deal with that..however, for VOD we put extra 0x80 bytes in there to fill the entire
        // time span
        if ((rawData[offset + 5] & 0xFF) != 0x80 || (rawData[offset + 6] & 0xFF) != 0x80)
          lastc1 = lastc2 = -1;
      }
      String packLang = activeChannel == 1 ? "CC1" : "CC2";
      if (selectedChannel != activeChannel)
      {
        if (debugCC) System.out.println("Skip CC data; wrong channel");
        continue;
      }

      if(debugCC) {
        sb.append(String.format(
            "(byte)0x%02X, (byte)0x%02X, (byte)0x%02X, (byte)0x%02X, (byte)0x%02X, (byte)0x%02X, (byte)0x%02X, (byte)0x%02X, ",
            rawData[offset], rawData[offset + 1], rawData[offset + 2], rawData[offset + 3],
            rawData[offset + 4], rawData[offset + 5], rawData[offset + 6], rawData[offset + 7]));
      }

      if (debugCC && ((rawData[offset + 5] & 0xFF) != 0x80 || (rawData[offset + 6] & 0xFF) != 0x80))
        System.out.println("Got CC data of 0x" + Integer.toString(c1, 16) + " 0x" + Integer.toString(c2, 16) + " lang=" + packLang + " ptsMsec=" + currPts/45 + " text=" + textCache);
      if (c1 >= 0x20)
      {
        if (captionMode != 0)
        {
          if (captionMode != POP_ON_CAPTIONS)
            rebuildText = true;
          buffer[cursorY][cursorX] = charmap[c1 - 0x20];
          cursorX = Math.min(cursorX + 1, CC_COLS);
          if (c2 >= 0x20) // If there is a second char in this data
          {
            buffer[cursorY][cursorX] = charmap[c2 - 0x20];
            cursorX = Math.min(cursorX + 1, CC_COLS);
          }
          clearedDisplay = false;
        }
      }
      else if (c1 < 0x10)
      {
        // Skip the first char because it's invalid; but still check the second one
        if (c1 == 0)
        {
          if (captionMode != 0 && c2 >= 0x20)
          {
            if (captionMode != POP_ON_CAPTIONS)
              rebuildText = true;
            buffer[cursorY][cursorX] = charmap[c2 - 0x20];
            cursorX = Math.min(cursorX + 1, CC_COLS);
            clearedDisplay = false;
          }
        }
        else // XDS data, ignore it
        {
          if (debugCC) System.out.println("XDS data...ignore it");
        }
      }
      else
      {
        // This is a control code pair that we'll execute
        // Set values to check redundancy later
        lastc1 = c1;
        lastc2 = c2;

        // Now remove the channel bit from the control code
        c1 = c1 & (~0x08);
        if (c1 == 0x11 && c2 >= 0x20 && c2 < 0x30) // formating
        {
          // TODO
          // For c2 the following applies:
          // Bitmask of 0x01 implies underlined
          // 0x20 - White
          // 0x22 - Green
          // 0x24 - Blue
          // 0x26 - Cyan
          // 0x28 - Red
          // 0x2A - Yellow
          // 0x2C - Magenta
          // 0x2E - Italics

          char formatValue = FORMAT_MASK;
          if ((c2 & 0x01) == 0x01)
            formatValue = (char)((formatValue | UNDERLINE_MASK) & 0xFFFF);
          if ((c2 & 0x2E) == 0x20)
            formatValue = (char)((formatValue | WHITE_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x22)
            formatValue = (char)((formatValue | GREEN_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x24)
            formatValue = (char)((formatValue | BLUE_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x26)
            formatValue = (char)((formatValue | CYAN_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x28)
            formatValue = (char)((formatValue | RED_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x2A)
            formatValue = (char)((formatValue | YELLOW_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x2C)
            formatValue = (char)((formatValue | MAGENTA_MASK) & 0xFFFF);
          else if ((c2 & 0x2E) == 0x2E)
            formatValue = (char)((formatValue | ITALICS_MASK) & 0xFFFF);
          // These are mid-row codes and do cause the cursor to advance one position
          buffer[cursorY][cursorX] = formatValue;
          cursorX = Math.min(cursorX + 1, CC_COLS);
          clearedDisplay = false;
          if (debugCC) System.out.println("Received mid-row formatting code");
        }
        else if (c1 == 0x11 && c2 >= 0x30 && c2 < 0x40) // special characters
        {
          if (captionMode != POP_ON_CAPTIONS)
            rebuildText = true;
          buffer[cursorY][cursorX] = charmap2[c2 - 0x30];
          cursorX = Math.min(cursorX + 1, CC_COLS);
          clearedDisplay = false;
        }
        else if (c1 == 0x12 && c2 >= 0x20 && c2 < 0x40) // extended characters
        {
          // Extended chars have an implicit backspace
          if (cursorX > 1)
            cursorX--;
          if (captionMode != POP_ON_CAPTIONS)
            rebuildText = true;
          buffer[cursorY][cursorX] = charmap3[c2 - 0x20];
          cursorX = Math.min(cursorX + 1, CC_COLS);
          clearedDisplay = false;
        }
        else if (c1 == 0x13 && c2 >= 0x20 && c2 < 0x40) // more extended characters
        {
          // Extended chars have an implicit backspace
          if (cursorX > 1)
            cursorX--;
          if (captionMode != POP_ON_CAPTIONS)
            rebuildText = true;
          buffer[cursorY][cursorX] = charmap4[c2 - 0x20];
          cursorX = Math.min(cursorX + 1, CC_COLS);
          clearedDisplay = false;
        }
        else if (((c1 == 0x14 && selectedDataStream == TOP_FIELD_CC12) ||
            (c1 == 0x15 && selectedDataStream == BOTTOM_FIELD_CC34)) && (c2 >= 0x20 && c2 < 0x30))
        {
          if (c2 == 0x20) // Resume Caption Loading
          {
            captionMode = POP_ON_CAPTIONS;
            buffer = memory;
            if (debugCC) System.out.println("POP-ON CAPTIONS Resume loading captions now");
          }
          else if (c2 == 0x21) // Backspace
          {
            if (cursorX > 1)
            {
              if (debugCC) System.out.println("CC backspace");
              cursorX--;
              buffer[cursorY][cursorX] = 0;
            }
          }
          // 0x22 & 0x23 are reserved
          else if (c2 == 0x24) // Delete to End of Row
          {
            if (debugCC) System.out.println("CC delete to end of row");
            for (int x = cursorX; x <= CC_HD_COLS; x++)
              buffer[cursorY][x] = 0;
          }
          else if (c2 >= 0x25 && c2 <= 0x27) // Roll up Captions - 2-4 Rows
          {
            if (captionMode != ROLL_UP_CAPTIONS)
            {
              rollUpBase = CC_ROWS - 1;
              rebuildText = true;
              // Turning on roll-up captions clears any pop-on or paint-on captions
              clearMemory(display);
              clearMemory(memory);
            }
            captionMode = ROLL_UP_CAPTIONS;
            buffer = display;
            int oldRollUpRows = rollUpRows;
            rollUpRows = c2 - 0x23;
            // 608Spec: Favor row-depth and adjust base.
            rollUpBase = Math.max(rollUpRows-1, rollUpBase);
            // If we're reducing the number of rows we should erase extra rows immediately
            if (oldRollUpRows > rollUpRows)
            {
              for (int y = Math.max(0, rollUpBase - oldRollUpRows + 1); y < rollUpBase - rollUpRows + 1 && y < CC_ROWS; y++)
                for (int x = 0; x <= CC_HD_COLS; x++)
                  buffer[y][x] = 0;
            }
            clearedDisplay = false;
            if (debugCC) System.out.println("CC ROLL UP MODE for " + rollUpRows + " rows");
          }
          else if (c2 == 0x28) // Flash on
          {
          }
          else if (c2 == 0x29) // Resume Direct Captioning
          {
            if (captionMode != PAINT_ON_CAPTIONS)
            {
              if (debugCC) System.out.println("CC PAINT-ON CAPTION MODE");
              captionMode = PAINT_ON_CAPTIONS;
              rebuildText = true;
              buffer = display;
              clearedDisplay = false;
            }
          }
          else if (c2 == 0x2A) // Text restart - Used for text modes, which we don't support
          {
            if (debugCC) System.out.println("CC RESTART");
          }
          else if (c2 == 0x2B) // Resume text display - Used for text modes, which we don't support
          {
            if (debugCC) System.out.println("CC RESUME TEXT");
          }
          else if (c2 == 0x2C) // Erase Displayed Memory
          {
            if (debugCC) System.out.println("CC Erase Displayed Memory");
            // Clear what we're currently displaying
            updateNeeded = true;
            textCache = "";
            clearMemory(display);
            clearedDisplay = true;
          }
          else if (c2 == 0x2D) // Carriage Return
          {
            if (captionMode == ROLL_UP_CAPTIONS)
            {
              if (debugCC) System.out.println("CC carriage return rollUpBase=" + rollUpBase + " rollUpRows=" + rollUpRows);
              // NOTE: NARFLEX - WE WANT TO ANIMATE THIS LATER
              // Roll up the bottom rows
              for (int roller = 1; roller < rollUpRows; roller++)
                for (int x = 0; x <= CC_HD_COLS && (rollUpBase - rollUpRows + roller) >= 0 && (rollUpBase - rollUpRows + roller) < CC_ROWS; x++)
                  buffer[rollUpBase - rollUpRows + roller][x] = buffer[rollUpBase - rollUpRows + roller + 1][x];

              // Clear the new bottom row
              for (int x = 0; x <= CC_HD_COLS; x++)
                buffer[rollUpBase][x] = 0;

              // It would be better if this went with the data it was intended to animate.
              addRollupRect(new Rectangle(rollUpBase - rollUpRows, rollUpBase, 0, CC_HD_COLS));
              rebuildText = true;
              cursorX = 1;
              clearedDisplay = false;
            }
            // no effect for pop-on or paint-on captions
          }
          else if (c2 == 0x2E) // Erase Non-displayed [buffer] Memory
          {
            clearMemory(memory);
            if (debugCC) System.out.println("CLEAR the the caption buffer memory");
          }
          else if (c2 == 0x2f) // End Of Caption (Flip Memories)
          {
            if (debugCC) System.out.println("DONE loading the captions; show them now!");
            captionMode = POP_ON_CAPTIONS;
            updateNeeded = true;
            char[][] temp = display;
            display = memory;
            memory = temp;
            textCache = createDisplayText(display);
            buffer = memory;
            clearedDisplay = false;
          }
        }
        else if (c1 == 0x17 && (c2 == 0x21 || c2 == 0x22 || c2 == 0x23)) // Tab Over
        {
          // Tabs over x space
          if (c2 == 0x21)
            cursorX++;
          else if (c2 == 0x22)
            cursorX += 2;
          else //if (c2 == 0x23)
            cursorX += 3;
          cursorX = Math.min(cursorX, CC_COLS);
        }
        else if ((c1 == 0x11 || c1 == 0x12 || c1 == 0x15 || c1 == 0x16 ||
            c1 == 0x17 || c1 == 0x10 || c1 == 0x13 || c1 == 0x14) && c2 >= 0x40) // curpos, color, underline
        {
          // This repositions the caption cursor on the screen and also sets the text color and whether or not its underlining
          if (c1 == 0x11)
            cursorY = 0;
          else if (c1 == 0x12)
            cursorY = 2;
          else if (c1 == 0x15)
            cursorY = 4;
          else if (c1 == 0x16)
            cursorY = 6;
          else if (c1 == 0x17)
            cursorY = 8;
          else if (c1 == 0x10)
            cursorY = 10;
          else if (c1 == 0x13)
            cursorY = 11;
          else if (c1 == 0x14)
            cursorY = 13;

          if ((c2 & 0x20) == 0x20)
            cursorY++;

          if (captionMode == ROLL_UP_CAPTIONS)
          {
            if (rollUpBase != cursorY) {
              // 608Spec: Favor row-depth and adjust base.
              cursorY = Math.max(rollUpRows - 1, cursorY);
            }
            if (rollUpBase != cursorY)
            {
              if (debugCC) System.out.println("ROLLUP-CC: Base position: " + rollUpBase + " different from cursorY: " + cursorY);
              // If the base position for the roll-ups change then we should reposition them immediately
              if (rollUpBase > cursorY)
              {
                // We're moving them up so we need to copy from the top line first and then clear the remaining lines
                // Do the copy
                for (int y = Math.max(0, rollUpBase - rollUpRows + 1); y <= rollUpBase; y++)
                  if (y - (rollUpBase - cursorY) >= 0)
                  {
                    for (int x = 0; x <= CC_HD_COLS; x++)
                      display[y - (rollUpBase - cursorY)][x] = display[y][x];
                  }
                // Now clear what was moved
                for (int y = cursorY + 1; y <= rollUpBase; y++)
                  for (int x = 0; x <= CC_HD_COLS; x++)
                    display[y][x] = 0;
              }
              else
              {
                // We're moving them down, so we start with the bottom line and go up and copy from there and then clear any remaining lines
                // Do the copy
                for (int y = rollUpBase; y >= 0 && y >= rollUpBase - rollUpRows + 1; y--)
                  if (y + (cursorY - rollUpBase) < CC_ROWS)
                  {
                    for (int x = 0; x <= CC_HD_COLS; x++)
                      display[y + (cursorY - rollUpBase)][x] = display[y][x];
                  }
                // Now clear what was moved
                for (int y = cursorY - rollUpRows; y > rollUpBase - rollUpRows && y >= 0; y--)
                  for (int x = 0; x <= CC_HD_COLS; x++)
                    display[y][x] = 0;
              }
            }
            rollUpBase = cursorY;
          }
          char formatValue = FORMAT_MASK;
          if ((c2 & 0x01) == 0x01)
            formatValue = (char)((formatValue | UNDERLINE_MASK) & 0xFFFF);
          // These masks apply colors to the text
          // 0x40 - White
          // 0x42 - Green
          // 0x44 - Blue
          // 0x46 - Cyan
          // 0x48 - Red
          // 0x4A - Yellow
          // 0x4C - Magenta
          // 0x4E - White Italics
          if ((c2 >= 0x50 && c2 < 0x60) || (c2 >= 0x70 && c2 < 0x80))
          {
            // Indentation w/ implied white color
            int indentAmount = 2 * (c2 & 0xE);
            cursorX = indentAmount + 1;
            formatValue = (char)((formatValue | WHITE_MASK) & 0xFFFF);
          }
          else
          {
            cursorX = 1;
            if ((c2 & 0x4E) == 0x40)
              formatValue = (char)((formatValue | WHITE_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x42)
              formatValue = (char)((formatValue | GREEN_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x44)
              formatValue = (char)((formatValue | BLUE_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x46)
              formatValue = (char)((formatValue | CYAN_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x48)
              formatValue = (char)((formatValue | RED_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x4A)
              formatValue = (char)((formatValue | YELLOW_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x4C)
              formatValue = (char)((formatValue | MAGENTA_MASK) & 0xFFFF);
            else if ((c2 & 0x4E) == 0x4E)
              formatValue = (char)((formatValue | ITALICS_MASK | WHITE_MASK) & 0xFFFF);
          }
          // Set the formatting code for this row now
          buffer[cursorY][0] = formatValue;
          if (debugCC) System.out.println("CC repositioned cursor at " + cursorX + "," + cursorY);
        }
      }
    }
    if (rebuildText)
    {
      updateNeeded = true;
      textCache = createDisplayText(display);
    }
    if (debugCC && sb.length() > 0) {
      System.out.println("Time:" + time + " Dur:" + dur + " Data: \r\n{" + sb.toString() + "},");
    }
    return updateNeeded;
  }

  public synchronized char[][] getCCDisplayData(long mediaTime)
  {
    if (canLog(NOISY)) System.out.println("getCCDisplayData()");
    if (!updateNeeded && mediaTime - lastUpdateTime >= 15000)
    {
      if (!clearedDisplay)
      {
        if (canLog(NOISY)) System.out.println("CLEARING the CC display due to no updates for 15 seconds");
        clearedDisplay = true;
        clearMemory(display);
        clearFormat(displayFormat);
      }
    }
    if (updateNeeded)
      lastUpdateTime = mediaTime;
    updateNeeded = false;
    return display;
  }

  protected synchronized char[][] getCCDisplayData() {
    return display;
  }

  protected synchronized long[][] get708CellFormat()	{
    return displayFormat;
  }

  /**
   * Retrieve the format information to paint the CC display data. See {@link CellFormat}
   * @param mediaTime
   * @return Two dimensional array of values ([y][x]) interpreted through CellFormat
   */
  public synchronized long[][] get708CellFormat(long mediaTime)	{
    if (canLog(NOISY)) System.out.println("get708CellFormat()");
    return displayFormat;
  }

  private StringBuffer tempSB = new StringBuffer(CC_ROWS * (CC_COLS + 3));
  private String createDisplayText(char[][] buffer)
  {
    if (!debugCC) return "";
    StringBuffer rv = tempSB;
    rv.setLength(0);
    boolean anyText = false;
    for (int y = 0; y < CC_ROWS; y++)
    {
      // Put a blank space at the beginning of every row for the leftmost opaque space
      rv.append(' ');
      for (int x = 0; x <= CC_HD_COLS; x++)
      {
        if (buffer[y][x] != 0 && ((buffer[y][x] & FORMAT_MASK) != FORMAT_MASK))
        {
          //					if (!charsThisRow)
          {
            //						for (int z = 0; z < x; z++)
            //							rv.append(' '); // insert leading spaces
            //						charsThisRow = true;
          }
          rv.append(buffer[y][x]);
          anyText = true;
        }
        else
          rv.append(' ');
      }
      //			if (charsThisRow)
      rv.append("\n");
    }
    if (anyText)
      return rv.toString();
    else
      return "";
  }

  static char[] charmap =
    {
    ' ','!','"','#','$','%','&','\'','(',')', '\u00e1','+',',','-','.','/',
    '0','1','2','3','4','5','6','7','8','9',':',';','<','=','>','?',
    '@','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O',
    'P','Q','R','S','T','U','V','W','X','Y','Z','[','\u00e9',']','\u00ed','\u00f3',
    '\u00fa','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o',
    'p','q','r','s','t','u','v','w','x','y','z','\u00e7','\u00f7', 0x00d1, 0x00f1, 0x2588//'?'
    };
  static char[] charmap2 =
    {
    '\u00ae', // (r)egistered
    '\u00b0', // degree
    '\u00bd', // 1/2
    '\u00bf', // inverted question mark
    '\u2122', // trade mark
    '\u00a2', // cent
    '\u00a3', // pound
    '\u266a', // music
    '\u00e0', // a`
    TRANSPARENT_SPACE, // transparent space, handled specially in renderer
    '\u00e8', // e`
    '\u00e2', // a^
    '\u00ea', // e^
    '\u00ee', // i^
    '\u00f4', // o^
    '\u00fb', // u^
    };

  static char[] charmap3 =
    {
    '\u00c1', // A'
    '\u00c9', // E'
    '\u00d3', // O'
    '\u00da', // U'
    '\u00dc', // U:
    '\u00fc', // u:
    '\u2018', // `
    '\u00a1', // inverted !
    '\u002a', // *
    '\u2019', // '
    '\u002d', // -
    '\u00a9', // (c)opyright
    '\u2120', // SM
    '\u00b7', // . (dot in the middle)
    '\u201c', // inverted "
    '\u201d', // "

    '\u00c0', // A`
    '\u00c2', // A^
    '\u00c7', // C,
    '\u00c8', // E`
    '\u00ca', // E^
    '\u00cb', // E:
    '\u00eb', // e:
    '\u00ce', // I^
    '\u00cf', // I:
    '\u00ef', // i:
    '\u00d4', // O^
    '\u00d9', // U`
    '\u00f9', // u`
    '\u00db', // U^
    '\u00ab', // <<
    '\u00bb', // >>
    };

  static char[] charmap4 =
    {
    '\u00c3', // A~
    '\u00e3', // a~
    '\u00cd', // I'
    '\u00cc', // I`
    '\u00ec', // i`
    '\u00d2', // O`
    '\u00f2', // o`
    '\u00d5', // O~
    '\u00f5', // o~
    '\u007b', // {
    '\u007d', // }
    '\\', // \
    '\u005e', // ^
    '\u005f', // _
    '\u00a6', // |
    '\u007e', // ~

    '\u00c4', // A:
    '\u00e4', // a:
    '\u00d6', // O:
    '\u00f6', // o:
    '\u00df', // B (ss in german)
    '\u00a5', // Y=
    '\u00a4', // ox
    '\u007c', // |
    '\u00c5', // Ao
    '\u00e5', // ao
    '\u00d8', // O/
    '\u00f8', // o/
    '\u250c', // |-
    '\u2510', // -|
    '\u2514', // |_
    '\u2518', // _|
    };

  @Override
  public boolean mpControlled()
  {
    return false;
  }

  @Override
  public String getSubtitleText(long currMediaTime, boolean willDisplay)
  {
    if (willDisplay)
      updateNeeded = false;
    return textCache;
  }

  @Override
  public long getTimeTillUpdate(long currMediaTime)
  {
    if (updateNeeded)
      return 0;
    else if (clearedDisplay || lastUpdateTime == 0)
      return sage.Sage.MILLIS_PER_WEEK;
    else
      return Math.max((lastUpdateTime + 15000) - currMediaTime, 0);
  }

  @Override
  public synchronized void setCurrLanguage(String x)
  {
    if (x == null) return;
    if (!x.equals(currLang))
    {
      currLang = x;
      reset708();
      textCache = "";
      captionMode = 0;
      updateNeeded = true;
    }
  }

  private void clearMemory(char[][] buffer)
  {
    for (int y = 0; y < CC_ROWS; y++)
      for (int x = 0; x <= CC_HD_COLS; x++)
        buffer[y][x] = 0;
  }

  /**
   * Clear the 708 format data with transparent foreground and background and resetting the pen.
   */
  public static boolean clearFormat(long[][] buffer) {
    int rows = buffer.length;
    boolean isDirty = false;
    for(int y = 0; y < rows; y++) {
      int cols = buffer[y].length;
      for(int x = 0; x < cols; x++) {
        isDirty = isDirty || buffer[y][x] != defaultEmpty708Format;
        buffer[y][x] = defaultEmpty708Format;
      }
    }
    return isDirty;
  }

  public static char applyMidRowToFormat(char currFormat, char newFormat)
  {
    // Pre
    char rv = currFormat;
    // Underlining can be in all codes, so take that as it is.
    if ((newFormat & UNDERLINE_MASK) == UNDERLINE_MASK)
      rv = (char)((rv | UNDERLINE_MASK) & 0xFFFF);
    else
      rv = (char)((rv & ~UNDERLINE_MASK) & 0xFFFF);

    // Color mid row codes turn off italics; but not the other way around
    if ((newFormat & COLOR_MASK) != 0)
    {
      rv = (char)((rv & ~COLOR_MASK) & 0xFFFF); // turn off colors first
      rv = (char)((rv | (newFormat & COLOR_MASK)) & 0xFFFF); // apply new color
      rv = (char)((rv & ~ITALICS_MASK) & 0xFFFF); // remove italics
    }
    // Turn on italics if its in the new format
    if ((newFormat & ITALICS_MASK) == ITALICS_MASK)
      rv = (char)((rv | ITALICS_MASK) & 0xFFFF);

    /*
		// The flash code is always additive; but any other codes disable it
		if ((newFormat & FLASH_MASK) == FLASH_MASK)
			rv = (char)((rv | FLASH_MASK) & 0xFFFF);
		else
			rv = (char)((rv & ~FLASH_MASK) & 0xFFFF);
     */
    return rv;
  }

  @Override
  public RollupAnimation getAnimationObject() {
    RollupAnimation toReturn = null;
    synchronized (rollAnimation) {
      if(rollAnimation.getCount() > 0) {
        toReturn = rollAnimation;
        rollAnimation = new RollupAnimation();
      }
    }
    return toReturn;
  }

  void addRollupRect(int windowID, DTVCCDirection direction) {
    synchronized (rollAnimation) {
      if(canLog(NOISY)) System.out.println("708 Rollup: Window:" + windowID + " Direction: " + direction);
      rollAnimation.add(windowID, direction);
    }
  }

  void addRollupRect(Rectangle rect) {
    synchronized (rollAnimation) {
      if(canLog(NOISY)) System.out.println("608 Rollup: " + rect);
      rollAnimation.add(rect);
    }
  }

  boolean canLog(int level) {
    return debugDTVCC && level >= debugParseLevel;
  }

  void parseWindowDefinition(
      byte command, byte param1, byte param2, byte param3, byte param4, byte param5, byte param6) {
    int id = command & 0x7;
    int penStyle = param6 & 0x7;
    int windowStyle = (param6 >> 3) & 0x7;

    int priority = param1 & 0x7;
    boolean coloumLock = (param1 & 0x8) == 0x8;
    boolean rowLock = (param1 & 0x10) == 0x10;
    boolean visible = (param1 & 0x20) == 0x20;
    int anchorVertical = param2 & 0x7F;
    boolean relativePosition = (param2 & 0x80) == 0x80;
    int anchorHorizontal = param3 & 0xFF;
    int rowCount = (param4 & 0xF) + 1;
    int anchorPoint = (param4 >> 4) & 0xF;
    int columnCount = (param5 & 0x3F) + 1;

    if (windows[id] == null) {
      // create a new window
      // Styles are 1..7, we're using an array 0..6. Zero is default to Style1 on create
      if (penStyle != 0) penStyle--;
      if (windowStyle != 0) windowStyle--;
      windows[id] = new DTVCCWindow(
          this, id, windowStyle, penStyle, anchorHorizontal, anchorVertical, anchorPoint,
          relativePosition, rowLock, coloumLock, rowCount, columnCount, visible, priority);
    } else {
      // update the window (if needed)
      DTVCCWindow window = windows[id];
      if (penStyle != 0) {
        penStyle--; // see note above about style indices
        // zero means leave alone; silly since a set pen will have to be called if we just tuned
        // and didn't catch the initial setting.]
        window.defaultPen = penStyle;
        window.setPen(DTVCCPen.defaultPens[penStyle]);
      }
      if (windowStyle != 0) {
        windowStyle--; // see note above about style indices
        if (window.attributes != DTVCCWindowAttributes.defaultWindows[windowStyle]) {
          // TODO(codefu) : Verify if the specification requires this window to be re-drawn
          // More than likely it'll forbid this situation.
          window.defaultWindow = windowStyle;
          window.setAttributes(DTVCCWindowAttributes.defaultWindows[windowStyle]);
        }
      }
      // Test for re-size
      if (rowCount != window.getRows() || columnCount != window.getCols()) {
        window.resize(rowCount, columnCount);
      }

      // Test for move
      if (anchorHorizontal != window.getAnchorX() || anchorVertical != window.getAnchorY()
          || anchorPoint != window.getAnchorPoint()
          || relativePosition != window.isRelativeAnchor()) {
        boolean visibility = window.isVisible();
        if(visibility) {
          window.setVisibility(false);
        }
        window.setAnchorX(anchorHorizontal);
        window.setAnchorY(anchorVertical);
        window.setAnchorPoint(anchorPoint);
        window.setRelativeAnchor(relativePosition);
        if(visibility) {
          window.setVisibility(true);
        }
      }
      window.possiblyFlush();
    }
    if (canLog(INFORM))
      System.out.println("Window definition parsed for id:" + id + " " + windows[id].toString());

    // TODO(codefu): Clarify if only new window defines or all defines set the the currentWindow.
    currentWindow = id;
  }

  void parseWindowAttributes(byte command, byte param1, byte param2, byte param3, byte param4) {
    if(currentWindow == -1 || windows[currentWindow] == null) return;
    byte fillColor = (byte) (param1 & 0x3F);
    DTVCCOpacity fillOpacity = DTVCCOpacity.from((param1 >> 6) & 0x3);
    byte borderColor = (byte) (param2 & 0x3f);
    DTVCCBorderType dTVCCBorderType = DTVCCBorderType.from(((param2 >> 6) & 0x3) | ((param3 >> 5) & 0x4));
    DTVCCJustify dTVCCJustify = DTVCCJustify.from(param3 & 0x3);
    DTVCCDirection scroll = DTVCCDirection.from((param3 >> 2) & 0x3);
    DTVCCDirection print = DTVCCDirection.from((param3 >> 4) & 0x3);
    boolean wrap = (param3 & 0x40) == 0x40;
    DTVCCEffect dTVCCEffect = DTVCCEffect.from(param4 & 0x3);
    DTVCCDirection effectDirection = DTVCCDirection.from((param4 >> 2)& 0x3);
    int effectSpeedMS = ((param4 >> 4) & 0xF) & 500;

    DTVCCWindow window = windows[currentWindow];
    DTVCCWindowAttributes attr = window.getAttributes();
    if (attr.wordWrap != wrap || attr.borderColor != borderColor || attr.borderType != dTVCCBorderType
        || attr.effect != dTVCCEffect || attr.effectDirection != effectDirection
        || attr.effectTimeMS != effectSpeedMS || attr.fillColor != fillColor
        || attr.justification != dTVCCJustify || attr.opacity != fillOpacity || attr.print != print
        || attr.scroll != scroll) {
      // dirty window attributes.
      window.setAttributes(new DTVCCWindowAttributes(
          dTVCCJustify, print, scroll, wrap, dTVCCEffect, effectDirection, effectSpeedMS, fillColor,
          fillOpacity, dTVCCBorderType, borderColor));
      if (canLog(INFORM)) System.out.println("Window(" + currentWindow +") new attributes:" + window.attributes);
    }
  }

  void parsePenAttributes(byte command, byte param1, byte param2) {
    if(currentWindow == -1 || windows[currentWindow] == null) return;
    DTVCCSize dTVCCSize = DTVCCSize.from(param1 & 0x3);
    DTVCCOffset dTVCCOffset = DTVCCOffset.from((param1 >> 2) & 0x3);
    DTVCCTextType type = DTVCCTextType.from((param1 >> 4) & 0xF);
    DTVCCFontType font = DTVCCFontType.from(param2 & 0x7);
    DTVCCBorderType edgeType = DTVCCBorderType.from((param2 >> 3) & 0x7);
    boolean underline = (param2 & 0x40) == 0x40;
    boolean italics = (param2 & 0x80) == 0x80;

    DTVCCWindow window = windows[currentWindow];
    DTVCCPenStyle pen = window.getPenAttributes();
    if (pen.italic != italics || pen.underline != underline || pen.edge != edgeType
        || pen.font != font || pen.dTVCCOffset != dTVCCOffset || pen.penSize != dTVCCSize
        || pen.textInfo != type) {
      // Note; we're allocating a new pen style here because the old style may be referenced by buffers.
      window.setPenAttributes(new DTVCCPenStyle(dTVCCSize, font, dTVCCOffset, italics, underline, edgeType));
      if (canLog(INFORM)) System.out.println("Window(" + currentWindow +") new pen attributes:" + window.penAttributes);
    }
  }

  void parsePenColor(byte command, byte param1, byte param2, byte param3) {
    if(currentWindow == -1 || windows[currentWindow] == null) return;
    byte fgColor = (byte) (param1 & 0x3F);
    byte bgColor = (byte) (param2 & 0x3F);
    byte edColor = (byte) (param3 & 0x3F);
    DTVCCOpacity fgOpacity = DTVCCOpacity.from((param1 >> 6) & 0x3);
    DTVCCOpacity bgOpacity = DTVCCOpacity.from((param2 >> 6) & 0x3);
    DTVCCWindow window = windows[currentWindow];
    DTVCCPen pen = window.getPen();
    if (pen.backgroundColor != bgColor || pen.backgroundOpacity != bgOpacity
        || pen.edgeColor != edColor || pen.foregroundColor != fgColor
        || pen.foregroundOpacity != fgOpacity) {
      // Note; we're allocating a new pen here because the old style may be referenced by buffers.
      window.setPen(new DTVCCPen(fgColor, bgColor, edColor, fgOpacity, bgOpacity));
      if (canLog(INFORM)) System.out.println("Window(" + currentWindow +") new pen color:" + window.pen);
    }
  }

  void parsePenLocation(byte command, byte param1, byte param2) {
    if(currentWindow == -1 || windows[currentWindow] == null) return;
    byte row = (byte) (param1 & 0xF);
    byte col = (byte) (param2 & 0x3F);
    DTVCCWindow window = windows[currentWindow];

    // 708-B, SetPenLocation quirks
    window.penY = (window.attributes.justification != DTVCCJustify.LEFT && (
        window.attributes.print == DTVCCDirection.TOP_TO_BOTTOM
        || window.attributes.print == DTVCCDirection.BOTTOM_TO_TOP)) ? window.penY : row;
    window.penX = (window.attributes.justification != DTVCCJustify.LEFT && (
        window.attributes.print == DTVCCDirection.RIGHT_TO_LEFT
        || window.attributes.print == DTVCCDirection.LEFT_TO_RIGHT)) ? window.penX : col;
    if (canLog(INFORM)) System.out.println("Window(" + currentWindow + ") new pen location x/y (" + window.penX + ", "
        + window.penY + ")");
  }

  // 708 current packet assembly; 127 is the max size
  ByteArrayOutputStream captionChannelPacket = new ByteArrayOutputStream(127);
  int lastSequenceNumber = -1; // 0-3
  boolean droppingPackets = true; // always start up in this mode as we're 99% of the time middle-starting

  DataOutputStream os;

  public void setOutputStream(DataOutputStream os) {
    if(this.os != null) {
      synchronized (this.os) {
        this.os = os;
      }
    } else {
      this.os = os;
    }
  }

  void parse708Datastream(byte field, byte data1, byte data2, byte flags) {
    if (canLog(VERY_NOISY)) {
      System.out.println(String.format(
          "CCData: { %02x %02x %02x %02x }; // DropLast: %b, lastSequenceNumber:%d", field, data1,
          data2, flags, droppingPackets, lastSequenceNumber));
    }

    if (field < 1 || field > 3) return;
    if (droppingPackets && field == 2) {
      return;
    }

    if ((flags & 0x1) == 1) {
      if (field == 3) {
        droppingPackets = false;
        if(captionChannelPacket.size() > 0) {
          if (canLog(NOISY)) System.out.println("DTVCC CaptionChannelPacket "
              + captionChannelPacket.size() + " bytes to parse into service blocks");
          parse708DTVCCPacket(captionChannelPacket.toByteArray());
          captionChannelPacket.reset();
        }
      } else if(captionChannelPacket.size() == 0) {
        // Note(codefu) there are some streams with data starting in field two; drop packets.
        if(canLog(NOISY)) System.out.println("Packet data starting with field 2; invalid data");
        droppingPackets = true;
        return;
      }
      captionChannelPacket.write(data1);
      captionChannelPacket.write(data2);
    } else if ((flags & 0x1) == 0 && field > 1 && captionChannelPacket.size() > 0) {
      if (canLog(NOISY)) System.out.println("DTVCC CaptionChannelPacket " + captionChannelPacket.size()
          + " bytes to parse into service blocks");
      parse708DTVCCPacket(captionChannelPacket.toByteArray());
      captionChannelPacket.reset();
    }
  }

  private void reset708() {
    clearMemory(display);
    clearMemory(memory); // we don't really use this, meh.
    for(int i = 0; i < windows.length; i++) {
      if(windows[i] != null) {
        windows[i].reset();
      }
      windows[i] = null;
    }
    clearFormat(displayFormat);
    currentWindow = -1;
  }

  void parse708DTVCCPacket(byte[] packet) {
    ByteArrayInputStream input = new ByteArrayInputStream(packet);

    if (canLog(VERY_NOISY)) {
      StringBuffer sb = new StringBuffer();
      for(int i = 0; i < packet.length; i++) {
        sb.append(String.format("%02x ", packet[i]));
        sb.append(" ");
      }
      System.out.println("Packet: { " + sb.toString() + " }");
    }

    int currByte = input.read();
    int sequenceNumber = (currByte >> 6) & 0x3;

    if (lastSequenceNumber != -1 && ((lastSequenceNumber + 1) & 0x3) != sequenceNumber) {
      droppingPackets = true;
      if (canLog(WARN)) System.out.println("Sequence numbers are out of sync; should RESET. Last:"
          + lastSequenceNumber + " Parsed (mod 4):" + sequenceNumber);
      lastSequenceNumber = -1;
      reset708();
      return;
    }
    lastSequenceNumber = sequenceNumber;

    currByte &= 0x3F;
    // NOTE(codefu): 708B 5.0, Fig4 states the 0 == 127, otherwise follow (n*2) - 1
    int packetSize = currByte == 0 ? 127 : (currByte<<1)-1;
    if (packetSize + 1 > packet.length) {
      if (canLog(WARN)) System.out.println("Packet size (" + packetSize + ") > phsyical buffer("
          + packet.length + "); tossing entire packet");
      // TODO(codefu): should we reset?
      return;
    }

    // Service Block Packets; Each one has a 1 or 2 byte header and up to 31 bytes of data payload.
    while(input.available() > 0) {
      currByte = input.read();
      int blockSize = currByte & 0x1F;
      int service = (currByte >> 5) & 0x7;
      // check for extended service id
      if (service == 7) {
        currByte = input.read();
        // ignore top 2 bits; but they should be null.
        service = currByte & 0x3F;
        // CEA-CEB10A-708 5.3.2 These values are illegal, skip the service block.
        if (service < 7) {
          if (canLog(WARN)) System.out.println("Service Block ("+service+") extended_service < 7; skipping " + blockSize);
          input.skip(blockSize);
          continue;
        }
      }
      // According to CEA-CEB10A-708; 0 is reserved and we should skip the bytes.
      if (service == 0 || service != dtvccSelectedChannel) {
        if (canLog(VERY_NOISY)) System.out.println("Service Block ("+service+") is not ours; skipping " + blockSize);
        input.skip(blockSize);
        continue;
      }
      byte[] serviceBlock = new byte[blockSize];
      try {
        int read = input.read(serviceBlock);
        if (read != blockSize) {
          // CEA-CEB10A-708 5.3.4 Err on the side of not displaying bad text; skip remainder of packet.
          if (canLog(WARN)) System.out.println("Service Block ("+service+"); unable to read " + blockSize);
          return;
        }
      } catch (IOException ignored) {}
      if (canLog(NOISY)) System.out.println("Service Block ("+service+"); read " + blockSize);
      parse708ServiceBlockData(serviceBlock);
    }
  }

  // TODO(codefu): Keep a list of priority active windows for drawing on the screen, or we just
  // do a search.  Look up if priorities are non-unique (zorder) or can have overlaps.  Fill
  // the display buffer in bottom-up fashion and we have support for overlapping windows!
  DTVCCWindow[] windows = new DTVCCWindow[8];
  int currentWindow = -1;

  // We're only ever processing one service ID, when that gets delayed, we skip through the CC data
  // looking for RESET or DELAY_CANCEL, else we wait for delayEnd and start processing again.
  boolean underDelay = false;
  long delayEnd;

  DTVCCWindow[] getWindows() {
    return windows;
  }

  void parse708ServiceBlockData(byte[] block) {
    int i;
    int value;
    if(underDelay) {
      long now = System.currentTimeMillis();
      underDelay = (now >= delayEnd);
      if(!underDelay && canLog(INFORM)) System.out.println("DTVCC decoder delay period ended");
    }
    // we must still scan the stream for reset and delay cancel.
    for (i = 0; i < block.length;) {
      value = block[i++] & 0xFF;
      if (value != 0x10) {
        /*
         * Normal (non-extended) tables, C0, C1, G0, G1 Some variable width commands apply
         */
        if (value < 0x20) {
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].possiblyFlush();
          }
          if (canLog(NOISY)) System.out.println(
              "[" + i + "] C0: ASCII Control Code : 0x" + Integer.toHexString(value));
          if (value == 0x0D) {
            if(currentWindow > -1 && windows[currentWindow] != null) {
              windows[currentWindow].carriagReturn();
            }
          } else if (value == 0x0E) {
            if(currentWindow > -1 && windows[currentWindow] != null) {
              windows[currentWindow].horizontalCarriagReturn();
            }
          } else if (value == 0x0C) {
            if(currentWindow > -1 && windows[currentWindow] != null) {
              windows[currentWindow].formFeed();
            }
          } else if (value > 0x10 && value < 0x18) {
            if (i >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C0 command; ran over block boundary");
              break;
            }
            value = block[i++] & 0xFF;
            if (canLog(NOISY)) System.out.println(
                "[" + i + "] C0: Skip one (unknown) byte: " + Integer.toHexString(value));
          } else if (value > 0x18) {
            if (i + 1 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C0 command; ran over block boundary");
              break;
            }
            value = ((block[i++] & 0xFF) << 8) | (block[i++] & 0xFF);
            System.out.println("[" + i + "] C0: Skip two ("
                + (value == 0x18 ? "chinese" : "unknown") + ") bytes: "
                + Integer.toHexString(value));
          }
        } else if (value < 0x7F) {
          if (canLog(NOISY)) System.out.println("[" + i + "] Window(" + currentWindow +") G0: Modified ANSI X3.4 = " + (char) value);
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].writeCharacter((char) value);
          }
        } else if (value < 0x80) { /* debug */
          if (canLog(NOISY)) System.out.println("[" + i + "] Window(" + currentWindow +") G0: Modified ANSI X3.4 = \u266A");
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].writeCharacter((char) 0x266A);
          }
        } else if (value < 0xA0) {
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].possiblyFlush();
          }
          if (canLog(NOISY)) System.out.println("[" + i + "] C1: Caption Command: " + Integer.toHexString(value));
          if (value < 0x88) {
            // nothing; set window
            currentWindow = value - 0x80;
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: SetCurrentWindow" + currentWindow);
          } else if (value < 0x8D) {
            if (i >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            int windowBitmap = block[i++] & 0xFF;
            if (canLog(NOISY)) {
              switch (value) {
                case 0x88:
                  System.out.println(
                      "[" + i + "] C1: ClearWindows: 0x" + Integer.toHexString(windowBitmap));
                  break;
                case 0x89:
                  System.out.println(
                      "[" + i + "] C1: DisplayWindows: 0x" + Integer.toHexString(windowBitmap));
                  break;
                case 0x8A:
                  System.out.println(
                      "[" + i + "] C1: HideWindows: 0x" + Integer.toHexString(windowBitmap));
                  break;
                case 0x8B:
                  System.out.println(
                      "[" + i + "] C1: ToggleWindows: 0x" + Integer.toHexString(windowBitmap));
                  break;
                case 0x8C:
                  System.out.println(
                      "[" + i + "] C1: DeleteWindows: 0x" + Integer.toHexString(windowBitmap));
                  break;
              }
            }
            for(int x = 0; x < 8; x++) {
              if( ((1<<x) & windowBitmap) == (1<<x) && windows[x] != null) {
                switch (value) {
                  case 0x88:
                    // wipe with fill color, set pen location to 0,0
                    windows[x].penX = windows[x].penY = 0;
                    windows[x].clear();
                    break;
                  case 0x89:
                    windows[x].setVisibility(true);
                    break;
                  case 0x8A:
                    windows[x].setVisibility(false);
                    break;
                  case 0x8B:
                    windows[x].setVisibility(!windows[x].visible);
                    break;
                  case 0x8C:
                    windows[x].reset();
                    windows[x] = null;
                }
              }
            }
          } else if (value == 0x8D) {
            // TODO(codefu): Need to flip a switch to ignore commands and not actually do anything.
            if (i >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            underDelay = true;
            value = block[i++] & 0xFF;
            delayEnd = (value * 100) + System.currentTimeMillis();
            if (canLog(INFORM)) System.out.println("[" + i + "] C1: Delay for ms: " + (value * 100)
                + " seconds; ignore all commands after this except DelayCancel and Reset");
          } else if (value == 0x8E) {
            // TODO(codefu): Need to re-enable parsing of windows and commands.
            underDelay = false;
            delayEnd = 0;
            if (canLog(INFORM)) System.out.println("[" + i + "] C1: DelayCancel; resume interpreting commands");
          } else if (value == 0x8F) {
            if (canLog(NOISY)) System.out.println(
                "[" + i + "] C1: Reset;  delete all windows, cancel delay, clear buffers.");
            underDelay = false;
            delayEnd = 0;
            reset708();
          } else if (value == 0x90) {
            if (i + 1 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: SetPenAttributes:"
                + String.format("0x%02X, 0x%02X", block[i], block[i + 1]));
            parsePenAttributes((byte)value, block[i], block[i+1]);
            i += 2;
          } else if (value == 0x91) {
            if (i + 2 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: SetPenColor:"
                + String.format("0x%02X, 0x%02X, 0x%02X", block[i], block[i + 1], block[i + 2]));
            parsePenColor((byte) value, block[i], block[i + 1], block[i + 2]);
            i += 3;
          } else if (value == 0x92) {
            if (i + 1 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: SetPenLocation:"
                + String.format("0x%02X, 0x%02X", block[i], block[i + 1]));
            parsePenLocation((byte) value, block[i], block[i + 1]);
            i += 2;
          } else if (value < 0x97) {
            // CEA-CEB10A-08 Implementation 6.2.2: 0x93 to 0x96 No additional bytes to be skipped.
            // These commands are recommended to be permanently unused.
            if (canLog(WARN)) System.out.println("[" + i
                + "] C1: UNDEFINED COMMAND; SHOULD NEVER BE USED!: " + Integer.toHexString(value));
          } else if (value == 0x97) {
            if (i + 3 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: SetWindowAttributes:" + String.format(
                "0x%02X, 0x%02X, 0x%02X, 0x%02X", block[i], block[i + 1], block[i + 2],
                block[i + 3]));
            parseWindowAttributes((byte) value, block[i], block[i + 1], block[i + 2], block[i + 3]);
            i += 4;
          } else if (value < 0xA0) {
            if (i + 5 >= block.length) {
              if (canLog(WARN)) System.out.println("[" + i + "] While parsing C1 command; ran over block boundary");
              break;
            }
            if (canLog(NOISY)) System.out.println("[" + i + "] C1: DefineWindow" + (value - 0x98)
                + ": " + String.format(
                    "0x%02x, 0x%02x, 0x%02x, 0x%02x, 0x%02x, 0x%02x, ", block[i], block[i + 1],
                    block[i + 2], block[i + 3], block[i + 4], block[i + 5]));
            parseWindowDefinition((byte) value, block[i], block[i + 1], block[i + 2], block[i + 3],
                block[i + 4], block[i + 5]);
            i += 6;
          }
        } else {
          if (canLog(NOISY)) System.out.println("[" + i + "] Window(" + currentWindow +") G1: ISO 8859-1 Latin 1: " + Integer.toHexString(value));
          if(currentWindow > -1 && windows[currentWindow] != null) {
            // Everything should be good here for ISO/IEC 8859-1 Latin 1
            windows[currentWindow].writeCharacter((char) value);
          }
        }
      } else {
        /*
         * 0x10 == EXT1: Break out to C2, C3, G2, and G3 tablets
         */
        if (i >= block.length) {
          if (canLog(WARN)) System.out.println("[" + i + "] While parsing EXT1 set; ran over block boundary");
          break;
        }
        value = block[i++] & 0xFF;
        if (value < 0x20) {
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].possiblyFlush();
          }
          // C2 Table; there is nothing defined here, but we must skip bytes
          if (canLog(NOISY)) System.out.println(
              "[" + i + "] C2: table(" + Integer.toHexString(value) + "); skipping extra bytes");
          if (value < 0x08) {
            continue; // no bytes required to skip
          } else if (value < 0x10) {
            i++;
          } else if (value < 0x18) {
            i += 2;
          } else {
            i += 3;
          }
          continue;
        } else if (value < 0x80) {
          // G2
          if (canLog(NOISY)) System.out.println("[" + i + "] Window(" + currentWindow
              + ") G2: table(" + Integer.toHexString(value) + "); Translation: "
              + Integer.toHexString(translation708G2table[value - 0x20]));
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].writeCharacter(translation708G2table[value - 0x20]);
          }
        } else if (value < 0x9F) {
          if(currentWindow > -1 && windows[currentWindow] != null) {
            windows[currentWindow].possiblyFlush();
          }
          if (canLog(NOISY)) System.out.println("[" + i + "] C3: table(" + Integer.toHexString(value) + "); ");
          // No commands as of CEA-708A; but skip some bytes if in the range [0x80..0x8F]
          // [0x90..0x9F] are variable length commands with a 1 byte header.
          if (value < 0x88) {
            i += 4;
          } else if (value < 0x90) {
            i += 5;
          } else {
            if (i >= block.length) {
              if (canLog(WARN)) System.out.println(
                  "[" + i + "] While parsing C3 variable length command; ran over block boundary");
              break;
            }
            value = block[i++] & 0xFF;
            int bytes = value & 0x1F;
            if (canLog(NOISY)) System.out.println("[" + i + "] C3: variable command(" + Integer.toHexString(value)
                + "); skipping: " + bytes);
            i += bytes;
          }
        } else {
          // G3 Future characters and icons, [0x10A0..0x10FF]
          // [CC] icon at 0xa0 -> There is no unicode symbol for this, so.....
          if (canLog(NOISY)) System.out.println(
              "[" + i + "] Window(" + currentWindow +") G3: character command(" + Integer.toHexString(value) + ")");
          // All unsupported G3 characters replaced with "_"
          if(currentWindow > -1 && windows[currentWindow] != null) {
            if (value == 0xa0) {
              // Squared CC.  NOTE: Our fonts have this "squared" with a box around it.
              windows[currentWindow].writeCharacter('\u33C4');
            } else {
             windows[currentWindow].writeCharacter('_');
            }
          }
        }
      }
    }
  }

  public boolean is708() {
    return currLang.startsWith("DTVCC");
  }


  // Returns true if the timing delay for the next subtitle update has changed (i.e. VF needs a kick)
  @Override
  public boolean postSubtitleInfo(long time, long dur, byte[] rawText, int flags)
  {
    if ((flags & FLUSH_SUBTITLE_QUEUE) == FLUSH_SUBTITLE_QUEUE) {
    }
    return super.postSubtitleInfo(time, dur, rawText, flags);
  }

  private RollupAnimation rollAnimation = new RollupAnimation();
  boolean updateNeeded; // If the text has changed, then this is true
  private String textCache = "";
  private int activeChannel = 1; // the CC1/CC2 channel the incoming data stream should be applied to
  private int lastc1; // for control code redundancy
  private int lastc2; // for control code redundancy
  private int dtvccSelectedChannel; // For DTVCC service 1-63

  // display is what we're showing on screen for roll-up or paint-on?
  private char[][] display;
  // memory is the buffered captions for pop-on style
  private char[][] memory;
  private long [][] displayFormat;

  // Current cursor position in the display grid
  private int cursorX;
  private int cursorY;

  private int captionMode;
  private int rollUpRows;
  private int rollUpBase;

  private long lastUpdateTime; // if there's no data for 15 seconds; we should clear the display
  private boolean clearedDisplay = true; // initially we are cleared

  private boolean debugCC;
  private boolean debugDTVCC = true;

  final static int VERY_NOISY = 0;
  final static int NOISY = 1;
  final static int INFORM = 2;
  final static int WARN = 3;
  int debugParseLevel = WARN;
}
