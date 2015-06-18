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
package sage.geom;

/**
 *
 * @author Narflex
 */
public class Rectangle
{

	/** Creates a new instance of Rectangle */
	public Rectangle()
	{
	}

	public Rectangle(/*EMBEDDED_SWITCH*/float/*/int/**/ x, /*EMBEDDED_SWITCH*/float/*/int/**/ y,
		/*EMBEDDED_SWITCH*/float/*/int/**/ w, /*EMBEDDED_SWITCH*/float/*/int/**/ h)
	{
		this.x = x;
		this.y = y;
		this.width = w;
		this.height = h;
	}

	public Rectangle(java.awt.geom.Rectangle2D rect)
	{
		x = (/*EMBEDDED_SWITCH*/float/*/int/**/) rect.getX();
		y = (/*EMBEDDED_SWITCH*/float/*/int/**/) rect.getY();
		width = (/*EMBEDDED_SWITCH*/float/*/int/**/) rect.getWidth();
		height = (/*EMBEDDED_SWITCH*/float/*/int/**/) rect.getHeight();
	}

	public void integerize()
	{
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = (int) Math.ceil(x+width);
		int y1 = (int) Math.ceil(y+height);
		x = x0;
		y = y0;
		width = x1 - x0;
		height = y1 - y0;
	}

	public /*EMBEDDED_SWITCH*/float/*/int/**/ getX() { return x; }
	public /*EMBEDDED_SWITCH*/float/*/int/**/ getY() { return y; }
	public /*EMBEDDED_SWITCH*/float/*/int/**/ getWidth() { return width; }
	public /*EMBEDDED_SWITCH*/float/*/int/**/ getHeight() { return height; }

	public int getXi() { return (int)x; }
	public int getYi() { return (int)y; }
	public int getWidthi() { return (int)width; }
	public int getHeighti() { return (int)height; }

/*EMBEDDED_SWITCH*/
	public int getXr() { return Math.round(x); }
	public int getYr() { return Math.round(y); }
	public int getWidthr() { return Math.round(width); }
	public int getHeightr() { return Math.round(height); }
/*/
	public int getXr() { return x; }
	public int getYr() { return y; }
	public int getWidthr() { return width; }
	public int getHeightr() { return height; }
/**/
	public /*EMBEDDED_SWITCH*/float/*/int/**/ x;
	public /*EMBEDDED_SWITCH*/float/*/int/**/ y;
	public /*EMBEDDED_SWITCH*/float/*/int/**/ width;
	public /*EMBEDDED_SWITCH*/float/*/int/**/ height;
}
