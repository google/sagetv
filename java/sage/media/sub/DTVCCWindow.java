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

public class DTVCCWindow {
  public static final int MAX_WINDOWS = 8;

  void setPriority(int priority) {
    this.priority = priority;
  }

  void setRowLock(boolean rowLock) {
    this.rowLock = rowLock;
  }

  void setColLock(boolean colLock) {
    this.colLock = colLock;
  }

  void setRelativeAnchor(boolean relativeAnchor) {
    this.relativeAnchor = relativeAnchor;
  }

  void setAnchorPoint(int anchorPoint) {
    this.anchorPoint = anchorPoint;
  }

  void setAnchorX(int anchorX) {
    this.anchorX = anchorX;
  }

  void setAnchorY(int anchorY) {
    this.anchorY = anchorY;
  }

  void setDefaultPen(int defaultPen) {
    this.defaultPen = defaultPen;
  }

  void setDefaultWindow(int defaultWindow) {
    this.defaultWindow = defaultWindow;
  }

  void setPenX(int penX) {
    this.penX = penX;
  }

  void setPenY(int penY) {
    this.penY = penY;
  }

  public int getPriority() {
    return priority;
  }

  public boolean isRowLock() {
    return rowLock;
  }

  public boolean isColLock() {
    return colLock;
  }

  public boolean isVisible() {
    return visible;
  }

  public boolean isRelativeAnchor() {
    return relativeAnchor;
  }

  public int getAnchorPoint() {
    return anchorPoint;
  }

  public int getAnchorX() {
    return anchorX;
  }

  public int getAnchorY() {
    return anchorY;
  }

  public int getDefaultPen() {
    return defaultPen;
  }

  public int getDefaultWindow() {
    return defaultWindow;
  }

  public int getPenX() {
    return penX;
  }

  public int getPenY() {
    return penY;
  }

  char[][] getBuffer() {
    return buffer;
  }

  long[][] getCellFormat() {
    return cellFormat;
  }

  long getCurrentFormatText() {
    return currentFormatText;
  }

  long getCurrentFormatFill() {
    return currentFormatFill;
  }

  CCSubtitleHandler getCcSubtitleHandler() {
    return ccSubtitleHandler;
  }

  public int getId() {
    return id;
  }

  boolean isDirty() {
    return dirty;
  }

  int priority; // 0-7; We only need to display 4 active windows, in priority order.

  boolean rowLock; // !lock: may display more rows if font size permits, SetPen can wander
  boolean colLock; // !lock: may display more columns if font size permits, SetPen can wander
  boolean visible; // default hidden.
  boolean relativeAnchor; // Anchor values are percentages

  // This is used to define the the point of the window that anchorX and anchorY refer to, which
  // will be used to calculate the upper left corner of the window.
  int anchorPoint; // upper left = 0, center, right; middle left, center, right; lower left, center right

  // These can be relative or fixed (based on the window aspect ratio).
  int anchorX; // [0,74]. If relative, 0-99
  int anchorY; // 16:9 [0,209], 4:3 [0,159]. If relative, 0-99

  int defaultPen; // Stores the default pen color & style used by this window
  int defaultWindow; // Stores the default window attributes used by this window

  DTVCCPen pen;	// If non-null, override default pen
  DTVCCPenStyle penAttributes; // If non-null, override default pen style
  DTVCCWindowAttributes attributes; // If non-null, override default window attributes
  int penX; // Normally 0-14
  int penY; // 16:9 [0-41], 4:3 [0-31]

  // Screen data!
  char [][] buffer;

  // Format for each cell. See See CellFormat
  long [][] cellFormat;

  long currentFormatText; // Compiled format for the current pen
  long currentFormatFill; // Compiled format for blank space (not " ",which is pen)

  private final CCSubtitleHandler ccSubtitleHandler; // where display and formats eventually go
  int id; // Our [0-7] id in the handler's array of windows.

  boolean dirty; // When characters are written to the window, they are accumulated.

  static class Rectangle {
    int top;
    int bottom;
    int left;
    int right;

    Rectangle() {
    }

