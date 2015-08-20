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

import javax.vecmath.*;

public final class MathUtils
{
  private MathUtils(){}

  // rv has x, y, w, h
  public static void transformRectCoords(java.awt.geom.RectangularShape rect, java.awt.geom.AffineTransform xform,
      float[] rv)
  {
    double[] coords = {rect.getX(), rect.getY()};
    double[] size = {rect.getWidth(), rect.getHeight()};
    xform.transform(coords, 0, coords, 0, 1);
    xform.deltaTransform(size, 0, size, 0, 1);
    rv[0] = (float)coords[0];
    rv[1] = (float)coords[1];
    rv[2] = (float)size[0];
    rv[3] = (float)size[1];
  }
  /*	public static java.awt.geom.Rectangle2D.Float transformRectCoords(java.awt.geom.RectangularShape rect, java.awt.geom.AffineTransform xform)
	{
		double[] coords = {rect.getX(), rect.getY()};
		double[] size = {rect.getWidth(), rect.getHeight()};
		xform.transform(coords, 0, coords, 0, 1);
		xform.deltaTransform(size, 0, size, 0, 1);
		return new java.awt.geom.Rectangle2D.Float((float)coords[0], (float)coords[1], (float)size[0], (float)size[1]);
	}

	public static void transformRectCoords(java.awt.geom.RectangularShape rect, java.awt.geom.AffineTransform xform,
		java.awt.geom.RectangularShape rv)
	{
		double[] coords = {rect.getX(), rect.getY()};
		double[] size = {rect.getWidth(), rect.getHeight()};
		xform.transform(coords, 0, coords, 0, 1);
		xform.deltaTransform(size, 0, size, 0, 1);
		rv.setFrame(coords[0], coords[1], size[0], size[1]);
	}
   */
  public static void clipSrcRect(java.awt.geom.Rectangle2D.Float clipRect, java.awt.geom.Rectangle2D.Float srcRect,
      float translateX, float translateY)
  {
    srcRect.x += translateX;
    srcRect.y += translateY;
    // Deal with negative dimensions
    if (clipRect.width < 0)
    {
      float temp = clipRect.width;
      clipRect.x = clipRect.x + clipRect.width;
      clipRect.width = -temp;
    }
    if (clipRect.height < 0)
    {
      float temp = clipRect.height;
      clipRect.y = clipRect.y + clipRect.height;
      clipRect.height = -temp;
    }
    if (srcRect.width < 0)
    {
      float temp = srcRect.width;
      srcRect.x = srcRect.x + srcRect.width;
      srcRect.width = -temp;
    }
    if (srcRect.height < 0)
    {
      float temp = srcRect.height;
      srcRect.y = srcRect.y + srcRect.height;
      srcRect.height = -temp;
    }

    // Clip the rects
    if (srcRect.x < clipRect.x)
    {
      float xDiff = clipRect.x - srcRect.x;
      srcRect.x += xDiff;
      srcRect.width -= xDiff;
    }
    if (srcRect.y < clipRect.y)
    {
      float yDiff = clipRect.y - srcRect.y;
      srcRect.y += yDiff;
      srcRect.height -= yDiff;
    }
    if (srcRect.x + srcRect.width > clipRect.x + clipRect.width)
    {
      float over = (srcRect.x + srcRect.width) - (clipRect.x + clipRect.width);
      srcRect.width -= over;
    }
    if (srcRect.y + srcRect.height > clipRect.y + clipRect.height)
    {
      float over = (srcRect.y + srcRect.height) - (clipRect.y + clipRect.height);
      srcRect.height -= over;
    }
    srcRect.x -= translateX;
    srcRect.y -= translateY;
  }

  public static void clipSrcRect(java.awt.Rectangle clipRect, java.awt.Rectangle srcRect,
      int translateX, int translateY)
  {
    srcRect.x += translateX;
    srcRect.y += translateY;
    // Deal with negative dimensions
    if (clipRect.width < 0)
    {
      int temp = clipRect.width;
      clipRect.x = clipRect.x + clipRect.width;
      clipRect.width = -temp;
    }
    if (clipRect.height < 0)
    {
      int temp = clipRect.height;
      clipRect.y = clipRect.y + clipRect.height;
      clipRect.height = -temp;
    }
    if (srcRect.width < 0)
    {
      int temp = srcRect.width;
      srcRect.x = srcRect.x + srcRect.width;
      srcRect.width = -temp;
    }
    if (srcRect.height < 0)
    {
      int temp = srcRect.height;
      srcRect.y = srcRect.y + srcRect.height;
      srcRect.height = -temp;
    }

    // Clip the rects
    if (srcRect.x < clipRect.x)
    {
      int xDiff = clipRect.x - srcRect.x;
      srcRect.x += xDiff;
      srcRect.width -= xDiff;
    }
    if (srcRect.y < clipRect.y)
    {
      int yDiff = clipRect.y - srcRect.y;
      srcRect.y += yDiff;
      srcRect.height -= yDiff;
    }
    if (srcRect.x + srcRect.width > clipRect.x + clipRect.width)
    {
      int over = (srcRect.x + srcRect.width) - (clipRect.x + clipRect.width);
      srcRect.width -= over;
    }
    if (srcRect.y + srcRect.height > clipRect.y + clipRect.height)
    {
      int over = (srcRect.y + srcRect.height) - (clipRect.y + clipRect.height);
      srcRect.height -= over;
    }
    srcRect.x -= translateX;
    srcRect.y -= translateY;
  }

