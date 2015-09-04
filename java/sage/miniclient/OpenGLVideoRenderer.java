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

import javax.media.opengl.*;
import com.jogamp.opengl.util.*;

public class OpenGLVideoRenderer
{
  private sage.miniclient.JOGLVideoUI owner;
  private boolean pureNativeMode=false; // defer drawing to underlying native code
  private int videowidth, videoheight;
  private int videoy, videoypitch, videou, videoupitch, videov, videovpitch;
  private java.nio.ByteBuffer videobuffer;
  private boolean activevideo=false;
  private int videot[]; // textures of the video
  private int videotemp[]; // temporary texture for nvidia combiner
  private int fragProg=0;
  private int videoMode=0; // 0=Unknown 1=fragment program  2=ATI 8500+  3=NVCombiner 4=Y

  // Native calls are only used on OSX/Windows (linux uses jtux)
  public native int initVideoServer();
  public native void deinitVideoServer();
  public native String getServerVideoOutParams();
  public native boolean isGLSLHardware();

  // native mode methods, called with the correct GL context active and from within the GL thread
  public native void createVideo0(int width, int height); // allocate GL resources that need to exist in our GL context
  public native void closeVideo0();
  public native void updateVideo0();	// update internal image data if they need to be done in our GL context
  public native void drawVideo0(java.awt.Rectangle srcVideoRect, java.awt.Rectangle currVideoBounds);

