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
package sage.pluginmanager;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import sage.Sage;
import sage.SageTV;

/**
 *
 * Container class for the static functions used to automatically
 * build an STV file with all imports included
 *
 * @author Niel Markwick
 *
 */
public final class PluginManager {

	// Widget ID of widget chain for displaying a messagebox.
	private static final String MESSAGEBOX_ID="NIELM-17382";
	// Name of top of widget chain for displaying a messagebox.
	private static final String MESSAGEBOX_NAME="REM Display MessageBox. Pass Message, DisplayOkButton, Timeout";
	private static final long MESSAGEBOX_TIMEOUT=10000;

	private static final String PROPERTIES_NAME="ui/plugin_manager/";
	private static final String PROPERTIES_BASESTV_NAME=PROPERTIES_NAME+"basestv/";
	private static final String PROPERTIES_STVIS_NAME=PROPERTIES_NAME+"stvis";


	private static void displayMessage(String UIContext,String message, boolean displayOK, long timeout) {

		// find message box widget chain
		Object messageBoxWidget=null;
		try {
			messageBoxWidget=SageTV.apiUI(
					UIContext,
					"FindWidgetBySymbol",
					new Object[]{MESSAGEBOX_ID});
		} catch (InvocationTargetException e){
			System.out.println(e.getCause());
		}
		if ( messageBoxWidget==null ){
			System.out.println("PluginManager.displayMessage: Failed to get message box wiget by ID "+MESSAGEBOX_ID+", trying name");
			try {
				messageBoxWidget=SageTV.apiUI(
						UIContext,
						"EvaluateExpression",
						new Object[]{
								"GetElement(" +
									"FilterByMethod(" +
										"GetWidgetsByType(\"Action\")," +
										"\"GetWidgetName\"," +
										"\"\\\""+MESSAGEBOX_NAME+"\\\"\", true),0)"
						});
			} catch (InvocationTargetException e){
				System.out.println(e.getCause());
			}
		}
		if ( messageBoxWidget==null ) {
			System.out.println("PluginManager.displayMessage: Failed to find messagebox widget by ID "+MESSAGEBOX_ID+" or by name \""+MESSAGEBOX_NAME+"\"");
			System.out.println("PluginManager.displayMessage: Message: "+message);
			return;
		}

		// setup variables
		try {
			SageTV.apiUI(
					UIContext,
					"AddStaticContext",
					new Object[] {"Message",message});
			SageTV.apiUI(
					UIContext,
					"AddStaticContext",
					new Object[] {"DisplayOkButton",new Boolean(displayOK)});
			SageTV.apiUI(
					UIContext,
					"AddStaticContext",
					new Object[] {"Timeout",new  Long(timeout)});
			// launch widgetchain
			SageTV.apiUI(
					UIContext,
					"ExecuteWidgetChain",
					new Object[] {messageBoxWidget});
		} catch (InvocationTargetException e){
			System.out.println(e.getCause());
			System.out.println("PluginManager.displayMessage: Failed to launch messagebox widget");
		}
		System.out.println("PluginManager.displayMessage: Message: "+message);
	}

