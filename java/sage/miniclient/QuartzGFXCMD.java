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

public class QuartzGFXCMD extends GFXCMD2
{
	private native long createNSViewLong0();

	public void setVideoBounds(java.awt.Rectangle srcRect, java.awt.Rectangle destRect)
	{
		super.setVideoBounds(srcRect, destRect);
		if (srcRect != null && destRect != null)
		{
			java.awt.geom.Rectangle2D.Float srcRectf = new java.awt.geom.Rectangle2D.Float(srcRect.x, srcRect.y, srcRect.width, srcRect.height);
			java.awt.geom.Rectangle2D.Float destRectf = new java.awt.geom.Rectangle2D.Float(destRect.x, destRect.y, destRect.width, destRect.height);

			setVideoRects0(srcRectf, destRectf, new java.awt.Color(0, true));
		}
	}

		// quick hack to get fonts looking correct since inter character spacing is different between AWT and Cocoa
	private class McFont {
		private java.awt.Font awtFont = null;
		private java.awt.font.FontRenderContext frc = null;
		public long nativeFont = 0;

		public McFont(String name, int style, int size) {
			frc = new java.awt.font.FontRenderContext(null, true, false);	// same as in MetaFont, so calculations should be the same here...
			awtFont = new java.awt.Font(name, style, size);
			nativeFont = loadFont0(name, style, size);
		}

		public void unload() {
			if(nativeFont != 0) unloadFont0(nativeFont);
			nativeFont = 0;
			awtFont = null;
		}

		public java.awt.Dimension getStringSize(String s) {
			java.awt.geom.Rectangle2D bounds = awtFont.getStringBounds(s, frc);
			return new java.awt.Dimension((int)bounds.getWidth(), (int)bounds.getHeight());
		}

		public float[] getGlyphPositions(String s) {
			java.awt.font.GlyphVector gvec = awtFont.createGlyphVector(frc, s);
//			System.out.println("getGlyphPositions gvec="+gvec);
			if(gvec != null) {
				float[] positions = gvec.getGlyphPositions(0, gvec.getNumGlyphs(), null);
				return positions;
			}
			return null;
		}
	}

		// CocoaComponent derives from java.awt.Canvas
	private class QuartzRendererView extends com.apple.eawt.CocoaComponent {
/*		public void setBounds(int x, int y, int width, int height)
		{
			System.out.println("QRV.setBounds("+x+","+y+","+width+","+height+")");
			super.setBounds(x,y,width,height);
		}

		public void setBounds(java.awt.Rectangle r)
		{
			System.out.println("QRV.setBounds("+r+")");
			super.setBounds(r);
		}
*/
		public long nativeView = 0;
//		private native long createNSViewLong0();

		public long createNSViewLong() {
			nativeView = createNSViewLong0();
			return nativeView;
		}

		// *sigh* ignore the deprecation warnings
		public int createNSView() {
			return (int)createNSViewLong();
		}

		// CocaComponent abstracts
		final java.awt.Dimension PREF_SIZE = new java.awt.Dimension(720,480);
		final java.awt.Dimension MIN_SIZE = new java.awt.Dimension(20,20);
		final java.awt.Dimension MAX_SIZE = new java.awt.Dimension(4096,4096); // Baud help us if we ever see this size...

		public java.awt.Dimension getPreferredSize() {
			return PREF_SIZE;
		}

		public java.awt.Dimension getMinimumSize() {
			return MIN_SIZE;
		}

		public java.awt.Dimension getMaximumSize() {
			return MAX_SIZE;
		}

		// all rendering is done on the native side, so override these to do nothing...
		public void update(java.awt.Graphics g) {}
		public void paint(java.awt.Graphics g) {}
	}

	public QuartzGFXCMD(MiniClientConnection myConn)
	{
		super(myConn);
		imageCacheLimit = 32000000;
		try
		{
			imageCacheLimit = Integer.parseInt(MiniClient.myProperties.getProperty("image_cache_size", "32000000"));
		}
		catch (Exception e)
		{
			System.out.println("Invalid image_cache_size property:" + e);
		}
	}

	private QuartzRendererView c;

	private java.awt.Graphics2D g2;
	private java.awt.Graphics2D primaryG2;
	private java.util.Map imageMap = new java.util.HashMap();
	private java.util.Map layerMap = new java.util.HashMap();
	private int handleCount = 2;
	private long hideTime = 0;
	private java.util.TimerTask hideTimer;

	private long currentLayer = 0;

	public void close()
	{
		if (f != null)
			f.dispose();
		c = null;
		cancelHideTimer();
		cleanupRenderer();
	}

	public void refresh()
	{
		c.invalidate();
		f.invalidate();
		f.validate();
	}

	private void cleanupRenderer()
	{
		java.util.Iterator iter;

		// free all images
		if(!imageMap.isEmpty()) {
			iter = imageMap.values().iterator();
			while(iter.hasNext()) {
				Long imagePtr = (Long)iter.next();
				if(imagePtr != null) freeNativeImage0(imagePtr.longValue());
			}

			imageMap.clear();
		}

		// free all layers
		if(!layerMap.isEmpty()) {
			iter = layerMap.values().iterator();
			while(iter.hasNext()) {
				Long layerPtr = (Long)iter.next();
				if(layerPtr != null) freeLayer0(layerPtr.longValue());
			}

			layerMap.clear();
		}

		// free all fonts
		if(!fontMap.isEmpty()) {
			iter = fontMap.values().iterator();
			while(iter.hasNext()) {
				McFont f = (McFont)iter.next();
				if(f != null) f.unload();
			}

			fontMap.clear();
		}

		// call cleanupRenderer0 to tell native side to clean up
		cleanupRenderer0();
	}

