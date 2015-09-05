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


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.Threading;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;

public class OpenGLGFXCMD extends GFXCMD2 implements GLEventListener, sage.miniclient.JOGLVideoUI
{
  public void setup(MiniClientConnection inConn)
  {
    myConn = inConn;
  }

  public OpenGLGFXCMD()
  {
    this(null);
  }

  public OpenGLGFXCMD(MiniClientConnection myConn)
  {
    super(myConn);
    System.out.println("Creating opengl renderer");
    if(java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.BIG_ENDIAN) bigendian = true;
    System.out.println("Native order: "+java.nio.ByteOrder.nativeOrder());
    videorenderer = new OpenGLVideoRenderer(this);
    imageCacheLimit = 32000000;
    try
    {
      imageCacheLimit = Integer.parseInt(MiniClient.myProperties.getProperty("image_cache_size", "32000000"));
    }
    catch (Exception e)
    {
      System.out.println("Invalid image_cache_size property:" + e);
    }
    glAlphaColorModel = new java.awt.image.ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),
        new int[] {8,8,8,8},
        true,
        false,
        java.awt.image.ComponentColorModel.TRANSLUCENT,
        java.awt.image.DataBuffer.TYPE_BYTE);

    glColorModel = new java.awt.image.ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),
        new int[] {8,8,8,0},
        false,
        false,
        java.awt.image.ComponentColorModel.OPAQUE,
        java.awt.image.DataBuffer.TYPE_BYTE);
  }

  public sage.miniclient.OpenGLVideoRenderer videorenderer;
  public boolean inframe=false;
  public GLOffscreenAutoDrawable pbuffer; // Used for rendering the UI
  public GLCanvas c;

  private boolean bigendian = false;
  private boolean useNativeServer=false;
  private int osdwidth=720, osdheight=480;
  private int windowwidth=720, windowheight=480;
  private int osdt[]; // texture of the osd used when drawing the window
  private java.util.Map imageMap = new java.util.HashMap();
  private java.util.Map imageMapSizes = new java.util.HashMap();
  private int handleCount = 2;
  private boolean alive=true;
  private boolean newpbuffer=true;
  private Object newpbufferlock = new Object();
  private int newosdwidth=720, newosdheight=480;
  private boolean connectingDone = false;
  private int RSurfWidth;
  private int RSurfHeight;

  private java.awt.image.ColorModel glAlphaColorModel;
  private java.awt.image.ColorModel glColorModel;

  public void refresh()
  {
    c.invalidate();
    f.invalidate();
    f.validate();
  }

  public void init(GLAutoDrawable drawable)
  {
    GL2 gl;
    gl = drawable.getGL().getGL2();
    gl.setSwapInterval(0);
  }

  public void dispose(GLAutoDrawable drawable) {
  }


  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
  {
    GL2 gl;
    System.out.println("reshape "+x+","+y+" "+width+","+height);
    gl = drawable.getGL().getGL2();
    newosdwidth=width;
    newosdheight=height;
    gl.glViewport(0, 0, newosdwidth, newosdheight);
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(0,newosdwidth,newosdheight,0,-1.0,1.0);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    gl.glLoadIdentity();
    gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);
    gl.glClear( gl.GL_COLOR_BUFFER_BIT);
    synchronized(newpbufferlock)
    {
      newpbuffer=true;
    }
    myConn.postResizeEvent(new java.awt.Dimension(c.getWidth(), c.getHeight()));
  }

  private void initpbuffer()
  {
    GL2 gl;
    System.out.println("initpbuffer");
    osdwidth=newosdwidth;
    osdheight=newosdheight;
    GLCapabilities caps = new GLCapabilities(null);
    caps.setHardwareAccelerated(true);
    caps.setDoubleBuffered(false);
    caps.setAlphaBits(8);
    caps.setRedBits(8);
    caps.setGreenBits(8);
    caps.setBlueBits(8);
    caps.setDepthBits(0);
    caps.setFBO(false);
    System.out.println("initpbuffer2");
    if (!GLDrawableFactory.getFactory(caps.getGLProfile()).canCreateGLPbuffer(null, caps.getGLProfile()))
    {
      throw new GLException("pbuffers unsupported");
    }
    if(pbuffer!=null) pbuffer.destroy();
    System.out.println("initpbuffer3");
    pbuffer = GLDrawableFactory.getFactory(caps.getGLProfile()).createOffscreenAutoDrawable(null,
        caps,
        null,
        osdwidth,
        osdheight
        );
    pbuffer.setContext(pbuffer.createContext(c.getContext()), true);
    //pbuffer.setContext(c.getContext(), false);
    System.out.println("initpbuffer4: pbuffers is null? " + (pbuffer==null));
    if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
    {
      System.out.println("Couldn't make pbuffer current?");
      return;
    }
    System.out.println("initpbuffer5");
    gl = pbuffer.getGL().getGL2();

    gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);

    gl.glClear( gl.GL_COLOR_BUFFER_BIT);

    gl.glViewport(0, 0, osdwidth, osdheight);
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(0,osdwidth,0,osdheight,-1.0,1.0);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    gl.glLoadIdentity();

    // TODO: look into reusing same texture like OSX version...
    if(osdt!=null) gl.glDeleteTextures(1, osdt, 0);
    osdt = new int[1];
    byte img[] = new byte[osdwidth*osdheight*4];
    gl.glGenTextures(1, osdt, 0);
    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,osdt[0]);
    gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
    gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
    gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, osdwidth, osdheight, 0,
        gl.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));

    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, osdt[0]);
    gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, 0, 0, osdwidth, osdheight);
    gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    System.out.println("initpbuffer6");
    pbuffer.getContext().release();
    System.out.println("initpbuffer7");
  }

  private java.net.ServerSocket ss;

  void drawString(String text, int x, int y, java.awt.Color color, GL2 gl, com.jogamp.opengl.util.awt.TextRenderer tr)
  {
    //System.out.println("drawString "+ text + " x: "+x+" y: "+y);
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glPushMatrix();
    gl.glLoadIdentity();
    //System.out.println("glOrtho(0,"+ c.getWidth() + ","+c.getHeight()+",0,-1.0,1.0)");
    gl.glOrtho(0,c.getWidth(),0,c.getHeight(),-1.0,1.0);
    tr.begin3DRendering();

    tr.setColor(color);
    tr.draw3D(text, x, c.getHeight()-y, 0, 1);
    tr.end3DRendering();
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glPopMatrix();
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
  }

  public void display(GLAutoDrawable drawable)
  {
    GL2 gl;
    gl = drawable.getGL().getGL2();

    if(osdt==null) gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);

    videorenderer.drawVideo(gl, srcVideoBounds, videoBounds);
    if(osdt!=null)
    {
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,osdt[0]);
      gl.glEnable(gl.GL_BLEND);
      gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
      gl.glColor4f(1,1,1,1);
      gl.glBegin(gl.GL_QUADS);
      gl.glTexCoord2f(0, 0);
      gl.glVertex3f(0, 0, 1.0f);

      gl.glTexCoord2f(osdwidth, 0);
      gl.glVertex3f(osdwidth, 0, 1.0f);

      gl.glTexCoord2f(osdwidth,osdheight);
      gl.glVertex3f(osdwidth, osdheight, 1.0f);

      gl.glTexCoord2f(0, osdheight);
      gl.glVertex3f(0.0f, osdheight, 1.0f);
      gl.glEnd();
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    }
    else if(connectingDone==false)
    {
      try
      {
        bgImage =
            (java.awt.image.BufferedImage) javax.imageio.ImageIO.read(getClass().getClassLoader().getResource("images/Background.jpg"));
        logoImage =
            (java.awt.image.BufferedImage)
            javax.imageio.ImageIO.read(getClass().getClassLoader().getResource("images/SageLogo256.png"));
      }
      catch (Exception e)
      {
        System.out.println("ERROR:" + e);
        e.printStackTrace();
      }
      pleaseWaitFont = new java.awt.Font("Arial", java.awt.Font.BOLD, 32);
      gl.glClearColor( 0.0f, 0.0f, 0.0f, 1.0f);
      gl.glClear(gl.GL_COLOR_BUFFER_BIT);
      int bgtexturet[] = new int[1];
      int logotexturet[] = new int[1];
      java.awt.image.BufferedImage bi=(java.awt.image.BufferedImage)bgImage;
      gl.glGenTextures(1, bgtexturet, 0);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, bgtexturet[0]);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
      gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, bi.getWidth(), bi.getHeight(), 0,
          /*bi.getColorModel().hasAlpha() ?*/ gl.GL_RGBA /*: gl.GL_RGB*/,
          gl.GL_UNSIGNED_BYTE, getBufferFromBI(bi));
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

      java.awt.image.BufferedImage logobi=(java.awt.image.BufferedImage)logoImage;
      gl.glGenTextures(1, logotexturet, 0);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, logotexturet[0]);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
      gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, logobi.getWidth(), logobi.getHeight(), 0,
          /*bi.getColorModel().hasAlpha() ?*/ gl.GL_RGBA /*: gl.GL_RGB*/,
          gl.GL_UNSIGNED_BYTE, getBufferFromBI(logobi));
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

      com.jogamp.opengl.util.awt.TextRenderer tr =
          new com.jogamp.opengl.util.awt.TextRenderer(pleaseWaitFont, true, false);

      float x=0,y=0,width=newosdwidth,height=newosdheight;
      // Draw the background
      gl.glEnable(gl.GL_BLEND);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,bgtexturet[0]);
      gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
      gl.glColor4f(1.0f,1.0f,1.0f,1.0f);
      gl.glBegin(gl.GL_QUADS);
      gl.glTexCoord2f(0,0);
      gl.glVertex2f(x,y);
      gl.glTexCoord2f(bi.getWidth(),0);
      gl.glVertex2f(x+width,y);
      gl.glTexCoord2f(bi.getWidth(),bi.getHeight());
      gl.glVertex2f(x+width,y+height);
      gl.glTexCoord2f(0,bi.getHeight());
      gl.glVertex2f(x,y+height);
      gl.glEnd();
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

      x=newosdwidth*2/100;
      y=newosdheight*15/1000;
      width=newosdwidth*2/10;
      height=newosdheight*7/100;
      float imageAR = ((float)logobi.getWidth(null))/logobi.getHeight(null);
      if (((float)width/height) > imageAR)
        width = Math.round(height * imageAR);
      else
        height = Math.round(width / imageAR);

      // Draw the logo
      gl.glEnable(gl.GL_BLEND);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,logotexturet[0]);
      gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
      gl.glColor4f(1.0f,1.0f,1.0f,1.0f);

      gl.glBegin(gl.GL_QUADS);
      gl.glTexCoord2f(0,0);
      gl.glVertex2f(x,y);
      gl.glTexCoord2f(logobi.getWidth(),0);
      gl.glVertex2f(x+width,y);
      gl.glTexCoord2f(logobi.getWidth(),logobi.getHeight());
      gl.glVertex2f(x+width,y+height);
      gl.glTexCoord2f(0,logobi.getHeight());
      gl.glVertex2f(x,y+height);
      gl.glEnd();
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
      // Draw text message
      String str1 = "SageTV Placeshifter is connecting to";
      String str2 = "the server: " + myConn.getServerName();
      String str3 = "Please Wait...";
      java.awt.FontMetrics fm = c.getFontMetrics(pleaseWaitFont);

      if (myConn.isLocahostConnection())
        str2 = str3;

      int fh = fm.getHeight() * 4;
      int ty = (newosdheight / 2) - fh/2;
      // Draw shadow
      ty += 2;
      if (!myConn.isLocahostConnection())
        drawString(str1, 2 + (newosdwidth/2) - (fm.stringWidth(str1)/2), ty + fm.getAscent(),
            new java.awt.Color(0,0,0,255), gl, tr);
      ty += fm.getHeight();
      drawString(str2, 2 + (newosdwidth/2) - (fm.stringWidth(str2)/2), ty + fm.getAscent(),
          new java.awt.Color(0,0,0,255), gl, tr);
      ty += 2*fm.getHeight();
      if (!myConn.isLocahostConnection())
        drawString(str3, 2 + (newosdwidth/2) - (fm.stringWidth(str3)/2), ty + fm.getAscent(),
            new java.awt.Color(0,0,0,255), gl, tr);

      // Draw fg text
      ty = (newosdheight / 2) - fh/2;
      if (!myConn.isLocahostConnection())
        drawString(str1, (newosdwidth/2) - (fm.stringWidth(str1)/2), ty + fm.getAscent(),
            new java.awt.Color(255,255,255,255), gl, tr);
      ty += fm.getHeight();
      drawString(str2, (newosdwidth/2) - (fm.stringWidth(str2)/2), ty + fm.getAscent(),
          new java.awt.Color(255,255,255,255), gl, tr);
      ty += 2*fm.getHeight();
      if (!myConn.isLocahostConnection())
        drawString(str3, (newosdwidth/2) - (fm.stringWidth(str3)/2), ty + fm.getAscent(),
            new java.awt.Color(255,255,255,255), gl, tr);

      gl.glDeleteTextures(1, bgtexturet, 0);
      gl.glDeleteTextures(1, logotexturet, 0);
      connectingDone=true;
      // TODO verify if really needed, I think should be needed not to waste memory but Jeff doesn't do it...
      bgImage=null;
      logoImage=null;
    }
  }

  public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged)
  {
    System.out.println("display changed");
  }

  private void setGLColor(GL2 gl, int color)
  {
    //System.out.println(Integer.toHexString(color));
    gl.glColor4ub((byte)((color>>16)&0xFF), (byte)((color>>8)&0xFF),
        (byte)((color>>0)&0xFF), (byte)((color>>24)&0xFF));

  }

  private int interpColor(int c1, int c2, float pos)
  {
    int color=0;
    for(int i=0;i<4;i++)
    {
      int ct1, ct2;
      ct1=(c1>>(i<<3))&0xFF;
      ct2=(c2>>(i<<3))&0xFF;
      color=color | ((int) (ct1 + pos*(ct2-ct1)) << (i<<3));
    }
    return color;
  }

  private void setInterpolatedGLColor(GL2 gl,
      int tlc, int trc, int brc, int blc,
      float posx, float posy, float width, float height)
  {
    int color=0;
    for(int i=0;i<4;i++)
    {
      int tlc1, trc1, brc1, blc1, interp1, interp2;
      tlc1=(tlc>>(i<<3))&0xFF;
      trc1=(trc>>(i<<3))&0xFF;
      brc1=(brc>>(i<<3))&0xFF;
      blc1=(blc>>(i<<3))&0xFF;
      interp1 = (int) (tlc1+posy*(blc1-tlc1)/height);
      interp2 = (int) (trc1+posy*(brc1-trc1)/height);
      color=color | ((int) (interp1 + posx*(interp2-interp1)/width) << (i<<3));
    }
    //System.out.println(Integer.toHexString(color));
    gl.glColor4ub((byte)((color>>16)&0xFF), (byte)((color>>8)&0xFF),
        (byte)((color>>0)&0xFF), (byte)((color>>24)&0xFF));
  }

  private void drawGLCurve(GL2 gl, float x, float y, float width, float height,
      float angs, float ange, int subdiv, int tlc, int trc, int brc, int blc,
      int xc, int yc, int widthc, int heightc, boolean full,
      int thickness)
  {
    if(full)
    {
      gl.glBegin(gl.GL_TRIANGLE_FAN);
      setInterpolatedGLColor(gl, tlc, trc, brc, blc,
          x-xc, y-yc, widthc, heightc);
      gl.glVertex2f(x, y);
    }
    else
    {
      gl.glLineWidth(thickness);
      gl.glBegin(gl.GL_LINE_STRIP);
    }

    for(int i=0; i<=subdiv; i++)
    {
      float posx, posy;
      float ang=angs+(ange-angs)*i/subdiv;
      posx=width * (float)java.lang.Math.cos(ang);
      posy=height * (float)java.lang.Math.sin(ang);
      setInterpolatedGLColor(gl, tlc, trc, brc, blc,
          posx+x-xc, posy+y-yc, widthc, heightc);
      gl.glVertex2f(x + posx, y + posy);
      //System.out.println("x: "+(x+posx)+" y: "+(y+posy));
    }
    gl.glEnd();
  }

  public int ExecuteGFXCommand(final int cmd, final int len, final byte[] cmddata, final int[] hasret)
  {
    if (pbuffer != null && !pbuffer.getContext().isCurrent() && Threading.isSingleThreaded() && !Threading.isOpenGLThread())
    {
      final int[] retholder = new int[1];
      Threading.invokeOnOpenGLThread(true, new Runnable()
      {
        public void run()
        {
          retholder[0] = ExecuteGFXCommandSync(cmd, len, cmddata, hasret);
        }
      });
      return retholder[0];
    }
    else
      return ExecuteGFXCommandSync(cmd, len, cmddata, hasret);
  }
  private int ExecuteGFXCommandSync(int cmd, int len, byte[] cmddata, int[] hasret)
  {
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
    GL2 gl=null;
    if(pbuffer!=null) gl = pbuffer.getGL().getGL2();
    len -= 4; // for the 4 byte header
    hasret[0] = 0; // Nothing to return by default
    //System.out.println("GFXCMD=" + cmd);
    switch(cmd)
    {
      case GFXCMD_INIT:
        System.out.println("Native order: "+java.nio.ByteOrder.nativeOrder());
        hasret[0] = 1;
        GLCapabilities caps2 = new GLCapabilities(null);
        caps2.setDoubleBuffered(true);
        caps2.setStereo(false);
        c = new GLCanvas(caps2);
        c.setAutoSwapBufferMode(true);
        c.addGLEventListener(this);
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
            //System.out.println("LAYOUT frame bounds=" + f.getBounds() + " videoBounds=" + videoBounds + " parentBounds=" + parent.getBounds());
          }
        };
        f.getContentPane().setLayout(layer);
        f.getContentPane().add(c);
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
        f.setFocusTraversalKeysEnabled(false);
        c.setFocusTraversalKeysEnabled(false);
        f.addWindowListener(new java.awt.event.WindowAdapter()
        {
          public void windowClosing(java.awt.event.WindowEvent evt)
          {
            MiniClient.myProperties.setProperty("main_window_width", Integer.toString(f.getWidth()));
            MiniClient.myProperties.setProperty("main_window_height", Integer.toString(f.getHeight()));
            MiniClient.myProperties.setProperty("main_window_x", Integer.toString(f.getX()));
            MiniClient.myProperties.setProperty("main_window_y", Integer.toString(f.getY()));
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
            //myConn.postResizeEvent(new java.awt.Dimension(c.getWidth(), c.getHeight()));
          }
        });
        f.addKeyListener(this);
        c.addKeyListener(this);
        f.addMouseListener(this);
        f.addMouseWheelListener(this);
        c.addMouseListener(this);
        if (ENABLE_MOUSE_MOTION_EVENTS)
        {
          f.addMouseMotionListener(this);
          c.addMouseMotionListener(this);
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
        return 1;
      case GFXCMD_DEINIT:
        // Remove the pbuffer?
        // Discard pointers?
        //...
        if(MiniClient.MAC_OS_X || MiniClient.WINDOWS_OS) videorenderer.deinitVideoServer();
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
          gl.glLineWidth(thickness);
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          gl.glBegin(gl.GL_LINE_STRIP);
          setGLColor(gl, argbTL);
          gl.glVertex2f(x,y);
          setGLColor(gl, argbTR);
          gl.glVertex2f(x+width,y);
          setGLColor(gl, argbBR);
          gl.glVertex2f(x+width,y+height);
          setGLColor(gl, argbBL);
          gl.glVertex2f(x,y+height);
          setGLColor(gl, argbTL);
          gl.glVertex2f(x,y);
          gl.glEnd();
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
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          gl.glBegin(gl.GL_QUADS);
          setGLColor(gl, argbTL);
          gl.glVertex2f(x,y);
          setGLColor(gl, argbTR);
          gl.glVertex2f(x+width,y);
          setGLColor(gl, argbBR);
          gl.glVertex2f(x+width,y+height);
          setGLColor(gl, argbBL);
          gl.glVertex2f(x,y+height);
          gl.glEnd();
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
          if(width<0 || height < 0) break;
          argbTL=readInt(16, cmddata);
          argbTR=readInt(20, cmddata);
          argbBR=readInt(24, cmddata);
          argbBL=readInt(28, cmddata);
          gl.glDisable(gl.GL_BLEND);
          gl.glBegin(gl.GL_QUADS);
          setGLColor(gl, argbTL);
          gl.glVertex2f(x,y);
          setGLColor(gl, argbTR);
          gl.glVertex2f(x+width,y);
          setGLColor(gl, argbBR);
          gl.glVertex2f(x+width,y+height);
          setGLColor(gl, argbBL);
          gl.glVertex2f(x,y+height);
          gl.glEnd();
          gl.glEnable(gl.GL_BLEND);
          //System.out.println("Clear Rect: " + x + " " + y + " " + width + " " + height + " " + argbTL);
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
          gl.glEnable(gl.GL_SCISSOR_TEST);
          gl.glScissor(clipX, clipY, clipW, clipH);
          gl.glLineWidth(thickness);
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          drawGLCurve(gl, (float)x+width/2, (float)y+height/2,
              (float)width/2, (float)height/2,
              (float)0, (float) (2*java.lang.Math.PI), width+height,
              argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
              thickness);
          gl.glDisable(gl.GL_SCISSOR_TEST);
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
          gl.glEnable(gl.GL_SCISSOR_TEST);
          gl.glScissor(clipX, clipY, clipW, clipH);
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          drawGLCurve(gl, x+width/2, y+height/2, width/2, height/2,
              0, (float)(2*java.lang.Math.PI), 64,
              argbTL, argbTR, argbBR, argbBL,
              x, y, width, height, true,
              1);
          gl.glDisable(gl.GL_SCISSOR_TEST);
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
          gl.glEnable(gl.GL_SCISSOR_TEST);
          gl.glScissor(clipX, clipY, clipW, clipH);
          gl.glLineWidth(thickness);
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          arcRadius=arcRadius/2;
          drawGLCurve(gl, x+arcRadius, y+arcRadius, arcRadius, arcRadius,
              (float)(2.0/4.0*2*java.lang.Math.PI), (float)(3.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
              thickness);
          drawGLCurve(gl, x+width-arcRadius, y+arcRadius, arcRadius, arcRadius,
              (float)(3.0/4.0*2*java.lang.Math.PI), (float)(4.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
              thickness);
          drawGLCurve(gl, x+width-arcRadius, y+height-arcRadius, arcRadius, arcRadius,
              (float)(0.0/4.0*2*java.lang.Math.PI),(float)( 1.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
              thickness);
          drawGLCurve(gl, x+arcRadius, y+height-arcRadius, arcRadius, arcRadius,
              (float)(1.0/4.0*2*java.lang.Math.PI), (float)(2.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
              thickness);
          gl.glBegin(gl.GL_LINES);
          setGLColor(gl, interpColor(argbTL, argbTR, 1.0f*arcRadius/width));
          gl.glVertex2f(x+arcRadius,y);
          setGLColor(gl, interpColor(argbTL, argbTR, 1-1.0f*arcRadius/width));
          gl.glVertex2f(x+width-arcRadius,y);

          setGLColor(gl, interpColor(argbTR, argbBR, 1.0f*arcRadius/height));
          gl.glVertex2f(x+width,y+arcRadius);
          setGLColor(gl, interpColor(argbTR, argbBR, 1-1.0f*arcRadius/height));
          gl.glVertex2f(x+width,y+height-arcRadius);

          setGLColor(gl, interpColor(argbBR, argbBL, 1.0f*arcRadius/width));
          gl.glVertex2f(x+width-arcRadius,y+height);
          setGLColor(gl, interpColor(argbBR, argbBL, 1-1.0f*arcRadius/width));
          gl.glVertex2f(x+arcRadius,y+height);

          setGLColor(gl, interpColor(argbBL, argbTL, 1.0f*arcRadius/height));
          gl.glVertex2f(x,y+height-arcRadius);
          setGLColor(gl, interpColor(argbBL, argbTL, 1-1.0f*arcRadius/height));
          gl.glVertex2f(x,y+arcRadius);

          gl.glEnd();
          gl.glDisable(gl.GL_SCISSOR_TEST);
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
          gl.glEnable(gl.GL_SCISSOR_TEST);
          gl.glScissor(clipX, clipY, clipW, clipH);
          gl.glEnable(gl.GL_BLEND);
          gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
          arcRadius=arcRadius/2;
          drawGLCurve(gl, x+arcRadius, y+arcRadius, arcRadius, arcRadius,
              (float)(2.0/4.0*2*java.lang.Math.PI), (float)(3.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL,
              argbTR,
              argbBR,
              argbBL,
              x, y, width, height,
              true,
              1);
          drawGLCurve(gl, x+width-arcRadius, y+arcRadius, arcRadius, arcRadius,
              (float)(3.0/4.0*2*java.lang.Math.PI), (float)(4.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL,
              argbTR,
              argbBR,
              argbBL,
              x, y, width, height,
              true,
              1);
          drawGLCurve(gl, x+width-arcRadius, y+height-arcRadius, arcRadius, arcRadius,
              (float)(0.0/4.0*2*java.lang.Math.PI),(float)( 1.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL,
              argbTR,
              argbBR,
              argbBL,
              x, y, width, height,
              true,
              1);
          drawGLCurve(gl, x+arcRadius, y+height-arcRadius, arcRadius, arcRadius,
              (float)(1.0/4.0*2*java.lang.Math.PI), (float)(2.0/4.0*2*java.lang.Math.PI),
              arcRadius,
              argbTL,
              argbTR,
              argbBR,
              argbBL,
              x, y, width, height,
              true,
              1);
          gl.glBegin(gl.GL_QUADS);
          setGLColor(gl, interpColor(argbTL, argbTR, 1.0f*arcRadius/width));
          gl.glVertex2f(x+arcRadius,y);
          setGLColor(gl, interpColor(argbTL, argbTR, 1-1.0f*arcRadius/width));
          gl.glVertex2f(x+width-arcRadius,y);
          setGLColor(gl, interpColor(argbBR, argbBL, 1.0f*arcRadius/width));
          gl.glVertex2f(x+width-arcRadius,y+height);
          setGLColor(gl, interpColor(argbBR, argbBL, 1-1.0f*arcRadius/width));
          gl.glVertex2f(x+arcRadius,y+height);

          setGLColor(gl, interpColor(argbTR, argbBR, 1.0f*arcRadius/height));
          gl.glVertex2f(x+width,y+arcRadius);
          setGLColor(gl, interpColor(argbTR, argbBR, 1-1.0f*arcRadius/height));
          gl.glVertex2f(x+width,y+height-arcRadius);
          setGLColor(gl, interpColor(interpColor(argbBR, argbBL, 1.0f*arcRadius/width),
              interpColor(argbTR, argbTL, 1.0f*arcRadius/width), 1.0f*arcRadius/height));
          gl.glVertex2f(x+width-arcRadius,y+height-arcRadius);
          setGLColor(gl, interpColor(interpColor(argbBR, argbBL, 1.0f*arcRadius/width),
              interpColor(argbTR, argbTL, 1.0f*arcRadius/width), 1-1.0f*arcRadius/height));
          gl.glVertex2f(x+width-arcRadius,y+arcRadius);

          setGLColor(gl, interpColor(argbBL, argbTL, 1.0f*arcRadius/height));
          gl.glVertex2f(x,y+height-arcRadius);
          setGLColor(gl, interpColor(argbBL, argbTL, 1-1.0f*arcRadius/height));
          gl.glVertex2f(x,y+arcRadius);
          setGLColor(gl, interpColor(interpColor(argbTL, argbTR, 1.0f*arcRadius/width),
              interpColor(argbBL, argbBR, 1.0f*arcRadius/width), 1.0f*arcRadius/height));
          gl.glVertex2f(x+arcRadius,y+arcRadius);
          setGLColor(gl, interpColor(interpColor(argbTL, argbTR, 1.0f*arcRadius/width),
              interpColor(argbBL, argbBR, 1.0f*arcRadius/width), 1-1.0f*arcRadius/height));
          gl.glVertex2f(x+arcRadius,y+height-arcRadius);

          gl.glEnd();
          gl.glDisable(gl.GL_SCISSOR_TEST);
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

          com.jogamp.opengl.util.awt.TextRenderer tr = (com.jogamp.opengl.util.awt.TextRenderer) fontMap.get(new Integer(fontHandle));
          if (tr != null)
          {
            /*						tr.beginRendering(c.getWidth(), c.getHeight());
						gl.glMatrixMode(gl.GL_PROJECTION);
						gl.glLoadIdentity();
						gl.glOrtho(0,c.getWidth(),c.getHeight(),0,-1.0,1.0);
						gl.glMatrixMode(gl.GL_TEXTURE);

						tr.setColor(new java.awt.Color(argb));
						gl.glEnable(gl.GL_SCISSOR_TEST);
						gl.glScissor(clipX, clipY, clipW, clipH);

						tr.draw(text.toString(), x, c.getHeight()-y);
						gl.glDisable(gl.GL_SCISSOR_TEST);
						tr.endRendering();*/
            gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glOrtho(0,RSurfWidth,RSurfHeight,0,-1.0,1.0);
            tr.begin3DRendering();
            //						gl.glMatrixMode(gl.GL_PROJECTION);
            //						gl.glLoadIdentity();
            //						gl.glOrtho(0,c.getWidth(),c.getHeight(),0,-1.0,1.0);
            //						gl.glMatrixMode(gl.GL_TEXTURE);

            tr.setColor(new java.awt.Color(argb, true));
            gl.glEnable(gl.GL_SCISSOR_TEST);
            gl.glScissor(clipX, clipY, clipW, clipH);

            tr.draw3D(text.toString(), x, RSurfHeight-y, 0, 1);
            gl.glDisable(gl.GL_SCISSOR_TEST);
            tr.end3DRendering();
            gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
          }
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
          int texturet[] = (int []) imageMap.get(new Integer(handle));
          if (texturet != null)
          {
            myConn.registerImageAccess(handle);
            gl.glEnable(gl.GL_BLEND);
            gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
            if(texturet.length==4) // This is a rendering buffer
            {
              //System.out.println("Render with fb texture of : " + texturet[1]);
              gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,texturet[1]);
            }
            else
            {
              gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,texturet[0]);
            }
            gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
            if(height<0)
            {
              gl.glBlendFunc(gl.GL_ONE, gl.GL_ZERO);
              height*=-1;
            }
            if(width>=0) // not font
            {
              setGLColor(gl, blend);
              //gl.glColor4f(1.0f,1.0f,1.0f,1.0f);

              gl.glBegin(gl.GL_QUADS);
              gl.glTexCoord2f(srcx,srcy);
              gl.glVertex2f(x,y);
              gl.glTexCoord2f(srcx+srcwidth,srcy);
              gl.glVertex2f(x+width,y);
              gl.glTexCoord2f(srcx+srcwidth,srcy+srcheight);
              gl.glVertex2f(x+width,y+height);
              gl.glTexCoord2f(srcx,srcy+srcheight);
              gl.glVertex2f(x,y+height);
              gl.glEnd();
              gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
              gl.glColor4f(1,1,1,1);
            }
            else // font
            {
              setGLColor(gl, blend);
              width*=-1;
              gl.glBegin(gl.GL_QUADS);
              gl.glTexCoord2f(srcx,srcy);
              gl.glVertex2f(x,y);
              gl.glTexCoord2f(srcx+srcwidth,srcy);
              gl.glVertex2f(x+width,y);
              gl.glTexCoord2f(srcx+srcwidth,srcy+srcheight);
              gl.glVertex2f(x+width,y+height);
              gl.glTexCoord2f(srcx,srcy+srcheight);
              gl.glVertex2f(x,y+height);
              gl.glEnd();
              gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
              gl.glColor4f(1,1,1,1);
            }
            gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,0);
            gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
          }
          else
          {
            System.out.println("ERROR invalid handle passed for texture rendering of: " + handle);
            abortRenderCycle = true;
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
          int imghandle = handleCount++;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          if (width * height * 4 + imageCacheSize > imageCacheLimit)
            imghandle = 0;
          else
          {
            if(inframe==false)
            {
              ensurePbuffer();
              if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
              {
                System.out.println("Couldn't make pbuffer current?");
                return 0;
              }
              gl = pbuffer.getGL().getGL2();
            }
            int texturet[] = new int[1];
            byte img[] = new byte[width*height*4];
            gl.glGenTextures(1, texturet, 0);
            gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
            gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, texturet[0]);
            gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
            gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
            gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, width, height, 0,
                gl.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
            gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
            imageMap.put(new Integer(imghandle), texturet);
            imageCacheSize += width * height * 4;
            imageMapSizes.put(new Integer(imghandle), new Integer(4*width*height));
            if(inframe==false)
            {
              pbuffer.getContext().release();
            }
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
      case GFXCMD_LOADIMAGETARGETED:
        // handle, width, height // Not used unless we do uncompressed images
        if(len>=12)
        {
          int width, height;
          int imghandle = readInt(0, cmddata);
          width=readInt(4, cmddata);
          height=readInt(8, cmddata);
          while (width * height * 4 + imageCacheSize > imageCacheLimit)
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache oldestImage=" + oldestImage + " imageCacheSize=" + imageCacheSize + " imageCacheLimit=" + imageCacheLimit + " newSize=" + (width * height * 4));
              unloadImage(gl, oldestImage);
              myConn.postImageUnload(oldestImage);
            }
            else
            {
              System.out.println("ERROR cannot free enough from the cache to support loading a new image!!!");
              break;
            }
          }
          if(inframe==false)
          {
            ensurePbuffer();
            if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
            {
              System.out.println("Couldn't make pbuffer current?");
              return 0;
            }
            gl = pbuffer.getGL().getGL2();
          }
          int texturet[] = new int[1];
          byte img[] = new byte[width*height*4];
          gl.glGenTextures(1, texturet, 0);
          gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
          gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, texturet[0]);
          gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
          gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
          gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, width, height, 0,
              gl.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
          gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
          imageMap.put(new Integer(imghandle), texturet);
          imageCacheSize += width * height * 4;
          imageMapSizes.put(new Integer(imghandle), new Integer(4*width*height));
          if(inframe==false)
          {
            pbuffer.getContext().release();
          }
          myConn.registerImageAccess(imghandle);

          hasret[0]=0;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGETARGETED : " + len);
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
          else if (len >= 12)
          {
            // We've got enough room for it and there's a cache ID, check if we've got it cached locally
            int strlen = readInt(8, cmddata);
            if (strlen > 1)
            {
              try
              {
                String rezName = new String(cmddata, 16, strlen - 1);
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
                  }
                  else
                  {
                    imghandle = handleCount++;
                    if(inframe==false)
                    {
                      ensurePbuffer();
                      if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
                      {
                        System.out.println("Couldn't make pbuffer current?");
                        return 0;
                      }
                      gl = pbuffer.getGL().getGL2();
                    }

                    int texturet[] = new int[1];
                    gl.glGenTextures(1, texturet, 0);
                    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
                    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, texturet[0]);
                    gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
                    gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
                    gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, rawImg.getWidth(), rawImg.getHeight(), 0,
                        /*bi.getColorModel().hasAlpha() ?*/ gl.GL_RGBA /*: gl.GL_RGB*/,
                        gl.GL_UNSIGNED_BYTE, fixPixelOrder(rawImg.getData()));
                    gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

                    if(inframe==false)
                    {
                      pbuffer.getContext().release();
                    }
                    imageMap.put(new Integer(imghandle), texturet);

                    imageCacheSize += width * height * 4;
                    imageMapSizes.put(new Integer(imghandle), new Integer(width*height*4));
                    sage.media.image.ImageLoader.freeImage(rawImg);
                    hasret[0] = 1;
                    return -1 * imghandle;
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
          while (width * height * 4 + imageCacheSize > imageCacheLimit)
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache oldestImage=" + oldestImage + " imageCacheSize=" + imageCacheSize + " imageCacheLimit=" + imageCacheLimit + " newSize=" + (width * height * 4));
              unloadImage(gl, oldestImage);
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
          while (width * height * 4 + imageCacheSize > imageCacheLimit)
          {
            // Keep freeing the oldest image until we have enough memory to do this
            int oldestImage = myConn.getOldestImage();
            if (oldestImage != 0)
            {
              System.out.println("Freeing image to make room in cache oldestImage=" + oldestImage + " imageCacheSize=" + imageCacheSize + " imageCacheLimit=" + imageCacheLimit + " newSize=" + (width * height * 4));
              unloadImage(gl, oldestImage);
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
              // We've got it locally in our cache! Read it from there.
              sage.media.image.RawImage rawImg = sage.media.image.ImageLoader.loadImageFromFile(cachedFile.getAbsolutePath());
              if (rawImg == null || rawImg.getWidth() != width || rawImg.getHeight() != height || rawImg.getHeight() == 0 || rawImg.getWidth() == 0)
              {
                // It doesn't match the cache
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
                if(inframe==false)
                {
                  ensurePbuffer();
                  if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
                  {
                    System.out.println("Couldn't make pbuffer current?");
                    // This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
                    myConn.postImageUnload(imghandle);
                    return 0;
                  }
                  gl = pbuffer.getGL().getGL2();
                }

                int texturet[] = new int[1];
                gl.glGenTextures(1, texturet, 0);
                gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
                gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, texturet[0]);
                gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
                gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
                gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, rawImg.getWidth(), rawImg.getHeight(), 0,
                    /*bi.getColorModel().hasAlpha() ?*/ gl.GL_RGBA /*: gl.GL_RGB*/,
                    gl.GL_UNSIGNED_BYTE, fixPixelOrder(rawImg.getData()));
                gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

                if(inframe==false)
                {
                  pbuffer.getContext().release();
                }
                imageMap.put(new Integer(imghandle), texturet);

                imageCacheSize += width * height * 4;
                imageMapSizes.put(new Integer(imghandle), new Integer(width*height*4));
                sage.media.image.ImageLoader.freeImage(rawImg);
                hasret[0] = 0;
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
                if(inframe==false)
                {
                  ensurePbuffer();
                  if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
                  {
                    System.out.println("Couldn't make pbuffer current?");
                    return 0;
                  }
                  gl = pbuffer.getGL().getGL2();
                }
                int texturet[] = new int[1];
                gl.glGenTextures(1, texturet, 0);
                gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
                gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, texturet[0]);
                gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
                gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
                gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, rawImg.getWidth(), rawImg.getHeight(), 0,
                    /*bi.getColorModel().hasAlpha() ?*/ gl.GL_RGBA /*: gl.GL_RGB*/,
                    gl.GL_UNSIGNED_BYTE, fixPixelOrder(rawImg.getData()));
                gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

                if(inframe==false)
                {
                  pbuffer.getContext().release();
                }
                imageMap.put(new Integer(handle), texturet);
                imageCacheSize += rawImg.getWidth() * rawImg.getHeight() * 4;
                imageMapSizes.put(new Integer(handle), new Integer(rawImg.getWidth()*rawImg.getHeight()*4));
                sage.media.image.ImageLoader.freeImage(rawImg);
                if (deleteCacheFile)
                  cacheFile.delete();
                return handle;
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
      case GFXCMD_UNLOADIMAGE:
        // handle
        if(len==4)
        {
          int handle;
          handle=readInt(0, cmddata);
          unloadImage(gl, handle);
          myConn.clearImageAccess(handle);
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
          if (myConn.hasFontServer())
          {
            // Check in the cache for the font since we load them from the server
            if (fonty.getFamily().equals("Dialog"))
            {
              if (!cachedFontMap.containsKey(cacheName))
              {
                // Fonts in the Dialog family are the default ones that come with Java that cause problems, so
                // load this from the other way if it's here.
                java.io.File cachedFile = myConn.getCachedImageFile(cacheName + "-" + myConn.getServerName());
                if (cachedFile != null)
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
                fonty = cachedFont.deriveFont(/*style,*/(float) size);
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
          fontMap.put(new Integer(fonthandle), new com.jogamp.opengl.util.awt.TextRenderer(fonty, true, false));
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
          ((com.jogamp.opengl.util.awt.TextRenderer)fontMap.remove(new Integer(handle))).dispose();
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADFONT : " + len);
        }
        break;
      case GFXCMD_LOADFONTSTREAM:
        // namelen, name, len, data
        if (len>=8)
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
            myConn.saveCacheData(name.toString() + "-" + myConn.getServerName(), cmddata, 12 + namelen, datalen);
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADFONTSTREAM : " + len);
        }
        break;
      case GFXCMD_FLIPBUFFER:
        hasret[0] = 1;
        //System.out.println("Flipbuffer");
        firstFrameDone = true;
        if (abortRenderCycle)
        {
          System.out.println("ERROR in painting cycle, ABORT was set...send full repaint command");
          myConn.postRepaintEvent(0, 0, c.getWidth(), c.getHeight());
        }
        else
        {
          gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
          gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, osdt[0]);
          gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, 0, 0, osdwidth, osdheight);
          // NOTE: IF WE HAVE ISSUES WITH THE OpenGL placeshifter we may need to put this glFlush outside of this conditional
          gl.glFlush();
          gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
        }
        pbuffer.getContext().release();
        inframe=false;
        c.repaint();
        //STBGFX.GFX_flipBuffer();
        return 0;
      case GFXCMD_STARTFRAME:
        ensurePbuffer();
        if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
        {
          System.out.println("Couldn't make pbuffer current?");
          return 0;
        }
        gl = pbuffer.getGL().getGL2();
        inframe=true;
        RSurfWidth=osdwidth;
        RSurfHeight=osdheight;
        abortRenderCycle = false;
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
          int texturet[] = (int []) imageMap.get(new Integer(handle));
          int texturedata[] = new int[len2];
          for (int i = 0; i < len2/4; i++, dataPos += 4)
          {
            texturedata[i]=readInt(dataPos, cmddata);
          }
          if(inframe==false)
          {
            ensurePbuffer();
            if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
            {
              System.out.println("Couldn't make pbuffer current?");
              return 0;
            }
            gl = pbuffer.getGL().getGL2();
          }
          gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
          gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,texturet[0]);
          gl.glTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, line, len2/4, 1,
              gl.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE
                  , java.nio.IntBuffer.wrap(texturedata));
          gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
          if(inframe==false)
          {
            pbuffer.getContext().release();
          }
          myConn.registerImageAccess(handle);
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_LOADIMAGELINE : " + len);
        }
        break;
      case GFXCMD_CREATESURFACE:
        // width, height
        if(len==8)
        {
          int width, height;
          int imghandle=0;
          width=readInt(0, cmddata);
          height=readInt(4, cmddata);
          int fbttexturet[] = new int[4]; // Store both in same array for now to put in imageMap...
          fbttexturet[2]=width;
          fbttexturet[3]=height;
          gl.glGenFramebuffers(1, fbttexturet, 0);
          gl.glGenTextures(1, fbttexturet, 1);
          gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, fbttexturet[0]);
          gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
          gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, fbttexturet[1]);
          gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
          gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
          gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, width, height, 0,
              gl.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE, null);
          gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER,
              gl.GL_COLOR_ATTACHMENT0,
              gl.GL_TEXTURE_RECTANGLE, fbttexturet[1], 0);
          int status = gl.glCheckFramebufferStatus(gl.GL_FRAMEBUFFER);
          gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0);
          gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, 0);
          gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
          if(status==gl.GL_FRAMEBUFFER_COMPLETE)
          {
            imghandle = handleCount++;
            //System.out.println("Created fb " + fbttexturet[0] + " with texture " + fbttexturet[1] +
            //	" on handle "+ imghandle);
            imageMap.put(new Integer(imghandle), fbttexturet);
            imageMapSizes.put(new Integer(imghandle), new Integer(4*width*height));
          }
          else
          {
            imghandle = 0;
            gl.glDeleteFramebuffers(1, fbttexturet, 0);
            gl.glDeleteTextures(1, fbttexturet, 1);
            System.out.println("error in status of framebuffer object " + status);
          }
          hasret[0]=1;
          return imghandle;
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_CREATESURFACE : " + len);
        }
        break;
      case GFXCMD_SETTARGETSURFACE:
        // handle
        if(len==4)
        {
          int handle;
          handle=readInt(0, cmddata);
          //STBGFX.GFX_unloadImage(handle);
          //System.out.println("Set target surface " + handle);
          if (handle == 0) // back to main rendering surface
          {
            gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, 0);
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0);
            gl.glViewport(0, 0, osdwidth, osdheight);
            gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glOrtho(0,osdwidth,0,osdheight,-1.0,1.0);
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glLoadIdentity();
            RSurfWidth=osdwidth;
            RSurfHeight=osdheight;
          }
          else
          {
            int [] fbttexturet = (int []) imageMap.get(new Integer(handle));
            if (fbttexturet != null)
            {
              //System.out.println("Set target fb " + fbttexturet[0]);
              gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, 0);
              gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, fbttexturet[0]);
              gl.glViewport(0, 0, fbttexturet[2], fbttexturet[3]);
              gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
              gl.glLoadIdentity();
              gl.glOrtho(0,fbttexturet[2],0,fbttexturet[3],-1.0,1.0);
              gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
              gl.glLoadIdentity();
              RSurfWidth=fbttexturet[2];
              RSurfHeight=fbttexturet[3];
            }
          }
        }
        else
        {
          System.out.println("Invalid len for GFXCMD_UNLOADIMAGE : " + len);
        }
        break;
      case GFXCMD_SETVIDEOPROP:
        if (len >= 40)
        {
          java.awt.Rectangle srcRect = new java.awt.Rectangle(readInt(4, cmddata), readInt(8, cmddata),
              readInt(12, cmddata), readInt(16, cmddata));
          java.awt.Rectangle destRect = new java.awt.Rectangle(readInt(20, cmddata), readInt(24, cmddata),
              readInt(28, cmddata), readInt(32, cmddata));
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
      default:
        return -1;
    }
    return 0;
  }

  //	NOTE: THIS NEEDS TO BE FIXED FOR MAC, THIS IS TEMPORARY TO GET IT TO COMPILE
  public String getVideoOutParams()
  {
    String params;
    params = videorenderer.getServerVideoOutParams();
    System.out.println("video out params: " + params);
    return params;
  }

  public java.nio.ByteBuffer getBufferFromBI(java.awt.image.BufferedImage bi)
  {
    java.nio.ByteBuffer imageBuffer = null;
    java.awt.image.WritableRaster raster;
    java.awt.image.BufferedImage texImage;

    raster = java.awt.image.Raster.createInterleavedRaster(java.awt.image.DataBuffer.TYPE_BYTE,
        bi.getWidth(), bi.getHeight(), 4, null);
    texImage = new java.awt.image.BufferedImage(glAlphaColorModel,raster,true,new java.util.Hashtable());

    java.awt.Graphics g = texImage.getGraphics();
    g.drawImage(bi,0,0,null);
    g.dispose();

    // Make sure that alpha is pre-multiplied
    texImage.coerceData(true);

    byte[] data = ((java.awt.image.DataBufferByte) texImage.getRaster().getDataBuffer()).getData();

    imageBuffer = java.nio.ByteBuffer.allocateDirect(data.length);
    imageBuffer.order(java.nio.ByteOrder.nativeOrder());
    imageBuffer.put(data, 0, data.length);
    imageBuffer.flip();
    return imageBuffer;
  }

  public boolean isInFrame()
  {
    return inframe;
  }
  public GLOffscreenAutoDrawable getPbuffer()
  {
    return pbuffer;
  }
  public int getCanvasHeight()
  {
    return c.getHeight();
  }
  public void videoWasUpdated()
  {
    c.repaint();
  }

  // This switches it from ARGB to be RGBA
  public java.nio.ByteBuffer fixPixelOrder(java.nio.ByteBuffer bb)
  {
    int size = bb.capacity();
    for (int i = 0; i < size; i+=4)
    {
      byte alpha = bb.get(i);
      bb.put(i, bb.get(i + 1));
      bb.put(i + 1, bb.get(i + 2));
      bb.put(i + 2, bb.get(i + 3));
      bb.put(i + 3, alpha);
    }
    return bb;
  }

  private void unloadImage(GL2 gl, int handle)
  {
    int texturet[] = (int []) imageMap.get(new Integer(handle));
    // This can happen when we unload an image and then notify the server about it being unloaded...this results in a callback made to
    // us to tell us to unload the image because of how that code path works on the server.
    if (texturet != null)
    {
      if(inframe==false)
      {
        if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
        {
          System.out.println("Couldn't make pbuffer current?");
          return;
        }
        gl = pbuffer.getGL().getGL2();
      }
      if(texturet.length==4) // fb object
      {
        gl.glDeleteFramebuffers(1, texturet, 0);
        gl.glDeleteTextures(1, texturet, 1);
      }
      else
      {
        imageCacheSize -= ((Integer) imageMapSizes.get(new Integer(handle))).intValue();
        gl.glDeleteTextures(1, texturet, 0);
      }
      if(inframe==false)
      {
        pbuffer.getContext().release();
      }
    }
    imageMap.remove(new Integer(handle));
    imageMapSizes.remove(new Integer(handle));
    myConn.clearImageAccess(handle);
  }

  private void ensurePbuffer()
  {
    boolean createpbuffer=false;
    synchronized(newpbufferlock)
    {
      if(newpbuffer)
      {
        createpbuffer=true;
        newpbuffer=false;
      }
    }
    if(createpbuffer) initpbuffer();
  }

  public boolean createVideo(int width, int height, int format)
  {
    return videorenderer.createVideo(width, height, format);
  }

  public boolean updateVideo(int frametype, java.nio.ByteBuffer buf)
  {
    return videorenderer.updateVideo(frametype, buf);
  }

}
