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

public class MiniClientApplet extends java.applet.Applet implements java.awt.event.KeyListener
{
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

  public static int readInt(int pos, byte[] cmddata)
  {
    return ((cmddata[pos+0] & 0xFF)<<24)|((cmddata[pos+1] & 0xFF)<<16)|((cmddata[pos+2] & 0xFF)<<8)|(cmddata[pos+3] & 0xFF);
  }

  public static short readShort(int pos, byte[] cmddata)
  {
    return (short) (((cmddata[pos+0] & 0xFF)<<8)|(cmddata[pos+1] & 0xFF));
  }

  //private STBGFX STBGFX;
  public MiniClientApplet()
  {
    //	STBGFX = new STBGFX();
    chosenOne = this;
  }

  private java.awt.Frame f;
  private java.awt.Canvas c;
  private java.awt.image.BufferedImage backBuff;
  private java.awt.Graphics2D g2;
  private java.util.Map fontMap = new java.util.HashMap();
  private java.util.Map imageMap = new java.util.HashMap();
  private int handleCount = 1;
  private static MiniClientApplet chosenOne;

  public static MiniClientApplet getInstance()
  {
    if (chosenOne == null)
      chosenOne = new MiniClientApplet();
    return chosenOne;
  }

  public void init()
  {
    setLayout(new java.awt.BorderLayout());
    c = new java.awt.Canvas()
    {
      public void update(java.awt.Graphics g)
      {
        paint(g);
      }
      public void paint(java.awt.Graphics g)
      {
        //				System.out.println("REPAINTING IMAGE");
        if (backBuff != null)
          g.drawImage(backBuff, 0, 0, null);
      }
    };
    add(c, "Center");
    c.addComponentListener(new java.awt.event.ComponentAdapter()
    {
      public void componentResized(java.awt.event.ComponentEvent evt)
      {
        synchronized (inputQueue)
        {
          inputQueue.add(new java.awt.Dimension(c.getWidth(), c.getHeight()));
          inputQueue.notifyAll();
        }
      }
    });
    //setSize(720, 480);
    //f.setVisible(true);
    addKeyListener(this);
    c.addKeyListener(this);
    //backBuff = new java.awt.image.BufferedImage(720, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
  }
  public void start()
  {
    main(new String[] { getCodeBase().getHost() });
  }