	public MiniClientWindow getWindow()
	{
		return f;
	}

	private java.awt.GradientPaint getGradient(float x, float y, float width, float height, int argbTL, int argbTR, int argbBL, int argbBR)
	{
		float x2 = x + width;
		float y2 = y + height;

		if (argbTL != argbTR || argbTL != argbBL || argbTL != argbBR)
		{
			if (Math.abs(argbTL - argbTR) >= Math.abs(argbTL - argbBL) &&
				Math.abs(argbTL - argbTR) >= Math.abs(argbTL - argbBR))
				return new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL, true), x2, y, new java.awt.Color(argbTR, true));
			else if (Math.abs(argbTL - argbBL) < Math.abs(argbTL - argbBR))
				return new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL, true), x2, y2, new java.awt.Color(argbBR, true));
			else
				return new java.awt.GradientPaint(x, y, new java.awt.Color(argbTL, true), x, y2, new java.awt.Color(argbBL, true));
		}

		return null;
	}

	public int ExecuteGFXCommand(int cmd, int len, byte[] cmddata, int[] hasret)
	{
		len -= 4; // for the 4 byte header
	    hasret[0] = 0; // Nothing to return by default
//		System.out.println("GFXCMD=" + cmd);
		// make sure the frame is still valid or we could crash on fullscreen mode switches
		if((cmd != GFXCMD_INIT) && (cmd != GFXCMD_DEINIT))
		{
			if((f != null) ? (!f.isDisplayable() || !f.isValid() || !f.isShowing()) : true) {
//				System.out.println("GFXCMD while frame not displayable");
				// spin until the frame is valid and displayable, if we don't we'll lose parts of the UI or crash
				while((f != null) ? (!f.isDisplayable() || !f.isValid() || !f.isShowing()) : true) {
					try {
						Thread.sleep(10);
					} catch(InterruptedException ex) {}
				}
			}
		}

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
		switch(cmd)
		{
			case GFXCMD_INIT:
				hasret[0] = 1;
//				System.out.println("INIT");

				// start up native renderer
				init0();

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
				f.setFocusTraversalKeysEnabled(false);

				/*
					if not connecting to localhost:
						- draw background to bounds (scaled)
						- draw logo to {{2% from left, 15% from top}{20% view width, 7% view height}}, no clipping, alpha = 0.85, adjust size to keep aspect ratio
						- load Arial 32 bold
						- draw the following text, double spaced using Arial 32 bold, white with black shadow (offset by (+2,+2))
							"SageTV Placeshifter is connecting to"
							"the server: "+myConn.getServerName()
							"Please Wait..."

						text is centered in the view on the middle line, use font metrics to determine proper location

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
				 */

				java.awt.Dimension panelSize = f.getContentPane().getSize();

				c = new QuartzRendererView();
				c.setSize(panelSize);
				c.setFocusTraversalKeysEnabled(false);

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
						close();
//						f.dispose();
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
				//f.addMouseListener(this);
				f.addMouseWheelListener(this);
				c.addMouseListener(this);
				if (ENABLE_MOUSE_MOTION_EVENTS)
				{
					//f.addMouseMotionListener(this);
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
				f.setVisible(true);
				f.setSize(1,1);
				f.setSize(Math.max(frameW, 320), Math.max(frameH, 240));

				f.setLocation(newPos);
				if (MiniClient.fsStartup)
					f.setFullScreen(true);
				MiniClient.hideSplash();

//				f.setVisible(true);

				return 1;
			case GFXCMD_DEINIT:
//				System.out.println("DEINIT");
				close();
				break;
			case GFXCMD_DRAWRECT:
				if(len==36)
				{
					float x, y, width, height;
					int thickness, argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					thickness=readInt(16, cmddata);
					argbTL=readInt(20, cmddata);
					argbTR=readInt(24, cmddata);
					argbBR=readInt(28, cmddata);
					argbBL=readInt(32, cmddata);

//					System.out.println("DRAWRECT: dest=("+x+","+y+" "+width+"x"+height+") thickness="+thickness+" argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					// FIXME: no gradients on framed rects yet...
					drawRect0(new java.awt.geom.Rectangle2D.Float(x, y, width, height), null,
							  0,
							  new java.awt.Color(argbTL, true), thickness,
							  null, 0.0f, 0.0f,
							  null, 0.0f, 0.0f,
							  1.0f);
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
					float x, y, width, height;
					int argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					argbTL=readInt(16, cmddata);
					argbTR=readInt(20, cmddata);
					argbBR=readInt(24, cmddata);
					argbBL=readInt(28, cmddata);

//					System.out.println("FILLRECT: dest=("+x+","+y+" "+width+"x"+height+") argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					java.awt.GradientPaint gp = getGradient(x, y, width, height, argbTL, argbTR, argbBL, argbBR);
					java.awt.geom.Rectangle2D.Float bounds = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					if(gp != null) {
						drawRect0(bounds, null,
								  0,
								  null, 0,
								  gp.getColor1(), (float)gp.getPoint1().getX(), (float)gp.getPoint1().getY(),
								  gp.getColor2(), (float)gp.getPoint2().getX(), (float)gp.getPoint2().getY(),
								  //(float)((argbTL>>24)&0xff)/255.0f);
								  1.0f); // alpha already supplied
					} else {
						drawRect0(bounds, null,
								  0,
								  null, 0,
								  new java.awt.Color(argbTL, true), 0.0f, 0.0f,
								  null, 0.0f, 0.0f,
								  1.0f);
					}
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

//					System.out.println("CLEARRECT: dest=("+x+","+y+" "+width+"x"+height+") argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					java.awt.geom.Rectangle2D.Float destRect = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					clearRect0(destRect);
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
					float x, y, width, height, clipX, clipY, clipW, clipH;
					int thickness, argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					thickness=readInt(16, cmddata);
					argbTL=readInt(20, cmddata);
					argbTR=readInt(24, cmddata);
					argbBR=readInt(28, cmddata);
					argbBL=readInt(32, cmddata);
					clipX=(float)readInt(36, cmddata);
					clipY=(float)readInt(40, cmddata);
					clipW=(float)readInt(44, cmddata);
					clipH=(float)readInt(48, cmddata);

//					System.out.println("DRAWOVAL: dest=("+x+","+y+" "+width+"x"+height+") clip=("+clipX+","+clipY+" "+clipW+"x"+clipH+") thickness="+thickness+" argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					// FIXME: no gradient for framed ovals
					java.awt.geom.Rectangle2D.Float bounds = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					java.awt.geom.Rectangle2D.Float clipRect = new java.awt.geom.Rectangle2D.Float(clipX, clipY, clipW, clipH);
					drawOval0(bounds, clipRect,
							  new java.awt.Color(argbTL, true), thickness,
							  null, 0.0f, 0.0f,
							  null, 0.0f, 0.0f,
							  1.0f);
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
					float x, y, width, height,
						clipX, clipY, clipW, clipH;
					int argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					argbTL=readInt(16, cmddata);
					argbTR=readInt(20, cmddata);
					argbBR=readInt(24, cmddata);
					argbBL=readInt(28, cmddata);
					clipX=(float)readInt(32, cmddata);
					clipY=(float)readInt(36, cmddata);
					clipW=(float)readInt(40, cmddata);
					clipH=(float)readInt(44, cmddata);

//					System.out.println("FILLOVAL: dest=("+x+","+y+" "+width+"x"+height+") clip=("+clipX+","+clipY+" "+clipW+"x"+clipH+") argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					java.awt.GradientPaint gp = getGradient(x, y, width, height, argbTL, argbTR, argbBL, argbBR);
					java.awt.geom.Rectangle2D.Float bounds = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					java.awt.geom.Rectangle2D.Float clipRect = new java.awt.geom.Rectangle2D.Float(clipX, clipY, clipW, clipH);
					if(gp != null) {
						drawOval0(bounds, clipRect,
								  null, 0,
								  gp.getColor1(), (float)gp.getPoint1().getX(), (float)gp.getPoint1().getY(),
								  gp.getColor2(), (float)gp.getPoint2().getX(), (float)gp.getPoint2().getY(),
								  1.0f);
					} else {
						drawOval0(bounds, clipRect,
								  null, 0,
								  new java.awt.Color(argbTL, true), 0.0f, 0.0f,
								  null, 0.0f, 0.0f,
								  1.0f);
					}
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
					float x, y, width, height,
						clipX, clipY, clipW, clipH;
					int thickness, arcRadius,
						argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					thickness=readInt(16, cmddata);
					arcRadius=readInt(20, cmddata);
					argbTL=readInt(24, cmddata);
					argbTR=readInt(28, cmddata);
					argbBR=readInt(32, cmddata);
					argbBL=readInt(36, cmddata);
					clipX=(float)readInt(40, cmddata);
					clipY=(float)readInt(44, cmddata);
					clipW=(float)readInt(48, cmddata);
					clipH=(float)readInt(52, cmddata);

//					System.out.println("DRAWROUNDRECT: dest=("+x+","+y+" "+width+"x"+height+") clip=("+clipX+","+clipY+" "+clipW+"x"+clipH+") thickness="+thickness+" arcRadius="+arcRadius+" argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					// FIXME: no gradients on stroked shapes
					java.awt.geom.Rectangle2D.Float bounds = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					java.awt.geom.Rectangle2D.Float clipRect = new java.awt.geom.Rectangle2D.Float(clipX, clipY, clipW, clipH);
					drawRect0(bounds, clipRect,
							  arcRadius,
							  new java.awt.Color(argbTL, true), thickness,
							  null, 0.0f, 0.0f,
							  null, 0.0f, 0.0f,
							  1.0f);
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
					float x, y, width, height,
						clipX, clipY, clipW, clipH;
					int arcRadius, argbTL, argbTR, argbBR, argbBL;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					arcRadius=readInt(16, cmddata);
					argbTL=readInt(20, cmddata);
					argbTR=readInt(24, cmddata);
					argbBR=readInt(28, cmddata);
					argbBL=readInt(32, cmddata);
					clipX=(float)readInt(36, cmddata);
					clipY=(float)readInt(40, cmddata);
					clipW=(float)readInt(44, cmddata);
					clipH=(float)readInt(48, cmddata);

//					System.out.println("FILLROUNDRECT: dest=("+x+","+y+" "+width+"x"+height+") clip=("+clipX+","+clipY+" "+clipW+"x"+clipH+") arcRadius="+arcRadius+" argbTL="+Integer.toHexString(argbTL)+" argbTR="+Integer.toHexString(argbTR)+" argbBL="+Integer.toHexString(argbBL)+" argbBR="+Integer.toHexString(argbBR));
					java.awt.GradientPaint gp = getGradient(x, y, width, height, argbTL, argbTR, argbBL, argbBR);
					java.awt.geom.Rectangle2D.Float bounds = new java.awt.geom.Rectangle2D.Float(x, y, width, height);
					java.awt.geom.Rectangle2D.Float clipRect = new java.awt.geom.Rectangle2D.Float(clipX, clipY, clipW, clipH);
					if(gp != null) {
						drawRect0(bounds, clipRect,
								  arcRadius,
								  null, 0,
								  gp.getColor1(), (float)gp.getPoint1().getX(), (float)gp.getPoint1().getY(),
								  gp.getColor2(), (float)gp.getPoint2().getX(), (float)gp.getPoint2().getY(),
								  //(float)((argbTL>>24)&0xff)/255.0f);
								  1.0f);
					} else {
						drawRect0(bounds, clipRect,
								  arcRadius,
								  null, 0,
								  new java.awt.Color(argbTL, true), 0.0f, 0.0f,
								  null, 0.0f, 0.0f,
								  1.0f);
					}
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
					float x, y, clipX, clipY, clipW, clipH;
					int textlen, fontHandle, argb;
					StringBuffer text = new StringBuffer();
					int i;

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					textlen=readInt(8, cmddata);
					for(i=0;i<textlen;i++)
					{
						text.append((char)readShort(12+i*2, cmddata));
					}
					fontHandle=readInt(textlen*2+12, cmddata);
					argb=readInt(textlen*2+16, cmddata);
					clipX=(float)readInt(textlen*2+20, cmddata);
					clipY=(float)readInt(textlen*2+24, cmddata);
					clipW=(float)readInt(textlen*2+28, cmddata);
					clipH=(float)readInt(textlen*2+32, cmddata);

					// TODO: check if this is needed
//					if (System.getProperty("java.version").startsWith("1.4"))
//						clipW = clipW * 5 / 4;

//					System.out.println("DRAWTEXT: dest=("+x+","+y+") clip=("+clipX+","+clipY+" "+clipW+"x"+clipH+") fontHandle="+fontHandle+" argb="+Integer.toHexString(argb)+" text="+text.toString());

					McFont fontPtr = (McFont)fontMap.get(new Integer(fontHandle));
					if(fontPtr != null) {
						// use AWT string bounds or we'll clip on occasion
						String theString = text.toString();
//						java.awt.Dimension textSize = fontPtr.getStringSize(theString);
						float[] positions = fontPtr.getGlyphPositions(theString);
//						System.out.println("drawText: \""+theString+"\"  loc=("+x+","+y+") num positions="+positions.length);
						drawTextWithPositions0(theString, fontPtr.nativeFont,
							x, y, positions, new java.awt.geom.Rectangle2D.Float(clipX,clipY,clipW,clipH),
							new java.awt.Color(argb, true));
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
					float x, y, width, height,
						srcx, srcy, srcwidth, srcheight;
					int handle, blend; // blend is a color, use alpha component for blending

					x=(float)readInt(0, cmddata);
					y=(float)readInt(4, cmddata);
					width=(float)readInt(8, cmddata);
					height=(float)readInt(12, cmddata);
					handle=readInt(16, cmddata);			// either an image handle or layer handle (if not in imageMap)
					srcx=(float)readInt(20, cmddata);
					srcy=(float)readInt(24, cmddata);
					srcwidth=(float)readInt(28, cmddata);
					srcheight=(float)readInt(32, cmddata);
					blend=readInt(36, cmddata);

					/*
						if height < 0
							disable blending

						if width < 0 (font mode, composite with background and blend with given color)
							blend with full RGBA color
						else
							blend with alpha only
					 */

//					System.out.println("DRAWTEXTURED: handle="+handle+" dest=("+x+","+y+" "+width+"x"+height+") src=("+srcx+","+srcy+" "+srcwidth+"x"+srcheight+") blend="+Integer.toHexString(blend));

					boolean doBlend = true;
					if(height < 0) {
						doBlend = false;
						height *= -1;
					}

					if(width < 0) {
						width *= -1;
					} else {
						if(doBlend)
							blend |= 0x00ffffff; // only use alpha
					}

					Long imagePtr = (Long)imageMap.get(new Integer(handle));
					java.awt.geom.Rectangle2D.Float destRect = new java.awt.geom.Rectangle2D.Float(x,y,width,height);
					java.awt.geom.Rectangle2D.Float srcRect = new java.awt.geom.Rectangle2D.Float(srcx,srcy,srcwidth,srcheight);

					if(imagePtr != null) {
						myConn.registerImageAccess(handle);
//						System.out.println("              (drawing image) imagePtr="+imagePtr);
						drawImage1(imagePtr.longValue(),
								   destRect, srcRect,
								   (doBlend) ? new java.awt.Color(blend, true) : null);
					} else {
						imagePtr = (Long)layerMap.get(new Integer(handle));
						if(imagePtr != null) {
							myConn.registerImageAccess(handle);
//							System.out.println("              (compositing surface) layerPtr="+Long.toHexString(imagePtr.longValue())+" currentLayer="+currentLayer);
							float alpha = (doBlend ? (float)(((blend >> 24)&0xff))/255.0f : 1.0f);
							composite0(imagePtr.longValue(), currentLayer, srcRect, destRect, alpha, doBlend);
						}
						else
						{
							System.out.println("ERROR invalid handle passed for texture rendering of: " + handle);
							abortRenderCycle = true;
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
					float x1, y1, x2, y2;
					int argb1, argb2;

					x1=readInt(0, cmddata);
					y1=readInt(4, cmddata);
					x2=readInt(8, cmddata);
					y2=readInt(12, cmddata);
					argb1=readInt(16, cmddata);
					argb2=readInt(20, cmddata);

//					System.out.println("DRAWLINE: start=("+x1+","+y1+") end=("+x2+","+y2+") argb1="+Integer.toHexString(argb1)+" argb2="+Integer.toHexString(argb2));
					drawLine0(x1, y1, x2, y2, 1, new java.awt.Color(argb1, true));
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
					int imghandle = 0;
					width=readInt(0, cmddata);
					height=readInt(4, cmddata);
//					System.out.println("LOADIMAGE: size=("+width+"x"+height+")");
					if (width * height * 4 + imageCacheSize > imageCacheLimit)
					{
						imghandle = 0;
					}
					else
					{
						// creating a new image from bitmap data being sent over myConn, create a new empty image
						long imagePtr = createNewImage0(width, height);
						imghandle = handleCount++;
//						System.out.println("           imghandle="+imghandle+" imagePtr="+imagePtr);
						imageMap.put(new Integer(imghandle), new Long(imagePtr));	// actual value is filled in later when it's prepared
						imageCacheSize += width * height * 4;
					}
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
					long imagePtr = createNewImage0(width, height);
//						System.out.println("           imghandle="+imghandle+" imagePtr="+imagePtr);
					imageMap.put(new Integer(imghandle), new Long(imagePtr));	// actual value is filled in later when it's prepared
					imageCacheSize += width * height * 4;
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
					int handle = handleCount++;;
					width=readInt(0, cmddata);
					height=readInt(4, cmddata);

					// width/height is managed here
					long layerPtr = createLayer0(c.getSize());
					layerMap.put(new Integer(handle), new Long(layerPtr));

//					System.out.println("CREATESURFACE: ("+width+","+height+") handle="+handle+" layerPtr="+layerPtr);

					hasret[0]=1;
					return handle;
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_CREATESURFACE : " + len);
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
//					System.out.println("PREPIMAGE: size=("+width+"x"+height+")");

					if (width * height * 4 + imageCacheSize > imageCacheLimit)
						imghandle = 0;
					else if (len >= 12)
					{
						// We've got enough room for it and there's a cache ID, check if we've got it cached locally
						int strlen = readInt(8, cmddata);
						if (strlen > 1)
						{
							String rezName = new String(cmddata, 16, strlen - 1);
//							System.out.println("           rezName="+rezName);
							lastImageResourceID = rezName;
							// We use this hashcode to match it up on the loadCompressedImage call so we know we're caching the right thing
							lastImageResourceIDHandle = imghandle = Math.abs(lastImageResourceID.hashCode());
							java.io.File cachedFile = myConn.getCachedImageFile(rezName);
							if (cachedFile != null)
							{
								// We've got it locally in our cache! Read it from there.
								long imagePtr = createImageFromPath0(cachedFile.getAbsolutePath());
								if(imagePtr != 0)
								{
									java.awt.Dimension imgSize = getImageDimensions0(imagePtr);
									if(imgSize != null)
									{
										if(imgSize.getWidth() == width && imgSize.getHeight() == height)
										{
											// valid image in cache, use it
											imghandle = handleCount++;
//											System.out.println("           loaded from cache, imagePtr="+imagePtr+" handle="+imghandle);
											imageMap.put(new Integer(imghandle), new Long(imagePtr));
											imageCacheSize += getImageSize0(imagePtr);
											hasret[0] = 1;
											return -1 * imghandle;
										}
										else
											freeNativeImage0(imagePtr);
									}
									else
										freeNativeImage0(imagePtr);
								}
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
					while (width * height * 4 + imageCacheSize > imageCacheLimit)
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

							// We've got it locally in our cache! Read it from there.
							long imagePtr = createImageFromPath0(cachedFile.getAbsolutePath());
							if(imagePtr != 0)
							{
								java.awt.Dimension imgSize = getImageDimensions0(imagePtr);
								if(imgSize != null && imgSize.getWidth() == width && imgSize.getHeight() == height)
								{
									// valid image in cache, use it
//											System.out.println("           loaded from cache, imagePtr="+imagePtr+" handle="+imghandle);
									imageMap.put(new Integer(imghandle), new Long(imagePtr));
									imageCacheSize += getImageSize0(imagePtr);
								}
								else
								{
									if (imgSize != null)
									{
										// It doesn't match the cache
										System.out.println("CACHE ID verification failed for rezName=" + rezName + " target=" + width + "x" + height + " actual=" + imgSize.getWidth() + "x" + imgSize.getHeight());
									}
									else
										System.out.println("CACHE Load failed for rezName=" + rezName);
									cachedFile.delete();
									freeNativeImage0(imagePtr);
									// This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
									myConn.postImageUnload(imghandle);
									myConn.postOfflineCacheChange(false, rezName);
								}
							}
							else
							{
								cachedFile.delete();
								// This load failed but the server thought it would succeed, so we need to inform it that the image is no longer loaded.
								myConn.postImageUnload(imghandle);
								myConn.postOfflineCacheChange(false, rezName);
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
					Long layerPtr = (Long)layerMap.get(new Integer(handle));
//					System.out.println("SETTARGETSURFACE: handle="+handle+" layerPtr="+ (layerPtr == null ? "0" : Long.toHexString(layerPtr.longValue())));

					currentLayer = (layerPtr != null) ? layerPtr.longValue() : 0;
					java.awt.Rectangle clipRect = new java.awt.Rectangle(0, 0, c.getWidth(), c.getHeight());
					setLayer0(currentLayer, c.getSize(), clipRect);
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_SETTARGETSURFACE : " + len);
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

//					System.out.println("LOADFONT: handle="+fonthandle+" name="+name.toString()+" style="+Integer.toHexString(style)+" size="+size);
					McFont fontPtr = new McFont(name.toString(), style, size);
//					long fontPtr = loadFont0(name.toString(), style, size);
					if(fontPtr == null) {
						// FIXME: implement!
						// we don't have the font on this sytem (yet) see if it's cached and try to load it manually
//						String cacheName = name.toString() + "-" + style;
//						fontPtr = loadCachedFont0(cacheDir.getAbsolutePath(), name.toString() + "-" + myConn.getServerName(), style, size);
//						if (fontPtr == 0) {
							// Return that we don't have this font so it'll load it into our cache
							hasret[0] = 1;
							return 0;
//						}
					}

//					System.out.println("          fontPtr=" + fontPtr);
					fontMap.put(new Integer(fonthandle), fontPtr);
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
					McFont fontPtr = (McFont)fontMap.get(new Integer(handle));
//					System.out.println("UNLOADFONT: handle="+handle+" fontPtr="+fontPtr);
					if(fontPtr != null) fontPtr.unload();
					fontMap.remove(new Integer(handle));
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
//						System.out.println("Saving font " + name.toString() + " to cache");
						myConn.saveCacheData(name.toString() + "-" + myConn.getServerName(), cmddata, 12 + namelen, datalen);
					}
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_LOADFONTSTREAM : " + len);
				}
				break;
			case GFXCMD_FLIPBUFFER:
//				System.out.println("FLIPBUFFER");
				if (abortRenderCycle)
				{
System.out.println("ERROR in painting cycle, ABORT was set...send full repaint command");
					myConn.postRepaintEvent(0, 0, c.getWidth(), c.getHeight());
				}
				else
				{
					present0(c.nativeView, new java.awt.Rectangle(0, 0, c.getWidth(), c.getHeight()));
				}
				hasret[0] = 1;
				//STBGFX.GFX_flipBuffer();
				firstFrameDone = true;
				return 0;
			case GFXCMD_STARTFRAME:
//				System.out.println("STARTFRAME");
				// prepare for a new frame to be rendered
				setTargetView0(c.nativeView);
				setLayer0(0, c.getSize(), null); // this makes sure the drawing surface gets resized properly
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
					//int dataPos = 12;
					Long imagePtr = (Long)imageMap.get(new Integer(handle));
//					System.out.println("LOADIMAGELINE: handle="+handle+" imagePtr="+imagePtr+" line="+line+" len2="+len2);
					if(imagePtr != null)
						loadImageLine0(imagePtr.longValue(), line, cmddata, 16/*12*/, len2);
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
					if (lastImageResourceID != null && lastImageResourceIDHandle == handle)
					{
						myConn.saveCacheData(lastImageResourceID, cmddata, 12, len2);
						myConn.postOfflineCacheChange(true, lastImageResourceID);
					}

					if (!myConn.doesUseAdvancedImageCaching())
					{
						handle = handleCount++;
						hasret[0] = 1;
					}
					else
						hasret[0] = 0;
					myConn.registerImageAccess(handle);
					long imagePtr = createImageFromBytes0(cmddata, 12, len2, null); // FIXME: grab extension if possible
//					System.out.println("LOADIMAGECOMPRESSED: handle="+handle+" imagePtr="+imagePtr+" len2="+len2);
					imageMap.put(new Integer(handle), new Long(imagePtr));
					imageCacheSize += getImageSize0(imagePtr);
					return handle;
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
					destHandle = readInt(4, cmddata);		// seems to be unused
					destWidth = readInt(8,  cmddata);		// scaled size (ignore?)
					destHeight = readInt(12, cmddata);
					maskCornerArc = readInt(16, cmddata);
					int rvHandle = destHandle;
					if (!myConn.doesUseAdvancedImageCaching())
					{
						rvHandle = handleCount++;
						hasret[0] = 1;
					}
					else
						hasret[0] = 0;

					// we cheat and apply the transforms to a metaimage object without actually creating a new image (saves oodles of memory)

					Long srcImg = (Long)imageMap.get(new Integer(srcHandle));
//					System.out.println("XFMIMAGE: srcHandle="+srcHandle+" srcImg="+srcImg+" destHandle="+destHandle+" destWidth="+destWidth+" destHeight="+destHeight+" maskCornerArc="+maskCornerArc);
					if(srcImg != null) {
						long newImage = transformImage0(srcImg.longValue(), destWidth, destHeight, maskCornerArc);
						if(newImage != 0) {
//							System.out.println("          newImage="+newImage);
							imageMap.put(new Integer(rvHandle), new Long(newImage));
						}
					}
					return rvHandle;
				}
				else
				{
					System.out.println("Invalid len for GFXCMD_XFMIMAGE : " + len);
				}
				break;
			case GFXCMD_SETVIDEOPROP:
				if (len >= 40)
				{
					java.awt.Rectangle srcRect = new java.awt.Rectangle(readInt(4, cmddata), readInt(8, cmddata),
						readInt(12, cmddata), readInt(16, cmddata));
					java.awt.Rectangle destRect = new java.awt.Rectangle(readInt(20, cmddata), readInt(24, cmddata),
						readInt(28, cmddata), readInt(32, cmddata));
					System.out.println("SETVIDEOPROP: srcRect="+srcRect+" dstRect="+destRect);
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

	private boolean lastWasPressed;
	private boolean ignoreNextTyped;
	private int lastKeyCode;
	private int lastModifiers;
	public void keyPressed(java.awt.event.KeyEvent evt)
	{
		lastWasPressed = true;
		lastKeyCode = evt.getKeyCode();
		lastModifiers = evt.getModifiers();
		setHidden0(false, false);

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
		setHidden0(false, false);
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
		setHidden0(false, false);
		if (lastWasPressed)
		{
			lastWasPressed = false;
			myConn.postKeyEvent(lastKeyCode, lastModifiers, (char)0);
		}
		// This is to fix a problem where the first character on an input was being ignored if we did a valid press release on a key
		// that didn't fire a keyTyped event. (like up/down/left/right)
		ignoreNextTyped = false;
	}

	public void sendMouseMoved(int x, int y, int modifiers, long when)
	{
		int awtModifiers = 0;

		if((modifiers & (1<<17)) != 0) awtModifiers |= java.awt.event.InputEvent.SHIFT_DOWN_MASK; // shift key
		if((modifiers & (1<<18)) != 0) awtModifiers |= java.awt.event.InputEvent.CTRL_DOWN_MASK; // control key
		if((modifiers & (1<<19)) != 0) awtModifiers |= java.awt.event.InputEvent.ALT_DOWN_MASK; // alternate/option
		if((modifiers & (1<<20)) != 0) awtModifiers |= java.awt.event.InputEvent.META_DOWN_MASK; // meta/command

		java.awt.event.MouseEvent evt = new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_MOVED, when, awtModifiers, x, y, 1, false);
		mouseMoved(evt);
	}

	public void sendMouseWheel(int x, int y, int modifiers, long when, int amount)
	{
		int awtModifiers = 0;

		if((modifiers & (1<<17)) != 0) awtModifiers |= java.awt.event.InputEvent.SHIFT_DOWN_MASK; // shift key
		if((modifiers & (1<<18)) != 0) awtModifiers |= java.awt.event.InputEvent.CTRL_DOWN_MASK; // control key
		if((modifiers & (1<<19)) != 0) awtModifiers |= java.awt.event.InputEvent.ALT_DOWN_MASK; // alternate/option
		if((modifiers & (1<<20)) != 0) awtModifiers |= java.awt.event.InputEvent.META_DOWN_MASK; // meta/command

		java.awt.event.MouseWheelEvent evt = new java.awt.event.MouseWheelEvent(c, java.awt.event.MouseEvent.MOUSE_WHEEL, when, awtModifiers, x, y, 1, false, java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL, 0, amount);
		mouseWheelMoved(evt);
	}

	public void mouseClicked(java.awt.event.MouseEvent e)
	{
		setHidden0(false, true);
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
		setHidden0(false, true);
		myConn.postMouseEvent(e);
	}

	public void mouseReleased(java.awt.event.MouseEvent e)
	{
		setHidden0(false, true);
		myConn.postMouseEvent(e);
	}

	public void mouseDragged(java.awt.event.MouseEvent e)
	{
		setHidden0(false, true);
		myConn.postMouseEvent(e);
	}

	public void mouseMoved(java.awt.event.MouseEvent e)
	{
		setHidden0(false, true);
		if (e.getSource() == c)
			f.setCursor(null);
		myConn.postMouseEvent(e);
	}

	public void mouseWheelMoved(java.awt.event.MouseWheelEvent e)
	{
		myConn.postMouseEvent(e);
	}

	public native boolean setHidden0(boolean x, boolean fromMouseAction);

	private Object timerLocks = new Object();
	private void cancelHideTimer()
	{
		synchronized (timerLocks)
		{
			if (hideTimer != null)
				hideTimer.cancel();
		}
	}
	public void resetHideTimer()
	{
		synchronized (timerLocks)
		{
			if (hideTimer != null)
				hideTimer.cancel();
			myConn.addTimerTask(hideTimer = new HideTimerTask(), 5000, 0);
		}
	}
	private class HideTimerTask extends java.util.TimerTask
	{
		public void run()
		{
			hideTime = System.currentTimeMillis();
			setHidden0(true, false);
		}
	}

	// FIXME: these should *never* be called, make sure we can safely remove them...
	public boolean createVideo(int width, int height, int format)
	{
System.out.println("QuartzGFXCMD.createVideo("+width+","+height+","+format);
		return true;
	}

	public boolean updateVideo(int frametype, java.nio.ByteBuffer buf)
	{
System.out.println("QuartzGFXCMD.updateVideo()");
		return true;
	}

	private void unloadImage(int handle)
	{
		Long imagePtr = (Long)imageMap.get(new Integer(handle));
//					System.out.println("UNLOADIMAGE: handle="+handle+" imagePtr="+imagePtr);
		if(imagePtr != null) {
			imageCacheSize -= getImageSize0(imagePtr.longValue());
			freeNativeImage0(imagePtr.longValue());
		}
		imageMap.remove(new Integer(handle));
		myConn.clearImageAccess(handle);
	}

	// native calls
	private native void init0();

		// Image loader calls
	// checks with ImageIO to see if a file of the given extension can be loaded
	private native boolean canLoadCompressed0(String extension);
	// create a native image from a file at the given path (may not fully load until rendered)
	private native long createNewImage0(int width, int height);	// always assumes 32 bit ARGB
	private native void loadImageLine0(long imagePtr, int line, byte[] lineData, int dataOffset, int dataLen);
	private native long createImageFromPath0(String path);
	private native long createImageFromURL0(String absoluteURL);
	private native long createImageFromBytes0(byte[] data, int offset, int length, String extension);
	private native long transformImage0(long sourceImagePtr, int width, int height, int arcRadius);
	private native int getImageSize0(long nativePtr);
	private native java.awt.Dimension getImageDimensions0(long nativePtr);
	private native void freeNativeImage0(long nativePointer);

	// FIXME: bind to the SageTVWindow Frame instead, call when the Frame we're supposed to draw to changes (e.g., window disposed)
	private native void setTargetView0(long view);

	private native long createLayer0(java.awt.Dimension size);
	private native void freeLayer0(long layerPtr);
	// if the size has changed, the layer will be reallocated to minimize memory usage
	private native void setLayer0(long layerPtr, java.awt.Dimension size, java.awt.Rectangle clipRect);

	// pass 0 for either source or dest to use the drawing layer
	private native void composite0(long srcLayer, long destLayer, java.awt.geom.Rectangle2D.Float srcRect, java.awt.geom.Rectangle2D.Float dstRect, float alphaFactor, boolean enableBlend);

	// prepare an NSFont for rendering, metrics will match, since AWT fonts use NSFonts directly
	// stuff the resulting value into the MetaFonts nativeHandle field
	private native long loadFont0(String name, int style, int size); // size is point size
	private native long loadCachedFont0(String cacheDir, String cacheFile, int style, int size);
	private native void unloadFont0(long fontPtr);

	private native void present0(long view, java.awt.Rectangle clipRect);

	// this assumes aspect ratio is already accounted for
	private native void setVideoRects0(java.awt.geom.Rectangle2D.Float srcRect, java.awt.geom.Rectangle2D.Float destRect, java.awt.Color backColor);

	// drawing functions
	private native void drawLine0(float sx, float sy, float dx, float dy, int thickness, java.awt.Color lineColor);

	private native void clearRect0(java.awt.geom.Rectangle2D.Float rect);

	// bounds defines the FULL boundary of the shape, e.g., for a rect, it defines the rect
	// if clipRect is non-null, then only the part of the shape that falls in that rect is drawn
	// both rects are relative to the surface origin
	private native void drawRect0(java.awt.geom.Rectangle2D.Float bounds, java.awt.geom.Rectangle2D.Float clipRect,
								  int arcRadius,
								  java.awt.Color strokeColor, int strokeWidth,	// if null, no border drawn
								  java.awt.Color gc1, float gc1x, float gc1y,	// if null, not filled
								  java.awt.Color gc2, float gc2x, float gc2y,	// if ! null, gradient is applied
								  float alphaFactor	// set to -1.0 (or any negative value) to use the alpha values in the stroke/fill colors
								  );
	private native void drawOval0(java.awt.geom.Rectangle2D.Float bounds, java.awt.geom.Rectangle2D.Float clipRect,
								  java.awt.Color strokeColor, int strokeWidth,	// if null, no border drawn
								  java.awt.Color gc1, float gc1x, float gc1y,	// if null, not filled
								  java.awt.Color gc2, float gc2x, float gc2y,	// if ! null, gradient is applied
								  float alphaFactor // same as above
								  );

	private native void drawImage0(long nativePtr, java.awt.geom.Rectangle2D.Float destRect, java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor);
	private native void drawImage1(long nativePtr, java.awt.geom.Rectangle2D.Float destRect, java.awt.geom.Rectangle2D.Float sourceRect, java.awt.Color blendColor);
	private native void drawImageWithInsets0(long nativePtr, java.awt.geom.Rectangle2D.Float destRect, java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor, int []insets);

	private native void drawText0(String theString, long nsFont, java.awt.geom.Rectangle2D.Float destRect, java.awt.geom.Rectangle2D.Float clipRect, java.awt.Color color);
	private native void drawTextWithPositions0(String theString, long nsFont, float x, float y, float[] positions, java.awt.geom.Rectangle2D.Float clipRect, java.awt.Color color);

	// purge all layers, fonts, images, etc...
	private native void cleanupRenderer0();

	// wait indicator drawing (set to zero to clear)
	private native void setWaitIndicatorImage(long viewPtr, long imagePtr, java.awt.geom.Rectangle2D.Float destRect, java.awt.geom.Rectangle2D.Float clipRect, float alphaFactor);

	public native String getServerVideoOutParams();
}
