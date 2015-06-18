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

public class QuartzSageRenderer extends SageRenderer implements NativeImageAllocator {
	private java.io.File logFile = null;
	private java.io.PrintStream logStream = null;
	
	private native void init0();
	
	private Thread waitWatcherThread;
	private boolean waitIndicatorShowing = false;
	
	private boolean leopard = false;
	
	// get vo module arguments for mplayer running in a different process
	public native String getServerVideoOutParams();
	
	Runnable waitWatcher = new Runnable(){
		public void run() {
			while(true) {
				if(renderCanvas != null) {
//					if(!waitIndicatorState) waitIndicatorState = true; // uncomment for testing..
					
					if(waitIndicatorState && waitIndicatorRops != null) {
						RenderingOp op = (RenderingOp)waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
						
						if(op != null) runWaitOp(op);
						try {
							Thread.sleep(66);
						} catch (Throwable t) {}
					} else {
						// no need to do this more than once...
						if(waitIndicatorShowing) {
							setWaitIndicatorImage(renderCanvas.nativeView, 0, null, null, 0);
							waitIndicatorShowing = false;
						}
						
						try {
							Thread.sleep(250);
						} catch (Throwable t) {}
					}
				} else try {
					Thread.sleep(250);
				} catch (Throwable t) {}
			}
		}
	};
	