  // 3D transformations
  public static Matrix4f createIdentityMatrix()
  {
    return new Matrix4f(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
  }

  public static void translateMatrix(Matrix4f mat, float x, float y)
  {
    Matrix4f transMat = new Matrix4f(1.0f, 0.0f, 0.0f, x, 0.0f, 1.0f, 0.0f, y, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    mat.mul(transMat);
  }

  public static void setToTranslation(Matrix4f mat, float x, float y)
  {
    mat.setRow(0, 1.0f, 0.0f, 0.0f, x);
    mat.setRow(1, 0.0f, 1.0f, 0.0f, y);
    mat.setRow(2, 0.0f, 0.0f, 1.0f, 0.0f);
    mat.setRow(3, 0.0f, 0.0f, 0.0f, 1.0f);
  }

  public static void scaleMatrix(Matrix4f mat, float x, float y)
  {
    Matrix4f scaleMat = new Matrix4f(x, 0.0f, 0.0f, 0.0f, 0.0f, y, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    mat.mul(scaleMat);
  }

  public static void rotateMatrixX(Matrix4f mat, float thetaDegrees)
  {
    Matrix4f rotMat = new Matrix4f();
    rotMat.rotX((float)(thetaDegrees * Math.PI/180));
    mat.mul(rotMat);
  }

  public static void rotateMatrixY(Matrix4f mat, float thetaDegrees)
  {
    Matrix4f rotMat = new Matrix4f();
    rotMat.rotY((float)(thetaDegrees * Math.PI/180));
    mat.mul(rotMat);
  }

  public static void rotateMatrixZ(Matrix4f mat, float thetaDegrees)
  {
    Matrix4f rotMat = new Matrix4f();
    rotMat.rotZ((float)(thetaDegrees * Math.PI/180));
    mat.mul(rotMat);
  }

  public static Matrix4f createScaleMatrix(float x, float y)
  {
    return new Matrix4f(x, 0.0f, 0.0f, 0.0f, 0.0f, y, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
  }

  public static Matrix4f createTranslateMatrix(float x, float y)
  {
    return new Matrix4f(1.0f, 0.0f, 0.0f, x, 0.0f, 1.0f, 0.0f, y, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
  }

  // rv has x, y, w, h; this also fixes any negative sizes for the rectangle since its used for clipping rects
  public static void transformRectCoords(java.awt.geom.RectangularShape rect, Matrix4f xform,
      float[] rv)
  {
    Point3f coords1 = new Point3f((float)rect.getMinX(), (float)rect.getMinY(), 0);
    Point3f coords2 = new Point3f((float)rect.getMinX(), (float)rect.getMaxY(), 0);
    Point3f coords3 = new Point3f((float)rect.getMaxX(), (float)rect.getMinY(), 0);
    Point3f coords4 = new Point3f((float)rect.getMaxX(), (float)rect.getMaxY(), 0);
    xform.transform(coords1);
    xform.transform(coords2);
    xform.transform(coords3);
    xform.transform(coords4);
    rv[0] = Math.min(Math.min(Math.min(coords1.x, coords2.x), coords3.x), coords4.x);
    rv[1] = Math.min(Math.min(Math.min(coords1.y, coords2.y), coords3.y), coords4.y);
    rv[2] = Math.max(Math.max(Math.max(coords1.x, coords2.x), coords3.x), coords4.x) - rv[0];
    rv[3] = Math.max(Math.max(Math.max(coords1.y, coords2.y), coords3.y), coords4.y) - rv[1];
    /*		Point3f coords = new Point3f((float)rect.getX(), (float)rect.getY(), 0);
		Vector3f size = new Vector3f((float)rect.getWidth(), (float)rect.getHeight(), 0);
		xform.transform(coords);
		xform.transform(size);
		rv[0] = coords.x;
		rv[1] = coords.y;
		rv[2] = size.x;
		rv[3] = size.y;
		// Remove negative dimensions from the rectangle
		if (rv[2] < 0)
		{
			rv[0] += rv[2];
			rv[2] *= -1;
		}
		if (rv[3] < 0)
		{
			rv[1] += rv[3];
			rv[3] *= -1;
		}*/
  }

  public static java.awt.geom.Rectangle2D.Float transformRectCoords(java.awt.geom.RectangularShape rect, Matrix4f xform)
  {
    java.awt.geom.Rectangle2D.Float rv = new java.awt.geom.Rectangle2D.Float();
    transformRectCoords(rect, xform, rv);
    return rv;
  }

  public static void transformRectCoords(java.awt.geom.RectangularShape rect, Matrix4f xform, java.awt.geom.RectangularShape rv)
  {
    float minx = (float)rect.getMinX();
    float miny = (float)rect.getMinY();
    float maxx = (float)rect.getMaxX();
    float maxy = (float)rect.getMaxY();

    float  x1, y1, x2, y2, x3, y3, x4, y4;
    x1 = xform.m00*minx + xform.m03;
    y1 = xform.m10*minx + xform.m13;

    x2 = x1 + xform.m01*maxy;
    y2 = y1 + xform.m11*maxy;

    x1 += xform.m01*miny;
    y1 += xform.m11*miny;

    x3 = xform.m00*maxx + xform.m03;
    y3 = xform.m10*maxx + xform.m13;

    x4 = x3 + xform.m01*maxy;
    y4 = y3 + xform.m11*maxy;

    x3 += xform.m01*miny;
    y3 += xform.m11*miny;

    float x = Math.min(Math.min(Math.min(x1, x2), x3), x4);
    float y = Math.min(Math.min(Math.min(y1, y2), y3), y4);
    float w = Math.max(Math.max(Math.max(x1, x2), x3), x4) - x;
    float h = Math.max(Math.max(Math.max(y1, y2), y3), y4) - y;
    if (rv instanceof java.awt.Rectangle)
    {
      int xi = (int)x;
      int yi = (int)y;
      int wi = (int)(x + w) - xi;
      int hi = (int)(y + h) - yi;
      ((java.awt.Rectangle) rv).setBounds(xi, yi, wi, hi);
    }
    else
      rv.setFrame(x, y, w, h);
  }

  public static Matrix4f createInverse(Matrix4f mat)
  {
    Matrix4f rv = new Matrix4f();
    rv.invert(mat);
    return rv;
  }

  public static boolean isTranslateOnlyMatrix(Matrix4f mat)
  {
    return mat.m00 == 1.0f && mat.m01 == 0 && mat.m02 == 0 &&
        mat.m10 == 0 && mat.m11 == 1.0f && mat.m12 == 0 &&
        mat.m20 == 0 && mat.m21 == 0 && mat.m22 == 1.0f &&
        mat.m30 == 0 && mat.m31 == 0 && mat.m32 == 0 && mat.m33 == 1;
  }

  public static boolean isTranslateScaleOnlyMatrix(Matrix4f mat)
  {
    return mat.m01 == 0 && mat.m02 == 0 &&
        mat.m10 == 0 && mat.m12 == 0 &&
        mat.m20 == 0 && mat.m21 == 0 &&
        mat.m30 == 0 && mat.m31 == 0 && mat.m32 == 0 && mat.m33 == 1;
  }

  public static void getMatrixValues(Matrix4f mat, float[] arr)
  {
    arr[0] = mat.m00; arr[1] = mat.m01; arr[2] = mat.m02; arr[3] = mat.m03;
    arr[4] = mat.m10; arr[5] = mat.m11; arr[6] = mat.m12; arr[7] = mat.m13;
    arr[8] = mat.m20; arr[9] = mat.m21; arr[10] = mat.m22; arr[11] = mat.m23;
    arr[12] = mat.m30; arr[13] = mat.m31; arr[14] = mat.m32; arr[15] = mat.m33;
  }

  public static void getMatrixValuesTransposed(Matrix4f mat, float[] arr)
  {
    arr[0] = mat.m00; arr[1] = mat.m10; arr[2] = mat.m02; arr[3] = mat.m30;
    arr[4] = mat.m01; arr[5] = mat.m11; arr[6] = mat.m21; arr[7] = mat.m31;
    arr[8] = mat.m02; arr[9] = mat.m12; arr[10] = mat.m22; arr[11] = mat.m32;
    arr[12] = mat.m03; arr[13] = mat.m13; arr[14] = mat.m23; arr[15] = mat.m33;
  }

  public static void convertToAffineTransform(Matrix4f mat, java.awt.geom.AffineTransform affineMat)
  {
    affineMat.setTransform(mat.m00, mat.m10, mat.m01, mat.m11, mat.m03, mat.m13);
  }

  public static float getTranslateX(Matrix4f mat)
  {
    return mat.m03;
  }
  public static float getTranslateY(Matrix4f mat)
  {
    return mat.m13;
  }
  public static float getScaleX(Matrix4f mat)
  {
    return mat.m00;
  }
  public static float getScaleY(Matrix4f mat)
  {
    return mat.m11;
  }

  public static int compositeColors(int c1, int c2)
  {
    return ((((c1 >> 16) & 0xFF) * ((c2 >> 16) & 0xFF) / 255) << 16) +
        ((((c1 >> 8) & 0xFF) * ((c2 >> 8) & 0xFF) / 255) << 8) +
        ((c1 & 0xFF) * (c2 & 0xFF) / 255);
  }
}