    Rectangle(int top, int bottom, int left, int right) {
      this.top = top;
      this.left = left;
      this.bottom = bottom;
      this.right = right;
    }

    Rectangle(Rectangle other) {
      top = other.top;
      bottom = other.bottom;
      left = other.left;
      right = other.right;
    }

    boolean collides(Rectangle other) {
      if(other.top >= bottom) return false;
      if(other.bottom <= top) return false;
      if(other.left >= right) return false;
      if(other.right <= left) return false;
      return true;
    }

    @Override
    public String toString() {
      return "t/l/r/b (" + top + ", " + left + ", " + right + ", " + bottom + ")";
    }
  }

  public DTVCCWindow(CCSubtitleHandler ccSubtitleHandler, int id, int defaultWindow,
      int defaultPen, int anchorHorizontal, int anchorVertical,
      int anchorPoint, boolean relativeAnchor, boolean rowLock, boolean colLock, int rowCount,
      int columnCount, boolean visible, int priority) {
    this.ccSubtitleHandler = ccSubtitleHandler;
    this.id = id;
    this.anchorX = anchorHorizontal;
    this.anchorY = anchorVertical;
    this.anchorPoint = anchorPoint;
    this.relativeAnchor = relativeAnchor;
    this.rowLock = rowLock;
    this.colLock = colLock;
    this.visible = visible;
    this.priority = priority;

    this.defaultWindow = defaultWindow;
    this.defaultPen = defaultPen;

    rowCount = Math.min(rowCount, CCSubtitleHandler.CC_ROWS);
    columnCount = Math.min(columnCount, CCSubtitleHandler.CC_HD_COLS);

    this.buffer = new char[rowCount][columnCount];
    this.cellFormat = new long[rowCount][columnCount];

    currentFormatFill = CellFormat.setWindowID(0, id);
    currentFormatText = CellFormat.setWindowID(0, id);

    setAttributes(DTVCCWindowAttributes.defaultWindows[defaultWindow]);
    setPen(DTVCCPen.defaultPens[defaultPen]);
    setPenAttributes(DTVCCPenStyle.defaultPenStyle[defaultPen]);
    this.clear();
    if(visible) {
      flush();
    }
  }

  public DTVCCPenStyle getPenAttributes() {
    if(penAttributes != null) return penAttributes;
    return DTVCCPenStyle.defaultPenStyle[defaultPen];
  }

  public DTVCCWindowAttributes getAttributes() {
    if(attributes != null) return attributes;
    return DTVCCWindowAttributes.defaultWindows[defaultWindow];
  }

  public DTVCCPen getPen() {
    if(pen != null) return pen;
    return DTVCCPen.defaultPens[defaultPen];
  }

