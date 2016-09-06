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

/**
 *
 * @author Narflex
 */
public class DirectX9GFXCMD extends GFXCMD2
{
  private static final int ELLIPTICAL_DIVISIONS = 50;
  private static final int RECT_CORNER_DIVISIONS = 32;
  private static final int MIN_DIVISIONS = 4;
  private static final int VERTEX_CACHE_SIZE = 8192;
  private static final int VRAM_USAGE_PERCENT = 80;
  private static DirectX9GFXCMD defaultDX9Renderer;
  private static final boolean GFX_DBG = false;

  /** Creates a new instance of DirectX9GFXCMD */
  public DirectX9GFXCMD(MiniClientConnection inConn)
  {
    super(inConn);
    srcXCache = new float[VERTEX_CACHE_SIZE];
    srcYCache = new float[VERTEX_CACHE_SIZE];
    srcWCache = new float[VERTEX_CACHE_SIZE];
    srcHCache = new float[VERTEX_CACHE_SIZE];
    dstXCache = new float[VERTEX_CACHE_SIZE];
    dstYCache = new float[VERTEX_CACHE_SIZE];
    dstWCache = new float[VERTEX_CACHE_SIZE];
    dstHCache = new float[VERTEX_CACHE_SIZE];
    cCache = new int[VERTEX_CACHE_SIZE];
    nativeTexturePtrs = new java.util.HashMap(); // Maps 32-bit ints to 64-bit long native pointers
    nativeTexturePtrSizes = new java.util.HashMap(); // stores size used by each native texture
    maxVramUsage = Long.parseLong(MiniClient.myProperties.getProperty("ui/max_d3d_vram_usage", "150000000"));

    if (!loadedDX9Lib)
    {
      sage.Native.loadLibrary("SageTVDX93D");
      loadedDX9Lib = true;
      registerMiniClientNatives0();
    }
    defaultDX9Renderer = this;
  }

  public void close()
  {
    try
    {
      super.close();
    }
    catch (Throwable t)
    {
      System.out.println("Error in parent close of:" + t);
      t.printStackTrace();
    }
    // NOTE: RELEASE ALL OF THE NATIVE IMAGE HANDLES WE HAVE!!!
    java.util.Iterator walker = nativeTexturePtrs.values().iterator();
    while (walker.hasNext())
    {
      freeD3DTexturePointer0(((Long) walker.next()).longValue());
    }
    nativeTexturePtrs.clear();
    if (backSurf != 0)
      freeD3DTexturePointer0(backSurf);
    backSurf = 0;
    nativeTexturePtrs.clear();
    nativeTexturePtrSizes.clear();
    imageCacheSize = 0;
    cleanupDX9SageRenderer0();
  }

  private static boolean loadedDX9Lib = false;
  private java.awt.Canvas c;

  private long pD3DObject;
  private long pD3DDevice;
  private boolean rerenderedDL = false;
  private int bufferWidth;
  private int bufferHeight;
  private long pD3DDevMgr;
  private long pD3DDevMgrToken;
  private long hD3DMgrHandle;

  private float[] srcXCache;
  private float[] srcYCache;
  private float[] srcWCache;
  private float[] srcHCache;
  private float[] dstXCache;
  private float[] dstYCache;
  private float[] dstWCache;
  private float[] dstHCache;
  private int[] cCache;
  private int handleCount = 2;

  // Currently we always use MPlayer in the MiniClient
  private boolean asyncMplayerRender = true;

  private java.util.Map nativeTexturePtrs;
  private java.util.Map nativeTexturePtrSizes;

  private long videoMemoryLimit;
  private long maxVramUsage;
  private java.awt.Dimension lastMasterSize;
  private boolean beganScene;

  private java.util.Map imageLoadBuffMap = new java.util.Hashtable();
  private static class ImageLoadData
  {
    java.nio.ByteBuffer imageLoadBuff;
    int imageLoadBuffHandle;
    java.awt.Dimension imageLoadBuffSize;
  }

  private long backSurf;

  private long videoSurface;
  private boolean inVmr9Callback;
  private boolean inFrameNow;

  private double[] currCoords;
  private java.util.Stack matrixStack = new java.util.Stack();;

  private int fullClearStatus = 0; // -1 no; 0 clear not found yet; 1 clear found
  private int lastFullClearStatus = 0; // -1 no; 0 clear not found yet; 1 clear found

