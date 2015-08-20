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

import javax.media.opengl.*;
import com.sun.opengl.util.*;
import com.sun.opengl.impl.*;

/**
 *
 * @author Narflex
 */
public class JOGLSageRenderer extends SageRenderer implements NativeImageAllocator, JOGLVideoUI
{
	/*
	 * NOTE: THIS IS A WORK IN PROGRESS, THE FOLLOWING THINGS ARE NOT COMPLETE
	 * - resource cleanup; I don't think it disposes the OpenGL system completely
	 */
	private static JOGLSageRenderer defaultJOGLRenderer;
	private static final int ELLIPTICAL_DIVISIONS = 50;
	private static final int RECT_CORNER_DIVISIONS = 32;
	private static final int MIN_DIVISIONS = 4;
	private static final int VERTEX_CACHE_SIZE = 8192;
	private static final int VRAM_USAGE_PERCENT = 80;
	private static final boolean D3D_DEBUG = false;
	
	private static final boolean PREMULTIPLY_ALPHA = true;
	
	public JOGLSageRenderer(ZRoot inMaster)
	{
		super(inMaster);
		defaultJOGLRenderer = this;
		vf = uiMgr.getVideoFrame();
		if(java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.BIG_ENDIAN) bigendian = true;
		glAlphaColorModel = new java.awt.image.ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),
											new int[] {8,8,8,8},
											true,
											true,
											java.awt.image.ComponentColorModel.TRANSLUCENT,
											java.awt.image.DataBuffer.TYPE_BYTE);
	}
	
	public boolean allocateBuffers(int width, int height)
	{
		int maxDim = uiMgr.getInt("ui/max_dimension_size", 2048);
		
		if (width > maxDim)
			width = maxDim;
		if (height > maxDim)
			height = maxDim;
		currSurfWidth = bufferWidth = width;
		currSurfHeight = bufferHeight = height;
		
		videoMemoryLimit = uiMgr.getInt("ui/texture_memory_limit", 32) * 1000000;//32000000; // this may get changed after we establish the max texture size
		if(Sage.MAC_OS_X && width >= 1024 && videoMemoryLimit < 64000000) videoMemoryLimit = 64000000;
		
		srcXCache = new float[VERTEX_CACHE_SIZE];
		srcYCache = new float[VERTEX_CACHE_SIZE];
		srcWCache = new float[VERTEX_CACHE_SIZE];
		srcHCache = new float[VERTEX_CACHE_SIZE];
		dstXCache = new float[VERTEX_CACHE_SIZE];
		dstYCache = new float[VERTEX_CACHE_SIZE];
		dstWCache = new float[VERTEX_CACHE_SIZE];
		dstHCache = new float[VERTEX_CACHE_SIZE];
		cCache = new int[VERTEX_CACHE_SIZE];
		return true;
	}
	
	public boolean useTextureMapsForText()
	{
		// Using DX9 fonts is faster, but the text measuring isn't always right. The spacing
		// between the glyphs is a little different from Java which makes the bounding boxes different
// NOTE: WE SHOULD NOT USE TEXTURE MAPS FOR UNICODE FONTS!!!!!!!!!!
// BUT HOW DO YOU DETERMINE THAT??
		return true;//!Sage.WINDOWS_OS || uiMgr.getBoolean("ui/use_texture_maps_for_3d_text", true);
	}
	
	public void preprocessNextDisplayList(java.util.ArrayList v)
	{
		cullDisplayList(v);
		for (int i = 0; i < v.size(); i++)
		{
			RenderingOp op = (RenderingOp) v.get(i);
			if (op.isTextOp() && useTextureMapsForText())
			{
				convertGlyphsToCachedImages(op);
				if (op.text.fontImage != null && op.text.renderImageNumCache != null)
				{
					for (int j = 0; j < op.text.renderImageNumCache.length; j++)
					{
						if (op.text.renderImageNumCache[j] != -1 && pbuffer != null)
						{
//							op.text.fontImage.getNativeImage(this, op.text.renderImageNumCache[j]);
//							op.text.fontImage.removeNativeRef(this, op.text.renderImageNumCache[j]);
						}
					}
				}
				continue;
			}
			else if (op.isImageOp())
			{
				// Check for valid image bounds first
				if (op.srcRect.width <= 0 || op.srcRect.height <= 0 || op.destRect.width <= 0 || op.destRect.height <= 0)
				{
					v.remove(i--);
				}
				else
				{
					// This'll have them all loaded before we execute the DL. If we can't fit them
					// all into memory, then they'll load individually as needed while rendering the DL

					// Limit the maximum texture size or megapixel pictures from the image library can kill us.
					if (op.texture.getWidth(op.textureIndex) > maxTextureDim ||
						op.texture.getHeight(op.textureIndex) > maxTextureDim)
					{
						float xScale = Math.min(maxTextureDim,
							op.texture.getWidth(op.textureIndex))/((float)op.texture.getWidth(op.textureIndex));
						float yScale = Math.min(maxTextureDim,
							op.texture.getHeight(op.textureIndex))/((float)op.texture.getHeight(op.textureIndex));
						xScale = yScale = Math.min(xScale, yScale);
						op.scaleSrc(xScale, yScale);
						op.textureIndex = op.texture.getImageIndex(Math.round(xScale *
							op.texture.getWidth(op.textureIndex)), Math.round(yScale *
							op.texture.getHeight(op.textureIndex)));
					}
					if (pbuffer != null)
					{
						// Since we're not guaranteed to be allocated before this call
//						op.texture.getNativeImage(this, op.textureIndex);
//						op.texture.removeNativeRef(this, op.textureIndex);
					}
				}
			}
		}
	}
	
	protected boolean compositeSurfaces(Object targetSurface, Object srcSurface, float alphaFactor, java.awt.geom.Rectangle2D region)
	{
		int[] target = (targetSurface instanceof Long) ? (new int[] { ((Long) targetSurface).intValue() }) : (int[])targetSurface;
		int[] src = (srcSurface instanceof Long) ? (new int[] { ((Long) srcSurface).intValue() }) : (int[])srcSurface;
		if (src != null && target != null)
		{
			int[] lastRT = currRT;
			GL gl = pbuffer.getGL();
			float x = (float)region.getX();
			float y = (float)region.getY();
			float w = (float)region.getWidth();
			float h = (float)region.getHeight();
			setRenderTarget(target);
			// Since it's the same dimensions just use nearest neighbor scaling
			gl.glEnable(GL.GL_BLEND);
			gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV,src[src.length == 4 ? 1 : 0]);
			gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
			setGLColor(gl, getShadingColor(java.awt.Color.white, alphaFactor));

			gl.glBegin(GL.GL_QUADS);
			gl.glTexCoord2f(x, y);
			gl.glVertex2f(x, y);
			gl.glTexCoord2f(x + w, y);
			gl.glVertex2f(x + w, y);
			gl.glTexCoord2f(x + w, y + h);
			gl.glVertex2f(x + w, y + h);
			gl.glTexCoord2f(x, y + h);
			gl.glVertex2f(x, y + h);
			gl.glEnd();
			gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
			setRenderTarget(lastRT);
			return true;
		}
		return false;
	}
	
	protected void setRenderTarget(int[] newRT)
	{
		GL gl = pbuffer.getGL();
		if (newRT == null)
		{
			currSurfWidth = bufferWidth;
			currSurfHeight = bufferHeight;
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, 0);
			gl.glBindFramebufferEXT(gl.GL_FRAMEBUFFER_EXT, 0);
			gl.glViewport(0, 0, currSurfWidth, currSurfHeight);
			gl.glMatrixMode(gl.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glOrtho(0, currSurfWidth, 0, currSurfHeight, -1.0, 1.0);
			gl.glMatrixMode(gl.GL_MODELVIEW);
			gl.glLoadIdentity();
		}
		else
		{
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, 0);
			gl.glBindFramebufferEXT(gl.GL_FRAMEBUFFER_EXT, newRT[0]);
			gl.glViewport(0, 0, newRT[2], newRT[3]);
			gl.glMatrixMode(gl.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glOrtho(0, newRT[2], 0, newRT[3], -1.0, 1.0);
			gl.glMatrixMode(gl.GL_MODELVIEW);
			gl.glLoadIdentity();
			currSurfWidth = newRT[2];
			currSurfHeight = newRT[3];
		}
	}

	public static int getCompositedColor(java.awt.Color color, float alpha)
	{
		if (color == null)
		{
			return ((int)(alpha*255) << 24);
		}
		if (PREMULTIPLY_ALPHA)
		{
			return (((int)(alpha * 255) & 0xFF) << 24) + 
				(((int)(alpha * color.getRed()) & 0xFF) << 16) +
				(((int)(alpha * color.getGreen()) & 0xFF) << 8) +
				((int)(alpha * color.getBlue()) & 0xFF);
		}
		else
		{
			return ((int)(alpha*255) << 24) + (color.getRGB() & 0xFFFFFF);
		}
	}
	
	public static int getCompositedColor(int color, float alpha)
	{
		if (PREMULTIPLY_ALPHA)
		{
			return (((int)(alpha * 255) & 0xFF) << 24) + 
				(((int)(alpha * ((color >> 16) & 0xFF)) & 0xFF) << 16) +
				(((int)(alpha * ((color >> 8) & 0xFF)) & 0xFF) << 8) +
				((int)(alpha * (color & 0xFF)) & 0xFF);
		}
		else
		{
			return ((int)(alpha*255) << 24) + (color & 0xFFFFFF);
		}
	}
	
	private static int getShadingColor(java.awt.Color c, float alpha)
	{
		if (PREMULTIPLY_ALPHA)
		{
			if (c == null)
			{
				int val = (int) (255*alpha);
				return ((val & 0xFF) << 24) + ((val & 0xFF) << 16) + ((val & 0xFF) << 8) + (val & 0xFF);
			}
			else
			{
				alpha *= (c.getAlpha() / 255.0f); // since the color may already have alpha that is not premultiplied
				return (((int)(alpha * 255) & 0xFF) << 24) + 
					(((int)(alpha * c.getRed()) & 0xFF) << 16) +
					(((int)(alpha * c.getGreen()) & 0xFF) << 8) +
					((int)(alpha * c.getBlue()) & 0xFF);
			}
		}
		else
		{
			if (c == null)
				return (((int)(255*alpha) & 0xFF) << 24) + 0xFFFFFF;
			else
				return (((int)(alpha * c.getAlpha()) & 0xFF) << 24) + (c.getRGB() & 0xFFFFFF);
		}
	}
	
	public boolean executeDisplayList(final java.awt.Rectangle clipRect)
	{
		if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
		{
			final boolean[] retholder = new boolean[1];
			javax.media.opengl.Threading.invokeOnOpenGLThread(new Runnable()
			{
				public void run()
				{
					inframe = true;
					retholder[0] = executeDisplayListSync(clipRect);
				}
			});
			return retholder[0];
		}
		else
		{
			inframe = true;
			return executeDisplayListSync(clipRect);
		}
	}
	private boolean executeDisplayListSync(java.awt.Rectangle clipRect)
	{
		if (D3D_DEBUG && Sage.DBG) System.out.println("JOGLSageRenderer is executing displayList list=" + (currDisplayList == null ? 0 : currDisplayList.size()));
		if (currDisplayList == null)
		{
			inframe = false;
			return true;
		}
		
		if(rebuildRenderers) {
			// rebuild the native GL context
			GLCapabilities caps2 = new GLCapabilities();
			caps2.setDoubleBuffered(true);
			caps2.setStereo(false);
			drawable = GLDrawableFactory.getFactory().getGLDrawable(master, caps2, null);
			// recycle the pbuffer context so we don't have to reset everything, since it's shared with our video renderer
			context = (GLContextImpl) drawable.createContext(pbuffer.getContext());
			context.setSynchronized(true);
			drawableHelper.setAutoSwapBufferMode(true);
			drawable.setRealized(true);
			
			rebuildRenderers = false;
		}
		else if (!realized)
		{
			videorenderer = new sage.miniclient.OpenGLVideoRenderer(this);
			GLCapabilities caps2 = new GLCapabilities();
			caps2.setDoubleBuffered(true);
			caps2.setStereo(false);
			drawable = GLDrawableFactory.getFactory().getGLDrawable(master, caps2, null);
			context = (GLContextImpl) drawable.createContext(null);
			context.setSynchronized(true);
			drawableHelper.setAutoSwapBufferMode(true);
			initpbuffer(bufferWidth, bufferHeight);
			drawable.setRealized(true);
			realized = true;
		}
			
		boolean hasVideo = false;
		for (int i = 0; i < currDisplayList.size(); i++)
			if (((RenderingOp) currDisplayList.get(i)).isVideoOp())
				hasVideo = true;
		
		// NOW EXECUTE THE DISPLAY LIST
		establishRenderThread();
		boolean masterSizeChange = !master.getSize().equals(lastMasterSize);
//		clearScene0(uiMgr.getCurrUI() != lastUI || masterSizeChange);
		lastUI = uiMgr.getCurrUI();
		lastMasterSize = master.getSize();
		if (currRT != null)
		{
			setRenderTarget(null);
			currRT = null;
		}
		rtStack.clear();
		animsThatGoAfterSurfPop.clear();
		
		rerenderedDL = (currDisplayList == lastDL) && Sage.WINDOWS_OS && !waitIndicatorState &&
			Sage.getBoolean("ui/enable_display_list_vram_caching", false);

		if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
		{
			System.out.println("Couldn't make pbuffer current?");
			inframe = false;
			return false;
		}
		boolean setVideoRegion = false;
		GL gl = pbuffer.getGL();
		try
		{
			if (lastDL != currDisplayList || !uiMgr.areLayersEnabled())
			{
				lastDL = null;
				if (uiMgr.areLayersEnabled())
				{
					// This is all of the surface names that have been used in Out animation operations. Therefore
					// they should NOT also be used for any In operation. If they are we should duplicate
					// the surface and use that for the Out operation.
					fixDuplicateAnimSurfs(currDisplayList, clipRect);
				}

				compositeOps.clear();
				java.util.Set clearedSurfs = new java.util.HashSet();
				for (int i = 0; i <= currDisplayList.size(); i++)
				{
					RenderingOp op;
					if (i == currDisplayList.size())
					{
						if (waitIndicatorState && waitIndicatorRops != null)
						{
							op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
						}
						else
							break;
					}
					else
						op = (RenderingOp) currDisplayList.get(i);
					if (op.isImageOp())
					{
						// Process operations on the same texture that follow this one
						long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
						int texturet[] = new int[] { (int) texturePtr };
						textureCopy(gl, (int) texturePtr, op.srcRect, op.destRect, op.renderColor, op.alphaFactor, true);
						op.texture.removeNativeRef(this, op.textureIndex);
					}
					else if (op.isTextOp())
					{
						if (op.text.fontImage != null && op.text.renderRectCache != null)
						{
							int numFontImages = op.text.fontImage.getNumImages();
							int numGlyphs = op.text.renderImageNumCache.length;
							for (int j = 0; j < numFontImages; j++)
							{
								long texturePtr = -1;
								int rectCoordIndex = 0;
								for (int k = 0; k < numGlyphs; k++)
								{
									if (op.text.renderImageNumCache[k] == j)
									{
										if (texturePtr == -1)
											texturePtr = op.text.fontImage.getNativeImage(this, j);
										// NOTE: The addition of 0.01f is very important. It prevents having boundary
										// alignment issues where the nearest-neighbor pixel samplings would skip pixels
										// However, this was only one case I had when I fixed this so I can't be sure it
										// actually fixes all cases.
										srcXCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].x + 0.01f;
										srcYCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].y + 0.01f;
										srcWCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].width;
										srcHCache[rectCoordIndex] = op.text.renderGlyphRectCache[k].height;
										dstXCache[rectCoordIndex] = op.text.renderRectCache[2*k];
										dstYCache[rectCoordIndex] = op.text.renderRectCache[2*k+1];
										dstWCache[rectCoordIndex] = srcWCache[rectCoordIndex];
										dstHCache[rectCoordIndex] = srcHCache[rectCoordIndex];
										cCache[rectCoordIndex] = getShadingColor(op.renderColor, op.alphaFactor);
										rectCoordIndex++;
									}
								}
								if (rectCoordIndex > 0)
								{
									gl.glPushMatrix();
									java.util.Arrays.fill(currMatCoords, 0);
									currMatCoords[12] = op.copyImageRect.x;
									currMatCoords[13] = op.copyImageRect.y;
//									MathUtils.getMatrixValuesTransposed(op.renderXform, currMatCoords);
									gl.glLoadMatrixf(currMatCoords, 0);
									int texturet[] = new int[] { (int) texturePtr };
									gl.glEnable(GL.GL_BLEND);
									gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
									gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV,texturet[0]);
									gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
									setGLColor(gl, getShadingColor(op.renderColor, op.alphaFactor));
									for (int q = 0; q < rectCoordIndex; q++)
									{
										gl.glBegin(GL.GL_QUADS);
										gl.glTexCoord2f(srcXCache[q], srcYCache[q]);
										gl.glVertex2f(dstXCache[q], dstYCache[q]);
										gl.glTexCoord2f(srcXCache[q] + srcWCache[q], srcYCache[q]);
										gl.glVertex2f(dstXCache[q] + dstWCache[q], dstYCache[q]);
										gl.glTexCoord2f(srcXCache[q] + srcWCache[q], srcYCache[q] + srcHCache[q]);
										gl.glVertex2f(dstXCache[q] + dstWCache[q], dstYCache[q] + dstHCache[q]);
										gl.glTexCoord2f(srcXCache[q], srcYCache[q] + srcHCache[q]);
										gl.glVertex2f(dstXCache[q], dstYCache[q] + dstHCache[q]);
										gl.glEnd();
									}
									gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
									gl.glPopMatrix();
									op.text.fontImage.removeNativeRef(this, j);
								}
							}
						}
						else
						{
							// Render using 3D text operations directly, required for Unicode text
							com.sun.opengl.util.j2d.TextRenderer fontPtr = (com.sun.opengl.util.j2d.TextRenderer) fontCacheMap.get(op.text.font);
							if (fontPtr == null)
							{
								if (masterSizeChange)
								{
									// If we don't clear the fonts somewhere, it's too easy to run out of vram. So
									// do it here. Changing the frame size is the most likely thing that'll cause
									// the allocation of a large number of fonts so this protects against that.
									freeAllFonts();
								}
								//java.awt.FontMetrics fm = master.getFontMetrics(op.text.font);
								fontPtr = new com.sun.opengl.util.j2d.TextRenderer(MetaFont.getJavaFont(op.text.font), true, false);
								if (Sage.DBG) System.out.println("Added font to 3D cache " + op.text.font);
								fontCacheMap.put(op.text.font, fontPtr);
							}
							if (fontPtr != null)
							{
								gl.glMatrixMode(gl.GL_PROJECTION);
								gl.glPushMatrix();
								gl.glLoadIdentity();
								gl.glOrtho(0,currSurfWidth,currSurfHeight,0,-1.0,1.0);
								fontPtr.begin3DRendering();

								fontPtr.setColor(new java.awt.Color(getShadingColor(op.renderColor, op.alphaFactor), true));
	//							gl.glEnable(GL.GL_SCISSOR_TEST);
	//							gl.glScissor(clipX, clipY, clipW, clipH);

								fontPtr.draw3D(op.text.string, op.destRect.x, bufferHeight-op.destRect.y-op.text.font.getAscent(), 0, 1);
	//							gl.glDisable(GL.GL_SCISSOR_TEST);
								fontPtr.end3DRendering();
								gl.glMatrixMode(gl.GL_PROJECTION);
								gl.glPopMatrix();
								gl.glMatrixMode(gl.GL_MODELVIEW);
	//							renderText0(op.text.string, fontPtr.longValue(),
	//								op.destRect.x, op.destRect.y, op.destRect.width, op.destRect.height,
	//								getShadingColor(op.renderColor, op.alphaFactor));
							}
						}
					}
					else if (op.isVideoOp())
					{
						if (vf.isNonBlendableVideoPlayerLoaded())
						{
							if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
							{
								uiMgr.repaintNextRegionChange = true;
							}

							// Convert from UI coordinates to screen coordinates
							if (Math.round(op.destRect.width) == master.getWidth() && 
								Math.round(op.destRect.height) == master.getHeight())
							{
								vf.setVideoBounds(null);
							}
							else
							{
								vf.setVideoBounds(op.destRect);
							}
							vf.refreshVideoSizing();
						}
						// We need to refresh the video whenever we redo the regions so always do this.
						// This also can send up update of the video size to the media player which we
						// need for mouse positioning in VMR9 DVD playback so always do it.
						else if (lastVideoRect == null || !SageRenderer.floatRectEquals(op.destRect, lastVideoRect))
						{
							vf.refreshVideoSizing(new java.awt.Dimension(videoWidth, videoHeight));
						}
						lastVideoRect = op.destRect;
						processVideoOp(op);
					}
					else if (op.isPrimitiveOp())
					{
						java.awt.Rectangle shapeClipRect = null;
						if (op.primitive.shapeWidth != op.srcRect.width || op.primitive.shapeHeight != op.srcRect.height)
						{
							shapeClipRect = new java.awt.Rectangle();
							// Transform the clipping rectangle into device space which is what the scissor rect uses.
							//MathUtils.transformRectCoords(op.srcRect, op.renderXform, shapeClipRect);
							shapeClipRect.setRect(op.srcRect);
							shapeClipRect.x += op.copyImageRect.x;
							shapeClipRect.y += op.copyImageRect.y;
							shapeClipRect.x = Math.max(0, shapeClipRect.x);
							shapeClipRect.y = Math.max(0, shapeClipRect.y);
							shapeClipRect.width = Math.min(currSurfWidth - shapeClipRect.x, shapeClipRect.width);
							shapeClipRect.height = Math.min(currSurfHeight - shapeClipRect.y, shapeClipRect.height);
							// Skip shapes that are entirely clipped
							if (shapeClipRect.width <= 0 || shapeClipRect.height <= 0)
								continue;
						}
						if (shapeClipRect != null)
						{
							gl.glEnable(GL.GL_SCISSOR_TEST);
							gl.glScissor(shapeClipRect.x, shapeClipRect.y, shapeClipRect.width, shapeClipRect.height);
						}
						java.util.Arrays.fill(currMatCoords, 0);
						currMatCoords[12] = op.copyImageRect.x;
						currMatCoords[13] = op.copyImageRect.y;
//						MathUtils.getMatrixValuesTransposed(op.renderXform, currMatCoords);
						gl.glPushMatrix();
						gl.glLoadMatrixf(currMatCoords, 0);
						if (op.primitive.fill)
						{
							if (op.primitive.shapeType.equals("Rectangle"))
							{
								if (op.primitive.cornerArc == 0)
								{
									gl.glEnable(GL.GL_BLEND);
									gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
									gl.glBegin(GL.GL_QUADS);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor));
									else
										setGLColor(gl, getCompositedColor(op.primitive.color, op.alphaFactor));
									gl.glVertex2f(0,0);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor));
									gl.glVertex2f(op.primitive.shapeWidth,0);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor));
									gl.glVertex2f(op.primitive.shapeWidth, op.primitive.shapeHeight);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor));
									gl.glVertex2f(0,op.primitive.shapeHeight);
									gl.glEnd();
								}
								else
								{
									// limit the corner arc based on overall width/height
									op.primitive.cornerArc = Math.min(op.primitive.cornerArc, (int)Math.floor(Math.min(
										op.primitive.shapeWidth/2, op.primitive.shapeHeight/2)));

									int argbTL, argbTR, argbBR, argbBL;
									if (op.primitive.gradc1 != null)
									{
										argbTL = getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor);
										argbTR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor);
										argbBR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor);
										argbBL = getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor);
									}
									else
										argbTL = argbTR = argbBR = argbBL = getCompositedColor(op.primitive.color, op.alphaFactor);
									gl.glEnable(GL.GL_BLEND);
									gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
									float arcRadius=op.primitive.cornerArc/2;
									drawGLCurve(gl, arcRadius, arcRadius, arcRadius, arcRadius,
										(float)(2.0/4.0*2*java.lang.Math.PI), (float)(3.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL,
										argbTR,
										argbBR,
										argbBL,
										0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight,
										true,
										1);
									drawGLCurve(gl, op.primitive.shapeWidth-arcRadius, arcRadius, arcRadius, arcRadius,
										(float)(3.0/4.0*2*java.lang.Math.PI), (float)(4.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL,
										argbTR,
										argbBR,
										argbBL,
										0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight,
										true,
										1);
									drawGLCurve(gl, op.primitive.shapeWidth-arcRadius, op.primitive.shapeHeight-arcRadius, arcRadius, arcRadius,
										(float)(0.0/4.0*2*java.lang.Math.PI),(float)( 1.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL,
										argbTR,
										argbBR,
										argbBL,
										0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight,
										true,
										1);
									drawGLCurve(gl, arcRadius, op.primitive.shapeHeight-arcRadius, arcRadius, arcRadius,
										(float)(1.0/4.0*2*java.lang.Math.PI), (float)(2.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL,
										argbTR,
										argbBR,
										argbBL,
										0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight,
										true,
										1);
									gl.glBegin(GL.GL_QUADS);
									setGLColor(gl, interpColor(argbTL, argbTR, 1.0f*arcRadius/op.primitive.shapeWidth));
									gl.glVertex2f(arcRadius,0);
									setGLColor(gl, interpColor(argbTL, argbTR, 1-1.0f*arcRadius/op.primitive.shapeWidth));
									gl.glVertex2f(op.primitive.shapeWidth-arcRadius,0);
									setGLColor(gl, interpColor(argbBR, argbBL, 1.0f*arcRadius/op.primitive.shapeWidth));
									gl.glVertex2f(op.primitive.shapeWidth-arcRadius,op.primitive.shapeHeight);
									setGLColor(gl, interpColor(argbBR, argbBL, 1-1.0f*arcRadius/op.primitive.shapeWidth));
									gl.glVertex2f(arcRadius,op.primitive.shapeHeight);

									setGLColor(gl, interpColor(argbTR, argbBR, 1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(op.primitive.shapeWidth,arcRadius);
									setGLColor(gl, interpColor(argbTR, argbBR, 1-1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(op.primitive.shapeWidth,op.primitive.shapeHeight-arcRadius);
									setGLColor(gl, interpColor(interpColor(argbBR, argbBL, 1.0f*arcRadius/op.primitive.shapeWidth),
										interpColor(argbTR, argbTL, 1.0f*arcRadius/op.primitive.shapeWidth), 1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(op.primitive.shapeWidth-arcRadius,op.primitive.shapeHeight-arcRadius);
									setGLColor(gl, interpColor(interpColor(argbBR, argbBL, 1.0f*arcRadius/op.primitive.shapeWidth),
										interpColor(argbTR, argbTL, 1.0f*arcRadius/op.primitive.shapeWidth), 1-1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(op.primitive.shapeWidth-arcRadius,arcRadius);

									setGLColor(gl, interpColor(argbBL, argbTL, 1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(0,op.primitive.shapeHeight-arcRadius);
									setGLColor(gl, interpColor(argbBL, argbTL, 1-1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(0,arcRadius);
									setGLColor(gl, interpColor(interpColor(argbTL, argbTR, 1.0f*arcRadius/op.primitive.shapeWidth),
										interpColor(argbBL, argbBR, 1.0f*arcRadius/op.primitive.shapeWidth), 1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(arcRadius,arcRadius);
									setGLColor(gl, interpColor(interpColor(argbTL, argbTR, 1.0f*arcRadius/op.primitive.shapeWidth),
										interpColor(argbBL, argbBR, 1.0f*arcRadius/op.primitive.shapeWidth), 1-1.0f*arcRadius/op.primitive.shapeHeight));
									gl.glVertex2f(arcRadius,op.primitive.shapeHeight-arcRadius);

									gl.glEnd();
								}
							}
							else if (op.primitive.shapeType.equals("Oval"))
							{
								gl.glEnable(GL.GL_BLEND);
								gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
								int argbTL, argbTR, argbBR, argbBL;
								if (op.primitive.gradc1 != null)
								{
									argbTL = getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor);
									argbTR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor);
									argbBR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor);
									argbBL = getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor);
								}
								else
									argbTL = argbTR = argbBR = argbBL = getCompositedColor(op.primitive.color, op.alphaFactor);
								drawGLCurve(gl, op.primitive.shapeWidth/2.0f, op.primitive.shapeHeight/2.0f,
									op.primitive.shapeWidth/2.0f, op.primitive.shapeHeight/2.0f,
									(float)0, (float) (2*java.lang.Math.PI), 64, 
									argbTL, argbTR, argbBR, argbBL, 0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight, true,
									1);
							}
						}
						else
						{
							if (op.primitive.shapeType.equals("Rectangle"))
							{
								// Limit the corner arc based on thickness, otherwise it doesn't make sense
								if (op.primitive.cornerArc == 0)
								{
									gl.glLineWidth(op.primitive.strokeSize);
									gl.glEnable(GL.GL_BLEND);
									gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
									gl.glBegin(GL.GL_LINE_STRIP);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor));
									else
										setGLColor(gl, getCompositedColor(op.primitive.color, op.alphaFactor));
									gl.glVertex2f(0, 0);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor));
									gl.glVertex2f(op.primitive.shapeWidth,0);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor));
									gl.glVertex2f(op.primitive.shapeWidth, op.primitive.shapeHeight);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor));
									gl.glVertex2f(0, op.primitive.shapeHeight);
									if (op.primitive.gradc1 != null)
										setGLColor(gl, getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor));
									gl.glVertex2f(0, 0);
									gl.glEnd();
								}
								else
								{
									gl.glLineWidth(op.primitive.strokeSize);
									gl.glEnable(GL.GL_BLEND);
									gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
									op.primitive.cornerArc = Math.min(op.primitive.cornerArc, (int)Math.floor(Math.min(
										op.primitive.shapeWidth/2, op.primitive.shapeHeight/2)));
									float arcRadius=op.primitive.cornerArc/2;
									int argbTL, argbTR, argbBR, argbBL;
									if (op.primitive.gradc1 != null)
									{
										argbTL = getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor);
										argbTR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor);
										argbBR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor);
										argbBL = getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor);
									}
									else
										argbTL = argbTR = argbBR = argbBL = getCompositedColor(op.primitive.color, op.alphaFactor);
									int x = 0;
									int y = 0;
									int thickness = op.primitive.strokeSize;
									float width = op.primitive.shapeWidth;
									float height = op.primitive.shapeHeight;
									drawGLCurve(gl, arcRadius, arcRadius, arcRadius, arcRadius,
										(float)(2.0/4.0*2*java.lang.Math.PI), (float)(3.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
										thickness);
									drawGLCurve(gl, x+width-arcRadius, y+arcRadius, arcRadius, arcRadius,
										(float)(3.0/4.0*2*java.lang.Math.PI), (float)(4.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
										thickness);
									drawGLCurve(gl, x+width-arcRadius, y+height-arcRadius, arcRadius, arcRadius,
										(float)(0.0/4.0*2*java.lang.Math.PI),(float)( 1.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
										thickness);
									drawGLCurve(gl, x+arcRadius, y+height-arcRadius, arcRadius, arcRadius,
										(float)(1.0/4.0*2*java.lang.Math.PI), (float)(2.0/4.0*2*java.lang.Math.PI),
										(int)arcRadius, 
										argbTL, argbTR, argbBR, argbBL, x, y, width, height, false,
										thickness);
									gl.glBegin(GL.GL_LINES);
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
								}
							}
							else if (op.primitive.shapeType.equals("Oval"))
							{
								gl.glLineWidth(op.primitive.strokeSize);
								gl.glEnable(GL.GL_BLEND);
								gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
								int argbTL, argbTR, argbBR, argbBL;
								if (op.primitive.gradc1 != null)
								{
									argbTL = getCompositedColor(getGradientColor(op.primitive, 0, 0), op.alphaFactor);
									argbTR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, 0), op.alphaFactor);
									argbBR = getCompositedColor(getGradientColor(op.primitive, op.primitive.shapeWidth, op.primitive.shapeHeight), op.alphaFactor);
									argbBL = getCompositedColor(getGradientColor(op.primitive, 0, op.primitive.shapeHeight), op.alphaFactor);
								}
								else
									argbTL = argbTR = argbBR = argbBL = getCompositedColor(op.primitive.color, op.alphaFactor);
								drawGLCurve(gl, op.primitive.shapeWidth/2.0f, op.primitive.shapeHeight/2.0f,
									op.primitive.shapeWidth/2.0f, op.primitive.shapeHeight/2.0f,
									(float)0, (float) (2*java.lang.Math.PI), (int)(op.primitive.shapeWidth + op.primitive.shapeHeight), 
									argbTL, argbTR, argbBR, argbBL, 0, 0, op.primitive.shapeWidth, op.primitive.shapeHeight, false,
									op.primitive.strokeSize);
							}
						}
						gl.glPopMatrix();
						if (shapeClipRect != null)
							gl.glDisable(GL.GL_SCISSOR_TEST);
					}
					else if (op.isSurfaceOp() && uiMgr.areLayersEnabled())
					{
						if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
						if (op.isSurfaceOpOn())
						{
							if (currRT != null)
							{
								rtStack.push(currRT);
							}
							int[] newSurfaceObj = (int[]) surfaceCache.get(op.surface);
							if (newSurfaceObj == null)
							{
								currRT = newSurfaceObj = new int[4];
								currRT[2] = bufferWidth;
								currRT[3] = bufferHeight;
								gl.glGenFramebuffersEXT(1, currRT, 0);
								gl.glGenTextures(1, currRT, 1);
								gl.glBindFramebufferEXT(gl.GL_FRAMEBUFFER_EXT, currRT[0]);
								gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
								gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, currRT[1]);
								gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
								gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
								gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, bufferWidth, bufferHeight, 0,
									GL.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_BYTE, null);
								gl.glFramebufferTexture2DEXT(gl.GL_FRAMEBUFFER_EXT,
									gl.GL_COLOR_ATTACHMENT0_EXT,
									gl.GL_TEXTURE_RECTANGLE_NV, currRT[1], 0);
								int status = gl.glCheckFramebufferStatusEXT(gl.GL_FRAMEBUFFER_EXT);
								gl.glBindFramebufferEXT(gl.GL_FRAMEBUFFER_EXT, 0);
								gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, 0);
								gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
								if(status==gl.GL_FRAMEBUFFER_COMPLETE_EXT)
								{
									//System.out.println("Created fb " + fbttexturet[0] + " with texture " + fbttexturet[1] +
									//	" on handle "+ imghandle);
									surfaceCache.put(op.surface, newSurfaceObj);
								}
								else
								{
									System.out.println("ERROR Cannot use frame buffer extension, status=" + status + " Disabling core animation support!");
									System.out.println("ERROR Cannot use frame buffer extension, status=" + status + " Disabling core animation support!");
									uiMgr.setCoreAnimationsEnabled(false);
									gl.glDeleteFramebuffersEXT(1, currRT, 0);
									gl.glDeleteTextures(1, currRT, 1);
									currRT = null;
									continue;
								}
							}
							else
								currRT = newSurfaceObj;
							if (ANIM_DEBUG) System.out.println("Switched rendering surface to " + op.surface + " " + newSurfaceObj);
							// Don't clear the area if this surface was already used
							setRenderTarget(currRT);
							if (clearedSurfs.add(newSurfaceObj))
							{
								if (ANIM_DEBUG) System.out.println("Clearing region on surface of " + clipRect);
								gl.glDisable(GL.GL_BLEND);
								gl.glBegin(GL.GL_QUADS);
								setGLColor(gl, 0);
								gl.glVertex2f(clipRect.x, clipRect.y);
								gl.glVertex2f(clipRect.x + clipRect.width,clipRect.y);
								gl.glVertex2f(clipRect.x + clipRect.width,clipRect.y + clipRect.height);
								gl.glVertex2f(clipRect.x,clipRect.y + clipRect.height);
								gl.glEnd();
							}
						}
						else
						{
							// Avoid double compositing operations from nested surface usage
							if (!rtStack.contains(currRT))
							{
								compositeOps.add(op);
								java.util.ArrayList remnantAnims = (java.util.ArrayList) animsThatGoAfterSurfPop.remove(currRT);
								if (remnantAnims != null)
								{
									if (ANIM_DEBUG) System.out.println("Adding animation ops into composite list now from prior nested surfs:" + remnantAnims);
									compositeOps.addAll(remnantAnims);
								}
							}
							if (rtStack.isEmpty())
								currRT = null;
							else
								currRT = (int[]) rtStack.pop();
							setRenderTarget(currRT);
						}
					}
					else if (op.isAnimationOp() && uiMgr.areLayersEnabled())
					{
						processAnimOp(op, i, clipRect);
						if ((currRT != null && currRT.equals(surfaceCache.get(op.surface))) || rtStack.contains(surfaceCache.get(op.surface)))
						{
							if (ANIM_DEBUG) System.out.println("Putting animation op in surf pop map because we're nested in the current surface");
							java.util.ArrayList vecy = (java.util.ArrayList) animsThatGoAfterSurfPop.get(currRT);
							if (vecy == null)
								animsThatGoAfterSurfPop.put(currRT, vecy = new java.util.ArrayList());
							vecy.add(compositeOps.remove(compositeOps.size() - 1));
						}
					}
				}
				if (uiMgr.areLayersEnabled())
				{
					java.util.Collections.sort(compositeOps, COMPOSITE_LIST_SORTER);

					fixSurfacePostCompositeRegions();
				}
			}
			else
			{
				if (ANIM_DEBUG) System.out.println("OPTIMIZATION Skip DL render & composite only! dlSize=" + currDisplayList.size() +
					" optSize=" + compositeOps.size());
			}
			// If rerenderedDL is true then on cleanup the devices aren't destroyed, this helps with FSEX
			// NOTE: It no longer helps since we fixed FSE support
			rerenderedDL = false;
			if (!hasVideo)
				lastVideoRect = null;

	//if (ZRoot.THREADING_DBG && Sage.DBG) System.out.println("DirectX9SageRenderer finished display list, waiting to present...lastPresentDiff=" + (Sage.time() - lastPresentTime));

			lastDL = currDisplayList;
			
			if (uiMgr.areLayersEnabled())
			{
				if (ANIM_DEBUG) System.out.println("Performing the surface compositing operations now");
				for (int i = 0; i <= compositeOps.size(); i++)
				{
					RenderingOp op = null;
					if (i == compositeOps.size())
					{
						if (waitIndicatorState && waitIndicatorRops != null)
						{
							op = (RenderingOp) waitIndicatorRops.get((++waitIndicatorRopsIndex) % waitIndicatorRops.size());
						}
						else
							break;
					}
					else
						op = (RenderingOp) compositeOps.get(i);
					if (op.isSurfaceOp())
					{
						if (ANIM_DEBUG) System.out.println("Surface Op surf=" + op.surface + " on=" + op.isSurfaceOpOn() + " " + op);
						if (op.isSurfaceOpOff())
						{
							int[] currSurface = (int[]) surfaceCache.get(op.surface);
							boolean compositeMode;
							if (/*(!hasVideo || videoSurface == 0) && */isBackgroundSurface(op.surface))
								compositeMode = false;
							else
								compositeMode = true;
							java.awt.geom.Rectangle2D.Float validRegion = new java.awt.geom.Rectangle2D.Float();
							java.awt.geom.Rectangle2D.intersect(op.destRect, clipRect, validRegion);
							textureCopy(gl, currSurface[1], validRegion, validRegion, java.awt.Color.white, op.alphaFactor, compositeMode);
							if (ANIM_DEBUG) System.out.println("Finished cached surface rendering and re-composited it with the main surface");
						}
					}
					else if (op.isImageOp())
					{
						// Process operations on the same texture that follow this one
						long texturePtr = op.texture.getNativeImage(this, op.textureIndex);
						textureCopy(gl, (int)texturePtr, op.srcRect, op.destRect, op.renderColor, op.alphaFactor, true);
						op.texture.removeNativeRef(this, op.textureIndex);
					}
					else if (op.isAnimationOp())
					{
						RenderingOp.Animation anime = op.anime;
						if (ANIM_DEBUG) System.out.println("Animation operation found! ANIMAIL ANIMAIL!!! " + op + " scrollSrcRect=" + anime.altSrcRect +
							" scrollDstRect=" + anime.altDestRect);
						// Find the cached surface first
						int[] cachedSurface = (int[]) surfaceCache.get(op.surface);
						if (cachedSurface != null)
						{
							if (ANIM_DEBUG) System.out.println("Cached animation surface found: " + op.surface);
							if (ANIM_DEBUG) System.out.println("Rendering Animation " + anime.animation);
							java.awt.geom.Rectangle2D.Float clippedSrcRect = new java.awt.geom.Rectangle2D.Float();
							clippedSrcRect.setRect(op.srcRect);
							java.awt.geom.Rectangle2D.Float clippedDstRect = new java.awt.geom.Rectangle2D.Float();
							clippedDstRect.setRect(op.destRect);
							java.awt.geom.Rectangle2D.Float clonedClipRect = new java.awt.geom.Rectangle2D.Float();
							clonedClipRect.setRect(clipRect);
							Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
							textureCopy(gl, cachedSurface[1], clippedSrcRect, clippedDstRect, java.awt.Color.white, op.alphaFactor, true);
							if (anime.isDualSurfaceOp())
							{
								// We need to render the other scrolling position
								cachedSurface = (int[]) surfaceCache.get(op.anime.altSurfName);
								if (cachedSurface != null)
								{
									if (ANIM_DEBUG) System.out.println("Rendering second scroll surface scrollSrcRect=" + anime.altSrcRect +
										" scrollDstRect=" + anime.altDestRect);
									clippedSrcRect.setRect(op.anime.altSrcRect);
									clippedDstRect.setRect(op.anime.altDestRect);
									Sage.clipSrcDestRects(clonedClipRect, clippedSrcRect, clippedDstRect);
									textureCopy(gl, cachedSurface[1], clippedSrcRect, clippedDstRect, java.awt.Color.white, op.anime.altAlphaFactor, true);
								}
							}
						}
						else
						{
							if (ANIM_DEBUG) System.out.println("ERROR: Could not find cached animation surface:" + op.surface);
						}
						if (!anime.expired)
							master.setActiveAnimation(op);
					}
				}
			}

			boolean currFullScreen = uiMgr.isFullScreen();
			if (Sage.MAC_OS_X && (lastFSMode != currFullScreen) && uiMgr.getRootPanel().isVisible()) {
				if(Sage.DBG) System.out.println("FS mode switch, rebuilding rendering pipelines");
				rebuildRenderers = true;
				uiMgr.trueValidate();
				lastFSMode = currFullScreen;
			}
			