  /**
   * Move the cursor to the start of the next line; if that line is beyond the window,
   * cause the data to be scrolled.
   * CEA-CEB-10-A 6.1.4 Clarification of CR, HCR, and FF.
   */
  public void carriagReturn() {
    int rows = getRows();
    int cols = getCols();

    // First, reset the cursor to the correct side
    if (attributes.print == DTVCCDirection.LEFT_TO_RIGHT) {
      penX = 0;
    } else if (attributes.print == DTVCCDirection.RIGHT_TO_LEFT) {
      penX = cols - 1;
    } else if (attributes.print == DTVCCDirection.TOP_TO_BOTTOM) {
      penY = 0;
    } else if (attributes.print == DTVCCDirection.BOTTOM_TO_TOP) {
      penY = rows - 1;
    }

    // Next figure out the scroll
    if (attributes.scroll == DTVCCDirection.TOP_TO_BOTTOM) {
      if(penY - 1 < 0) {
        if (visible && ccSubtitleHandler != null)
          ccSubtitleHandler.addRollupRect(id, attributes.scroll);
        dirty = true;
        //scroll: Move all rows down one, starting with second to last row.
        for(int x = rows - 2; x >=0; x--) {
          System.arraycopy(buffer[x], 0, buffer[x+1], 0, cols);
          System.arraycopy(cellFormat[x], 0, cellFormat[x+1], 0, cols);
        }
        // Then fill.
        for(int x = 0; x < cols; x++) {
          buffer[0][x] = 0;
          cellFormat[0][x] = currentFormatFill;
        }
      } else {
        penY--;
      }
    } else if (attributes.scroll == DTVCCDirection.BOTTOM_TO_TOP) {
      if(penY + 1 >= rows) {
        if (visible && ccSubtitleHandler != null)
          ccSubtitleHandler.addRollupRect(id, attributes.scroll);
        dirty = true;
        //scroll: Move all rows up one, starting with second to first row.
        for(int x = 1; x < rows; x++) {
          System.arraycopy(buffer[x], 0, buffer[x-1], 0, cols);
          System.arraycopy(cellFormat[x], 0, cellFormat[x-1], 0, cols);
        }
        // Then fill.
        for(int x = 0; x < cols; x++) {
          buffer[rows-1][x] = 0;
          cellFormat[rows-1][x] = currentFormatFill;
        }
      } else {
        penY++;
      }
    } else if (attributes.scroll == DTVCCDirection.LEFT_TO_RIGHT) {
      if(penX - 1 < 0) {
        if (visible && ccSubtitleHandler != null)
          ccSubtitleHandler.addRollupRect(id, attributes.scroll);
        dirty = true;
        //scroll: Move all cols right one, starting with second to last col.
        for(int x = cols - 2; x >= 0; x--) {
          for(int i = 0; i < rows; i++) {
            buffer[i][x+1] = buffer[i][x];
            cellFormat[i][x+1] = cellFormat[i][x];
          }
        }
        // Then fill.
        for(int x = 0; x < rows; x++) {
          buffer[x][0] = 0;
          cellFormat[x][0] = currentFormatFill;
        }
      } else {
        penX--;
      }
    } else if (attributes.scroll == DTVCCDirection.RIGHT_TO_LEFT) {
      if(penX + 1 >= rows) {
        if (visible && ccSubtitleHandler != null)
          ccSubtitleHandler.addRollupRect(id, attributes.scroll);
        dirty = true;
        //scroll: Move all cols left one, starting with second to first col.
        for(int x = 1; x < cols; x++) {
          for(int i = 0; i < rows; i++) {
            buffer[i][x] = buffer[i][x-1];
            cellFormat[i][x] = cellFormat[i][x-1];
          }
        }
        // Then fill.
        for(int x = 0; x < rows; x++) {
          buffer[x][cols-1] = 0;
          cellFormat[x][cols-1] = currentFormatFill;
        }
      } else {
        penX++;
      }
    }
    possiblyFlush();
  }

  /**
   * Clear the screen and move to 0,0
   * CEA-CEB-10-A 6.1.4 Clarification of CR, HCR, and FF.
   */
  public void formFeed() {
    clear();
    penX = 0;
    penY = 0;
  }

  /**
   * Move the cursor to the start of the line and erase it.
   * CEA-CEB-10-A 6.1.4 Clarification of CR, HCR, and FF.
   */
  public void horizontalCarriagReturn() {
    if (attributes.print == DTVCCDirection.LEFT_TO_RIGHT) {
      penX = 0;
    } else if (attributes.print == DTVCCDirection.RIGHT_TO_LEFT) {
      penX = getCols();
    } else if (attributes.print == DTVCCDirection.TOP_TO_BOTTOM) {
      penY = 0;
    } else if (attributes.print == DTVCCDirection.BOTTOM_TO_TOP) {
      penY = getRows();
    }

    /* Clear the column or the row */
    if ((attributes.print == DTVCCDirection.TOP_TO_BOTTOM
        || attributes.print == DTVCCDirection.BOTTOM_TO_TOP) && (penX < getRows() && penX > -1)) {
      dirty = true;
      for(int i = 0; i < getRows(); i++) {
        buffer[i][penX] = 0;
        cellFormat[i][penX] = currentFormatFill;
      }
    } else if ((attributes.print == DTVCCDirection.LEFT_TO_RIGHT
        || attributes.print == DTVCCDirection.RIGHT_TO_LEFT) && (penY < getCols() && penY > -1)) {
      dirty = true;
      for(int i = 0; i < getCols(); i++) {
        buffer[penY][i] = 0;
        cellFormat[penY][i] = currentFormatFill;
      }
    }
    possiblyFlush();
  }