	/**
	 * Builds a custom STV using the base STV and the imports
	 * specified in the .properties files.
	 * Upon failure, it reloads the current STV file.
	 * This must be run in a Fork()'ed thread.
	 *
	 *
	 * Returns Boolean.TRUE, on success or a String containing an error message on failure
	 */
	public static Object buildCustomSTV(String UIContext) {

		// get current STV file so that we can revert...
		String currentSTV;
		try {
			currentSTV=(String) SageTV.apiUI(
								UIContext,
								"GetCurrentSTVFile",
								null);
			assert currentSTV!=null && currentSTV.length()>0;
		} catch ( Throwable e) {
			if ( e.getCause()!=null)
				e=e.getCause();
			System.out.println("PluginManager.BuidCustomSTV: Failed getting current STV file: "+e);
			return "Failed to get current STV file";
		}
		String baseSTV;
		String[] stvis;
		int numStvis=0;
		// Read properties file to get base and import STVIs
		try {
			baseSTV=(String) SageTV.apiUI(
					UIContext,
					"GetProperty",
					new Object[] {
							PROPERTIES_BASESTV_NAME+"name",
							""
					});

			String numStvisString=(String) SageTV.apiUI(
						UIContext,
						"GetProperty",
						new Object[] {
							PROPERTIES_STVIS_NAME+"/num_items",
							new Integer(0)
						});
			if ( numStvisString!=null && numStvisString.length()>0) {
				try {
					numStvis=Integer.parseInt(numStvisString);
				} catch (NumberFormatException e){
					numStvis=0;
					SageTV.apiUI(
							UIContext,
							"SetProperty",
							new Object[] {
								PROPERTIES_STVIS_NAME+"/num_items",
								new Integer(0)
							});
				}
			}
			if ( numStvis==0)
				return "No importable plugins configured";

			// loop getting STVI names
			stvis=new String[numStvis];
			for (int i = 0; i < numStvis; i++) {
				stvis[i]=(String) SageTV.apiUI(
						UIContext,
						"GetProperty",
						new Object[] {
								PROPERTIES_STVIS_NAME+"/"+i+"/name",
								""
						});
			}
		} catch (Throwable e) {
			if ( e.getCause()!=null)
				e=e.getCause();
			System.out.println("PluginManager.BuidCustomSTV: Failed getting "+PROPERTIES_NAME+" properties: "+e);
			return "Failed to get plugins configuration";
		}

		// next check readability of all stvs and stvi's
		File baseStvFile=new File(baseSTV);
		if ( ! baseStvFile.exists() || ! baseStvFile.canRead()) {
			return "Cannot read base STV "+baseStvFile;
		}
		File[] stviFiles=new File[numStvis];
		for (int i = 0; i < numStvis; i++) {
			if ( stvis[i].length()>0)
				stviFiles[i]=new File(stvis[i]);
			if ( stviFiles[i]==null
					|| ! stviFiles[i].exists()
					|| ! stviFiles[i].canRead()) {
				return "Cannot read STVI plugin file "+i+":"+stviFiles[i];
			}
		}

		// Ok, we have done as much checking as we can do, lets load the base STV

		try {
			displayMessage(UIContext, "Loading base STV\n"+baseStvFile.getName(), true, MESSAGEBOX_TIMEOUT);
			Object retval=SageTV.apiUI(
					UIContext,
					"LoadSTVFile",
					new Object[] {baseStvFile} );

			if ( ! retval.equals(Boolean.TRUE)) {
				return "Failed to read base STV file "+baseStvFile +" - "+retval;
			}

			// we are now in the base STV, any failure from here on in results in a revert...

			// import each and every stvi file, reporting it to the user
			for (int i = 0; i < stviFiles.length; i++) {

				displayMessage(UIContext, "Importing plugin\n"+stviFiles[i].getName(), true, MESSAGEBOX_TIMEOUT);

				// JEFF: this IF block could be replaced by Studio's import function
				// that does not save and reload the STV...
				retval=SageTV.apiUI(
						UIContext,
						"ImportSTVFile",
						new Object[] {stviFiles[i]} );
				if (! retval.equals(Boolean.TRUE)) {
					throw new Exception("Failed to import plugin: "+stviFiles+" - "+retval);
				}


				// import successful.. This should have created a new file
				// named base-nn. Wait a little for it to be released, then Delete it...
				Thread.sleep(500);
				String tmpSTV=(String) SageTV.apiUI(
						UIContext,
						"GetCurrentSTVFile",
						null);
				File tmpStvFile=new File(tmpSTV);
				System.out.println("PluginManager.BuidCustomSTV: deleting "+tmpSTV);
				// quick check that we are not deleteing the base STV!
				if ( ! tmpStvFile.equals(baseStvFile)){
					if ( !tmpStvFile.delete())
						// Delete failed, well, delete it later...
						tmpStvFile.deleteOnExit();
				}

			}


			// All STVI's imported, update all file timestamps in .properties
			SageTV.apiUI(
					UIContext,
					"SetProperty",
					new Object[] {
							PROPERTIES_BASESTV_NAME+"timestamp",
							new Long(baseStvFile.lastModified())
					});
			for (int i = 0; i < numStvis; i++) {
				SageTV.apiUI(
						UIContext,
						"SetProperty",
						new Object[] {
								PROPERTIES_STVIS_NAME+"/"+i+"/timestamp",
								new Long(stviFiles[i].lastModified())
						});
			}


			// Save the current STV with the defined name, and reload it
			displayMessage(UIContext, "Loading STV with plugins", true, MESSAGEBOX_TIMEOUT);

			String stvName;
			if ( baseSTV.endsWith(".xml") || baseSTV.endsWith(".stv") )
				stvName=baseSTV.substring(0, baseSTV.length()-4);
			else
				stvName=baseSTV;
			stvName=stvName+"_withimports_"+UIContext+".xml";
			File stvFile=new File(stvName);
			retval=SageTV.apiUI(
					UIContext,
					"SaveWidgetsAsXML",
					new Object[] { stvFile, Boolean.TRUE });
			if (!retval.equals(Boolean.TRUE)){
				throw new Exception("Failed to save STV file as: "+stvName);
			}

			// set custom STV filename property
			SageTV.apiUI(
					UIContext,
					"SetProperty",
					new Object[] {
							PROPERTIES_NAME+"stv_name",
							stvName
					});

			// before loading new STV file, clear global and theme variables
			SageTV.apiUI(
					UIContext,
					"AddGlobalContext",
					new Object[] {"gCurThemeLoaded",null} );
			SageTV.apiUI(
					UIContext,
					"AddGlobalContext",
					new Object[] {"gGlobalVarsAreSet",null} );


			// Load custom STV file.
			SageTV.apiUI(
					UIContext,
					"LoadSTVFile",
					new Object[] {stvFile} );

			if ( ! retval.equals(Boolean.TRUE)) {
				throw new Exception("Failed to read generated STV file "+stvFile +" - "+retval);
			}

		} catch ( Throwable e) {
			if ( e.getCause()!=null)
				e=e.getCause();
			System.out.println("PluginManager.BuidCustomSTV: Failed to create custom STV file: "+e);
			displayMessage(UIContext, "Failed to create custom STV file:\n"+e, true, 0);

			// revert to original STV.
			try {
				SageTV.apiUI(
						UIContext,
						"LoadSTVFile",
						new Object[] {new File(currentSTV)} );
			} catch (Throwable e1){
				if ( e1.getCause()!=null)
					e1=e1.getCause();
				System.out.println("PluginManager.BuidCustomSTV: Failed to to reload original STV file: "+e1);
			}

		}
		displayMessage(UIContext, "STV rebuilt with STVI plugins and reloaded", true, MESSAGEBOX_TIMEOUT);

		return Boolean.TRUE;
	}

	/**
	 * Private constructor -- cannot construct
	 */
	private PluginManager() {
	}

}
