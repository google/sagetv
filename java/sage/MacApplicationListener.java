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

import com.apple.eawt.*;

public class MacApplicationListener extends ApplicationAdapter {
	public static void StartStudioMode()
	{
		Application app = Application.getApplication();
		
		app.addAboutMenuItem();
		app.setEnabledAboutMenu(true);
		
		if(app.isPreferencesMenuItemPresent()) app.removePreferencesMenuItem(); // no prefs from here
		
		app.addApplicationListener(new MacApplicationListener());
	}
	
	private sage.MacAboutBox aboutBox = null;
	
	public void handleAbout(ApplicationEvent event)
	{
		System.out.println("MacApplicationListener.handleAbout");
		event.setHandled(true); // stop event propagation here...
		
		if(aboutBox == null) aboutBox = new sage.MacAboutBox();
		
		// get/set background (splash) image...
		
		aboutBox.setResizable(false);
		aboutBox.setVisible(true);
	}
	
	public void handleQuit(ApplicationEvent event)
	{
		// call into sage.Sage to terminate
		System.out.println("MacApplicationListener.handleAbout");
		event.setHandled(true); // stop event propagation here...
		sage.SageTV.exit(false, 0);
	}
};