//			boolean presentRes = present0(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
			lastPresentTime = Sage.eventTime();

		}
		catch (Throwable thr)
		{
			System.out.println("Exception in native 3D system of:" + thr);
			Sage.printStackTrace(thr);
		}
		releaseRenderThread();
		return true;
	}
	
	private void textureCopy(GL gl, int texturePtr, java.awt.geom.Rectangle2D.Float srcRect, java.awt.geom.Rectangle2D.Float destRect,
		java.awt.Color color, float alphaFactor, boolean blend)
	{
		int texturet[] = new int[] { (int) texturePtr };
		if (blend)
			gl.glEnable(GL.GL_BLEND);
		else
			gl.glDisable(GL.GL_BLEND);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV,texturet[0]);
		if (blend)
			gl.glBlendFunc(gl.GL_ONE, gl.GL_ONE_MINUS_SRC_ALPHA);
		setGLColor(gl, getShadingColor(color, alphaFactor));

		gl.glBegin(GL.GL_QUADS);
			gl.glTexCoord2f(srcRect.x,srcRect.y);
			gl.glVertex2f(destRect.x,destRect.y);
			gl.glTexCoord2f(srcRect.x+srcRect.width,srcRect.y);
			gl.glVertex2f(destRect.x+destRect.width,destRect.y);
			gl.glTexCoord2f(srcRect.x+srcRect.width,srcRect.y+srcRect.height);
			gl.glVertex2f(destRect.x+destRect.width,destRect.y+destRect.height);
			gl.glTexCoord2f(srcRect.x,srcRect.y+srcRect.height);
			gl.glVertex2f(destRect.x,destRect.y+destRect.height);
		gl.glEnd();
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
		
	}
	
	private void processVideoOp(RenderingOp op)
	{
		long myvsurf = videoSurface;
		long myvtime = videoFrameTime;
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
		float forcedRatio = (Sage.WINDOWS_OS && myvsurf != 0) ? vf.getCurrentAspectRatio(videoARx, videoARy) : vf.getCurrentAspectRatio();
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
		clipArea.setRect(0, 0, currSurfWidth, currSurfHeight);
		Sage.clipSrcDestRects(op.destRect, videoSrc, videoDest);
		srcVideoRect.setFrame(videoSrc);
		srcVideoRect.x = Math.max(0, srcVideoRect.x);
		srcVideoRect.y = Math.max(0, srcVideoRect.y);
		srcVideoRect.width = Math.min(vw - srcVideoRect.x, srcVideoRect.width);
		srcVideoRect.height = Math.min(vh - srcVideoRect.y, srcVideoRect.height);
		usedVideoRect.setFrame(videoDest);
		usedVideoRect.x = Math.max(0, usedVideoRect.x);
		usedVideoRect.y = Math.max(0, usedVideoRect.y);
		usedVideoRect.width = Math.min(currSurfWidth - usedVideoRect.x, usedVideoRect.width);
		usedVideoRect.height = Math.min(currSurfHeight - usedVideoRect.y, usedVideoRect.height);
		fullVideoRect.setFrame(op.destRect);
		fullVideoRect.x = Math.max(0, fullVideoRect.x);
		fullVideoRect.y = Math.max(0, fullVideoRect.y);
		fullVideoRect.width = Math.min(currSurfWidth - fullVideoRect.x, fullVideoRect.width);
		fullVideoRect.height = Math.min(currSurfHeight - fullVideoRect.y, fullVideoRect.height);
		
		// Clear the video rect area
		int rgbVideo = vf.getVideoBGColor().getRGB();
		rgbVideo &= 0xFFFFFF; // 0 alpha
		java.awt.Rectangle tempVideoRect = new java.awt.Rectangle();
		GL gl = pbuffer.getGL();
		gl.glDisable(GL.GL_BLEND);

		gl.glBegin(GL.GL_QUADS);
		setGLColor(gl, rgbVideo);
		gl.glVertex2f(op.destRect.x, op.destRect.y);
		gl.glVertex2f(op.destRect.x + op.destRect.width,op.destRect.y);
		gl.glVertex2f(op.destRect.x + op.destRect.width,op.destRect.y + op.destRect.height);
		gl.glVertex2f(op.destRect.x,op.destRect.y + op.destRect.height);
		gl.glEnd();

		
		// Need to clear the left edge of the video region
		if (usedVideoRect.x > fullVideoRect.x)
		{
			tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y, usedVideoRect.x - fullVideoRect.x,
				fullVideoRect.height);
			gl.glBegin(GL.GL_QUADS);
			setGLColor(gl, vf.getVideoBGColor().getRGB());
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y + tempVideoRect.height);
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y + tempVideoRect.height);
			gl.glEnd();
		}
		// Need to clear the top edge of the video region
		if (usedVideoRect.y > fullVideoRect.y)
		{
			tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y, fullVideoRect.width,
				usedVideoRect.y - fullVideoRect.y);
			gl.glBegin(GL.GL_QUADS);
			setGLColor(gl, vf.getVideoBGColor().getRGB());
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y + tempVideoRect.height);
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y + tempVideoRect.height);
			gl.glEnd();
		}
		// Need to clear the right edge of the video region
		if (usedVideoRect.x + usedVideoRect.width < fullVideoRect.x + fullVideoRect.width)
		{
			int adjust = (fullVideoRect.x + fullVideoRect.width) - (usedVideoRect.x + usedVideoRect.width);
			tempVideoRect.setFrame(fullVideoRect.x + fullVideoRect.width - adjust, fullVideoRect.y, adjust,
				fullVideoRect.height);
			gl.glBegin(GL.GL_QUADS);
			setGLColor(gl, vf.getVideoBGColor().getRGB());
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y + tempVideoRect.height);
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y + tempVideoRect.height);
			gl.glEnd();
		}
		// Need to clear the bottom edge of the video region
		if (usedVideoRect.y + usedVideoRect.height < fullVideoRect.y + fullVideoRect.height)
		{
			int adjust = (fullVideoRect.y + fullVideoRect.height) - (usedVideoRect.y + usedVideoRect.height);
			tempVideoRect.setFrame(fullVideoRect.x, fullVideoRect.y + fullVideoRect.height - adjust, fullVideoRect.width,
				adjust);
			gl.glBegin(GL.GL_QUADS);
			setGLColor(gl, vf.getVideoBGColor().getRGB());
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y);
			gl.glVertex2f(tempVideoRect.x + tempVideoRect.width, tempVideoRect.y + tempVideoRect.height);
			gl.glVertex2f(tempVideoRect.x, tempVideoRect.y + tempVideoRect.height);
			gl.glEnd();
		}

		srcVideoBounds.setFrame(srcVideoRect);
		videoBounds.setFrame(usedVideoRect);
		
		gl.glEnable(GL.GL_BLEND);
	}
	
	public static int getGradientColor(ShapeDescription sd, float x, float y)
	{
		// Calculate the projection of the point onto the vector, and then we use that distance relative to the
		// length of the vector to determine what proportionality of each color to use.
		float frac2 = Math.abs((x-sd.fx1)*(sd.fx2-sd.fx1) + (y-sd.fy1)*(sd.fy2-sd.fy1)) /
			((sd.fx2-sd.fx1)*(sd.fx2-sd.fx1) + (sd.fy2-sd.fy1)*(sd.fy2-sd.fy1));
		if (frac2 > 1.0f || frac2 < 0) // don't convert 1.0 to 0
			frac2 = frac2 % 1.0f;
		float frac1 = 1.0f - frac2;
		return 0xFF000000 | ((int)(sd.gradc1.getRed()*frac1 + sd.gradc2.getRed()*frac2) << 16) |
			((int)(sd.gradc1.getGreen()*frac1 + sd.gradc2.getGreen()*frac2) << 8) |
			((int)(sd.gradc1.getBlue()*frac1 + sd.gradc2.getBlue()*frac2));
	}
	private Runnable displayAction = new Runnable()
	{
		public void run()
		{
			display();
		}
	};
	private Runnable initAction = new Runnable()
	{
		public void run()
		{
			init();
		}
	};
	private Runnable rethreadAction = new Runnable()
	{
		public void run()
		{
			GL gl = pbuffer.getGL();
			gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, osdt[0]);
			gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE_NV, 0, currClipRect.x, currClipRect.y, currClipRect.x, currClipRect.y, currClipRect.width, currClipRect.height);
			gl.glFlush();
			gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
			pbuffer.getContext().release();

			drawableHelper.invokeGL(drawable, context, displayAction, initAction);
			inframe = false;
		}
	};
	private Runnable rethreadDisplayOnlyAction = new Runnable()
	{
		public void run()
		{
			drawableHelper.invokeGL(drawable, context, displayAction, initAction);
		}
	};
	private java.awt.Rectangle currClipRect;
	public void present(java.awt.Rectangle clipRect)
	{
		currClipRect = clipRect;
		if (Threading.isSingleThreaded() &&
			!Threading.isOpenGLThread()) 
		{
		  Threading.invokeOnOpenGLThread(rethreadAction);
		} else {
			GL gl = pbuffer.getGL();
			gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, osdt[0]);
			gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE_NV, 0, currClipRect.x, currClipRect.y, currClipRect.x, currClipRect.y, currClipRect.width, currClipRect.height);
			gl.glFlush();
			gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
			pbuffer.getContext().release();

			drawableHelper.invokeGL(drawable, context, displayAction, initAction);
			inframe = false;
		}
		markAnimationStartTimes();
		master.getToolkit().sync();
	}
	
	private void freeAllFonts()
	{
		java.util.Iterator walker = fontCacheMap.values().iterator();
		while (walker.hasNext())
		{
			((com.sun.opengl.util.j2d.TextRenderer) walker.next()).dispose();
		}
		fontCacheMap.clear();
	}
	
	public void cleanupRenderer()
	{
		if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
		{
			javax.media.opengl.Threading.invokeOnOpenGLThread(new Runnable()
			{
				public void run()
				{
					cleanupRendererSync();
				}
			});
		}
		else
		{
			cleanupRendererSync();
		}
	}
	private void cleanupRendererSync()
	{
		drawable.setRealized(false);
		// Do NOT sync before we call prepareForReload or we can deadlock because that has to wait on the AWT thread which
		// can link back around to the active thread due to events coming in.
		vf.prepareForReload();
		synchronized (this)
		{
			MetaImage.clearNativeCache(this);
			freeAllFonts();
			
			// Clear out the alternate rendering surfaces
			java.util.Iterator walker = surfaceCache.values().iterator();
			GL gl = pbuffer.getGL();
			while (walker.hasNext())
			{
				int[] texturet = (int[]) walker.next();
				gl.glDeleteFramebuffersEXT(1, texturet, 0);
				gl.glDeleteTextures(1, texturet, 1);
			}
			surfaceCache.clear();

			videorenderer.deinitVideoServer();
			super.cleanupRenderer();
			stopVmr9Callback = false;
		}
	}
	
	public boolean createNativeImage(MetaImage image, int imageIndex)
	{
		if (!checkedMaxTextureSize)
		{
			int testDim = 1024;
			while (true)
			{
				final int currTestDim = testDim;
				final int[] width = new int[1];
				final int[] height = new int[1];
				if (Threading.isSingleThreaded() &&
					!Threading.isOpenGLThread()) 
				{
				  Threading.invokeOnOpenGLThread(new Runnable()
				  {
					  public void run()
					  {
							GL gl = pbuffer.getGL();
							int[] testt = new int[1];
							gl.glGenTextures(1, testt, 0);
							gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
							gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, testt[0]);
							gl.glTexImage2D(GL.GL_PROXY_TEXTURE_2D, 0, 4,  currTestDim, currTestDim, 0, GL.GL_BGRA, 
								 bigendian ? GL.GL_UNSIGNED_INT_8_8_8_8_REV : GL.GL_UNSIGNED_BYTE, null);  
							gl.glGetTexLevelParameteriv(GL.GL_PROXY_TEXTURE_2D, 0, GL.GL_TEXTURE_WIDTH, width, 0); 
							gl.glGetTexLevelParameteriv(GL.GL_PROXY_TEXTURE_2D, 0, GL.GL_TEXTURE_HEIGHT, height, 0);
							gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
							gl.glDeleteTextures(1, testt, 0);
					  }
				  });
				} else {
					GL gl = pbuffer.getGL();
					int[] testt = new int[1];
					gl.glGenTextures(1, testt, 0);
					gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
					gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, testt[0]);
					gl.glTexImage2D(GL.GL_PROXY_TEXTURE_2D, 0, 4,  currTestDim, currTestDim, 0, GL.GL_BGRA, 
						 bigendian ? GL.GL_UNSIGNED_INT_8_8_8_8_REV : GL.GL_UNSIGNED_BYTE, null);  
					gl.glGetTexLevelParameteriv(GL.GL_PROXY_TEXTURE_2D, 0, GL.GL_TEXTURE_WIDTH, width, 0); 
					gl.glGetTexLevelParameteriv(GL.GL_PROXY_TEXTURE_2D, 0, GL.GL_TEXTURE_HEIGHT, height, 0);
					gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
					gl.glDeleteTextures(1, testt, 0);
				}
				if (width[0] == 0 || height[0] == 0)
				{
					if (testDim > 1024)
						testDim = testDim/2;
					break;
				}
				else if (testDim >= 2048)
					break;
				else
					testDim *= 2;
			}
			maxTextureDim = testDim;
			if (Sage.DBG) System.out.println("Set JOGL max texture dimension to be " + testDim);
			checkedMaxTextureSize = true;
			// We can't actually check the max vram in JOGL so just base it off the texture size
			if (testDim > 1024)
				videoMemoryLimit = 96000000;
			else
				videoMemoryLimit = 32000000;
		}
		sage.media.image.RawImage tightImage = null;
		long nativePtr = 0;
		final int filterMode = (image.getSource() instanceof MetaFont) ? GL.GL_NEAREST : GL.GL_LINEAR;
		if (!Sage.getBoolean("ui/disable_native_image_loader", false))
		{
			try
			{
				pauseIfNotRenderingThread();
				tightImage = image.getRawImage(imageIndex);
				final int width = image.getWidth(imageIndex);
				final int height = image.getHeight(imageIndex);
				int pow2W, pow2H;
				pow2W = pow2H = 1;
				while (pow2W < width)
					pow2W = pow2W << 1;
				while (pow2H < height)
					pow2H = pow2H << 1;
				int nativeMemUse = pow2W*pow2H*4;

				synchronized (MetaImage.getNiaCacheLock(this))
				{
					while (MetaImage.getNativeImageCacheSize(this) + nativeMemUse > videoMemoryLimit)
					{
						Object[] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
						if (oldestImage == null)
						{
							if (Sage.DBG) System.out.println("JOGL is unable to find an image to release to add " + image.getSource() +
								" cacheSize=" + MetaImage.getNativeImageCacheSize(this));
							// Don't return false, let JOGL try to do some memory management to clean
							// this up and give us some back.
							break;
						}
						if (Sage.DBG) System.out.println("JOGL is freeing texture memory to make room size=" + MetaImage.getNativeImageCacheSize(this) +
							" src=" + ((MetaImage) oldestImage[0]).getSource());
						((MetaImage) oldestImage[0]).clearNativePointer(this, ((Integer) oldestImage[1]).intValue());
					}
					MetaImage.reserveNativeCache(this, nativeMemUse);
				}

				final int texturet[] = new int[1];
				final sage.media.image.RawImage finalTight = tightImage;
				pauseIfNotRenderingThread();
				if (Threading.isSingleThreaded() &&
					!Threading.isOpenGLThread()) 
				{
				  Threading.invokeOnOpenGLThread(new Runnable()
				  {
					  public void run()
					  {
							GL gl = pbuffer.getGL();
							gl.glGenTextures(1, texturet, 0);
							gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
							gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, texturet[0]);
							gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, filterMode);
							gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, filterMode);
							gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, width, height, 0,
								GL.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_INT_8_8_8_8, finalTight.getROData());
							gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
					  }
				  });
				} else {
					GL gl = pbuffer.getGL();
					gl.glGenTextures(1, texturet, 0);
					gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
					gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, texturet[0]);
					gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, filterMode);
					gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, filterMode);
					gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, width, height, 0,
						GL.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : gl.GL_UNSIGNED_INT_8_8_8_8, tightImage.getROData());
					gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
				}
				nativePtr = texturet[0];
				MetaImage.returnNativeCacheReserve(this, nativeMemUse);
				if (nativePtr != 0)
				{
					image.setNativePointer(this, imageIndex, nativePtr, nativeMemUse);
	//				if (D3D_DEBUG) System.out.println("VRAM State free=" + getAvailableVideoMemory0() +
	//					" cache=" + MetaImage.getNativeImageCacheSize(this));
				}
			}
			finally
			{
				image.removeRawRef(imageIndex);
			}
		}
		else
		{
			// if we don't have this as a buffered image, we need to convert it
			java.awt.image.BufferedImage tempBuf;
			pauseIfNotRenderingThread();
			java.awt.Image javaImage = image.getJavaImage(imageIndex);
			try
			{
				// NOTE: On 8/11/2004 I made it so it only takes ARGB again. Otherwise my video snapshots wouldn't appear.
				if (!(javaImage instanceof java.awt.image.BufferedImage) ||
					(((java.awt.image.BufferedImage) javaImage).getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB &&
					 ((java.awt.image.BufferedImage) javaImage).getType() != java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE))
				{
					if (!(javaImage instanceof java.awt.image.BufferedImage))
						ImageUtils.ensureImageIsLoaded(javaImage);
					tempBuf = ImageUtils.createBestImage(javaImage);
				}
				else
				{
					tempBuf = (java.awt.image.BufferedImage)javaImage;
				}
				if (!tempBuf.isAlphaPremultiplied())
				{
					pauseIfNotRenderingThread();
					if (Sage.DBG) System.out.println("Premultiplying alpha for BuffImage...");
					tempBuf.coerceData(true);
				}
				final int width = tempBuf.getWidth();
				final int height = tempBuf.getHeight();
				int pow2W, pow2H;
				pow2W = pow2H = 1;
				while (pow2W < width)
					pow2W = pow2W << 1;
				while (pow2H < height)
					pow2H = pow2H << 1;
				int nativeMemUse = pow2W*pow2H*4;

				synchronized (MetaImage.getNiaCacheLock(this))
				{
					while (MetaImage.getNativeImageCacheSize(this) + nativeMemUse > videoMemoryLimit)
					{
						Object[] oldestImage = MetaImage.getLeastRecentlyUsedImage(this, image, imageIndex);
						if (oldestImage == null)
						{
							if (Sage.DBG) System.out.println("JOGL is unable to find an image to release to add " + image.getSource() +
								" cacheSize=" + MetaImage.getNativeImageCacheSize(this));
							// Don't return false, let JOGL try to do some memory management to clean
							// this up and give us some back.
							break;
						}
						if (Sage.DBG) System.out.println("JOGL is freeing texture memory to make room size=" + MetaImage.getNativeImageCacheSize(this) +
							" src=" + ((MetaImage) oldestImage[0]).getSource());
						((MetaImage) oldestImage[0]).clearNativePointer(this, ((Integer) oldestImage[1]).intValue());
					}
					MetaImage.reserveNativeCache(this, nativeMemUse);
				}

				final java.nio.ByteBuffer bb = getBufferFromBI(tempBuf);
				final int texturet[] = new int[1];
				pauseIfNotRenderingThread();
				if (Threading.isSingleThreaded() &&
					!Threading.isOpenGLThread()) 
				{
				  Threading.invokeOnOpenGLThread(new Runnable()
				  {
					  public void run()
					  {
						GL gl = pbuffer.getGL();
						gl.glGenTextures(1, texturet, 0);
						gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
						gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, texturet[0]);
						gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, filterMode);
						gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, filterMode);
						gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, width, height, 0,
							/*bi.getColorModel().hasAlpha() ?*/ GL.GL_RGBA /*: GL.GL_RGB*/, 
							gl.GL_UNSIGNED_BYTE, bb);
						gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
					  }
				  });
				} else {
					GL gl = pbuffer.getGL();
					gl.glGenTextures(1, texturet, 0);
					gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
					gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, texturet[0]);
					gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, filterMode);
					gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, filterMode);
					gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, tempBuf.getWidth(), tempBuf.getHeight(), 0,
						/*bi.getColorModel().hasAlpha() ?*/ GL.GL_RGBA /*: GL.GL_RGB*/, 
						gl.GL_UNSIGNED_BYTE, bb);
					gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
				}
				nativePtr = texturet[0];
				
				MetaImage.returnNativeCacheReserve(this, nativeMemUse);
				if (nativePtr != 0)
				{
					image.setNativePointer(this, imageIndex, nativePtr, nativeMemUse);
//					if (D3D_DEBUG) System.out.println("VRAM State free=" + getAvailableVideoMemory0() +
//						" cache=" + MetaImage.getNativeImageCacheSize(this));
				}
			}
			finally
			{
				image.removeJavaRef(imageIndex);
			}
		}
		return (nativePtr != 0);
	}
	
	public void releaseNativeImage(long nativePointer)
	{
		final int texturet[] = new int[] { (int)nativePointer };
		// this doesn't ever seem to be called on a different thread, but I'm leaving it in here just to be safe...
		if (Threading.isSingleThreaded() &&
			!Threading.isOpenGLThread()) 
		{
		  Threading.invokeOnOpenGLThread(
			new Runnable() {
				public void run()
				{
				  if(!inframe) pbuffer.getContext().makeCurrent();
				  pbuffer.getGL().glDeleteTextures(1, texturet, 0);
				  if(!inframe) pbuffer.getContext().release();
		    	}
		    }
		  );
		} else {
			if(!inframe) pbuffer.getContext().makeCurrent();
			pbuffer.getGL().glDeleteTextures(1, texturet, 0);
			if(!inframe) pbuffer.getContext().release();
		}
		
	}

	public void preloadImage(final MetaImage mi)
	{
		if (javax.media.opengl.Threading.isSingleThreaded() && 
			!javax.media.opengl.Threading.isOpenGLThread())
		{
			// NOTE: We have to rethread this now for JOGL or it can deadlock against the FinalRender
			// thread which has to run on the AWT thread as well
			javax.media.opengl.Threading.invokeOnOpenGLThread(new Runnable()
			{
				public void run()
				{
					if (mi.getWidth(0) > maxTextureDim ||
						mi.getHeight(0) > maxTextureDim)
					{
						float xScale = Math.min(maxTextureDim,
							mi.getWidth(0))/((float)mi.getWidth(0));
						float yScale = Math.min(maxTextureDim,
							mi.getHeight(0))/((float)mi.getHeight(0));
						xScale = yScale = Math.min(xScale, yScale);
						int newIdex = mi.getImageIndex(Math.round(xScale *
							mi.getWidth(0)), Math.round(yScale * mi.getHeight(0)));
						mi.getNativeImage(JOGLSageRenderer.this, newIdex);
						mi.removeNativeRef(JOGLSageRenderer.this, newIdex);
					}
					else
					{
						mi.getNativeImage(JOGLSageRenderer.this, 0);
						mi.removeNativeRef(JOGLSageRenderer.this, 0);
					}
				}
			});
		}
		else
		{
			if (mi.getWidth(0) > maxTextureDim ||
				mi.getHeight(0) > maxTextureDim)
			{
				float xScale = Math.min(maxTextureDim,
					mi.getWidth(0))/((float)mi.getWidth(0));
				float yScale = Math.min(maxTextureDim,
					mi.getHeight(0))/((float)mi.getHeight(0));
				xScale = yScale = Math.min(xScale, yScale);
				int newIdex = mi.getImageIndex(Math.round(xScale *
					mi.getWidth(0)), Math.round(yScale * mi.getHeight(0)));
				mi.getNativeImage(this, newIdex);
				mi.removeNativeRef(this, newIdex);
			}
			else
			{
				mi.getNativeImage(this, 0);
				mi.removeNativeRef(this, 0);
			}
		}
	}
	
	public boolean supportsPartialUIUpdates()
	{
		return false;
	}
	
	public void init()
	{
		GL gl = context.getGL();
		gl.setSwapInterval(1);
	}
	
	private boolean surfaceOpaque = true;

	public void setOpaque(boolean val)
	{
		// someday this could actually do something...
		surfaceOpaque = val;
	}
	
	public void display()
	{
		GL gl = context.getGL();
    
		gl.glViewport(0, 0, master.getWidth(), master.getHeight());
		gl.glMatrixMode(gl.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0,master.getWidth(), master.getHeight(),0,-1.0,1.0);
		gl.glMatrixMode(gl.GL_MODELVIEW);
		gl.glLoadIdentity();

		/*if(osdt==null)*/ gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);
		gl.glClear(gl.GL_COLOR_BUFFER_BIT);
		
		videorenderer.drawVideo(gl, srcVideoBounds, videoBounds);
		if(osdt!=null)
		{
			gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
			gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV,osdt[0]);
			gl.glEnable(gl.GL_BLEND);
			gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
			gl.glColor4f(1,1,1,1);
			gl.glBegin(gl.GL_QUADS);
				gl.glTexCoord2f(0, 0);
				gl.glVertex3f(0, 0, 1.0f);
            
				gl.glTexCoord2f(bufferWidth, 0);
				gl.glVertex3f(bufferWidth, 0, 1.0f);
            
				gl.glTexCoord2f(bufferWidth, bufferHeight);
				gl.glVertex3f(bufferWidth, bufferHeight, 1.0f);
            
				gl.glTexCoord2f(0, bufferHeight);
				gl.glVertex3f(0.0f, bufferHeight, 1.0f);
			gl.glEnd();
			gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
		}
	}

	public void reshape(GLAutoDrawable gLAutoDrawable, int i, int i0, int i1, int i2)
	{
		// I don't think we need to do anything here
	}

	public void displayChanged(GLAutoDrawable gLAutoDrawable, boolean b, boolean b0)
	{
	}

	private void initpbuffer(int width, int height)
	{
		GL gl;
//		System.out.println("initpbuffer");
		GLCapabilities caps = new GLCapabilities();
		caps.setHardwareAccelerated(true);
		caps.setDoubleBuffered(false);
		caps.setAlphaBits(8);
		caps.setRedBits(8);
		caps.setGreenBits(8);
		caps.setBlueBits(8);
		caps.setDepthBits(0);
		if (!GLDrawableFactory.getFactory().canCreateGLPbuffer()) 
		{
			throw new GLException("pbuffers unsupported");
		}
        if(pbuffer!=null) pbuffer.destroy();
		pbuffer = GLDrawableFactory.getFactory().createGLPbuffer(
						caps,
						null,
						width,
						height,
						context); // Do we really need to specify which to share with
		if(pbuffer.getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT)
		{
			System.out.println("Couldn't make pbuffer current?");
			return;
		}
		gl = pbuffer.getGL();
    
		gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);
            
		gl.glClear( gl.GL_COLOR_BUFFER_BIT);

		gl.glViewport(0, 0, width, height);
		gl.glMatrixMode(gl.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0,width,0,height,-1.0,1.0);
		gl.glMatrixMode(gl.GL_MODELVIEW);
		gl.glLoadIdentity();

        // TODO: look into reusing same texture like OSX version...
		if(osdt!=null) gl.glDeleteTextures(1, osdt, 0);
		osdt = new int[1];
		byte img[] = new byte[width*height*4];
		gl.glGenTextures(1, osdt, 0);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV,osdt[0]);
		gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
		gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE_NV, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
		gl.glTexImage2D(GL.GL_TEXTURE_RECTANGLE_NV, 0, 4, width, height, 0,
                        GL.GL_BGRA, bigendian ? gl.GL_UNSIGNED_INT_8_8_8_8_REV : GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
        
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE_NV);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE_NV, osdt[0]);
		gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE_NV, 0, 0, 0, 0, 0, width, height);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE_NV);
		pbuffer.getContext().release();
	}

	private void setGLColor(GL gl, int color)
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

	private void setInterpolatedGLColor(GL gl, 
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

	private void drawGLCurve(GL gl, float x, float y, float width, float height,
		float angs, float ange, int subdiv, int tlc, int trc, int brc, int blc, 
		int xc, int yc, float widthc, float heightc, boolean full,
		int thickness)
	{
		if(full)
		{
			gl.glBegin(GL.GL_TRIANGLE_FAN);
			setInterpolatedGLColor(gl, tlc, trc, brc, blc, 
				x-xc, y-yc, widthc, heightc);
			gl.glVertex2f(x, y);
		}
		else
		{
			gl.glLineWidth(thickness);
			gl.glBegin(GL.GL_LINE_STRIP);
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

	public String getServerVideoOutParams()
	{
		String params;
		params = videorenderer.getServerVideoOutParams();
//		System.out.println("video out params: " + params);
		return params;
	}

	public boolean isInFrame()
	{
		return inframe;
	}
	public GLPbuffer getPbuffer()
	{
		return pbuffer;
	}
	public int getCanvasHeight()
	{
		return master.getHeight();
	}
	public void videoWasUpdated()
	{
		if (Threading.isSingleThreaded() &&
			!Threading.isOpenGLThread()) 
		{
		  Threading.invokeOnOpenGLThread(rethreadDisplayOnlyAction);
		} else {
			drawableHelper.invokeGL(drawable, context, displayAction, initAction);
		}
//		master.getToolkit().sync();
	}
	
	private int bufferWidth;
	private int bufferHeight;
	
	private long videoSurface;
	private int videoWidth;
	private int videoHeight;
	private long videoFrameTime;
	private int videoARx;
	private int videoARy;
	
	private PseudoMenu lastUI;
	
	// DEBUGGING
	private boolean rerenderedDL;
	private java.util.ArrayList lastDL;
	
	private java.awt.Dimension lastMasterSize;
	
	private float[] srcXCache;
	private float[] srcYCache;
	private float[] srcWCache;
	private float[] srcHCache;
	private float[] dstXCache;
	private float[] dstYCache;
	private float[] dstWCache;
	private float[] dstHCache;
	private int[] cCache;
	
	private VideoFrame vf;
	private java.awt.geom.Rectangle2D.Float lastVideoRect;
	
	private java.util.Map fontCacheMap = new java.util.HashMap();
	
	private boolean stopVmr9Callback;
	
	private long videoMemoryLimit;
	
	// Cache these so we don't reallocate them every video frame
	private java.awt.geom.Rectangle2D.Float videoSrc = new java.awt.geom.Rectangle2D.Float();
	private java.awt.geom.Rectangle2D.Float videoDest = new java.awt.geom.Rectangle2D.Float();
	private java.awt.geom.Rectangle2D.Float clipArea = new java.awt.geom.Rectangle2D.Float();
	private java.awt.Rectangle srcVideoRect = new java.awt.Rectangle();
	private java.awt.Rectangle usedVideoRect = new java.awt.Rectangle();
	private java.awt.Rectangle fullVideoRect = new java.awt.Rectangle();
	private float[] currMatCoords = new float[16];

	private GLDrawableHelper drawableHelper = new GLDrawableHelper();
	private GLDrawable drawable;
	private GLContextImpl context;
	private boolean autoSwapBufferMode = true;
	private boolean sendReshape = false;

	public GLPbuffer pbuffer; // Used for rendering the UI
	private int osdt[]; // texture of the osd used when drawing the window
	private boolean bigendian;
	private java.awt.image.ColorModel glAlphaColorModel;
	private boolean realized = false;
	private boolean inframe = false;
	private sage.miniclient.OpenGLVideoRenderer videorenderer;
	private java.awt.Rectangle srcVideoBounds = new java.awt.Rectangle();
	private java.awt.Rectangle videoBounds = new java.awt.Rectangle();
	
	private boolean checkedMaxTextureSize = false;
		// for handling switching in/out of fullscreen mode
	private boolean lastFSMode = false;
	private boolean rebuildRenderers = false;
	
	private int maxTextureDim = 1024;
	
	private int[] currRT;
	private int currSurfWidth;
	private int currSurfHeight;
	private java.util.Stack rtStack = new java.util.Stack();
	private java.util.Map animsThatGoAfterSurfPop = new java.util.HashMap();
}