  /**
   * The pen is allowed to move off the page, we just ignore any characters written there
   */
  public void incrementCursor() {
    if (attributes.print == DTVCCDirection.LEFT_TO_RIGHT) {
      penX++;
    } else if (attributes.print == DTVCCDirection.RIGHT_TO_LEFT) {
      penX--;
    } else if (attributes.print == DTVCCDirection.TOP_TO_BOTTOM) {
      penY++;
    } else if (attributes.print == DTVCCDirection.BOTTOM_TO_TOP) {
      penY--;
    }
  }

  public int getRows() {
    return buffer.length;
  }

  public int getCols() {
    return buffer[0].length;
  }

  public void writeCharacter(char value) {
    if (penX >= getCols() || penX < 0 || penY >= getRows() || penY < 0) return;
    // If we're handed a transparent space, reset the background and foreground to transparent.
    if (value == CCSubtitleHandler.TRANSPARENT_SPACE_708) {
      cellFormat[penY][penX] =
          CellFormat.setBackground(currentFormatText, (byte) 0, DTVCCOpacity.TRANSPARENT);
      cellFormat[penY][penX] =
          CellFormat.setForeground(cellFormat[penY][penX], (byte) 0, DTVCCOpacity.TRANSPARENT);
    } else {
      cellFormat[penY][penX] = currentFormatText;
    }
    buffer[penY][penX] = value;
    dirty = true;
    incrementCursor();
    flush();
  }

  @Override
  public String toString() {
    return new String("window(priority:" + priority + " rowLock:" + rowLock + " colLock:" + colLock
        + " col/row:(" + getCols() + ", " + getRows() + ") visible:" + visible + " relativeAnchor:"
        + relativeAnchor + " anchorx/y:(" + anchorX + ", " + anchorY + ") " + " anchorPoint:"
        + anchorPoint + " " + getAttributes() + " " + getPenAttributes() + " " + getPen()
        + " pen x/y:" + penX + ", " + penY + ")");
  }

  /**
   * Dump window information as well as buffer data.
   */
  public String dumpWindow() {
    StringBuffer sb = new StringBuffer(toString());
    sb.append(": \r\n");

    int cols = getCols();
    int rows = getRows();
    for(int y = 0; y < cols; y++) {
      sb.append(y % 10);
    }
    sb.append("\r\n");
    for(int x = 0; x < rows; x++) {
      for(int y = 0; y < cols; y++) {
        if(buffer[x][y] > 0x19 && buffer[x][y] < 0x7F) {
          sb.append(buffer[x][y]);
        } else {
          sb.append(".");
        }
      }
      sb.append("\r\n");
    }
    for(int x = 0; x < rows; x++) {
      for(int y = 0; y < cols; y++) {
        sb.append(Long.toHexString(cellFormat[x][y]) + ", ");
      }
      sb.append("\r\n");
    }
    return sb.toString();
  }