	public QuartzSageRenderer(ZRoot inMaster) {
		super(inMaster);
		vf = uiMgr.getVideoFrame();
		
		try {
			init0();
		} catch(Throwable t) {
			System.out.println("Exception creating Quartz renderer: "+t);
		}
		
		// TODO: enable when we get leopard stuff in
		//leopard = (System.getProperty("os.version").indexOf("10.5") != -1);
		
		lastMasterSize = master.getSize();
		
		imageMemoryLimit = 33554432; // this applies only to native loaded images (minimum 32 MB)
		imageMemoryLimit = Math.min(imageMemoryLimit, Sage.getInt("quartz/image_memory_limit", 33554432));
		
		// create a new java.awt.Canvas that we'll do all our rendering to
		// this should be layered on top of the video canvas, so it appears on top of video playing
		// we also need to handle DVD playback, which requires using overlay views since we can't touch DVD pixels
		if(uiMgr.getBoolean("quartz/debug_logging", false)) {
			logFile = new java.io.File(sage.Sage.getLogPath("quartz_renderer.log"));
			try {
				// clear the log file
				java.io.RandomAccessFile foo = new java.io.RandomAccessFile(logFile, "rw");
				foo.setLength(0);
				
				logStream = new java.io.PrintStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(logFile)), true);
			} catch(Throwable t) {
				System.out.println("exception opening quartz renderer log file: "+t);
				logFile = null;
				logStream = null;
			}
		}
		
		// wait indicator thread, we'll watch the status and run through the wait indicator ops while it's active
		waitWatcherThread = new Thread(waitWatcher, "WaitIndicatorThread");
		waitWatcherThread.setPriority(Thread.MIN_PRIORITY);
		waitWatcherThread.setDaemon(true);
		waitWatcherThread.start();
	}
	
	private void runWaitOp(RenderingOp op) {
		//System.out.println("executing wait indicator op: "+op);
		if(op.isImageOp()) {
			long nativePtr = op.texture.getNativeImage(this, op.textureIndex);
			setWaitIndicatorImage(renderCanvas.nativeView, nativePtr, op.copyImageRect, op.destRect, op.alphaFactor);
			waitIndicatorShowing = true;
			op.texture.removeNativeRef(this, op.textureIndex);
		} // TODO: any other ops???
	}
	
	// send to quartz log file if enabled, or to stdout if built with ANIM_DEBUG set to true
	private void qlog(String s) {
		if(logStream != null) {
			synchronized (logStream) {
				logStream.print(s);
			}
		}
		if(ANIM_DEBUG) System.out.print(s);
	}
	
	private void qlogln(String s) {
		if(logStream != null) {
			synchronized (logStream) {
				logStream.println(s);
			}
		}
		if(ANIM_DEBUG) System.out.println(s);
	}
	
	public boolean useTextureMapsForText()
	{
		// return true to disable NSString based rendering (not a good idea...)
		return false;
	}
	
	// NativeImageAllocator
	
	public boolean createNativeImage(MetaImage image, int imageIndex)
	{
			// no memory management is done here, NSImage and CoreGraphics handles all that automatically
//		qlogln("createNativeImage: "+image);
		
		boolean canLoadCompressed = false;
		String imgSourceName = image.getLcSourcePathname();
		String ext = imgSourceName.substring(imgSourceName.lastIndexOf('.')+1, imgSourceName.length());
		
		// check with ImageIO first
		canLoadCompressed = canLoadCompressed0(ext);
		
		if(!Sage.getBoolean("ui/disable_native_image_loader", false)) {
			if(canLoadCompressed) {
				// use getSourceAsBAOS if it's not a file or URL we can load from
				String imgPath = null;
				String imgURL = null;
				Object imgSource = image.getSource();
				long nativePtr = 0;
				int nativeSize = 0;
				
				if(imgSource instanceof java.io.File) {
					java.io.File imgFile = (java.io.File)imgSource;
					if(imgFile.exists() && imgFile.canRead())
						imgPath = imgSource.toString();
					// else the next check will fail and it'll pull from the server
				} else if(imgSource instanceof java.net.URL) {
					imgURL = imgSource.toString();
				} else if((imgSource instanceof MediaFile) && ((MediaFile)imgSource).isLocalFile()) {
					java.io.File localSrcFile = ((MediaFile)imgSource).isPicture()
						? ((MediaFile)imgSource).getFile(0)
						: ((MediaFile)imgSource).getSpecificThumbnailFile();
					imgPath = localSrcFile.toString();
				} // otherwise I can't load it directly, we'll get it from the server as a byte array
				
				pauseIfNotRenderingThread();
				if(imgPath != null) {
					//System.out.println("createImageFromPath0("+imgPath+"), image: "+image);
					nativePtr = createImageFromPath0(imgPath);
				} else if(imgURL != null) {
					//System.out.println("createImageFromURL0("+imgURL+"), image: "+image);
					nativePtr = createImageFromURL0(imgURL);
				} else {
					// some other method, use a baos to get the data directly
					//System.out.println("loading from byte array, image: "+image);
					java.io.ByteArrayOutputStream baos = image.getSourceAsBAOS();
					byte[] imgData = baos.toByteArray();
					nativePtr = createImageFromBytes0(imgData, 0, imgData.length, ext);
				}
				
				if(nativePtr != 0) {
					nativeSize = getImageSize0(nativePtr);
					image.setNativePointer(this, imageIndex, nativePtr, nativeSize);
					if(Sage.DBG) System.out.println("native image loaded, ptr = "+nativePtr+", size = "+nativeSize);
					
					// limit memory usage once we've allocated the image
					synchronized (MetaImage.getNiaCacheLock(this))
					{
						while(MetaImage.getNativeImageCacheSize(this) > imageMemoryLimit) {
							Object [] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
							if(oldestImage == null) {
								System.out.println("No images to unload to make room for "+image.getSource()+" size="+MetaImage.getNativeImageCacheSize(this));
								break;
							}

							if(Sage.DBG) System.out.println("Freeing image to make room, size="+MetaImage.getNativeImageCacheSize(this)+" src="+((MetaImage)oldestImage[0]).getSource());
							((MetaImage)oldestImage[0]).clearNativePointer(this, ((Integer)oldestImage[1]).intValue());
						}
					}
					
					return true;
				}
				
				// fall through on failure
			}
			
		}
		
		// create a raw image and create a native image from that
//		sage.media.image.RawImage tightImage = null;
		
		return false;
	}
	
	public void releaseNativeImage(long nativePointer)
	{
		if(Sage.DBG) System.out.println("Releasing native image "+nativePointer);
		freeNativeImage0(nativePointer);
	}
	
	public void preloadImage(MetaImage mi)
	{
		mi.getNativeImage(this, 0);
		mi.removeNativeRef(this, 0);
	}
	
	public int getMaximumImageDimension()
	{
		return 4096; // there doesn't seem to be a limit yet, we'll find out in the field I guess...
	}
	
		// checks with ImageIO to see if a file of the given extension can be loaded
	private native boolean canLoadCompressed0(String extension);
		// create a new empty (clear) image
	private native long createNewImage0(int width, int height);	// always assumes 32 bit ARGB
	private native void loadImageLine0(long imagePtr, int line, byte[] lineData, int dataOffset, int dataLen);
		// create a native image from a file at the given path (may not fully load until rendered)
	private native long createImageFromPath0(String path);
	private native long createImageFromURL0(String absoluteURL);
	private native long createImageFromBytes0(byte[] data, int offset, int length, String extension);
	private native int getImageSize0(long nativePtr);
	private native java.awt.Dimension getImageDimensions0(long nativePtr);
	private native void freeNativeImage0(long nativePointer);
	
	// SageRenderer
	public boolean allocateBuffers(int width, int height) {
//		qlogln("allocateBuffers("+width+"x"+height+")");
		// disable animations
//		uiMgr.setCoreAnimationsEnabled(false);
		return true;
	}
	
	public void preprocessNextDisplayList(java.util.ArrayList v) {
		cullDisplayList(v);
//		qlogln("setNextDisplayList");
		
		for(int i = 0; i < v.size(); i++) {
			RenderingOp op = (RenderingOp)v.get(i);
			
				// punt invalid src or dest rects
			if(op.isTextOp() || op.isImageOp()) {
				if(op.srcRect.width <= 0 || op.srcRect.height <= 0 || op.destRect.width <= 0 || op.destRect.height <= 0) {
					v.remove(i--);
					continue;
				}
			}
			
			// FIXME: remove since we don't use pre-rendered fonts
			if(op.isTextOp() && useTextureMapsForText()) {
				convertGlyphsToCachedImages(op);
				if(op.text.fontImage != null && op.text.renderImageNumCache != null) {
					for(int j = 0; j < op.text.renderImageNumCache.length; j++) {
						if(op.text.renderImageNumCache[j] != -1) {
							op.text.fontImage.getNativeImage(this, op.text.renderImageNumCache[j]);
							op.text.fontImage.removeNativeRef(this, op.text.renderImageNumCache[j]);
						}
					}
				}
			}
		}
	}
	
	protected boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region)
	{
		qlogln("compositeSurfaces("+targetSurface+", "+srcSurface+", "+alphaFactor+", "+region+")");
		
		// targetSurface and srcSurface are pulled directly from surfaceCache, so just grab the longValue and pass them down
		java.awt.geom.Rectangle2D.Float myRegion = new java.awt.geom.Rectangle2D.Float((float)region.getX(), (float)region.getY(), (float)region.getWidth(), (float)region.getHeight());
		composite0(((Long)srcSurface).longValue(), ((Long)targetSurface).longValue(), myRegion, myRegion, alphaFactor, true);
		return true;
	}
	
	private String describeRect(java.awt.Rectangle r) {
		return new String("("+r.x+","+r.y+") ("+r.width+"x"+r.height+")");
	}
	
	private String describeFloatRect(java.awt.geom.Rectangle2D.Float r) {
		return new String("("+r.x+","+r.y+") ("+r.width+"x"+r.height+")");
	}
	
	private String describeTransform(java.awt.geom.AffineTransform t) {
		int type = t.getType();
		double[] matrix = new double[6]; // m00, m01, m10, m11, m02, m12
		
		String foo = new String("("+type+":");
		
		if((type & java.awt.geom.AffineTransform.TYPE_FLIP) != 0) foo += " FLIP";
		if((type & java.awt.geom.AffineTransform.TYPE_GENERAL_ROTATION) != 0) foo += " GENERAL_ROTATION";
		if((type & java.awt.geom.AffineTransform.TYPE_GENERAL_SCALE) != 0) foo += " GENERAL_SCALE";
		if((type & java.awt.geom.AffineTransform.TYPE_IDENTITY) != 0) foo += " IDENTITY";
		if((type & java.awt.geom.AffineTransform.TYPE_QUADRANT_ROTATION) != 0) foo += " QUADRANT_ROTATION";
		if((type & java.awt.geom.AffineTransform.TYPE_TRANSLATION) != 0) foo += " TRANSLATION";
		if((type & java.awt.geom.AffineTransform.TYPE_UNIFORM_SCALE) != 0) foo += " UNIFORM_SCALE";
		
		t.getMatrix(matrix);
		
		return foo + ":"+matrix[0]+","+matrix[1]+","+matrix[2]+","+matrix[3]+","+matrix[4]+","+matrix[5]+")";
	}
	
	public boolean executeDisplayList(java.awt.Rectangle clipRect) {
		boolean setVideoRegion = false;
		int ii;
		
		qlogln("executeDisplayList:");
		
		if(renderCanvas != null) {
			setTargetView0(renderCanvas.nativeView);
			setLayer0(0, master.getSize(), clipRect);	// makes sure the drawing surface is resized properly if animations are off...
		} else {
			// see if we can create our view yet
			// We can't do this at instantiation because we're instantiated before UIManager has been initialized, so the embedded panel doesn't exist yet...
			// create a QuartzRendererView and attach it to the embedded panel so it can be arranged properly
//			System.out.println("************** Creating QuartzRendererView, ep = "+uiMgr.getEmbeddedPanel());
			
			java.awt.Dimension panelSize = uiMgr.getGlobalFrame().getContentPane().getSize();
			//System.out.println("************** root panel size = "+panelSize);
			
			renderCanvas = new QuartzRendererView();
			renderCanvas.setVisible(true);
			renderCanvas.setSize(panelSize);
			
			java.awt.Panel ep = uiMgr.getEmbeddedPanel();
			if(ep != null) {
				java.awt.Container rootPanel = uiMgr.getGlobalFrame().getContentPane();
				
				ep.setVisible(true);
				
				ep.add(renderCanvas);
				ep.validate();
				ep.doLayout(); // forces ep to resize itself so the UI actually appears
				
				// set ZRoot in front of the embedded pane so that key and mouse events get handled in AWT instead of AppKit
				// nothing is ever drawn to ZRoot so this should be OK
				
				// NOTE: if we ever need to intercept events inside the embedded views, then we'll need to swap them back
				
				// this still doesn't affect DVD playback, which requires us to use an overlay window, but that's a special case anyways
				// and key/mouse events are still properly routed to AWT anyways
				
				rootPanel.setComponentZOrder(ep,1);
				rootPanel.setComponentZOrder(master,0);
				rootPanel.validate();
			} else {
				if(Sage.DBG) System.out.println("+++++++++++++++++++++++++++ NO EMBEDDED PANEL!!!!!!!!!!!!!!!!!!!!!!!!!");
				return false;
			}
			
			setTargetView0(renderCanvas.nativeView);
			setLayer0(0, panelSize, clipRect);
		}
		
		if(currDisplayList == null) return true;
		
		// render video first
		boolean hasVideo = false;
		for(ii=0; ii < currDisplayList.size(); ii++) {
			if(((RenderingOp)currDisplayList.get(ii)).isVideoOp()) {
				hasVideo = true;
				break;
			}
		}
		
		establishRenderThread();
		boolean masterSizeChange = !master.getSize().equals(lastMasterSize);
		lastMasterSize = master.getSize();
		currRT = 0;
		rtStack.clear();
		animsThatGoAfterSurfPop.clear();
		
		try {
			if (lastDL != currDisplayList || !uiMgr.areLayersEnabled()) {
				lastDL = null;
				if(uiMgr.areLayersEnabled()) {
					// This is all of the surface names that have been used in Out animation operations. Therefore
					// they should NOT also be used for any In operation. If they are we should duplicate
					// the surface and use that for the Out operation.
					fixDuplicateAnimSurfs(currDisplayList, clipRect);
				}
				
				// now run through the display list executing each operation
				compositeOps.clear();
				for(ii=0; ii <= currDisplayList.size(); ii++) {
					RenderingOp op;
					
					// if wait indicator showing, render it
					if(ii == currDisplayList.size()) {
						if(waitIndicatorState && waitIndicatorRops != null) {
/*
							op = (RenderingOp)waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
							if(op.isImageOp()) {
								long nativePtr = op.texture.getNativeImage(this, op.textureIndex);
								System.out.println("wait indicator image ptr "+nativePtr);
								setWaitIndicatorImage(renderCanvas.nativeView, nativePtr, op.copyImageRect, op.destRect, op.alphaFactor);
								op.texture.removeNativeRef(this, op.textureIndex);
							}
 */
							continue;
						} else break;
					} else op = (RenderingOp) currDisplayList.get(ii);
					
					// this dumps loads more info than we need...
//					qlogln(" "+ii+" -> "+op.toString());
					
					if(op.isImageOp()) {
						// use the MetaImage info to load NSImages on the native side
						// scaling will be done when rendered, so just load by file name
						long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
						
						qlog(" "+ii+" -> image op:");
						if(op.texture != null) qlog(" image="+op.texture.getSource()+"#"+op.textureIndex);
//						if(op.renderXform != null) qlog(" xform="+describeTransform(op.renderXform));
//						if(texturePtr != 0) qlog(" nativePtr="+texturePtr);
						if(op.privateData != null) {
							java.awt.Insets[] inny = (java.awt.Insets[]) op.privateData;
							qlogln(" insets: src=("+inny[0].left+","+inny[0].top+","+inny[0].right+","+inny[0].bottom+") dst=("+inny[1].left+","+inny[1].top+","+inny[1].right+","+inny[1].bottom+")");
						}
						qlogln(" src="+describeFloatRect(op.srcRect)+" dest="+describeFloatRect(op.destRect));
						
						if(texturePtr != 0) {
							if(leopard && op.privateData != null) {
								java.awt.Insets[] inny = (java.awt.Insets[]) op.privateData;
								int [] insets = {inny[0].top, inny[0].left, inny[0].bottom, inny[0].right, inny[1].top, inny[1].left, inny[1].bottom, inny[1].right};
								drawImageWithInsets0(texturePtr, op.copyImageRect, op.destRect, op.alphaFactor, insets);
							} else {
								drawImage0(texturePtr, op.copyImageRect, op.destRect, op.alphaFactor);
							}
						}
						op.texture.removeNativeRef(this, op.textureIndex);
					}
					else if(op.isTextOp()) {
						qlogln(" "+ii+" -> text op: string=\""+op.text.string+"\" op="+op);
						
						Long fontPtr = (Long) fontCacheMap.get(op.text.font);
						
						if(fontPtr == null) {
							// unload fonts on size change
							if(masterSizeChange) freeAllFonts();
							
							long temp = loadFont0(op.text.font.name, op.text.font.style, op.text.font.size);
							
							fontPtr = new Long(temp);
							fontCacheMap.put(op.text.font, fontPtr);
						}
						
						if(fontPtr != null) {
								// calculate positions for each glyph and pass them along
								// we already have glyph vectors in op.text.glyphVector so this is easy
							int gcount = op.text.glyphVector.getNumGlyphs();
							float[] positions = new float[gcount * 2];
							for(int gindex=0; gindex < gcount; gindex++) {
								positions[gindex*2] = op.text.glyphVector.getGlyphPosition(gindex);
								//positions[gindex*2+1] = 0;
							}
							
//							System.out.println("drawTextWithPositions0(\""+op.text.string+"\", "+fontPtr.longValue()+", "+op.copyImageRect.getX()
//											   +", "+op.copyImageRect.getY()+","+positions+"("+gcount+" positions), "+op.destRect+","+op.renderColor+")");
								// adjust Y coordinate to baseline
							drawTextWithPositions0(op.text.string, fontPtr.longValue(), (float)op.copyImageRect.getX(), (float)op.copyImageRect.getY()+op.text.font.getAscent(), positions, op.destRect, op.renderColor);
//							drawText0(op.text.string, fontPtr.longValue(), op.copyImageRect, op.destRect, op.renderColor);
						}
					}
					else if(op.isVideoOp()) {
							// source always seems to be the same as dest...
						qlogln(" "+ii+" -> video op: srcRect="+describeFloatRect(op.srcRect)+" destRect="+describeFloatRect(op.destRect));
						
						if(vf.isNonBlendableVideoPlayerLoaded()) {
							if(lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect)) {
								uiMgr.repaintNextRegionChange = true;
							}
							
							// Convert from UI coordinates to screen coordinates
							if (Math.round(op.destRect.width) == master.getWidth() && 
								Math.round(op.destRect.height) == master.getHeight())
							{
								vf.setVideoBounds(null);
							} else {
								vf.setVideoBounds(op.destRect);
							}
							vf.refreshVideoSizing();
						}
						// We need to refresh the video whenever we redo the regions so always do this.
						// This also can send up update of the video size to the media player which we
						// need for mouse positioning in VMR9 DVD playback so always do it.
						else if(lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect)) {
//							vf.refreshVideoSizing(new java.awt.Dimension(videoWidth, videoHeight));
							// videoWidth and videoHeight are never set!
							vf.refreshVideoSizing();
						}
						
						lastVideoRect = op.destRect;
						processVideoOp(op); // calculates new video bounds based on aspect ratio settings
					}
					else if(op.isPrimitiveOp()) {
						qlog(" "+ii+" -> primitive op: shape="+op.primitive.shapeType+(op.primitive.fill ? " (fill)" : " (stroke)"));
						qlog(" size=("+op.primitive.shapeWidth+"x"+op.primitive.shapeHeight+")");
						if(op.primitive.cornerArc > 0) qlog(" arc radius="+op.primitive.cornerArc);
						qlog(" stroke="+op.primitive.strokeSize);
						if(op.primitive.gradc1 != null) {
							qlog(" grad1="+op.primitive.gradc1+" ("+op.primitive.fx1+","+op.primitive.fy1+
								 ") grad2="+op.primitive.gradc2+" ("+op.primitive.fx2+","+op.primitive.fy2+")"
							);
							// gradc1, gradc2: gradient colors
							// fx1,fy1: gradient 1 coordinate (surface relative?)
							// fx2,fy1: gradient 2 coordinate
						}
						qlog(" color="+op.primitive.color);
						qlogln(" src="+describeFloatRect(op.srcRect)+" dst="+describeFloatRect(op.destRect));
						
						java.awt.Rectangle.Float shapeBounds = new java.awt.Rectangle.Float();
						
						java.awt.Rectangle.Float shapeClipRect = null;
						if(op.primitive.shapeWidth != op.srcRect.width || op.primitive.shapeHeight != op.srcRect.height) {
							shapeClipRect = new java.awt.Rectangle.Float();
							// Transform the clipping rectangle into device space which is what the scissor rect uses.
							MathUtils.transformRectCoords(op.srcRect, op.renderXform, shapeClipRect);
							shapeClipRect.x = Math.max(0, shapeClipRect.x);
							shapeClipRect.y = Math.max(0, shapeClipRect.y);
								// width and height can extend beyond the view bounds, they'll be clipped anyways
//							shapeClipRect.width = Math.min(bufferWidth - shapeClipRect.x, shapeClipRect.width);
//							shapeClipRect.height = Math.min(bufferHeight - shapeClipRect.y, shapeClipRect.height);
							// Skip shapes that are entirely clipped
							if (shapeClipRect.width <= 0 || shapeClipRect.height <= 0)
								continue;
						}
						if(shapeClipRect != null) qlogln("    primitive: clipping shape to "+shapeClipRect);
						
						shapeBounds.x = op.destRect.x - op.srcRect.x;
						shapeBounds.y = op.destRect.y - op.srcRect.y;
						shapeBounds.width = (float)op.primitive.shapeWidth;
						shapeBounds.height = (float)op.primitive.shapeHeight;
						
						qlogln("    primitive: shape bounds="+shapeBounds);
						
						if(op.primitive.shapeType.equals("Rectangle")) {
							if(op.primitive.fill) {
								if(op.primitive.gradc2 == null) {
									drawRect0(shapeBounds, shapeClipRect,
											  op.primitive.cornerArc/2,
											  null, 0,
											  op.primitive.color, 0, 0,		// fill color
											  null, 0, 0,
											  op.alphaFactor
									);
								} else {
									// gradient coordinates must be converted to surface coordinates
									drawRect0(shapeBounds, shapeClipRect,
											  op.primitive.cornerArc/2,
											  null, 0,
											  op.primitive.gradc1, op.primitive.fx1+shapeBounds.x, op.primitive.fy1+shapeBounds.y,
											  op.primitive.gradc2, op.primitive.fx2+shapeBounds.x, op.primitive.fy2+shapeBounds.y,
											  op.alphaFactor
									);
								}
							} else {
								drawRect0(shapeBounds, shapeClipRect,
										  op.primitive.cornerArc/2,
										  op.primitive.color, op.primitive.strokeSize,
										  null, 0, 0,
										  null, 0, 0,
										  op.alphaFactor
								);
							}
						} else if(op.primitive.shapeType.equals("Oval")) {
							if(op.primitive.fill) {
								if(op.primitive.gradc2 == null) {
									drawOval0(shapeBounds, shapeClipRect,
											  null, 0,
											  op.primitive.color, 0, 0,		// fill color
											  null, 0, 0,
											  op.alphaFactor
									);
								} else {
									drawOval0(shapeBounds, shapeClipRect,
											  null, 0,
											  op.primitive.gradc1, op.primitive.fx1+shapeBounds.x, op.primitive.fy1+shapeBounds.y,
											  op.primitive.gradc2, op.primitive.fx2+shapeBounds.x, op.primitive.fy2+shapeBounds.y,
											  op.alphaFactor
									);
								}
							} else {
								drawOval0(shapeBounds, shapeClipRect,
										  op.primitive.color, op.primitive.strokeSize,
										  null, 0, 0,
										  null, 0, 0,
										  op.alphaFactor
								);
							}
						}
					}
					else if(op.isSurfaceOp() && uiMgr.areLayersEnabled()) {
						qlogln(" "+ii+" -> surface op: surface="+op.surface+" "+(op.isSurfaceOpOn() ? "ON" : "OFF"));
						
						if(op.isSurfaceOpOn()) {
							Long layerPtr = (Long)surfaceCache.get(op.surface);
							
							if(currRT != 0) rtStack.push(new Long(currRT));
							
							if(layerPtr == null) {
								// allocate a new layer for this surface
								currRT = createLayer0(master.getSize());
								layerPtr = new Long(currRT);
								surfaceCache.put(op.surface, layerPtr);
							} else {
								currRT = layerPtr.longValue();
							}
						} else {
							// if last pop, add it and any associated animations to the compositeOps list
							if(!rtStack.contains(new Long(currRT))) {
								compositeOps.add(op);
								java.util.ArrayList remnantAnims = (java.util.ArrayList) animsThatGoAfterSurfPop.remove(new Long(currRT));
								if(remnantAnims != null) {
									qlogln("+++ adding animation op to composite ops");
									compositeOps.addAll(remnantAnims);
								}
							}
							if(rtStack.isEmpty())
								currRT = 0;
							else
								currRT = ((Long)rtStack.pop()).longValue();
						}
						
						// setLayer0 handles all the minutiae of allocating the surface and clearing it when it's first activated
						// it also transparently handles size changes...
						setLayer0(currRT, master.getSize(), clipRect);
					}
					else if(op.isAnimationOp() && uiMgr.areLayersEnabled()) {
						qlogln(" "+ii+" -> animation op: "+op.anime.animation+" srcRect="+op.srcRect+" destRect="+op.destRect+" currRT="+currRT);
						
						processAnimOp(op, ii, clipRect);
						
						if(new Long(currRT).equals(surfaceCache.get(op.surface)) || rtStack.contains(surfaceCache.get(op.surface)))
						{
							java.util.ArrayList vecy = (java.util.ArrayList) animsThatGoAfterSurfPop.get(new Long(currRT));
							if(vecy == null)
								animsThatGoAfterSurfPop.put(new Long(currRT), vecy = new java.util.ArrayList());
							vecy.add(compositeOps.remove(compositeOps.size() - 1));
						}
					}
				}
				if(uiMgr.areLayersEnabled()) {
					java.util.Collections.sort(compositeOps, COMPOSITE_LIST_SORTER);
					fixSurfacePostCompositeRegions();
				}
			} else {
				qlogln("Skipping display list execution, compositing directly");
			}
			
			if(!hasVideo)
				lastVideoRect = null;
			
			lastDL = currDisplayList;
			if(uiMgr.areLayersEnabled()) {
				qlogln("Performing compositing/animations");		
				for(int i = 0; i <= compositeOps.size(); i++) {
					RenderingOp op = null;
					
					if(i == compositeOps.size())
					{
						if(waitIndicatorState && waitIndicatorRops != null)
						{
/*
							op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
							if(op.isImageOp()) {
								long nativePtr = op.texture.getNativeImage(this, op.textureIndex);
								System.out.println("wait indicator image ptr "+nativePtr);
								setWaitIndicatorImage(renderCanvas.nativeView, nativePtr, op.copyImageRect, op.destRect, op.alphaFactor);
								op.texture.removeNativeRef(this, op.textureIndex);
							}
 */
							continue;
						} else
							break;
					}
					else op = (RenderingOp) compositeOps.get(i);
					
					if(op.isSurfaceOp()) {
						qlogln("Composite: surface op: "+op.surface+" on=" + op.isSurfaceOpOn());
						if(op.isSurfaceOpOff()) {
							Long surf = (Long)surfaceCache.get(op.surface);
							boolean disableBlend = (/*!hasVideo && */isBackgroundSurface(op.surface));
							java.awt.geom.Rectangle2D.Float validRegion = new java.awt.geom.Rectangle2D.Float();
							java.awt.geom.Rectangle2D.intersect(op.destRect, clipRect, validRegion);
							
							qlogln("     destRect="+op.destRect+" clipRect="+clipRect+" validRegion="+validRegion+" cir="+op.copyImageRect);
							composite0(surf.longValue(), 0, validRegion, validRegion, op.alphaFactor, !disableBlend);
						}
					} else if(op.isImageOp()) {
						qlogln("Composite: image op: "+op.texture.getSource());
						// at this point the current active surface is the drawing surface
						long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
						drawImage0(texturePtr, op.copyImageRect, op.destRect, op.alphaFactor);
						op.texture.removeNativeRef(this, op.textureIndex);
					} else if(op.isAnimationOp()) {
						RenderingOp.Animation anime = op.anime;
						
						Long surf = (Long) surfaceCache.get(op.surface);
						if(surf != null) {
							qlogln("Composite: animation op: "+op+" scrollSrcRect="+op.srcRect+" scrollDstRect="+op.destRect);
							qlogln("Rendering animation: "+anime.animation);
							
							java.awt.geom.Rectangle2D.Float clippedSrcRect = new java.awt.geom.Rectangle2D.Float();
							clippedSrcRect.setRect(op.srcRect);
							
							java.awt.geom.Rectangle2D.Float clippedDstRect = new java.awt.geom.Rectangle2D.Float();
							clippedDstRect.setRect(op.destRect);
							
							java.awt.geom.Rectangle2D.Float clonedClipRect = new java.awt.geom.Rectangle2D.Float();
							clonedClipRect.setRect(clipRect);
							
							Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
							composite0(surf.longValue(), 0, clippedSrcRect, clippedDstRect, op.alphaFactor, true);
							
							if(anime.isDualSurfaceOp()) {
								surf = (Long) surfaceCache.get(anime.altSurfName);
								if(surf != null) {
									qlogln("Rendering second scroll surface scrollSrcRect="+anime.altSrcRect+" scrollDestRect="+anime.altDestRect);
									clippedSrcRect.setRect(anime.altSrcRect);
									clippedDstRect.setRect(anime.altDestRect);
									Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
									composite0(surf.longValue(), 0, clippedSrcRect, clippedDstRect, anime.altAlphaFactor, true);
								}
							}
						} else {
							qlogln("Cached anim surface not found, skipping composition");
						}
						if(!anime.expired)
							master.setActiveAnimation(op);
					}
				}
			}
