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
package sage.miniclient;

public class AppletGFXCMD extends java.applet.Applet implements java.awt.event.KeyListener, java.awt.event.MouseListener, java.awt.event.MouseMotionListener,
java.awt.event.MouseWheelListener
{
  public static final boolean ENABLE_MOUSE_MOTION_EVENTS = true;
  public static final int GFXCMD_INIT = 1;

  public static final int  GFXCMD_DEINIT = 2;

  public static final int  GFXCMD_DRAWRECT = 16;
  // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_FILLRECT = 17;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_CLEARRECT = 18;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL

  public static final int GFXCMD_DRAWOVAL = 19;
  // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_FILLOVAL = 20;
  // x, y, width, height, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWROUNDRECT = 21;
  // x, y, width, height, thickness, arcRadius, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_FILLROUNDRECT = 22;
  // x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
  // clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWTEXT = 23;
  // x, y, len, text, handle, argb, clipX, clipY, clipW, clipH

  public static final int GFXCMD_DRAWTEXTURED = 24;
  // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend

  public static final int GFXCMD_DRAWLINE = 25;
  // x1, y1, x2, y2, argb1, argb2

  public static final int GFXCMD_LOADIMAGE = 26;
  // width, height

  public static final int GFXCMD_UNLOADIMAGE = 27;
  // handle

  public static final int GFXCMD_LOADFONT = 28;
  // namelen, name, style, size

  public static final int GFXCMD_UNLOADFONT = 29;
  // handle

  public static final int GFXCMD_FLIPBUFFER = 30;

  public static final int GFXCMD_STARTFRAME = 31;

  public static final int GFXCMD_LOADIMAGELINE = 32;
  // handle, line, len, data

  public static final int GFXCMD_PREPIMAGE = 33;
  // width, height

  public static final int GFXCMD_LOADIMAGECOMPRESSED = 34;
  // handle, len, data

  public static final int GFXCMD_XFMIMAGE = 35;
  // srcHandle, destHandle, destWidth, destHeight, maskCornerArc

  public static final int GFXCMD_LOADFONTSTREAM = 36;
  // namelen, name, len, data

  public static int readInt(int pos, byte[] cmddata)
  {
    pos += 4; // for the 4 bytes for the header
    return ((cmddata[pos+0] & 0xFF)<<24)|((cmddata[pos+1] & 0xFF)<<16)|((cmddata[pos+2] & 0xFF)<<8)|(cmddata[pos+3] & 0xFF);
  }

  public static int readIntSwapped(int pos, byte[] cmddata)
  {
    pos += 4; // for the 4 bytes for the header
    return ((cmddata[pos+3] & 0xFF)<<24)|((cmddata[pos+2] & 0xFF)<<16)|((cmddata[pos+1] & 0xFF)<<8)|(cmddata[pos+0] & 0xFF);
  }

  public static short readShort(int pos, byte[] cmddata)
  {
    pos += 4; // for the 4 bytes for the header
    return (short) (((cmddata[pos+0] & 0xFF)<<8)|(cmddata[pos+1] & 0xFF));
  }

  public static short readShortSwapped(int pos, byte[] cmddata)
  {
    pos += 4; // for the 4 bytes for the header
    return (short) (((cmddata[pos+1] & 0xFF)<<8)|(cmddata[pos+0] & 0xFF));
  }

  public void start()
  {
  }

  public AppletGFXCMD()
  {
    imageCacheLimit = 32000000;
  }
  public void setConn(AppletConnection inConn)
  {
    myConn = inConn;
  }

  protected AppletConnection myConn;
  private java.awt.Canvas c;
  private java.awt.image.BufferedImage backBuff;
  private java.awt.Graphics2D g2;
  private java.util.Map fontMap = new java.util.HashMap();
  private java.util.Map imageMap = new java.util.HashMap();
  private int handleCount = 2;
  private java.awt.Image bgImage;
  private java.awt.Image logoImage;
  private java.awt.Font pleaseWaitFont;
  private long imageCacheSize;
  private long imageCacheLimit;
  private java.awt.Cursor hiddenCursor;
  private boolean cursorHidden;
  private boolean firstFrameDone;

  public void close()
  {
  }

  public java.awt.Canvas getGraphicsCanvas()
  {
    return c;
  }

  public void refresh()
  {
    c.invalidate();
    invalidate();
    validate();
  }

  public void init()
  {
    try
    {
      new AppletConnection(this, getCodeBase().getHost(), null).connect();
    }catch (Exception e){}

    java.awt.LayoutManager layer = new java.awt.LayoutManager()
    {
      public void addLayoutComponent(String name, java.awt.Component comp)
      {}
      public java.awt.Dimension minimumLayoutSize(java.awt.Container parent)
      {
        return preferredLayoutSize(parent);
      }
      public java.awt.Dimension preferredLayoutSize(java.awt.Container parent)
      {
        return parent.getPreferredSize();
      }
      public void removeLayoutComponent(java.awt.Component comp)
      {}
      public void layoutContainer(java.awt.Container parent)
      {
        c.setBounds(parent.getInsets().left, parent.getInsets().top, parent.getWidth() - parent.getInsets().left - parent.getInsets().right,
            parent.getHeight() - parent.getInsets().top - parent.getInsets().bottom);
        //System.out.println("LAYOUT frame bounds=" + f.getBounds() + " videoBounds=" + videoBounds + " parentBounds=" + parent.getBounds());
      }
    };
    setLayout(layer);
    try
    {
      bgImage = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/Background.jpg"));
      ensureImageIsLoaded(bgImage);
      logoImage = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/SageLogo256.png"));
      ensureImageIsLoaded(logoImage);
    }
    catch (Exception e)
    {
      System.out.println("ERROR:" + e);
      e.printStackTrace();
    }
    pleaseWaitFont = new java.awt.Font("Arial", java.awt.Font.BOLD, 32);
    setFocusTraversalKeysEnabled(false);
    c = new java.awt.Canvas()
    {
      public void update(java.awt.Graphics g)
      {
        paint(g);
      }
      public void paint(java.awt.Graphics g)
      {
        //				System.out.println("REPAINTING IMAGE");
        if (backBuff != null && firstFrameDone)
          g.drawImage(backBuff, 0, 0, null);
        else
        {
          java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
          g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
          g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
          int targetWidth = getWidth()*2/10;
          int targetHeight = getHeight()*7/100;
          float imageAR = ((float)logoImage.getWidth(null))/logoImage.getHeight(null);
          if (((float)targetWidth/targetHeight) > imageAR)
            targetWidth = Math.round(targetHeight * imageAR);
          else
            targetHeight = Math.round(targetWidth / imageAR);
          java.awt.Composite oldC = ((java.awt.Graphics2D) g).getComposite();
          ((java.awt.Graphics2D) g).setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.85f));
          g.drawImage(logoImage, getWidth()*2/100, getHeight()*15/1000, getWidth()*2/100+targetWidth, targetHeight+getHeight()*15/1000,
              0, 0, logoImage.getWidth(null), logoImage.getHeight(null), null);
          String str1 = "SageTV Placeshifter is connecting to";
          String str2 = "the server: " + ((myConn == null) ? "" : myConn.getServerName());
          String str3 = "Please Wait...";
          java.awt.FontMetrics fm = getFontMetrics(pleaseWaitFont);
          int fh = fm.getHeight() * 4;
          int y = (getHeight() / 2) - fh/2;
          g.setFont(pleaseWaitFont);
          g.setColor(java.awt.Color.black);
          y += 2;
          g.drawString(str1, 2 + (getWidth()/2) - (fm.stringWidth(str1)/2), y + fm.getAscent());
          y += fm.getHeight();
          g.drawString(str2, 2 + (getWidth()/2) - (fm.stringWidth(str2)/2), y + fm.getAscent());
          y += 2*fm.getHeight();
          g.drawString(str3, 2 + (getWidth()/2) - (fm.stringWidth(str3)/2), y + fm.getAscent());
          g.setColor(java.awt.Color.white);
          y = (getHeight() / 2) - fh/2;
          g.drawString(str1, (getWidth()/2) - (fm.stringWidth(str1)/2), y + fm.getAscent());
          y += fm.getHeight();
          g.drawString(str2, (getWidth()/2) - (fm.stringWidth(str2)/2), y + fm.getAscent());
          y += 2*fm.getHeight();
          g.drawString(str3, (getWidth()/2) - (fm.stringWidth(str3)/2), y + fm.getAscent());
        }
      }
    };
    c.setFocusTraversalKeysEnabled(false);
    add(c);
    c.addComponentListener(new java.awt.event.ComponentAdapter()
    {
      public void componentResized(java.awt.event.ComponentEvent evt)
      {
        if (myConn != null)
          myConn.postResizeEvent(new java.awt.Dimension(c.getWidth(), c.getHeight()));
      }
    });
    c.addKeyListener(this);
    c.addMouseListener(this);
    addMouseWheelListener(this);
    if (ENABLE_MOUSE_MOTION_EVENTS)
    {
      //f.addMouseMotionListener(this);
      c.addMouseMotionListener(this);
    }
  }

  public int ExecuteGFXCommand(int cmd, int len, byte[] cmddata, int[] hasret)
  {
    len -= 4; // for the 4 byte header
    hasret[0] = 0; // Nothing to return by default
    //System.out.println("GFXCMD=" + cmd);

    if (getGraphicsCanvas() != null)
    {
      switch(cmd)
      {
        case GFXCMD_INIT:
        case GFXCMD_DEINIT:
        case GFXCMD_STARTFRAME:
        case GFXCMD_FLIPBUFFER:
          getGraphicsCanvas().setCursor(null);
          break;
        case GFXCMD_DRAWRECT:
        case GFXCMD_FILLRECT:
        case GFXCMD_CLEARRECT:
        case GFXCMD_DRAWOVAL:
        case GFXCMD_FILLOVAL:
        case GFXCMD_DRAWROUNDRECT:
        case GFXCMD_FILLROUNDRECT:
        case GFXCMD_DRAWTEXT:
        case GFXCMD_DRAWTEXTURED:
        case GFXCMD_DRAWLINE:
        case GFXCMD_LOADIMAGE:
        case GFXCMD_UNLOADIMAGE:
        case GFXCMD_LOADFONT:
        case GFXCMD_UNLOADFONT:
          break;
        case GFXCMD_PREPIMAGE:
        case GFXCMD_LOADIMAGELINE:
        case GFXCMD_LOADIMAGECOMPRESSED:
        case GFXCMD_XFMIMAGE:
          getGraphicsCanvas().setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
          break;
      }
    }
    switch(cmd)
    {
      case GFXCMD_INIT:
        hasret[0] = 1;
        //backBuff = new java.awt.image.BufferedImage(720, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        return 1;
      case GFXCMD_DEINIT:
        break;
      case GFXCMD_DRAWRECT:
        if(len==36)
        {
          int x, y, width, height, thickness,
          argbTL, argbTR, argbBR, argbBL;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          thickness=readInt(16, cmddata);
          argbTL=readInt(20, cmddata);
          argbTR=readInt(24, cmddata);
          argbBR=readInt(28, cmddata);
          argbBL=readInt(32, cmddata);
          g2.setStroke(new java.awt.BasicStroke(thickness));
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //					updatePaintContext(g2, (argbTL >> 24) & 0xFF, x, y, new java.awt.Color(argbTL), x + width, y + height, new java.awt.Color(argbB
          //					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          //					g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //						x + width, y + height, new java.awt.Color(argbBR)));
          g2.drawRect(x+thickness/2, y+thickness/2, width-thickness, height-thickness);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWRECT : " + len);
        }
        break;
      case GFXCMD_FILLRECT:
        // x, y, width, height, argbTL, argbTR, argbBR, argbBL
        if(len==32)
        {
          int x, y, width, height,
          argbTL, argbTR, argbBR, argbBL;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          argbTL=readInt(16, cmddata);
          argbTR=readInt(20, cmddata);
          argbBR=readInt(24, cmddata);
          argbBL=readInt(28, cmddata);
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          //g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //	x + width, y + height, new java.awt.Color(argbBR)));
          g2.fillRect(x, y, width, height);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_FILLRECT : " + len);
        }
        break;
      case GFXCMD_CLEARRECT:
        // x, y, width, height, argbTL, argbTR, argbBR, argbBL
        if(len==32)
        {
          int x, y, width, height,
          argbTL, argbTR, argbBR, argbBL;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          argbTL=readInt(16, cmddata);
          argbTR=readInt(20, cmddata);
          argbBR=readInt(24, cmddata);
          argbBL=readInt(28, cmddata);
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC));
          g2.setBackground(new java.awt.Color(argbTL, true));
          g2.clearRect(x, y, width, height);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_CLEARRECT : " + len);
        }
        break;
      case GFXCMD_DRAWOVAL:
        // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL,
        // clipX, clipY, clipW, clipH
        if(len==52)
        {
          int x, y, width, height, thickness,
          argbTL, argbTR, argbBR, argbBL,
          clipX, clipY, clipW, clipH;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          thickness=readInt(16, cmddata);
          argbTL=readInt(20, cmddata);
          argbTR=readInt(24, cmddata);
          argbBR=readInt(28, cmddata);
          argbBL=readInt(32, cmddata);
          clipX=readInt(36, cmddata);
          clipY=readInt(40, cmddata);
          clipW=readInt(44, cmddata);
          clipH=readInt(48, cmddata);
          g2.setStroke(new java.awt.BasicStroke(thickness));
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //					g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //						x, y + height, new java.awt.Color(argbBL)));
          //					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.drawOval(x+thickness/2, y+thickness/2, width-thickness, height-thickness);
          g2.setClip(null);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWOVAL : " + len);
        }

        break;
      case GFXCMD_FILLOVAL:
        // x, y, width, height, argbTL, argbTR, argbBR, argbBL,
        // clipX, clipY, clipW, clipH
        if(len==48)
        {
          int x, y, width, height,
          argbTL, argbTR, argbBR, argbBL,
          clipX, clipY, clipW, clipH;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          argbTL=readInt(16, cmddata);
          argbTR=readInt(20, cmddata);
          argbBR=readInt(24, cmddata);
          argbBL=readInt(28, cmddata);
          clipX=readInt(32, cmddata);
          clipY=readInt(36, cmddata);
          clipW=readInt(40, cmddata);
          clipH=readInt(44, cmddata);
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //					g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //						x, y + height, new java.awt.Color(argbBL)));
          //					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.fillOval(x, y, width, height);
          g2.setClip(null);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_FILLOVAL : " + len);
        }
        break;
      case GFXCMD_DRAWROUNDRECT:
        // x, y, width, height, thickness, arcRadius, argbTL, argbTR, argbBR, argbBL,
        // clipX, clipY, clipW, clipH
        if(len==56)
        {
          int x, y, width, height, thickness, arcRadius,
          argbTL, argbTR, argbBR, argbBL,
          clipX, clipY, clipW, clipH;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          thickness=readInt(16, cmddata);
          arcRadius=readInt(20, cmddata)*2;
          argbTL=readInt(24, cmddata);
          argbTR=readInt(28, cmddata);
          argbBR=readInt(32, cmddata);
          argbBL=readInt(36, cmddata);
          clipX=readInt(40, cmddata);
          clipY=readInt(44, cmddata);
          clipW=readInt(48, cmddata);
          clipH=readInt(52, cmddata);
          g2.setStroke(new java.awt.BasicStroke(thickness));
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //					g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //						x, y + height, new java.awt.Color(argbBL)));
          //					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.drawRoundRect(x+thickness/2, y+thickness/2, width-thickness, height-thickness, arcRadius, arcRadius);
          g2.setClip(null);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWROUNDRECT : " + len);
        }
        break;
      case GFXCMD_FILLROUNDRECT:
        // x, y, width, height, arcRadius, argbTL, argbTR, argbBR, argbBL,
        // clipX, clipY, clipW, clipH
        if(len==52)
        {
          int x, y, width, height, arcRadius,
          argbTL, argbTR, argbBR, argbBL,
          clipX, clipY, clipW, clipH;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          arcRadius=readInt(16, cmddata)*2;
          argbTL=readInt(20, cmddata);
          argbTR=readInt(24, cmddata);
          argbBR=readInt(28, cmddata);
          argbBL=readInt(32, cmddata);
          clipX=readInt(36, cmddata);
          clipY=readInt(40, cmddata);
          clipW=readInt(44, cmddata);
          clipH=readInt(48, cmddata);
          updatePaintContext(g2, x, y, width, height, argbTL, argbTR, argbBL, argbBR);
          //					g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
          //						x, y + height, new java.awt.Color(argbBL)));
          //					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.fillRoundRect(x, y, width, height, arcRadius, arcRadius);
          g2.setClip(null);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_FILLROUNDRECT : " + len);
        }
        break;
      case GFXCMD_DRAWTEXT:
        // x, y, len, text, handle, argb, clipX, clipY, clipW, clipH
        if(len>=36 && len>=(36+readInt(8, cmddata)*2))
        {
          int x, y, textlen,
          fontHandle, argb,
          clipX, clipY, clipW, clipH;
          StringBuffer text = new StringBuffer();
          int i;

          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          textlen=readInt(8, cmddata);
          for(i=0;i<textlen;i++)
          {
            text.append((char)readShort(12+i*2, cmddata));
          }
          fontHandle=readInt(textlen*2+12, cmddata);
          argb=readInt(textlen*2+16, cmddata);
          clipX=readInt(textlen*2+20, cmddata);
          clipY=readInt(textlen*2+24, cmddata);
          clipW=readInt(textlen*2+28, cmddata);
          clipH=readInt(textlen*2+32, cmddata);
          if (System.getProperty("java.version").startsWith("1.4"))
            clipW = clipW * 5 / 4;
          g2.setColor(new java.awt.Color(argb));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argb >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          if (fontMap.get(new Integer(fontHandle)) != null)
          {
            g2.setFont((java.awt.Font) fontMap.get(new Integer(fontHandle)));
            g2.drawString(text.toString(), x, y);
          }
          g2.setClip(null);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWTEXT : " + len);
        }
        break;
      case GFXCMD_DRAWTEXTURED:
        // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend
        if(len==40)
        {
          int x, y, width, height, handle,
          srcx, srcy, srcwidth, srcheight, blend;
          x=readInt(0, cmddata);
          y=readInt(4, cmddata);
          width=readInt(8, cmddata);
          height=readInt(12, cmddata);
          handle=readInt(16, cmddata);
          srcx=readInt(20, cmddata);
          srcy=readInt(24, cmddata);
          srcwidth=readInt(28, cmddata);
          srcheight=readInt(32, cmddata);
          blend=readInt(36, cmddata);
          java.awt.image.BufferedImage img = (java.awt.image.BufferedImage) imageMap.get(new Integer(handle));
          if (img != null)
          {
            try
            {
              if (width > 0)
              {
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((blend >> 24) & 0xFF) / 255.0f));
                g2.drawImage(img, x, y, x + width, y + height, srcx, srcy, srcx + srcwidth, srcy + srcheight, null);
              }
              else
              {
                java.awt.Color blendColor = new java.awt.Color(blend, true);
                java.awt.image.RescaleOp colorScaler = new java.awt.image.RescaleOp(
                    blendColor.getRGBComponents(null), new float[] { 0f, 0f, 0f, 0f }, null);
                java.awt.image.BufferedImage subImage = img.getSubimage(srcx, srcy, srcwidth, srcheight);
                java.awt.image.BufferedImage bi2 = colorScaler.filter(subImage, null);
                g2.drawImage(bi2, x, y, x - width, y + height, 0, 0, srcwidth, srcheight, null);
                bi2.flush();
                bi2 = null;
              }
            }
            catch (Exception e)
            {
              System.out.println("ERROR: " + e);
              e.printStackTrace();
            }
            if (width > 0)
              g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER));
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWTEXTURED : " + len);
        }
        break;
      case GFXCMD_DRAWLINE:
        // x1, y1, x2, y2, argb1, argb2
        if(len==24)
        {
          int x1, y1, x2, y2, argb1, argb2;
          x1=readInt(0, cmddata);
          y1=readInt(4, cmddata);
          x2=readInt(8, cmddata);
          y2=readInt(12, cmddata);
          argb1=readInt(16, cmddata);
          argb2=readInt(20, cmddata);
          g2.setPaint(new java.awt.GradientPaint(x1, y1, new java.awt.Color(argb1),
              x2, y2, new java.awt.Color(argb2)));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argb1 >> 24) & 0xFF) / 255.0f));
          g2.drawLine(x1, y1, x2, y2);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWLINE : " + len);
        }
        break;
      case GFXCMD_LOADIMAGE:
        // width, height
        if(len>=8)
        {
          int width, height;
          int imghandle = handleCount++;;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          if (width * height * 4 + imageCacheSize > imageCacheLimit)
            imghandle = 0;
          else
          {
            java.awt.Image img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            imageMap.put(new Integer(imghandle), img);
            imageCacheSize += width * height * 4;
          }
          //imghandle=STBGFX.GFX_loadImage(width, height);
          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGE : " + len);
        }
        break;
      case GFXCMD_PREPIMAGE:
        // width, height
        if(len>=8)
        {
          int width, height;
          //int imghandle = handleCount++;;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          //java.awt.Image img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
          //imageMap.put(new Integer(imghandle), img);
          // We don't actually use this, it's just for being sure we have enough room for allocation
          int imghandle = 1;
          if (width * height * 4 + imageCacheSize > imageCacheLimit)
            imghandle = 0;
          //imghandle=STBGFX.GFX_loadImage(width, height);
          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_PREPIMAGE : " + len);
        }
        break;
      case GFXCMD_UNLOADIMAGE:
        // handle
        if(len==4)
        {
          int handle;
          handle=readInt(0, cmddata);
          //STBGFX.GFX_unloadImage(handle);
          java.awt.image.BufferedImage bi = (java.awt.image.BufferedImage) imageMap.get(new Integer(handle));
          imageCacheSize -= bi.getWidth(null) * bi.getHeight(null) * 4;
          imageMap.remove(new Integer(handle));
          if (bi != null)
            bi.flush();
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADIMAGE : " + len);
        }
        break;
      case GFXCMD_LOADFONT:
        // namelen, name, style, size
        if(len>=12 && len>=(12+readInt(0, cmddata)))
        {
          int namelen, style, size;
          StringBuffer name = new StringBuffer();
          int i;
          int fonthandle = handleCount++;

          namelen=readInt(0, cmddata);
          for(i=0;i<namelen-1;i++) // skip the terminating \0 character
          {
            name.append((char) cmddata[8 + i]); // an extra 4 for the header
          }
          style=readInt(namelen+4, cmddata);
          size=readInt(namelen+8, cmddata);
          java.awt.Font fonty = new java.awt.Font(name.toString(), style, size);
          String cacheName = name.toString() + "-" + style;
          System.out.println("Loaded Font=" + fonty);
          fontMap.put(new Integer(fonthandle), fonty);
          //fonthandle=STBGFX.GFX_loadFont(name.toString(), style, size);
          hasret[0] = 1;
          return fonthandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADFONT : " + len);
        }

        break;
      case GFXCMD_UNLOADFONT:
        // handle
        if(len==4)
        {
          int handle;
          handle=readInt(0, cmddata);
          //STBGFX.GFX_unloadFont(handle);
          fontMap.remove(new Integer(handle));
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADFONT : " + len);
        }
        break;
      case GFXCMD_FLIPBUFFER:
        hasret[0] = 1;
        //STBGFX.GFX_flipBuffer();
        firstFrameDone = true;
        g2.dispose();
        java.awt.Graphics cg = c.getGraphics();
        cg.drawImage(backBuff, 0, 0, null);
        cg.dispose();
        java.awt.Toolkit.getDefaultToolkit().sync();
        return 0;
      case GFXCMD_STARTFRAME:
        if (backBuff == null || backBuff.getWidth() < c.getWidth() || backBuff.getHeight() < c.getHeight())
        {
          if (backBuff != null)
            backBuff.flush();
          backBuff = new java.awt.image.BufferedImage(c.getWidth(), c.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        }
        g2 = backBuff.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER));
        break;
      case GFXCMD_LOADIMAGELINE:
        // handle, line, len, data
        if(len>=12 && len>=(12+readInt(8, cmddata)))
        {
          int handle, line, len2;
          //unsigned char *data=&cmddata[12];
          handle=readInt(0, cmddata);
          line=readInt(4, cmddata);
          len2=readInt(8, cmddata);
          // the last number is the offset into the data array to start reading from
          //STBGFX.GFX_loadImageLine(handle, line, len, data, 12);
          int dataPos = 12;
          java.awt.image.BufferedImage bi = (java.awt.image.BufferedImage) imageMap.get(new Integer(handle));
          for (int i = 0; i < len2/4; i++, dataPos += 4)
          {
            bi.setRGB(i, line, readInt(dataPos, cmddata));
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGELINE : " + len);
        }
        break;
      case GFXCMD_LOADIMAGECOMPRESSED:
        // handle, line, len, data
        if(len>=8 && len>=(8+readInt(4, cmddata)))
        {
          int handle, len2;
          handle=readInt(0, cmddata);
          len2=readInt(4, cmddata);
          java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(cmddata, 12, len2); // an extra 4 for the header

          handle = handleCount++;
          try
          {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(bais);
            imageMap.put(new Integer(handle), bi);
            imageCacheSize += bi.getWidth() * bi.getHeight() * 4;
            hasret[0] = 1;
            return handle;
          }
          catch (java.io.IOException e)
          {
            System.out.println("ERROR loading compressed image: " + e);
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGECOMPRESSED : " + len);
        }
        break;
      case GFXCMD_XFMIMAGE:
        // srcHandle, destHandle, destWidth, destHeight, maskCornerArc
        if (len >= 20)
        {
          int srcHandle, destHandle, destWidth, destHeight, maskCornerArc;
          srcHandle = readInt(0, cmddata);
          destHandle = readInt(4, cmddata);
          destWidth = readInt(8,  cmddata);
          destHeight = readInt(12, cmddata);
          maskCornerArc = readInt(16, cmddata);
          int rvHandle = handleCount++;
          hasret[0]=1;
          java.awt.Image srcImg = (java.awt.Image) imageMap.get(new Integer(srcHandle));
          if (destWidth * destHeight * 4 + imageCacheSize > imageCacheLimit || srcImg == null)
            rvHandle = 0;
          else
          {
            java.awt.image.BufferedImage destImg = new java.awt.image.BufferedImage(destWidth, destHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            imageMap.put(new Integer(rvHandle), destImg);
            imageCacheSize += destWidth * destHeight * 4;
            if (maskCornerArc > 0)
            {
              java.awt.Graphics2D g2 = destImg.createGraphics();
              g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                  java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
              g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION,
                  java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
              g2.setComposite(java.awt.AlphaComposite.Src);
              java.awt.Shape imageArea = new java.awt.geom.RoundRectangle2D.Float(0, 0, destWidth, destHeight, maskCornerArc, maskCornerArc);
              g2.setClip(imageArea);
              g2.drawImage(srcImg, 0, 0, destWidth, destHeight, null);
              if (imageArea != null)
              {
                g2.setClip(null);
                g2.setColor(new java.awt.Color(0, 0, 0, 0.5f));
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.draw(imageArea);
              }
              g2.dispose();
            }
            else
            {
              java.awt.Graphics2D g2 = destImg.createGraphics();
              g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                  java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
              g2.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION,
                  java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
              g2.setComposite(java.awt.AlphaComposite.Src);
              g2.drawImage(srcImg, 0, 0, destWidth, destHeight, null);
              g2.dispose();
            }
          }
          return rvHandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_XFMIMAGE : " + len);
        }
        break;

      default:
        return -1;
    }
    return 0;
  }

  private boolean lastWasPressed;
  private boolean ignoreNextTyped;
  private int lastKeyCode;
  private int lastModifiers;
  public void keyPressed(java.awt.event.KeyEvent evt)
  {
    lastWasPressed = true;
    lastKeyCode = evt.getKeyCode();
    lastModifiers = evt.getModifiers();

    // If it's only modifier keys, then don't post the event
    if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_SHIFT ||
        evt.getKeyCode() == java.awt.event.KeyEvent.VK_CONTROL ||
        evt.getKeyCode() == java.awt.event.KeyEvent.VK_ALT ||
        evt.getKeyCode() == java.awt.event.KeyEvent.VK_ALT_GRAPH ||
        evt.getKeyCode() == java.awt.event.KeyEvent.VK_META)
    {
      ignoreNextTyped = false;
    }
    else if (evt.getKeyChar() == 0 || evt.getKeyChar() == java.awt.event.KeyEvent.CHAR_UNDEFINED)
    {
      myConn.postKeyEvent(lastKeyCode, lastModifiers, (char) 0);
      lastWasPressed = false;
      ignoreNextTyped = false;
    }
    else
    {
      lastWasPressed = false;
      myConn.postKeyEvent(lastKeyCode, lastModifiers, evt.getKeyChar());
      ignoreNextTyped = true;
    }
  }

  public void keyTyped(java.awt.event.KeyEvent evt)
  {
    if (lastWasPressed)
    {
      lastWasPressed = false;
      myConn.postKeyEvent(lastKeyCode, lastModifiers, evt.getKeyChar());
    }
    else if (!ignoreNextTyped && evt.getKeyCode() == 0 && evt.getKeyChar() != 0 && evt.getKeyChar() != java.awt.event.KeyEvent.CHAR_UNDEFINED)
    {
      // This is used for input methods. Specifically for Chinese on Windows
      myConn.postKeyEvent(0, 0, evt.getKeyChar());
    }
    ignoreNextTyped = false;
  }

  public void keyReleased(java.awt.event.KeyEvent evt)
  {
    if (lastWasPressed)
    {
      lastWasPressed = false;
      myConn.postKeyEvent(lastKeyCode, lastModifiers, (char)0);
    }
    // This is to fix a problem where the first character on an input was being ignored if we did a valid press release on a key
    // that didn't fire a keyTyped event. (like up/down/left/right)
    ignoreNextTyped = false;
  }

  public void mouseClicked(java.awt.event.MouseEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public void mouseEntered(java.awt.event.MouseEvent e)
  {
  }

  public void mouseExited(java.awt.event.MouseEvent e)
  {
  }

  public void mousePressed(java.awt.event.MouseEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public void mouseReleased(java.awt.event.MouseEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public void mouseDragged(java.awt.event.MouseEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public void mouseMoved(java.awt.event.MouseEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public static void ensureImageIsLoaded(java.awt.Image theImage)
  {
    // To actually get an image to fully load is a multi-step process.
    // First you have to create the image resource through the toolkit.
    // Second you have to call prepareImage on the toolkit and register
    // an ImageObserver to be notified of state changes.
    // Lastly you have to listen for the final loaded state change.
    final Object imageLock = new Object();
    final boolean[] imageStats = new boolean[2];
    imageStats[0] = false;
    java.awt.image.ImageObserver watcher = new java.awt.image.ImageObserver()
    {
      public boolean imageUpdate(java.awt.Image img, int infoflags,
          int x, int y, int width, int height)
      {
        synchronized (imageLock)
        {
          if (((infoflags & ALLBITS) == ALLBITS) ||
              ((infoflags & FRAMEBITS) == FRAMEBITS))
          {
            imageLock.notify();
            imageStats[0] = true;
            imageStats[1] = true;
            return false;
          }
          else if (((infoflags & ERROR) == ERROR) ||
              ((infoflags & ABORT) == ABORT))
          {
            imageLock.notify();
            imageStats[0] = true;
            imageStats[1] = false;
            return true;
          }
          else
          {
            return true;
          }
        }
      }
    };
    if (!java.awt.Toolkit.getDefaultToolkit().prepareImage(theImage,
        -1, -1, watcher))
    {
      synchronized (imageLock)
      {
        while (!imageStats[0])
        {
          try
          {
            imageLock.wait(5000);
          }
          catch (InterruptedException e)
          {}
        }
      }
    }
  }

  public void mouseWheelMoved(java.awt.event.MouseWheelEvent e)
  {
    myConn.postMouseEvent(e);
  }

  public void updatePaintContext(java.awt.Graphics2D g2, int x1, int y1, int w, int h, int argbTL, int argbTR, int argbBL, int argbBR)
  {
    // NOTE: We'd also want to not do this if music is playing...
    int x2 = x1 + w;
    int y2 = y1 + h;
    int alpha = (argbTL >> 24) & 0xFF;
    g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha/255.0f));
    if (argbTL != argbTR || argbTL != argbBL || argbTL != argbBR)
    {
      if (Math.abs(argbTL - argbTR) >= Math.abs(argbTL - argbBL) &&
          Math.abs(argbTL - argbTR) >= Math.abs(argbTL - argbBR))
        g2.setPaint(new java.awt.GradientPaint(x1, y1, new java.awt.Color(argbTL), x2, y1, new java.awt.Color(argbTR)));
      else if (Math.abs(argbTL - argbBL) < Math.abs(argbTL - argbBR))
        g2.setPaint(new java.awt.GradientPaint(x1, y1, new java.awt.Color(argbTL), x2, y2, new java.awt.Color(argbBR)));
      else
        g2.setPaint(new java.awt.GradientPaint(x1, y1, new java.awt.Color(argbTL), x1, y2, new java.awt.Color(argbBL)));
    }
    else
      g2.setColor(new java.awt.Color(argbTL));
  }
}