  public int ExecuteGFXCommand(int cmd, int len, byte[] cmddata, int[] hasret)
  {
    hasret[0] = 0; // Nothing to return by default
    System.out.println("GFXCMD=" + cmd);
    switch(cmd)
    {
      case GFXCMD_INIT:
        hasret[0] = 1;
        /*f = new java.awt.Frame();
				f.setLayout(new java.awt.BorderLayout());
				c = new java.awt.Canvas();
				f.add(c, "Center");
				f.setSize(720, 480);
				f.setVisible(true);
				f.addWindowListener(new java.awt.event.WindowAdapter()
				{
					public void windowClosing(java.awt.event.WindowEvent evt)
					{
						System.exit(0);
					}
				});
				f.addKeyListener(this);*/
        return 1;
      case GFXCMD_DEINIT:
        f.dispose();
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
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.drawRect(x, y, width, height);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWRECT : " + len);
        }
        break;
      case GFXCMD_FILLRECT:
        // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL
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
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.fillRect(x, y, width, height);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_FILLRECT : " + len);
        }
        break;
      case GFXCMD_CLEARRECT:
        // x, y, width, height, thickness, argbTL, argbTR, argbBR, argbBL
        if(len==32)
        {
          int x, y, width, height, thickness,
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
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.drawOval(x, y, width, height);
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
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
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
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
          g2.setClip(clipX, clipY, clipW, clipH);
          g2.drawRoundRect(x, y, width, height, arcRadius, arcRadius);
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
          g2.setPaint(new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL),
              x + width, y + height, new java.awt.Color(argbBR)));
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((argbTL >> 24) & 0xFF) / 255.0f));
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
          java.awt.Image img = (java.awt.Image) imageMap.get(new Integer(handle));
          if (img != null)
          {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((blend >> 24) & 0xFF) / 255.0f));
            System.out.println("x=" + x + " y=" + y + " width=" + width + " height=" + height + " srcx=" + srcx + " srcy=" + srcy + " srcw=" + srcwidth + " srch=" + srcheight);
            g2.drawImage(img, x, y, x + width, y + height, srcx, srcy, srcx + srcwidth, srcy + srcheight, null);
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
        if(len==8)
        {
          int width, height;
          int imghandle = handleCount++;;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          java.awt.Image img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
          imageMap.put(new Integer(imghandle), img);
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
        if(len==8)
        {
          int width, height;
          //int imghandle = handleCount++;;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          //java.awt.Image img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
          //imageMap.put(new Integer(imghandle), img);
          // We don't actually use this, it's just for being sure we have enough room for allocation
          int imghandle = 1;
          //imghandle=STBGFX.GFX_loadImage(width, height);
          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGE : " + len);
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
          for(i=0;i<namelen;i++)
          {
            name.append((char) cmddata[4 + i]);
          }
          style=readInt(namelen+4, cmddata);
          size=readInt(namelen+8, cmddata);
          java.awt.Font fonty = new java.awt.Font(name.toString(), style, size);
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
          for (int i = 0; dataPos < len2; i++, dataPos += 4)
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
          java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(cmddata, 8, len2);

          handle = handleCount++;
          try
          {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(bais);
            imageMap.put(new Integer(handle), bi);
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
      default:
        return -1;
    }
    return 0;
  }

  public void keyPressed(java.awt.event.KeyEvent e)
  {
    System.out.println("keyevent:" + e);
    synchronized (inputQueue)
    {
      int myCode = e.getKeyCode();
      switch (e.getKeyCode())
      {
        case java.awt.event.KeyEvent.VK_UP:
          myCode = 14228; break;
        case java.awt.event.KeyEvent.VK_DOWN:
          myCode = 14229; break;
        case java.awt.event.KeyEvent.VK_RIGHT:
          myCode = 14231; break;
        case java.awt.event.KeyEvent.VK_LEFT:
          myCode = 14230; break;
        case java.awt.event.KeyEvent.VK_ENTER:
          myCode = 14245; break;
        case java.awt.event.KeyEvent.VK_PAGE_UP:
          myCode = 14240; break;
        case java.awt.event.KeyEvent.VK_PAGE_DOWN:
          myCode = 14241; break;
        case java.awt.event.KeyEvent.VK_HOME:
          myCode = 14267; break;
        case java.awt.event.KeyEvent.VK_ESCAPE:
          myCode = 14264; break;
      }
      inputQueue.add(new Integer(myCode));
      inputQueue.notifyAll();
    }
  }

  public void keyReleased(java.awt.event.KeyEvent e)
  {
    //System.out.println("keyevent:" + e);
  }

  public void keyTyped(java.awt.event.KeyEvent e)
  {
    //System.out.println("keyevent:" + e);
  }

  private static int ConnectionError=0;

  /*int fullrecv(int sock, void *vbuffer, int size)
	{
		int cur=0;
		int count=0;
		unsigned char *buffer=(unsigned char *) vbuffer;

		while(cur<size)
		{
			count=recv(sock,&buffer[cur],size-cur,0);
			if(count<=0)
			{
				perror("recv");
				fflush(stdin);
				fflush(stderr);
				return count;
			}
			cur+=count;
		}
		return size;
	}

/*	int GetMACAddress(unsigned char *buffer)
	{
		int fd;
		unsigned char *hw;
		struct ifreq ifreq;

		fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
		strcpy(ifreq.ifr_name, "eth0");
		ioctl(fd, SIOCGIFHWADDR, &ifreq);
		hw = ifreq.ifr_hwaddr.sa_data;
		buffer[0]=hw[0];
		buffer[1]=hw[1];
		buffer[2]=hw[2];
		buffer[3]=hw[3];
		buffer[4]=hw[4];
		buffer[5]=hw[5];
		close(fd);
		return 0;
	}
   */
  public static java.net.Socket EstablishServerConnection(String servername, int port)
  {
    int flag=1;
    int blockingmode;
    java.net.Socket sake = null;
    java.io.InputStream inStream = null;
    java.io.OutputStream outStream = null;
    try
    {
      sake = new java.net.Socket(servername, port);
      sake.setTcpNoDelay(true);
      outStream = sake.getOutputStream();
      inStream = sake.getInputStream();
      byte[] msg = { 1, 1, 2, 3, 4, 5, 6 };
      //GetMACAddress(&msg[1]);
      outStream.write(msg);
      /*			if(send(sc.sockfd,&msg[0],7,0)<0)
			{
				#ifdef DEBUGCLIENT
				perror("send ver+addr");
				#endif
				close(sc.sockfd);
				return NULL;
			}*/
      int rez = inStream.read();
      if(rez != 2)
      {
        System.out.println("Error with reply from server:" + rez);
        inStream.close();
        outStream.close();
        sake.close();
        return null;
      }
      System.out.println("Connection accepted by server");
      return sake;
    }
    catch (java.io.IOException e)
    {
      System.out.println("ERROR with socket connection: " + e);
      try
      {
        sake.close();
        inStream.close();
        outStream.close();
      }
      catch (Exception e1)
      {}
    }
    return null;
    /*		memset(&sc, 0, sizeof(ServerConnection));

		if((sc.sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
		{
			#ifdef DEBUGCLIENT
			perror("socket");
			#endif
			return NULL;
		}

		sc.server_addr.sin_family = AF_INET;
		sc.server_addr.sin_addr.s_addr = inet_addr(servername);
		sc.server_addr.sin_port = htons(port);

		blockingmode = fcntl(sc.sockfd, F_GETFL, NULL);
		if(blockingmode < 0)
		{
			#ifdef DEBUGCLIENT
			perror("fcntl F_GETFL");
			#endif
			close(sc.sockfd);
			return NULL;
		}

		blockingmode |= O_NONBLOCK;
		if(fcntl(sc.sockfd, F_SETFL, blockingmode)<0)
		{
			#ifdef DEBUGCLIENT
			perror("fcntl F_SETFL");
			#endif
			close(sc.sockfd);
			return NULL;
		}

		if(connect(sc.sockfd, (struct sockaddr *) &sc.server_addr,
		   sizeof(struct sockaddr)) < 0)
		{
			fd_set wfds;
			if(errno == EINPROGRESS)
			{
				// Wait until there is success, error or timeout...
				while(1)
				{
					struct timeval tv;
					tv.tv_sec = 2;  // Wait 2 seconds for connection
					tv.tv_usec = 0;
					int selret;
					FD_ZERO(&wfds);
					FD_SET(sc.sockfd, &wfds);
					selret = select(sc.sockfd+1, NULL, &wfds, NULL, &tv);
					if(selret<0)
					{
						// Possibly a timeout
						#ifdef DEBUGCLIENT
						perror("connect");
						#endif
						close(sc.sockfd);
						return NULL;
					}
					else
					{
						// We should be connected here...
						// TODO: maybe add better error code, FD_ISSET...
						break;
					}
				}
			}
		}

		blockingmode = fcntl(sc.sockfd, F_GETFL, NULL);
		if(blockingmode < 0)
		{
			#ifdef DEBUGCLIENT
			perror("fcntl F_GETFL");
			#endif
			return NULL;
		}

		blockingmode &= ~O_NONBLOCK;
		if(fcntl(sc.sockfd, F_SETFL, blockingmode)<0)
		{
			#ifdef DEBUGCLIENT
			perror("fcntl F_SETFL");
			#endif
			return NULL;
		}

		setsockopt(sc.sockfd,
				   IPPROTO_TCP,
				   TCP_NODELAY,
				   (char *) &flag,
					sizeof(int));
		// Send protocol version, mac address then see if we get back reply of 2
		{
			char msg[7] = { 1, 0, 0, 0, 0, 0, 0 };
			GetMACAddress(&msg[1]);
			if(send(sc.sockfd,&msg[0],7,0)<0)
			{
				#ifdef DEBUGCLIENT
				perror("send ver+addr");
				#endif
				close(sc.sockfd);
				return NULL;
			}
			if(fullrecv(sc.sockfd, &msg[0], 1)<1 || msg[0]!=2)
			{
				printf("Error with reply from server\n");
				close(sc.sockfd);
				return NULL;
			}
			printf("Connection accepted by server\n");
		}
		outsc = (ServerConnection *) malloc(sizeof(ServerConnection));
		if(outsc==NULL) return NULL;
		memcpy(outsc, &sc, sizeof(ServerConnection));
		return outsc;*/
  }

  /*	DropServerConnection(ServerConnection *sc)
	{
		// TODO: don't use that if we want it to work on windows
		close(sc->sockfd);
		free(sc);
	}
   */
  public static void GFXThread(String servername)
  {
    java.net.Socket sc = null;
    while(sc == null)
    {
      sc = EstablishServerConnection(servername, 31099);
      if(sc == null)
      {
        System.out.println("couldn't connect to input server, retrying in 5 secs.");
        try { Thread.sleep(5000);} catch (InterruptedException e){}
      }
    }

    System.out.println("Connected to gfx server");
    MiniClientApplet myGfx = MiniClientApplet.getInstance();
    byte[] cmd = new byte[4];
    byte[] cmdbuffer = new byte[4096];
    int command,len;
    int[] hasret = new int[1];
    int retval;
    byte[] retbuf = new byte[4];
    try
    {
      java.io.OutputStream os = sc.getOutputStream();
      java.io.DataInputStream is = new java.io.DataInputStream(sc.getInputStream());
      while (true)
      {
        is.readFully(cmd);

        command = (cmd[0] & 0xFF);
        len = ((cmd[1] & 0xFF) << 16) | ((cmd[2] & 0xFF)<<8) | (cmd[3] & 0xFF);
        if (len > 1000000)
        {
          System.out.println("Error processing GFX Command");
          break;
        }
        else if (len > cmdbuffer.length)
          cmdbuffer = new byte[len];
        is.readFully(cmdbuffer, 0, len);

        retval = myGfx.ExecuteGFXCommand(command, len, cmdbuffer, hasret);

        if(hasret[0] != 0)
        {
          retbuf[0] = (byte)((retval>>24) & 0xFF);
          retbuf[1] = (byte) ((retval>>16) & 0xFF);
          retbuf[2] = (byte) ((retval>>8) & 0xFF);
          retbuf[3] = (byte) ((retval>>0) & 0xFF);
          os.write(retbuf, 0, 4);
        }
      }
    }
    catch (Exception e)
    {
      System.out.println("Error w/ GFX Thread: " + e);
      e.printStackTrace();
    }

    ConnectionError = 1;
  }

  /*	int ProcessMediaCommand(int sockfd, char *cmdbuffer)
	{
		unsigned char cmd[4];
		unsigned int command,len;
		int hasret=0;
		unsigned int retval;
		unsigned char retbuf[4];

		if(fullrecv(sockfd, &cmd, 4)<4)
		{
			return -1;
		}

		command=cmd[0]<<8|cmd[1];
		len=cmd[2]<<8|cmd[3];
		if(len>65536)
		{
			return -2;
		}

		if(fullrecv(sockfd, cmdbuffer, len)<len)
		{
			return -3;
		}

		retval = ExecuteMediaCommand(command, len, cmdbuffer, &hasret);

		if(hasret)
		{
			retbuf[0]=retval>>24;
			retbuf[1]=retval>>16;
			retbuf[2]=retval>>8;
			retbuf[3]=retval>>0;
			send(sockfd,&retbuf[0],4,0);
			return 0;
		}
		return retval;
	}
   */
  public static int InputInit()
  {
    return 0;
  }

  public static java.util.Vector inputQueue = new java.util.Vector();

  public static Object ReadInput(int handle)
  {
    synchronized (inputQueue)
    {
      if (inputQueue.isEmpty())
      {
        try
        {
          inputQueue.wait(30000);
        }
        catch (InterruptedException e){}
      }
      else
      {
        Object i = inputQueue.firstElement();
        inputQueue.remove(0);
        return i;
      }
    }
    return null;
  }

  public static int CloseInput(int handle)
  {
    return 0;
  }
  public static void InputThread(String servername)
  {
    java.net.Socket sc = null;
    while(sc == null)
    {
      sc = EstablishServerConnection(servername, 31098);
      if(sc == null)
      {
        System.out.println("couldn't connect to input server, retrying in 5 secs.");
        try{Thread.sleep(5000);}catch(InterruptedException e){}
      }
    }

    System.out.println("Connected to input server");

    int inputhandle = InputInit();

    try
    {
      java.io.OutputStream os = sc.getOutputStream();
      java.io.DataInputStream is = new java.io.DataInputStream(sc.getInputStream());
      while (true)
      {
        byte[] cmd = new byte[2];
        byte[] reply = new byte[4];
        Object event = ReadInput(inputhandle);
        if (event instanceof Integer)
        {
          int evtCode = ((Integer) event).intValue();
          cmd[0]=(byte)((evtCode>>8)&0xFF);
          cmd[1]=(byte)((evtCode)&0xFF);
          os.write(cmd);
        }
        else if (event instanceof java.awt.Dimension)
        {
          cmd[0] = (byte)0xFF;
          cmd[1] = (byte)0xFF;
          os.write(cmd);
          java.awt.Dimension d = (java.awt.Dimension) event;
          os.write((byte)((d.width >> 8) & 0xFF));
          os.write((byte)(d.width & 0xFF));
          os.write((byte)((d.height >> 8) & 0xFF));
          os.write((byte)(d.height & 0xFF));
        }
        else
          continue;

        is.readInt();
      }
    }
    catch (Exception e)
    {
      System.out.println("Error w/ input connection: " + e);
    }

    ConnectionError = 1;
  }
  /*
	int MediaThread(void *data)
	{
		char *servername=(char *) data;
		char *cmdbuffer = (char *) malloc(65536);
		if(cmdbuffer==NULL)
		{
			printf("Couldn't allocate cmd buffer\n");
			ConnectionError=1;
			return -1;
		}

		while(1)
		{
			ServerConnection *sc = NULL;
			while(sc==NULL)
			{
				sc = EstablishServerConnection(servername, 31097);
				if(sc==NULL)
				{
					printf("couldn't connect to media server, retrying in 1 secs.\n");
					ACL_Delay(1000);
				}
			}

			#ifdef DEBUGCLIENT
			printf("Connected to media server\n");
			#endif

			while(1)
			{
				if(ProcessMediaCommand(sc->sockfd, cmdbuffer)<0)
				{
					// TODO: Should try to reconnect
					printf("Error processing Media Command\n");
					break;
				}
			}
			DropServerConnection(sc);
		}

		ConnectionError=1;
		return -1;
	}

/*	public static String FindServer()
	{
		ServerConnection sc;
		socklen_t client_len;
		int flag=1;

		memset(&sc, 0, sizeof(ServerConnection));
		memset(&dest_addr, 0, sizeof(struct sockaddr_in));

		// Accept input from all interfaces on a port that will be assigned
		sc.server_addr.sin_family = AF_INET;
		sc.server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
		sc.server_addr.sin_port = htons(0);

		if((sc.sockfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
		{
			#ifdef DEBUGCLIENT
			perror("socket");
			#endif
			return 0;
		}

		if(bind(sc.sockfd, (struct sockaddr *) &sc.server_addr,
			sizeof(struct sockaddr)) < 0)
		{
			#ifdef DEBUGCLIENT
			perror("bind");
			#endif
			return 0;
		}

		dest_addr.sin_family = AF_INET;
		dest_addr.sin_addr.s_addr = inet_addr("255.255.255.255");
		dest_addr.sin_port = htons(31100);
		int opt=1;
		struct timeval timeout;
		timeout.tv_sec=1;
		timeout.tv_usec=0;
		setsockopt(sc.sockfd, SOL_SOCKET, SO_RCVTIMEO, (void *)&timeout,
			sizeof(timeout));
		setsockopt(sc.sockfd, SOL_SOCKET, SO_BROADCAST, &opt, sizeof(int));
		char msg[10] = { 'S', 'T', 'V', 1, 0, 0, 0, 0, 0, 0}; // Query
		char inmsg[1024];
		GetMACAddress(&msg[4]);

		while(1)
		{
			client_len = sizeof(client_addr);
			int n = sendto(sc.sockfd,msg,10,0,(struct sockaddr *)(&dest_addr),
				sizeof(dest_addr));
			if(n<0)
			{
				#ifdef DEBUGCLIENT
				perror("Error sending message\n");
				#endif
				return 0;
			}


			n=-1;
			while(1)
			{
				n = recvfrom(sc.sockfd,inmsg,1024,0,(struct sockaddr *)(&client_addr),
					&client_len);
				printf("n : %d\n",n);
				if(n<0)
				{
					if(errno==EAGAIN) break;
					#ifdef DEBUGCLIENT
					perror("Error receiving message\n");
					#endif
					return 0;
				}
				if(n<4) continue;

				if(inmsg[0]=='S' && inmsg[1]=='T' && inmsg[2]=='V' && inmsg[3]==2 )
				{
					printf("%X\n",ntohl(client_addr.sin_addr.s_addr));
					close(sc.sockfd);
					int ip=ntohl(client_addr.sin_addr.s_addr);
					sprintf(servername,"%d.%d.%d.%d",(ip>>24)&0xFF,(ip>>16)&0xFF,(ip>>8)&0xFF,(ip>>0)&0xFF);
					return 1;
				}
			}
		}
	}
   */
  public static void main(String[] args)
  {
    final String servername = args[0]; // 3.3.3.3 total 12+3+1

    System.out.println("Starting SageTVMini Client to host: " + servername);

    /*    // Search for our server
		if(argc<2)
		{
			printf("Usage: miniclient ip\n");
			return -1;
		}*/

    // To show the logo while we search...
    //GFX_init();
    /*		if((servername = FindServer()) == null)
		{
			System.out.println("Couldn't find server");
			return;
		}*/
    //GFX_deinit();
    Thread t = new Thread("GFX-" + servername)
    {
      public void run()
      {
        GFXThread(servername);
      }
    };
    t.start();
    t = new Thread("Input-" + servername)
    {
      public void run()
      {
        InputThread(servername);
      }
    };
    t.start();
    /*		Thread t = new Thread("Media-" + servername)
		{
			public void run()
			{
				MediaThread(servername);
			}
		};
		t.start();*/
    System.out.println("Starting main loop");
    /*		while (true)
		{
			if (ConnectionError != 0)
			{
				// TODO: tell other threads to exit so they can do it more cleanly
				System.out.println("Connection error " + ConnectionError + ", exiting");
				System.exit(-1);
			}
			try
			{
				Thread.sleep(200);
			}
			catch (InterruptedException e){}
		}*/
  }

  public void update(java.awt.Graphics g)
  {
  }
  public void paint(java.awt.Graphics g)
  {
  }
}
