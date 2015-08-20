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

import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;

public class MacAboutBox extends JFrame implements ActionListener {
	protected JLabel titleLabel, aboutLabel[];
	protected static int labelCount = 8;
	protected static int aboutWidth = 280;
	protected static int aboutHeight = 230;
	protected static int aboutTop = 200;
	protected static int aboutLeft = 350;
	protected Font titleFont, bodyFont;
//	protected ResourceBundle resbundle;

	public MacAboutBox() {
		super("");
		this.setResizable(false);
//		resbundle = ResourceBundle.getBundle ("MyFirstJNIProjectstrings", Locale.getDefault());
		SymWindow aSymWindow = new SymWindow();
		this.addWindowListener(aSymWindow);

		// Initialize useful fonts
		titleFont = new Font("Lucida Grande", Font.BOLD, 14);
		if (titleFont == null) {
			titleFont = new Font("SansSerif", Font.BOLD, 14);
		}
		bodyFont  = new Font("Lucida Grande", Font.PLAIN, 10);
		if (bodyFont == null) {
			bodyFont = new Font("SansSerif", Font.PLAIN, 10);
		}

		this.getContentPane().setLayout(new BorderLayout(15, 15));

		Image iconImage = sage.ImageUtils.fullyLoadImage(new java.io.File(Sage.getPath("data","SageTV.icns"))); // java.awt.Toolkit.getDefaultToolkit().createImage(sage.Sage.get("ui/splash_image", null));
		ImageIcon appIcon = new ImageIcon(iconImage);

		aboutLabel = new JLabel[labelCount];
		aboutLabel[0] = new JLabel(appIcon);
		aboutLabel[1] = new JLabel("SageTV Studio V" + sage.Version.VERSION); //resbundle.getString("frameConstructor"));
		aboutLabel[1].setFont(bodyFont);
		aboutLabel[2] = new JLabel("");//resbundle.getString("appVersion"));
		aboutLabel[2].setFont(bodyFont);
		aboutLabel[3] = new JLabel("");
		aboutLabel[4] = new JLabel("");
		aboutLabel[5] = new JLabel("JDK " + System.getProperty("java.version"));
		aboutLabel[5].setFont(bodyFont);
		aboutLabel[6] = new JLabel("Copyright 2007 SageTV, LLC");//resbundle.getString("copyright"));
		aboutLabel[6].setFont(bodyFont);
		aboutLabel[7] = new JLabel("");

		Panel textPanel2 = new Panel(new GridLayout(labelCount, 1));
		for (int i = 0; i<labelCount; i++) {
			aboutLabel[i].setHorizontalAlignment(JLabel.CENTER);
			textPanel2.add(aboutLabel[i]);
		}
		this.getContentPane().add (textPanel2, BorderLayout.CENTER);
		this.pack();
		this.setLocation(aboutLeft, aboutTop);
		this.setSize(aboutWidth, aboutHeight);
	}

	class SymWindow extends java.awt.event.WindowAdapter {
		public void windowClosing(java.awt.event.WindowEvent event) {
			setVisible(false);
		}
	}

	public void actionPerformed(ActionEvent newEvent) {
		setVisible(false);
	}
}