//			if(!waitIndicatorState) setWaitIndicatorImage(renderCanvas.nativeView, 0, null, null, 0);
			
			lastPresentTime = Sage.eventTime();
			
			boolean currFullScreen = uiMgr.isFullScreen();
			if (Sage.MAC_OS_X && (lastFSMode != currFullScreen) && uiMgr.getRootPanel().isVisible()) {
				if(Sage.DBG) System.out.println("FS mode switch, forcing complete redraw");
				uiMgr.trueValidate();
				lastFSMode = currFullScreen;
			}
		} catch(Throwable t) {
			if(Sage.DBG) {
				System.out.println("Exception while executing display list of "+t);
				Sage.printStackTrace(t);
			}
		}
		releaseRenderThread();
		return true;
	}
	
	private void processVideoOp(RenderingOp op)
	{
		int vw, vh;
		vw = videoWidth;
		vh = videoHeight;
		java.awt.Dimension vfVidSize = null;
		if (vw <= 0 || vh <= 0) vfVidSize = vf.getVideoSize();
		if (vw <= 0)
		{
			vw = vfVidSize != null ? vfVidSize.width : 0;
			if (vw <= 0)
				vw = 720;
		}
		if (vh <= 0)
		{
			vh = vfVidSize != null ? vfVidSize.height : 0;
			if (vh <= 0)
				vh = MMC.getInstance().isNTSCVideoFormat() ? 480 : 576;
		}
		int assMode = vf.getAspectRatioMode();
		float targetX = op.destRect.x;
		float targetY = op.destRect.y;
		float targetW = op.destRect.width;
		float targetH = op.destRect.height;
		float forcedRatio = vf.getCurrentAspectRatio();
		if (forcedRatio != 0)
		{
			if (targetW/targetH < forcedRatio)
			{
				float shrink = targetH - targetW/forcedRatio;
				targetH -= shrink;
				targetY += shrink/2;
			}
			else
			{
				float shrink = targetW - targetH*forcedRatio;
				targetW -= shrink;
				targetX += shrink/2;
			}
		}
		float zoomX = vf.getVideoZoomX(assMode);
		float zoomY = vf.getVideoZoomY(assMode);
		float transX = vf.getVideoOffsetX(assMode) * targetW / lastMasterSize.width;
		float transY = vf.getVideoOffsetY(assMode) * targetH / lastMasterSize.height;
		
		float widthAdjust = (zoomX - 1.0f)*targetW;
		float heightAdjust = (zoomY - 1.0f)*targetH;
		targetX -= widthAdjust/2;
		targetY -= heightAdjust/2;
		targetW += widthAdjust;
		targetH += heightAdjust;
		
		targetX += transX;
		targetY += transY;
		
		long videoHShiftFreq =  vf.getVideoHShiftFreq();
		if (videoHShiftFreq != 0)
		{
			float maxHShift = (op.destRect.width - targetW)/2;
			long timeDiff = Sage.time();
			timeDiff %= videoHShiftFreq;
			if (timeDiff < videoHShiftFreq/2)
			{
				if (timeDiff < videoHShiftFreq/4)
					targetX -= maxHShift*timeDiff*4/videoHShiftFreq;
				else
					targetX -= maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
			}
			else
			{
				timeDiff -= videoHShiftFreq/2;
				if (timeDiff < videoHShiftFreq/4)
					targetX += maxHShift*timeDiff*4/videoHShiftFreq;
				else
					targetX += maxHShift - (maxHShift*(timeDiff - videoHShiftFreq/4)*4/videoHShiftFreq);
			}
		}
		
		videoSrc.setRect(0, 0, vw, vh);
		videoDest.setRect(targetX, targetY, targetW, targetH);
		clipArea.setRect(0, 0, lastMasterSize.width, lastMasterSize.height);
		
		Sage.clipSrcDestRects(op.destRect, videoSrc, videoDest);
		
		// FIXME: determine if these are really necessary for our purposes
		
			// clipped source rect
		srcVideoRect.setFrame(videoSrc);
		srcVideoRect.x = Math.max(0, srcVideoRect.x);
		srcVideoRect.y = Math.max(0, srcVideoRect.y);
		srcVideoRect.width = Math.min(vw - srcVideoRect.x, srcVideoRect.width);
		srcVideoRect.height = Math.min(vh - srcVideoRect.y, srcVideoRect.height);
		
			// where the video will actually be played inside the video player bounds
		usedVideoRect.setFrame(videoDest);
		usedVideoRect.x = Math.max(0, usedVideoRect.x);
		usedVideoRect.y = Math.max(0, usedVideoRect.y);
		usedVideoRect.width = Math.min(lastMasterSize.width - usedVideoRect.x, usedVideoRect.width);
		usedVideoRect.height = Math.min(lastMasterSize.height - usedVideoRect.y, usedVideoRect.height);
		
			// video player bounds
		fullVideoRect.setFrame(op.destRect);
		fullVideoRect.x = Math.max(0, fullVideoRect.x);
		fullVideoRect.y = Math.max(0, fullVideoRect.y);
		fullVideoRect.width = Math.min(lastMasterSize.width - fullVideoRect.x, fullVideoRect.width);
		fullVideoRect.height = Math.min(lastMasterSize.height - fullVideoRect.y, fullVideoRect.height);
		
//		System.out.println("setting video rects: src="+videoSrc+", dest="+videoDest+", bg="+vf.getVideoBGColor());
		
		  // all the blank region and video rendering will be done elsewhere, just send the bounds to draw the video into along
		clearRect0(op.destRect);
		setVideoRects0(videoSrc, videoDest, vf.getVideoBGColor());
	}
	
	public void present(java.awt.Rectangle clipRect) {
		qlogln("present");
		
		if(renderCanvas != null) {
//			System.out.println("presenting in rect ("+clipRect.x+","+clipRect.y+"),("+clipRect.width+"x"+clipRect.height+")");
			present0(renderCanvas.nativeView, clipRect);
			renderCanvas.repaint();
		}
		markAnimationStartTimes();
	}
	
	private void freeAllFonts()
	{
		java.util.Iterator walker = fontCacheMap.values().iterator();
		while (walker.hasNext())
		{
			Long fontPtr = (Long) walker.next();
			if(fontPtr != null)
				unloadFont0(fontPtr.longValue());
		}
		fontCacheMap.clear();
	}
	
	public void cleanupRenderer()
	{
		qlogln("cleanupRenderer");
		freeAllFonts();
		cleanupRenderer0();
	}
	
	public boolean hasOSDRenderer() {
		return false; // ??? technically, we could...
	}
	
	public boolean supportsPartialUIUpdates() {
		return true;
	}
	
	// -----------------------------------------------------------------------------------------
	// native calls
	
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
								  float alphaFactor
								  );
	private native void drawOval0(java.awt.geom.Rectangle2D.Float bounds, java.awt.geom.Rectangle2D.Float clipRect,
								  java.awt.Color strokeColor, int strokeWidth,	// if null, no border drawn
								  java.awt.Color gc1, float gc1x, float gc1y,	// if null, not filled
								  java.awt.Color gc2, float gc2x, float gc2y,	// if ! null, gradient is applied
								  float alphaFactor
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
	
	// -----------------------------------------------------------------------------------------
	
	private VideoFrame vf;
	private java.awt.geom.Rectangle2D.Float lastVideoRect;
	private sage.QuartzRendererView renderCanvas = null;
	
	private int videoWidth = 0;
	private int videoHeight = 0;
	private int videoARx;
	private int videoARy;
	
	// pulled from JOGL renderer
	private java.awt.geom.Rectangle2D.Float videoSrc = new java.awt.geom.Rectangle2D.Float();
	private java.awt.geom.Rectangle2D.Float videoDest = new java.awt.geom.Rectangle2D.Float();
	private java.awt.geom.Rectangle2D.Float clipArea = new java.awt.geom.Rectangle2D.Float();
	private java.awt.Rectangle srcVideoRect = new java.awt.Rectangle();
	private java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
	private java.awt.Rectangle fullVideoRect = new java.awt.Rectangle();
	
	private boolean lastFSMode = false;
	private java.awt.Dimension lastMasterSize = null;
	private long currRT = 0;
	private java.util.Stack rtStack = new java.util.Stack();
	private java.util.Map animsThatGoAfterSurfPop = new java.util.HashMap();
	private java.util.ArrayList lastDL = null;
	
	// image management
	// there's no limit on the number of images we can load, Quartz manages VRAM transparently
	// but we need to release images if they haven't been used in a while or our application heap will grow unwieldy
	private long imageMemoryLimit;
	private java.util.Map fontCacheMap = new java.util.HashMap();
}