  public int ExecuteGFXCommand(int cmd, int len, byte[] cmddata, int[] hasret)
  {
    len -= 4; // for the 4 byte header
    hasret[0] = 0; // Nothing to return by default
    //System.out.println("GFXCMD=" + ((cmd >= 0 && cmd < CMD_NAMES.length) ? CMD_NAMES[cmd] : ("UnknownCmd " + cmd)));
    //System.out.println("GFXCMD=" + cmd);

    if (c != null)
    {
      switch(cmd)
      {
        case GFXCMD_INIT:
        case GFXCMD_DEINIT:
        case GFXCMD_STARTFRAME:
        case GFXCMD_FLIPBUFFER:
          c.setCursor(null);
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
        case GFXCMD_DRAWTEXTUREDDIFFUSE:
        case GFXCMD_PUSHTRANSFORM:
        case GFXCMD_POPTRANSFORM:
        case GFXCMD_DRAWLINE:
        case GFXCMD_LOADIMAGE:
        case GFXCMD_LOADIMAGETARGETED:
        case GFXCMD_UNLOADIMAGE:
        case GFXCMD_LOADFONT:
        case GFXCMD_UNLOADFONT:
        case GFXCMD_SETTARGETSURFACE:
        case GFXCMD_CREATESURFACE:
          break;
        case GFXCMD_PREPIMAGE:
        case GFXCMD_LOADIMAGELINE:
        case GFXCMD_LOADIMAGECOMPRESSED:
        case GFXCMD_XFMIMAGE:
        case GFXCMD_LOADCACHEDIMAGE:
        case GFXCMD_PREPIMAGETARGETED:
          if (!cursorHidden)
            c.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
          break;
      }
    }
    switch(cmd)
    {
      case GFXCMD_INIT:
        hasret[0] = 1;
        //backBuff = new java.awt.image.BufferedImage(720, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int windowTitleStyle = 0;
        try
        {
          windowTitleStyle = Integer.parseInt(MiniClient.myProperties.getProperty("window_title_style", "0"));
        }
        catch (NumberFormatException e){}
        if (!"true".equals(MiniClient.myProperties.getProperty("enable_custom_title_bar", MiniClient.MAC_OS_X ? "false" : "true")))
          windowTitleStyle = 10; // platform default
        f = new MiniClientWindow(myConn.getWindowTitle(), windowTitleStyle);
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
            //							videoCanvas.setBounds(parent.getInsets().left, parent.getInsets().top,
            //								parent.getWidth() - parent.getInsets().left - parent.getInsets().right,
            //								parent.getHeight() - parent.getInsets().top - parent.getInsets().bottom);//vf.setBounds(x, y, w, h);
            //System.out.println("LAYOUT frame bounds=" + f.getBounds() + " videoBounds=" + videoBounds + " parentBounds=" + parent.getBounds());
          }
        };
        f.getContentPane().setLayout(layer);
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
        f.setFocusTraversalKeysEnabled(false);
        //				videoCanvas = new VideoCanvas();
        c = new java.awt.Canvas()
        {
          public void update(java.awt.Graphics g)
          {
            paint(g);
          }
          public void paint(java.awt.Graphics g)
          {
            //				System.out.println("REPAINTING IMAGE");
            if (firstFrameDone)
            {
              g.setClip(null);
              g.setColor(java.awt.Color.black);
              g.fillRect(0, 0, getWidth(), getHeight());
              java.awt.EventQueue.invokeLater(new Runnable()
              {
                public void run()
                {
                  Thread t = new Thread()
                  {
                    public void run()
                    {
                      try{Thread.sleep(100);}catch(Exception e){}
                      System.out.println("REPAINTING!!!");
                      synchronized (DirectX9GFXCMD.this)
                      {
                        if (!beganScene)
                        {
                          beginScene0(getWidth(), getHeight());
                          beganScene = true;
                        }
                        setRenderTarget0(0);
                        //										java.awt.Rectangle clipRect = g.getClipBounds();
                        int x = 0;
                        int y = 0;
                        int w = c.getWidth();
                        int h = c.getHeight();
                        //										if (clipRect != null)
                        {
                          //											x = clipRect.x;
                          //											y = clipRect.y;
                          //											w = clipRect.width;
                          //											h = clipRect.height;
                        }
                        textureMap0(backSurf, x, y, w, h, w, y, w, h, 0,
                            java.awt.AlphaComposite.SRC, 0xFFFFFFFF, false);
                        if (beganScene)
                          endScene0();
                        present0(x, y, w, h);
                        beganScene = false;
                      }
                    }
                  };
                  t.start();
                }
              });
            }
            else if (!"127.0.0.1".equals(myConn.getServerName()))
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
              String str2 = "the server: " + myConn.getServerName();
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
            else
            {
              g.setColor(java.awt.Color.black);
              g.fillRect(0, 0, getWidth(), getHeight());
            }
          }
        };
        c.setBackground(java.awt.Color.black);
        c.setFocusTraversalKeysEnabled(false);
        //				videoCanvas.setFocusTraversalKeysEnabled(false);
        f.getContentPane().add(c);
        //				f.getContentPane().add(videoCanvas);
        try
        {
          java.awt.Image frameIcon = java.awt.Toolkit.getDefaultToolkit().createImage(getClass().getClassLoader().getResource("images/tvicon.gif"));
          ensureImageIsLoaded(frameIcon);
          f.setIconImage(frameIcon);
        }
        catch (Exception e)
        {
          System.out.println("ERROR:" + e);
          e.printStackTrace();
        }
        f.addWindowListener(new java.awt.event.WindowAdapter()
        {
          public void windowClosing(java.awt.event.WindowEvent evt)
          {
            if (!f.isFullScreen() || System.getProperty("os.name").toLowerCase().indexOf("windows") != -1)
            {
              MiniClient.myProperties.setProperty("main_window_width", Integer.toString(f.getWidth()));
              MiniClient.myProperties.setProperty("main_window_height", Integer.toString(f.getHeight()));
              MiniClient.myProperties.setProperty("main_window_x", Integer.toString(f.getX()));
              MiniClient.myProperties.setProperty("main_window_y", Integer.toString(f.getY()));
            }
            myConn.close();
            /*						try
						{
							if (myConn.getMediaCmd().getPlaya() != null)
							{
								myConn.getMediaCmd().getPlaya().stop();
								myConn.getMediaCmd().getPlaya().free();
							}
						}catch (Exception e){}
						System.exit(0);*/
            f.dispose();
          }
        });
        c.addComponentListener(new java.awt.event.ComponentAdapter()
        {
          public void componentResized(java.awt.event.ComponentEvent evt)
          {
            myConn.postResizeEvent(new java.awt.Dimension(c.getWidth(), c.getHeight()));
          }
        });
        f.addKeyListener(this);
        c.addKeyListener(this);
        //				videoCanvas.addKeyListener(this);
        //f.addMouseListener(this);
        f.addMouseWheelListener(this);
        c.addMouseListener(this);
        //				videoCanvas.addMouseListener(this);
        if (ENABLE_MOUSE_MOTION_EVENTS)
        {
          //f.addMouseMotionListener(this);
          c.addMouseMotionListener(this);
          //					videoCanvas.addMouseMotionListener(this);
        }
        int frameX = 100;
        int frameY = 100;
        int frameW = 720;
        int frameH = 480;
        try
        {
          frameW = Integer.parseInt(MiniClient.myProperties.getProperty("main_window_width", "720"));
          frameH = Integer.parseInt(MiniClient.myProperties.getProperty("main_window_height", "480"));
          frameX = Integer.parseInt(MiniClient.myProperties.getProperty("main_window_x", "100"));
          frameY = Integer.parseInt(MiniClient.myProperties.getProperty("main_window_y", "100"));
        }
        catch (NumberFormatException e){}
        java.awt.Point newPos = new java.awt.Point(frameX, frameY);
        boolean foundScreen = sage.UIUtils.isPointOnAScreen(newPos);
        if (!foundScreen)
        {
          newPos.x = 150;
          newPos.y = 150;
        }
        f.setSize(Math.max(frameW, 320), Math.max(frameH, 240));
        f.setLocation(newPos);
        if (MiniClient.fsStartup)
          f.setFullScreen(true);
        MiniClient.hideSplash();
        f.setVisible(true);
        java.awt.Dimension scrSize = myConn.getReportedScrSize();
        bufferWidth = scrSize.width;
        bufferHeight = scrSize.height;
        boolean rv = initDX9SageRenderer0(scrSize.width, scrSize.height, sage.UIUtils.getHWND(c));
        if (rv)
        {
          long vram = getAvailableVideoMemory0();
          videoMemoryLimit = vram*VRAM_USAGE_PERCENT/100;
          videoMemoryLimit = Math.min(videoMemoryLimit, maxVramUsage);
          backSurf = createD3DRenderTarget0(scrSize.width, scrSize.height);
        }
        System.out.println("Created back drawing surface " + backSurf + " of size " + scrSize.width + "x" + scrSize.height);
        return rv ? 1 : 0;
      case GFXCMD_DEINIT:
        f.dispose();
        // NOTE: RELEASE ALL OF THE NATIVE IMAGE HANDLES WE HAVE!!!
        java.util.Iterator walker = nativeTexturePtrs.values().iterator();
        while (walker.hasNext())
        {
          freeD3DTexturePointer0(((Long) walker.next()).longValue());
        }
        nativeTexturePtrs.clear();
        if (backSurf != 0)
          freeD3DTexturePointer0(backSurf);
        backSurf = 0;
        nativeTexturePtrs.clear();
        nativeTexturePtrSizes.clear();
        imageCacheSize = 0;
        cleanupDX9SageRenderer0();
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
          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }

            srcXCache[0] = x;
            srcXCache[1] = x + width;
            srcXCache[2] = x + width;
            srcXCache[3] = x;
            srcYCache[0] = y;
            srcYCache[1] = y;
            srcYCache[2] = y + thickness;
            srcYCache[3] = y + thickness;
            cCache[0] = argbTL;
            cCache[1] = argbTR;
            cCache[2] = argbTR;
            cCache[3] = argbTL;
            fillShape(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
            srcXCache[0] = x;
            srcXCache[1] = x + width;
            srcXCache[2] = x + width;
            srcXCache[3] = x;
            srcYCache[0] = y + height - thickness;
            srcYCache[1] = y + height - thickness;
            srcYCache[2] = y + height;
            srcYCache[3] = y + height;
            cCache[0] = argbBL;
            cCache[1] = argbBR;
            cCache[2] = argbBR;
            cCache[3] = argbBL;
            fillShape(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
            srcXCache[0] = x;
            srcXCache[1] = x + thickness;
            srcXCache[2] = x + thickness;
            srcXCache[3] = x;
            srcYCache[0] = y + thickness;
            srcYCache[1] = y + thickness;
            srcYCache[2] = y + height - thickness;
            srcYCache[3] = y + height - thickness;
            cCache[0] = argbTL;
            cCache[1] = argbTL;
            cCache[2] = argbBL;
            cCache[3] = argbBL;
            fillShape(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
            srcXCache[0] = x + width - thickness;
            srcXCache[1] = x + width;
            srcXCache[2] = x + width;
            srcXCache[3] = x + width - thickness;
            srcYCache[0] = y + thickness;
            srcYCache[1] = y + thickness;
            srcYCache[2] = y + height - thickness;
            srcYCache[3] = y + height - thickness;
            cCache[0] = argbTR;
            cCache[1] = argbTR;
            cCache[2] = argbBR;
            cCache[3] = argbBR;
            fillShape(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
          }
          fullClearStatus = -1;
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

          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }
            srcXCache[0] = x;
            srcXCache[1] = x + width;
            srcXCache[2] = x + width;
            srcXCache[3] = x;
            srcYCache[0] = y;
            srcYCache[1] = y;
            srcYCache[2] = y + height;
            srcYCache[3] = y + height;
            cCache[0] = argbTL;
            cCache[1] = argbTR;
            cCache[2] = argbBR;
            cCache[3] = argbBL;
            fillShape(srcXCache, srcYCache, cCache, 0, 4, null, currCoords);
          }
          fullClearStatus = -1;
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
          cCache[0] = argbTL;
          cCache[1] = argbTR;
          cCache[2] = argbBR;
          cCache[3] = argbBL;
          if (GFX_DBG) System.out.println("GFXCMD_CLEARRECT " + x + "," + y + "," + width + "," + height + " color=0x" + Integer.toString(argbTL, 16));
          fillShape0(null, null, cCache, -1, 4, new java.awt.Rectangle(x, y, width, height));
          if (fullClearStatus == 0)
            fullClearStatus = 1;
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

          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }
            int circum = (int) ((width + height)*Math.PI/2);
            int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum, ELLIPTICAL_DIVISIONS/2));
            // move ourself around the circle to get each point
            int numVerts = numDivs*2;
            if (argbTL == argbBR)
              java.util.Arrays.fill(cCache, 0, numVerts, argbTL);
            float centerX = x + width/2.0f;
            float centerY = y + height/2.0f;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j*2] = (float)Math.cos((2*Math.PI*j)/(numDivs-1))*width/2.0f + centerX;
              srcYCache[j*2] = (float)Math.sin((2*Math.PI*j)/(numDivs-1))*height/2.0f + centerY;
              srcXCache[j*2+1] = (float)Math.cos((2*Math.PI*j)/(numDivs-1))*(width/2.0f -
                  thickness) + centerX;
              srcYCache[j*2+1] = (float)Math.sin((2*Math.PI*j)/(numDivs-1))*(height/2.0f -
                  thickness) + centerY;
            }
            if (argbTL != argbBR)
            {
              for (int j = 0; j < numVerts; j++)
                cCache[j] = getGradientColor(x, y, width, height, argbTL, argbBR,
                    srcXCache[j], srcYCache[j]);
            }
            int shapeMinX = clipX;
            int shapeMinY = clipY;
            int shapeMaxX = clipX + clipW;
            int shapeMaxY = clipY + clipH;
            for (int j = 0; j < numVerts; j++)
            {
              if (srcXCache[j] < shapeMinX)
                srcXCache[j] = shapeMinX;
              else if (srcXCache[j] > shapeMaxX)
                srcXCache[j] = shapeMaxX;
              if (srcYCache[j] < shapeMinY)
                srcYCache[j] = shapeMinY;
              else if (srcYCache[j] > shapeMaxY)
                srcYCache[j] = shapeMaxY;
            }
            fillShape(srcXCache, srcYCache, cCache, 1, numVerts, null, currCoords);
          }
          fullClearStatus = -1;
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

          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }

            int circum = (int) ((width + height)*Math.PI/2.0f);
            int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS));
            // move ourself around the circle to get each point
            int numVerts = numDivs + 1;
            float centerX = x + width/2.0f;
            float centerY = y + height/2.0f;
            srcXCache[0] = centerX;
            srcYCache[0] = centerY;
            if (argbTL == argbBR)
              java.util.Arrays.fill(cCache, 0, numVerts, argbTL);
            else
              cCache[0] = getGradientColor(x, y, width, height, argbTL, argbBR,
                  srcXCache[0], srcYCache[0]);
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j+1] = (float)Math.cos(-(2*Math.PI*j)/(numDivs-1))*width/2.0f + centerX;
              srcYCache[j+1] = (float)Math.sin(-(2*Math.PI*j)/(numDivs-1))*height/2.0f + centerY;
              if (argbTL != argbBR)
                cCache[j+1] = getGradientColor(x, y, width, height, argbTL, argbBR,
                    srcXCache[j+1], srcYCache[j+1]);
            }
            int shapeMinX = clipX;
            int shapeMinY = clipY;
            int shapeMaxX = clipX + clipW;
            int shapeMaxY = clipY + clipH;
            for (int j = 0; j < numVerts; j++)
            {
              if (srcXCache[j] < shapeMinX)
                srcXCache[j] = shapeMinX;
              else if (srcXCache[j] > shapeMaxX)
                srcXCache[j] = shapeMaxX;
              if (srcYCache[j] < shapeMinY)
                srcYCache[j] = shapeMinY;
              else if (srcYCache[j] > shapeMaxY)
                srcYCache[j] = shapeMaxY;
            }
            fillShape(srcXCache, srcYCache, cCache, 0, numVerts, null, currCoords);
          }
          fullClearStatus = -1;
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

          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }
            // limit the corner arc based on overall width/height
            int circum = (int) (arcRadius*Math.PI/4);
            int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS/8));
            // move ourself around the circle to get each point
            int numVerts = numDivs*8 + 2;
            if (argbTL == argbBR)
              java.util.Arrays.fill(cCache, 0, numVerts, argbTL);
            // top left
            float centerX = x + arcRadius/2.0f;
            float centerY = y + arcRadius/2.0f;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j*2] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*arcRadius/2.0f + centerX;
              srcYCache[j*2] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*arcRadius/2.0f + centerY;
              srcXCache[j*2+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*(arcRadius/2.0f-thickness) + centerX;
              srcYCache[j*2+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + 3*Math.PI/2)*(arcRadius/2.0f-thickness) + centerY;
            }
            // bottom left
            centerX = x + arcRadius/2.0f;
            centerY = y + height - arcRadius/2.0f;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j*2+2*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerX;
              srcYCache[j*2+2*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerY;
              srcXCache[j*2+2*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*(arcRadius/2.0f-thickness) + centerX;
              srcYCache[j*2+2*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI)*(arcRadius/2.0f-thickness) + centerY;
            }
            // bottom right
            centerX = x + width - arcRadius/2.0f;
            centerY = y + height - arcRadius/2.0f;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j*2+4*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*arcRadius/2.0f + centerX;
              srcYCache[j*2+4*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*arcRadius/2.0f + centerY;
              srcXCache[j*2+4*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*(arcRadius/2.0f-thickness) + centerX;
              srcYCache[j*2+4*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1) + Math.PI/2)*(arcRadius/2.0f-thickness) + centerY;
            }
            // top right
            centerX = x + width - arcRadius/2.0f;
            centerY = y + arcRadius/2.0f;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j*2+6*numDivs] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1))*arcRadius/2.0f + centerX;
              srcYCache[j*2+6*numDivs] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1))*arcRadius/2.0f + centerY;
              srcXCache[j*2+6*numDivs+1] = (float)Math.cos(-((Math.PI*j)/2)/(numDivs-1))*(arcRadius/2.0f-thickness) + centerX;
              srcYCache[j*2+6*numDivs+1] = (float)Math.sin(-((Math.PI*j)/2)/(numDivs-1))*(arcRadius/2.0f-thickness) + centerY;
            }
            srcXCache[8*numDivs] = srcXCache[0];
            srcYCache[8*numDivs] = srcYCache[0];
            srcXCache[8*numDivs+1] = srcXCache[1];
            srcYCache[8*numDivs+1] = srcYCache[1];
            if (argbTL != argbBR)
            {
              for (int j = 0; j < numVerts; j++)
                cCache[j] = getGradientColor(x, y, width, height, argbTL, argbBR,
                    srcXCache[j], srcYCache[j]);
            }
            int shapeMinX = clipX;
            int shapeMinY = clipY;
            int shapeMaxX = clipX + clipW;
            int shapeMaxY = clipY + clipH;
            for (int j = 0; j < numVerts; j++)
            {
              if (srcXCache[j] < shapeMinX)
                srcXCache[j] = shapeMinX;
              else if (srcXCache[j] > shapeMaxX)
                srcXCache[j] = shapeMaxX;
              if (srcYCache[j] < shapeMinY)
                srcYCache[j] = shapeMinY;
              else if (srcYCache[j] > shapeMaxY)
                srcYCache[j] = shapeMaxY;
            }
            fillShape(srcXCache, srcYCache, cCache, 1, numVerts, null, currCoords);
          }
          fullClearStatus = -1;
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

          synchronized (this)
          {
            if (!beganScene)
            {
              if (!beginScene0(c.getWidth(), c.getHeight()))
                break;
              beganScene = true;
            }
            int circum = (int) (arcRadius*Math.PI/4);
            int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS));
            // move ourself around the circle to get each point
            int numVerts = 4*numDivs + 2;
            int shapeMinY = y;
            int shapeMinX = x;
            int shapeMaxX = x + width;
            int shapeMaxY = y + height;
            float centerX = arcRadius/2.0f + shapeMinX;
            float centerY = arcRadius/2.0f + shapeMinY;
            srcXCache[0] = (shapeMinX + shapeMaxX)/2;
            srcYCache[0] = (shapeMinY + shapeMaxY)/2;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*arcRadius/2.0f + centerX;
              srcYCache[j+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*arcRadius/2.0f + centerY;
            }
            centerX = arcRadius/2.0f + shapeMinX;
            centerY = height - arcRadius/2.0f + shapeMinY;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j+1+numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerX;
              srcYCache[j+1+numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerY;
            }
            centerX = width - arcRadius/2.0f + shapeMinX;
            centerY = height - arcRadius/2.0f + shapeMinY;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j+1+2*numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*arcRadius/2.0f + centerX;
              srcYCache[j+1+2*numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*arcRadius/2.0f + centerY;
            }
            centerX = width - arcRadius/2.0f + shapeMinX;
            centerY = arcRadius/2.0f + shapeMinY;
            for (int j = 0; j < numDivs; j++)
            {
              srcXCache[j+numDivs*3+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1))*arcRadius/2.0f + centerX;
              srcYCache[j+numDivs*3+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1))*arcRadius/2.0f + centerY;
            }
            srcXCache[numVerts - 1] = srcXCache[1];
            srcYCache[numVerts - 1] = srcYCache[1];
            float orgShapeX = shapeMinX;
            float orgShapeY = shapeMinY;
            shapeMinX = clipX;
            shapeMaxX = clipX + clipW;
            shapeMinY = clipY;
            shapeMaxY = clipY + clipH;
            if (argbTL == argbBR)
              java.util.Arrays.fill(cCache, 0, numVerts, argbTL);
            for (int j = 0; j < numVerts; j++)
            {
              if (srcXCache[j] < shapeMinX)
                srcXCache[j] = shapeMinX;
              else if (srcXCache[j] > shapeMaxX)
                srcXCache[j] = shapeMaxX;
              if (srcYCache[j] < shapeMinY)
                srcYCache[j] = shapeMinY;
              else if (srcYCache[j] > shapeMaxY)
                srcYCache[j] = shapeMaxY;
              if (argbTL != argbBR)
                cCache[j] = getGradientColor(x, y, width, height, argbTL, argbBR,
                    srcXCache[j], srcYCache[j]);
            }
            fillShape(srcXCache, srcYCache, cCache, 0, numVerts, null, currCoords);
            /*						// limit the corner arc based on overall width/height
						int circum = (int) ((arcRadius)*Math.PI/4);
						int numDivs = Math.max(MIN_DIVISIONS, Math.min(circum/3, RECT_CORNER_DIVISIONS));
						// move ourself around the circle to get each point
						int numVerts = 4*numDivs + 2;
						float centerX = arcRadius/2.0f;
						float centerY = arcRadius/2.0f;
						srcXCache[0] = x + width/2;
						srcYCache[0] = y + height/2;
						cCache[0] = getGradientColor(x, y, width, height, argbTL, argbBR, srcXCache[0], srcYCache[0]);
						for (int j = 0; j < numDivs; j++)
						{
							srcXCache[j+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*arcRadius/2.0f + centerX;
							srcYCache[j+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + 3*Math.PI/2.0f)*arcRadius/2.0f + centerY;
							cCache[j+1] = getGradientColor(x, y, width, height, argbTL, argbBR,
								srcXCache[j+1], srcYCache[j+1]);
						}
						centerX = x + arcRadius/2.0f;
						centerY = y + height - arcRadius/2.0f;
						for (int j = 0; j < numDivs; j++)
						{
							srcXCache[j+1+numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerX;
							srcYCache[j+1+numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI)*arcRadius/2.0f + centerY;
							cCache[j+1+numDivs] = getGradientColor(x, y, width, height, argbTL, argbBR,
									srcXCache[j+1+numDivs], srcYCache[j+1+numDivs]);
						}
						centerX = x + width - arcRadius/2.0f;
						centerY = y + height - arcRadius/2.0f;
						for (int j = 0; j < numDivs; j++)
						{
							srcXCache[j+1+2*numDivs] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*arcRadius/2.0f + centerX;
							srcYCache[j+1+2*numDivs] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1) + Math.PI/2.0f)*arcRadius/2.0f + centerY;
							cCache[j+1+2*numDivs] = getGradientColor(x, y, width, height, argbTL, argbBR,
									srcXCache[j+1+2*numDivs], srcYCache[j+1+2*numDivs]);
						}
						centerX = x + width - arcRadius/2.0f;
						centerY = y + arcRadius/2.0f;
						for (int j = 0; j < numDivs; j++)
						{
							srcXCache[j+numDivs*3+1] = (float)Math.cos(-((Math.PI*j)/2.0f)/(numDivs-1))*arcRadius/2.0f + centerX;
							srcYCache[j+numDivs*3+1] = (float)Math.sin(-((Math.PI*j)/2.0f)/(numDivs-1))*arcRadius/2.0f + centerY;
							cCache[j+numDivs*3+1] = getGradientColor(x, y, width, height, argbTL, argbBR,
									srcXCache[j+numDivs*3+1], srcYCache[j+numDivs*3+1]);
						}
						srcXCache[numVerts - 1] = srcXCache[1];
						srcYCache[numVerts - 1] = srcYCache[1];
						cCache[numVerts - 1] = cCache[1];
						fillShape0(srcXCache, srcYCache, cCache, 0, numVerts, new java.awt.Rectangle(clipX, clipY, clipW, clipH), currCoords);*/
          }
          fullClearStatus = -1;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_FILLROUNDRECT : " + len);
        }
        break;
      case GFXCMD_DRAWTEXT:
        /*				// x, y, len, text, handle, argb, clipX, clipY, clipW, clipH
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
				}*/
        break;
      case GFXCMD_DRAWTEXTURED:
        // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend
        if(len==40)
        {
          int handle, blend;
          dstXCache[0]=readInt(0, cmddata);
          dstYCache[0]=readInt(4, cmddata);
          dstWCache[0]=readInt(8, cmddata);
          dstHCache[0]=readInt(12, cmddata);
          handle=readInt(16, cmddata);
          srcXCache[0]=readInt(20, cmddata);
          srcYCache[0]=readInt(24, cmddata);
          srcWCache[0]=readInt(28, cmddata);
          srcHCache[0]=readInt(32, cmddata);
          blend=readInt(36, cmddata);
          int scaleHint = 1; // 0 is nearest neighbor, 1 is linear scaling
          if (dstWCache[0] < 0 || (srcWCache[0] == dstWCache[0] && srcHCache[0] == dstHCache[0]))
            scaleHint = 0;
          if (GFX_DBG) System.out.println("GFXCMD_DRAWTEXTURED src=" + srcXCache[0] + "," + srcYCache[0] + "," + srcWCache[0] + "," + srcHCache[0] +
              " dst=" + dstXCache[0] + "," + dstYCache[0] + "," + dstWCache[0] + "," + dstHCache[0] + " handle=" + handle + " blend=0x" + Integer.toString(blend, 16));

          // negative height indicates SRC compositing mode
          // negative width indicates blended text rendering
          Long nativePtr = (Long) nativeTexturePtrs.get(new Integer(handle));
          if (nativePtr == null)
          {
            System.out.println("ERROR invalid handle passed for texture rendering of: " + handle);
            abortRenderCycle = true;
          }
          else
          {
            myConn.registerImageAccess(handle);
            if (dstWCache[0] != 0 && dstHCache[0] != 0)
            {
              synchronized (this)
              {
                if (!beganScene)
                {
                  if (!beginScene0(c.getWidth(), c.getHeight()))
                    break;
                  beganScene = true;
                }
                dstWCache[0] = Math.abs(dstWCache[0]);
                int blendMode = dstHCache[0] < 0 ? java.awt.AlphaComposite.SRC : java.awt.AlphaComposite.SRC_OVER;
                dstHCache[0] = Math.abs(dstHCache[0]);
                cCache[0] = blend;
                if (currCoords == null || blendMode == java.awt.AlphaComposite.SRC)
                  textureMap0(nativePtr.longValue(), srcXCache[0], srcYCache[0], srcWCache[0], srcHCache[0], dstXCache[0], dstYCache[0], dstWCache[0], dstHCache[0], scaleHint,
                      blendMode, cCache[0], false);
                else
                  textureMultiMap0(nativePtr.longValue(), srcXCache, srcYCache, srcWCache, srcHCache, dstXCache, dstYCache, dstWCache, dstHCache, scaleHint,
                      blendMode, cCache, 1, currCoords);
              }
              fullClearStatus = -1;
            }
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWTEXTURED : " + len);
        }
        break;
      case GFXCMD_DRAWTEXTUREDDIFFUSE:
        // x, y, width, height, handle, srcx, srcy, srcwidth, srcheight, blend, diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight
        if(len==60)
        {
          int x, y, width, height, handle,
          srcx, srcy, srcwidth, srcheight, blend,
          diffhandle, diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight;
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
          diffhandle=readInt(40, cmddata);
          diffsrcx = readInt(44, cmddata);
          diffsrcy=readInt(48, cmddata);
          diffsrcwidth=readInt(52, cmddata);
          diffsrcheight=readInt(56, cmddata);
          int scaleHint = 1; // 0 is nearest neighbor, 1 is linear scaling...this will never align properly for diffused textures
          if (GFX_DBG) System.out.println("GFXCMD_DRAWTEXTUREDDIFFUSE src=" + srcx + "," + srcy + "," + srcwidth + "," + srcheight +
              " dst=" + x + "," + y + "," + width + "," + height + " handle=" + handle + " blend=0x" + Integer.toString(blend, 16));

          Long nativePtr = (Long) nativeTexturePtrs.get(new Integer(handle));
          Long nativeDiffPtr = (Long) nativeTexturePtrs.get(new Integer(diffhandle));
          if (nativePtr == null)
          {
            System.out.println("ERROR invalid handle passed for texture rendering of: " + handle);
            abortRenderCycle = true;
          }
          else if (nativeDiffPtr == null)
          {
            System.out.println("ERROR invalid handle passed for diffused texture rendering of: " + diffhandle);
            abortRenderCycle = true;
          }
          else
          {
            myConn.registerImageAccess(handle);
            myConn.registerImageAccess(diffhandle);
            if (width >= 0 && height >= 0)
            {
              synchronized (this)
              {
                if (!beganScene)
                {
                  if (!beginScene0(c.getWidth(), c.getHeight()))
                    break;
                  beganScene = true;
                }
                textureDiffuseMap0(nativePtr.longValue(), nativeDiffPtr.longValue(), srcx, srcy, srcwidth, srcheight,
                    diffsrcx, diffsrcy, diffsrcwidth, diffsrcheight, x, y, width, height, scaleHint,
                    java.awt.AlphaComposite.SRC_OVER, blend, currCoords);
              }
              fullClearStatus = -1;
            }
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
          // Apparently we didn't do line rendering in the DX9 stuff...
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_DRAWLINE : " + len);
        }
        break;
      case GFXCMD_LOADIMAGE:
        // width, height // Not used unless we do uncompressed images
        if(len>=8)
        {
          int width, height;
          int imghandle = handleCount++;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          if (!canAllocNewTexture(width, height))
            imghandle = 0;
          else
          {
            ImageLoadData ild = new ImageLoadData();
            ild.imageLoadBuff = java.nio.ByteBuffer.allocateDirect(width * height * 4);
            ild.imageLoadBuff.clear();
            ild.imageLoadBuffHandle = imghandle;
            ild.imageLoadBuffSize = new java.awt.Dimension(width, height);
            imageLoadBuffMap.put(new Integer(imghandle), ild);
          }
          //imghandle = allocNewTexture(width, height, imghandle);

          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGE : " + len);
        }
        break;
      case GFXCMD_LOADIMAGETARGETED:
        // handle, width, height // Not used unless we do uncompressed images
        if(len>=12)
        {
          int width, height;
          int imghandle = readInt(0, cmddata);
          width=readInt(4, cmddata);
          height=readInt(8, cmddata);
          while (!canAllocNewTexture(width, height))
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache");
              unloadImage(oldestImage);
              myConn.postImageUnload(oldestImage);
            }
            else
            {
              System.out.println("ERROR cannot free enough from the cache to support loading a new image!!!");
              break;
            }
          }
          ImageLoadData ild = new ImageLoadData();
          ild.imageLoadBuff = java.nio.ByteBuffer.allocateDirect(width * height * 4);
          ild.imageLoadBuff.clear();
          ild.imageLoadBuffHandle = imghandle;
          ild.imageLoadBuffSize = new java.awt.Dimension(width, height);
          imageLoadBuffMap.put(new Integer(imghandle), ild);
          myConn.registerImageAccess(imghandle);

          hasret[0]=0;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGETARGETED : " + len);
        }
        break;
      case GFXCMD_CREATESURFACE:
        // width, height
        if(len>=8)
        {
          int width, height;
          int imghandle = handleCount++;;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          long surfPtr = createD3DRenderTarget0(width, height);
          if (surfPtr == 0)
          {
            System.out.println("Failed creating surface of size " + width + "x" + height);
            imghandle = 0;
          }
          else
          {
            System.out.println("Created surface " + surfPtr + " of size " + width + "x" + height);
            imageCacheSize += width * height * 4;
            nativeTexturePtrs.put(new Integer(imghandle), new Long(surfPtr));
            nativeTexturePtrSizes.put(new Integer(imghandle), new Long(width * height * 4));
          }

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
          int imghandle = 1;
          if (!canAllocNewTexture(width, height))
            imghandle = 0;
          else if (len >= 12)
          {
            // We've got enough room for it and there's a cache ID, check if we've got it cached locally
            int strlen = readInt(8, cmddata);
            if (strlen > 1)
            {
              try
              {
                String rezName = new String(cmddata, 16, strlen - 1);
                System.out.println("Checking cache for resource " + rezName);
                lastImageResourceID = rezName;
                // We use this hashcode to match it up on the loadCompressedImage call so we know we're caching the right thing
                lastImageResourceIDHandle = imghandle = Math.abs(lastImageResourceID.hashCode());
                java.io.File cachedFile = myConn.getCachedImageFile(rezName);
                if (cachedFile != null)
                {
                  // We've got it locally in our cache! Read it from there.
                  sage.media.image.RawImage rawImg = sage.media.image.ImageLoader.loadImageFromFile(cachedFile.getAbsolutePath());
                  if (rawImg == null || rawImg.getWidth() != width || rawImg.getHeight() != height)
                  {
                    if (rawImg != null)
                    {
                      // It doesn't match the cache
                      System.out.println("CACHE ID verification failed for rezName=" + rezName + " target=" + width + "x" + height + " actual=" + rawImg.getWidth() + "x" + rawImg.getHeight());
                      sage.media.image.ImageLoader.freeImage(rawImg);
                      cachedFile.delete();
                    }
                    // else we failed loading it from the cache so we want it for sure!
                  }
                  else
                  {
                    long nativePtr = createD3DTextureFromMemory0(width, height, rawImg.getROData());
                    sage.media.image.ImageLoader.freeImage(rawImg);
                    if (nativePtr != 0)
                    {
                      System.out.println("Loaded " + width + "x" + height + " image to " + imghandle + " from local cache file " + cachedFile);
                      int pow2W, pow2H;
                      pow2W = pow2H = 1;
                      while (pow2W < width)
                        pow2W = pow2W << 1;
                      while (pow2H < height)
                        pow2H = pow2H << 1;
                      int nativeMemUse = pow2W*pow2H*4;
                      imghandle = handleCount++;
                      nativeTexturePtrs.put(new Integer(imghandle), new Long(nativePtr));
                      nativeTexturePtrSizes.put(new Integer(imghandle), new Long(nativeMemUse));
                      imageCacheSize += nativeMemUse;
                      hasret[0] = 1;
                      return -1 * imghandle;
                    }
                  }
                }
              }
              catch (java.io.IOException e)
              {
                System.out.println("ERROR loading compressed image: " + e);
              }
            }
          }
          //imghandle=STBGFX.GFX_loadImage(width, height);
          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_PREPIMAGE : " + len);
        }
        break;
      case GFXCMD_PREPIMAGETARGETED:
        // handle, width, height, [rezID]
        if(len>=12)
        {
          int imghandle, width, height;
          imghandle = readInt(0, cmddata);
          width=readInt(4, cmddata);
          height=readInt(8, cmddata);
          int strlen = readInt(12, cmddata);
          while (!canAllocNewTexture(width, height))
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache");
              unloadImage(oldestImage);
              myConn.postImageUnload(oldestImage);
            }
            else
            {
              System.out.println("ERROR cannot free enough from the cache to support loading a new image!!!");
              break;
            }
          }
          if (len >= 16)
          {
            // We will not have this cached locally...but setup our vars to track it
            String rezName = new String(cmddata, 20, strlen - 1);
            lastImageResourceID = rezName;
            lastImageResourceIDHandle = imghandle;
            System.out.println("Prepped targeted image with handle " + imghandle + " resource=" + rezName);
          }
          myConn.registerImageAccess(imghandle);
          hasret[0]=0;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_PREPIMAGE : " + len);
        }
        break;
      case GFXCMD_LOADCACHEDIMAGE:
        // width, height
        if(len>=18)
        {
          int width, height, imghandle;
          imghandle = readInt(0, cmddata);
          width = readInt(4, cmddata);
          height = readInt(8, cmddata);
          int strlen = readInt(12, cmddata);
          String rezName = new String(cmddata, 20, strlen - 1);
          System.out.println("imghandle=" + imghandle + " width=" + width + " height=" + height + " strlen=" + strlen + " rezName=" + rezName);
          while (!canAllocNewTexture(width, height))
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache");
              unloadImage(oldestImage);
              myConn.postImageUnload(oldestImage);
            }
            else
            {
              System.out.println("ERROR cannot free enough from the cache to support loading a new image!!!");
              break;
            }
          }
          myConn.registerImageAccess(imghandle);
          try
          {
            System.out.println("Loading resource from cache: " + rezName);
            java.io.File cachedFile = myConn.getCachedImageFile(rezName);
            if (cachedFile != null)
            {
              // We've got it locally in our cache! Read it from there.
              System.out.println("Image found in cache!");
              sage.media.image.RawImage rawImg = sage.media.image.ImageLoader.loadImageFromFile(cachedFile.getAbsolutePath());
              if (rawImg == null || rawImg.getWidth() != width || rawImg.getHeight() != height)
              {
                if (rawImg != null)
                {
                  // It doesn't match the cache
                  System.out.println("CACHE ID verification failed for rezName=" + rezName + " target=" + width + "x" + height + " actual=" + rawImg.getWidth() + "x" + rawImg.getHeight());
                  sage.media.image.ImageLoader.freeImage(rawImg);
                }
                else
                  System.out.println("CACHE Load failed for rezName=" + rezName);
                cachedFile.delete();
                // This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
                myConn.postImageUnload(imghandle);
                myConn.postOfflineCacheChange(false, rezName);
              }
              else
              {
                long nativePtr = createD3DTextureFromMemory0(width, height, rawImg.getROData());
                sage.media.image.ImageLoader.freeImage(rawImg);
                if (nativePtr != 0)
                {
                  System.out.println("Loaded " + width + "x" + height + " image to " + imghandle + " from local cache file " + cachedFile);
                  int pow2W, pow2H;
                  pow2W = pow2H = 1;
                  while (pow2W < width)
                    pow2W = pow2W << 1;
                  while (pow2H < height)
                    pow2H = pow2H << 1;
                  int nativeMemUse = pow2W*pow2H*4;
                  nativeTexturePtrs.put(new Integer(imghandle), new Long(nativePtr));
                  nativeTexturePtrSizes.put(new Integer(imghandle), new Long(nativeMemUse));
                  imageCacheSize += nativeMemUse;
                }
                else
                {
                  System.out.println("Native image load failed for cache File!");
                  // This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
                  myConn.postImageUnload(imghandle);
                  myConn.postOfflineCacheChange(false, rezName);
                }
              }
            }
            else
            {
              System.out.println("ERROR Image not found in cache that should be there! rezName=" + rezName);
              // This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
              myConn.postImageUnload(imghandle);
              myConn.postOfflineCacheChange(false, rezName);
            }
          }
          catch (java.io.IOException e)
          {
            System.out.println("ERROR loading compressed image: " + e);
          }
          hasret[0]=0;
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
          unloadImage(handle);
          myConn.clearImageAccess(handle);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADIMAGE : " + len);
        }
        break;
      case GFXCMD_SETTARGETSURFACE:
        // handle
        if(len==4)
        {
          int handle;
          handle=readInt(0, cmddata);
          //STBGFX.GFX_unloadImage(handle);
          if (GFX_DBG) System.out.println("GFXCMD_SETTARGETSURFACE " + handle);
          if (handle == 0)
          {
            setRenderTarget0(backSurf);
          }
          else
          {
            Long nativePtr = (Long) nativeTexturePtrs.get(new Integer(handle));
            if (nativePtr != null)
            {
              setRenderTarget0(nativePtr.longValue());
            }
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADIMAGE : " + len);
        }
        break;
      case GFXCMD_LOADFONT:
        System.out.println("GFXCMD_LOADFONT is not implemented...");
        // namelen, name, style, size
        /*if(len>=12 && len>=(12+readInt(0, cmddata)))
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
					if (myConn.hasFontServer())
					{
						// Check in the cache for the font since we load them from the server
						if (fonty.getFamily().equals("Dialog"))
						{
							if (!cachedFontMap.containsKey(cacheName))
							{
								// Fonts in the Dialog family are the default ones that come with Java that cause problems, so
								// load this from the other way if it's here.
								java.io.File cachedFile = new java.io.File(cacheDir, cacheName + "-" + myConn.getServerName());
								if (cachedFile.isFile() && cachedFile.length() > 0)
								{
									System.out.println("Loading font from cache for " + cacheName);
									// We've got it locally in our cache! Read it from there.
									java.io.FileInputStream fis = null;
									try
									{
										fis = new java.io.FileInputStream(cachedFile);
										java.awt.Font cacheFont = java.awt.Font.createFont(0, fis);
										fis.close();
										fis = null;
										cachedFontMap.put(cacheName, cacheFont);
									}
									catch (java.awt.FontFormatException ffe)
									{
										System.out.println("Failed loading as truetype, retrying:" + ffe);
										try
										{
											if (fis != null)
												fis.close();
											fis = new java.io.FileInputStream(cachedFile);
											java.awt.Font cacheFont = java.awt.Font.createFont(1, fis);
											fis.close();
											cachedFontMap.put(cacheName, cacheFont);
										}
										catch (Exception e)
										{
											System.out.println("ERROR loading font of:" + e);
										}
									}
									catch (java.io.IOException e1)
									{
										System.out.println("ERROR loading font of:" + e1);
									}
								}
							}

							java.awt.Font cachedFont = (java.awt.Font) cachedFontMap.get(cacheName);
							if (cachedFont != null)
							{
								// Narflex: 5/11/06 - I'm not all that sure about this....but the data for character widths line up correctly
								// when not applying the style here and don't line up if we do (for the default Java fonts).
								// It makes sense because we're already caching fonts based on name + style so why would we need to
								// re-apply the style?  Unless there's the same font file for both...but that's the case with the Java fonts
								// and applying the style there causes incorrect widths. Interesting...
								fonty = cachedFont.deriveFont((float) size);
							}
							else
							{
								// Return that we don't have this font so it'll load it into our cache
								hasret[0] = 1;
								return 0;
							}
						}
					}
					System.out.println("Loaded Font=" + fonty);
					fontMap.put(new Integer(fonthandle), fonty);
					//fonthandle=STBGFX.GFX_loadFont(name.toString(), style, size);
					hasret[0] = 1;
					return fonthandle;
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_LOADFONT : " + len);
				}*/

        break;
      case GFXCMD_UNLOADFONT:
        System.out.println("GFXCMD_UNLOADFONT is not implemented...");
        // handle
        /*				if(len==4)
				{
					int handle;
					handle=readInt(0, cmddata);
					//STBGFX.GFX_unloadFont(handle);
					fontMap.remove(new Integer(handle));
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_UNLOADFONT : " + len);
				}            */
        break;
      case GFXCMD_LOADFONTSTREAM:
        System.out.println("GFXCMD_LOADFONTSTREAM is not implemented...");
        // namelen, name, len, data
        /*				if (len>=8)
				{
					StringBuffer name = new StringBuffer();
					int namelen = readInt(0, cmddata);
					for(int i=0;i<namelen-1;i++) // skip the terminating \0 character
					{
						name.append((char) cmddata[8 + i]); // an extra 4 for the header
					}
					int datalen = readInt(4 + namelen, cmddata);
					if (len >= datalen + 8 + namelen)
					{
						System.out.println("Saving font " + name.toString() + " to cache");
						// Save this image to our disk cache
						java.io.FileOutputStream fos = null;
						try
						{
							java.io.File cacheFile = new java.io.File(cacheDir, name.toString() + "-" + myConn.getServerName());
							fos = new java.io.FileOutputStream(cacheFile);
							fos.write(cmddata, 12 + namelen, datalen);
						}
						catch (java.io.IOException e)
						{
							System.out.println("ERROR writing to cache file " + name + " of " + e);
						}
						finally
						{
							try
							{
								if (fos != null)
									fos.close();
							}
							catch (Exception e)
							{
							}
							fos = null;
						}
					}
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_LOADFONTSTREAM : " + len);
				}            */
        break;
      case GFXCMD_PUSHTRANSFORM:
        // 12 float values
        if (len == 64)
        {
          double[] newcoords = new double[16];
          newcoords[0] = readFloat(0, cmddata);
          newcoords[4] = readFloat(4, cmddata);
          newcoords[8] = readFloat(8, cmddata);
          newcoords[12] = readFloat(12, cmddata);
          newcoords[1] = readFloat(16, cmddata);
          newcoords[5] = readFloat(20, cmddata);
          newcoords[9] = readFloat(24, cmddata);
          newcoords[13] = readFloat(28, cmddata);
          newcoords[2] = readFloat(32, cmddata);
          newcoords[6] = readFloat(36, cmddata);
          newcoords[10] = readFloat(40, cmddata);
          newcoords[14] = readFloat(44, cmddata);
          newcoords[3] = readFloat(48, cmddata);
          newcoords[7] = readFloat(52, cmddata);
          newcoords[11] = readFloat(56, cmddata);
          newcoords[15] = readFloat(60, cmddata);
          matrixStack.push(currCoords);
          if (newcoords[0] == 1 && newcoords[1] == 0 && newcoords[2] == 0 && newcoords[3] == 0 && newcoords[4] == 0 &&
              newcoords[5] == 1 && newcoords[6] == 0 && newcoords[7] == 0 && newcoords[8] == 0 && newcoords[9] == 0 &&
              newcoords[10] == 1 && newcoords[11] == 0 && newcoords[12] == 0 && newcoords[13] == 0 && newcoords[14] == 0 &&
              newcoords[15] == 1)
            newcoords = null;
          currCoords = newcoords;
        }
        break;
      case GFXCMD_POPTRANSFORM:
        currCoords = (double[])matrixStack.pop();
        break;
      case GFXCMD_FLIPBUFFER:
        hasret[0] = 1;
        if (GFX_DBG) System.out.println("GFXCMD_FLIPBUFFER");
        //STBGFX.GFX_flipBuffer();
        synchronized (this)
        {
          setRenderTarget0(0);
          if (videoSurface != 0 && srcVideoBounds != null && videoBounds != null)
          {
            if (asyncMplayerRender)
            {
              if (!beganScene)
              {
                beginScene0(c.getWidth(), c.getHeight());
                beganScene = true;
              }
              stretchBlt0(videoSurface, srcVideoBounds.x, srcVideoBounds.y, srcVideoBounds.width,
                  srcVideoBounds.height, 0, videoBounds.x, videoBounds.y,
                  videoBounds.width, videoBounds.height, -1);
            }
            else
            {
              if (beganScene)
              {
                endScene0();
                beganScene = false;
              }
              stretchBlt0(videoSurface, srcVideoBounds.x, srcVideoBounds.y, srcVideoBounds.width,
                  srcVideoBounds.height, 0, videoBounds.x, videoBounds.y,
                  videoBounds.width, videoBounds.height, inVmr9Callback ? 0 : 1);
            }
          }
          // If the last frame wasn't a full clear; then we still need to clean up the rendering from it in the transparent areas
          if (abortRenderCycle)
          {
            System.out.println("ERROR in painting cycle, ABORT was set...send full repaint command");
            fullClearStatus = lastFullClearStatus = 1;
            myConn.postRepaintEvent(0, 0, c.getWidth(), c.getHeight());
          }
          if (fullClearStatus < 1 || lastFullClearStatus < 1)
          {
            if (!beganScene)
            {
              beginScene0(c.getWidth(), c.getHeight());
              beganScene = true;
            }
            textureMap0(backSurf, 0, 0, c.getWidth(), c.getHeight(), 0, 0, c.getWidth(), c.getHeight(), 0,
                java.awt.AlphaComposite.SRC_OVER, 0xFFFFFFFF, false);
          }
          else
          {
            System.out.println("FULL CLEAR; skipping UI rendering!");
          }
          firstFrameDone = true;
          if (beganScene)
            endScene0();
          beganScene = false;
          present0(0, 0, c.getWidth(), c.getHeight());
          inFrameNow = false;
        }
        return 0;
      case GFXCMD_STARTFRAME:
        if (GFX_DBG) System.out.println("GFXCMD_STARTFRAME");
        boolean masterSizeChange = !f.getSize().equals(lastMasterSize);
        synchronized (this)
        {
          inFrameNow = true;
          clearScene0(masterSizeChange);
          setRenderTarget0(backSurf);
          lastMasterSize = f.getSize();
          beganScene = false;
          lastFullClearStatus = fullClearStatus;
          fullClearStatus = 0;
        }
        abortRenderCycle = false;
        matrixStack.clear();
        break;
      case GFXCMD_LOADIMAGELINE:
        if(len>=12 && len>=(12+readInt(8, cmddata)))
        {
          int handle, line, len2;
          //unsigned char *data=&cmddata[12];
          handle=readInt(0, cmddata);
          line=readInt(4, cmddata);
          len2=readInt(8, cmddata);
          // the last number is the offset into the data array to start reading from
          //STBGFX.GFX_loadImageLine(handle, line, len, data, 12);
          ImageLoadData ild = (ImageLoadData) imageLoadBuffMap.get(new Integer(handle));
          if (ild != null)
          {
            ild.imageLoadBuff.put(cmddata, 16, len2);
            if (ild.imageLoadBuff.remaining() == 0)
            {
              long nativePtr = createD3DTextureFromMemory0(ild.imageLoadBuffSize.width, ild.imageLoadBuffSize.height, ild.imageLoadBuff);
              if (nativePtr != 0)
              {
                System.out.println("Loaded " + ild.imageLoadBuffSize.width + "x" + ild.imageLoadBuffSize.height + " image to " + nativePtr + " from line-based load");
                int pow2W, pow2H;
                pow2W = pow2H = 1;
                while (pow2W < ild.imageLoadBuffSize.width)
                  pow2W = pow2W << 1;
                while (pow2H < ild.imageLoadBuffSize.height)
                  pow2H = pow2H << 1;
                int nativeMemUse = pow2W*pow2H*4;
                nativeTexturePtrs.put(new Integer(ild.imageLoadBuffHandle), new Long(nativePtr));
                nativeTexturePtrSizes.put(new Integer(ild.imageLoadBuffHandle), new Long(nativeMemUse));
                imageCacheSize += nativeMemUse;
                imageLoadBuffMap.remove(new Integer(handle));
              }
            }
          }
          myConn.registerImageAccess(handle);
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
          java.io.File cacheFile = null;
          // Save this image to our disk cache
          java.io.FileOutputStream fos = null;
          boolean deleteCacheFile = false;
          String resID = null;
          try
          {
            boolean cacheAdd = false;
            if (lastImageResourceID != null && lastImageResourceIDHandle == handle)
            {
              cacheFile = myConn.getCachedImageFile(lastImageResourceID, false);
              resID = lastImageResourceID;
              if (cacheFile != null && (!cacheFile.isFile() || cacheFile.length() == 0))
                cacheAdd = true;
            }
            if (cacheFile == null)
            {
              cacheFile = java.io.File.createTempFile("stv", "img");
              deleteCacheFile = true;
            }
            fos = new java.io.FileOutputStream(cacheFile);
            fos.write(cmddata, 12, len2); // an extra 4 for the header
            if (cacheAdd)
            {
              myConn.postOfflineCacheChange(true, lastImageResourceID);
            }
          }
          catch (java.io.IOException e)
          {
            System.out.println("ERROR writing to cache file " + cacheFile + " of " + e);
            cacheFile = null;
          }
          finally
          {
            try
            {
              if (fos != null)
                fos.close();
            }
            catch (Exception e)
            {
            }
            fos = null;
          }
          myConn.registerImageAccess(handle);
          if (cacheFile != null)
          {
            if (!myConn.doesUseAdvancedImageCaching())
            {
              handle = handleCount++;
              hasret[0] = 1;
            }
            else
              hasret[0] = 0;
            try
            {
              sage.media.image.RawImage rawImg = sage.media.image.ImageLoader.loadImageFromFile(cacheFile.getAbsolutePath());
              if (rawImg != null)
              {
                long nativePtr = createD3DTextureFromMemory0(rawImg.getWidth(), rawImg.getHeight(), rawImg.getROData());
                sage.media.image.ImageLoader.freeImage(rawImg);
                if (nativePtr != 0)
                {
                  System.out.println("Loaded compressed image " + handle + " of size " + rawImg.getWidth() + "x" + rawImg.getHeight() + " resID=" + resID);
                  int pow2W, pow2H;
                  pow2W = pow2H = 1;
                  while (pow2W < rawImg.getWidth())
                    pow2W = pow2W << 1;
                  while (pow2H < rawImg.getHeight())
                    pow2H = pow2H << 1;
                  int nativeMemUse = pow2W*pow2H*4;
                  nativeTexturePtrs.put(new Integer(handle), new Long(nativePtr));
                  nativeTexturePtrSizes.put(new Integer(handle), new Long(nativeMemUse));
                  imageCacheSize += nativeMemUse;
                  if (deleteCacheFile)
                    cacheFile.delete();
                  return handle;
                }
              }
            }
            catch (java.io.IOException e)
            {
              System.out.println("ERROR loading compressed image: " + e);
            }
          }
          if (deleteCacheFile && cacheFile != null)
            cacheFile.delete();
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGECOMPRESSED : " + len);
        }
        break;
      case GFXCMD_XFMIMAGE:
        // srcHandle, destHandle, destWidth, destHeight, maskCornerArc
        System.out.println("XFMIMAGE is not implemented since we do HW scaling...");
        break;
      case GFXCMD_SETVIDEOPROP:
        if (len >= 40)
        {
          java.awt.Rectangle srcRect = new java.awt.Rectangle(readInt(4, cmddata), readInt(8, cmddata),
              readInt(12, cmddata), readInt(16, cmddata));
          java.awt.Rectangle destRect = new java.awt.Rectangle(readInt(20, cmddata), readInt(24, cmddata),
              readInt(28, cmddata), readInt(32, cmddata));
          if (GFX_DBG) System.out.println("GFXCMD_SETVIDEOPROP src=" + srcRect + " dst=" + destRect);
          MediaCmd mc = MediaCmd.getInstance();
          if (mc != null)
          {
            MiniMPlayerPlugin playa = mc.getPlaya();
            if (playa != null)
              playa.setVideoRectangles(srcRect, destRect, false);
          }
          setVideoBounds(srcRect, destRect);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_SETVIDEOPROP: " + len);
        }
        break;
      case GFXCMD_TEXTUREBATCH:
        if (len >= 8)
        {
          int numCmds = readInt(0, cmddata);
          int sizeCmds = readInt(4, cmddata);
        }
        break;
      default:
        return -1;
    }
    return 0;
  }

  public static int getGradientColor(int shapeX, int shapeY, int shapeW, int shapeH, int argbTL,
      int argbBR, float x, float y)
  {
    // Calculate the projection of the point onto the vector, and then we use that distance relative to the
    // length of the vector to determine what proportionality of each color to use.
    float frac2 = Math.abs((x-shapeX)*shapeW + (y-shapeY)*shapeH) /
        (shapeW*shapeW + shapeH*shapeH);
    if (frac2 > 1.0f || frac2 < 0) // don't convert 1.0 to 0
      frac2 = frac2 % 1.0f;
    float frac1 = 1.0f - frac2;
    int alpha = (argbTL & 0xFF000000);
    return alpha | ((int)(((argbTL & 0xFF0000) >> 16)*frac1 + ((argbBR & 0xFF0000) >> 16)*frac2) << 16) |
        ((int)(((argbTL & 0xFF00) >> 8)*frac1 + ((argbBR & 0xFF00) >> 8)*frac2) << 8) |
        ((int)((argbTL & 0xFF)*frac1 + (argbBR & 0xFF)*frac2));
  }

  private boolean canAllocNewTexture(int width, int height)
  {
    int pow2W, pow2H;
    pow2W = pow2H = 1;
    while (pow2W < width)
      pow2W = pow2W << 1;
    while (pow2H < height)
      pow2H = pow2H << 1;
    int nativeMemUse = pow2W*pow2H*4;

    long availableLimit = 8000000;//Sage.getLong("ui/video_memory_available_minimum_limit", 2000000);
    long newLimit = getAvailableVideoMemory0()*VRAM_USAGE_PERCENT/100;
    newLimit = Math.min(newLimit, maxVramUsage);
    if (newLimit > videoMemoryLimit)
    {
      videoMemoryLimit = newLimit;
      System.out.println("New video memory limit=" + videoMemoryLimit);
    }
    if (imageCacheSize + nativeMemUse > videoMemoryLimit ||
        getAvailableVideoMemory0() < availableLimit + nativeMemUse)
    {
      // Not enough room; need to free images
      return false;
    }
    else
      return true;

  }

  public static void vmr9RenderNotify(int flags, long pSurface, long pTexture,
      long startTime, long endTime, int width, int height)
  {
    if (defaultDX9Renderer != null)
      defaultDX9Renderer.vmr9RenderUpdate(flags, pSurface, pTexture, startTime, endTime, width, height, 0, 0);
  }
  public void vmr9RenderUpdate(int flags, long pSurface, long pTexture, long startTime, long endTime, int width,
      int height, int arx, int ary)
  {
    synchronized (this)
    {
      if (pSurface != 0)
      {
        videoSurface = pSurface;
        if (!inFrameNow)
        {
          if (srcVideoBounds != null && videoBounds != null)
          {
            setRenderTarget0(0);
            if (asyncMplayerRender)
            {
              if (!beganScene)
              {
                beginScene0(c.getWidth(), c.getHeight());
                beganScene = true;
              }
              stretchBlt0(videoSurface, srcVideoBounds.x, srcVideoBounds.y, srcVideoBounds.width,
                  srcVideoBounds.height, 0, videoBounds.x, videoBounds.y,
                  videoBounds.width, videoBounds.height, -1);
            }
            else
            {
              if (beganScene)
              {
                endScene0();
                beganScene = false;
              }
              stretchBlt0(videoSurface, srcVideoBounds.x, srcVideoBounds.y, srcVideoBounds.width,
                  srcVideoBounds.height, 0, videoBounds.x, videoBounds.y,
                  videoBounds.width, videoBounds.height, inVmr9Callback ? 0 : 1);
            }
            if (fullClearStatus < 1)
            {
              if (!beganScene)
              {
                beginScene0(c.getWidth(), c.getHeight());
                beganScene = true;
              }
              textureMap0(backSurf, 0, 0, c.getWidth(), c.getHeight(), 0, 0, c.getWidth(), c.getHeight(), 0,
                  java.awt.AlphaComposite.SRC_OVER, 0xFFFFFFFF, false);
            }
            else
            {
              //							System.out.println("FULL CLEAR; skipping UI rendering!");
            }
            if (beganScene)
              endScene0();
            beganScene = false;
            present0(0, 0, c.getWidth(), c.getHeight());
          }
        }
      }
      else
        videoSurface = 0;
    }
  }

  private void unloadImage(int handle)
  {
    Integer intKey = new Integer(handle);
    Long nativePtr = (Long) nativeTexturePtrs.get(intKey);
    if (nativePtr != null)
    {
      long nativeSize = ((Long) nativeTexturePtrSizes.get(intKey)).longValue();
      System.out.println("Unloading native image " + handle + " of size " + nativeSize);
      imageCacheSize -= nativeSize;
      freeD3DTexturePointer0(nativePtr.longValue());
      nativeTexturePtrs.remove(intKey);
      nativeTexturePtrSizes.remove(intKey);
      myConn.clearImageAccess(handle);
    }

  }

  private synchronized native boolean initDX9SageRenderer0(int width, int height, long hwnd);

  private synchronized native void cleanupDX9SageRenderer0();

  private synchronized native long createD3DTextureFromMemory0(int width, int height, java.nio.ByteBuffer data);

  private synchronized native void freeD3DTexturePointer0(long nativePtr);

  private synchronized native boolean present0(int clipX, int clipY, int clipWidth, int clipHeight);

  private synchronized native void clearScene0(boolean fullClear);
  private synchronized native boolean beginScene0(int viewportWidth, int viewportHeight);
  private synchronized native void endScene0();

  private synchronized native boolean stretchBlt0(long srcSurface, int srcRectX, int srcRectY, int srcRectW, int srcRectH,
      long destTexture, int destRectX, int destRectY, int destRectW, int destRectH, int scaleHint);
  private synchronized native boolean textureMap0(long srcTexture, float srcRectX, float srcRectY, float srcRectW, float srcRectH,
      float destRectX, float destRectY, float destRectW, float destRectH, int scaleHint, int compositing,
      int textureColor, boolean spritesOK);
  private synchronized native boolean textureMultiMap0(long srcTexture, float[] srcRectX, float[] srcRectY,
      float[] srcRectW, float[] srcRectH,	float[] destRectX, float[] destRectY, float[] destRectW,
      float[] destRectH, int scaleHint, int compositing, int[] textureColor, int numRects, double[] matrixCoords);
  private synchronized native boolean textureDiffuseMap0(long srcTexture, long diffuseTexture, float srcRectX, float srcRectY,
      float srcRectW, float srcRectH, float diffuseSrcX, float diffuseSrcY, float diffuseSrcW, float diffuseSrcH,
      float destRectX, float destRectY, float destRectW, float destRectH, int scaleHint, int compositing, int textureColor, double[] matrixCoords);
  private boolean fillShape(float[] xcoords, float[] ycoords, int[] colors, int triangleStrip,
      int numVertices, java.awt.Rectangle clipRect, double[] matrixCoords)
  {
    if (matrixCoords == null)
      return fillShape0(xcoords, ycoords, colors, triangleStrip, numVertices, clipRect);
    else
      return fillShape0(xcoords, ycoords, colors, triangleStrip, numVertices, clipRect, matrixCoords);
  }
  private synchronized native boolean fillShape0(float[] xcoords, float[] ycoords, int[] colors, int triangleStrip,
      int numVertices, java.awt.Rectangle clipRect);
  private synchronized native boolean fillShape0(float[] xcoords, float[] ycoords, int[] colors, int triangleStrip,
      int numVertices, java.awt.Rectangle clipRect, double[] matrixCoords);
  private synchronized native long getAvailableVideoMemory0();

  private synchronized native long createD3DRenderTarget0(int width, int height);
  // Use 0 for the back buffer
  private synchronized native boolean setRenderTarget0(long targetPtr);

  // NEW NATIVE METHODS FOR PS
  //private synchronized native long createD3DTexture0(int width, int height);
  private static native void registerMiniClientNatives0();

  protected void asyncVideoRender(String shMemPrefix)
  {
    //		asyncMplayerRender = true;
    asyncVideoRender0(shMemPrefix);
    //		asyncMplayerRender = false;
  }
  protected native void asyncVideoRender0(String shMemPrefix);
}