  /**
   * Clear the window data and fill.
   */
  public void clear() {
    int cols = getCols();
    int rows = getRows();
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        cellFormat[row][col] = currentFormatFill;
        buffer[row][col] = 0;
      }
    }
    flush();
  }

  public void setPenAttributes(DTVCCPenStyle dtvccPenStyle) {
    penAttributes = dtvccPenStyle;
    currentFormatText = CellFormat.setEdgeType(currentFormatText, penAttributes.edge);
    currentFormatText = CellFormat.setFormatting(
        currentFormatText, penAttributes.underline, penAttributes.italic);
    currentFormatText = CellFormat.setPen(
        currentFormatText, penAttributes.font, penAttributes.penSize, penAttributes.dTVCCOffset);
  }

  public void setPen(DTVCCPen dtvccPen) {
    pen = dtvccPen;
    currentFormatText = CellFormat.setBackground(
        currentFormatText, pen.backgroundColor, this.pen.backgroundOpacity);
    currentFormatText =
        CellFormat.setForeground(currentFormatText, pen.foregroundColor, pen.foregroundOpacity);
    currentFormatText = CellFormat.setEdgeColor(currentFormatText, pen.edgeColor);
  }

  public void setAttributes(DTVCCWindowAttributes dtvccWindowAttributes) {
    attributes = dtvccWindowAttributes;
    currentFormatFill =
        CellFormat.setBackground(currentFormatFill, attributes.fillColor, attributes.opacity);
  }

  private Rectangle windowAnchorToRectangle() {
    // NOTE: We're doing minimum resolution, so screen coordinates are / 5.
    Rectangle rect = new Rectangle();
    int cols = getCols();
    int rows = getRows();

    // top left = 0, center, right; middle left, center, right; bottom left, center right
    // NOTE(codefu): Need to know 4:3 vs 16:9... Assume 16:9?
    // First, figure out the top line.
    rect.top = relativeAnchor ? ((75*anchorY)/100) : anchorY;
    rect.top /= 5;
    if(anchorPoint < 3) {
      // top; nothing
    } else if (anchorPoint < 6) {
      // middle
      rect.top -= rows / 2;
    } else {
      // bottom
      rect.top -= rows;
    }

    // Second, figure out left
    rect.left = relativeAnchor ? ((210*anchorX)/100) : anchorX;
    rect.left /= 5;
    switch(anchorPoint) {
      case 1: // x center
      case 4: // x center
      case 7: // x center
        rect.left -= cols / 2;
        break;
      case 2: // x right
      case 5: // x right
      case 8: // x right
        rect.left -= cols;
        break;
    }
    rect.bottom = rect.top + rows;
    rect.right = rect.left + cols;
    return rect;
  }

  /**
   * Gate our flushing to the screen based on dirty accumulation of data. When copying
   * to the display, make sure we don't have anyone else above us overlapping.
   */
  public void possiblyFlush() {
    if(dirty) {
      flush();
    }
  }

  public void setVisibility(boolean vis) {
    if(vis != visible) {
      visible = vis;
      if(vis) {
        // turning on, draw ourselves into the buffers
        flush();
      } else {
        if(ccSubtitleHandler == null) return;
        // First remove our buffer
        int cols = getCols();
        int rows = getRows();
        char[][] display = ccSubtitleHandler.getCCDisplayData();
        long[][] displayFormat = ccSubtitleHandler.get708CellFormat();
        Rectangle ourRect = windowAnchorToRectangle();
        // Erase ourselves from the display buffers.
        for (int y = 0; y < rows; y++) {
          for (int x = 0; x < cols; x++) {
            display[ourRect.top + y][ourRect.left + x] = 0;
            displayFormat[ourRect.top + y][ourRect.left + x] = CCSubtitleHandler.defaultEmpty708Format;
          }
        }
        ccSubtitleHandler.updateNeeded = true;

        // Then redraw the other ones.
        DTVCCWindow[] windows = ccSubtitleHandler.getWindows();
        for(int i = 0; i < windows.length; i++) {
          if (windows[i] != null && windows[i] != this && windows[i].visible && ourRect.collides(windows[i].windowAnchorToRectangle())) {
            // redraw uncovered areas; they'll collide upwards and only draw what's uncovered.
            windows[i].flush();
          }
        }
      }
    }
  }

  public void flush() {
    if(!visible) return;

    int cols = getCols();
    int rows = getRows();

    if (ccSubtitleHandler == null) return;
    if (ccSubtitleHandler.canLog(CCSubtitleHandler.INFORM)) System.out.println("Flushing dirty window");
    // hit box, to be clipped by all higher-level windows
    //		int startCol = 0;
    //		int startRow = 0;
    //		int endCol = cols;
    //		int endRow = rows;

    // only copy things in the current visible (uncovered) area
    //		BitSet[] lines = new BitSet[rows];
    //		for (int x = 0; x < rows; x++) {
    //			// defaults to all bits being zero; we'll just say "0 = visible"
    //			// TODO(codefu) should cache this rectangle and only update it on move/resize
    //			lines[x] = new BitSet(cols);
    //		}

    //	boolean clipped = false;
    Rectangle ourRect = windowAnchorToRectangle();
    DTVCCWindow[] windows = ccSubtitleHandler.getWindows();
    for (int i = 0; i < windows.length; i++) {
      if (windows[i] != null && windows[i] != this && windows[i].visible && windows[i].priority < priority) {
        // test for collision
        Rectangle otherRect = windows[i].windowAnchorToRectangle();
        if(!ourRect.collides(otherRect)) continue;

        // According to the minimum requirements, we could just ditch here.
        return;
        // TODO(codefu): Allow overlapping windows
        //				startRow = (otherRect.top < ourRect.top) ? 0 : otherRect.top - ourRect.top;
        //				startCol = (otherRect.left < ourRect.left) ? 0 : otherRect.left - ourRect.left;
        //				endRow = (otherRect.bottom < ourRect.bottom) ? ourRect.bottom - otherRect.bottom : cols;
        //				endCol = (otherRect.right < ourRect.right) ? ourRect.right - otherRect.right : rows;
        //				if (startCol >= endCol) continue;
        //				clipped = true;
        //				// Mark segments that are "hidden"
        //				for (int y = startRow; y < endRow; y++) {
        //					lines[y].set(startCol, endCol);
        //				}
      }
    }
    // lines now contains 0's for copy and 1's for skips.
    char[][] display = ccSubtitleHandler.getCCDisplayData();
    long[][] displayFormat = ccSubtitleHandler.get708CellFormat();
    rows = Math.min(display.length - ourRect.top, rows);
    cols = Math.min(display[0].length - ourRect.left, cols);

    //		if (clipped) {
    //			for (int y = 0; y < rows; y++) {
    //				for (int x = 0; x < cols; x++) {
    //					if (lines[y].get(x)) continue;
    //					display[ourRect.top + y][ourRect.left + x] = buffer[y][x];
    //					displayFormat[ourRect.top + y][ourRect.left + x] = cellFormat[y][x];
    //				}
    //			}
    //		} else {
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        display[ourRect.top + y][ourRect.left + x] = buffer[y][x];
        displayFormat[ourRect.top + y][ourRect.left + x] = cellFormat[y][x];
      }
    }
    //		}
    dirty = false;
    ccSubtitleHandler.updateNeeded = true;
  }

  /**
   * Reset the window (perhaps we're being deleted?)
   */
  public void reset() {
    // note; window ID is being reset to 0 (invalid)
    currentFormatFill = CellFormat.setBackground(0, (byte)0, DTVCCOpacity.TRANSPARENT);
    currentFormatFill = CellFormat.setForeground(currentFormatFill, (byte)0, DTVCCOpacity.TRANSPARENT);
    currentFormatFill = CellFormat.setPen(currentFormatFill, DTVCCFontType.DEFAULT, DTVCCSize.STANDARD, DTVCCOffset.NORMALSCRIPT);
    clear();
  }

  public void resize(int rowCount, int columnCount) {
    // NOTE: Resize might expand to the left, right, both directions, etc, depending on the
    // anchor point.  Clarification is needed to know if the text moves, cleared, or fill space
    // is given to the direction(s).
    //buffer = new char[rows][cols];
    //cellFormat = new long[rows][cols];
    char[][] newBuffer = new char[rowCount][columnCount];
    long[][] newCellFormat = new long[rowCount][columnCount];

    int cols = getCols();
    int rows = getRows();

    // HACK: For now; either clip or grow to the right and down
    for (int y = 0; y < rowCount; y++) {
      if(y < rows) {
        for (int x = 0; x < columnCount; x++) {
          if (x < cols) {
            newBuffer[y][x] = buffer[y][x];
            newCellFormat[y][x] = cellFormat[y][x];
          } else {
            newBuffer[y][x] = 0;
            newCellFormat[y][x] = currentFormatFill;
          }
        }
      } else {
        for (int x = 0; x < columnCount; x++) {
          newBuffer[y][x] = 0;
          newCellFormat[y][x] = currentFormatFill;
        }
      }
    }

    buffer = newBuffer;
    cellFormat = newCellFormat;
    dirty = true;
  }
}