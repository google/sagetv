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
package tv.sage.util;

public class StudioAPIProcessor
{
	public static void main(String[] args)
	{
		if (args.length != 2)
		{
			System.out.println("Usage: java StudioAPIProcessor SourceDir DestDir");
			return;
		}
		java.io.File srcDir = new java.io.File(args[0]);
		java.io.File destDir = new java.io.File(args[1]);
		if (!srcDir.isDirectory())
		{
			System.out.println("SourceDir is not valid!");
			return;
		}
		if (!destDir.isDirectory() && !destDir.mkdirs())
		{
			System.out.println("Failed creating DestDir!");
			return;
		}
		java.io.File[] srcFiles = srcDir.listFiles(new java.io.FileFilter()
		{
			public boolean accept(java.io.File pathname)
			{
				return pathname.toString().toLowerCase().endsWith(".java");
			}
		});
		if (srcFiles == null || srcFiles.length == 0)
		{
			System.out.println("No .java files found in source directory. Nothing to do.");
			return;
		}
		for (int i = 0; i < srcFiles.length; i++)
		{
			System.out.println("processing source file: " + srcFiles[i]);
			try
			{
				java.io.BufferedReader inStream = new java.io.BufferedReader(new java.io.FileReader(srcFiles[i]));
				java.io.PrintWriter outStream = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(
					new java.io.File(destDir, srcFiles[i].getName()))));
				String currLine = inStream.readLine();
				// First we look for the first curly brace
				while (currLine.indexOf('{') == -1)
				{
					outStream.println(currLine);
					currLine = inStream.readLine();
				}
				outStream.println(currLine);

				// Now don't write anything else out except for the comment blocks, and read each of those in fully
				// before you write them out
				while (true)
				{
					java.util.Vector currCommentBlock = new java.util.Vector();
					currLine = inStream.readLine();
					if (currLine == null) break;
					while (!currLine.trim().startsWith("/**") || currLine.trim().startsWith("/**/"))
					{
						currLine = inStream.readLine();
						if (currLine == null) break;
					}
					currCommentBlock.add(currLine);
					currLine = inStream.readLine();
					if (currLine == null) break;
					while (currLine.trim().startsWith("*"))
					{
						currCommentBlock.add(currLine);
						currLine = inStream.readLine();
						if (currLine == null) break;
					}
					// Now write out the comment block, but uncomment the 2nd to last line and put a */ before it
					for (int j = 0; j < currCommentBlock.size() - 2; j++)
					{
						outStream.println(currCommentBlock.get(j).toString());
					}
					outStream.println("*/");
					if (currCommentBlock.size() > 2)
					{
						String declare = currCommentBlock.get(currCommentBlock.size() - 2).toString();
						if (declare.indexOf("@declaration") != -1)
						{
							declare = declare.substring(declare.indexOf("@declaration") + "@declaration".length()).trim();
							if (declare.length() > 8)
							{
								outStream.println(declare);
							}
						}
					}
					//declare = declare.trim().substring(1).trim();
				}
				outStream.println("}");
				outStream.close();
				inStream.close();
			}
			catch (Exception e)
			{
				System.out.println("Processing error: " + e);
				e.printStackTrace();
			}
		}
	}
}