  public OpenGLVideoRenderer(sage.miniclient.JOGLVideoUI owner)
  {
    this.owner = owner;
    try
    {
      if(MiniClient.MAC_OS_X)
        initVideoServer();
      else if (MiniClient.WINDOWS_OS)
      {
        System.loadLibrary("SageTVWin32");
        // For Windows we create the thread in Java
        Thread t = new Thread("OpenGLVideo")
        {
          public void run()
          {
            initVideoServer();
          }
        };
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY); // video is highest priority
        t.start();
        System.out.println("Started Win32 opengl video thread");
      }
    } catch (Throwable t)
    {
      System.out.println("Error creating OpenGL Video of:" + t);
    }
  }

  private void createVideo()
  {
    //System.out.println("createvideo");
    if(owner.isInFrame() || (owner.getPbuffer().getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT))
    {
      if(!owner.isInFrame())
      {
        System.out.println("Couldn't make pbuffer current?");
        return;
      }
      else
      {
        System.out.println("Create video while already in frame");
      }
    }
    GL2 gl = owner.getPbuffer().getGL().getGL2();
    System.out.println("createvideo width: "+videowidth+" height: "+videoheight);
    if(videot!=null) gl.glDeleteTextures(3, videot, 0);
    //		try {
    //			closeVideo0();
    //		} catch(Throwable t) {}

    if(pureNativeMode) {
      videot = null;
      videoMode = 0; // release resources and force reallocation when we leave native mode
      try {
        createVideo0(videowidth, videoheight);
      } catch(Throwable t) {}
    } else {
      byte img[] = new byte[videowidth*videoheight*2];
      videot = new int[3];
      gl.glGenTextures(3, videot, 0);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[0]);
      if(MiniClient.MAC_OS_X) {
        gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, 0x85BC/*GL_TEXTURE_STORAGE_HINT_APPLE*/, 0x85Be/*GL_STORAGE_CACHED_APPLE*/);
        gl.glPixelStorei(0x85B2/*GL_UNPACK_CLIENT_STORAGE_APPLE*/, gl.GL_TRUE);
      }
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
      gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 1, videowidth, videoheight, 0,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[1]);
      if(MiniClient.MAC_OS_X) {
        gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, 0x85BC/*GL_TEXTURE_STORAGE_HINT_APPLE*/, 0x85Be/*GL_STORAGE_CACHED_APPLE*/);
        gl.glPixelStorei(0x85B2/*GL_UNPACK_CLIENT_STORAGE_APPLE*/, gl.GL_TRUE);
      }
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
      gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 1, videowidth/2, videoheight/2, 0,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[2]);
      if(MiniClient.MAC_OS_X) {
        gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, 0x85BC/*GL_TEXTURE_STORAGE_HINT_APPLE*/, 0x85Be/*GL_STORAGE_CACHED_APPLE*/);
        gl.glPixelStorei(0x85B2/*GL_UNPACK_CLIENT_STORAGE_APPLE*/, gl.GL_TRUE);
      }
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
      gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
      gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 1, videowidth/2, videoheight/2, 0,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(img));
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    }
    if(!owner.isInFrame()) owner.getPbuffer().getContext().release();
  }

  private void updateVideo(int frametype)
  {
    if(owner.isInFrame() || (owner.getPbuffer().getContext().makeCurrent()==GLContext.CONTEXT_NOT_CURRENT))
    {
      if(!owner.isInFrame())
      {
        System.out.println("Couldn't make pbuffer current?");
        return;
      }
      else
      {
        System.out.println("update video while already in frame");
      }
    }
    GL2 gl = owner.getPbuffer().getGL().getGL2();

    if(pureNativeMode) {
      try {
        updateVideo0();
      } catch(Throwable t) {}
    } else {
      //System.out.println("20,20 pixel: "+videobuffer.get(20*720+20));
      //System.out.println(" width: "+videowidth+" height: "+videoheight);
      gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[0]);
      gl.glPixelStorei(gl.GL_UNPACK_SKIP_PIXELS, videoy);
      gl.glTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, videowidth, videoheight,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, videobuffer);

      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[1]);
      gl.glPixelStorei(gl.GL_UNPACK_SKIP_PIXELS, videou);
      gl.glTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, videowidth/2, videoheight/2,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, videobuffer);

      gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[2]);
      gl.glPixelStorei(gl.GL_UNPACK_SKIP_PIXELS, videov);
      gl.glTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, videowidth/2, videoheight/2,
          gl.GL_LUMINANCE, gl.GL_UNSIGNED_BYTE, videobuffer);
      gl.glPixelStorei(gl.GL_UNPACK_SKIP_PIXELS, 0);
      gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
      gl.glFlush();
    }
    if(!owner.isInFrame()) owner.getPbuffer().getContext().release();
  }

  public void initFragmentProgram(GL2 gl)
  {
    int[] fragProgTmp = new int[1];
    gl.glGenProgramsARB(1, fragProgTmp, 0);
    fragProg = fragProgTmp[0];
    //	  R = Y + 1.371 (Cr - 128)
    //	  G = Y - 0.698 (Cr - 128) - 0.336 (Cb - 128)
    //	  B = Y + 1.732 (Cb - 128)
    String combineFragProg =
        "!!ARBfp1.0\n" +
            "TEMP texSamp0, texSamp1, texSamp2;\n" +
            "PARAM half = { 0.5, 0.5, 0.5, 0.5 };\n" +
            "PARAM mulY = { 1.0, 1.0, 1.0, 0.0 };\n" +
            "PARAM mulCr = { 1.371, -0.698, 0.0, 0.0 };\n" +
            "PARAM mulCb = { 0.0, -0.336, 1.732, 0.0 };\n" +
            "TEX texSamp0, fragment.texcoord[0], texture[0], RECT;\n" +
            "TEX texSamp1, fragment.texcoord[1], texture[1], RECT;\n" +
            "TEX texSamp2, fragment.texcoord[2], texture[2], RECT;\n" +
            "SUB texSamp1, texSamp1, half;\n" +
            "SUB texSamp2, texSamp2, half;\n" +
            "MUL texSamp0, texSamp0, mulY;\n" +
            "MAD texSamp0, texSamp1, mulCr, texSamp0;\n" +
            "MAD texSamp0, texSamp2, mulCb, texSamp0;\n" +
            "MOV result.color, texSamp0;\n" +
            "END";

    gl.glBindProgramARB  (gl.GL_FRAGMENT_PROGRAM_ARB, fragProg);
    gl.glProgramStringARB(gl.GL_FRAGMENT_PROGRAM_ARB, gl.GL_PROGRAM_FORMAT_ASCII_ARB,
        combineFragProg.length(), combineFragProg);
    int[] errPos = new int[1];
    gl.glGetIntegerv(gl.GL_PROGRAM_ERROR_POSITION_ARB, errPos, 0);
    if (errPos[0] >= 0)
    {
      System.out.println("Fragment program failed to load:");
      String errMsg = gl.glGetString(gl.GL_PROGRAM_ERROR_STRING_ARB);
      if (errMsg == null)
      {
        System.out.println("[No error message available]");
      }
      else
      {
        System.out.println("Error message: \"" + errMsg + "\"");
      }
      System.out.println("Error occurred at position " + errPos[0] + " in program:");
      int endPos = errPos[0];
      while (endPos < combineFragProg.length() && combineFragProg.charAt(endPos) != '\n')
      {
        ++endPos;
      }
      System.out.println(combineFragProg.substring(errPos[0], endPos));
    }
  }


  /*public void initATIFragmentProgram(GL2 gl)
	{
		int[] fragProgTmp = new int[1];
		gl.glGenProgramsARB(1, fragProgTmp, 0);
		fragProg = fragProgTmp[0];
//	  R = Y + 1.371 (Cr - 128)
//	  G = Y - 0.698 (Cr - 128) - 0.336 (Cb - 128)
//	  B = Y + 1.732 (Cb - 128)
		String combineFragProg =
			"!!ATIfs1.0\n" +
			"StartConstants;\n" +
			"CONSTANT c0 = { 1.0, 1.0, 1.0, 0.0 };\n" +
			"CONSTANT c1 = { 0.84275, 0.3255, 0.5, 0.5 };\n" +
			"CONSTANT c2 = { 0.5, 0.416, 0.933, 0.5 };\n" +
			"EndConstants;\n" +
			"StartOutputPass;\n" +
			"SampleMap r0, t0.stq_dq; #sample the texture\n" +
			"SampleMap r1, t1.stq_dq; #sample the texture\n" +
			"SampleMap r2, t2.stq_dq; #sample the texture\n" +
			"MAD r0, r1.2x.bias, c1.2x.bias, r0;\n" +
			"MAD r0, r2.2x.bias, c2.2x.bias, r0;\n" +
			"EndPass;\n";

		gl.glBindProgramARB  (gl.GL_TEXT_FRAGMENT_SHADER_ATI, fragProg);
		gl.glProgramStringARB(gl.GL_TEXT_FRAGMENT_SHADER_ATI, gl.GL_PROGRAM_FORMAT_ASCII_ARB,
				combineFragProg.length(), combineFragProg);
		int[] errPos = new int[1];
		gl.glGetIntegerv(gl.GL_PROGRAM_ERROR_POSITION_ARB, errPos, 0);
		if (errPos[0] >= 0)
		{
			System.out.println("Fragment program failed to load:");
			String errMsg = gl.glGetString(gl.GL_PROGRAM_ERROR_STRING_ARB);
			if (errMsg == null)
			{
				System.out.println("[No error message available]");
			}
			else
			{
			System.out.println("Error message: \"" + errMsg + "\"");
			}
			System.out.println("Error occurred at position " + errPos[0] + " in program:");
			int endPos = errPos[0];
			while (endPos < combineFragProg.length() && combineFragProg.charAt(endPos) != '\n')
			{
				++endPos;
			}
			System.out.println(combineFragProg.substring(errPos[0], endPos));
		}
	}*/

  /*public void initNVCombiner(GL2 gl)
	{
		videotemp = new int[1];
		gl.glGenTextures(1, videotemp, 0);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videotemp[0]);
		if(MiniClient.MAC_OS_X) {
			gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, 0x85BC/*GL_TEXTURE_STORAGE_HINT_APPLE*//*, 0x85BE*//*GL_STORAGE_CACHED_APPLE*//*);*/
  //gl.glPixelStorei(0x85B2/*GL_UNPACK_CLIENT_STORAGE_APPLE*/, gl.GL_TRUE);
  /*}
		gl.glTexImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 4, videowidth, videoheight, 0,
			gl.GL_BGRA, gl.GL_UNSIGNED_BYTE, null);
		gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
		gl.glTexParameteri(gl.GL_TEXTURE_RECTANGLE, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
		gl.glTexEnvi(gl.GL_TEXTURE_ENV, gl.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
	}*/

  // Shaders from MAC version
  // TODO: verify math on color transforms and divide into video and pc levels...
  // can be used for ATI text fragment shader
  private static String arbVertexProgram =
      "!!ARBvp1.0\n" +
          "ATTRIB vertexPosition  = vertex.position;\n" +
          "OUTPUT outputPosition  = result.position;\n" +
          "DP4	outputPosition.x, state.matrix.mvp.row[0], vertexPosition;\n" +
          "DP4	outputPosition.y, state.matrix.mvp.row[1], vertexPosition;\n" +
          "DP4	outputPosition.z, state.matrix.mvp.row[2], vertexPosition;\n" +
          "DP4	outputPosition.w, state.matrix.mvp.row[3], vertexPosition;\n" +
          "MOV	result.color, vertex.color;\n" +
          "MOV	result.texcoord[0], vertex.texcoord[0];\n" +
          "MOV	result.texcoord[1], vertex.texcoord[1];\n" +
          "MOV	result.texcoord[2], vertex.texcoord[2];\n" +
          "END";

  private static String glslVertexProgram =
      "void main() {\n" +
          "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
          "gl_TexCoord[1] = gl_MultiTexCoord1;\n" +
          "gl_TexCoord[2] = gl_MultiTexCoord2;\n" +
          "gl_Position = ftransform();\n" +
          "}\n";

  private static String glslFragmentProgram =
      "uniform sampler2DRect luma;\n" +
          "uniform sampler2DRect chromaBlue;\n" +
          "uniform sampler2DRect chromaRed;\n" +
          "void main(void) {\n" +
          "vec4 c0 = vec4(1.403, 0.714, 0.0, 1.0);\n" +
          "vec4 c1 = vec4(0.0, 0.344, 1.770, 1.0);\n" +
          "vec4 Y = texture2DRect(luma, gl_TexCoord[0].st) - 0.0625;\n" +
          "vec4 Cb = texture2DRect(chromaBlue, gl_TexCoord[1].st) - 0.5;\n" +
          "vec4 Cr = texture2DRect(chromaRed, gl_TexCoord[2].st) - 0.5;\n" +
          "Cb *= c1;\n" +
          "Cr *= c0;\n" +
          "gl_FragColor.r = Y.r + Cr.r;\n" +
          "gl_FragColor.g = Y.g - Cr.g - Cb.g;\n" +
          "gl_FragColor.b = Y.b + Cb.b;\n" +
          "gl_FragColor.a = 1.0;\n" +
          "}\n";

  private static String atiFragmentProgram =
      "!!ATIfs1.0\n" +
          "StartConstants;\n" +
          "CONSTANT c0 = {0.7015, 0.0, 0.885, 0.0};\n" +
          "CONSTANT c1 = {0.714, 0.344, 0.0, 0.0};\n" +
          "CONSTANT c2 = {0.0625, 0.0625, 0.0625, 0.0};\n" +
          "EndConstants;\n" +
          "StartPrelimPass;\n" +
          "SampleMap r0, t0.str;\n" +
          "SampleMap r1, t1.str;\n" +
          "SampleMap r2, t2.str;\n" +
          "SUB r0.rgb, r0, c2;\n" +
          "MOV r3.r, r2.bias;\n" +
          "MOV r3.g, r1.bias;\n" +
          "MOV r0.a, 1;\n" +
          "EndPass;\n" +
          "StartOutputPass;\n" +
          "PassTexCoord r0, r0.str;\n" +
          "PassTexCoord r1, r1.str;\n" +
          "PassTexCoord r2, r2.str;\n" +
          "PassTexCoord r3, r3.str;\n" +
          "DOT2ADD r3.g, r3, c1, 0;\n" +
          "SUB r0.g, r0, r3;\n" +
          "MOV r3.rb, c0;\n" +
          "MAD r0.r, r2.bias, r3.2x, r0;\n" +
          "MAD r0.b, r1.bias, r3.2x, r0;\n" +
          "EndPass;\n";

  public void renderFragmentProgram(GL2 gl, java.awt.Rectangle srcVideoRect, java.awt.Rectangle currVideoBounds)
  {
    gl.glEnable(gl.GL_FRAGMENT_PROGRAM_ARB);
    gl.glBindProgramARB(gl.GL_FRAGMENT_PROGRAM_ARB, fragProg);
    gl.glActiveTexture(gl.GL_TEXTURE0);
    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[0]);
    gl.glActiveTexture(gl.GL_TEXTURE1);
    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[1]);
    gl.glActiveTexture(gl.GL_TEXTURE2);
    gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
    gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[2]);
    gl.glDisable(gl.GL_BLEND);
    gl.glColor4f(1,1,1,1);
    gl.glBegin(gl.GL_QUADS);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2, srcVideoRect.y/2);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE2, srcVideoRect.x/2, srcVideoRect.y/2);
    gl.glVertex3f(currVideoBounds.x, currVideoBounds.y, 1.0f);

    gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x+srcVideoRect.width, srcVideoRect.y);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x+srcVideoRect.width)/2, srcVideoRect.y/2);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE2, (srcVideoRect.x+srcVideoRect.width)/2, srcVideoRect.y/2);
    gl.glVertex3f(currVideoBounds.x+currVideoBounds.width, currVideoBounds.y, 1.0f);

    gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x+srcVideoRect.width, srcVideoRect.y + srcVideoRect.height);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x+srcVideoRect.width)/2, (srcVideoRect.y + srcVideoRect.height)/2);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE2, (srcVideoRect.x+srcVideoRect.width)/2, (srcVideoRect.y + srcVideoRect.height)/2);
    gl.glVertex3f(currVideoBounds.x+currVideoBounds.width,
        currVideoBounds.y+currVideoBounds.height, 1.0f);

    gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y + srcVideoRect.height);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2, (srcVideoRect.y + srcVideoRect.height)/2);
    gl.glMultiTexCoord2f(gl.GL_TEXTURE2, srcVideoRect.x/2, (srcVideoRect.y + srcVideoRect.height)/2);
    gl.glVertex3f(currVideoBounds.x, currVideoBounds.y+currVideoBounds.height, 1.0f);
    gl.glEnd();
    gl.glActiveTexture(gl.GL_TEXTURE2);
    gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    gl.glActiveTexture(gl.GL_TEXTURE1);
    gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    gl.glActiveTexture(gl.GL_TEXTURE0);
    gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
    gl.glDisable(gl.GL_FRAGMENT_PROGRAM_ARB);
  }

  /*public void renderATIFragmentProgram(GL2 gl, java.awt.Rectangle srcVideoRect, java.awt.Rectangle currVideoBounds)
	{
		gl.glEnable(gl.GL_TEXT_FRAGMENT_SHADER_ATI);
		gl.glBindProgramARB(gl.GL_TEXT_FRAGMENT_SHADER_ATI, fragProg);
		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[0]);
		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[1]);
		gl.glActiveTexture(gl.GL_TEXTURE2);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[2]);
		gl.glDisable(gl.GL_BLEND);
		gl.glColor4f(1,1,1,1);
		gl.glBegin(gl.GL_QUADS);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2, srcVideoRect.y/2);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE2, srcVideoRect.x/2, srcVideoRect.y/2);
			gl.glVertex3f(currVideoBounds.x, currVideoBounds.y, 1.0f);

			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x+srcVideoRect.width, srcVideoRect.y);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x+srcVideoRect.width)/2, srcVideoRect.y/2);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE2, (srcVideoRect.x+srcVideoRect.width)/2, srcVideoRect.y/2);
			gl.glVertex3f(currVideoBounds.x+currVideoBounds.width, currVideoBounds.y, 1.0f);

			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x+srcVideoRect.width, srcVideoRect.y + srcVideoRect.height);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x+srcVideoRect.width)/2, (srcVideoRect.y + srcVideoRect.height)/2);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE2, (srcVideoRect.x+srcVideoRect.width)/2, (srcVideoRect.y + srcVideoRect.height)/2);
			gl.glVertex3f(currVideoBounds.x+currVideoBounds.width,
				currVideoBounds.y+currVideoBounds.height, 1.0f);

			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y + srcVideoRect.height);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2, (srcVideoRect.y + srcVideoRect.height)/2);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE2, srcVideoRect.x/2, (srcVideoRect.y + srcVideoRect.height)/2);
			gl.glVertex3f(currVideoBounds.x, currVideoBounds.y+currVideoBounds.height, 1.0f);
		gl.glEnd();
		gl.glActiveTexture(gl.GL_TEXTURE2);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
		gl.glDisable(gl.GL_TEXT_FRAGMENT_SHADER_ATI);
	}*/

  // JFT: This is based on the old eavios opengl mode...
  /*public void renderNVCombiner(GL2 gl, java.awt.Rectangle srcVideoRect, java.awt.Rectangle currVideoBounds)
	{
		/* YUV to RGB conversion

		R = Y + 1.371 (Cr - 128)
		G = Y - 0.698 (Cr - 128) - 0.336 (Cb - 128)
		B = Y + 1.732 (Cb - 128)

		Using 9bit precision I think we can obtain partial conversion in hardware :

		Generate texture from  :

		(Cr-0.5) * (C0R/2-0.5) + 0.5
		(Cr-0.5) * (C0G/2-0.5) + 0.5
		(Cr-0.5) * (C0B/2-0.5) + 0.5

					+

		(Cb-0.5) * (C1R/2-0.5) + 0.5
		(Cb-0.5) * (C1G/2-0.5) + 0.5
		(Cb-0.5) * (C1B/2-0.5) + 0.5

		C0R,C0G,C0B = 1.371, -0.698, 0
		C1R,C1G,C1B = 0, -0.336, 1.732

		Then combine with Y :

		Y + (T0-0.5)*2
		Y + (T0-0.5)*2
		Y + (T0-0.5)*2
   */
  /*
		float constConv0[] = {1.371f/4.0f+0.5f, -0.698f/4.0f+0.5f, 0.0f+0.5f, 0.0f+0.5f};
		float constConv1[] = {0.0f+0.5f, -0.336f/4.0f+0.5f, 1.732f/4.0f+0.5f, 0.0f+0.5f};

		gl.glCombinerParameteriNV(gl.GL_NUM_GENERAL_COMBINERS_NV, 2);
		gl.glCombinerParameterfvNV(gl.GL_CONSTANT_COLOR0_NV, constConv0, 0);
		gl.glCombinerParameterfvNV(gl.GL_CONSTANT_COLOR1_NV, constConv1, 0);

		/* A = (Cr Texture - 0.5)
		B = (constant 0 - 0.5)*2
		C = (Cb Texture - 0.5)
		D = (constant 1 - 0.5)*2
		out = AB+CD
   */
  /*
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_A_NV, gl.GL_TEXTURE0,
						gl.GL_HALF_BIAS_NORMAL_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_B_NV, gl.GL_CONSTANT_COLOR0_NV,
						gl.GL_EXPAND_NORMAL_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_C_NV, gl.GL_TEXTURE1,
						gl.GL_HALF_BIAS_NORMAL_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_D_NV, gl.GL_CONSTANT_COLOR1_NV,
						gl.GL_EXPAND_NORMAL_NV, gl.GL_RGB);
		gl.glCombinerOutputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_DISCARD_NV, gl.GL_DISCARD_NV, gl.GL_SPARE0_NV,
						gl.GL_NONE, gl.GL_NONE, false , false, false);

		/* A = Spare0
		B = 1-0
		C = 1-0
		D = -0+0.5
		out = AB+CD
   */
  /*
		gl.glCombinerInputNV(gl.GL_COMBINER1_NV, gl.GL_RGB, gl.GL_VARIABLE_A_NV, gl.GL_SPARE0_NV,
						gl.GL_SIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER1_NV, gl.GL_RGB, gl.GL_VARIABLE_B_NV, gl.GL_ZERO,
						gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER1_NV, gl.GL_RGB, gl.GL_VARIABLE_C_NV, gl.GL_ZERO,
						gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER1_NV, gl.GL_RGB, gl.GL_VARIABLE_D_NV, gl.GL_ZERO,
						gl.GL_HALF_BIAS_NEGATE_NV, gl.GL_RGB);

		gl.glCombinerOutputNV(gl.GL_COMBINER1_NV, gl.GL_RGB, gl.GL_DISCARD_NV, gl.GL_DISCARD_NV, gl.GL_SPARE1_NV,
						gl.GL_NONE, gl.GL_NONE, false , false, false);

		/* out = SPARE1 */
  /*gl.glFinalCombinerInputNV(gl.GL_VARIABLE_A_NV, gl.GL_SPARE1_NV, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_B_NV, gl.GL_ZERO, gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_C_NV, gl.GL_ZERO, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_D_NV, gl.GL_ZERO, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);

		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[1]);
		gl.glTexEnvi(gl.GL_TEXTURE_ENV, gl.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
		gl.glColor4f(1,1,1,1);

		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glEnable(gl.GL_TEXTURE_RECTANGLE);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE,videot[2]);
		gl.glTexEnvi(gl.GL_TEXTURE_ENV, gl.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
		gl.glColor4f(1,1,1,1);

		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glDisable(gl.GL_BLEND);
		gl.glColor4f(1,1,1,1);
		gl.glEnable(gl.GL_REGISTER_COMBINERS_NV);
		gl.glBegin(gl.GL_QUADS);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, 0.0f , 0.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, 0.0f , 0.0f);
			gl.glVertex3f(0, 0, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, videowidth/2.0f, 0.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, videowidth/2.0f, 0.0f);
			gl.glVertex3f(videowidth/2.0f, 0, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, videowidth/2.0f, videoheight/2.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, videowidth/2.0f, videoheight/2.0f);
			gl.glVertex3f(videowidth/2.0f, videoheight/2.0f, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, 0.0f, videoheight/2.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, 0.0f, videoheight/2.0f);
			gl.glVertex3f(0.0f, videoheight/2.0f, 1.0f);
		gl.glEnd();
		gl.glDisable(gl.GL_REGISTER_COMBINERS_NV);

	/*		glClearColor( 128/256.0,
			128/256.0,
			128/256.0,
			0.5);

		// TODO: Optimization, no need to clear if video takes all the space...
		gl.glClear( GL_COLOR_BUFFER_BIT);*/
  /*
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videotemp[0]);
		gl.glCopyTexSubImage2D(gl.GL_TEXTURE_RECTANGLE, 0, 0, 0, 0,
			owner.getCanvasHeight()-videoheight/2, videowidth/2, videoheight/2); //windowheight-videoheight/2

		gl.glClearColor( 0.0f, 0.0f, 0.0f, 0.0f);

		gl.glClear(gl.GL_COLOR_BUFFER_BIT);

		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videot[0]);

		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glBindTexture(gl.GL_TEXTURE_RECTANGLE, videotemp[0]);

		gl.glCombinerParameteriNV(gl.GL_NUM_GENERAL_COMBINERS_NV, 1);

		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_A_NV, gl.GL_TEXTURE0,
						gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_B_NV, gl.GL_ZERO,
						gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_C_NV, gl.GL_TEXTURE1,
						gl.GL_EXPAND_NORMAL_NV, gl.GL_RGB);
		gl.glCombinerInputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_VARIABLE_D_NV, gl.GL_ZERO,
						gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glCombinerOutputNV(gl.GL_COMBINER0_NV, gl.GL_RGB, gl.GL_DISCARD_NV, gl.GL_DISCARD_NV, gl.GL_SPARE0_NV,
						gl.GL_NONE, gl.GL_NONE, false , false, false);

		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_A_NV, gl.GL_SPARE0_NV, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_B_NV, gl.GL_ZERO, gl.GL_UNSIGNED_INVERT_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_C_NV, gl.GL_ZERO, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);
		gl.glFinalCombinerInputNV(gl.GL_VARIABLE_D_NV, gl.GL_ZERO, gl.GL_UNSIGNED_IDENTITY_NV, gl.GL_RGB);


		gl.glEnable(gl.GL_REGISTER_COMBINERS_NV);
		gl.glBegin(gl.GL_QUADS);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2.0f , (srcVideoRect.y + srcVideoRect.height)/2.0f);
			gl.glVertex3f(currVideoBounds.x, currVideoBounds.y, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x + srcVideoRect.width, srcVideoRect.y);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x + srcVideoRect.width)/2.0f, (srcVideoRect.y + srcVideoRect.height)/2.0f);
			gl.glVertex3f(currVideoBounds.x+currVideoBounds.width, currVideoBounds.y, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x + srcVideoRect.width, srcVideoRect.y + srcVideoRect.height);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, (srcVideoRect.x + srcVideoRect.width)/2.0f, srcVideoRect.y/2.0f);
			gl.glVertex3f(currVideoBounds.x+currVideoBounds.width, currVideoBounds.y+currVideoBounds.height, 1.0f);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE0, srcVideoRect.x, srcVideoRect.y + srcVideoRect.height);
			gl.glMultiTexCoord2f(gl.GL_TEXTURE1, srcVideoRect.x/2.0f, srcVideoRect.y/2.0f);
			gl.glVertex3f(currVideoBounds.x, currVideoBounds.y+currVideoBounds.height, 1.0f);
		gl.glEnd();

		gl.glDisable(gl.GL_REGISTER_COMBINERS_NV);

		gl.glActiveTexture(gl.GL_TEXTURE1);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);

		gl.glActiveTexture(gl.GL_TEXTURE0);
		gl.glDisable(gl.GL_TEXTURE_RECTANGLE);
		gl.glDisable(gl.GL_BLEND);
		gl.glColor4f(1,1,1,1);
	}*/
  public boolean drawVideo(GL2 gl, java.awt.Rectangle srcVideoRect, java.awt.Rectangle currVideoBounds)
  {
    if(activevideo && pureNativeMode) {
      try {
        drawVideo0(srcVideoRect, currVideoBounds);
      } catch(Throwable t) {}
      return true;
    } else if(activevideo && videot!=null && currVideoBounds!=null)
    {
      if (srcVideoRect == null)
        srcVideoRect = new java.awt.Rectangle(0, 0, videowidth, videoheight);
      if(videoMode==0)
      {
        String rndrStr = gl.glGetString(gl.GL_RENDERER).toUpperCase();
        String vendrStr = gl.glGetString(gl.GL_VENDOR).toUpperCase();
        if(gl.isExtensionAvailable("GL_ARB_fragment_program"))
        {
          System.out.println("Using fragment program mode\n");
          videoMode=1;
          initFragmentProgram(gl);
        }
        // JFT: those are not supported anymore in recent jogl
        /*else if(gl.isExtensionAvailable("GL_ATI_text_fragment_shader"))
				{
					System.out.println("Using ATI mode\n");
					videoMode=2;
					initATIFragmentProgram(gl);
				}
				else if(gl.isExtensionAvailable("GL_NV_register_combiners"))
				{
					System.out.println("Using  NV_register_combiners mode\n");
					videoMode=3;
					initNVCombiner(gl);
				}*/
        else
        {
          System.out.println("Couldn't find fragment program extension? No video will be shown\n");
          videoMode=9;
        }
      }
      switch(videoMode)
      {
        case 0:
          gl.glClear( gl.GL_COLOR_BUFFER_BIT);
          break;
        case 1:
          renderFragmentProgram(gl, srcVideoRect, currVideoBounds);
          break;
          /*case 2:
					renderATIFragmentProgram(gl, srcVideoRect, currVideoBounds);
					break;
				case 3:
					renderNVCombiner(gl, srcVideoRect, currVideoBounds);
					break;*/
        case 9:
          gl.glClear( gl.GL_COLOR_BUFFER_BIT);
          break;
      }
      return true;
    }
    else
    {
      gl.glClear(gl.GL_COLOR_BUFFER_BIT);
      return false;
    }
  }

  // defer rendering to underlying native code (for integration with other non-Java APIs, like CoreVideo or QT)
  // calling closeVideo or createVideo will end native mode
  public boolean startNativeMode(int width, int height)
  {
    System.out.println("OpenGLVideoRenderer startNativeMode "+width+" "+height);
    videowidth=width;
    videoheight=height;
    pureNativeMode=true;

    if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
    {
      javax.media.opengl.Threading.invokeOnOpenGLThread(true, new Runnable() {
        public void run() {
          createVideo();
        }
      });
    }
    else
      createVideo();
    return true;
  }

  // This method is called to create the video server
  public boolean createVideo(int width, int height, int format)
  {
    System.out.println("OpenGLVideoRenderer createVideo "+width+" "+height+" "+format);
    videowidth=width;
    videoheight=height;
    pureNativeMode=false;
    if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
    {
      javax.media.opengl.Threading.invokeOnOpenGLThread(true, new Runnable()
      {
        public void run()
        {
          createVideo();
        }
      });
    }
    else
      createVideo();
    return true;
  }

  public boolean updateVideo(final int frametype, java.nio.ByteBuffer buf)
  {
    //System.out.println("OpenGLVideoRenderer updateVideo "+frametype);
    if(!pureNativeMode) {
      videoy=0;
      videoypitch=videowidth;
      videou=videowidth*videoheight;
      videoupitch=videowidth/2;
      videov=videowidth*videoheight+videowidth*videoheight/4;
      videovpitch=videowidth/2;
      videobuffer=buf;
    }
    if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
    {
      javax.media.opengl.Threading.invokeOnOpenGLThread(true, new Runnable()
      {
        public void run()
        {
          updateVideo(frametype);
        }
      });
    }
    else
      updateVideo(frametype);

    activevideo=true;
    owner.videoWasUpdated();
    return true;
  }

  public boolean closeVideo()
  {
    System.out.println("OpenGLVideoRenderer closeVideo");
    activevideo=false;
    pureNativeMode=false;
    if(MiniClient.MAC_OS_X) {
      if (javax.media.opengl.Threading.isSingleThreaded() && !javax.media.opengl.Threading.isOpenGLThread())
      {
        javax.media.opengl.Threading.invokeOnOpenGLThread(true,new Runnable()
        {
          public void run()
          {
            closeVideo0();
          }
        });
      }
      else
        closeVideo0();
    }
    return true;
  }
}
