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

// GUIInfoManager.cpp is where all the functions are defined for XBMC

// TODO List for controls:
// FadeLabel can have multiple info objects which it cycles through
// FadeLabel support in general
// label scrolling
// multiple images rendered for multiimage control
// angle (but we won't be supporting this anyways)
// camera
// selectbutton (but its rarely used, one example in MusicOSD)
// multiselect (rarely used, no examples yet)
// spincontrol, spincontrolex
// slider, sliderex
// for list & panel we don't support preloaditems
// fixedlist (rarely used, no examples)
// textbox paging & scrolling
// rss
// mover
// resizer
// pulseonselect is not supported
// hitrect may not be properly supported; we're not sure yet

// Default controls for each window type is another

// Linking in the dialogs is another

package sage.xbmc;

import java.io.*;
import java.util.Vector;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import sage.Widget;
import sage.WidgetFidget;
/**
 *
 * @author Narflex
 */
public class XBMCSkinParser implements sage.WidgetConstants
{
	public boolean FAST = false; // doesn't really matter; but writing OUR XML file takes about 4 seconds
	/*
	 * Default image filenames used by XBMC
defaultaudiobig.png
defaultaudio.png
defaultcddabig.png
defaultcdda.png
defaultdvdemptybig.png
defaultdvdempty.png
defaultdvdrombig.png
defaultdvdrom.png
defaultfolderbackbig.png
defaultfolderback.png
defaultfolderbig.png
defaultfolder.png
defaultharddiskbig.png
defaultharddisk.png
defaultlockedbig.png
defaultlocked.png
defaultnetworkbig.png
defaultnetwork.png
defaultpicturebig.png
defaultpicture.png
defaultplaylistbig.png
defaultplaylist.png
defaultprogrambig.png
defaultprogram.png
defaultshortcutbig.png
defaultshortcut.png
defaultvcdbig.png
defaultvcd.png
defaultvideobig.png
defaultvideo.png
defaultxboxdvdbig.png
defaultxboxdvd.png
	 */
	 public static void main(String[] args) throws Exception
	{
		File skinDir = new File(args[0]);
		new XBMCSkinParser(skinDir, new File(args[1]));
	}

	private static DocumentBuilderFactory docBuilderFac;
	static
	{
		docBuilderFac = DocumentBuilderFactory.newInstance();
		docBuilderFac.setValidating(false);
	}

	/** Creates a new instance of XBMCSkinParser */
	public XBMCSkinParser(File skinDir, File outputFile) throws Exception
	{
		this.skinDir = skinDir;
		focusableControlTypes.add("group");
		if (outputFile == null)
			FAST = true;
		long startTime = System.currentTimeMillis();
		windowIDMap.put("0", "Home".toLowerCase());
		windowIDMap.put("1", "MyPrograms".toLowerCase());
		windowAliasMap.put("programs", "myprograms");
		windowIDMap.put("2", "MyPics".toLowerCase());
		mediaWinIDs.add(new Integer(2));
		windowAliasMap.put("pictures", "mypics");
		windowAliasMap.put("mypictures", "mypics");
		windowIDMap.put("3", "FileManager".toLowerCase());
		windowAliasMap.put("files", "filemanager");
		windowIDMap.put("4", "Settings".toLowerCase());
		windowIDMap.put("5", "mymusicnav");
		mediaWinIDs.add(new Integer(5));
		windowAliasMap.put("music", "mymusicnav");
		windowAliasMap.put("musicnav", "mymusicnav");
		windowIDMap.put("6", "myvideo");
		mediaWinIDs.add(new Integer(6));
		windowAliasMap.put("video", "myvideo");
		windowAliasMap.put("videos", "myvideo");
		windowIDMap.put("7", "SettingsSystemInfo".toLowerCase());
		windowAliasMap.put("systeminfo", "settingssysteminfo");
		windowIDMap.put("10", "SettingsUICalibration".toLowerCase());
		windowAliasMap.put("guicalibration", "settingsuicalibration");
		windowIDMap.put("11", "SettingsScreenCalibration".toLowerCase());
		windowAliasMap.put("screencalibration", "settingsscreencalibration");
		//windowIDMap.put("12", "picturessettings");
		windowIDMap.put("12", "settingscategory");
		windowIDMap.put("13", "programssettings");
		windowIDMap.put("14", "weathersettings");
		windowIDMap.put("15", "musicsettings");
		windowIDMap.put("16", "systemsettings");
		windowIDMap.put("17", "videossettings");
		windowIDMap.put("18", "networksettings");
		windowIDMap.put("19", "appearancesettings");
		// TVSettings is 20 for the PVR branch, and scripts is 30
		windowIDMap.put("20", "MyScripts".toLowerCase());
		windowAliasMap.put("scripts", "myscripts");
		windowIDMap.put("25", "MyVideoNav".toLowerCase());
		mediaWinIDs.add(new Integer(25));
		windowAliasMap.put("videolibrary", "myvideonav");
		windowAliasMap.put("myvideolibrary", "myvideonav");
		windowIDMap.put("24", "MyVideo".toLowerCase());
		mediaWinIDs.add(new Integer(24));
		windowAliasMap.put("videofiles", "myvideo");
		windowAliasMap.put("myvideofiles", "myvideo");
		windowIDMap.put("28", "MyVideoPlaylist".toLowerCase());
		mediaWinIDs.add(new Integer(28));
		windowAliasMap.put("videoplaylist", "myvideoplaylist");
		windowIDMap.put("29", "LoginScreen".toLowerCase());
		windowAliasMap.put("logonscreen", "loginscreen");
		windowIDMap.put("34", "SettingsProfile".toLowerCase());
		windowAliasMap.put("profiles", "settingsprofile");
		windowIDMap.put("35", "MyGameSaves".toLowerCase());
		windowAliasMap.put("gamesaves", "mygamesaves");
		dialogIDMap.put("100", "DialogYesNo".toLowerCase());
		windowAliasMap.put("yesnodialog", "dialogyesno");
		dialogIDMap.put("101", "DialogProgress".toLowerCase());
		windowAliasMap.put("progressdialog", "dialogprogress");
		dialogIDMap.put("102", "DialogInvite".toLowerCase());
		windowAliasMap.put("invitedialog", "dialoginvite");
		dialogIDMap.put("103", "DialogKeyboard".toLowerCase());
		windowAliasMap.put("virtualkeyboard", "dialogkeyboard");
		dialogIDMap.put("104", "DialogVolumeBar".toLowerCase());
		windowAliasMap.put("volumebar", "dialogvolumebar");
		dialogIDMap.put("105", "DialogSubMenu".toLowerCase());
		windowAliasMap.put("submenu", "dialogsubmenu");
		dialogIDMap.put("106", "DialogContextMenu".toLowerCase());
		windowAliasMap.put("contextmenu", "dialogcontextmenu");
		dialogIDMap.put("107", "DialogKaiToast".toLowerCase());
		windowAliasMap.put("infodialog", "dialogkaitoast");
		dialogIDMap.put("108", "DialogHost".toLowerCase());
		windowAliasMap.put("hostdialog", "dialoghost");
		dialogIDMap.put("109", "DialogNumeric".toLowerCase());
		windowAliasMap.put("numericinput", "dialognumeric");
		dialogIDMap.put("110", "DialogGamepad".toLowerCase());
		windowAliasMap.put("gamepadinput", "dialoggamepad");
		dialogIDMap.put("111", "DialogButtonMenu".toLowerCase());
		windowAliasMap.put("shutdownmenu", "dialogbuttonmenu");
		dialogIDMap.put("112", "DialogMusicScan".toLowerCase());
		windowAliasMap.put("scandialog", "dialogmusicscan");
		dialogIDMap.put("113", "DialogMuteBug".toLowerCase());
		windowAliasMap.put("mutebug", "dialogmutebug");
		dialogIDMap.put("114", "PlayerControls".toLowerCase());
		dialogIDMap.put("115", "DialogSeekBar".toLowerCase());
		windowAliasMap.put("seekbar", "dialogseekbar");
		dialogIDMap.put("120", "MusicOSD".toLowerCase());
		dialogIDMap.put("121", "MusicOSDVisSettings".toLowerCase());
		windowAliasMap.put("visualisationsettings", "musicosdvissettings");
		dialogIDMap.put("122", "VisualisationPresetList".toLowerCase());
		windowAliasMap.put("visualizationpresetlist", "visualisationpresetlist");
		dialogIDMap.put("123", "VideoOSDSettings".toLowerCase());
		windowAliasMap.put("osd video settings", "videoosdsettings");
		dialogIDMap.put("124", "AudioOSDSettings".toLowerCase());
		windowAliasMap.put("audio osd settings", "audioosdsettings");
		dialogIDMap.put("125", "VideoOSDBookmarks".toLowerCase());
		windowAliasMap.put("video bookmarks", "videoosdbookmarks");
		dialogIDMap.put("126", "FileBrowser".toLowerCase());
		windowAliasMap.put("file browser", "filebrowser");
		dialogIDMap.put("127", "TrainerSettings".toLowerCase());
		dialogIDMap.put("128", "DialogNetworkSetup".toLowerCase());
		windowAliasMap.put("networksetup", "dialognetworksetup");
		dialogIDMap.put("129", "DialogMediaSource".toLowerCase());
		windowAliasMap.put("mediasource", "dialogmediasource");
		windowIDMap.put("130", "ProfileSettings".toLowerCase());
		windowIDMap.put("131", "LockSettings".toLowerCase());
		dialogIDMap.put("132", "DialogContentSettings".toLowerCase());
		windowAliasMap.put("contentsettings", "dialogcontentsettings");
		dialogIDMap.put("133", "DialogVideoScan".toLowerCase());
		// NOTE: There's a duplicate name of scandialog in there docs
		dialogIDMap.put("134", "DialogFavourites".toLowerCase());
		windowAliasMap.put("favourites", "dialogfavourites");
		dialogIDMap.put("135", "DialogSongInfo".toLowerCase());
		windowAliasMap.put("songinformation", "dialogsonginfo");
		dialogIDMap.put("136", "SmartPlaylistEditor".toLowerCase());
		dialogIDMap.put("137", "SmartPlaylistRule".toLowerCase());
		dialogIDMap.put("138", "DialogBusy".toLowerCase());
		windowAliasMap.put("busydialog", "dialogbusy");
		dialogIDMap.put("139", "DialogPictureInfo".toLowerCase());
		windowAliasMap.put("pictureinfo", "dialogpictureinfo");
		dialogIDMap.put("140", "DialogAddonSettings".toLowerCase());
		windowAliasMap.put("addonsettings", "DialogAddonSettings");
		dialogIDMap.put("141", "DialogAccessPoints".toLowerCase());
		dialogIDMap.put("142", "DialogFullScreenInfo".toLowerCase());
		windowAliasMap.put("fullscreeninfo", "dialogfullscreeninfo");
		dialogIDMap.put("143", "DialogKaraokeSongSelector".toLowerCase());
		windowAliasMap.put("karaokeselector", "dialogkaraokesongselector");
		dialogIDMap.put("144", "DialogKaraokeSongSelectorLarge".toLowerCase());
		windowAliasMap.put("karaokelargeselector", "dialogkaraokesongselectorlarge");
		dialogIDMap.put("145", "DialogSlider".toLowerCase());
		windowAliasMap.put("sliderdialog", "dialogslider");
		dialogIDMap.put("146", "DialogPVRUpdateProgressBar".toLowerCase());
		dialogIDMap.put("147", "DialogPluginSettings".toLowerCase());
		windowAliasMap.put("pluginsettings", "dialogpluginsettings");
		dialogIDMap.put("160", "DialogAddonBrowser".toLowerCase());
		windowAliasMap.put("addonbrowser", "dialogaddonbrowser");
		windowIDMap.put("500", "MyMusicPlaylist".toLowerCase());
		mediaWinIDs.add(new Integer(500));
		windowAliasMap.put("musicplaylist", "mymusicplaylist");
		windowIDMap.put("501", "MyMusicSongs".toLowerCase());
		mediaWinIDs.add(new Integer(501));
		windowAliasMap.put("musicfiles", "mymusicsongs");
		windowAliasMap.put("musicsongs", "mymusicsongs");
		windowAliasMap.put("mymusicfiles", "mymusicsongs");
		windowIDMap.put("502", "MyMusicNav".toLowerCase());
		mediaWinIDs.add(new Integer(502));
		windowAliasMap.put("musiclibrary", "mymusicnav");
		windowAliasMap.put("mymusiclibrary", "mymusicnav");
		windowAliasMap.put("mymusic", "mymusicnav");
		windowIDMap.put("503", "MyMusicPlaylistEditor".toLowerCase());
		windowAliasMap.put("musicplaylisteditor", "mymusicplaylisteditor");
		windowIDMap.put("1000", "VirtualKeyboard".toLowerCase());
		dialogIDMap.put("2000", "DialogSelect".toLowerCase());
		windowAliasMap.put("selectdialog", "dialogselect");
		dialogIDMap.put("2001", "DialogAlbumInfo".toLowerCase());
		windowAliasMap.put("musicinformation", "dialogalbuminfo");
		dialogIDMap.put("2002", "DialogOK".toLowerCase());
		windowAliasMap.put("okdialog", "dialogok");
		dialogIDMap.put("2003", "DialogVideoInfo".toLowerCase());
		windowAliasMap.put("movieinformation", "dialogvideoinfo");
		dialogIDMap.put("2004", "DialogScriptInfo".toLowerCase());
		windowAliasMap.put("scriptsdebuginfo", "dialogscriptinfo");
		windowIDMap.put("2005", "VideoFullScreen".toLowerCase());
		windowAliasMap.put("fullscreenvideo", "videofullscreen");
		windowIDMap.put("2006", "MusicVisualisation".toLowerCase());
		windowAliasMap.put("visualisation", "musicvisualisation");
		windowIDMap.put("2007", "SlideShow".toLowerCase());
		dialogIDMap.put("2008", "DialogFileStacking".toLowerCase());
		windowAliasMap.put("filestackingdialog", "dialogfilestacking");
		windowIDMap.put("2600", "MyWeather".toLowerCase());
		windowAliasMap.put("weather", "myweather");
		windowIDMap.put("2700", "MyBuddies".toLowerCase());
		windowAliasMap.put("xlinkkai", "mybuddies");
		windowIDMap.put("2900", "Screen Saver".toLowerCase());
		windowAliasMap.put("screensaver", "Screen Saver");
		dialogIDMap.put("2901", "VideoOSD".toLowerCase());
		windowIDMap.put("2902", "videomenu".toLowerCase());
		windowIDMap.put("2999", "Startup".toLowerCase());
		dialogIDMap.put("2903", "musicoverlay");
		dialogIDMap.put("2904", "videooverlay");

		windowIDMap.put("600", "mytv");
		windowAliasMap.put("tv", "mytv");
		dialogIDMap.put("601", "DialogPVRGuideInfo".toLowerCase());
		dialogIDMap.put("602", "DialogPVRRecordingInfo".toLowerCase());
		dialogIDMap.put("603", "DialogPVRTimerSettings".toLowerCase());
		dialogIDMap.put("604", "DialogPVRGroupManager".toLowerCase());
		dialogIDMap.put("605", "DialogPVRChannelManager".toLowerCase());
		dialogIDMap.put("606", "DialogPVRGuideSearch".toLowerCase());
		dialogIDMap.put("607", "DialogPVRChannelScan".toLowerCase()); // Unused in XBMC
		dialogIDMap.put("608", "DialogPVRUpdateProgress".toLowerCase()); // Unused in XBMC
		dialogIDMap.put("609", "DialogPVRChannelsOSD".toLowerCase());
		windowAliasMap.put("pvrosdchannels", "dialogpvrchannelsosd");
		dialogIDMap.put("610", "DialogPVRGuideOSD".toLowerCase());
		windowAliasMap.put("pvrosdguide", "dialogpvrguideosd");
		dialogIDMap.put("611", "DialogPVRDirectorOSD".toLowerCase());
		windowAliasMap.put("pvrosddirector", "dialogpvrdirectorosd");
		dialogIDMap.put("612", "DialogPVRCutterOSD".toLowerCase());
		windowAliasMap.put("pvrosdcutter", "dialogpvrcutterosd");
		// Window 613 is for teletext; but it doesn't have an XML file description for it

		java.util.Iterator walker = windowIDMap.entrySet().iterator();
		while (walker.hasNext())
		{
			java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
			windowNameToIDMap.put(ent.getValue(), ent.getKey());
		}
		walker = dialogIDMap.entrySet().iterator();
		while (walker.hasNext())
		{
			java.util.Map.Entry ent = (java.util.Map.Entry) walker.next();
			dialogNameToIDMap.put(ent.getValue(), ent.getKey());
		}

		File mainSkinFile = new File(skinDir, "skin.xml");
		if (!mainSkinFile.isFile())
			throw new IOException("Missing skin.xml file!");
		Document doc = docBuilderFac.newDocumentBuilder().parse(mainSkinFile);
		Element docRoot = doc.getDocumentElement();
		if (!docRoot.getNodeName().equals("skin"))
			throw new IOException("Missing main <skin> tag");
		System.out.println("root node name=" + docRoot.getNodeName());

		NodeList kids = docRoot.getChildNodes();
		int nodeLen = kids.getLength();
		for (int i = 0; i < nodeLen; i++)
		{
			Node currChild = kids.item(i);
			if (!(currChild instanceof Element))
				continue;
			String currNodeName = currChild.getNodeName();
			System.out.println("child node name=" + currNodeName);

			if ("defaultresolution".equals(currNodeName))
			{
				defaultResolution = currChild.getTextContent().trim();
				System.out.println("defaultResolution=" + defaultResolution);
			}
			else if ("defaultresolutionwide".equals(currNodeName))
			{
				defaultResolutionWide = currChild.getTextContent().trim();
				System.out.println("defaultResolutionWide=" + defaultResolutionWide);
			}
			else if ("defaultthemename".equals(currNodeName))
			{
				defaultTheme = currChild.getTextContent().trim();
				System.out.println("defaultThemeName=" + defaultTheme);
			}
			else if ("effectslowdown".equals(currNodeName))
			{
				effectslowdown = Float.parseFloat(currChild.getTextContent().trim());
				System.out.println("effectslowdown=" + effectslowdown);
			}
			else if ("version".equals(currNodeName))
			{
				version = currChild.getTextContent().trim();
				System.out.println("version=" + version);
			}
			else if ("zoom".equals(currNodeName))
			{
				zoom = Float.parseFloat(currChild.getTextContent().trim());
				System.out.println("zoom=" + zoom);
			}
			else if ("credits".equals(currNodeName))
			{
				NodeList skinnameKid = ((Element) currChild).getElementsByTagName("skinname");
				if (skinnameKid != null && skinnameKid.getLength() > 0)
				{
					skinName = skinnameKid.item(0).getTextContent().trim();
					System.out.println("skinName=" + skinName);
				}
				NodeList nameKids = ((Element) currChild).getElementsByTagName("name");
				if (nameKids != null)
				{
					skinCredits = "";
					for (int j = 0; j < nameKids.getLength(); j++)
					{
						skinCredits += nameKids.item(j).getTextContent().trim() + "\r\n";
					}
					System.out.println("skinCredits=" + skinCredits);
				}
			}
			else if ("startwindows".equals(currNodeName))
			{
				NodeList winKids = ((Element) currChild).getElementsByTagName("window");
				for (int j = 0; winKids != null && j < winKids.getLength(); j++)
				{
					Node currWin = winKids.item(j);
					String winName = currWin.getTextContent();
					try
					{
						int stringID = parseInt(winName);
						System.out.println("STRING ID LOOKUP NEEDED FOR " + stringID);
					}
					catch (NumberFormatException e){}
					windowIdToNameMap.put(new Integer(currWin.getAttributes().getNamedItem("id").getTextContent().trim()), winName);
				}
				System.out.println("Start window map=" + windowIdToNameMap);
			}
		}

		// Fix the case of the media subfolder if its different
		if (!(new File(skinDir, mediaPath).isFile()))
		{
			File[] folderNames = skinDir.listFiles();
			for (int i = 0; i < folderNames.length; i++)
			{
				if (folderNames[i].isDirectory() && folderNames[i].getName().equalsIgnoreCase("media"))
				{
					mediaPath = folderNames[i].getName();
					break;
				}
			}
		}

		// Now load the information for the default resolution
		defaultResDir = new File(skinDir, defaultResolution);

		// Now would be a good time to figure out the ID's of the custom windows so they will resolve correctly later
		String[] fileList = defaultResDir.list();
		for (int i = 0; fileList != null && i < fileList.length; i++)
		{
			if (fileList[i].toLowerCase().endsWith(".xml"))
			{
				winNameToFilenameMap.put(fileList[i].substring(0, fileList[i].length() - 4).toLowerCase(), fileList[i]);
				if (fileList[i].toLowerCase().startsWith("custom"))
				{
					File winFile = new File(defaultResDir, fileList[i]);
					System.out.println("Examing custom window file: " + winFile + "...");
					doc = docBuilderFac.newDocumentBuilder().parse(winFile);
					docRoot = doc.getDocumentElement();
					if (!docRoot.getNodeName().equals("window"))
					{
						System.out.println("Missing main <window> tag");
						continue;
					}
					System.out.println("root node name=" + docRoot.getNodeName());
					kids = docRoot.getChildNodes();
					String menuName = winFile.getName();
					menuName = menuName.substring(0, menuName.indexOf(".xml"));
					if (docRoot.getAttribute("id") != null && docRoot.getAttribute("id").length() > 0)
					{
						int winid = parseInt(docRoot.getAttribute("id"));
						System.out.println("Found custom window with id=" + winid);
						windowIDMap.put("" + winid, fileList[i].toLowerCase().substring(0, fileList[i].length() - 4));
						dialogIDMap.remove("" + winid); // Aeon custom dialogs match PVR IDs
						windowNameToIDMap.put(windowIDMap.get("" + winid), "" + winid);
					}
					else
					{
						System.out.println("Custom window missing ID parameter!");
					}
				}
			}
		}

		// Load the includes first
		File includeFile = new File(defaultResDir, winNameToFilenameMap.containsKey("includes") ? winNameToFilenameMap.get("includes").toString() : "Includes.xml");
		if (includeFile.isFile())
		{
			loadIncludes(includeFile);
			System.out.println("Done with includes load!");
			if (!FAST)
			{
				System.out.println("constant map=" + constantsMap);
				System.out.println("includes map=" + includeNameToNodeListMap);
				System.out.println("defaults map=" + defaultControlIncludes);
			}
		}

		if ((includeFile = new java.io.File(skinDir, "../../language/English/strings.xml")).isFile())
			loadStrings(includeFile);

		if ((includeFile = new java.io.File(skinDir, "language/English/strings.xml")).isFile())
			loadStrings(includeFile);

		if ((includeFile = new java.io.File(defaultResDir, winNameToFilenameMap.containsKey("font") ? winNameToFilenameMap.get("font").toString() : "Font.xml")).isFile())
			loadFonts(includeFile);

		if ((includeFile = new java.io.File(skinDir, "colors/Defaults.xml")).isFile())
			loadColors(includeFile);

		mgroup = tv.sage.ModuleManager.newModuleGroup();
		mgroup.defaultModule.setBatchLoad(true);

		// Import the base STV we use from our codebase
		java.io.File xbmcBase = new java.io.File("XBMCBase.xml");
		if (!xbmcBase.isFile())
			xbmcBase = new java.io.File("C:\\dev\\src\\stvs\\XBMC\\XBMCBase.xml");
		mgroup.importXML(xbmcBase, null);

		java.util.Set baseWidgets = new java.util.HashSet();
		baseWidgets.addAll(java.util.Arrays.asList(mgroup.getWidgets()));

		dialogOrganizer = mgroup.addWidget(THEME);
		WidgetFidget.setName(dialogOrganizer, "DIALOG ORGANIZER");

		// Setup the default size for scaling insets
		rezWidth = "720";
		rezHeight = "576";
		if (defaultResolution.equals("720p"))
		{
			rezWidth = "1280";
			rezHeight = "720";
		}
		else if (defaultResolution.equals("1080i"))
		{
			rezWidth = "1920";
			rezHeight = "1080";
		}
		else if (defaultResolution.equalsIgnoreCase("NTSC"))
		{
			rezWidth = "720";
			rezHeight = "480";
		}

		// Case/filename issues for default images
		String[] imageFiles = new File(skinDir, mediaPath).list();
		for (int i = 0; i < imageFiles.length; i++)
		{
			String lcName = imageFiles[i].toLowerCase();
			if (lcName.startsWith("default") && lcName.endsWith(".png"))
			{
				String subName = lcName.substring(7, lcName.length() - 4);
				if (subName.endsWith("big"))
					subName = subName.substring(0, subName.length() - 3);
				defaultImageMap.put(subName, "\"" + mediaPath + "/" + imageFiles[i] + "\"");
			}
		}

		addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(
			null, HOOK, "ApplicationStarted"),
			ACTION, "SetProperty(\"ui/scaling_insets_base_height\", " + rezHeight + ")"),
			ACTION, "SetProperty(\"ui/scaling_insets_base_width\", " + rezWidth + ")"),
			ACTION, "SetProperty(\"ui/default_hdd_icon\", " + defaultImageMap.get("harddisk") + ")"),
			ACTION, "SetProperty(\"ui/default_folder_icon\", " + defaultImageMap.get("folder") + ")"),
			ACTION, "SetProperty(\"xbmc/system/settings/videolibrary.enabled\", true)"),
			ACTION, "AddGlobalContext(\"CircFocusCheck\", new_java_util_HashSet())"),
			ACTION, "AddGlobalContext(\"XBMCSkinName\", \"" + skinName + "\")"),
			ACTION, "SetProperty(\"xbmc/system/settings/input.enablemouse\", IsDesktopUI())"),
			ACTION, "SetProperty(\"xbmc/system/settings/pvrmanager.enabled\", \"true\")"),
			ACTION, "\"XOUT: ApplicationStarted\"");

		viewValidationRoot = addWidgetNamed(addWidgetNamed(null, ACTION, "\"XIN: ValidateContainerView\""), CONDITIONAL, "GetWidgetName(GetCurrentMenuWidget())");

		// Now load the window XML files
		loadWindow(new File(defaultResDir, "Home.xml"));

		// Now go through the map and load any other known window IDs which we haven't encountered yet
		loadRemainingWindows(windowIDMap);
		loadRemainingWindows(dialogIDMap);

		Widget globalTheme = mgroup.addWidget(THEME);
		WidgetFidget.setName(globalTheme, "Global");
		WidgetFidget.contain(addWidgetNamed(globalTheme, LISTENER, "Home"), resolveMenuWidget("home"));
		addWidgetNamed(addWidgetNamed(addWidgetNamed(globalTheme, LISTENER, "Stop"), ACTION, "CloseAndWaitUntilClosed()"), ACTION, "Refresh()");
		addAttribute(globalTheme, "XBMCCompatability", "true");
		addAttribute(globalTheme, "AllowHiddenFocus", "true");
		addAttribute(globalTheme, "SingularMouseTransparency", "true");
		addAttribute(globalTheme, "DisableParentClip", "true");

		Widget globalHook = mgroup.addWidget(HOOK);
		WidgetFidget.setName(globalHook, "MediaPlayerPlayStateChanged");
		addWidgetNamed(globalHook, ACTION, "Refresh()");

		globalHook = mgroup.addWidget(HOOK);
		WidgetFidget.setName(globalHook, "MediaPlayerSeekCompleted");
		addWidgetNamed(addWidgetNamed(globalHook, ACTION, "AddGlobalContext(\"LastSeekCompleteTime\", Time())"), ACTION, "Refresh()");

		globalHook = mgroup.addWidget(HOOK);
		WidgetFidget.setName(globalHook, "MediaPlayerFileLoadComplete");
		addWidgetNamed(globalHook, ACTION, "Refresh()");

		// Now link the base widgets up with the generated STV
		java.util.Set completeWidgets = new java.util.HashSet();
		completeWidgets.addAll(java.util.Arrays.asList(mgroup.getWidgets()));
		completeWidgets.removeAll(baseWidgets);

		// The linking rules are we find anything in the generated STV that has an action with name:
		// "XOUT: Tag" and then try to find a corresponding "XIN: Tag" in the base; we also do the opposite
		// as well. When matching pairs are found the XOUT contains the XIN widget. Multiple XOUT points can exist for a Tag; and also
		// multiple XIN points can exist for any Tag. Each XOUT will be linked to all of the XINs. Menu & dialog names may be linked from the base widgets by using a "XMENU: Tag" where the
		// Tag is the dialog/menu name. Other Widget typese can also be used as well; but for all other types the XIN widget is not linked to
		// and instead all of its children are linked to. The IN widget is linked under a 'XIN Widgets' theme organizer.
		java.util.Map baseWidgetInputs = new java.util.HashMap();
		java.util.Map genWidgetInputs = new java.util.HashMap();
		java.util.Vector baseWidgetOutputs = new java.util.Vector();
		java.util.Vector genWidgetOutputs = new java.util.Vector();
		walker = baseWidgets.iterator();
		while (walker.hasNext())
		{
			Widget w = (Widget) walker.next();
			if (w.getName().startsWith("\"XIN:"))
			{
				String key = w.getName().substring(5, w.getName().length() - 1).trim();
				java.util.Vector oldVec = (java.util.Vector) baseWidgetInputs.get(key);
				if (oldVec == null)
					baseWidgetInputs.put(key, oldVec = new java.util.Vector());
				oldVec.add(w);
			}
			else if (w.getName().startsWith("\"XOUT:"))
				baseWidgetOutputs.add(w);
			else if (w.type() == ACTION && w.getName().startsWith("\"XMENU:"))
			{
				// Try to link the menus immediately
				Widget menWidg = resolveMenuWidget(w.getName().substring(7, w.getName().length() - 1).trim());
				if (menWidg != null)
					WidgetFidget.contain(w, menWidg);
			}
		}

		walker = completeWidgets.iterator();
		while (walker.hasNext())
		{
			Widget w = (Widget) walker.next();
			if (w.getName().startsWith("\"XIN:"))
			{
				String key = w.getName().substring(5, w.getName().length() - 1).trim();
				java.util.Vector oldVec = (java.util.Vector) genWidgetInputs.get(key);
				if (oldVec == null)
					genWidgetInputs.put(key, oldVec = new java.util.Vector());
				oldVec.add(w);
			}
			else if (w.getName().startsWith("\"XOUT:"))
				genWidgetOutputs.add(w);
		}

		// Now try to link every output with its corresponding input
		for (int i = 0; i < genWidgetOutputs.size(); i++)
		{
			Widget w = (Widget) genWidgetOutputs.get(i);
			String linkName = w.getName().substring(6, w.getName().length() - 1).trim();
			java.util.Vector linkers = (java.util.Vector) baseWidgetInputs.get(linkName);
			if (linkers != null)
			{
				for (int j = 0; j < linkers.size(); j++)
				{
					Widget linker = (Widget) linkers.get(j);
					if (linker.type() == w.type())
					{
						if (w.type() == ACTION)
							WidgetFidget.contain(w, linker);
						else
						{
							Widget[] inKids = linker.contents();
							for (int k = 0; k < inKids.length; k++)
							{
								WidgetFidget.contain(w, inKids[k]);
							}
						}
					}
					else
					{
						System.out.println("ERROR Incompatible types for Widget linking: " + linkName);
					}
				}
			}
			else
			{
				System.out.println("ERROR Undefined link to base widgets with tag: " + linkName);
			}
		}

		for (int i = 0; i < baseWidgetOutputs.size(); i++)
		{
			Widget w = (Widget) baseWidgetOutputs.get(i);
			String linkName = w.getName().substring(6, w.getName().length() - 1).trim();
			java.util.Vector linkers = (java.util.Vector) genWidgetInputs.get(linkName);
			if (linkers != null)
			{
				for (int j = 0; j < linkers.size(); j++)
				{
					Widget linker = (Widget) linkers.get(j);
					if (linker.type() == w.type())
					{
						if (w.type() == ACTION)
							WidgetFidget.contain(w, linker);
						else
						{
							Widget[] inKids = linker.contents();
							for (int k = 0; k < inKids.length; k++)
							{
								WidgetFidget.contain(w, inKids[k]);
							}
						}
					}
					else
					{
						System.out.println("ERROR Incompatible types for Widget linking: " + linkName);
					}
				}
			}
			else
			{
				System.out.println("ERROR Undefined link to generated widgets with tag: " + linkName);
			}
		}

		mgroup.defaultModule.setBatchLoad(false);
		System.out.println("XBMC Load took " + (System.currentTimeMillis() - startTime) + " msec");
		if (outputFile != null)
		{
			mgroup.defaultModule.saveXML(outputFile, "SageTV Generated " + skinName);
			System.out.println("Saved the STV file to: " + outputFile);
		}

/*		if (!FAST)
		{
			java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter("exprdump.txt")));
			java.util.TreeMap tree = new java.util.TreeMap(exprDump);
			walker = tree.entrySet().iterator();
			while (walker.hasNext())
			{
				java.util.Map.Entry ent = (java.util.Map.Entry)walker.next();
				pw.println(ent.getKey() + "=" + ent.getValue());
			}
			pw.close();
		}*/
	}

	public tv.sage.mod.Module getModule()
	{
		return mgroup.defaultModule;
	}

	private void loadRemainingWindows(java.util.Map nameValueMap) throws Exception
	{
		java.util.Iterator walker = nameValueMap.values().iterator();
		while (walker.hasNext())
		{
			String currName = (String) walker.next();
			if (windowWidgMap.containsKey(currName))
				continue; // window already loaded
			if (winNameToFilenameMap.containsKey(currName.toLowerCase()))
				currName = winNameToFilenameMap.get(currName.toLowerCase()).toString();
			if (new File(defaultResDir, currName).isFile())
			{
				System.out.println("Loading unreferenced window file of:" + currName);
				loadWindow(new File(defaultResDir, currName));
			}
			else
				System.out.println("Window was not loaded but is in main map: " + currName);
		}
	}

	private void loadIncludes(File includeFile) throws Exception
	{
		System.out.println("Loading " + includeFile + "...");
		java.io.BufferedInputStream bis = null;
		try
		{
			bis = new java.io.BufferedInputStream(new java.io.FileInputStream(includeFile));
			Document doc = docBuilderFac.newDocumentBuilder().parse(bis);
			Element docRoot = doc.getDocumentElement();
			if (!docRoot.getNodeName().equals("includes"))
				throw new IOException("Missing main <includes> tag");
			System.out.println("root node name=" + docRoot.getNodeName());
			NodeList kids = docRoot.getChildNodes();
			int numKids = kids.getLength();
			for (int i = 0; i < numKids; i++)
			{
				Node currKid = kids.item(i);
				if (!(currKid instanceof Element))
					continue;
				if ("include".equals(currKid.getNodeName()))
				{
					Node fileAttr = currKid.getAttributes().getNamedItem("file");
					if (fileAttr != null)
					{
						loadIncludes(new File(includeFile.getParentFile(), fileAttr.getTextContent().trim()));
					}
					else
					{
						includeNameToNodeListMap.put(currKid.getAttributes().getNamedItem("name").getTextContent().trim(),
							currKid.getChildNodes());
					}
				}
				else if ("constant".equals(currKid.getNodeName()))
				{
					constantsMap.put(currKid.getAttributes().getNamedItem("name").getTextContent().trim(),
						currKid.getTextContent().trim());
				}
				else if ("default".equals(currKid.getNodeName()))
				{
					defaultControlIncludes.put(currKid.getAttributes().getNamedItem("type").getTextContent().trim(),
						currKid.getChildNodes());
				}
			}

			System.out.println("Finished loading includes from: " + includeFile);
		}
		catch (Throwable t)
		{
			System.out.println("ERROR loading include file of:" + t);
			t.printStackTrace();
		}
		finally
		{
			if (bis != null)
				bis.close();
		}
	}

	private void loadStrings(File includeFile) throws Exception
	{
		System.out.println("Loading " + includeFile + "...");
		Document doc = docBuilderFac.newDocumentBuilder().parse(includeFile);
		Element docRoot = doc.getDocumentElement();
		if (!docRoot.getNodeName().equals("strings"))
			throw new IOException("Missing main <strings> tag");
		System.out.println("root node name=" + docRoot.getNodeName());
		NodeList kids = docRoot.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++)
		{
			Node currKid = kids.item(i);
			if (!(currKid instanceof Element))
				continue;
			if ("string".equals(currKid.getNodeName()))
			{
				stringMap.put(currKid.getAttributes().getNamedItem("id").getTextContent().trim(),
					currKid.getTextContent().trim());
			}
		}

		System.out.println("Finished loading includes from: " + includeFile);
	}

	private void loadColors(File includeFile) throws Exception
	{
		System.out.println("Loading " + includeFile + "...");
		Document doc = docBuilderFac.newDocumentBuilder().parse(includeFile);
		Element docRoot = doc.getDocumentElement();
		if (!docRoot.getNodeName().equals("colors"))
			throw new IOException("Missing main <colors> tag");
		System.out.println("root node name=" + docRoot.getNodeName());
		NodeList kids = docRoot.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++)
		{
			Node currKid = kids.item(i);
			if (!(currKid instanceof Element))
				continue;
			if ("color".equals(currKid.getNodeName()))
			{
				colorMap.put(currKid.getAttributes().getNamedItem("name").getTextContent().trim(),
					currKid.getTextContent().trim());
			}
		}

		System.out.println("Finished loading includes from: " + includeFile);
	}

	private void loadFonts(File includeFile) throws Exception
	{
		System.out.println("Loading " + includeFile + "...");
		Document doc = docBuilderFac.newDocumentBuilder().parse(includeFile);
		Element docRoot = doc.getDocumentElement();
		if (!docRoot.getNodeName().equals("fonts"))
			throw new IOException("Missing main <fonts> tag");
		System.out.println("root node name=" + docRoot.getNodeName());
		NodeList kids = docRoot.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++)
		{
			Node currKid = kids.item(i);
			if (!(currKid instanceof Element))
				continue;
			if ("fontset".equals(currKid.getNodeName())/* && "default".equalsIgnoreCase(currKid.getAttributes().getNamedItem("id").getTextContent().trim())*/)
			{
				System.out.println("Default fontset found; loading it now...");
				NodeList allFonts = ((Element)currKid).getElementsByTagName("font");
				for (int j = 0; j < allFonts.getLength(); j++)
				{
					Node fontItem = allFonts.item(j);
					FontData fd = new FontData();
					fd.fontName = getChildNodeNamed(fontItem, "name").getTextContent().trim();
					fd.fontPath = "fonts/" + getChildNodeNamed(fontItem, "filename").getTextContent().trim();
//					if (fontFile.indexOf('.') != -1)
//						fontFile = fontFile.substring(0, fontFile.indexOf('.'));
					fd.size = "20";
					fd.style = "Plain";
					Node tempNode = getChildNodeNamed(fontItem, "size");
					if (tempNode != null)
						fd.size = tempNode.getTextContent().trim();
					tempNode = getChildNodeNamed(fontItem, "style");
					if (tempNode != null)
					{
						String styleName = tempNode.getTextContent().trim();
						if ("bold".equalsIgnoreCase(styleName))
							fd.style = "Bold";
						else if ("italics".equalsIgnoreCase(styleName))
							fd.style = "Italic";
						else if ("bolditalics".equalsIgnoreCase(styleName))
							fd.style = "BoldItalic";
					}
					fontMap.put(fd.fontName, fd);
				}
				break;
			}
		}

		if (!FAST)
			System.out.println("Finished loading fonts from: " + includeFile + " map=" + fontMap);
	}

	private void resolveIncludes(Element theNode, int currWinID) throws Exception
	{
		NodeList kids = theNode.getChildNodes();
		for (int i = 0; kids != null && i < kids.getLength(); i++)
		{
			Node currKid = kids.item(i);
			if (currKid instanceof Element && "include".equals(currKid.getNodeName()))
			{
				String translatedCond = null;
				if (((Element) currKid).hasAttribute("condition"))
				{
					translatedCond = translateBooleanExpression(((Element) currKid).getAttribute("condition"), null, currWinID);
					sage.Catbert.Context winVarContext = new sage.Catbert.Context();
					winVarContext.set("IsMediaWindow", Boolean.valueOf(mediaWinIDs.contains(new Integer(currWinID))));
					try
					{
						Object rez = sage.Catbert.evaluateExpression(translatedCond, winVarContext, null, null);
//System.out.println("INCLUDE CONDITION original=" + ((Element) currKid).getAttribute("condition") + " trans=" + translatedCond + " rez=" + rez);
						if (rez == null || !rez.toString().equals("true"))
							continue;
					}
					catch (Throwable e)
					{
						System.out.println("ERROR with expression evaluation of: " + translatedCond + " Error=" + e);
						e.printStackTrace();
						continue;
					}
				}
				NodeList includers = (NodeList) includeNameToNodeListMap.get(currKid.getTextContent().trim());
				if (includers == null)
				{
					System.out.println("ERROR missing include: " + currKid.getTextContent().trim());
				}
				else
				{
					//System.out.println("processing include for " + currKid.getTextContent().trim());
					for (int k = 0; k < includers.getLength(); k++)
					{
						Node newNode = theNode.insertBefore(currKid.getOwnerDocument().importNode(includers.item(k), true), currKid);
// ENABLE THIS TO HELP DEBUG CONDITIONAL INCLUDES
//						if (translatedCond != null && newNode instanceof Element)
//							((Element) newNode).setAttribute("condition", translatedCond);
					}
					theNode.removeChild(currKid);
					i--;
				}
			}
			else if (currKid instanceof Element)
				resolveIncludes((Element) currKid, currWinID);
		}
	}

	private Widget loadWindow(File windowFile) throws Exception
	{
		System.out.println("Loading " + windowFile + "...");
		Document doc = docBuilderFac.newDocumentBuilder().parse(windowFile);
		Element docRoot = doc.getDocumentElement();
		if (!docRoot.getNodeName().equals("window"))
			throw new IOException("Missing main <window> tag");
		System.out.println("root node name=" + docRoot.getNodeName());
		NodeList kids = docRoot.getChildNodes();
		Window winny = new Window();
		String menuName = windowFile.getName();
		if (!menuName.startsWith("Home"))
			menuName = menuName.toLowerCase();
		menuName = menuName.substring(0, menuName.indexOf(".xml"));
		winny.menuName = menuName;
		String lcMenuName = menuName.toLowerCase();
		// Use the XBMC window ID over the one in the XML file if we know what its supposed to be for this window
		if (windowNameToIDMap.containsKey(lcMenuName))
			winny.id = parseInt(resolveWindowID("" + parseInt(windowNameToIDMap.get(lcMenuName).toString())));
		if (winny.id < 0 && docRoot.getAttribute("id") != null && docRoot.getAttribute("id").length() > 0)
			winny.id = parseInt(resolveWindowID("" + parseInt(docRoot.getAttribute("id"))));
		if (docRoot.getAttribute("type") != null && docRoot.getAttribute("type").length() > 0)
			winny.windowType = docRoot.getAttribute("type");

		// Process all of the includes recursively now
		if (winny.id >= 0)
			resolveIncludes(docRoot, winny.id);
		int kidLen = kids.getLength();
		for (int i = 0; i < kidLen; i++)
		{
			Node currKid = kids.item(i);
			if (!(currKid instanceof Element))
				continue;
			String currName = currKid.getNodeName();
			if ("id".equals(currName) && winny.id < 0)
			{
				boolean doIncludesNow = (winny.id < 0);
				winny.id = parseInt(resolveWindowID("" + parseInt(currKid.getTextContent().trim())));
				if (doIncludesNow)
					resolveIncludes(docRoot, winny.id);
			}
			else if ("defaultcontrol".equals(currName))
			{
				try
				{
					winny.defaultControl = parseInt(currKid.getTextContent().trim());

					winny.defaultAlways = currKid.getAttributes() != null && currKid.getAttributes().getNamedItem("always") != null &&
						evalBool(currKid.getAttributes().getNamedItem("always").getTextContent().trim());
				}
				catch (NumberFormatException nfe)
				{
					System.out.println("Badly formatted defaultControl=" + currKid.getTextContent().trim());
				}
			}
			else if ("allowoverlay".equals(currName))
			{
				winny.allowOverlay = evalBoolObj(currKid.getTextContent().trim());
			}
			else if ("type".equals(currName))
			{
				winny.windowType = currKid.getTextContent().trim();
			}
			else if ("visible".equals(currName))
			{
				if (winny.visibles == null)
					winny.visibles = new java.util.Vector();
				winny.visibles.add(currKid.getTextContent().trim());
			}
			else if ("zorder".equals(currName))
			{
				winny.zorder = parseInt(currKid.getTextContent().trim());
			}
			else if ("coordinates".equals(currName))
			{
				NodeList systemKid = ((Element) currKid).getElementsByTagName("system");
				if (systemKid != null && systemKid.getLength() > 0)
				{
					winny.systemCoords = "1".equals(systemKid.item(0).getTextContent().trim());
				}
				NodeList posKid = ((Element) currKid).getElementsByTagName("posx");
				if (posKid != null && posKid.getLength() > 0)
				{
					winny.coordPosX = parseInt(posKid.item(0).getTextContent().trim());
				}
				posKid = ((Element) currKid).getElementsByTagName("posy");
				if (posKid != null && posKid.getLength() > 0)
				{
					winny.coordPosY = parseInt(posKid.item(0).getTextContent().trim());
				}
				NodeList originKids = ((Element) currKid).getElementsByTagName("origin");
				if (originKids != null)
				{
					// handle origins....
				}
			}
			else if ("previouswindow".equals(currName))
			{
				winny.prevWindow = currKid.getTextContent().trim();
			}
			else if ("controls".equals(currName))
			{
				// Parse the controls!
				NodeList controls = currKid.getChildNodes();
				int controlsLen = controls.getLength();
				for (int j = 0; j < controlsLen; j++)
				{
					if (controls.item(j) instanceof Element)
					{
						if ("control".equals(controls.item(j).getNodeName()))
						{
							Control c = parseControl((Element)controls.item(j), winny, null);
							if (c != null)
							{
								winny.controls.add(c);
							}
						}
						else
						{
							System.out.println("UNKNOWN TAG IN controls GROUP FOR A WINDOW of:" + controls.item(j).getNodeName());
						}
					}
				}
			}
			else if ("views".equals(currName))
			{
				winny.forcedViews = new java.util.Vector();
				java.util.StringTokenizer toker = new java.util.StringTokenizer(currKid.getTextContent().trim(), ",");
				while (toker.hasMoreTokens())
					winny.forcedViews.add(toker.nextToken());
			}
			else if ("animation".equals(currName))
			{
				if (winny.anims == null)
					winny.anims = new java.util.Vector();
				Element tempElem = (Element) currKid;
				NodeList innerAnimList = null;
				int innerAnimIndex = 0;
				String commonAnimType = null;
				String commonCondition = null;
				boolean commonReversible = true;
				int innerAnimLen = 0;
				if (tempElem.hasAttribute("type"))
				{
					commonAnimType = tempElem.getAttribute("type").toLowerCase();
					if (tempElem.hasAttribute("condition"))
						commonCondition = tempElem.getAttribute("condition");
					if (tempElem.hasAttribute("reversible"))
						commonReversible = Boolean.parseBoolean(tempElem.getAttribute("reversible"));
					innerAnimList = tempElem.getElementsByTagName("effect");
					innerAnimLen = innerAnimList.getLength();
				}
				do
				{
					AnimData newAnim = new AnimData();
					winny.anims.add(newAnim);
					if (innerAnimList != null)
					{
						tempElem = (Element) innerAnimList.item(innerAnimIndex);
						newAnim.trigger = commonAnimType;
						newAnim.effect = tempElem.getAttribute("type");
						newAnim.condition = commonCondition;
						newAnim.reversible = commonReversible;
					}
					else
					{
						newAnim.trigger = currKid.getTextContent().trim().toLowerCase();
						newAnim.effect = tempElem.getAttribute("effect");
						if (tempElem.hasAttribute("condition"))
							newAnim.condition = tempElem.getAttribute("condition");
						if (tempElem.hasAttribute("reversible"))
							newAnim.reversible = Boolean.parseBoolean(tempElem.getAttribute("reversible"));
					}
					if (newAnim.effect != null)
						newAnim.effect = newAnim.effect.toLowerCase();
					if (tempElem.hasAttribute("time"))
						newAnim.time = parseInt(tempElem.getAttribute("time"));
					if (tempElem.hasAttribute("delay"))
						newAnim.delay = parseInt(tempElem.getAttribute("delay"));
					if (tempElem.hasAttribute("start"))
					{
						java.util.StringTokenizer toker = new java.util.StringTokenizer(tempElem.getAttribute("start"), ",");
						newAnim.start = new int[toker.countTokens()];
						for (int q = 0; toker.hasMoreTokens(); q++)
							newAnim.start[q] = parseInt(toker.nextToken());
					}
					if (tempElem.hasAttribute("end"))
					{
						java.util.StringTokenizer toker = new java.util.StringTokenizer(tempElem.getAttribute("end"), ",");
						newAnim.end = new int[toker.countTokens()];
						for (int q = 0; toker.hasMoreTokens(); q++)
							newAnim.end[q] = parseInt(toker.nextToken());
					}
					if (tempElem.hasAttribute("acceleration"))
						newAnim.acceleration = tempElem.getAttribute("acceleration");
					if (tempElem.hasAttribute("center"))
					{
						String startStr = tempElem.getAttribute("center");
						int idx = startStr.indexOf(',');
						if (idx != -1)
						{
							newAnim.center = new int[2];
							newAnim.center[0] = parseInt(startStr.substring(0, idx));
							newAnim.center[1] = parseInt(startStr.substring(idx + 1));
						}
						else
						{
							try
							{
								newAnim.center = new int[] { parseInt(startStr) };
							}
							catch (NumberFormatException nfe){}
						}
						/*else if (startStr.equalsIgnoreCase("auto"))
						{
							newAnim.center = new int[] { -1, -1 };
						}*/
					}
					if (tempElem.hasAttribute("pulse"))
						newAnim.pulse = Boolean.parseBoolean(tempElem.getAttribute("pulse"));
					if (tempElem.hasAttribute("tween"))
						newAnim.tween = tempElem.getAttribute("tween");
					if (tempElem.hasAttribute("easing"))
						newAnim.easing = tempElem.getAttribute("easing").toLowerCase();
					innerAnimIndex++;
				} while (innerAnimList != null && innerAnimIndex < innerAnimLen);
			}
			else
			{
				System.out.println("UNKONWN TAG INSIDE OF WINDOW of " + currName);
			}
		}
		if (winny.id > 10000)
			winny.id -= 10000;

		// Now build the widget for this menu
		Widget menuWidget;
		if (windowWidgMap.containsKey(lcMenuName))
			menuWidget = (Widget) windowWidgMap.get(lcMenuName);
		else
		{
			if ("dialogseekbar".equals(lcMenuName) || "musicoverlay".equals(lcMenuName) || "videooverlay".equals(lcMenuName))
			{
				menuWidget = mgroup.addWidget(PANEL);
				WidgetFidget.setProperty(menuWidget, Z_OFFSET, "100");
			}
			else if ("buttonmenu".equalsIgnoreCase(winny.windowType) || "dialog".equalsIgnoreCase(winny.windowType) ||
				dialogIDMap.containsKey("" + winny.id))
				menuWidget = mgroup.addWidget(OPTIONSMENU);
			else
				menuWidget = mgroup.addWidget(MENU);
			windowWidgMap.put(lcMenuName, menuWidget);
			widgToWindowObjMap.put(menuWidget, winny);
		}
		winny.menuWidget = menuWidget;
		if (winny.id >= 0)
			addAttribute(menuWidget, "MenuXBMCID", resolveWindowID("" + winny.id));
		if (winny.defaultControl >= 0)
		{
			Widget hooky = addWidgetNamed(menuWidget, HOOK, "MenuNeedsDefaultFocus");
			addWidgetNamed(hooky, ACTION, getSetFocusExpr(winny.defaultControl + ""));
		}
		if (menuWidget.type() == OPTIONSMENU)
			WidgetFidget.contain(dialogOrganizer, menuWidget);

		if (winny.visibles != null)
		{
			for (int i = 0; i < winny.visibles.size(); i++)
			{
				Widget newCond = mgroup.addWidget(CONDITIONAL);
				WidgetFidget.setName(newCond, translateBooleanExpression(winny.visibles.get(i).toString(), null));
				if (winny.targetParent == null)
				{
					winny.targetParent = newCond;
					WidgetFidget.contain(newCond, menuWidget);
				}
				else
				{
					WidgetFidget.contain(newCond, winny.targetParent);
					winny.targetParent = newCond;
				}
			}
		}

		if (winny.anims != null)
			processAnimations(winny.anims, menuWidget, null, 0, 0, Integer.parseInt(rezWidth), Integer.parseInt(rezHeight));

		if (winny.prevWindow != null)
		{
			Widget listener = addWidgetNamed(menuWidget, LISTENER, "Back");
			WidgetFidget.contain(listener, resolveMenuWidget(winny.prevWindow));
		}
		if (menuWidget.type() != MENU)
		{
			WidgetFidget.setProperty(menuWidget, ANCHOR_X, winny.systemCoords ? ("" + winny.coordPosX) : "0.5");
			WidgetFidget.setProperty(menuWidget, ANCHOR_Y, winny.systemCoords ? ("" + winny.coordPosY) : "0.5");
			// These clear the context from where the options menu was launched
//			addAttribute(menuWidget, "RetainedFocusItemXBMCID", "null");
//			addAttribute(menuWidget, "RetainedFocusParentDashItemXBMCID", "null");
		}
		else
		{
			if (menuTheme == null)
			{
				menuTheme = addWidgetNamed(menuWidget, THEME, "MenuTheme");
				WidgetFidget.setProperty(menuTheme, BACKGROUND_COLOR, "0x000000");

				// See if we have music/video overlays to put as part of the general menu theme
				Widget musicOverlayWidg = resolveMenuWidget("musicoverlay");
				Widget videoOverlayWidg = resolveMenuWidget("videooverlay");
				if (musicOverlayWidg != null || videoOverlayWidg != null)
				{
					Widget rootCond = addWidgetNamed(addWidgetNamed(menuTheme, MENU, "OverlayMenus"), CONDITIONAL, "IsMediaWindow");
					if (musicOverlayWidg != null)
					{
						WidgetFidget.contain(addWidgetNamed(rootCond, CONDITIONAL, "IsCurrentMediaFileMusic()"), musicOverlayWidg);
					}
					if (videoOverlayWidg != null)
					{
						WidgetFidget.contain(addWidgetNamed(rootCond, CONDITIONAL, "DoesCurrentMediaFileHaveVideo()"), videoOverlayWidg);
					}
				}
			}
			else
				WidgetFidget.contain(menuWidget, menuTheme);
		}
//		if (menuWidget.type() == OPTIONSMENU)
//			addWidgetNamed(addWidgetNamed(menuWidget, LISTENER, "Info"), ACTION, "CloseOptionsMenu()");

		WidgetFidget.setName(menuWidget, "Home".equalsIgnoreCase(menuName) ? "Main Menu" : menuName);

		if ("home".equals(lcMenuName))
		{
			// Ensure the weather is updated in the background from the main menu to prevent delays in the foreground
			addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "AfterMenuLoad"), ACTION, "Fork()"),
				ACTION, "tv_sage_weather_WeatherDotCom_updateNow(tv_sage_weather_WeatherDotCom_getInstance())");
		}
		if ("dialogpictureinfo".equals(menuName) || "dialogvideoinfo".equals(menuName) || "dialogalbuminfo".equals(menuName) || "dialogsonginfo".equals(menuName) || "dialogscriptinfo".equals(menuName))
		{
			addWidgetNamed(addWidgetNamed(menuWidget, LISTENER, "Info"), ACTION, "CloseOptionsMenu()");
			addWidgetNamed(addWidgetNamed(menuWidget, LISTENER, "Select"), ACTION, "CloseOptionsMenu()");
		}

		Widget contSortMethodsAtt = null;
		if ("mypics".equals(menuName))
		{
			// GUIViewStatePictures.cpp
			addAttribute(menuWidget, "ContainerSortMethodsTop", "CreateArray(\"Name\")");
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Size\", \"Date\", \"File\")");
		}
		else if ("mygamesaves".equals(menuName))
		{
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Title\", \"Date\", \"File\")");
		}
		else if ("myprograms".equals(menuName))
		{
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Date\", \"Count\", \"Size\", \"File\")");
		}
		else if ("myscripts".equals(menuName))
		{
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Date\", \"Size\", \"File\")");
		}
		else if ("myvideo".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerSortMethodsTop", "CreateArray(\"Name\")");
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Size\", \"Date\", \"File\")");
		}
		else if ("myvideonav".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerSortMethodsTop", "CreateArray(\"Name\")");
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Title\", \"Year\", \"Rating\", \"EpisodeName\", \"EpisodeID\", \"Date\")");
		}
		else if ("mymusicsongs".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerSortMethodsTop", "CreateArray(\"Name\")");
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\", \"Size\", \"Date\", \"File\")");
		}
		else if ("mymusicnav".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerSortMethodsTop", "CreateArray(\"Name\")");
			contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Title\", \"Category\", \"Artist\", \"Album\", \"Year\", \"Track\", \"Rating\", \"Duration\")");
		}

		if ("mypics".equals(menuName) || "myvideo".equals(menuName) || "mymusicsongs".equals(menuName) || "mymusicnav".equals(menuName) || "myvideonav".equals(menuName) ||
			"myprograms".equals(menuName) || "mygamesaves".equals(menuName) || "myscripts".equals(menuName) || "mymusicplaylist".equals(menuName) || "myvideoplaylist".equals(menuName) ||
			"mytv".equals(menuName))
		{
			addAttribute(menuWidget, "RootMediaNode", "null");
			addAttribute(menuWidget, "CurrNode", "null");
			if (contSortMethodsAtt == null)
				contSortMethodsAtt = addAttribute(menuWidget, "ContainerSortMethods", "CreateArray(\"Name\")");
			if (!winny.views.isEmpty())
			{
				String viewList = "DataUnion(";
				if (!winny.forcedViews.isEmpty())
				{
					for (int i = 0; i < winny.forcedViews.size(); i++)
					{
						if (viewList.length() > 12)
							viewList += ", ";
						int currViewID = Integer.parseInt(winny.forcedViews.get(i).toString());
						Control viewControl = winny.getControlForID(currViewID);
						if (viewControl != null)
						{
							if (viewControl.visible != null && viewControl.visible.size() > 1)
							{
								String visCond = "";
								for (int j = 0; j < viewControl.visible.size(); j++)
								{
									String currCond = viewControl.visible.get(j).toString();
									if (currCond.indexOf("java_lang") == -1)
									{
										// Not one of ours...
										if (visCond.length() > 0)
											visCond += " && ";
										visCond += translateBooleanExpression(currCond, viewControl);
									}
								}
								viewList += "If(" + visCond + ", \"" + viewControl.viewtypeLabel + "\", null)";
							}
							else
								viewList += "\"" + viewControl.viewtypeLabel + "\"";
						}
					}
				}
				else
				{
					for (int i = 0; i < winny.views.size(); i++)
					{
						if (i != 0)
							viewList += ", ";
						viewList += "\"" + winny.views.get(i) + "\"";
					}
				}
				viewList += ")";
				addAttribute(menuWidget, "ContainerViews", viewList);
				winny.viewTypesSetupAction = "ContainerViews = " + viewList;
				addWidgetNamed(addWidgetNamed(viewValidationRoot, BRANCH, "\"" + menuWidget.getName() + "\""), ACTION, winny.viewTypesSetupAction);
			}
			addAttribute(menuWidget, "ContainerViewType", "GetElement(ContainerViews, 0)");
			addAttribute(menuWidget, "IsMediaWindow", "true");
			addAttribute(menuWidget, "ActiveContainerXBMCID", "-1");
		}
		if ("filebrowser".equals(menuName))
		{
			addAttribute(menuWidget, "IsMediaWindow", "false");
			addAttribute(menuWidget, "ActiveContainerXBMCID", "-1");
			addAttribute(menuWidget, "MenuListItem", "null");
		}
		else if ("filemanager".equals(menuName))
		{
			addAttribute(menuWidget, "IsMediaWindow", "false");
			addAttribute(menuWidget, "LeftCurrNode", "LeftCurrNode");
			addAttribute(menuWidget, "RightCurrNode", "RightCurrNode");
			addAttribute(menuWidget, "LeftSelectedSize", "0");
			addAttribute(menuWidget, "RightSelectedSize", "0");
			addAttribute(menuWidget, "ActiveContainerXBMCID", "-1");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
			addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "LeftCurrNode = GetMediaSource(\"Filesystem\")"),
				ACTION, "SetNodeSort(LeftCurrNode, \"Name\", true)");
			addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "RightCurrNode = GetMediaSource(\"Filesystem\")"),
				ACTION, "SetNodeSort(RightCurrNode, \"Name\", true)");
		}
		else if ("mymusicplaylisteditor".equals(menuName))
		{
			addAttribute(menuWidget, "IsMediaWindow", "true");
			addAttribute(menuWidget, "BrowserCurrNode", "BrowserCurrNode");
			addAttribute(menuWidget, "PlaylistCurrNode", "PlaylistCurrNode");
			addAttribute(menuWidget, "FilesRootNode", "FilesRootNode");
			addAttribute(menuWidget, "MusicNavRootNode", "MusicNavRootNode");
			addAttribute(menuWidget, "ActiveContainerXBMCID", "-1");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
			addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "FilesRootNode = GetMediaSource(\"MusicByFolder\")"),
				ACTION, "SetNodeSort(FilesRootNode, \"Name\", true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "MusicNavRootNode = GetMediaSource(\"MusicNavigator\")"),
				CONDITIONAL, "GetNodeSortTechnique(MusicNavRootNode) == null"),
				ACTION, "SetNodeSort(MusicNavRootNode, \"Name\", true)");
		}
		else if ("mytv".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"livetv\"");
			addAttribute(menuWidget, "NowNotNext", "true");
			addAttribute(menuWidget, "TVSearchResults", "null");
			Widget bmlHook = addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\", null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("myvideo".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"files\"");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
//			addWidgetNamed(addWidgetNamed(bmlHook, CONDITIONAL, "XBMCMenuMode != null"),
//				ACTION, "ContainerContent = XBMCMenuMode");
			Widget cond = addWidgetNamed(bmlHook, CONDITIONAL, "true");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"Movies\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByFolder\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshows\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVByFolder\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideos\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByFolder\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"clips\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"ClipsByFolder\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"files\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"playlists\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"VideoPlaylists\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"files\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "RootMediaNode = GetMediaSource(\"VideosByFolder\")"),
				ACTION, "ContainerContent = \"files\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\" + GetNodeTypePath(CurrNode), null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("mymusicsongs".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"files\"");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
//			addWidgetNamed(addWidgetNamed(bmlHook, CONDITIONAL, "XBMCMenuMode != null"),
//				ACTION, "ContainerContent = XBMCMenuMode");
			Widget cond = addWidgetNamed(bmlHook, CONDITIONAL, "true");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"playlists\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicPlaylists\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "RootMediaNode = GetMediaSource(\"MusicByFolder\")"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\" + GetNodeTypePath(CurrNode), null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("mymusicnav".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"music\"");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
//			addWidgetNamed(addWidgetNamed(bmlHook, CONDITIONAL, "XBMCMenuMode != null"),
//				ACTION, "ContainerContent = XBMCMenuMode");
			Widget cond = addWidgetNamed(bmlHook, CONDITIONAL, "true");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"genres\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicByGenre\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"artists\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicByArtist\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"albums\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicByAlbum\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"songs\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicByTitle\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"years\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicByYear\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"compilations\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"CompilationsByAlbum\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "RootMediaNode = GetMediaSource(\"MusicNavigator\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\" + GetNodeTypePath(CurrNode), null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("myvideonav".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"videos\"");
			Widget bmlHook = addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad"), CONDITIONAL, "!Reloaded");
//			addWidgetNamed(addWidgetNamed(bmlHook, CONDITIONAL, "XBMCMenuMode != null"),
//				ACTION, "ContainerContent = XBMCMenuMode");
			Widget cond = addWidgetNamed(bmlHook, CONDITIONAL, "true");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"moviegenres\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByGenre\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"movietitles\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByTitle\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"movieyears\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByYear\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"movieactors\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByActor\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"moviedirectors\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByDirector\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"moviestudios\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesByStudio\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"movies\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MoviesNavigator\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"movies\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshowgenres\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVByGenre\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshowtitles\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVBySeries\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshowyears\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVByYear\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshowactors\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVByActor\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"tvshows\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"TVNavigator\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"tvshows\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideogenres\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByGenre\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideotitles\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByTitle\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideoyears\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByYear\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideoArtists\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByArtist\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideodirectors\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByDirector\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideostudios\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosByStudio\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "java_lang_String_equalsIgnoreCase(\"musicvideos\", XBMCMenuMode)"),
				ACTION, "RootMediaNode = GetMediaSource(\"MusicVideosNavigator\")"),
				ACTION, "CurrNode = RootMediaNode"),
				ACTION, "ContainerContent = \"musicvideos\""),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "RootMediaNode = GetMediaSource(\"VideoNavigator\")"),
				ACTION, "CurrNode = RootMediaNode"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\" + GetNodeTypePath(CurrNode), null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("mypics".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"files\"");
			Widget bmlHook = addWidgetNamed(menuWidget, HOOK, "BeforeMenuLoad");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "RootMediaNode = GetMediaSource(\"PicturesByFolder\")"),
				CONDITIONAL, "GetNodeSortTechnique(RootMediaNode) == null"),
				ACTION, "SetNodeSort(RootMediaNode, GetElement(ContainerSortMethods, 0), true)");
			if (winny.viewTypesSetupAction != null)
				addWidgetNamed(bmlHook, ACTION, winny.viewTypesSetupAction);
			addWidgetNamed(addWidgetNamed(addWidgetNamed(bmlHook, ACTION, "NextView = GetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\" + GetNodeTypePath(CurrNode), null)"),
				CONDITIONAL, "NextView != null && FindElementIndex(ContainerViews, NextView) != -1"), ACTION, "ContainerViewType = NextView");
		}
		else if ("mymusicplaylist".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"Music\"");
		}
		else if ("videoplaylist".equals(menuName))
		{
			addAttribute(menuWidget, "ContainerContent", "\"Video\"");
		}
		else if ("videofullscreen".equals(menuName))
		{
			Widget video = addWidgetNamed(menuWidget, VIDEO, "Video");
			WidgetFidget.setProperty(video, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(video, FIXED_HEIGHT, "1.0");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, LISTENER, "Stop"), ACTION, "CloseAndWaitUntilClosed()"), ACTION, "SageCommand(\"Back\")");
			addAttribute(menuWidget, "FullScreenVideo", "true");
			addAttribute(menuWidget, "ShowCodecInfo", "false");
			addAttribute(menuWidget, "ShowDisplayInfo", "false");
			addAttribute(menuWidget, "ShowOSDInfo", "true");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, LISTENER, "Info"), ACTION, "ShowOSDInfo = !ShowOSDInfo"), ACTION, "Refresh()");
			Widget seekBarWidg = resolveMenuWidget("dialogseekbar");
			if (seekBarWidg != null)
			{
				Window seekWindow = (Window) widgToWindowObjMap.get(seekBarWidg);
				if (seekWindow != null)
				{
					sage.WidgetFidget.contain(addWidgetNamed(menuWidget, CONDITIONAL, "ShowOSDInfo"), seekWindow.targetParent != null ? seekWindow.targetParent : seekWindow.menuWidget);
				}
			}
			// Put a conditional on the first child which should only be shown when info is displayed
			if (!winny.controls.isEmpty())
			{
				((Control) winny.controls.get(0)).addVisibleCondition("Player.ShowCodec | ShowDisplayInfo");
				for (int i = 0; i < winny.controls.size(); i++)
					((Control) winny.controls.get(i)).addVisibleCondition("ShowOSDInfo");
			}

			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "InactivityTimeout"),
				CONDITIONAL, "IsPlaying()"), ACTION, "ShowOSDInfo = false"), ACTION, "Refresh()");

			// Info command should trigger the fullscreeninfo window...but this isn't even used in MediaStream....
		}
		else if ("settingscategory".equals(menuName))
		{
			addAttribute(menuWidget, "CurrentCategory", "If(SettingsMode == null, \"Appearance\", SettingsMode)");
		}
		else if ("dialogalbuminfo".equals(menuName))
		{
//			addAttribute(menuWidget, "ListItem", "null");
			addAttribute(menuWidget, "DisplayReview", "false");
		}
		else if ("dialogvideoinfo".equals(menuName))
		{
//			addAttribute(menuWidget, "ListItem", "null");
			addAttribute(menuWidget, "DisplayReview", "true");
		}
		else if ("dialogsonginfo".equals(menuName))
		{
//			addAttribute(menuWidget, "ListItem", "null");
		}
		else if ("dialogyesno".equals(menuName))
		{
			addAttribute(menuWidget, "DialogCompleted", "false");
			addAttribute(menuWidget, "DialogAutoCloseTime", "If(DialogAutoCloseTime == null, 0, DialogAutoCloseTime)");
			addAttribute(menuWidget, "ListItem", "null");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "AfterMenuLoad"), CONDITIONAL, "DialogAutoCloseTime > 0"),
				ACTION, "Fork()"), ACTION, "Wait(DialogAutoCloseTime)"), CONDITIONAL, "!DialogCompleted"), ACTION, "CloseOptionsMenu()");
		}
		else if ("dialogok".equals(menuName))
		{
			addAttribute(menuWidget, "ListItem", "null");
		}
		else if ("dialogprogress".equals(menuName))
		{
			addAttribute(menuWidget, "ListItem", "null");
		}
		else if ("dialogcontextmenu".equals(menuName))
		{
			addAttribute(menuWidget, "ListItem", "null");
			addAttribute(menuWidget, "ContextOffsetX", "If(ContextOffsetX == null, 0, ContextOffsetX)");
			addAttribute(menuWidget, "ContextOffsetY", "If(ContextOffsetY == null, 0, ContextOffsetY)");
			WidgetFidget.setProperty(menuWidget, ANCHOR_X, "=(ContextOffsetX*1.0)/GetFullUIWidth()");
			WidgetFidget.setProperty(menuWidget, ANCHOR_Y, "=(ContextOffsetY*1.0)/GetFullUIHeight()");
		}
		else if ("dialogkeyboard".equals(menuName))
		{
			addAttribute(menuWidget, "KBSymbolsOn", "false");
			addAttribute(menuWidget, "KBCapsOn", "false");
			addAttribute(menuWidget, "KBShiftOn", "false");
			addAttribute(menuWidget, "SymbolList", "\"*?\\\"\\\\/'`.,:;@$&!-+_() \"");
		}
		else if ("dialogkaitoast".equals(menuName))
		{
			// Just displays for a short period of time and then goes away; used for certain notifications
			addAttribute(menuWidget, "DialogAutoCloseTime", "If(DialogAutoCloseTime == null, 5000, Max(1500, DialogAutoCloseTime))");
			addAttribute(menuWidget, "ListItem", "null");
			addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(menuWidget, HOOK, "AfterMenuLoad"), CONDITIONAL, "DialogAutoCloseTime > 0"),
				ACTION, "Fork()"), ACTION, "Wait(DialogAutoCloseTime)"), CONDITIONAL, "!DialogCompleted"), ACTION, "CloseOptionsMenu()");
		}

		if (menuWidget.type() == MENU)
			addAttribute(menuWidget, "MenuListItem", "MenuListItem");
//		addAttribute(menuWidget, "MenuListItemLabel", "null");
//		addAttribute(menuWidget, "MenuListItemLabel2", "null");
//		addAttribute(menuWidget, "MenuListItemIcon", "null");
//		addAttribute(menuWidget, "MenuListItemThumb", "null");

		// Build the widgets for all of the controls now
		for (int i = 0; i < winny.controls.size(); i++)
		{
			Control c = (Control) winny.controls.get(i);
			buildControl(c, lcMenuName);
		}
		// Now process all of the top-level controls on the menu and put them in the STV
		for (int i = 0; i < winny.controls.size(); i++)
		{
			Control c = (Control) winny.controls.get(i);
			c.processPagingControls();
			if (c.targetParent != null)
				WidgetFidget.contain(menuWidget, c.targetParent);
			else if (c.widg != null)
				WidgetFidget.contain(menuWidget, c.widg);
		}

		// Now do any post-creation work for higher-level analysis and linking of components
		if ("settingscategory".equals(menuName))
		{
			// Put the FontTheme from the default button under the settings area.
			// Also create an attribute in the settings area that defines the height of the default button
			Control settingsArea = winny.getControlForID(5);
			if (settingsArea != null)
			{
				Control defaultButton = winny.getControlForID(7);
				if (defaultButton != null)
				{
					addAttribute(settingsArea.widg, "RowPanelItemHeight", "" + defaultButton.widg.getProperty(FIXED_HEIGHT));
					if (defaultButton.themeWidg != null)
						WidgetFidget.contain(settingsArea.widg, defaultButton.themeWidg);
				}
			}
		}

		if (!FAST)
		{
			// Save out the include resolved XML file
			javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(docRoot),
				new javax.xml.transform.stream.StreamResult(new java.io.File("ProcessedIncludes-" + menuName + ".xml")));
		}
		System.out.println("Done loading window " + menuName);

		return menuWidget;
	}

	private Node getChildNodeNamed(Node parentNode, String name)
	{
		NodeList kids = parentNode.getChildNodes();
		int len = kids.getLength();
		for (int i = 0; kids != null && i < len; i++)
		{
			Node currKid = kids.item(i);
			if (currKid instanceof Element && currKid.getNodeName().equals(name))
				return currKid;
		}
		return null;
	}

	private Node[] getChildNodesNamed(Node parentNode, String name)
	{
		NodeList kids = parentNode.getChildNodes();
		java.util.Vector rv = new java.util.Vector();
		int len = kids.getLength();
		for (int i = 0; kids != null && i < len; i++)
		{
			Node currKid = kids.item(i);
			if (currKid instanceof Element && currKid.getNodeName().equals(name))
				rv.add(currKid);
		}
		return (Node[]) rv.toArray(new Node[0]);
	}

	private class Control
	{
		Window win;
		Control parent;

		String type = null;
		int id = -1;
		String desc = null;
		String posx = null; // can end with 'r' for right-aligned, undefined means fill parent width w/out going out of bounds
		String posy = null;
		String width = null;
		String height = null;
		java.util.Vector visible = null;
		boolean allowhiddenfocus = false;
		java.util.Vector anims;
		// Camera????
		String colordiffuse = null;

		// In case we need to relocate it later
		Widget xbmcIdAttribute;

		// Focusable attributes
		java.util.Vector upTargets;
		java.util.Vector rightTargets;
		java.util.Vector downTargets;
		java.util.Vector leftTargets;
		// hit rect...
		String enableCondition;
		boolean pulse = true;

		java.util.Vector kids;

		// Type-specific attributes

		// group
		int defaultControl = -1;

		// group list
		int itemgap = 0;
		String orientation = "vertical";
		int pagecontrol = -1;
		boolean usecontrolcoords = false;

		// label
		String alignx;
		String aligny;
		boolean textscroll = false;
		String textLabel = null;
		boolean dontTranslateTextLabel = false;
		String numLabel = null;
		String infoContent = null; // dynamic label content
		// angle....
		// haspath...
		String font = null;
		String textcolor;
		String shadowcolor;
		String focusedcolor;
		boolean wrapmultiline;
		int scrollspeed = 60;
		String selectedcolor;
		String disabledcolor;

		// image
		String aspectratio = null;
		boolean scaleDiffuse = true;
		TextureInfo bordertexture;
		int bordersize;
		String dynamicImage = null;
		int imageFadeTime = -1;
		TextureInfo texture;

		// multiimage
		String imagepath = null;
		String fallback = null;
		boolean backgroundLoad = false;

		// buttons
		String textoffsetx;
		String textoffsety;
		String textwidth;
		String textheight;
		java.awt.Rectangle hitRect;
		TextureInfo focusedTexture;
		TextureInfo unfocusedTexture;
		TextureInfo altFocusedTexture;
		TextureInfo altUnfocusedTexture;
		TextureInfo radioOnTexture;
		TextureInfo radioOffTexture;
		String radioposx;
		String radioposy;
		String radiowidth;
		String radioheight;
		String altLabel;
		String textLabel2;
		String useAltTexture;
		Widget focusedImageWidg;
		Widget unfocusedImageWidg;
		Widget altFocusedImageWidg;
		Widget altUnfocusedImageWidg;
		Widget labelWidg;
		Widget label2Widg;
		Widget altLabelWidg;
		Widget radioOnWidg;
		Widget radioOffWidg;
		java.util.Vector onclicks;
		java.util.Vector onaltclicks;
		String onfocus;
		String selected;

		// progress
		TextureInfo texturebg;
		TextureInfo textureleft;
		TextureInfo texturemid;
		TextureInfo textureright;
		TextureInfo textureoverlay;

		// wraplist
		int focusposition = 0;
		Widget focusedLayoutWidg;
		Widget itemLayoutWidg;
		Widget tableActionFeederWidg;
		java.util.Vector contentItems;
		java.util.Vector itemLayouts;
		java.util.Vector focusedLayouts;
		java.util.Vector itemKids;
		String viewtype;
		String viewtypeLabel;
		int scrolltime = -1;

		// epggrid
		Widget focusedChannelLayoutWidg;
		Widget channelLayoutWidg;
		Widget rulerLayoutWidg;
		java.util.Vector focusedChannelLayouts;
		java.util.Vector channelLayouts;
		java.util.Vector rulerLayouts;
		int timeblocks;
		int rulerunit;

		// scrollbar
		TextureInfo sliderBackgroundTexture;
		TextureInfo sliderBarTexture;
		TextureInfo sliderBarFocusTexture;
		TextureInfo sliderNibTexture;
		TextureInfo sliderNibFocusTexture;
		boolean showonepage;

		Widget widg;
		Widget themeWidg;
		Widget targetParent;
		Widget tableWidg;
		Widget pagingWidg;

		boolean alreadyBuilt;

/*		public void parseInnerControls(Element controlXml) throws Exception
		{
			kids = new java.util.Vector();
			NodeList innerKids = controlXml.getChildNodes();
			for (int i = 0; innerKids != null && i < innerKids.getLength(); i++)
			{
				if (!(innerKids.item(i) instanceof Element))
					continue;
				if (!innerKids.item(i).getNodeName().equals("control"))
				{
					System.out.println("Skipping tag inside control group of <" + innerKids.item(i).getNodeName() + ">");
					continue;
				}
				Control kiddie = parseControl((Element) innerKids.item(i));
				if (kiddie != null)
					kids.add(kiddie);
			}
		}*/

		public void addVisibleCondition(String s)
		{
			if (visible == null)
				visible = new java.util.Vector();
			visible.add(s);
		}

		public void addClickBehavior(String s)
		{
			if (onclicks == null)
				onclicks = new java.util.Vector();
			onclicks.add(s);
		}

		public Control getControlForID(int matchID)
		{
			for (int i = 0; kids != null && i < kids.size(); i++)
			{
				Control c = (Control) kids.get(i);
				if (c.id == matchID)
					return c;
				c = c.getControlForID(matchID);
				if (c != null)
					return c;
			}
			for (int i = 0; itemKids != null && i < itemKids.size(); i++)
			{
				Control c = (Control) itemKids.get(i);
				if (c.id == matchID)
					return c;
				c = c.getControlForID(matchID);
				if (c != null)
					return c;
			}
			return null;
		}

		public void buildControlIDCacheMap(java.util.Map cacheMap)
		{
			if (id >= 0)
			{
				java.util.Vector alreadyCached = (java.util.Vector) cacheMap.get(new Integer(id));
				if (alreadyCached == null)
					cacheMap.put(new Integer(id), alreadyCached = new java.util.Vector());
				alreadyCached.add(this);
			}
			for (int i = 0; kids != null && i < kids.size(); i++)
			{
				Control c = (Control) kids.get(i);
				c.buildControlIDCacheMap(cacheMap);
			}
			for (int i = 0; itemKids != null && i < itemKids.size(); i++)
			{
				Control c = (Control) itemKids.get(i);
				c.buildControlIDCacheMap(cacheMap);
			}
		}

		public boolean isInsideContainer()
		{
			boolean insideContainer = "wraplist".equals(type) || "list".equals(type) || "panel".equals(type);
			Control currParent = parent;
			while (!insideContainer && currParent != null)
			{
				insideContainer = "wraplist".equals(currParent.type) || "list".equals(currParent.type) || "panel".equals(currParent.type);
				currParent = currParent.parent;
			}
			return insideContainer;
		}

		// Everything needs to be built before we link these up because it analyzes the hierarchy
		public void processPagingControls()
		{
			if (pagecontrol >= 0 && pagingWidg != null)
			{
				String myOffsetX = (pagingWidg == tableWidg) ? getAbsoluteXOffset() : (parent == null ? "0" : parent.getAbsoluteXOffset());
				String myOffsetY = (pagingWidg == tableWidg) ? getAbsoluteYOffset() : (parent == null ? "0" : parent.getAbsoluteYOffset());
				java.util.Vector matchingPagers = win.getControlsWithID(pagecontrol);
/*				Control searchControl = this;
				Control pager = null;
				while (pager == null && searchControl != null)
				{
					pager = searchControl.getControlForID(pagecontrol);
					searchControl = searchControl.parent;
				}
				if (pager == null)
					pager = win.getControlForID(pagecontrol);*/
				if (matchingPagers != null)
				{
					java.util.Iterator walker = matchingPagers.iterator();
					while (walker.hasNext())
						if (!((Control) walker.next()).type.equals("scrollbar"))
							walker.remove();
				}
				if (matchingPagers == null || matchingPagers.isEmpty())//pager == null || !pager.type.equals("scrollbar"))
					System.out.println("ERROR page control was not found!!! pagecontrolid=" + pagecontrol);
				else
				{
//					if (matchingPagers.size() > 1)
//						addAttribute(pagingWidg, "ScrollbarFound", "false");
					for (int i = 0; i < matchingPagers.size(); i++)
					{
						Control pager = (Control) matchingPagers.get(i);
						String pagerOffsetX = pager.parent == null ? "0" : pager.parent.getAbsoluteXOffset();
						String pagerOffsetY = pager.parent == null ? "0" : pager.parent.getAbsoluteYOffset();
						Widget pageOffset = addWidgetNamed(pagingWidg, PANEL, "Scroller Offset");
						WidgetFidget.setProperty(pageOffset, MOUSE_TRANSPARENCY, "true");
						WidgetFidget.setProperty(pageOffset, ANCHOR_X, "=(" + pagerOffsetX + ") - (" + myOffsetX + ")");
						WidgetFidget.setProperty(pageOffset, ANCHOR_Y, "=(" + pagerOffsetY + ") - (" + myOffsetY + ")");
						WidgetFidget.setProperty(pageOffset, BACKGROUND_COMPONENT, "true");
/*						if (matchingPagers.size() > 1)
						{
							if (i == 0)
							{
								WidgetFidget.contain(addWidgetNamed(pageOffset, ACTION, "ScrollbarFound = false"),
									pager.targetParent == null ? pager.widg : pager.targetParent);
							}
							else
							{
								WidgetFidget.contain(addWidgetNamed(pageOffset, CONDITIONAL, "!ScrollbarFound"),
									pager.targetParent == null ? pager.widg : pager.targetParent);
							}
						}
						else*/
							WidgetFidget.contain(pageOffset, pager.targetParent == null ? pager.widg : pager.targetParent);
					}
				}
			}
			for (int i = 0; kids != null && i < kids.size(); i++)
				((Control) kids.get(i)).processPagingControls();
		}

		public String getAbsoluteXOffset()
		{
			String rv = "";
			if (widg.hasProperty(ANCHOR_X))
			{
				rv = widg.getProperty(ANCHOR_X);
				if (rv.startsWith("="))
					rv = rv.substring(1);
			}
			if (tableWidg != null)
			{
				if (tableWidg.hasProperty(ANCHOR_X))
				{
					String curr = tableWidg.getProperty(ANCHOR_X);
					if (curr.startsWith("="))
						curr = curr.substring(1);
					if (rv.length() > 0)
						rv += " + ";
					rv += curr;
				}
			}
			Control currParent = parent;
			while (currParent != null)
			{
				if (currParent.widg.hasProperty(ANCHOR_X))
				{
					if (rv.length() > 0)
						rv += " + ";
					String curr = currParent.widg.getProperty(ANCHOR_X);
					rv += (curr.startsWith("=") ? curr.substring(1) : curr);
				}
				currParent = currParent.parent;
			}
			if (rv.length() == 0)
				rv = "0";
			return rv;
		}

		public String getAbsoluteYOffset()
		{
			String rv = "";
			if (widg.hasProperty(ANCHOR_Y))
			{
				rv = widg.getProperty(ANCHOR_Y);
				if (rv.startsWith("="))
					rv = rv.substring(1);
			}
			if (tableWidg != null)
			{
				if (tableWidg.hasProperty(ANCHOR_Y))
				{
					String curr = tableWidg.getProperty(ANCHOR_Y);
					if (curr.startsWith("="))
						curr = curr.substring(1);
					if (rv.length() > 0)
						rv += " + ";
					rv += curr;
				}
			}
			Control currParent = parent;
			while (currParent != null)
			{
				if (currParent.widg.hasProperty(ANCHOR_Y))
				{
					if (rv.length() > 0)
						rv += " + ";
					String curr = currParent.widg.getProperty(ANCHOR_Y);
					rv += (curr.startsWith("=") ? curr.substring(1) : curr);
				}
				currParent = currParent.parent;
			}
			if (rv.length() == 0)
				rv = "0";
			return rv;
		}

		public String getRealWidth()
		{
			if (width != null) return width;
			String parentWidth;
			if (parent != null)
				parentWidth = parent.getRealWidth();
			else
				parentWidth = rezWidth;
			if (posx != null)
			{
				String clean = resolveProperty(posx);
				if (clean.endsWith("r"))
					return clean.substring(0, clean.length() - 1);
				parentWidth = "(" + parentWidth + " - " + clean + ")";
			}
			return parentWidth;
		}

		public String getRealHeight()
		{
			if (height != null) return height;
			String parentHeight;
			if (parent != null)
				parentHeight = parent.getRealHeight();
			else
				parentHeight = rezHeight;
			if (posy != null)
			{
				String clean = resolveProperty(posy);
				if (clean.endsWith("r"))
					return clean.substring(0, clean.length() - 1);
				parentHeight = "(" + parentHeight + " - " + clean + ")";
			}
			return parentHeight;
		}
	}

	private class ContentItem
	{
		public ContentItem(){}
		public String desc;
		public String label;
		public String label2;
		public String icon;
		public String thumb;
		public java.util.Vector onclicks;
		public String visible;
		public String id;
	}

	private class ItemLayout
	{
		public ItemLayout(){}
		public String width;
		public String height;
		public String condition;
		public java.util.Vector kids;
	}

	private class TextureInfo
	{
		public TextureInfo(){}
		public int[] scalingInsets;
		public String texturePath;
		public boolean flipx;
		public boolean flipy;
		public String alignx;
		public String aligny;
		public String diffuseImage;
		public boolean backgroundLoad;
	}

	private java.util.Set focusableControlTypes = new java.util.HashSet();

	private Control parseControl(Element controlXml, Window menu, Control inParent) throws Exception
	{
		Control cont = new Control();
		cont.win = menu;
		cont.parent = inParent;

		cont.type = controlXml.getAttribute("type");
//System.out.println("parsing control type: " + cont.type);

		String controlType = cont.type;
		if (controlXml.getAttribute("id") != null && controlXml.getAttribute("id").length() > 0)
		{
			cont.id = parseInt(controlXml.getAttribute("id"));
		}

		// Parse the generic properties
		boolean insertedDefaults = false;
		NodeList kidsElems = controlXml.getChildNodes();
		int kidLen = (kidsElems == null) ? 0 : kidsElems.getLength();
		for (int i = 0; kidsElems != null && i < kidLen; i++)
		{
			Node tempNode = kidsElems.item(i);
			if (tempNode instanceof Element)
			{
				String name = tempNode.getNodeName();
				String lcName = name.toLowerCase();
//System.out.println("Processing control attribute tag of: " + name);
				if (tempNode.getTextContent().equals("-") && !"font".equals(name)) // need to allow empty fonts which hide text
				{
//	System.out.println("skipping attribute since its empty");
				}
				else
				{
					try
					{
						if ("description".equals(lcName) && cont.desc == null && !insertedDefaults)
						{
							cont.desc = tempNode.getTextContent().trim();
						}
						else if ("posx".equals(lcName) && cont.posx == null)
						{
							cont.posx = tempNode.getTextContent().trim();
						}
						else if ("posy".equals(lcName) && cont.posy == null)
						{
							cont.posy = tempNode.getTextContent().trim();
						}
						else if ("width".equals(lcName) && cont.width == null)
						{
							cont.width = tempNode.getTextContent().trim();
						}
						else if ("height".equals(lcName) && cont.height == null)
						{
							cont.height = tempNode.getTextContent().trim();
						}
						else if ("visible".equals(lcName))
						{
							if (cont.visible == null)
								cont.visible = new java.util.Vector();
							cont.visible.add(tempNode.getTextContent().trim());
							cont.allowhiddenfocus = ((Element) tempNode).hasAttribute("allowhiddenfocus") &&
								Boolean.parseBoolean(((Element) tempNode).getAttribute("allowhiddenfocus"));
						}
						else if ("colordiffuse".equals(lcName) && cont.colordiffuse == null)
						{
							cont.colordiffuse = tempNode.getTextContent().trim();
						}
						else if ("scrolltime".equals(lcName) && cont.scrolltime < 0)
						{
							cont.scrolltime = parseInt(tempNode.getTextContent().trim());
						}
						else if ("timeblocks".equals(lcName))
						{
							cont.timeblocks = parseInt(tempNode.getTextContent().trim());
						}
						else if ("rulerunit".equals(lcName))
						{
							cont.rulerunit = parseInt(tempNode.getTextContent().trim());
						}
						else if ("onup".equals(lcName))
						{
							if (cont.upTargets == null)
								cont.upTargets = new java.util.Vector();
							cont.upTargets.add(tempNode.getTextContent().trim());
						}
						else if ("ondown".equals(lcName))
						{
							if (cont.downTargets == null)
								cont.downTargets = new java.util.Vector();
							cont.downTargets.add(tempNode.getTextContent().trim());
						}
						else if ("onleft".equals(lcName))
						{
							if (cont.leftTargets == null)
								cont.leftTargets = new java.util.Vector();
							cont.leftTargets.add(tempNode.getTextContent().trim());
						}
						else if ("onright".equals(lcName))
						{
							if (cont.rightTargets == null)
								cont.rightTargets = new java.util.Vector();
							cont.rightTargets.add(tempNode.getTextContent().trim());
						}
						else if ("enable".equals(lcName))
						{
							cont.enableCondition = tempNode.getTextContent().trim();
						}
						else if ("pulseonselect".equals(lcName))
						{
							cont.pulse = evalBool(tempNode.getTextContent().trim());
						}
						else if ("defaultcontrol".equals(lcName))
						{
							cont.defaultControl = parseInt(tempNode.getTextContent().trim());
						}
						else if ("control".equals(lcName))
						{
							if (cont.kids == null)
								cont.kids = new java.util.Vector();
							Control kiddie = parseControl((Element) tempNode, menu, cont);
							if (kiddie != null)
							{
								cont.kids.add(kiddie);
							}
						}
						else if ("itemlayout".equals(lcName) || "focusedlayout".equals(lcName) ||
							"rulerlayout".equals(lcName) || "channellayout".equals(lcName) ||
							"focusedchannellayout".equals(lcName))
						{
							ItemLayout newLay = new ItemLayout();
							newLay.kids = new java.util.Vector();
							if ("itemlayout".equals(lcName))
							{
								if (cont.itemLayouts == null)
									cont.itemLayouts = new java.util.Vector();
								cont.itemLayouts.add(newLay);
							}
							else if ("rulerlayout".equals(lcName))
							{
								if (cont.rulerLayouts == null)
									cont.rulerLayouts = new java.util.Vector();
								cont.rulerLayouts.add(newLay);
							}
							else if ("channellayout".equals(lcName))
							{
								if (cont.channelLayouts == null)
									cont.channelLayouts = new java.util.Vector();
								cont.channelLayouts.add(newLay);
							}
							else if ("focusedchannellayout".equals(lcName))
							{
								if (cont.focusedChannelLayouts == null)
									cont.focusedChannelLayouts = new java.util.Vector();
								cont.focusedChannelLayouts.add(newLay);
							}
							else
							{
								if (cont.focusedLayouts == null)
									cont.focusedLayouts = new java.util.Vector();
								cont.focusedLayouts.add(newLay);
							}
							if (((Element) tempNode).hasAttribute("width"))
								newLay.width = ((Element) tempNode).getAttribute("width");
							if (((Element) tempNode).hasAttribute("height"))
								newLay.height = ((Element) tempNode).getAttribute("height");
							if (((Element) tempNode).hasAttribute("condition"))
								newLay.condition = ((Element) tempNode).getAttribute("condition");

							NodeList layoutKids = tempNode.getChildNodes();
							if (cont.itemKids == null)
								cont.itemKids = new java.util.Vector();
							int currKidLen = layoutKids.getLength();
							for (int k = 0; k < currKidLen; k++)
							{
								Node currLayKid = layoutKids.item(k);
								if (currLayKid instanceof Element && currLayKid.getNodeName().equals("control"))
								{
									Control kiddie = parseControl((Element) currLayKid, menu, cont);
									if (kiddie != null)
									{
										newLay.kids.add(kiddie);
										cont.itemKids.add(kiddie);
									}
								}
							}
						}
						else if ("content".equals(lcName))
						{
							cont.contentItems = new java.util.Vector();
							NodeList contentKids = tempNode.getChildNodes();
							int currKidLen = contentKids.getLength();
							for (int k = 0; k < currKidLen; k++)
							{
								Node currContentKid = contentKids.item(k);
								if (currContentKid instanceof Element && currContentKid.getNodeName().equals("item"))
								{
									ContentItem currItem = new ContentItem();
									Node itemKid = getChildNodeNamed(currContentKid, "label");
									if (itemKid != null)
										currItem.label = itemKid.getTextContent().trim();
									itemKid = getChildNodeNamed(currContentKid, "label2");
									if (itemKid != null)
										currItem.label2 = itemKid.getTextContent().trim();
									itemKid = getChildNodeNamed(currContentKid, "icon");
									if (itemKid != null)
										currItem.icon = itemKid.getTextContent().trim();
									itemKid = getChildNodeNamed(currContentKid, "thumb");
									if (itemKid != null)
										currItem.thumb = itemKid.getTextContent().trim();
									Node[] itemKids = getChildNodesNamed(currContentKid, "visible");
									if (itemKids != null && itemKids.length > 0)
									{
										currItem.visible = itemKids[0].getTextContent().trim();
										for (int c = 1; c < itemKids.length; c++)
											currItem.visible += " && " + itemKids[c].getTextContent().trim();
									}
									itemKids = getChildNodesNamed(currContentKid, "onclick");
									if (itemKids != null && itemKids.length > 0)
									{
										currItem.onclicks = new java.util.Vector();
										for (int c = 0; c < itemKids.length; c++)
											currItem.onclicks.add(itemKids[c].getTextContent().trim());
									}
									if (((Element)currContentKid).hasAttribute("id"))
										currItem.id = ((Element) currContentKid).getAttribute("id");
									cont.contentItems.add(currItem);
								}
							}
						}
						else if ("itemgap".equals(lcName))
						{
							cont.itemgap = parseInt(tempNode.getTextContent().trim());
						}
						else if ("orientation".equals(lcName))
						{
							cont.orientation = tempNode.getTextContent().trim();
						}
						else if ("selected".equals(lcName))
						{
							cont.selected = tempNode.getTextContent().trim();
						}
						else if ("pagecontrol".equals(lcName))
						{
							cont.pagecontrol = parseInt(tempNode.getTextContent().trim());
						}
						else if ("usecontrolcoords".equals(lcName))
						{
							cont.usecontrolcoords = evalBool(tempNode.getTextContent().trim());
						}
						else if ("showonepage".equals(lcName))
						{
							cont.showonepage = evalBool(tempNode.getTextContent().trim());
						}
						else if (("textoffsetx".equals(lcName) || "textxoff".equals(lcName)) && cont.textoffsetx == null)
						{
							cont.textoffsetx = tempNode.getTextContent().trim();
						}
						else if (("textoffsety".equals(lcName) || "textyoff".equals(lcName)) && cont.textoffsety == null)
						{
							cont.textoffsety = tempNode.getTextContent().trim();
						}
						else if ("textwidth".equals(lcName) && cont.textwidth == null)
						{
							cont.textwidth = tempNode.getTextContent().trim();
						}
						else if ("textheight".equals(lcName) && cont.textheight == null)
						{
							cont.textheight = tempNode.getTextContent().trim();
						}
						else if ("hitrect".equals(lcName))
						{
							cont.hitRect = new java.awt.Rectangle(parseInt(((Element) tempNode).getAttribute("x")),
								parseInt(((Element) tempNode).getAttribute("y")),
								parseInt(((Element) tempNode).getAttribute("w")),
								parseInt(((Element) tempNode).getAttribute("h")));
						}
						else if ("align".equals(lcName) && cont.alignx == null)
						{
							cont.alignx = tempNode.getTextContent().trim().toLowerCase();
						}
						else if ("aligny".equals(lcName) && cont.aligny == null)
						{
							cont.aligny = tempNode.getTextContent().trim().toLowerCase();
						}
						else if ("label".equals(lcName) && cont.textLabel == null)
						{
							cont.textLabel = tempNode.getTextContent().trim();
							if (cont.textLabel.length() == 0)
								cont.textLabel = null;
							if (((Element) tempNode).hasAttribute("fallback"))
								cont.fallback = ((Element) tempNode).getAttribute("fallback").toLowerCase();
						}
						else if ("label2".equals(lcName) && cont.textLabel2 == null)
						{
							cont.textLabel2 = tempNode.getTextContent().trim();
							if (cont.textLabel2.length() == 0)
								cont.textLabel2 = null;
						}
						else if ("info".equals(lcName) && cont.infoContent == null)
						{
							cont.infoContent = tempNode.getTextContent().trim();
						}
						else if ("number".equals(lcName) && cont.numLabel == null)
						{
							cont.numLabel = tempNode.getTextContent().trim();
						}
						else if ("textcolor".equals(lcName) && cont.textcolor == null)
						{
							cont.textcolor = tempNode.getTextContent().trim();
						}
						else if ("shadowcolor".equals(lcName) && cont.shadowcolor == null)
						{
							cont.shadowcolor = tempNode.getTextContent().trim();
						}
						else if ("focusedcolor".equals(lcName) && cont.focusedcolor == null)
						{
							cont.focusedcolor = tempNode.getTextContent().trim();
						}
						else if ("selectedcolor".equals(lcName) && cont.selectedcolor == null)
						{
							cont.selectedcolor = tempNode.getTextContent().trim();
						}
						else if ("disabledcolor".equals(lcName) && cont.disabledcolor == null)
						{
							cont.disabledcolor = tempNode.getTextContent().trim();
						}
						else if ("font".equals(lcName) && cont.font == null)
						{
							cont.font = tempNode.getTextContent().trim();
						}
						else if ("imagepath".equals(lcName))
						{
							cont.imagepath = tempNode.getTextContent().trim();
							if (((Element) tempNode).hasAttribute("fallback"))
								cont.fallback = ((Element) tempNode).getAttribute("fallback").toLowerCase();
							if (((Element) tempNode).hasAttribute("background"))
								cont.backgroundLoad = ((Element) tempNode).getAttribute("background").equalsIgnoreCase("true");
						}
						else if ("scroll".equals(lcName))
						{
							cont.textscroll = evalBool(tempNode.getTextContent().trim());
						}
						else if ("wrapmultiline".equals(lcName))
						{
							cont.wrapmultiline = evalBool(tempNode.getTextContent().trim());
						}
						else if ("scrollspeed".equals(lcName))
						{
							cont.scrollspeed = parseInt(tempNode.getTextContent().trim());
						}
						else if ("aspectratio".equals(lcName) && cont.aspectratio == null)
						{
							cont.aspectratio = tempNode.getTextContent().trim();
							if (((Element) tempNode).hasAttribute("align"))
								cont.alignx = ((Element) tempNode).getAttribute("align").toLowerCase();
							if (((Element) tempNode).hasAttribute("aligny"))
								cont.aligny = ((Element) tempNode).getAttribute("aligny").toLowerCase();
							if (((Element) tempNode).hasAttribute("scalediffuse"))
								cont.scaleDiffuse = evalBool(((Element) tempNode).getAttribute("scalediffuse"));
						}
						else if ("viewtype".equals(lcName))
						{
							cont.viewtype = tempNode.getTextContent().trim();
							if (stringMap.containsKey(cont.viewtype))
								cont.viewtype = stringMap.get(cont.viewtype).toString();
							if (((Element) tempNode).hasAttribute("label"))
							{
								cont.viewtypeLabel = localizeStrings(((Element) tempNode).getAttribute("label"), false, cont);
								if (stringMap.containsKey(cont.viewtypeLabel))
									cont.viewtypeLabel = stringMap.get(cont.viewtypeLabel).toString();
							}
							else
								cont.viewtypeLabel = cont.viewtype;
							// For the TV menu we automatically set the view names
							if (!menu.menuName.equals("mytv"))
								menu.views.add(cont.viewtypeLabel);
						}
						else if ("texture".equals(lcName) && cont.texture == null)
						{
							cont.texture = parseTextureAttributes((Element)tempNode);
						}
						else if ("bordertexture".equals(lcName) && cont.bordertexture == null)
						{
							cont.bordertexture = parseTextureAttributes((Element)tempNode);
						}
						else if ("textureradiofocus".equals(lcName))
						{
							cont.radioOnTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("textureradionofocus".equals(lcName))
						{
							cont.radioOffTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("texturesliderbackground".equals(lcName))
						{
							cont.sliderBackgroundTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("texturesliderbar".equals(lcName))
						{
							cont.sliderBarTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("texturesliderbarfocus".equals(lcName))
						{
							cont.sliderBarFocusTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("textureslidernib".equals(lcName))
						{
							cont.sliderNibTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("textureslidernibfocus".equals(lcName))
						{
							cont.sliderNibFocusTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("texturebg".equals(lcName))
						{
							cont.texturebg = parseTextureAttributes((Element) tempNode);
						}
						else if ("lefttexture".equals(lcName) || "textureleft".equals(lcName))
						{
							cont.textureleft = parseTextureAttributes((Element) tempNode);
						}
						else if ("midtexture".equals(lcName))
						{
							cont.texturemid = parseTextureAttributes((Element) tempNode);
						}
						else if ("righttexture".equals(lcName) || "textureright".equals(lcName))
						{
							cont.textureright = parseTextureAttributes((Element) tempNode);
						}
						else if ("overlaytexture".equals(lcName))
						{
							cont.textureoverlay = parseTextureAttributes((Element) tempNode);
						}
						else if ("radioposx".equals(lcName))
						{
							cont.radioposx = tempNode.getTextContent().trim();
						}
						else if ("radioposy".equals(lcName))
						{
							cont.radioposy = tempNode.getTextContent().trim();
						}
						else if ("radiowidth".equals(lcName))
						{
							cont.radiowidth = tempNode.getTextContent().trim();
						}
						else if ("radioheight".equals(lcName))
						{
							cont.radioheight = tempNode.getTextContent().trim();
						}
						else if ("bordersize".equals(lcName))
						{
							cont.bordersize = parseInt(tempNode.getTextContent().trim());
						}
						else if ("fadetime".equals(lcName))
						{
							cont.imageFadeTime = parseInt(tempNode.getTextContent().trim());
						}
						else if ("texturefocus".equals(lcName))
						{
							cont.focusedTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("texturenofocus".equals(lcName))
						{
							cont.unfocusedTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("alttexturefocus".equals(lcName))
						{
							cont.altFocusedTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("alttexturenofocus".equals(lcName))
						{
							cont.altUnfocusedTexture = parseTextureAttributes((Element) tempNode);
						}
						else if ("usealttexture".equals(lcName))
						{
							cont.useAltTexture = tempNode.getTextContent().trim();
						}
						else if ("altlabel".equals(lcName))
						{
							cont.altLabel = tempNode.getTextContent().trim();
						}
						else if ("onclick".equals(lcName))
						{
							if (cont.onclicks == null)
								cont.onclicks = new java.util.Vector();
							cont.onclicks.add(tempNode.getTextContent().trim());
						}
						else if ("altclick".equals(lcName))
						{
							if (cont.onaltclicks == null)
								cont.onaltclicks = new java.util.Vector();
							cont.onaltclicks.add(tempNode.getTextContent().trim());
						}
						else if ("onfocus".equals(lcName))
						{
							cont.onfocus = tempNode.getTextContent().trim();
						}
						else if ("focusposition".equals(lcName) || "focusedposition".equals(lcName))
						{
							cont.focusposition = parseInt(tempNode.getTextContent().trim());
						}
						else if ("animation".equals(lcName))
						{
							if (cont.anims == null)
								cont.anims = new java.util.Vector();
							Element tempElem = (Element) tempNode;
							NodeList innerAnimList = null;
							int innerAnimIndex = 0;
							String commonAnimType = null;
							String commonCondition = null;
							boolean commonReversible = true;
							int innerAnimLen = 0;
							if (tempElem.hasAttribute("type"))
							{
								commonAnimType = tempElem.getAttribute("type").toLowerCase();
								if (tempElem.hasAttribute("condition"))
									commonCondition = tempElem.getAttribute("condition");
								if (tempElem.hasAttribute("reversible"))
									commonReversible = Boolean.parseBoolean(tempElem.getAttribute("reversible"));
								innerAnimList = tempElem.getElementsByTagName("effect");
								innerAnimLen = innerAnimList.getLength();
							}
							do
							{
								AnimData newAnim = new AnimData();
								cont.anims.add(newAnim);
								if (innerAnimList != null)
								{
									tempElem = (Element) innerAnimList.item(innerAnimIndex);
									newAnim.trigger = commonAnimType;
									newAnim.effect = tempElem.getAttribute("type");
									newAnim.condition = commonCondition;
									newAnim.reversible = commonReversible;
								}
								else
								{
									newAnim.trigger = tempNode.getTextContent().trim().toLowerCase();
									newAnim.effect = tempElem.getAttribute("effect");
									if (tempElem.hasAttribute("condition"))
										newAnim.condition = tempElem.getAttribute("condition");
									if (tempElem.hasAttribute("reversible"))
										newAnim.reversible = Boolean.parseBoolean(tempElem.getAttribute("reversible"));
								}
								if (newAnim.effect != null)
									newAnim.effect = newAnim.effect.toLowerCase();
								if (tempElem.hasAttribute("time"))
									newAnim.time = parseInt(tempElem.getAttribute("time"));
								if (tempElem.hasAttribute("delay"))
									newAnim.delay = parseInt(tempElem.getAttribute("delay"));
								if (tempElem.hasAttribute("start"))
								{
									java.util.StringTokenizer toker = new java.util.StringTokenizer(tempElem.getAttribute("start"), ",");
									newAnim.start = new int[toker.countTokens()];
									for (int q = 0; toker.hasMoreTokens(); q++)
										newAnim.start[q] = parseInt(toker.nextToken());
								}
								if (tempElem.hasAttribute("end"))
								{
									java.util.StringTokenizer toker = new java.util.StringTokenizer(tempElem.getAttribute("end"), ",");
									newAnim.end = new int[toker.countTokens()];
									for (int q = 0; toker.hasMoreTokens(); q++)
										newAnim.end[q] = parseInt(toker.nextToken());
								}
								if (tempElem.hasAttribute("acceleration"))
									newAnim.acceleration = tempElem.getAttribute("acceleration");
								if (tempElem.hasAttribute("center"))
								{
									String startStr = tempElem.getAttribute("center");
									int idx = startStr.indexOf(',');
									if (idx != -1)
									{
										newAnim.center = new int[2];
										newAnim.center[0] = parseInt(startStr.substring(0, idx));
										newAnim.center[1] = parseInt(startStr.substring(idx + 1));
									}
									else
									{
										try
										{
											newAnim.center = new int[] { parseInt(startStr) };
										}
										catch (NumberFormatException nfe){}
									}
									/*else if (startStr.equalsIgnoreCase("auto"))
									{
										newAnim.center = new int[] { -1, -1 };
									}*/
								}
								if (tempElem.hasAttribute("pulse"))
									newAnim.pulse = Boolean.parseBoolean(tempElem.getAttribute("pulse"));
								if (tempElem.hasAttribute("tween"))
									newAnim.tween = tempElem.getAttribute("tween");
								if (tempElem.hasAttribute("easing"))
									newAnim.easing = tempElem.getAttribute("easing");
								innerAnimIndex++;
							} while (innerAnimList != null && innerAnimIndex < innerAnimLen);
						}
						else
						{
//							if (!name.equals("description") && !name.equals("include") && !name.equals("posy") && !name.equals("font"))
//								System.out.println("Unknown tag found inside control type " + cont.type + " of " + name);
						}
					}
					catch (Exception e)
					{
						System.out.println("ERROR processing node of: " + e);
						e.printStackTrace();
					}
				}
			}
			if (i == kidLen - 1 && !insertedDefaults)
			{
				// Insert all of the default control tags that aren't replaced by something else in this one
				NodeList defaultTags = (NodeList) defaultControlIncludes.get(cont.type);
				if (defaultTags == null)
				{
//					System.out.println("ERROR no defaults set for control type:" + cont.type);
				}
				else
				{
					for (int j = 0; j < defaultTags.getLength(); j++)
					{
						if (getChildNodeNamed(controlXml, defaultTags.item(j).getNodeName()) == null)
						{
							controlXml.appendChild(controlXml.getOwnerDocument().importNode(defaultTags.item(j), true));
						}
					}
					kidLen = kidsElems.getLength();
				}
				insertedDefaults = true;
			}
		}

		boolean isContainer = controlType.equalsIgnoreCase("list") || controlType.equalsIgnoreCase("wraplist") || controlType.equalsIgnoreCase("fixedlist") ||
			controlType.equalsIgnoreCase("panel") || controlType.equalsIgnoreCase("epggrid");
		// Setup anything else we need that's specific for our type of control that Widget building may depend on
		if ("mypics".equals(menu.menuName) || "myvideo".equals(menu.menuName) || "mymusicsongs".equals(menu.menuName) || "mymusicnav".equals(menu.menuName) ||
			"myvideonav".equals(menu.menuName) || "myprograms".equals(menu.menuName) || "mygamesaves".equals(menu.menuName) || "myscripts".equals(menu.menuName))
		{
			if (cont.viewtype != null)
			{
				if (cont.visible == null)
					cont.visible = new java.util.Vector();
				cont.visible.add("java_lang_String_equalsIgnoreCase(\"" + cont.viewtypeLabel + "\", ContainerViewType)");
			}
			if (cont.id == 2)
			{
				// View type
				String str = stringMap.get("534").toString();
				cont.textLabel = str.substring(0, str.indexOf('%')) + "$INFO[ContainerViewType]"; // Strip the %s
				cont.addClickBehavior("container.nextviewmode");
			}
			else if (cont.id == 3)
			{
				// Sort by
				String str = stringMap.get("550").toString();
				cont.textLabel = "\"" + str.substring(0, str.indexOf('%')) + "\" + If(IsEmpty(GetNodeSortTechnique(CurrNode)), \"Name\", GetNodeSortTechnique(CurrNode))";
				cont.dontTranslateTextLabel = true;
				cont.addClickBehavior("NewSortMethod = GetElement(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), " +
					"(FindElementIndex(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), GetNodeSortTechnique(CurrNode)) + 1) % Size(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods)))");
				cont.addClickBehavior("SetNodeSort(CurrNode, NewSortMethod, IsNodeSortAscending(CurrNode))");
			}
			else if (cont.id == 4)
			{
				// Sort direction
				cont.selected = cont.useAltTexture = "!IsNodeSortAscending(CurrNode)";
				cont.addClickBehavior("SetNodeSort(CurrNode, GetNodeSortTechnique(CurrNode), !IsNodeSortAscending(CurrNode))");
			}
			else if (cont.id == 5 && (menu.menuName.indexOf("video") != -1 || menu.menuName.indexOf("music") != -1)) // pictures don't have a library mode
			{
				// Switches between file/library mode
				if ("myvideo".equals(menu.menuName))
				{
					cont.textLabel = "15100"; // Library
					cont.addClickBehavior("ActivateWindow(myvideonav)");
				}
				else if ("myvideonav".equals(menu.menuName))
				{
					cont.textLabel = "744"; // Files
					cont.addClickBehavior("ActivateWindow(myvideo)");
				}
				else if ("mymusicsongs".equals(menu.menuName))
				{
					cont.textLabel = "15100"; // Library
					cont.addClickBehavior("ActivateWindow(mymusicnav)");
				}
				else if ("mymusicnav".equals(menu.menuName))
				{
					cont.textLabel = "744"; // Files
					cont.addClickBehavior("ActivateWindow(mymusicsongs)");
				}
			}
			else if (cont.id == 6 && "mypics".equals(menu.menuName))
			{
				cont.addClickBehavior("\"XOUT: LaunchSlideshow\"");
			}
			else if (cont.id == 6 && menu.menuName.indexOf("video") != -1)
			{
				// play DVD
				cont.addClickBehavior("PlayDVD");
			}
			else if (cont.id == 7 && menu.menuName.indexOf("video") != -1)
			{
				// TODO: Stacking control
				cont.enableCondition = "false";
				cont.textLabel = "14000";
			}
			else if (cont.id == 7 && "mypics".equals(menu.menuName))
			{
				// TODO: Launch a recursive slideshow
				cont.addClickBehavior("\"XOUT: LaunchRecursiveSlideshow\"");
			}
			else if (cont.id == 7 && "mymusicsongs".equals(menu.menuName))
			{
				cont.addClickBehavior("ActivateWindow(mymusicnav, playlists)");
			}
			else if (cont.id == 8 && menu.menuName.indexOf("video") != -1)
			{
				// Rescan the current file--we don't implement this
				cont.enableCondition = "false";
			}
			else if (cont.id == 8 && "mymusicnav".equals(menu.menuName))
			{
				// Search dialog for music library
				// We launch the keyboard; prepopulating it w/ the current search string if there is one.
				// When it is complete; we execute the search and display the results as artists/albums/songs (some of which may be grouped)
				cont.addClickBehavior("\"XOUT: MusicNavSearch\"");
			}
			else if (cont.id == 8 && "myvideonav".equals(menu.menuName))
			{
				// Search dialog for video library
				// We launch the keyboard; prepopulating it w/ the current search string if there is one.
				// When it is complete; we execute the search and display the results as actors/titles/episodes (some of which may be grouped)
				cont.addClickBehavior("\"XOUT: VideoNavSearch\"");
			}
			else if (cont.id == 9 && menu.menuName.indexOf("video") != -1)
			{
				// IMDB search - not supported
				cont.enableCondition = "false";
			}
			else if (cont.id == 9 && "mypics".equals(menu.menuName))
			{
				cont.selected = cont.useAltTexture = "(GetProperty(\"slideshow_is_random\", false))";
				cont.addClickBehavior("(SetProperty(\"slideshow_is_random\", !GetProperty(\"slideshow_is_random\", false)))");
			}
			else if (cont.id == 9 && "mymusicsongs".equals(menu.menuName))
			{
				// Rescan the current file--we don't implement this
				cont.enableCondition = "false";
				cont.textLabel = "102";
			}
			else if (cont.id == 10 && "mymusicsongs".equals(menu.menuName))
			{
				// Record the current stream--we don't implement this
				cont.enableCondition = "false";
				cont.textLabel = "264";
			}
			else if (cont.id == 10 && "myvideonav".equals(menu.menuName))
			{
				// Changes the 'showmode' which toggles through the watched/unwatched/all filter setting
				cont.addClickBehavior("SetNodeFilter(CurrNode, If(GetNodeFilterTechnique(CurrNode, 0) != null && IsNodeFilterMatching(CurrNode, 0), null, \"Watched\"), " +
					"If(GetNodeFilterTechnique(CurrNode, 0) == null, false, true))");
				cont.textLabel = "If(GetNodeFilterTechnique(CurrNode, 0) == null, \"" + stringMap.get("16100") + "\", If(IsNodeFilterMatching(CurrNode, 0), \"" + stringMap.get("16101") +
					"\", \"" + stringMap.get("16102") + "\"))";
				cont.dontTranslateTextLabel = true;
			}
			else if (cont.id == 11 && "mymusicsongs".equals(menu.menuName))
			{
				// Rip the current disc--we don't implement this
				cont.enableCondition = "false";
			}
			else if (cont.id == 12)
			{
				// # of files
				cont.dontTranslateTextLabel = true;
				cont.textLabel = "GetNodeNumChildren(CurrNode) + \" " + stringMap.get("127") + "\"";
			}
			else if (cont.id == 13 && "myvideo".equals(menu.menuName))
			{
				cont.addClickBehavior("ActivateWindow(myvideo, playlists)");
			}
			else if (cont.id == 10 && "myvideonav".equals(menu.menuName))
			{
				// Show all button which toggles between unwatched & all
				cont.addClickBehavior("SetNodeFilter(CurrNode, If(GetNodeFilterTechnique(CurrNode, 0) == null, \"Watched\", null), false)");
				cont.selected = cont.useAltTexture = "GetNodeFilterTechnique(CurrNode, 0) != null";
			}
			else if (cont.id == 15 && ("mymusicnav".equals(menu.menuName) || "myvideonav".equals(menu.menuName)))
			{
				// Filter label; it shows the hierarchical path of the current node
				cont.textLabel = "GetNodeFullPath(CurrNode)";
				cont.dontTranslateTextLabel = true;
			}
			else if (cont.id == 16 && ("mymusicnav".equals(menu.menuName) || "myvideonav".equals(menu.menuName)))
			{
				// Party mode
				cont.addClickBehavior("\"XOUT: TogglePartyMode\"");
			}
			else if (cont.id == 17 && "mymusicnav".equals(menu.menuName))
			{
				// Brings up the info dialog for the selected item
				cont.addClickBehavior("SageCommand(\"Info\")");
			}
			else if (cont.id == 17 && "myvideonav".equals(menu.menuName))
			{
				// TODO: Flatten button....whatever that does
				cont.addClickBehavior("\"XOUT: FlattenVideoNav\"");
			}
			else if (cont.id == 18 && ("mymusicnav".equals(menu.menuName) || "myvideonav".equals(menu.menuName)))
			{
				// Displays the message about the DB being empty if it is

			}
			else if (cont.id == 19 && "mymusicnav".equals(menu.menuName))
			{
				// Filter button; this launches a keyboard where you can set the current filter text...the current filter
				// text is also supposed to be displayed as a 'label2' inside this control.
				cont.addClickBehavior("\"XOUT: FilterMusicNav\"");
			}
			else if (cont.id == 19 && "myvideonav".equals(menu.menuName))
			{
				// Filter button; this launches a keyboard where you can set the current filter text...the current filter
				// text is also supposed to be displayed as a 'label2' inside this control.
				cont.addClickBehavior("\"XOUT: FilterVideoNav\"");
			}
			else if (cont.id == 20 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Shuffle playback
				cont.addClickBehavior("PlayerControl.Random");
				cont.selected = cont.useAltTexture = "Playlist.IsRandom";
			}
			else if (cont.id == 21 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Save now playing list as a playlist
				cont.enableCondition = "(GetNodeNumChildren(CurrNode) > 0 && (GetCurrentPlaylist() == GetNowPlayingList()))";
			}
			else if (cont.id == 22 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Clear now playing list
				cont.enableCondition = "(GetNodeNumChildren(CurrNode) > 0 && (GetCurrentPlaylist() == GetNowPlayingList()))";
				cont.addClickBehavior("Playlist.Clear");
			}
			else if (cont.id == 23 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Play selected item
				cont.enableCondition = "MenuListItem != null";
				cont.addClickBehavior("StartPlaylistAt(GetCurrentPlaylist(), FindElementIndex(GetNodeChildren(CurrNode), MenuListItem) + 1)");
			}
			else if (cont.id == 24 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Next playlist item
				cont.addClickBehavior("ChannelUp()");
			}
			else if (cont.id == 25 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Previous playlist item
				cont.addClickBehavior("ChannelDown()");
			}
			else if (cont.id == 26 && ("mymusicplaylist".equals(menu.menuName) || "myvideoplaylist".equals(menu.menuName)))
			{
				// Toggle repeat mode
				cont.addClickBehavior("PlayControl.Repeat");
				cont.textLabel = "If(((IsCurrentMediaFileMusic() && GetProperty(\"music/repeat_playback\", false)) || " +
					"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"video_lib/repeat_playback\", false))), \"" + stringMap.get("597") + "\", \"" + stringMap.get("595") + "\")";
				cont.dontTranslateTextLabel = true;
			}
		}
		else if ("mytv".equals(menu.menuName))
		{
			if (cont.id == 10 && isContainer)
			{
				// EPG Grid
				cont.viewtype = cont.viewtypeLabel = "EPG";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 11 && isContainer)
			{
				// TV Channels
				cont.viewtype = cont.viewtypeLabel = "TVChannels";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 12 && isContainer)
			{
				// Radio Channels
				cont.viewtype = cont.viewtypeLabel = "RadioChannels";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 13 && isContainer)
			{
				// Recordings
				cont.viewtype = cont.viewtypeLabel = "Recordings";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 14 && isContainer)
			{
				// Schedule
				cont.viewtype = cont.viewtypeLabel = "Schedule";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 15 && isContainer)
			{
				// Programs on a specific channel
				cont.viewtype = cont.viewtypeLabel = "ChannelPrograms";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 16 && isContainer)
			{
				// Now/Next on Channels
				cont.viewtype = cont.viewtypeLabel = "NowNextPrograms";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 17 && isContainer)
			{
				// TV Search Results
				cont.viewtype = cont.viewtypeLabel = "TVSearch";
				menu.views.add(cont.viewtype);
			}
			if (cont.id == 29)
			{
				// Header that displays high-level info about the current view
				cont.textLabel = "If(ContainerViewType == \"TVSearch\", \"" + stringMap.get("283") + "\", If(ContainerViewType == \"Schedule\", \"" + stringMap.get("19025") +
					"\", If(ContainerViewType == \"Recordings\", \"" + stringMap.get("19017") + "\", If(ContainerViewType == \"RadioChannels\", \"" + stringMap.get("19024") +
					"\", If(ContainerViewType == \"TVChannels\", \"" + stringMap.get("19023") + "\", \"" + stringMap.get("19029") + "\")))))";
				cont.dontTranslateTextLabel = true;
			}
			if (cont.id == 30)
			{
				// Header that displays the channel group name or the current channel for the Programs on a Channel view
				// NOTE: We could also display the channel group name; but we don't do that currently since we have no such concept
				cont.textLabel = "If(ContainerViewType == \"ChannelPrograms\", GetChannelName(If(GetCurrentMediaFile() == null, GetChannelForStationID(GetProperty(\"videoframe/last_station_id\", null)), GetCurrentMediaFile())), If(ContainerViewType == \"NowNextPrograms\", If(NowNotNext, \"" + stringMap.get("19030") +
					"\", \"" + stringMap.get("19031") + "\"), If(ContainerViewType == \"EPG\", \"" + stringMap.get("19032") + "\", \"\")))";
				cont.dontTranslateTextLabel = true;
			}
			if (cont.id == 31)
			{
				// Button that toggles between the different EPG views: channel, now, next, timeline
				cont.textLabel = "\"" + stringMap.get("19029") + "\" + If(ContainerViewType == \"NowNextPrograms\", If(NowNotNext, \": " + stringMap.get("19030") +
					"\", \": " + stringMap.get("19031") + "\"), If(ContainerViewType == \"EPG\", \": " + stringMap.get("19032") + "\", \"\"))";
				cont.dontTranslateTextLabel = true;
			}
			if (cont.id == 32)
			{
				// Sets view to TV Channels view
				cont.addClickBehavior("Container.SetViewMode(11)");
			}
			if (cont.id == 33)
			{
				// Sets view to Radio Channels view
				cont.addClickBehavior("Container.SetViewMode(12)");
			}
			if (cont.id == 34)
			{
				// Sets view to Recordings view
				cont.addClickBehavior("Container.SetViewMode(13)");
			}
			if (cont.id == 35)
			{
				// Sets view to Schedule view
				cont.addClickBehavior("Container.SetViewMode(14)");
			}
			if (cont.id == 36)
			{
				// Sets view to Search view
				cont.addClickBehavior("\"XOUT: TVSearch\"");
			}
			if (cont.id == 37)
			{
				// Sets view to ChannelPrograms view
				cont.addClickBehavior("Container.SetViewMode(15)");
			}
			if (cont.id == 38)
			{
				// Sets view to NowPrograms view
				cont.addClickBehavior("NowNotNext = true");
				cont.addClickBehavior("Container.SetViewMode(16)");
			}
			if (cont.id == 39)
			{
				// Sets view to NextPrograms view
				cont.addClickBehavior("NowNotNext = false");
				cont.addClickBehavior("Container.SetViewMode(16)");
			}
			if (cont.id == 39)
			{
				// Sets view to EPG view
				cont.addClickBehavior("Container.SetViewMode(10)");
			}
			if (cont.viewtype != null)
			{
				if (cont.visible == null)
					cont.visible = new java.util.Vector();
				cont.visible.add("java_lang_String_equalsIgnoreCase(\"" + cont.viewtypeLabel + "\", ContainerViewType)");
			}
		}
		if ("myweather".equals(menu.menuName))
		{
			// from XBMC/Utils/Weather.h and GUIWindowWeather.cpp
			if (cont.id == 2)
			{
				// Refresh button
				cont.addClickBehavior("tv_sage_weather_WeatherDotCom_updateNow(tv_sage_weather_WeatherDotCom_getInstance())");
			}
			if (cont.id == 3)
			{
				// TODO: Location select; which we don't support
				cont.addVisibleCondition("false");
			}
			if (cont.id == 11)
			{
				// Last Updated
				cont.infoContent = "Window(Weather).Property(Updated)";
			}
			else if (cont.id == 10)
			{
				// Location
				cont.infoContent = "Window(Weather).Property(Location)";
			}
			else if (cont.id == 21)
			{
				// Current icon
				cont.infoContent = "Window(Weather).Property(Current.ConditionIcon)";
			}
			else if (cont.id == 22)
			{
				// Current condition
				cont.infoContent = "Window(Weather).Property(Current.Condition)";
			}
			else if (cont.id == 23)
			{
				// Current temp
				cont.infoContent = "Window(Weather).Property(Current.Temperature)";
			}
			else if (cont.id == 24)
			{
				// Current feels like
				cont.infoContent = "Window(Weather).Property(Current.FeelsLike)";
			}
			else if (cont.id == 25)
			{
				// Current uvid
				cont.infoContent = "Window(Weather).Property(Current.UVIndex)";
			}
			else if (cont.id == 26)
			{
				// Current wind
				cont.infoContent = "Window(Weather).Property(Current.Wind)";
			}
			else if (cont.id == 27)
			{
				// Current dew point
				cont.infoContent = "Window(Weather).Property(Current.DewPoint)";
			}
			else if (cont.id == 28)
			{
				// Current humidity
				cont.infoContent = "Window(Weather).Property(Current.Humidity)";
			}
			for (int i = 0; i <= 5; i++)
			{
				if (cont.id == (31 + i*10))
				{
					// Date for forecast
					cont.infoContent = "Window(Weather).Property(Day" + i + ".Title)";
				}
				else if (cont.id == (32 + i*10))
				{
					// High
					cont.infoContent = "Window(Weather).Property(Day" + i + ".HighTemp)";
				}
				else if (cont.id == (33 + i*10))
				{
					// Low
					cont.infoContent = "Window(Weather).Property(Day" + i + ".LowTemp)";
				}
				else if (cont.id == (34 + i*10))
				{
					// Conditions
					cont.infoContent = "Window(Weather).Property(Day" + i + ".Outlook)";
				}
				else if (cont.id == (35 + i*10))
				{
					// Icon
					cont.infoContent = "Window(Weather).Property(Day" + i + ".OutlookIcon)";
				}
			}
		}
		String lcMenuName = menu.menuName.toLowerCase();
		if ("dialogbuttonmenu".equals(lcMenuName))
		{
			if (cont.id == 3100)
			{
				cont.infoContent = "GetTextForUIComponent(GetUIComponentForVariable(\"Focused\", true))";
			}
		}
		else if ("filebrowser".equals(lcMenuName))
		{
			if (cont.id == 411) // heading label
			{
				cont.infoContent = "DirBrowseTitle";
			}
			if (cont.id == 412) // selected path name
			{
				cont.infoContent = "ListItem.FileNameAndPath";
			}
			if (cont.id == 413) // OK button
			{
				cont.enableCondition = "If(false, \"Focused\", If(FileSelectMode, IsFilePath(MenuListItem), IsDirectoryPath(CurrNode)))";
				cont.addClickBehavior("SelectedFilePath = GetNodeDataObject(If(FileSelectMode, MenuListItem, CurrNode))");
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 414) // Cancel button
			{
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 415) // New folder button
			{
				cont.enableCondition = "!FileSelectMode && java_io_File_canWrite(GetNodeDataObject(CurrNode))";
			}
			if (cont.id == 416) // flip
			{
				// This is meant to use a flipped version of the image; but we don't really support that
				cont.enableCondition = "false";
			}
			if (cont.id == 450) // list view
			{
				cont.addVisibleCondition("ThumbsFileView != true");
			}
			if (cont.id == 451) // thumbs view
			{
				cont.addVisibleCondition("ThumbsFileView == true");
			}
		}
		else if ("filemanager".equals(lcMenuName))
		{
			if (cont.id == 12) // num files left side
			{
				cont.infoContent = "If(GetChildrenCheckedCount(LeftCurrNode, true) == 0, GetNodeNumChildren(LeftCurrNode) + \" " + stringMap.get("127") +
					"\", \"\" + GetChildrenCheckedCount(LeftCurrNode, true) + \"/\" + GetNodeNumChildren(LeftCurrNode) + \" " + stringMap.get("127") +
					" (\" + If(LeftSelectedSize < 10000000, (LeftSelectedSize/1024) + \" KB\", (LeftSelectedSize/(1024*1024)) + \" MB\") + \")\")";
			}
			if (cont.id == 13) // num files right side
			{
				cont.infoContent = "If(GetChildrenCheckedCount(RightCurrNode, true) == 0, GetNodeNumChildren(RightCurrNode) + \" " + stringMap.get("127") +
					"\", \"\" + GetChildrenCheckedCount(RightCurrNode, true) + \"/\" + GetNodeNumChildren(RightCurrNode) + \" " + stringMap.get("127") +
					" (\" + If(RightSelectedSize < 10000000, (RightSelectedSize/1024) + \" KB\", (RightSelectedSize/(1024*1024)) + \" MB\") + \")\")";
			}
			if (cont.id == 101) // current directory left
			{
				cont.infoContent = "If(GetNodeParent(LeftCurrNode) == null, \"" + stringMap.get("20108") + "\", GetAbsoluteFilePath(LeftCurrNode))";
			}
			if (cont.id == 102) // current directory right
			{
				cont.infoContent = "If(GetNodeParent(RightCurrNode) == null, \"" + stringMap.get("20108") + "\", GetAbsoluteFilePath(RightCurrNode))";
			}
		}
		else if ("mymusicplaylisteditor".equals(lcMenuName))
		{
			if (cont.id == 6) // load playlist
			{
				cont.enableCondition = "false"; // we don't allow this since its automatic in the import process
			}
			if (cont.id == 7) // save playlist
			{
				cont.enableCondition = "false"; // we don't allow this since changes are always saved instantly
			}
			if (cont.id == 12) // num files left side
			{
				cont.infoContent = "GetNodeNumChildren(BrowserCurrNode) + \" " + stringMap.get("127") + "\"";
			}
			if (cont.id == 101) // num items right side
			{
				cont.infoContent = "GetNodeNumChildren(PlaylistCurrNode) + \" " + stringMap.get("134") + "\"";
			}
		}
		else if ("videofullscreen".equals(lcMenuName))
		{
			if (cont.id == 24) // buffering label
			{
				cont.addVisibleCondition("Player.IsCaching");
			}
			if (cont.id == 10) // Label #1 details
			{
				cont.infoContent = "\"\"";
				cont.addVisibleCondition("Player.ShowCodec | ShowDisplayInfo");
			}
			if (cont.id == 11) // Label #2 details
			{
				cont.infoContent = "\"\"";
				cont.addVisibleCondition("Player.ShowCodec | ShowDisplayInfo");
			}
			if (cont.id == 12) // Label #3 details
			{
				cont.infoContent = "\"\"";
				cont.addVisibleCondition("Player.ShowCodec | ShowDisplayInfo");
			}
		}
		else if ("settingscategory".equals(lcMenuName))
		{
			if (cont.id == 2)
			{
				// Settings category label
				cont.textLabel = "If(false, \"Focused\", CurrentCategory)";
				cont.dontTranslateTextLabel = true;
			}
			else if (cont.id == 7)
			{
				// default button
				cont.addVisibleCondition("false && DefaultButton");
			}
			else if (cont.id == 8)
			{
				// default radiobutton
				cont.addVisibleCondition("false && DefaultRadioButton");
			}
			else if (cont.id == 9)
			{
				// default spin control
				cont.addVisibleCondition("false && DefaultSpin");
			}
			else if (cont.id == 10)
			{
				// default category button
				cont.addVisibleCondition("false && DefaultCategoryButton");
			}
			else if (cont.id == 11)
			{
				// default separator
				cont.addVisibleCondition("false && DefaultSeparator");
			}
			else if (cont.id == 12)
			{
				// default edit control
				cont.addVisibleCondition("false && DefaultEdit");
			}
		}
		else if ("dialogalbuminfo".equals(lcMenuName))
		{
			if (cont.id == 20) // albumName
				cont.infoContent = "ListItem.Album";
			if (cont.id == 21) // artist
				cont.infoContent = "ListItem.AlbumArtist";
			if (cont.id == 22) // year
				cont.infoContent = "ListItem.Year";
			if (cont.id == 23) // rating
				cont.infoContent = "ListItem.Rating";
			if (cont.id == 24) // rating
				cont.infoContent = "ListItem.Genre";
			if (cont.id == 25) // moods
				cont.infoContent = "ListItem.property(albummoods)";
			if (cont.id == 26) // styles
				cont.infoContent = "ListItem.property(albumstyles)";
			if (cont.id == 3) // thumbnail
				cont.infoContent = "ListItem.Thumb";
			if (cont.id == 4) // textarea
			{
				cont.addVisibleCondition("DisplayReview");
				cont.infoContent = "If(IsEmpty(GetMediaFileMetadata(Album ,\"Review\")), \"" +
					stringMap.get("414") + "\", GetMediaFileMetadata(Album ,\"Review\"))";
			}
			if (cont.id == 5) // tracks button
			{
				cont.addClickBehavior("DisplayReview = !DisplayReview");
				cont.textLabel = "If(DisplayReview, \"" + stringMap.get("182") + "\", \"" + stringMap.get("183") + "\")";
				cont.dontTranslateTextLabel = true;
			}
			if (cont.id == 6) // refresh button....it closes it actually (and it tries to refresh the metadata for this file)
			{
				cont.addClickBehavior("Refresh()");
			}
			if (cont.id == 10) // get thumb button (always disabled)
			{
				cont.enableCondition = "false";
			}
			if (cont.id == 11) // lastfm button (always hidden)
			{
				cont.addVisibleCondition("false");
			}
			if (cont.id == 12) // get fan art button (always disabled)
			{
				cont.enableCondition = "false";
			}
			if (cont.id == 50) // track list
			{
				cont.addVisibleCondition("!DisplayReview");
			}
		}
		else if ("dialogvideoinfo".equals(lcMenuName))
		{
			if (cont.id == 20)
				cont.infoContent = "ListItem.Title";
			if (cont.id == 21)
				cont.infoContent = "ListItem.Director";
			if (cont.id == 22)
				cont.infoContent = "ListItem.property(Credits)";
			if (cont.id == 23)
				cont.infoContent = "ListItem.Genre";
			if (cont.id == 24)
				cont.infoContent = "ListItem.Year";
			if (cont.id == 25)
				cont.infoContent = "ListItem.Tagline";
			if (cont.id == 26)
				cont.infoContent = "ListItem.PlotOutline";
			if (cont.id == 29)
				cont.infoContent = "ListItem.Cast";
			if (cont.id == 30)
				cont.infoContent = "ListItem.RatingAndVotes";
			if (cont.id == 31)
				cont.infoContent = "ListItem.property(runtime)";
			if (cont.id == 32)
				cont.infoContent = "ListItem.mpaa";
			if (cont.id == 36)
				cont.infoContent = "ListItem.studio";
			if (cont.id == 37)
				cont.infoContent = "ListItem.top250";
			if (cont.id == 38)
				cont.infoContent = "ListItem.trailer";
			if (cont.id == 3) // thumbnail
				cont.infoContent = "ListItem.Thumb";
			if (cont.id == 4) // textarea
			{
				cont.addVisibleCondition("DisplayReview");
//				cont.addVisibleCondition("IsTVFile(ListItem) | IsWatched(ListItem) | !System.GetBool(videolibrary.hideplots)");
				// XBMC conditionally hides this if 'videolibrary.hideplots' is set to true and its unwatched and shows 20370 in its place
				cont.infoContent = "ListItem.Plot";
			}
			if (cont.id == 5) // tracks button
			{
				cont.addClickBehavior("DisplayReview = !DisplayReview");
				cont.textLabel = "If(DisplayReview, \"" + stringMap.get("206") + "\", \"" + stringMap.get("207") + "\")";
				cont.dontTranslateTextLabel = true;
			}
			if (cont.id == 6) // refresh button....it closes it actually (and it tries to refresh the metadata for this file) (always disabled for us)
			{
				cont.enableCondition = "false";
				cont.addClickBehavior("Refresh()");
			}
			if (cont.id == 8) // play
			{
				cont.addClickBehavior("ClearWatched(ListItem)");
				cont.addClickBehavior("Watch(GetNodeDataObject(ListItem))");
				cont.addClickBehavior("ActivateWindow(videofullscreen)");
			}
			if (cont.id == 9) // resume
			{
				cont.addVisibleCondition("GetRealWatchedStartTime(ListItem) > 0");
				cont.addClickBehavior("Watch(GetNodeDataObject(ListItem))");
				cont.addClickBehavior("ActivateWindow(videofullscreen)");
			}
			if (cont.id == 10) // get thumb button (always disabled for us)
			{
				cont.enableCondition = "false";
			}
			if (cont.id == 11) // play trailer
			{
//				cont.addVisibleCondition("!IsEmpty(ListItem.Trailer)");
				cont.addClickBehavior("Watch(GetMediaFileMetadata(ListItem, \"Trailer\"))");
				cont.addClickBehavior("ActivateWindow(videofullscreen)");
			}
			if (cont.id == 12) // get fan art button (always disabled for us)
			{
				// Should only be enabled for tvshows & movies once we have fanart support
				cont.enableCondition = "false";
			}
			if (cont.id == 50) // track list
			{
				cont.addVisibleCondition("!DisplayReview");
			}
		}
		else if ("dialogsonginfo".equals(lcMenuName))
		{
			if (cont.id == 10 || cont.id == 11) // OK & Cancel
			{
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 13) // get thumb button (always disabled for us)
			{
				cont.enableCondition = "false";
			}
		}
		else if ("dialogyesno".equals(lcMenuName))
		{
			if (cont.id == 1) // heading
				cont.infoContent = "If(YNHeadingLabel == null, \"\", YNHeadingLabel)";
			if (cont.id == 2) // line 1
				cont.infoContent = "If(YNLine1Label == null, \"\", YNLine1Label)";
			if (cont.id == 3) // line 2
				cont.infoContent = "If(YNLine2Label == null, \"\", YNLine2Label)";
			if (cont.id == 4) // line 3
				cont.infoContent = "If(YNLine3Label == null, \"\", YNLine3Label)";
			if (cont.id == 10) // no button
			{
				cont.infoContent = "ForcedNoLabel";
				cont.addClickBehavior("ReturnValue = false");
				cont.addClickBehavior("DialogCompleted = true");
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 11) // yes button
			{
				cont.infoContent = "ForcedYesLabel";
				cont.addClickBehavior("ReturnValue = true");
				cont.addClickBehavior("DialogCompleted = true");
				cont.addClickBehavior("CloseOptionsMenu()");
			}
		}
		else if ("dialogok".equals(lcMenuName))
		{
			if (cont.id == 1) // heading
				cont.infoContent = "If(OKHeadingLabel == null, \"\", OKHeadingLabel)";
			if (cont.id == 2) // line 1
				cont.infoContent = "If(OKLine1Label == null, \"\", OKLine1Label)";
			if (cont.id == 3) // line 2
				cont.infoContent = "If(OKLine2Label == null, \"\", OKLine2Label)";
			if (cont.id == 4) // line 3
				cont.infoContent = "If(OKLine3Label == null, \"\", OKLine3Label)";
			if (cont.id == 10) // ok button
				cont.addClickBehavior("CloseOptionsMenu()");
		}
		else if ("dialogprogress".equals(lcMenuName))
		{
			if (cont.id == 1) // heading
				cont.infoContent = "If(ProgressHeadingLabel == null, \"\", ProgressHeadingLabel)";
			if (cont.id == 2) // line 1
				cont.infoContent = "If(ProgressLine1Label == null, \"\", ProgressLine1Label)";
			if (cont.id == 3) // line 2
				cont.infoContent = "If(ProgressLine2Label == null, \"\", ProgressLine2Label)";
			if (cont.id == 4) // line 3
				cont.infoContent = "If(ProgressLine3Label == null, \"\", ProgressLine3Label)";
			if (cont.id == 10) // cancel button
			{
				cont.addVisibleCondition("HideProgressCancel != true");
			}
			if (cont.id == 20) // progress bar
			{
				cont.infoContent = "ProgressAmount";
				cont.addVisibleCondition("HideProgressBar != true");
			}
		}
		else if ("dialogcontextmenu".equals(lcMenuName))
		{
			if (cont.id == 1000) // button template
			{
				// Kill the up/down/left/right targets
				cont.downTargets = cont.leftTargets = cont.rightTargets = cont.upTargets = null;
				cont.textLabel = "$INFO[ContextButton]";
			}
		}
		else if ("dialogkeyboard".equals(lcMenuName))
		{
			// Arguments: KBHeadingLabel, KBText(optional)
			// Returns: KBEntryConfirmed, KBText
			if (cont.id == 300) // done button
			{
				cont.addClickBehavior("KBEntryConfirmed = true");
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 311) // heading
				cont.infoContent = "If(KBHeadingLabel == null, \"\", KBHeadingLabel)";
			if (cont.id == 310) // edit label
			{
				cont.desc = "KBText";
				cont.textLabel = null;
			}
			if (cont.id >= 48 && cont.id <= 57) // numerals
			{
				cont.textLabel = "If(KBSymbolsOn, java_lang_String_charAt(SymbolList, " + (cont.id - 48) + "), \"" + ((char) cont.id) + "\")";
				cont.dontTranslateTextLabel = true;
				cont.addClickBehavior("Keystroke(If(KBSymbolsOn, java_lang_String_charAt(SymbolList, " + (cont.id - 48) + "), \"" + ((char) cont.id) + "\"), false)");
			}
			else if (cont.id >= 65 && cont.id <= 90) // text
			{
				cont.textLabel = "If(KBSymbolsOn, java_lang_String_charAt(SymbolList, Min(Size(SymbolList) - 1, " + (cont.id - 55) +
					")), If((KBCapsOn && KBShiftOn) || (!KBCapsOn && !KBSymbolsOn && !KBShiftOn), \"" + ((char)(cont.id + 32)) + "\", \"" + ((char) cont.id) + "\"))";
				cont.dontTranslateTextLabel = true;
				cont.addClickBehavior("Keystroke(If(KBSymbolsOn, java_lang_String_charAt(SymbolList, Min(Size(SymbolList) - 1, " + (cont.id - 55) +
					")), If((KBCapsOn && KBShiftOn) || (!KBCapsOn && !KBSymbolsOn && !KBShiftOn), \"" + ((char)(cont.id + 32)) + "\", \"" + ((char) cont.id) + "\")), false)");
				cont.addClickBehavior("KBShiftOn = false");
			}
			else if (cont.id == 32)
				cont.addClickBehavior("Keystroke(\" \", false)");
			String extraSymButtons = "._-@/\\";
			for (int i = 0; i < extraSymButtons.length(); i++)
				if (cont.id == extraSymButtons.charAt(i))
				{
					cont.textLabel = "\"" + (extraSymButtons.charAt(i) == '\\' ? "\\" : "") + extraSymButtons.charAt(i) + "\"";
					cont.dontTranslateTextLabel = true;
					cont.addClickBehavior("Keystroke(\"" + (extraSymButtons.charAt(i) == '\\' ? "\\" : "") + extraSymButtons.charAt(i) + "\", false)");
				}
			if (cont.id == 301) // cancel button
				cont.addClickBehavior("CloseOptionsMenu()");
			if (cont.id == 302) // shift button
			{
				cont.addClickBehavior("KBShiftOn = !KBShiftOn");
				cont.selected = cont.useAltTexture = "KBShiftOn";
			}
			if (cont.id == 303) // caps button
			{
				cont.addClickBehavior("KBCapsOn = (!KBSymbolsOn && !KBCapsOn)");
				cont.selected = cont.useAltTexture = "KBCapsOn";
			}
			if (cont.id == 304) // symbols button
			{
				cont.addClickBehavior("KBCapsOn = false");
				cont.addClickBehavior("KBSymbolsOn = !KBSymbolsOn");
				cont.selected = cont.useAltTexture = "KBSymbolsOn";
			}
			if (cont.id == 305) // cursor left button
			{
				cont.addClickBehavior("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", 310), \"Left\", 1)");
			}
			if (cont.id == 306) // cursor right button
			{
				cont.addClickBehavior("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", 310), \"Left\", 1)");
			}
			if (cont.id == 307) // IP address button
			{
				cont.addClickBehavior("\"XOUT: KeyboardIPAddress\"");
			}
			if (cont.id == 8) // backspace buton
				cont.addClickBehavior("Keystroke(\"Backspace\", false)");
		}
		else if ("dialognumeric".equals(lcMenuName))
		{
			// Arguments: KBHeadingLabel, KBText(optional)
			// Returns: KBEntryConfirmed, KBText
			if (cont.id == 21) // done button
			{
				cont.addClickBehavior("KBEntryConfirmed = true");
				cont.addClickBehavior("CloseOptionsMenu()");
			}
			if (cont.id == 1) // heading
				cont.infoContent = "If(KBHeadingLabel == null, \"\", KBHeadingLabel)";
			if (cont.id == 4) // edit label
			{
				cont.desc = "KBText";
				cont.textLabel = null;
			}
			if (cont.id >= 10 && cont.id <= 19) // numerals
			{
				cont.textLabel = "\"" + (cont.id - 10) + "\"";
				cont.dontTranslateTextLabel = true;
				cont.addClickBehavior("Keystroke(\"" + (cont.id - 10) + "\", false)");
			}
			if (cont.id == 20) // cursor left button
			{
				cont.addClickBehavior("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", 4), \"Left\", 1)");
			}
			if (cont.id == 22) // cursor right button
			{
				cont.addClickBehavior("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", 4), \"Left\", 1)");
			}
			if (cont.id == 23) // backspace buton
				cont.addClickBehavior("Keystroke(\"Backspace\", false)");
		}
		else if ("dialogkaitoast".equals(lcMenuName))
		{
			if (cont.id == 400) // icon
				cont.infoContent = "KaiMessageIcon";
			else if (cont.id == 401) // caption
				cont.infoContent = "KaiMessageCaption";
			else if (cont.id == 402) // description
				cont.infoContent = "KaiMessageDescription";
		}
		else if ("settings".equals(lcMenuName))
		{
			if (cont.id == 12) // just runs the credits
				cont.addClickBehavior("XBMC.Credits()");
		}
		return cont;
	}

	public Control buildControl(Control cont, String menuName) throws Exception
	{
		if (cont.alreadyBuilt)
			return cont;
		cont.alreadyBuilt = true;
		// Build our children recursively
		for (int i = 0; cont.kids != null && i < cont.kids.size(); i++)
			buildControl((Control) cont.kids.get(i), menuName);
		for (int i = 0; cont.itemKids != null && i < cont.itemKids.size(); i++)
			buildControl((Control) cont.itemKids.get(i), menuName);

		// Since there's dependencies between the different attributes sometimes; and the order of attributes shouldn't
		// really matter; we process them all at the end here collectively so things end up in the right
		String controlType = cont.type;
		String controlTypeLc = controlType.toLowerCase();
		// Break these up on type
		if ("group".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
		}
		else if ("grouplist".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
			WidgetFidget.setProperty(cont.widg, SCROLLING, ("vertical".equalsIgnoreCase(cont.orientation) ? "1" : "2"));
		}
		else if ("label".equals(controlTypeLc))
		{
			// Special cases for editable text controls
			if (("dialogkeyboard".equals(menuName) && cont.id == 310) ||
				("dialognumeric".equals(menuName) && cont.id == 4))
				cont.widg = mgroup.addWidget(TEXTINPUT);
			else
				cont.widg = mgroup.addWidget(TEXT);
		}
		else if ("edit".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(TEXTINPUT);
		}
		else if ("fadelabel".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(TEXT);
		}
		else if ("image".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(IMAGE);
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
// I think this is the default
			WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
		}
		else if ("largeimage".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(IMAGE);
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
			WidgetFidget.setProperty(cont.widg, BACKGROUND_LOAD, "true");
// I think this is the default
			WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
		}
		else if ("multiimage".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(IMAGE);
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
			WidgetFidget.setProperty(cont.widg, BACKGROUND_LOAD, "true");
// I think this is the default
			WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
		}
		else if ("button".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("radiobutton".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("selectbutton".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("togglebutton".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("multiselect".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("spincontrol".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("spincontrolex".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("slider".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("scrollbar".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
			// Scrollbars need to include all of the visible conditions that their parents have
			Control currParent = cont.parent;
			while (currParent != null)
			{
				if (currParent.visible != null && !currParent.visible.isEmpty())
				{
					if (cont.visible == null)
						cont.visible = new java.util.Vector();
					cont.visible.addAll(currParent.visible);
				}
				currParent = currParent.parent;
			}
		}
		else if ("sliderex".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(ITEM);
		}
		else if ("progress".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
			// Have progress bars update every second so they keep their data correct
			WidgetFidget.setProperty(cont.widg, ANIMATION, "0,1000,0");
		}
		else if ("list".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("wraplist".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("epggrid".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("fixedlist".equals(controlTypeLc))
		{
// HACK FOR NOW UNTIL WE HAVE A WAY TO DO FIXEDLIST PROPERLY
			controlTypeLc = "wraplist";
			controlType = "wraplist";
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("panel".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("textbox".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(TEXT);
		}
		else if ("rss".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("visualisation".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(VIDEO);
			cont.targetParent = mgroup.addWidget(CONDITIONAL);
			WidgetFidget.setName(cont.targetParent, "IsCurrentMediaFileMusic()");
			WidgetFidget.contain(cont.targetParent, cont.widg);
		}
		else if ("videowindow".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(VIDEO);
			cont.targetParent = mgroup.addWidget(CONDITIONAL);
			WidgetFidget.setName(cont.targetParent, "DoesCurrentMediaFileHaveVideo()");
			WidgetFidget.contain(cont.targetParent, cont.widg);
		}
		else if ("mover".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else if ("resize".equals(controlTypeLc))
		{
			cont.widg = mgroup.addWidget(PANEL);
		}
		else
		{
			System.out.println("UNKNOWN CONTROL TYPE OF:" + controlType);
			return null;
		}

		if (cont.win != null && cont.win.menuWidget.type() == OPTIONSMENU && (cont.widg.type() == TEXT || cont.widg.type() == ITEM))
			WidgetFidget.setProperty(cont.widg, IGNORE_THEME_PROPERTIES, "true");

		if (cont.id >= 0)
		{
			cont.xbmcIdAttribute = mgroup.addWidget(ATTRIBUTE);
			WidgetFidget.setName(cont.xbmcIdAttribute, "XBMCID");
			WidgetFidget.setProperty(cont.xbmcIdAttribute, VALUE, cont.id + "");
			WidgetFidget.contain(cont.widg, cont.xbmcIdAttribute);
		}

		// Setup things for default controls for windows now

		// Fix controls that don't have proper defaults
		if ("togglebutton".equals(controlTypeLc) && cont.useAltTexture == null)
		{
			System.out.println("ERROR ToggleButton doesn't have anything to determine if its selected!!! menu=" + menuName + " controlID=" + cont.id);
			cont.useAltTexture = "ToggleSelected";
			addAttribute(cont.widg, "ToggleSelected", "false");
			if (cont.onaltclicks == null)
				cont.onaltclicks = cont.onclicks == null ? new java.util.Vector() : new java.util.Vector(cont.onclicks);
			if (cont.onclicks == null)
				cont.onclicks = new java.util.Vector();
			cont.onclicks.add("ToggleSelected = !ToggleSelected");
			cont.onaltclicks.add("ToggleSelected = !ToggleSelected");
		}
		if ("radiobutton".equals(controlTypeLc) && cont.selected == null)
		{
			System.out.println("ERROR RadioButton doesn't have anything to determine if its selected!!! menu=" + menuName + " controlID=" + cont.id);
			cont.selected = "RadioSelected";
			addAttribute(cont.widg, "RadioSelected", "false");
			if (cont.onclicks == null)
				cont.onclicks = new java.util.Vector();
			cont.onclicks.add("RadioSelected = !RadioSelected");
		}

		if (cont.desc != null && (cont.widg.type() != Widget.ITEM || cont.textLabel != null))
			WidgetFidget.setName(cont.widg, cont.desc);
		if (cont.posx != null)
		{
			String cleanX = resolveProperty(cont.posx);
			if (cleanX.endsWith("r"))
				WidgetFidget.setProperty(cont.widg, ANCHOR_X, "=" + (cont.parent == null ? rezWidth : resolveProperty(cont.parent.getRealWidth())) + " - " + cleanX.substring(0, cleanX.length() - 1));
			else
				WidgetFidget.setProperty(cont.widg, ANCHOR_X, cleanX);
		}
		if (cont.posy != null)
		{
			String cleanY = resolveProperty(cont.posy);
			if (cleanY.endsWith("r"))
				WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "=" + (cont.parent == null ? rezHeight : resolveProperty(cont.parent.getRealHeight())) + " - " + cleanY.substring(0, cleanY.length() - 1));
			else
				WidgetFidget.setProperty(cont.widg, ANCHOR_Y, cleanY);
		}
		if (cont.width != null)
			WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, resolveProperty(cont.width));
		if (cont.height != null)
		{
			if (cont.height.equals("0"))
				cont.height = "2"; // fudge factor
			WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, resolveProperty(cont.height));
		}
		if (cont.colordiffuse != null)
		{
			if (cont.colordiffuse.length() == 8)
			{
				int alphaValue = Integer.parseInt(cont.colordiffuse.substring(0, 2), 16);
				// Also get the 3 color components and average them and then multiply that scaling factor by the alpha as well so we match the XBMC
				// fade levels better
				if (IMAGE == cont.widg.type())
					WidgetFidget.setProperty(cont.widg, FOREGROUND_ALPHA, "" + (alphaValue / 255.0f));
				else
					WidgetFidget.setProperty(cont.widg, BACKGROUND_ALPHA, alphaValue + "");
				WidgetFidget.setProperty(cont.widg, FOREGROUND_COLOR, "0x" + cont.colordiffuse.substring(2));
			}
			else if (cont.colordiffuse.length() == 6)
			{
				WidgetFidget.setProperty(cont.widg, FOREGROUND_COLOR, "0x" + cont.colordiffuse);
			}
		}
		if (cont.upTargets != null)
		{
			Widget listenParent = addWidgetNamed(cont.widg, LISTENER, "Up");
			boolean needRefresh = false;
			for (int i = 0; i < cont.upTargets.size(); i++)
			{
				String currExpr = cont.upTargets.get(i).toString();
				try
				{
					int targetID = parseInt(currExpr);
					if (!"horizontal".equals(cont.orientation) && targetID == cont.id && controlTypeLc.equals("list"))
						WidgetFidget.contain(addWidgetNamed(listenParent, CONDITIONAL, "!SetFocusForVariable(\"ListItem\", GetElement(TableData, Size(TableData) - 1))"),
							getMoveFocusChain("Up", currExpr, cont));
					else
						WidgetFidget.contain(listenParent, getMoveFocusChain("Up", currExpr, cont));
					//addWidgetNamed(addWidgetNamed(listenParent, CONDITIONAL, "!" + getSetFocusExpr(currExpr)), ACTION, "PassiveListen()");
				}
				catch (NumberFormatException nfe)
				{
					WidgetFidget.contain(listenParent, createProcessChainFromExpression(currExpr, cont));
					needRefresh = true;
				}
			}
			if (needRefresh)
				addWidgetNamed(listenParent, ACTION, "Refresh()");
		}
		if (cont.downTargets != null)
		{
			Widget listenParent = addWidgetNamed(cont.widg, LISTENER, "Down");
			boolean needRefresh = false;
			for (int i = 0; i < cont.downTargets.size(); i++)
			{
				String currExpr = cont.downTargets.get(i).toString();
				try
				{
					int targetID = parseInt(currExpr);
					if (!"horizontal".equals(cont.orientation) && targetID == cont.id && controlTypeLc.equals("list"))
						WidgetFidget.contain(addWidgetNamed(listenParent, CONDITIONAL, "!SetFocusForVariable(\"ListItem\", GetElement(TableData, 0))"),
							getMoveFocusChain("Down", currExpr, cont));
					else
						WidgetFidget.contain(listenParent, getMoveFocusChain("Down", currExpr, cont));
//					addWidgetNamed(addWidgetNamed(listenParent, CONDITIONAL, "!" + getSetFocusExpr(currExpr)), ACTION, "PassiveListen()");
				}
				catch (NumberFormatException nfe)
				{
					WidgetFidget.contain(listenParent, createProcessChainFromExpression(currExpr, cont));
					needRefresh = true;
				}
			}
			if (needRefresh)
				addWidgetNamed(listenParent, ACTION, "Refresh()");
		}
		if (cont.leftTargets != null)
		{
			Widget listenParent = addWidgetNamed(cont.widg, LISTENER, "Left");
			boolean needRefresh = false;
			for (int i = 0; i < cont.leftTargets.size(); i++)
			{
				String currExpr = cont.leftTargets.get(i).toString();
				try
				{
					int targetID = parseInt(currExpr);
					if ("horizontal".equals(cont.orientation) && targetID == cont.id && controlTypeLc.equals("list"))
						WidgetFidget.contain(addWidgetNamed(listenParent, CONDITIONAL, "!SetFocusForVariable(\"ListItem\", GetElement(TableData, Size(TableData) - 1))"),
							getMoveFocusChain("Left", currExpr, cont));
					else
						WidgetFidget.contain(listenParent, getMoveFocusChain("Left", currExpr, cont));
//					addWidgetNamed(addWidgetNamed(listenParent, CONDITIONAL, "!" + getSetFocusExpr(currExpr)), ACTION, "PassiveListen()");
				}
				catch (NumberFormatException nfe)
				{
					WidgetFidget.contain(listenParent, createProcessChainFromExpression(currExpr, cont));
					needRefresh = true;
				}
			}
			if (needRefresh)
				addWidgetNamed(listenParent, ACTION, "Refresh()");
		}
		if (cont.rightTargets != null)
		{
			Widget listenParent = addWidgetNamed(cont.widg, LISTENER, "Right");
			boolean needRefresh = false;
			for (int i = 0; i < cont.rightTargets.size(); i++)
			{
				String currExpr = cont.rightTargets.get(i).toString();
				try
				{
					int targetID = parseInt(currExpr);
					if ("horizontal".equals(cont.orientation) && targetID == cont.id && controlTypeLc.equals("list"))
						WidgetFidget.contain(addWidgetNamed(listenParent, CONDITIONAL, "!SetFocusForVariable(\"ListItem\", GetElement(TableData, 0))"),
							getMoveFocusChain("Right", currExpr, cont));
					else
						WidgetFidget.contain(listenParent, getMoveFocusChain("Right", currExpr, cont));
//					addWidgetNamed(addWidgetNamed(listenParent, CONDITIONAL, "!" + getSetFocusExpr(currExpr)), ACTION, "PassiveListen()");
				}
				catch (NumberFormatException nfe)
				{
					WidgetFidget.contain(listenParent, createProcessChainFromExpression(currExpr, cont));
					needRefresh = true;
				}
			}
			if (needRefresh)
				addWidgetNamed(listenParent, ACTION, "Refresh()");
		}
		for (int i = 0; cont.kids != null && i < cont.kids.size(); i++)
		{
			Control kiddie = (Control) cont.kids.get(i);
			// Don't add scrollbar children directly; they get placed directly underneath the table that's using them instead
			if ("scrollbar".equals(kiddie.type))
				continue;
			if (kiddie.targetParent != null)
				WidgetFidget.contain(cont.widg, kiddie.targetParent);
			else if (kiddie.widg != null)
				WidgetFidget.contain(cont.widg, kiddie.widg);
		}
		if (cont.itemLayouts != null)
		{
			cont.itemLayoutWidg = mgroup.addWidget(PANEL);
			WidgetFidget.setName(cont.itemLayoutWidg, "ItemLayout");
			boolean hadKids = setupItemLayouts(cont, cont.itemLayoutWidg, cont.itemLayouts);
			if (!hadKids)
			{
				WidgetFidget.setProperty(cont.itemLayoutWidg, FIXED_WIDTH, "0");
				WidgetFidget.setProperty(cont.itemLayoutWidg, FIXED_HEIGHT, "0");
			}
		}
		if (cont.focusedLayouts != null)
		{
			cont.focusedLayoutWidg = mgroup.addWidget("wraplist".equals(controlTypeLc) ? ITEM : PANEL);
			WidgetFidget.setName(cont.focusedLayoutWidg, "FocusedLayout");
			setupItemLayouts(cont, cont.focusedLayoutWidg, cont.focusedLayouts);
		}
		if (cont.rulerLayouts != null)
		{
			cont.rulerLayoutWidg = mgroup.addWidget(PANEL);
			WidgetFidget.setName(cont.rulerLayoutWidg, "TimeColLayout");
			setupItemLayouts(cont, cont.rulerLayoutWidg, cont.rulerLayouts);
		}
		if (cont.channelLayouts != null)
		{
			cont.channelLayoutWidg = mgroup.addWidget(PANEL);
			WidgetFidget.setName(cont.channelLayoutWidg, "ChannelRowLayout");
			setupItemLayouts(cont, cont.channelLayoutWidg, cont.channelLayouts);
		}
		if (cont.focusedChannelLayouts != null)
		{
			cont.focusedChannelLayoutWidg = mgroup.addWidget(PANEL);
			WidgetFidget.setName(cont.focusedChannelLayoutWidg, "FocusedChannelRowLayout");
			setupItemLayouts(cont, cont.focusedChannelLayoutWidg, cont.focusedChannelLayouts);
		}

		if (cont.textcolor != null)
		{
			if (cont.themeWidg == null)
				cont.themeWidg = addWidgetNamed(cont.widg, THEME, "FontTheme");
			java.awt.Color theColor = parseColor(cont.textcolor);
			if (cont.selectedcolor != null)
			{
				if (cont.disabledcolor != null && cont.enableCondition != null)
				{
					java.awt.Color selColor = parseColor(cont.selectedcolor);
					java.awt.Color disColor = parseColor(cont.disabledcolor);
					String disabledExpr = "!(" + translateBooleanExpression(cont.enableCondition, cont) + ")";
					WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_COLOR, "=If(" + disabledExpr + ", \"0x" + Integer.toString(disColor.getRGB() & 0xFFFFFF, 16) + "\", " +
						"If(IsNodeChecked(ListItem), \"0x" + Integer.toString(selColor.getRGB() & 0xFFFFFF, 16) +
						"\", \"0x" + Integer.toString(theColor.getRGB() & 0xFFFFFF, 16) + "\"))");
					WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_ALPHA, "=If(" + disabledExpr + ", " + disColor.getAlpha() +
						", If(IsNodeChecked(ListItem), " + selColor.getAlpha() + ", " + theColor.getAlpha() + "))");
				}
				else
				{
					java.awt.Color selColor = parseColor(cont.selectedcolor);
					WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_COLOR, "=If(IsNodeChecked(ListItem), \"0x" + Integer.toString(selColor.getRGB() & 0xFFFFFF, 16) +
						"\", \"0x" + Integer.toString(theColor.getRGB() & 0xFFFFFF, 16) + "\")");
					WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_ALPHA, "=If(IsNodeChecked(ListItem), " + selColor.getAlpha() + ", " + theColor.getAlpha() + ")");
				}
			}
			else if (cont.disabledcolor != null && cont.enableCondition != null)
			{
				java.awt.Color disColor = parseColor(cont.disabledcolor);
				String disabledExpr = "!(" + translateBooleanExpression(cont.enableCondition, cont) + ")";
				WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_COLOR, "=If(" + disabledExpr + ", \"0x" + Integer.toString(disColor.getRGB() & 0xFFFFFF, 16) +
					"\", \"0x" + Integer.toString(theColor.getRGB() & 0xFFFFFF, 16) + "\")");
				WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_ALPHA, "=If(" + disabledExpr + ", " + disColor.getAlpha() + ", " + theColor.getAlpha() + ")");
			}
			else
			{
				WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_COLOR, "0x" + Integer.toString(theColor.getRGB() & 0xFFFFFF, 16));
				WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_ALPHA, Integer.toString(theColor.getAlpha()));
			}
		}
		if (cont.shadowcolor != null)
		{
			if (cont.themeWidg == null)
				cont.themeWidg = addWidgetNamed(cont.widg, THEME, "FontTheme");
			java.awt.Color shadow = parseColor(cont.shadowcolor);
			WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_SHADOW_COLOR, "0x" + Integer.toString(shadow.getRGB() & 0xFFFFFF, 16));
			WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_SHADOW_ALPHA, Integer.toString(shadow.getAlpha()));
			if (cont.widg.type() == TEXT)
				WidgetFidget.setProperty(cont.widg, TEXT_SHADOW, "true");
		}
		if (cont.focusedcolor != null)
		{
			if (cont.themeWidg == null)
				cont.themeWidg = addWidgetNamed(cont.widg, THEME, "FontTheme");
			java.awt.Color shadow = parseColor(cont.focusedcolor);
			WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_SELECTED_COLOR, "0x" + Integer.toString(shadow.getRGB() & 0xFFFFFF, 16));
			WidgetFidget.setProperty(cont.themeWidg, FOREGROUND_SELECTED_ALPHA, Integer.toString(shadow.getAlpha()));
		}
		if (cont.font != null)
		{
			FontData realFont = (FontData) fontMap.get(cont.font);
			if (realFont != null)
			{
				if (cont.themeWidg == null)
					cont.themeWidg = addWidgetNamed(cont.widg, THEME, "FontTheme");
				WidgetFidget.setProperty(cont.themeWidg, FONT_FACE, realFont.fontPath);
				WidgetFidget.setProperty(cont.themeWidg, FONT_SIZE, realFont.size);
				WidgetFidget.setProperty(cont.themeWidg, FONT_STYLE, realFont.style);
			}
		}
		if (cont.wrapmultiline || cont.type.equals("textbox"))
			WidgetFidget.setProperty(cont.widg, WRAP_TEXT, "true");
		if (cont.widg.type() == IMAGE)
		{
			// It defaults to "keep" inside containers; and if its a constant path inside a container then it is forced to "stretch"
			if (cont.isInsideContainer())
			{
				if (cont.aspectratio == null)
				{
					cont.aspectratio = "keep";
				}
				if (cont.texture != null && cont.texture.texturePath != null && cont.texture.texturePath.startsWith("\"" + mediaPath + "/") && cont.texture.texturePath.endsWith("\""))
				{
					cont.aspectratio = "stretch";
				}
			}
			if (cont.aspectratio != null)
			{
				if ("keep".equalsIgnoreCase(cont.aspectratio))
				{
					WidgetFidget.setProperty(cont.widg, PRESERVE_ASPECT_RATIO, "true");
					WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
				}
				else if ("stretch".equalsIgnoreCase(cont.aspectratio))
					WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
				else if ("scale".equalsIgnoreCase(cont.aspectratio))
				{
					WidgetFidget.setProperty(cont.widg, PRESERVE_ASPECT_RATIO, "true");
					WidgetFidget.setProperty(cont.widg, RESIZE_IMAGE, "true");
					WidgetFidget.setProperty(cont.widg, CROP_TO_FILL, "true");
				}
			}
			if (cont.imageFadeTime > 0)
				WidgetFidget.setProperty(cont.widg, DURATION, "" + cont.imageFadeTime);
		}
		if (cont.texture != null)
		{
			if (cont.infoContent == null)
			{
				Widget newAct = mgroup.addWidget(ACTION);
				WidgetFidget.setName(newAct, cont.texture.texturePath);
				if (cont.targetParent == null)
				{
					cont.targetParent = newAct;
					WidgetFidget.contain(newAct, cont.widg);
				}
				else
				{
					WidgetFidget.discontent(cont.targetParent, cont.widg);
					WidgetFidget.contain(newAct, cont.widg);
					WidgetFidget.contain(cont.targetParent, newAct);
				}
			}
			if (cont.texture.scalingInsets != null)
				WidgetFidget.setProperty(cont.widg, SCALING_INSETS, cont.texture.scalingInsets[0] + "," +
					cont.texture.scalingInsets[1] + "," + cont.texture.scalingInsets[2] + "," + cont.texture.scalingInsets[3]);
			if (cont.texture.diffuseImage != null)
			{
				WidgetFidget.setProperty(cont.widg, DIFFUSE_FILE, cont.texture.diffuseImage);
				WidgetFidget.setProperty(cont.widg, SCALE_DIFFUSE, cont.scaleDiffuse + "");
			}
			addFlipAndBGEffect(cont.widg, cont.texture);
		}
		if (cont.bordersize != 0)
			WidgetFidget.setProperty(cont.widg, INSETS, "" + cont.bordersize);
		if ("togglebutton".equals(controlTypeLc))
		{
			if (cont.altFocusedTexture != null)
			{
				Widget focusCond = addWidgetNamed(cont.widg, CONDITIONAL, ((cont.useAltTexture != null) ?
					(translateBooleanExpression(cont.useAltTexture, cont) + " && ") : "") + "Focused");
				cont.altFocusedImageWidg = addWidgetNamed(addWidgetNamed(focusCond, ACTION, cont.altFocusedTexture.texturePath),
					IMAGE, "Alt Focus Image");
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_HEIGHT, "1.0");
				if (cont.altFocusedTexture.scalingInsets != null)
					WidgetFidget.setProperty(cont.altFocusedImageWidg, SCALING_INSETS, cont.altFocusedTexture.scalingInsets[0] + "," +
						cont.altFocusedTexture.scalingInsets[1] + "," + cont.altFocusedTexture.scalingInsets[2] + "," + cont.altFocusedTexture.scalingInsets[3]);
				// I think focused/nonfocused textures are scaled by default
				WidgetFidget.setProperty(cont.altFocusedImageWidg, RESIZE_IMAGE, "true");
				if (cont.altFocusedTexture.diffuseImage != null)
					WidgetFidget.setProperty(cont.altFocusedImageWidg, DIFFUSE_FILE, cont.altFocusedTexture.diffuseImage);
				addFlipAndBGEffect(cont.altFocusedImageWidg, cont.altFocusedTexture);
				WidgetFidget.setProperty(cont.altFocusedImageWidg, ANIMATION, "LayerFocus");
			}
			if (cont.altUnfocusedTexture != null)
			{
				Widget focusCond = addWidgetNamed(cont.widg, CONDITIONAL, ((cont.useAltTexture != null) ?
					(translateBooleanExpression(cont.useAltTexture, cont) + " && ") : "") + "!Focused");
				cont.altUnfocusedImageWidg = addWidgetNamed(addWidgetNamed(focusCond, ACTION, cont.altUnfocusedTexture.texturePath),
					IMAGE, "Alt UnFocused Image");
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_HEIGHT, "1.0");
				if (cont.altUnfocusedTexture.scalingInsets != null)
					WidgetFidget.setProperty(cont.altUnfocusedImageWidg, SCALING_INSETS, cont.altUnfocusedTexture.scalingInsets[0] + "," +
						cont.altUnfocusedTexture.scalingInsets[1] + "," + cont.altUnfocusedTexture.scalingInsets[2] + "," + cont.altUnfocusedTexture.scalingInsets[3]);
				// I think focused/nonfocused textures are scaled by default
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, RESIZE_IMAGE, "true");
				if (cont.altUnfocusedTexture.diffuseImage != null)
					WidgetFidget.setProperty(cont.altUnfocusedImageWidg, DIFFUSE_FILE, cont.altUnfocusedTexture.diffuseImage);
				addFlipAndBGEffect(cont.altUnfocusedImageWidg, cont.altUnfocusedTexture);
			}
		}
		if (cont.focusedTexture != null)
		{
			Widget focusCond = addWidgetNamed(cont.widg, CONDITIONAL, ((cont.useAltTexture != null && cont.altFocusedImageWidg != null) ?
				("!" + translateBooleanExpression(cont.useAltTexture, cont) + " && ") : "") + "Focused");
			cont.focusedImageWidg = addWidgetNamed(addWidgetNamed(focusCond, ACTION, cont.focusedTexture.texturePath),
				IMAGE, "Focus Image");
			WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_HEIGHT, "1.0");
			if (cont.focusedTexture.scalingInsets != null)
				WidgetFidget.setProperty(cont.focusedImageWidg, SCALING_INSETS, cont.focusedTexture.scalingInsets[0] + "," +
					cont.focusedTexture.scalingInsets[1] + "," + cont.focusedTexture.scalingInsets[2] + "," + cont.focusedTexture.scalingInsets[3]);
			// I think focused/nonfocused textures are scaled by default
			WidgetFidget.setProperty(cont.focusedImageWidg, RESIZE_IMAGE, "true");
			if (cont.focusedTexture.diffuseImage != null)
				WidgetFidget.setProperty(cont.focusedImageWidg, DIFFUSE_FILE, cont.focusedTexture.diffuseImage);
			addFlipAndBGEffect(cont.focusedImageWidg, cont.focusedTexture);
			WidgetFidget.setProperty(cont.focusedImageWidg, ANIMATION, "LayerFocus");
		}
		if (cont.unfocusedTexture != null)
		{
			Widget focusCond = addWidgetNamed(cont.widg, CONDITIONAL, ((cont.useAltTexture != null && cont.altUnfocusedImageWidg != null) ?
				("!" + translateBooleanExpression(cont.useAltTexture, cont) + " && ") : "") + "!Focused");
			cont.unfocusedImageWidg = addWidgetNamed(addWidgetNamed(focusCond, ACTION, cont.unfocusedTexture.texturePath),
				IMAGE, "UnFocused Image");
			WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_HEIGHT, "1.0");
			if (cont.unfocusedTexture.scalingInsets != null)
				WidgetFidget.setProperty(cont.unfocusedImageWidg, SCALING_INSETS, cont.unfocusedTexture.scalingInsets[0] + "," +
					cont.unfocusedTexture.scalingInsets[1] + "," + cont.unfocusedTexture.scalingInsets[2] + "," + cont.unfocusedTexture.scalingInsets[3]);
			// I think focused/nonfocused textures are scaled by default
			WidgetFidget.setProperty(cont.unfocusedImageWidg, RESIZE_IMAGE, "true");
			if (cont.unfocusedTexture.diffuseImage != null)
				WidgetFidget.setProperty(cont.unfocusedImageWidg, DIFFUSE_FILE, cont.unfocusedTexture.diffuseImage);
			addFlipAndBGEffect(cont.unfocusedImageWidg, cont.unfocusedTexture);
		}
		if ("radiobutton".equals(controlTypeLc))
		{
			if (cont.radioOffTexture != null || cont.radioOnTexture != null)
			{
				Widget condy = addWidgetNamed(cont.widg, CONDITIONAL, translateBooleanExpression(cont.selected, cont));
				Widget bt = addWidgetNamed(condy, BRANCH, "true");
				Widget be = addWidgetNamed(condy, BRANCH, "else");
				if (cont.radioOffTexture != null)
				{
					cont.radioOffWidg = addWidgetNamed(addWidgetNamed(be, ACTION, cont.radioOffTexture.texturePath),
						IMAGE, "UnSelected Image");
					if (cont.radiowidth != null)
						WidgetFidget.setProperty(cont.radioOffWidg, FIXED_WIDTH, (cont.radioposx != null ? "" : "=8 + ") + parseInt(cont.radiowidth));
					else
						WidgetFidget.setProperty(cont.radioOffWidg, FIXED_WIDTH, "24"); // GUIRadioButton.cpp width + inset
					if (cont.radioheight != null)
						WidgetFidget.setProperty(cont.radioOffWidg, FIXED_HEIGHT, "" + parseInt(cont.radioheight));
					else
						WidgetFidget.setProperty(cont.radioOffWidg, FIXED_HEIGHT, "16"); // GUIRadioButton.cpp
					if (cont.radioposx != null)
						WidgetFidget.setProperty(cont.radioOffWidg, ANCHOR_X, "" + parseInt(cont.radioposx));
					else
						WidgetFidget.setProperty(cont.radioOffWidg, ANCHOR_X, "1.0");
					if (cont.radioposy != null)
						WidgetFidget.setProperty(cont.radioOffWidg, ANCHOR_Y, "" + parseInt(cont.radioposy));
					else
						WidgetFidget.setProperty(cont.radioOffWidg, ANCHOR_Y, "0.5");
					if (cont.radioposx == null)
						WidgetFidget.setProperty(cont.radioOffWidg, INSETS, "0,0,0,8"); // GUIRadioButton.cpp
					WidgetFidget.setProperty(cont.radioOffWidg, RESIZE_IMAGE, "true");
					WidgetFidget.setProperty(cont.radioOffWidg, PRESERVE_ASPECT_RATIO, "true");
					if (cont.radioOffTexture.scalingInsets != null)
						WidgetFidget.setProperty(cont.radioOffWidg, SCALING_INSETS, cont.radioOffTexture.scalingInsets[0] + "," +
							cont.radioOffTexture.scalingInsets[1] + "," + cont.radioOffTexture.scalingInsets[2] + "," + cont.radioOffTexture.scalingInsets[3]);
					if (cont.radioOffTexture.diffuseImage != null)
						WidgetFidget.setProperty(cont.radioOffWidg, DIFFUSE_FILE, cont.radioOffTexture.diffuseImage);
					addFlipAndBGEffect(cont.radioOffWidg, cont.radioOffTexture);
				}
				if (cont.radioOnTexture != null)
				{
					cont.radioOnWidg = addWidgetNamed(addWidgetNamed(bt, ACTION, cont.radioOnTexture.texturePath),
						IMAGE, "Selected Image");
					if (cont.radiowidth != null)
						WidgetFidget.setProperty(cont.radioOnWidg, FIXED_WIDTH, (cont.radioposx != null ? "" : "=8 + ") + parseInt(cont.radiowidth));
					else
						WidgetFidget.setProperty(cont.radioOnWidg, FIXED_WIDTH, "24"); // GUIRadioButton.cpp
					if (cont.radioheight != null)
						WidgetFidget.setProperty(cont.radioOnWidg, FIXED_HEIGHT, "" + parseInt(cont.radioheight));
					else
						WidgetFidget.setProperty(cont.radioOnWidg, FIXED_HEIGHT, "16"); // GUIRadioButton.cpp
					if (cont.radioposx != null)
						WidgetFidget.setProperty(cont.radioOnWidg, ANCHOR_X, "" + parseInt(cont.radioposx));
					else
						WidgetFidget.setProperty(cont.radioOnWidg, ANCHOR_X, "1.0");
					if (cont.radioposy != null)
						WidgetFidget.setProperty(cont.radioOnWidg, ANCHOR_Y, "" + parseInt(cont.radioposy));
					else
						WidgetFidget.setProperty(cont.radioOnWidg, ANCHOR_Y, "0.5");
					if (cont.radioposx == null)
						WidgetFidget.setProperty(cont.radioOnWidg, INSETS, "0,0,0,8"); // GUIRadioButton.cpp
					WidgetFidget.setProperty(cont.radioOnWidg, RESIZE_IMAGE, "true");
					WidgetFidget.setProperty(cont.radioOnWidg, PRESERVE_ASPECT_RATIO, "true");
					if (cont.radioOnTexture.scalingInsets != null)
						WidgetFidget.setProperty(cont.radioOnWidg, SCALING_INSETS, cont.radioOnTexture.scalingInsets[0] + "," +
							cont.radioOnTexture.scalingInsets[1] + "," + cont.radioOnTexture.scalingInsets[2] + "," + cont.radioOnTexture.scalingInsets[3]);
					if (cont.radioOnTexture.diffuseImage != null)
						WidgetFidget.setProperty(cont.radioOnWidg, DIFFUSE_FILE, cont.radioOnTexture.diffuseImage);
					addFlipAndBGEffect(cont.radioOnWidg, cont.radioOnTexture);
				}
			}
		}
		if ("multiimage".equals(controlTypeLc))
		{
// NOTE: THIS NEEDS TO BE UPDATED TO ACTUALLY CYCLE THROUGH THE MULTIPLE IMAGES
			if (cont.backgroundLoad)
				WidgetFidget.setProperty(cont.widg, BACKGROUND_LOAD, "true");
			if (cont.infoContent != null)
			{
				Widget newAct = mgroup.addWidget(ACTION);
				WidgetFidget.setName(newAct, "MultiImageFolder = " + translateImageExpression(cont.infoContent, cont));
				if (cont.targetParent == null)
				{
					cont.targetParent = newAct;
				}
				else
				{
					WidgetFidget.discontent(cont.targetParent, cont.widg);
					WidgetFidget.contain(cont.targetParent, newAct);
				}
				if (cont.imagepath != null)
				{
					Widget condy = addWidgetNamed(newAct, CONDITIONAL, "Size(MultiImageFolder) > 0");
					Widget bt = addWidgetNamed(condy, BRANCH, "true");
					Widget be = addWidgetNamed(condy, BRANCH, "else");
					Widget finalAct = addWidgetNamed(bt, ACTION, "If(IsDirectoryPath(MultiImageFolder), GetElement(DirectoryListing(MultiImageFolder, \"P\"), 0), MultiImageFolder)");
					WidgetFidget.contain(finalAct, cont.widg);
					String imagePathExpr = translateImagePath(cont.imagepath);
					if (cont.fallback != null)
						imagePathExpr = "If(IsEmpty(" + imagePathExpr + "), " + translateImagePath(cont.fallback) + ", " + imagePathExpr + ")";
					WidgetFidget.contain(addWidgetNamed(be, ACTION,
						"MultiImageFolder = " + imagePathExpr),
						finalAct);
				}
				else
				{
					WidgetFidget.contain(addWidgetNamed(newAct, ACTION, "If(IsDirectoryPath(MultiImageFolder), GetElement(DirectoryListing(MultiImageFolder, \"P\"), 0), MultiImageFolder)"), cont.widg);
				}
			}
			else if (cont.imagepath != null)
			{
				Widget newAct = mgroup.addWidget(ACTION);
				String imagePathExpr = translateImagePath(cont.imagepath);
				if (cont.fallback != null)
					imagePathExpr = "If(IsEmpty(" + imagePathExpr + "), " + translateImagePath(cont.fallback) + ", " + imagePathExpr + ")";
				WidgetFidget.setName(newAct, "MultiImageFolder = " + imagePathExpr);
				if (cont.targetParent == null)
				{
					cont.targetParent = newAct;
				}
				else
				{
					WidgetFidget.discontent(cont.targetParent, cont.widg);
					WidgetFidget.contain(cont.targetParent, newAct);
				}
				WidgetFidget.contain(addWidgetNamed(newAct, ACTION, "If(IsDirectoryPath(MultiImageFolder), GetElement(DirectoryListing(MultiImageFolder, \"P\"), 0), MultiImageFolder)"), cont.widg);
			}
		}
		else if ("progress".equals(controlTypeLc))
		{
			WidgetFidget.setProperty(cont.widg, LAYOUT, "Horizontal");
			if (cont.texturebg != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.texturebg.texturePath),
					IMAGE, "ProgressBackground");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.texturebg.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.texturebg.scalingInsets[0] + "," +
						cont.texturebg.scalingInsets[1] + "," + cont.texturebg.scalingInsets[2] + "," + cont.texturebg.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.texturebg.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.texturebg.diffuseImage);
				addFlipAndBGEffect(img, cont.texturebg);
				WidgetFidget.setProperty(img, BACKGROUND_COMPONENT, "true");
			}
			if (cont.textureleft != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.textureleft.texturePath),
					IMAGE, "ProgressLeft");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.textureleft.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.textureleft.scalingInsets[0] + "," +
						cont.textureleft.scalingInsets[1] + "," + cont.textureleft.scalingInsets[2] + "," + cont.textureleft.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.textureleft.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.textureleft.diffuseImage);
				addFlipAndBGEffect(img, cont.textureleft);
			}
			if (cont.texturemid != null && cont.infoContent != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.texturemid.texturePath),
					IMAGE, "ProgressIndicator");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "=" + translateStringExpression(cont.infoContent, cont));
				if (cont.texturemid.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.texturemid.scalingInsets[0] + "," +
						cont.texturemid.scalingInsets[1] + "," + cont.texturemid.scalingInsets[2] + "," + cont.texturemid.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.texturemid.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.texturemid.diffuseImage);
				addFlipAndBGEffect(img, cont.texturemid);
			}
			if (cont.textureright != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.textureright.texturePath),
					IMAGE, "ProgressRight");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.textureright.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.textureright.scalingInsets[0] + "," +
						cont.textureright.scalingInsets[1] + "," + cont.textureright.scalingInsets[2] + "," + cont.textureright.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.textureright.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.textureright.diffuseImage);
				addFlipAndBGEffect(img, cont.textureright);
			}
			if (cont.textureoverlay != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.textureoverlay.texturePath),
					IMAGE, "ProgressBackground");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.textureoverlay.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.textureoverlay.scalingInsets[0] + "," +
						cont.textureoverlay.scalingInsets[1] + "," + cont.textureoverlay.scalingInsets[2] + "," + cont.textureoverlay.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.textureoverlay.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.textureoverlay.diffuseImage);
				addFlipAndBGEffect(img, cont.textureoverlay);
				WidgetFidget.setProperty(img, BACKGROUND_COMPONENT, "true");
			}
		}
		else if (cont.infoContent != null)
		{
			Widget newAct = mgroup.addWidget(ACTION);
			WidgetFidget.setName(newAct, controlType.indexOf("image") != -1 ? translateImageExpression(cont.infoContent, cont) : translateStringExpression(cont.infoContent, cont));
			if (controlType.indexOf("image") == -1 && cont.infoContent.toLowerCase().indexOf("system.time") != -1)
				WidgetFidget.setName(cont.widg, "$Clock");
			else if (controlType.indexOf("image") == -1 && newAct.getName().indexOf("GetMediaTime()") != -1)
				WidgetFidget.setProperty(cont.widg, ANIMATION, "0,1000,0");
			if (cont.targetParent == null)
			{
				cont.targetParent = newAct;
				WidgetFidget.contain(newAct, cont.widg);
			}
			else
			{
				WidgetFidget.discontent(cont.targetParent, cont.widg);
				WidgetFidget.contain(newAct, cont.widg);
				WidgetFidget.contain(cont.targetParent, newAct);
			}
		}
		if (cont.numLabel != null)
			WidgetFidget.setName(cont.widg, cont.numLabel);
		if ("scrollbar".equals(controlTypeLc))
		{
//			cont.targetParent = mgroup.addWidget(ACTION);
//			WidgetFidget.setName(cont.targetParent, "ScrollbarFound = true");
			if (!cont.showonepage)
			{
				WidgetFidget.contain(cont.targetParent = addWidgetNamed(null/*cont.targetParent*/, CONDITIONAL, "!IsFirstVPage || !IsLastVPage || !IsLastHPage || !IsFirstHPage"),
					cont.widg);
			}
//			else
//				WidgetFidget.contain(cont.targetParent, cont.widg);
			if (cont.sliderBackgroundTexture != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, cont.sliderBackgroundTexture.texturePath),
					IMAGE, "ScrollBarBackground");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.sliderBackgroundTexture.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.sliderBackgroundTexture.scalingInsets[0] + "," +
						cont.sliderBackgroundTexture.scalingInsets[1] + "," + cont.sliderBackgroundTexture.scalingInsets[2] + "," + cont.sliderBackgroundTexture.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.sliderBackgroundTexture.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.sliderBackgroundTexture.diffuseImage);
				addFlipAndBGEffect(img, cont.sliderBackgroundTexture);
				WidgetFidget.setProperty(img, BACKGROUND_COMPONENT, "true");
			}
			Widget scroller = addWidgetNamed(cont.widg, ITEM, "ScrollerNib");
			if ("horizontal".equalsIgnoreCase(cont.orientation))
			{
//				WidgetFidget.setProperty(scroller, ANCHOR_X, "=Min(1.0, Max(0.0, (HScrollIndex - 1)*NumRowsPerPage*1.0/Max(1, (NumCols - NumRowsPerPage*NumColsPerPage + 1))))");
				WidgetFidget.setProperty(scroller, ANCHOR_X, "=Max(0, If(!IsEmpty(TableData), Max(1, HScrollIndex) - 1, HScrollIndex))*1.0/Max(1,NumHPages)");
				WidgetFidget.setProperty(scroller, ANCHOR_Y, "0.5");
//				WidgetFidget.setProperty(scroller, FIXED_WIDTH, "=NumRowsPerPage*NumColsPerPage*1.0/Max(1, NumCols)");
				WidgetFidget.setProperty(scroller, FIXED_WIDTH, "=1.0/Max(1,NumHPagesF)");
				WidgetFidget.setProperty(scroller, FIXED_HEIGHT, "1.0");
				addWidgetNamed(cont.widg, ACTION, "\"XOUT: Horizontal Mouse Scroll\"");
			}
			else
			{
				WidgetFidget.setProperty(scroller, ANCHOR_X, "0.5");
//				WidgetFidget.setProperty(scroller, ANCHOR_Y, "=Min(1.0, Max(0.0, (VScrollIndex - 1)*NumColsPerPage*1.0/Max(1, (NumRows - NumRowsPerPage*NumColsPerPage + 1))))");
				WidgetFidget.setProperty(scroller, ANCHOR_Y, "=Max(0, If(!IsEmpty(TableData), Max(1, VScrollIndex) - 1, VScrollIndex))*1.0/Max(1,NumVPages)");
				WidgetFidget.setProperty(scroller, FIXED_WIDTH, "1.0");
//				WidgetFidget.setProperty(scroller, FIXED_HEIGHT, "=NumRowsPerPage*NumColsPerPage*1.0/Max(1, NumRows)");
				WidgetFidget.setProperty(scroller, FIXED_HEIGHT, "=1.0/Max(1,NumVPagesF)");
				addWidgetNamed(cont.widg, ACTION, "\"XOUT: Vertical Mouse Scroll\"");
			}
			if ("horizontal".equalsIgnoreCase(cont.orientation))
			{
				Widget cond = addWidgetNamed(addWidgetNamed(scroller, LISTENER, "Left"), CONDITIONAL, "IsFirstHPage");
				addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "PassiveListen()");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "SageCommand(\"Page Left\")"),
					ACTION, "SetFocusForVariable(\"XBMCID\", XBMCID)");
				cond = addWidgetNamed(addWidgetNamed(scroller, LISTENER, "Right"), CONDITIONAL, "IsLastHPage");
				addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "PassiveListen()");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "SageCommand(\"Page Right\")"),
					ACTION, "SetFocusForVariable(\"XBMCID\", XBMCID)");
			}
			else
			{
				Widget cond = addWidgetNamed(addWidgetNamed(scroller, LISTENER, "Up"), CONDITIONAL, "IsFirstVPage");
				addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "PassiveListen()");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "SageCommand(\"Page Up\")"),
					ACTION, "SetFocusForVariable(\"XBMCID\", XBMCID)");
				cond = addWidgetNamed(addWidgetNamed(scroller, LISTENER, "Down"), CONDITIONAL, "IsLastVPage");
				addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "PassiveListen()");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "SageCommand(\"Page Down\")"),
					ACTION, "SetFocusForVariable(\"XBMCID\", XBMCID)");
			}
			if (cont.sliderBarTexture != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(addWidgetNamed(scroller, CONDITIONAL, "!Focused"),
					ACTION, cont.sliderBarTexture.texturePath),
					IMAGE, "ScrollBar");
				WidgetFidget.setProperty(img, ANCHOR_X, "0.5");
				WidgetFidget.setProperty(img, ANCHOR_Y, "0.5");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.sliderBarTexture.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.sliderBarTexture.scalingInsets[0] + "," +
						cont.sliderBarTexture.scalingInsets[1] + "," + cont.sliderBarTexture.scalingInsets[2] + "," + cont.sliderBarTexture.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.sliderBarTexture.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.sliderBarTexture.diffuseImage);
				addFlipAndBGEffect(img, cont.sliderBarTexture);
			}
			if (cont.sliderBarFocusTexture != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(addWidgetNamed(scroller, CONDITIONAL, "Focused"),
					ACTION, cont.sliderBarFocusTexture.texturePath),
					IMAGE, "ScrollBarFocused");
				WidgetFidget.setProperty(img, ANCHOR_X, "0.5");
				WidgetFidget.setProperty(img, ANCHOR_Y, "0.5");
				WidgetFidget.setProperty(img, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(img, FIXED_HEIGHT, "1.0");
				if (cont.sliderBarFocusTexture.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.sliderBarFocusTexture.scalingInsets[0] + "," +
						cont.sliderBarFocusTexture.scalingInsets[1] + "," + cont.sliderBarFocusTexture.scalingInsets[2] + "," + cont.sliderBarFocusTexture.scalingInsets[3]);
				WidgetFidget.setProperty(img, RESIZE_IMAGE, "true");
				if (cont.sliderBarFocusTexture.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.sliderBarFocusTexture.diffuseImage);
				addFlipAndBGEffect(img, cont.sliderBarFocusTexture);
			}
			if (cont.sliderNibTexture != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(addWidgetNamed(scroller, CONDITIONAL, "!Focused"),
					ACTION, cont.sliderNibTexture.texturePath),
					IMAGE, "Scroller");
				WidgetFidget.setProperty(img, ANCHOR_X, "0.5");
				WidgetFidget.setProperty(img, ANCHOR_Y, "0.5");
				if (cont.sliderNibTexture.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.sliderNibTexture.scalingInsets[0] + "," +
						cont.sliderNibTexture.scalingInsets[1] + "," + cont.sliderNibTexture.scalingInsets[2] + "," + cont.sliderNibTexture.scalingInsets[3]);
				if (cont.sliderNibTexture.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.sliderNibTexture.diffuseImage);
				addFlipAndBGEffect(img, cont.sliderNibTexture);
			}
			if (cont.sliderNibFocusTexture != null)
			{
				Widget img = addWidgetNamed(addWidgetNamed(addWidgetNamed(scroller, CONDITIONAL, "Focused"),
					ACTION, cont.sliderNibFocusTexture.texturePath),
					IMAGE, "ScrollerFocus");
				WidgetFidget.setProperty(img, ANCHOR_X, "0.5");
				WidgetFidget.setProperty(img, ANCHOR_Y, "0.5");
				if (cont.sliderNibFocusTexture.scalingInsets != null)
					WidgetFidget.setProperty(img, SCALING_INSETS, cont.sliderNibFocusTexture.scalingInsets[0] + "," +
						cont.sliderNibFocusTexture.scalingInsets[1] + "," + cont.sliderNibFocusTexture.scalingInsets[2] + "," + cont.sliderNibFocusTexture.scalingInsets[3]);
				if (cont.sliderNibFocusTexture.diffuseImage != null)
					WidgetFidget.setProperty(img, DIFFUSE_FILE, cont.sliderNibFocusTexture.diffuseImage);
				addFlipAndBGEffect(img, cont.sliderNibFocusTexture);
			}
		}
		if (cont.textLabel != null)
		{
			if (cont.textLabel.startsWith("[B]") && cont.themeWidg != null)
				WidgetFidget.setProperty(cont.themeWidg, FONT_STYLE, "Bold");
			else if (cont.textLabel.startsWith("[I]") && cont.themeWidg != null)
				WidgetFidget.setProperty(cont.themeWidg, FONT_STYLE, "Italic");
			String textLabelExpr = cont.dontTranslateTextLabel ? cont.textLabel : localizeStrings(cont.textLabel, true, cont);
			if (cont.fallback != null && !cont.dontTranslateTextLabel)
				textLabelExpr = "If(IsEmpty(" + textLabelExpr + "), " + localizeStrings(cont.fallback, true, cont) + ", " + textLabelExpr + ")";
			if ("button".equals(controlTypeLc) || "radiobutton".equals(controlTypeLc))
			{
				cont.labelWidg = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, textLabelExpr), TEXT, cont.textLabel);
				// If the '-' font is specified then hide the text by giving it zero alpha; XBMC seems to hide text w/ a font of '-'
				if ("-".equals(cont.font))
					WidgetFidget.setProperty(cont.labelWidg, BACKGROUND_ALPHA, "0");
				else if (cont.textLabel2 != null)
					cont.label2Widg = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, localizeStrings(cont.textLabel2, true, cont)), TEXT, cont.textLabel2);
			}
			else if ("togglebutton".equals(controlTypeLc))
			{
				if (cont.useAltTexture != null && cont.altLabel != null)
				{
					Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, translateBooleanExpression(cont.useAltTexture, cont));
					Widget bt = addWidgetNamed(cond, BRANCH, "true");
					Widget be = addWidgetNamed(cond, BRANCH, "else");
					cont.labelWidg = addWidgetNamed(addWidgetNamed(be, ACTION, textLabelExpr), TEXT, cont.textLabel);
					cont.altLabelWidg = addWidgetNamed(addWidgetNamed(bt, ACTION, localizeStrings(cont.altLabel, true, cont)),
						TEXT, cont.altLabel);
				}
				else
				{
					cont.labelWidg = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, textLabelExpr), TEXT, cont.textLabel);
				}
				// If the '-' font is specified then hide the text by giving it zero alpha; XBMC seems to hide text w/ a font of '-'
				if ("-".equals(cont.font))
					WidgetFidget.setProperty(cont.labelWidg, BACKGROUND_ALPHA, "0");
				else if (cont.textLabel2 != null && !"-".equals(cont.font))
					cont.label2Widg = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, localizeStrings(cont.textLabel2, true, cont)), TEXT, cont.textLabel2);

			}
			else
			{
				Widget newAct = mgroup.addWidget(ACTION);
				if (cont.infoContent != null)
					WidgetFidget.setName(newAct, "If(IsEmpty(this), " + textLabelExpr + ", this)");
				else
					WidgetFidget.setName(newAct, textLabelExpr);
				if (cont.targetParent == null)
				{
					cont.targetParent = newAct;
					WidgetFidget.contain(cont.targetParent, cont.widg);
					WidgetFidget.setName(cont.widg, cont.textLabel);
				}
				else
				{
					WidgetFidget.discontent(cont.targetParent, cont.widg);
					WidgetFidget.contain(newAct, cont.widg);
					WidgetFidget.contain(cont.targetParent, newAct);
				}
				if (cont.textLabel.toLowerCase().indexOf("system.time") != -1)
					WidgetFidget.setName(cont.widg, "$Clock");
				else if (newAct.getName().indexOf("GetMediaTime()") != -1)
					WidgetFidget.setProperty(cont.widg, ANIMATION, "0,1000,0");
			}
		}
		// Handle component alignment
		if ("label".equals(controlTypeLc) || "textbox".equals(controlTypeLc) || "fadelabel".equals(controlTypeLc))
		{
			if (cont.alignx == null || "left".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, TEXT_ALIGNMENT, "0.0");
				WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_X, "0.0");
			}
			else if ("center".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, TEXT_ALIGNMENT, "0.5");
				// I'm still not sure about this one; Empty makes it look like it centers on the posx; but there's other ones
				// where the posx is 0 and its the same width as the parent and then its just centered inside that
				// My new guess is that insideContainer changes this
				if (cont.isInsideContainer()/*!"0".equals(cont.posx)*/)
					WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_X, "0.5");
			}
			else if ("right".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, TEXT_ALIGNMENT, "1.0");
				if (!"textbox".equals(controlTypeLc))
					WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_X, "1.0");
			}
			if (cont.aligny == null || "top".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, "0.0");
//				WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_Y, "0.0");
			}
			else if ("center".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, "0.5");
//				WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_Y, "0.5");
			}
			else if ("bottom".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, "1.0");
//				WidgetFidget.setProperty(cont.widg, ANCHOR_POINT_Y, "1.0");
			}
		}
		else if ("image".equals(controlTypeLc) || "multiimage".equals(controlTypeLc) || "largeimage".equals(controlTypeLc))
		{
			if ("left".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, HALIGNMENT, (cont.texture != null && cont.texture.flipx) ? "1.0" : "0.0");
			}
			else if (cont.alignx == null || "center".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, HALIGNMENT, "0.5");
			}
			else if ("right".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.widg, HALIGNMENT, (cont.texture != null && cont.texture.flipx) ? "0,0" : "1.0");
			}
			if ("top".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, (cont.texture != null && cont.texture.flipy) ? "1.0" : "0.0");
			}
			else if (cont.aligny == null || "center".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, "0.5");
			}
			else if ("bottom".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.widg, VALIGNMENT, (cont.texture != null && cont.texture.flipy) ? "0.0" : "1.0");
			}
		}
		else if (cont.labelWidg != null &&
			("button".equals(controlTypeLc) || "selectbutton".equals(controlTypeLc) || "togglebutton".equals(controlTypeLc) || "radiobutton".equals(controlTypeLc) ||
			"spincontrol".equals(controlTypeLc) || "spincontrolex".equals(controlTypeLc)))
		{
			if (cont.alignx == null || "left".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.labelWidg, TEXT_ALIGNMENT, "0.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, TEXT_ALIGNMENT, "0.0");
				if (cont.textoffsetx == null)
				{
					WidgetFidget.setProperty(cont.labelWidg, ANCHOR_X, "0.0");
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_X, "0.0");
				}
				else
				{
					WidgetFidget.setProperty(cont.labelWidg, ANCHOR_X, parseInt(cont.textoffsetx) + "");
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_X, parseInt(cont.textoffsetx) + "");
				}
			}
			else if ("center".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.labelWidg, TEXT_ALIGNMENT, "0.5");
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_X, "0.5");
				if (cont.altLabelWidg != null)
				{
					WidgetFidget.setProperty(cont.altLabelWidg, TEXT_ALIGNMENT, "0.5");
					WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_X, "0.5");
				}
			}
			else if ("right".equals(cont.alignx))
			{
				WidgetFidget.setProperty(cont.labelWidg, TEXT_ALIGNMENT, "1.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, TEXT_ALIGNMENT, "1.0");
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_X, "1.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_X, "1.0");
				if (cont.textoffsetx != null)
				{
					WidgetFidget.setProperty(cont.labelWidg, INSETS, "0,0,0," + parseInt(cont.textoffsetx));
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, INSETS, "0,0,0," + parseInt(cont.textoffsetx));
				}
			}
			if (cont.aligny == null || "top".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.labelWidg, VALIGNMENT, "0.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, VALIGNMENT, "0.0");
				if (cont.textoffsety == null)
				{
					WidgetFidget.setProperty(cont.labelWidg, ANCHOR_Y, "0.0");
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_Y, "0.0");
				}
				else
				{
					WidgetFidget.setProperty(cont.labelWidg, ANCHOR_Y, parseInt(cont.textoffsety) + "");
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_Y, parseInt(cont.textoffsety) + "");
				}
			}
			else if ("center".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.labelWidg, VALIGNMENT, "0.5");
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_Y, "0.5");
				if (cont.altLabelWidg != null)
				{
					WidgetFidget.setProperty(cont.altLabelWidg, VALIGNMENT, "0.5");
					WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_Y, "0.5");
				}
			}
			else if ("bottom".equals(cont.aligny))
			{
				WidgetFidget.setProperty(cont.labelWidg, VALIGNMENT, "1.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, VALIGNMENT, "1.0");
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_Y, "1.0");
				if (cont.altLabelWidg != null)
					WidgetFidget.setProperty(cont.altLabelWidg, ANCHOR_Y, "1.0");
				if (cont.textoffsety != null)
				{
					WidgetFidget.setProperty(cont.labelWidg, INSETS, "0,0," + parseInt(cont.textoffsety) + ",0");
					if (cont.altLabelWidg != null)
						WidgetFidget.setProperty(cont.altLabelWidg, INSETS, "0,0," + parseInt(cont.textoffsety) + ",0");
				}
			}

			if (cont.label2Widg != null)
			{
				WidgetFidget.setProperty(cont.label2Widg, ANCHOR_X, "1.0");
				WidgetFidget.setProperty(cont.label2Widg, ANCHOR_Y, cont.labelWidg.getProperty(ANCHOR_Y));
				if (cont.textoffsetx != null)
					WidgetFidget.setProperty(cont.label2Widg, INSETS, "0,0,0," + cont.textoffsetx);
			}
		}
		if (cont.bordertexture != null && cont.widg.type() == IMAGE)
		{
			// Rearrange this so we can do a border that matches the AR of the image itself
			Widget panel1 = mgroup.addWidget(PANEL);
			WidgetFidget.setProperty(panel1, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
			WidgetFidget.setProperty(panel1, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
			WidgetFidget.setProperty(panel1, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
			WidgetFidget.setProperty(panel1, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			Widget borderImage = mgroup.addWidget(IMAGE);
			if (cont.bordertexture.scalingInsets != null)
				WidgetFidget.setProperty(borderImage, SCALING_INSETS, cont.bordertexture.scalingInsets[0] + "," +
						cont.bordertexture.scalingInsets[1] + "," + cont.bordertexture.scalingInsets[2] + "," + cont.bordertexture.scalingInsets[3]);
			if (cont.bordertexture.diffuseImage != null)
				WidgetFidget.setProperty(borderImage, DIFFUSE_FILE, cont.bordertexture.diffuseImage);
			addFlipAndBGEffect(borderImage, cont.bordertexture);
			WidgetFidget.setProperty(borderImage, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(borderImage, FIXED_HEIGHT, "1.0");
			WidgetFidget.setProperty(borderImage, RESIZE_IMAGE, "true");
			WidgetFidget.setProperty(borderImage, BACKGROUND_COMPONENT, "true");
			Widget borderAction = mgroup.addWidget(ACTION);
			WidgetFidget.setName(borderAction, cont.bordertexture.texturePath);
			WidgetFidget.contain(borderAction, borderImage);
			if ("true".equals(cont.widg.getProperty(PRESERVE_ASPECT_RATIO)))
			{
				Widget panel2 = addWidgetNamed(panel1, PANEL, "AR Matcher");
				WidgetFidget.setProperty(panel2, ANCHOR_X, cont.widg.getProperty(HALIGNMENT));
				WidgetFidget.setProperty(panel2, ANCHOR_Y, cont.widg.getProperty(VALIGNMENT));
				WidgetFidget.contain(panel2, borderAction);
				WidgetFidget.contain(panel2, cont.targetParent == null ? cont.widg : cont.targetParent);

				WidgetFidget.setProperty(cont.widg, ANCHOR_X, "");
				WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "");
				WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, "");
				WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, "");
			}
			else
			{
				WidgetFidget.contain(panel1, borderAction);
				WidgetFidget.contain(panel1, cont.targetParent == null ? cont.widg : cont.targetParent);
				WidgetFidget.setProperty(cont.widg, ANCHOR_X, "");
				WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "");
				WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, "1.0");
				WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, "1.0");
			}
			// Don't just do the targetParent because if animations affect it we want them applied to panel1 instead since it holds the size information
			cont.widg = panel1;
			cont.targetParent = panel1;
		}
		// Do visible after the bordertexture so we contain the proper widgets with conditionality
		if (cont.visible != null)
		{
			if (cont.allowhiddenfocus)
			{
				Widget hiddenFocus = addWidgetNamed(cont.widg, ATTRIBUTE, "AllowHiddenFocus");
				WidgetFidget.setProperty(hiddenFocus, VALUE, "true");
			}
			for (int i = 0; i < cont.visible.size(); i++)
			{
				Widget newCond = mgroup.addWidget(CONDITIONAL);
				WidgetFidget.setName(newCond, translateBooleanExpression(cont.visible.get(i).toString(), cont));
				if (cont.targetParent == null)
				{
					cont.targetParent = newCond;
					WidgetFidget.contain(newCond, cont.widg);
				}
				else
				{
					WidgetFidget.contain(newCond, cont.targetParent);
					cont.targetParent = newCond;
				}
			}
		}
		if (cont.onclicks != null && cont.widg.type() == ITEM)
		{
			Widget lastclicker = cont.widg;
			Widget altlastclicker = null;
			if (cont.onaltclicks != null && "togglebutton".equals(controlTypeLc) && cont.useAltTexture != null)
			{
				lastclicker = addWidgetNamed(cont.widg, CONDITIONAL, translateBooleanExpression(cont.useAltTexture, cont));
				altlastclicker = addWidgetNamed(lastclicker, BRANCH, "true");
				lastclicker = addWidgetNamed(lastclicker, BRANCH, "else");
			}
			for (int i = 0; i < cont.onclicks.size(); i++)
			{
				String currStr = cont.onclicks.get(i).toString();
				if (currStr.length() == 0 || currStr.equals("-"))
					continue;
				Widget newChain = createProcessChainFromExpression(cont.onclicks.get(i).toString(), cont);
				WidgetFidget.contain(lastclicker, newChain);
				lastclicker = newChain;
			}
			// Some components needs a refresh after they act
			if (lastclicker.type() != MENU &&
				("button".equals(controlTypeLc) || "radiobutton".equals(controlTypeLc) || "togglebutton".equals(controlTypeLc) || "selectbutton".equals(controlTypeLc)))
				addWidgetNamed(lastclicker, ACTION, "Refresh()");
			if (altlastclicker != null)
			{
				for (int i = 0; i < cont.onaltclicks.size(); i++)
				{
					Widget newChain = createProcessChainFromExpression(cont.onaltclicks.get(i).toString(), cont);
					WidgetFidget.contain(altlastclicker, newChain);
					altlastclicker = newChain;
				}
				// Some components needs a refresh after they act
				if (altlastclicker.type() != MENU &&
					("button".equals(controlTypeLc) || "radiobutton".equals(controlTypeLc) || "togglebutton".equals(controlTypeLc) || "selectbutton".equals(controlTypeLc)))
					addWidgetNamed(altlastclicker, ACTION, "Refresh()");
			}
		}
		if (cont.enableCondition != null)
			WidgetFidget.setProperty(cont.widg, FOCUSABLE_CONDITION, "=" + translateBooleanExpression(cont.enableCondition, cont));

		// dialogcontextmenu has its size determined by the internal components; and its not set at the menu level so
		// we can't make everything inside expand automatically
		// This messes up animation center points because they're calculated around the preferred size; not the maximum size
		if (!"label".equals(controlTypeLc) && !cont.win.menuName.equalsIgnoreCase("dialogcontextmenu"))
		{
			if (cont.width == null)
			{
//				WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, "1.0");
			}
			if (cont.height == null)
			{
//				WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, "1.0");
			}
		}

		// Calculate these now in case animations modify them with conditionals and later animations then need to know the original value
		// These can have format issues because they may not be integers if there is no zooming animation for the component
		int compx=0, compy=0, compw=0, comph=0;
		try{ compx = cont.widg.hasProperty(ANCHOR_X) ? parseInt(cont.widg.getProperty(ANCHOR_X)) : 0; } catch (NumberFormatException nfe){}
		try{ compy = cont.widg.hasProperty(ANCHOR_Y) ? parseInt(cont.widg.getProperty(ANCHOR_Y)) : 0; } catch (NumberFormatException nfe){}
		try{ compw = cont.widg.hasProperty(FIXED_WIDTH) ? parseInt(cont.widg.getProperty(FIXED_WIDTH)) : 0; } catch (NumberFormatException nfe){}
		try{ comph = cont.widg.hasProperty(FIXED_HEIGHT) ? parseInt(cont.widg.getProperty(FIXED_HEIGHT)) : 0; } catch (NumberFormatException nfe){}
		// Check for animations with an end state which would modify how the component is positioned during steady state.
		if (cont.anims != null)
		{
			processAnimations(cont.anims, cont.widg, cont, compx, compy, compw, comph);
		}

		// Now we do the more advanced widget creation stuff when we need to know the structure before we build it
		if ("wraplist".equals(controlTypeLc) || "list".equals(controlTypeLc) || "panel".equals(controlTypeLc))
		{
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
			boolean vert = !"horizontal".equalsIgnoreCase(cont.orientation);
			// First thing we have to do is create the action chain that populates the table. This is either menu
			// dependent (predefined data shown on a menu w/ XBMC) or its defined with the content items
			cont.pagingWidg = cont.tableWidg = addWidgetNamed(null, TABLE, cont.desc == null ? "Table" : (cont.desc + " Table"));
			if ("wraplist".equals(controlTypeLc))
				WidgetFidget.setProperty(cont.tableWidg, Widget.AUTO_REPEAT_ACTION, "0.25");
			addAttribute(cont.tableWidg, "FreeformCellSize", "true");
			if (cont.scrolltime >= 0)
				WidgetFidget.setProperty(cont.tableWidg, DURATION, cont.scrolltime + "");
			// Enforce table bounds; effects can go beyond it of course
			addAttribute(cont.tableWidg, "EnforceBounds", "true");
			Widget tsrc1=null, tsrc2=null;
			if (cont.contentItems != null)
			{
				addAttribute(cont.widg, "TableData", "null");
				Widget tabRootCond = addWidgetNamed(cont.widg, CONDITIONAL, "TableData == null");
				Widget chainParent1 = addWidgetNamed(tabRootCond, BRANCH, "true");
				Widget chainParent2 = null;
				chainParent1 = addWidgetNamed(chainParent1, ACTION, "TableData = new_java_util_Vector()");
				for (int i = 0; i < cont.contentItems.size(); i++)
				{
					ContentItem currItem = (ContentItem) cont.contentItems.get(i);
					if (currItem.visible == null)
					{
						chainParent1 = addWidgetNamed(chainParent1, ACTION, "java_util_Vector_add(TableData, CreateMediaNode(" +
							localizeStrings(currItem.label, true, cont) + ", " + localizeStrings(currItem.label2, true, cont) + ", " +
							translateImageExpression(currItem.thumb, cont) + ", " + translateImageExpression(currItem.icon, cont) +
							", " + currItem.id + "))");
						if (chainParent2 != null)
						{
							WidgetFidget.contain(chainParent2, chainParent1);
							chainParent2 = null;
						}
					}
					else
					{
						chainParent1 = addWidgetNamed(chainParent1, CONDITIONAL, translateBooleanExpression(currItem.visible, cont));
						if (chainParent2 != null)
							WidgetFidget.contain(chainParent2, chainParent1);
						chainParent2 = addWidgetNamed(chainParent1, BRANCH, "else");
						chainParent1 = addWidgetNamed(addWidgetNamed(chainParent1, BRANCH, "true"), ACTION, "java_util_Vector_add(TableData, CreateMediaNode(" +
							localizeStrings(currItem.label, true, cont) + ", " + localizeStrings(currItem.label2, true, cont) + ", " +
							translateImageExpression(currItem.thumb, cont) + ", " + translateImageExpression(currItem.icon, cont) +
							", " + currItem.id + "))");
					}
				}
				tsrc1 = addWidgetNamed(chainParent1, ACTION, "TableData");
				if (chainParent2 != null)
					WidgetFidget.contain(chainParent2, tsrc1);
				WidgetFidget.contain(addWidgetNamed(tabRootCond, BRANCH, "else"), tsrc1);
			}
			else if ("mypics".equals(menuName) || "myvideo".equals(menuName) || "mymusicsongs".equals(menuName)/* && cont.id == 50*/)
			{
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "CurrNode == null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "TableData = DataUnion(CreateMediaNode(\"" +
					("mypics".equals(menuName) ? "Picture" : ("myvideo".equals(menuName) ? "Video" : "Music")) + " Library\", \"\", " + defaultImageMap.get("harddisk") + "," +
					" " + defaultImageMap.get("harddisk") + ", \"Library\"), CreateMediaNode(\"Add Source\", \"\", " + defaultImageMap.get("addsource")+ ", " + defaultImageMap.get("addsource")+ ", \"AddSource\"))");
				tsrc2 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"),
					ACTION, "TableData = DataUnion(CreateMediaNode(\"..\", \"\", " + defaultImageMap.get("folderback")+ ", " + defaultImageMap.get("folderback")+ ", \"..\"), GetNodeChildren(CurrNode))");
			}
			else if ("myvideonav".equals(menuName) || "mymusicnav".equals(menuName)/* && cont.id == 50*/)
			{
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "GetNodeParent(CurrNode) == null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "TableData = new_java_util_Vector(GetNodeChildren(CurrNode))");
				tsrc2 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"),
					ACTION, "TableData = DataUnion(CreateMediaNode(\"..\", \"\", " + defaultImageMap.get("folderback")+ ", " + defaultImageMap.get("folderback")+ ", \"..\"), GetNodeChildren(CurrNode), " +
					"If(GetNodeDataType(CurrNode) == \"Virtual\" && GetNodePrimaryLabel(CurrNode) == LocalizeString(\"Playlists\"), CreateMediaNode(\"" +
					stringMap.get("525") + "\", \"\", null, null, \"New Playlist\"), null))");
			}
			else if ("dialogpictureinfo".equals(menuName))
			{
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg,
					ACTION, "TableData = new_java_util_Vector()"),
					ACTION, "java_util_Vector_add(TableData, CreateMediaNode(\"File Name\", GetFileNameFromPath(GetFileForSegment(MediaFile, 0)), null, null, null))"),
					ACTION, "java_util_Vector_add(TableData, CreateMediaNode(\"File Path\", GetPathParentDirectory(GetFileForSegment(MediaFile, 0)), null, null, null))"),
					ACTION, "java_util_Vector_add(TableData, CreateMediaNode(\"File Size\", (GetSize(MediaFile)/100)/10.0 + \" KB\", null, null, null))"),
					ACTION, "java_util_Vector_add(TableData, CreateMediaNode(\"File Date/Time\", PrintDateShort(GetFileStartTime(MediaFile)) + \" \" + PrintTimeShort(GetFileStartTime(MediaFile)), null, null, null))"),
					ACTION, "FullDesc = GetShowDescription(MediaFile)"),
					ACTION, "toker = new_java_util_StringTokenizer(FullDesc, \"\\r\\n\")"),
					CONDITIONAL, "java_util_StringTokenizer_hasMoreTokens(toker)");
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond,
					BRANCH, "true"),
					ACTION, "token = java_util_StringTokenizer_nextToken(toker)"),
					ACTION, "idx = StringIndexOf(token, \":\")"),
					ACTION, "java_util_Vector_add(TableData, CreateMediaNode(Substring(token, 0, idx), java_lang_String_trim(Substring(token, idx + 1, -1)), null, null, null))"),
					cond);
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "TableData");
			}
			else if ("mymusicplaylist".equals(menuName))
			{
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(cont.widg, ACTION, "TableData = new_java_util_Vector(GetNodeChildren(GetMediaView(\"Playlist\", If(GetCurrentPlaylist() == null, GetNowPlayingList(), GetCurrentPlaylist()))))");
			}
			else if ("dialogalbuminfo".equals(menuName))
			{
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(cont.widg, ACTION, "TableData = new_java_util_Vector(GetNodeChildren(GetMediaView(\"MediaFile\", GetAlbumTracks(Album))))");
			}
			else if ("dialogvideoinfo".equals(menuName))
			{
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(cont.widg, ACTION, "TableData = new_java_util_Vector(GetNodeChildren(GetMediaView(\"Actor\", GetPeopleAndCharacterListInShow(ListItem))))");
			}
			else if ("filebrowser".equals(menuName))
			{
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "GetNodeParent(CurrNode) == null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "TableData = new_java_util_Vector(GetNodeChildren(CurrNode))");
				tsrc2 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "TableData = DataUnion(CreateMediaNode(\"..\", \"\", " +
					defaultImageMap.get("folderback")+ ", " + defaultImageMap.get("folderback")+ ", \"..\"), GetNodeChildren(CurrNode))");
			}
			else if ("filemanager".equals(menuName) && (cont.id == 20 || cont.id == 21))
			{
				String sidePrefix = (cont.id == 20) ? "Left" : "Right";
				// Left/Right file list
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "GetNodeParent(" + sidePrefix + "CurrNode) == null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "TableData = new_java_util_Vector(GetNodeChildren(" + sidePrefix + "CurrNode))");
				tsrc2 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "TableData = DataUnion(CreateMediaNode(\"..\", \"\", " +
					defaultImageMap.get("folderback")+ ", " + defaultImageMap.get("folderback")+ ", \"..\"), GetNodeChildren(" + sidePrefix + "CurrNode))");
			}
			else if ("mymusicplaylisteditor".equals(menuName) && cont.id == 50)
			{
				// browser list
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "BrowserCurrNode == null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"), ACTION, "TableData = DataUnion(FilesRootNode, MusicNavRootNode)");
				tsrc2 = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"),
					ACTION, "TableData = DataUnion(CreateMediaNode(\"..\", \"\", " + defaultImageMap.get("folderback")+ ", " + defaultImageMap.get("folderback")+ ", \"..\"), GetNodeChildren(BrowserCurrNode))");
			}
			else if ("mymusicplaylisteditor".equals(menuName) && cont.id == 100)
			{
				// Playlist list
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(cont.widg, ACTION, "TableData = new_java_util_Vector(GetNodeChildren(GetMediaView(\"Playlist\", CurrPlaylist)))");
			}
			else if ("mytv".equals(menuName) && (cont.id == 11 || cont.id == 12))
			{
				// Current airings on all channels
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "CurrNode = GetMediaView(\"AiringsAndChannels\", Sort(GetAiringsOnViewableChannelsAtTime(Time(), Time() + 1000, false), false, \"ChannelNumber\", \"GetAiringChannelNumber\"))"),
					ACTION, "TableData = GetNodeChildren(CurrNode)");
			}
			else if ("mytv".equals(menuName) && cont.id == 13)
			{
				// Recordings list
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "CurrNode = GetMediaSource(\"TV\")"),
					ACTION, "SetNodeSort(CurrNode, \"Date\", false)"), ACTION, "TableData = GetNodeChildren(CurrNode)");
			}
			else if ("mytv".equals(menuName) && cont.id == 14)
			{
				// Recordings schedule
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "CurrNode = GetMediaView(\"Airing\", GetScheduledRecordings())"),
					ACTION, "TableData = GetNodeChildren(CurrNode)");
			}
			else if ("mytv".equals(menuName) && cont.id == 15)
			{
				// All future airings on current channel
				addAttribute(cont.widg, "TableData", "null");
				tsrc1 = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "CurrNode = GetMediaView(\"Airings\", GetAiringsOnChannelAtTime(If(GetCurrentMediaFile() == null, GetChannelForStationID(GetProperty(\"videoframe/last_station_id\", null)), GetCurrentMediaFile()), Time(), Time()*2, true))"),
					ACTION, "TableData = GetNodeChildren(CurrNode)");
			}
			else if ("mytv".equals(menuName) && cont.id == 16)
			{
				// Now/next airings on all channels
				addAttribute(cont.widg, "TableData", "null");
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "NowNotNext");
				tsrc1 = addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "true"),
					ACTION, "CurrNode = GetMediaView(\"Airings\", Sort(GetAiringsOnViewableChannelsAtTime(Time(), Time() + 1000, false), false, \"ChannelNumber\", \"GetAiringChannelNumber\"))"),
					ACTION, "TableData = GetNodeChildren(CurrNode)");
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"),
					ACTION, "CurrNode = GetMediaView(\"Airings\", Sort(GetAiringsOnViewableChannelsAtTime(Time(), Time() + 3600000, true), false, \"ChannelNumber\", \"GetAiringChannelNumber\"))"),
					tsrc1);
			}
			else if ("mytv".equals(menuName) && cont.id == 17)
			{
				// TV search results
				tsrc1 = addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "CurrNode = GetMediaView(\"Airings\", TVSearchResults)"),
					ACTION, "TableData = GetNodeChildren(CurrNode)");
			}
			else
			{
				WidgetFidget.contain(addWidgetNamed(cont.widg, ACTION, "GetMediaFiles()"), cont.tableWidg);
			}
			if (tsrc1 != null)
			{
				if ("wraplist".equals(controlTypeLc))
				{
					Widget cond = addWidgetNamed(tsrc1, CONDITIONAL, "Size(TableData) <= " + (vert ? "NumRowsPerPage" : "NumColsPerPage"));
					if (tsrc2 != null)
						WidgetFidget.contain(tsrc2, cond);
					Widget be = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "TableData");
					WidgetFidget.contain(be, cont.tableWidg);
					Widget bt = addWidgetNamed(cond, BRANCH, "true");
					bt = addWidgetNamed(bt, ACTION, "NewTableData = new_java_util_Vector(TableData)");
					bt = addWidgetNamed(bt, ACTION, "java_util_Vector_addAll(NewTableData, TableData)");
					cond = addWidgetNamed(bt, CONDITIONAL, "Size(NewTableData) <= " + (vert ? "NumRowsPerPage" : "NumColsPerPage"));
					bt = addWidgetNamed(cond, BRANCH, "true");
					be = addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"), ACTION, "TableData = NewTableData");
					WidgetFidget.contain(be, cont.tableWidg);
					bt = addWidgetNamed(bt, ACTION, "java_util_Vector_addAll(NewTableData, TableData)");
					WidgetFidget.contain(bt, cond);
				}
				else
				{
					WidgetFidget.contain(tsrc1, cont.tableWidg);
					if (tsrc2 != null)
						WidgetFidget.contain(tsrc2, cont.tableWidg);
				}

			}
			String focusedLayWidth = cont.focusedLayoutWidg.getProperty(FIXED_WIDTH);
			String focusedLayHeight = cont.focusedLayoutWidg.getProperty(FIXED_HEIGHT);
			String itemLayWidth = cont.itemLayoutWidg.getProperty(FIXED_WIDTH);
			String itemLayHeight = cont.itemLayoutWidg.getProperty(FIXED_HEIGHT);
			// We subtract 5 instead of 1 because there's a case where XBMC used 8 rows for size 160 over dimension 1281 (which is just over 8; is its' not doing a ceil, but some kind of rounding)
			// Now its 21 because we found a case that needed it to be that high
/*			String tableSizeV = "=" + (vert ? "2" : "1") + " + Round(java_lang_Math_floor((1.0*(" + cont.getRealHeight() + " - (" + (focusedLayHeight.startsWith("=") ? focusedLayHeight.substring(1) : focusedLayHeight) +
				") - " + (vert ? "21" : "0") + ")) / (" + (itemLayHeight.startsWith("=") ? itemLayHeight.substring(1) : itemLayHeight) + ")))";
			String tableSizeH = "=" + (vert ? "1" : "2") + " + Round(java_lang_Math_floor((1.0*(" + cont.getRealWidth() + " - (" + (focusedLayWidth.startsWith("=") ? focusedLayWidth.substring(1) : focusedLayWidth) +
				") - " + (vert ? "0" : "21") + ")) / (" + (itemLayWidth.startsWith("=") ? itemLayWidth.substring(1) : itemLayWidth) + ")))";
*/ // New technique here based off the simple way XBMC calculates it 1 + (int)((float)totalsize - focusSize)/itemSize; but it's different for Panels vs. lists
/*			try
			{
				tableSizeV = (int)Math.ceil(((float)parseInt(cont.height)) / Math.max(parseInt(cont.itemLayoutWidg.getProperty(FIXED_HEIGHT)),
					parseInt(cont.focusedLayoutWidg.getProperty(FIXED_HEIGHT))));
			}catch (NumberFormatException nfe){}
			try
			{
				tableSizeH = (int)Math.ceil(((float)parseInt(cont.width)) / Math.max(parseInt(cont.itemLayoutWidg.getProperty(FIXED_WIDTH)),
					parseInt(cont.focusedLayoutWidg.getProperty(FIXED_WIDTH))));
			}catch (NumberFormatException nfe){}
*/
			// NOTE: The proper way this is supposed to work is that the table will clip any rendering of children beyond its size for all tables. BUT when
			// an animation operation is performed; those function outside of the clipping rectangles. For 1D tables; determining the proper number of elements
			// in the one column/row that has variance is easy; we just need to have a number large enough so we fill the display. The number of items per page
			// doesn't really have an effect in the 1D case aside from determining scroll amount. For the 2D case; the page size does have an impact. It determines
			// the size in the dimension that we don't scroll along; and this number WILL BE rounded down. In the other dimension; we follow the same rule
			// as for the 1D table where we just let the clipping rectangle finish off where we need to stop rendering.
			// So later once we do clipping properly and animations; then we should change these calculations to match the above rules.
			if ("panel".equals(controlTypeLc))
			{
				String tableSizeV = "=Round(java_lang_Math_floor((1.0*(" + cont.getRealHeight() + ")) / Max(1, (" + (itemLayHeight.startsWith("=") ? itemLayHeight.substring(1) : itemLayHeight) + "))))";
				String tableSizeH = "=Round(java_lang_Math_floor((1.0*(" + cont.getRealWidth() + ")) / Max(1, (" + (itemLayWidth.startsWith("=") ? itemLayWidth.substring(1) : itemLayWidth) + "))))";
				WidgetFidget.setProperty(cont.tableWidg, NUM_ROWS, "" + tableSizeV);
				WidgetFidget.setProperty(cont.tableWidg, NUM_COLS, "" + tableSizeH);
			}
			else
			{
				String tableSizeV = "=" + ("wraplist".equals(controlTypeLc) ? "2" : "1") + " + Round(java_lang_Math_floor(Max(0, (1.0*(" + cont.getRealHeight() + " - (" + (focusedLayHeight.startsWith("=") ? focusedLayHeight.substring(1) : focusedLayHeight) +
					") - 1))) / Max(1, (" + (itemLayHeight.startsWith("=") ? itemLayHeight.substring(1) : itemLayHeight) + "))))";
				String tableSizeH = "=" + ("wraplist".equals(controlTypeLc) ? "2" : "1") + " + Round(java_lang_Math_floor(Max(0, (1.0*(" + cont.getRealWidth() + " - (" + (focusedLayWidth.startsWith("=") ? focusedLayWidth.substring(1) : focusedLayWidth) +
					") - 1))) / Max(1, (" + (itemLayWidth.startsWith("=") ? itemLayWidth.substring(1) : itemLayWidth) + "))))";
				WidgetFidget.setProperty(cont.tableWidg, vert ? NUM_ROWS : NUM_COLS, "" + (vert ? tableSizeV : tableSizeH));
				WidgetFidget.setProperty(cont.tableWidg, vert ? NUM_COLS : NUM_ROWS, "1");
			}
			WidgetFidget.setProperty(cont.tableWidg, DIMENSIONS, vert ? "1" : "2");
			if ("wraplist".equals(controlTypeLc))
				WidgetFidget.setProperty(cont.tableWidg, TABLE_WRAPPING, vert ? "1" : "2");
			WidgetFidget.setProperty(cont.tableWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
			WidgetFidget.setProperty(cont.tableWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			WidgetFidget.setProperty(cont.tableWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
			WidgetFidget.setProperty(cont.tableWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
			WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, null);
			WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, null);
			WidgetFidget.setProperty(cont.widg, ANCHOR_X, null);
			WidgetFidget.setProperty(cont.widg, ANCHOR_Y, null);

			Widget tableCompWidg = addWidgetNamed(cont.tableWidg, TABLECOMPONENT, "ListItem");
			WidgetFidget.setProperty(tableCompWidg, TABLE_SUBCOMP, "Cell");
			WidgetFidget.setProperty(tableCompWidg, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(tableCompWidg, FIXED_HEIGHT, "1.0");

			Widget cellPanel = addWidgetNamed(tableCompWidg, !"wraplist".equals(controlTypeLc) ? ITEM : PANEL, "Table Cell");
//			WidgetFidget.setProperty(cellPanel, FIXED_WIDTH, "1.0");
//			WidgetFidget.setProperty(cellPanel, FIXED_HEIGHT, "1.0");
/*			if (cont.allowhiddenfocus)
			{
				Widget hiddenFocus = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "AllowHiddenFocus");
				WidgetFidget.setProperty(hiddenFocus, VALUE, "true");
				WidgetFidget.contain(tableCompWidg, hiddenFocus);
				WidgetFidget.contain(cellPanel, hiddenFocus);
			}
*/
			// Now setup the fixed focus conditional
			Widget fixedCond = null;
			if ("wraplist".equals(controlTypeLc))
			{
				// It seems like they use a zero-based offset for even size tables; but a 1-based offset for odd-sized tables
				// probably some kind of rounding issue in their code....
//				String fixedCondStr = vert ? (cont.focusposition + " - If((Max(NumRowsPerPage, 1)/2)*2 == Max(NumRowsPerPage, 1), 1, 0) == (TableRow - VScrollIndex + NumRows) % Max(NumRows, 1)") :
//					(cont.focusposition + " - If((Max(NumColsPerPage, 1)/2)*2 == Max(NumColsPerPage, 1), 1, 0) == (TableRow - HScrollIndex + NumCols) % Max(NumCols, 1)");
				String fixedCondStr = vert ? (cont.focusposition + " == (TableRow - VScrollIndex + NumRows) % Max(NumRows, 1)") :
					(cont.focusposition + " == (TableRow - HScrollIndex + NumCols) % Max(NumCols, 1)");
				fixedCond = addWidgetNamed(cellPanel, CONDITIONAL, fixedCondStr);
				// Be sure the focused one is on the top of the z order
				WidgetFidget.setProperty(cellPanel, Z_OFFSET, "=If(" + fixedCondStr + ", 1, 0)");
				addAttribute(cont.tableWidg, "ListFocusIndex", cont.focusposition + "");
			}
			else
			{
				fixedCond = addWidgetNamed(cellPanel, CONDITIONAL, "If(false, \"Focused\", LastFocus == ListItem || (LastFocus == null && TableRow == " + (vert ? "VScrollIndex" : "HScrollIndex") + "))");
				// Be sure the focused one is on the top of the z order
				WidgetFidget.setProperty(cellPanel, Z_OFFSET, "=If(LastFocus == ListItem || LastFocus == null, 1, 0)");
				addAttribute(cont.tableWidg, "LastFocus", "LastFocus");
			}

			Widget elseBranch = addWidgetNamed(fixedCond, BRANCH, "else");
			Widget trueBranch = addWidgetNamed(fixedCond, BRANCH, "true");

			WidgetFidget.contain(trueBranch, cont.focusedLayoutWidg);
			WidgetFidget.contain(elseBranch, cont.itemLayoutWidg);
			Widget upDirAction = null;
			Widget focusHook = null;
			if (cont.contentItems != null)
			{
				// Now setup the switch/case for the actions we execute
				Widget actionCond = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, CONDITIONAL, "GetNodeDataObject(ListItem)");
				// Setup the attributes for the content ListItem data
/*				String labels = "CreateArray(";
				String labels2 = "CreateArray(";
				String icons = "CreateArray(";
				String thumbs = "CreateArray(";
				String xbmcids = "CreateArray(";
*/				for (int i = 0; i < cont.contentItems.size(); i++)
				{
					ContentItem currCont = (ContentItem) cont.contentItems.get(i);
					if (currCont.onclicks != null)
					{
						Widget branchy = addWidgetNamed(actionCond, BRANCH, "" + currCont.id);
						for (int j = 0; j < currCont.onclicks.size(); j++)
						{
							String currStr = currCont.onclicks.get(j).toString();
							if (currStr.length() == 0 || currStr.equals("-"))
								continue;
							Widget newAct = createProcessChainFromExpression(currCont.onclicks.get(j).toString(), cont);
							WidgetFidget.contain(branchy, newAct);
							branchy = newAct;
						}
					}
/*					labels += localizeStrings(currCont.label, true);
					labels2 += localizeStrings(currCont.label2, true);
					icons += translateExpression(currCont.icon, cont, true);
					thumbs += translateExpression(currCont.thumb, cont, true);
					xbmcids += currCont.id;
					if (i < cont.contentItems.size() - 1)
					{
						labels += ", ";
						labels2 += ", ";
						icons += ", ";
						thumbs += ", ";
						xbmcids += ", ";
					}
*/				}
/*				labels += ")";
				labels2 += ")";
				icons += ")";
				thumbs += ")";
				xbmcids += ")";
*/				if ("wraplist".equals(controlTypeLc))
				{
					Widget mouseClicker = addWidgetNamed(cont.itemLayoutWidg, LISTENER, "MouseClick");
					WidgetFidget.contain(mouseClicker, actionCond);
				}
				if ("wraplist".equals(controlTypeLc))
				{
// NOTE: This used to put the ItemXBMCID under the focused/item widgets independently; but I moved it up a level so it was the first child of the table component
// so that when we found the Container.HasFocus element we could just check the ListFocusIndex child of the tablecomponent to get it
					addAttribute(cellPanel, "ItemXBMCID", "GetNodeDataObject(ListItem)");
//					WidgetFidget.contain(cont.focusedLayoutWidg, attribID);
//					attribID = addWidgetNamed(cont.itemLayoutWidg, ATTRIBUTE, "ParentDashItemXBMCID");
//					WidgetFidget.setProperty(attribID, VALUE, "XBMCID + \"-\" + GetElement(ListItemXBMCIDs, ListItem)");
//					WidgetFidget.contain(cont.focusedLayoutWidg, attribID);
				}
				else
				{
					addAttribute(cellPanel, "ItemXBMCID", "GetNodeDataObject(ListItem)");
//					Widget attribID = addWidgetNamed(cellPanel, ATTRIBUTE, "ItemXBMCID");
//					WidgetFidget.setProperty(attribID, VALUE, "GetElement(ListItemXBMCIDs, ListItem)");
//					attribID = addWidgetNamed(cellPanel, ATTRIBUTE, "ParentDashItemXBMCID");
//					WidgetFidget.setProperty(attribID, VALUE, "XBMCID + \"-\" + GetElement(ListItemXBMCIDs, ListItem)");
				}
/*				Widget itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "ListItemLabels");
				WidgetFidget.setProperty(itemAttrib, VALUE, labels);
				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "ListItemLabels2");
				WidgetFidget.setProperty(itemAttrib, VALUE, labels2);
				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "ListItemIcons");
				WidgetFidget.setProperty(itemAttrib, VALUE, icons);
				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "ListItemThumbs");
				WidgetFidget.setProperty(itemAttrib, VALUE, thumbs);
				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "ListItemXBMCIDs");
				WidgetFidget.setProperty(itemAttrib, VALUE, xbmcids);*/
//				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "RetainedFocusItemXBMCID");
//				WidgetFidget.setProperty(itemAttrib, VALUE, "null");
//				itemAttrib = addWidgetNamed(cont.tableWidg, ATTRIBUTE, "RetainedFocusParentDashItemXBMCID");
//				WidgetFidget.setProperty(itemAttrib, VALUE, "null");
//				Widget focusHook = addWidgetNamed("wraplist".equalsIgnoreCase(controlType) ? cont.focusedLayoutWidg : cellPanel, HOOK, "FocusGained");
//				addWidgetNamed(addWidgetNamed(focusHook, ACTION, "RetainedFocusItemXBMCID = ItemXBMCID"),
//					ACTION, "RetainedFocusParentDashItemXBMCID = ParentDashItemXBMCID");

/*				itemAttrib = addWidgetNamed(cellPanel, ATTRIBUTE, "ListItemLabel");
				WidgetFidget.setProperty(itemAttrib, VALUE, "GetElement(ListItemLabels, ListItem)");
				itemAttrib = addWidgetNamed(cellPanel, ATTRIBUTE, "ListItemLabel2");
				WidgetFidget.setProperty(itemAttrib, VALUE, "GetElement(ListItemLabels2, ListItem)");
				itemAttrib = addWidgetNamed(cellPanel, ATTRIBUTE, "ListItemIcon");
				WidgetFidget.setProperty(itemAttrib, VALUE, "GetElement(ListItemIcons, ListItem)");
				itemAttrib = addWidgetNamed(cellPanel, ATTRIBUTE, "ListItemThumb");
				WidgetFidget.setProperty(itemAttrib, VALUE, "GetElement(ListItemThumbs, ListItem)");
*/			}
			else if ("mypics".equals(menuName) || "myvideo".equals(menuName) || "mymusicsongs".equals(menuName) ||
				"mymusicnav".equals(menuName) || "myvideonav".equals(menuName) || ("mytv".equals(menuName) && (cont.id == 13 || cont.id == 11 || cont.id == 12 || cont.id == 16 || cont.id == 17)))
			{
				focusHook = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, HOOK, "FocusGained");
				addWidgetNamed(addWidgetNamed(focusHook, ACTION, "MenuListItem = ListItem"),
					ACTION, "ActiveContainerXBMCID = ContainerXBMCID");
				if ("mytv".equals(menuName) && cont.id == 16)
				{
					addWidgetNamed(addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, CONDITIONAL, "NowNotNext"), ACTION,
						"\"XOUT: SelectMedia(ListItem)\"");
				}
				else
					addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, ACTION, "\"XOUT: SelectMedia(ListItem)\"");

				// These are the actions for when an item is selected
				if ("mypics".equals(menuName))
				{
					// Also add the picture information option
					WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel,
						LISTENER, "Info"), CONDITIONAL, "GetNodeDataType(ListItem) == \"MediaFile\""), ACTION, "MediaFile = GetNodeDataObject(ListItem)"), resolveMenuWidget("dialogpictureinfo"));
				}
				else if ("myvideo".equals(menuName) || "myvideonav".equals(menuName) || ("mytv".equals(menuName) && cont.id == 13))
				{
					// Also add the video information option
					WidgetFidget.contain(addWidgetNamed(addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel,
						LISTENER, "Info"), CONDITIONAL, "GetNodeDataType(ListItem) == \"MediaFile\""),  resolveMenuWidget("dialogvideoinfo"));
				}
				else if ("mymusicsongs".equals(menuName) || "mymusicnav".equals(menuName))
				{
					// Also add the album/artist information option
					Widget infoListy = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel,
						LISTENER, "Info");
					// We should also do albuminfo for an artist view; but we don't have detailed artist information to display
					WidgetFidget.contain(addWidgetNamed(addWidgetNamed(infoListy, CONDITIONAL, "GetNodeDataType(ListItem) == \"Album\""),
						ACTION, "Album = GetNodeDataObject(ListItem)"), resolveMenuWidget("dialogalbuminfo"));
					WidgetFidget.contain(addWidgetNamed(infoListy, CONDITIONAL, "GetNodeDataType(ListItem) == \"MediaFile\""),  resolveMenuWidget("dialogsonginfo"));
				}
			}
			else if ("dialogvideoinfo".equals(menuName))
			{
				// NOTE: Here we're supposed to do a search for the person that's been selected from the cast list
			}
			else if ("filebrowser".equals(menuName))
			{
				focusHook = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, HOOK, "FocusGained");
				addWidgetNamed(addWidgetNamed(focusHook, ACTION, "MenuListItem = ListItem"),
					ACTION, "ActiveContainerXBMCID = ContainerXBMCID");

				Widget condtl = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, CONDITIONAL, "\"..\" == GetNodeDataObject(ListItem)");
				Widget bt = addWidgetNamed(condtl, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(bt, ACTION, "CurrNode = GetNodeParent(CurrNode)"), ACTION, "Refresh()");
				Widget be = addWidgetNamed(condtl, BRANCH, "else");
				condtl = addWidgetNamed(be, CONDITIONAL, "IsNodeFolder(ListItem)");
				be = addWidgetNamed(condtl, BRANCH, "else");
				bt = addWidgetNamed(condtl, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(bt, ACTION, "CurrNode = ListItem"), ACTION, "Refresh()");
				addWidgetNamed(addWidgetNamed(be, ACTION, "SelectedFilePath = GetNodeDataObject(ListItem)"), ACTION, "CloseOptionsMenu()");
			}
			else if ("filemanager".equals(menuName) && (cont.id == 20 || cont.id == 21))
			{
				String sidePrefix = (cont.id == 20) ? "Left" : "Right";
				focusHook = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, HOOK, "FocusGained");
				addWidgetNamed(addWidgetNamed(focusHook, ACTION, "MenuListItem = ListItem"),
					ACTION, "ActiveContainerXBMCID = ContainerXBMCID");

				Widget selectResponse = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, CONDITIONAL, "\"..\" == GetNodeDataObject(ListItem)");
				Widget bt = addWidgetNamed(selectResponse, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(bt, ACTION, "SetAllChildrenChecked(" + sidePrefix + "CurrNode, false)"),
					ACTION, sidePrefix + "CurrNode = GetNodeParent(" + sidePrefix + "CurrNode)"),
					ACTION, sidePrefix + "SelectedSize = 0"),
					ACTION, "Refresh()");
				Widget be = addWidgetNamed(selectResponse, BRANCH, "else");
				Widget condtl = addWidgetNamed(be, CONDITIONAL, "IsNodeFolder(ListItem)");
				be = addWidgetNamed(condtl, BRANCH, "else");
				bt = addWidgetNamed(condtl, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(bt, ACTION, "SetAllChildrenChecked(" + sidePrefix + "CurrNode, false)"),
					ACTION, sidePrefix + "CurrNode = ListItem"),
					ACTION, sidePrefix + "SelectedSize = 0"),
					ACTION, "Refresh()");
				Widget cond = addWidgetNamed(addWidgetNamed(be, CONDITIONAL, "IsImportableFileType(GetNodeDataObject(ListItem))"), CONDITIONAL, "GuessMajorFileType(GetNodeDataObject(ListItem))");
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "\"V\""), ACTION, "Watch(GetNodeDataObject(ListItem))"), resolveMenuWidget("videofullscreen"));
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "\"M\""), ACTION, "Watch(GetNodeDataObject(ListItem))"), ACTION, "Refresh()");
				addWidgetNamed(addWidgetNamed(cond, BRANCH, "\"P\""), ACTION, "\"XOUT: ViewPhoto(ListItem)\"");//(Widget) mgroup.symbolMap.get("BASE-68314"));

				Widget clickCond = addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "MouseClick"),
					CONDITIONAL, "MouseClick == 1"), CONDITIONAL, "ClickCount");
				WidgetFidget.contain(addWidgetNamed(clickCond, BRANCH, "2"), selectResponse);
				Widget highlightResponse = null;
				clickCond = addWidgetNamed(highlightResponse = addWidgetNamed(addWidgetNamed(clickCond, BRANCH, "1"), CONDITIONAL, "GetPathParentDirectory(ListItem) != null"),
					CONDITIONAL, "IsNodeChecked(ListItem)");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(clickCond, BRANCH, "true"),
					ACTION, "SetNodeChecked(ListItem, false)"), ACTION, sidePrefix + "SelectedSize = Max(" + sidePrefix + "SelectedSize - GetFilePathSize(ListItem), 0)"),
					ACTION, "Refresh()");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(clickCond, BRANCH, "else"),
					ACTION, "SetNodeChecked(ListItem, true)"),
					ACTION, sidePrefix + "SelectedSize = " + sidePrefix + "SelectedSize + GetFilePathSize(ListItem)"),
					ACTION, "Refresh()");

				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "-"), CONDITIONAL,
					"GetNodeParent(" + sidePrefix + "CurrNode) != null"),
					ACTION, "SetAllChildrenChecked(" + sidePrefix + "CurrNode, false)"),
					ACTION, sidePrefix + "CurrNode = GetNodeParent(" + sidePrefix + "CurrNode)"),
					ACTION, sidePrefix + "SelectedSize = 0"),
					ACTION, "Refresh()");

				Widget kbCond = addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "RawKeyboard"), CONDITIONAL, "KeyChar == \" \"");
				WidgetFidget.contain(bt = addWidgetNamed(kbCond, BRANCH, "true"), highlightResponse);
				addWidgetNamed(bt, ACTION, "SageCommand(\"Down\")"); // select the next item after highlighting
				addWidgetNamed(addWidgetNamed(kbCond, BRANCH, "else"), ACTION, "PassiveListen()");

				// Context menu buttons setup
				Widget contextStartWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "Options"),
					CONDITIONAL, "GetNodeParent(" + sidePrefix + "CurrNode) != null"),
					ACTION, "ButtonNames = DataUnion(\"" + stringMap.get("188") + "\")"),
					ACTION, "ContextOffsetX = " + (compx + compw/2)),
					ACTION, "ContextOffsetY = " + (compy + comph/2)),
					ACTION, "ContextSelection = null");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(contextStartWidg, ACTION,
					"DeselectAfter = (GetChildrenCheckedCount(" + sidePrefix + "CurrNode, true) == 0) && (\"..\" != GetNodeDataObject(ListItem))"),
					CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, true)");
				addWidgetNamed(addWidgetNamed(contextStartWidg, CONDITIONAL, "IsImportableFileType(GetNodeDataObject(ListItem))"),
					ACTION, "AddElement(ButtonNames, \"" + stringMap.get("13358") + "\")"); // "Play Item" localized string instead of "Play Using..."
				WidgetFidget.contain(contextStartWidg, resolveMenuWidget("dialogcontextmenu"));

				// Action performed as a result of context menu selection
				Widget contextChoiceRoot = addWidgetNamed(contextStartWidg, CONDITIONAL, "ContextSelection");
				// Select All
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(contextChoiceRoot, BRANCH, "\"" + stringMap.get("188") + "\""),
					ACTION, "DeselectAfter = false"), ACTION, "SetAllChildrenChecked(" + sidePrefix + "CurrNode, true)"), ACTION, "Refresh()");

				// Deselection
				addWidgetNamed(addWidgetNamed(contextStartWidg, CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, false)");


				Widget actRoot = addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "Delete"),
					CONDITIONAL, "GetNodeParent(" + sidePrefix + "CurrNode) != null && java_io_File_canWrite(GetNodeDataObject(" + sidePrefix + "CurrNode))"),
					ACTION, "DeselectAfter = (GetChildrenCheckedCount(" + sidePrefix + "CurrNode, true) == 0) && (\"..\" != GetNodeDataObject(ListItem))");
				addWidgetNamed(addWidgetNamed(actRoot, CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, true)");

				// Now from here we launch the delete confirmation dialog....
				Widget deleteConfirmLaunch = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(actRoot, CONDITIONAL, "GetChildrenCheckedCount(" + sidePrefix + "CurrNode, true) > 0"),
					ACTION, "ReturnValue = false"),
					ACTION, "YNHeadingLabel = \"" + stringMap.get("122") + "\""),
					ACTION, "YNLine1Label = \"" + stringMap.get("125") + "\""),
					ACTION, "YNLine2Label = null"),
					ACTION, "YNLine3Label = null");
				WidgetFidget.contain(deleteConfirmLaunch, resolveMenuWidget("dialogyesno"));
				Widget deleteResponseCond = addWidgetNamed(deleteConfirmLaunch, CONDITIONAL, "ReturnValue");
				addWidgetNamed(addWidgetNamed(deleteResponseCond, BRANCH, "true"), ACTION, "\"REM Delete the files now!\"");

				// This dialog launch here is for when the delete fails!!!
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(deleteResponseCond, BRANCH, "false"),
					ACTION, "OKHeadingLabel = \"" + stringMap.get("16205") + "\""),
					ACTION, "OKLine1Label = \"" + stringMap.get("16206") + "\""),
					ACTION, "OKLine2Label = \"" + stringMap.get("16200") + "\""),
					ACTION, "OKLine3Label = null"), resolveMenuWidget("dialogok"));

				addWidgetNamed(addWidgetNamed(deleteConfirmLaunch, CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, false)");
				addWidgetNamed(addWidgetNamed(deleteConfirmLaunch, ACTION, "RefreshNode(" + sidePrefix + "CurrNode)"), ACTION, "Refresh()");
			}
			else if ("mymusicplaylisteditor".equals(menuName) && cont.id == 50)
			{
				// playlist editor browser selection
				focusHook = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, HOOK, "FocusGained");
				addWidgetNamed(addWidgetNamed(focusHook, ACTION, "MenuListItem = ListItem"),
					ACTION, "ActiveContainerXBMCID = ContainerXBMCID");

				Widget selectResponse = addWidgetNamed("wraplist".equals(controlTypeLc) ? cont.focusedLayoutWidg : cellPanel, CONDITIONAL, "\"..\" == GetNodeDataObject(ListItem)");
				Widget bt = addWidgetNamed(selectResponse, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(bt, ACTION, "SetAllChildrenChecked(BrowserCurrNode, false)"),
					ACTION, "BrowserCurrNode = GetNodeParent(BrowserCurrNode)"),
					ACTION, "Refresh()");
				Widget be = addWidgetNamed(selectResponse, BRANCH, "else");
				Widget condtl = addWidgetNamed(be, CONDITIONAL, "IsNodeFolder(ListItem)");
				be = addWidgetNamed(condtl, BRANCH, "else");
				bt = addWidgetNamed(condtl, BRANCH, "true");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(bt, ACTION, "SetAllChildrenChecked(BrowserCurrNode, false)"),
					ACTION, "BrowserCurrNode = ListItem"),
					ACTION, "Refresh()");

				Widget clickCond = addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "MouseClick"),
					CONDITIONAL, "MouseClick == 1"), CONDITIONAL, "ClickCount");
				WidgetFidget.contain(addWidgetNamed(clickCond, BRANCH, "2"), selectResponse);
				Widget highlightResponse = null;
				highlightResponse = addWidgetNamed(addWidgetNamed(clickCond, BRANCH, "1"), CONDITIONAL, "GetNodeDataType(ListItem) != \"Virtual\"");
				addWidgetNamed(addWidgetNamed(highlightResponse, ACTION, "SetNodeChecked(ListItem, !IsNodeChecked(ListItem))"),
					ACTION, "Refresh()");

				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "-"), CONDITIONAL,
					"GetNodeParent(BrowserCurrNode) != null"),
					ACTION, "SetAllChildrenChecked(BrowserCurrNode, false)"),
					ACTION, "BrowserCurrNode = GetNodeParent(BrowserCurrNode)"),
					ACTION, "Refresh()");

				Widget kbCond = addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "RawKeyboard"), CONDITIONAL, "KeyChar == \" \"");
				WidgetFidget.contain(bt = addWidgetNamed(kbCond, BRANCH, "true"), highlightResponse);
				addWidgetNamed(bt, ACTION, "SageCommand(\"Down\")"); // select the next item after highlighting
				addWidgetNamed(addWidgetNamed(kbCond, BRANCH, "else"), ACTION, "PassiveListen()");

				// Context menu buttons setup
				Widget contextStartWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "Options"),
					CONDITIONAL, "GetNodeParent(BrowserCurrNode) != null"),
					ACTION, "ButtonNames = DataUnion(\"" + stringMap.get("188") + "\")"),
					ACTION, "ContextOffsetX = " + (compx + compw/2)),
					ACTION, "ContextOffsetY = " + (compy + comph/2)),
					ACTION, "ContextSelection = null");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(contextStartWidg, ACTION,
					"DeselectAfter = (GetChildrenCheckedCount(BrowserCurrNode, true) == 0) && (\"..\" != GetNodeDataObject(ListItem))"),
					CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, true)");
				addWidgetNamed(addWidgetNamed(contextStartWidg, CONDITIONAL, "IsMediaFileObject(GetNodeDataObject(ListItem))"),
					ACTION, "AddElement(ButtonNames, \"" + stringMap.get("13358") + "\")"); // "Play Item" localized string instead of "Play Using..."
				addWidgetNamed(addWidgetNamed(contextStartWidg, CONDITIONAL, "GetNodeDataType(ListItem) == \"MediaFile\" || GetNodeDataType(ListItem) == \"Album\" || GetNodeDataType(ListItem) == \"Playlist\""),
					ACTION, "AddElement(ButtonNames, \"" + stringMap.get("526") + "\")"); // "Add to Playlist" localized string instead of "Play Using..."
				WidgetFidget.contain(contextStartWidg, resolveMenuWidget("dialogcontextmenu"));

				// Action performed as a result of context menu selection
				Widget contextChoiceRoot = addWidgetNamed(contextStartWidg, CONDITIONAL, "ContextSelection");
				// Select All
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(contextChoiceRoot, BRANCH, "\"" + stringMap.get("188") + "\""),
					ACTION, "DeselectAfter = false"), ACTION, "SetAllChildrenChecked(BrowserCurrNode, true)"), ACTION, "Refresh()");
				// Play Item
				addWidgetNamed(addWidgetNamed(addWidgetNamed(contextChoiceRoot, BRANCH, "\"" + stringMap.get("13358") + "\""),
					ACTION, "Watch(GetNodeDataObject(ListItem))"), ACTION, "Refresh()");
				// Add to Playlist
				addWidgetNamed(addWidgetNamed(addWidgetNamed(contextChoiceRoot, BRANCH, "\"" + stringMap.get("526") + "\""),
					ACTION, "AddToPlaylist(CurrPlaylist, GetNodeDataObject(ListItem))"), ACTION, "Refresh()");

				// Deselection
				addWidgetNamed(addWidgetNamed(contextStartWidg, CONDITIONAL, "DeselectAfter"), ACTION, "SetNodeChecked(ListItem, false)");
			}
			else if ("mymusicplaylisteditor".equals(menuName) && cont.id == 100)
			{
				// Actions to perform on items in the playlist portion of the playlist editor
			}
			if (!"wraplist".equals(controlTypeLc))
			{
				if (focusHook == null)
					focusHook = addWidgetNamed(cellPanel, HOOK, "FocusGained");
				addWidgetNamed(focusHook, ACTION, "LastFocus = ListItem");
			}
			if (cont.xbmcIdAttribute != null)
			{
				// Relocate this to be directly under the table component so we can access its variables;
				// we used to have it under the Table directly; but then that would interfere with the scrolling component
				WidgetFidget.discontent(cont.widg, cont.xbmcIdAttribute);
				WidgetFidget.contain(tableCompWidg, cont.xbmcIdAttribute);
			}
			// Do up directory for the Dash command
			if (upDirAction != null)
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cellPanel, LISTENER, "-"), CONDITIONAL, "GetNodeParent(CurrNode) != null"), upDirAction);
		}
		else if ("epggrid".equals(controlTypeLc))
		{
			WidgetFidget.setProperty(cont.widg, MOUSE_TRANSPARENCY, "true");
			cont.pagingWidg = cont.tableWidg = addWidgetNamed(cont.widg, TABLE, cont.desc == null ? "Table" : (cont.desc + " Table"));
			WidgetFidget.setProperty(cont.tableWidg, Widget.AUTO_REPEAT_ACTION, "0.025");
			// EPG is not free-form since we fit everything nicely into the grid
			//addAttribute(cont.tableWidg, "FreeformCellSize", "true");
			if (cont.scrolltime >= 0)
				WidgetFidget.setProperty(cont.tableWidg, DURATION, cont.scrolltime + "");
			// Enforce table bounds; effects can go beyond it of course
			addAttribute(cont.tableWidg, "EnforceBounds", "true");

			String channelWidth = cont.channelLayoutWidg.getProperty(FIXED_WIDTH);
			String rulerHeight = cont.rulerLayoutWidg.getProperty(FIXED_HEIGHT);
			String focusChanHeight = cont.focusedChannelLayoutWidg.getProperty(FIXED_HEIGHT);
			String chanHeight = cont.channelLayoutWidg.getProperty(FIXED_HEIGHT);
			String itemLayWidth = cont.itemLayoutWidg.getProperty(FIXED_WIDTH);
			String itemLayHeight = cont.itemLayoutWidg.getProperty(FIXED_HEIGHT);

			WidgetFidget.setProperty(cont.tableWidg, NUM_ROWS, "=1 + ((" + comph + ") - (" + rulerHeight + ") - (" + focusChanHeight + "))/(" + chanHeight + ")");
			String numCols = "java_lang_Math_ceil((" + cont.timeblocks + "*1.0)/" + cont.rulerunit + ")";
			WidgetFidget.setProperty(cont.tableWidg, NUM_COLS, "=" + numCols);
			WidgetFidget.setProperty(cont.tableWidg, DIMENSIONS, "3"); // both
			WidgetFidget.setProperty(cont.tableWidg, TABLE_WRAPPING, "1"); // vertical

			WidgetFidget.setProperty(cont.tableWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
			WidgetFidget.setProperty(cont.tableWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			WidgetFidget.setProperty(cont.tableWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
			WidgetFidget.setProperty(cont.tableWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
			WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, null);
			WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, null);
			WidgetFidget.setProperty(cont.widg, ANCHOR_X, null);
			WidgetFidget.setProperty(cont.widg, ANCHOR_Y, null);

			Widget rowHeaderWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.tableWidg, ACTION, "GetAllChannels()"),
				ACTION, "ChannelList = FilterByBoolMethod(this, \"IsChannelViewable\", true)"),
				ACTION, "Sort(ChannelList, false, \"ChannelNumber\")"),
				TABLECOMPONENT, "ChanRow");
			WidgetFidget.setProperty(rowHeaderWidg, TABLE_SUBCOMP, "RowHeader");
			WidgetFidget.setProperty(rowHeaderWidg, FIXED_WIDTH, channelWidth);
			WidgetFidget.setProperty(rowHeaderWidg, FIXED_HEIGHT, "=(" + comph + ") - (" + rulerHeight + ")");
			WidgetFidget.setProperty(rowHeaderWidg, ANCHOR_X, "0");
			WidgetFidget.setProperty(rowHeaderWidg, ANCHOR_Y, rulerHeight);
			Widget rowPanel = addWidgetNamed(rowHeaderWidg, PANEL, "RowPanel");
			WidgetFidget.setProperty(rowPanel, FIXED_WIDTH, "1.0");
			WidgetFidget.setProperty(rowPanel, FIXED_HEIGHT, "1.0");
			addAttribute(rowPanel, "ListItem", "CreateMediaNode(GetChannelNumber(ChanRow), GetChannelNumber(ChanRow) + \" \" + GetChannelName(ChanRow), GetChannelLogo(ChanRow), GetChannelLogo(ChanRow), ChanRow)");
			Widget chanCond = addWidgetNamed(rowPanel, CONDITIONAL, "GetVariableFromContext(\"Focused\", true, \"ChanRow\") == ChanRow");
			WidgetFidget.contain(addWidgetNamed(chanCond, BRANCH, "true"), cont.focusedChannelLayoutWidg);
			WidgetFidget.contain(addWidgetNamed(chanCond, BRANCH, "else"), cont.channelLayoutWidg);

			int colTimeWidth = cont.rulerunit * 5 * 60000;
			// We have to recalculate all of the child cell widths since they cannot be determined in the skin itself
			Widget colHeaderWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.tableWidg, ACTION, "CurrTime = Time()"),
				ACTION, "CurrTime = CurrTime - (CurrTime % " + colTimeWidth + ")"),
				ACTION, "CreateTimeSpan(CurrTime, CurrTime + " + colTimeWidth + "*(" + numCols + "))"),
				TABLECOMPONENT, "TimeCol");
			WidgetFidget.setProperty(colHeaderWidg, TABLE_SUBCOMP, "ColHeader");
			WidgetFidget.setProperty(colHeaderWidg, FIXED_WIDTH, "=(" + compw + ") - (" + channelWidth + ")");
			WidgetFidget.setProperty(colHeaderWidg, FIXED_HEIGHT, rulerHeight);
			WidgetFidget.setProperty(colHeaderWidg, ANCHOR_X, channelWidth);
			WidgetFidget.setProperty(colHeaderWidg, ANCHOR_Y, "0");
			addAttribute(cont.rulerLayoutWidg, "ListItem", "CreateMediaNode(PrintTimeShort(TimeCol), PrintTimeShort(TimeCol), null, null, TimeCol)");
			WidgetFidget.contain(colHeaderWidg, cont.rulerLayoutWidg);
			adjustChildWidths(cont.rulerLayoutWidg, cont.timeblocks);

			Widget tableCompWidg = addWidgetNamed(addWidgetNamed(cont.tableWidg, ACTION,"GetAiringsOnChannelAtTime(ChanRow, GetElement(TimeCol, 0), GetElement(TimeCol, 1), false)"),
				TABLECOMPONENT, "Airing");
			WidgetFidget.setProperty(tableCompWidg, TABLE_SUBCOMP, "Cell");
			WidgetFidget.setProperty(tableCompWidg, FIXED_WIDTH, "=(" + compw + ") - (" + channelWidth + ")");
			WidgetFidget.setProperty(tableCompWidg, FIXED_HEIGHT, "=(" + comph + ") - (" + rulerHeight + ")");
			WidgetFidget.setProperty(tableCompWidg, ANCHOR_X, channelWidth);
			WidgetFidget.setProperty(tableCompWidg, ANCHOR_Y, rulerHeight);

			Widget cellPanel = addWidgetNamed(tableCompWidg, ITEM, "Table Cell");
			addAttribute(cellPanel, "ListItem", "CreateMediaNode(GetAiringTitle(Airing), null, null, null, Airing)");
			Widget fixedCond = null;
			fixedCond = addWidgetNamed(cellPanel, CONDITIONAL, "Focused");
			// Be sure the focused one is on the top of the z order
			WidgetFidget.setProperty(cellPanel, Z_OFFSET, "=If(Focused, 1, 0)");

			Widget elseBranch = addWidgetNamed(fixedCond, BRANCH, "else");
			Widget trueBranch = addWidgetNamed(fixedCond, BRANCH, "true");

			WidgetFidget.contain(trueBranch, cont.focusedLayoutWidg);
			WidgetFidget.contain(elseBranch, cont.itemLayoutWidg);
			adjustChildWidths(cont.focusedLayoutWidg, cont.timeblocks);
			adjustChildWidths(cont.itemLayoutWidg, cont.timeblocks);
			Widget focusHook = addWidgetNamed(cellPanel, HOOK, "FocusGained");
			addWidgetNamed(addWidgetNamed(focusHook, ACTION, "MenuListItem = ListItem"),
				ACTION, "ActiveContainerXBMCID = ContainerXBMCID");
			if (cont.xbmcIdAttribute != null)
			{
				// Relocate this to be directly under the table component so we can access its variables;
				// we used to have it under the Table directly; but then that would interfere with the scrolling component
				WidgetFidget.discontent(cont.widg, cont.xbmcIdAttribute);
				WidgetFidget.contain(tableCompWidg, cont.xbmcIdAttribute);
			}
		}
		else if (("togglebutton".equals(controlTypeLc) || "button".equals(controlTypeLc) || "radiobutton".equals(controlTypeLc)) && cont.hitRect != null)
		{
			// Reposition the button to cover the hit rect; put the images at the buttons original coordinates and
			// also fix the positioning of the text label
			if (cont.focusedImageWidg != null)
			{
				WidgetFidget.setProperty(cont.focusedImageWidg, ANCHOR_X, "" + (parseInt(cont.widg.getProperty(ANCHOR_X)) - cont.hitRect.x));
				WidgetFidget.setProperty(cont.focusedImageWidg, ANCHOR_Y, "" + (parseInt(cont.widg.getProperty(ANCHOR_Y)) - cont.hitRect.y));
				WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_WIDTH, (parseInt(cont.widg.getProperty(FIXED_WIDTH))) + "");
				WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_HEIGHT, (parseInt(cont.widg.getProperty(FIXED_HEIGHT))) + "");
			}
			if (cont.unfocusedImageWidg != null)
			{
				WidgetFidget.setProperty(cont.unfocusedImageWidg, ANCHOR_X, "" + (parseInt(cont.widg.getProperty(ANCHOR_X)) - cont.hitRect.x));
				WidgetFidget.setProperty(cont.unfocusedImageWidg, ANCHOR_Y, "" + (parseInt(cont.widg.getProperty(ANCHOR_Y)) - cont.hitRect.y));
				WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_WIDTH, "" + (parseInt(cont.widg.getProperty(FIXED_WIDTH))));
				WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_HEIGHT, "" + (parseInt(cont.widg.getProperty(FIXED_HEIGHT))));
			}
			if (cont.altFocusedImageWidg != null)
			{
				WidgetFidget.setProperty(cont.altFocusedImageWidg, ANCHOR_X, "" + (parseInt(cont.widg.getProperty(ANCHOR_X)) - cont.hitRect.x));
				WidgetFidget.setProperty(cont.altFocusedImageWidg, ANCHOR_Y, "" + (parseInt(cont.widg.getProperty(ANCHOR_Y)) - cont.hitRect.y));
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_WIDTH, (parseInt(cont.widg.getProperty(FIXED_WIDTH))) + "");
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_HEIGHT, (parseInt(cont.widg.getProperty(FIXED_HEIGHT))) + "");
			}
			if (cont.altUnfocusedImageWidg != null)
			{
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, ANCHOR_X, "" + (parseInt(cont.widg.getProperty(ANCHOR_X)) - cont.hitRect.x));
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, ANCHOR_Y, "" + (parseInt(cont.widg.getProperty(ANCHOR_Y)) - cont.hitRect.y));
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_WIDTH, "" + (parseInt(cont.widg.getProperty(FIXED_WIDTH))));
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_HEIGHT, "" + (parseInt(cont.widg.getProperty(FIXED_HEIGHT))));
			}
			if (cont.labelWidg != null)
			{
				// dialogkeyboard in MediaStream caused changes here; test against that
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_X, "" + (parseInt(cont.widg.getProperty(ANCHOR_X)) - cont.hitRect.x + parseInt(cont.textoffsetx)));
				WidgetFidget.setProperty(cont.labelWidg, ANCHOR_Y, "" + (parseInt(cont.widg.getProperty(ANCHOR_Y)) - cont.hitRect.y + parseInt(cont.textoffsety)));
				if (cont.textwidth != null)
					WidgetFidget.setProperty(cont.labelWidg, FIXED_WIDTH, "" + parseInt(cont.textwidth));
				if (cont.textheight != null)
					WidgetFidget.setProperty(cont.labelWidg, FIXED_HEIGHT, "" + parseInt(cont.textheight));
				if ("button".equals(controlTypeLc))
				{

//					WidgetFidget.setProperty(cont.labelWidg, FIXED_WIDTH, "" + (parseInt(cont.widg.getProperty(FIXED_WIDTH))));
//					WidgetFidget.setProperty(cont.labelWidg, FIXED_HEIGHT, "" + (parseInt(cont.widg.getProperty(FIXED_HEIGHT))));
				}
				if ("right".equals(cont.alignx))
					WidgetFidget.setProperty(cont.labelWidg, ANCHOR_POINT_X, "1.0");
			}
			WidgetFidget.setProperty(cont.widg, ANCHOR_X, "" + cont.hitRect.x);
			WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "" + cont.hitRect.y);
			WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, "" + cont.hitRect.width);
			WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, "" + cont.hitRect.height);
		}
		else if ("togglebutton".equals(controlTypeLc))
		{
			// Resize the item to be full size; then make the images the size of the control itself; then put the label
			// at the text offset
// We MAY not want to shift the anchor x/y for these parts....but if we do; then we should NOT shift them in the case the parent is a "grouplist"
			if (cont.focusedImageWidg != null)
			{
//				WidgetFidget.setProperty(cont.focusedImageWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
//				WidgetFidget.setProperty(cont.focusedImageWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
				WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
				WidgetFidget.setProperty(cont.focusedImageWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			}
			if (cont.unfocusedImageWidg != null)
			{
//				WidgetFidget.setProperty(cont.unfocusedImageWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
//				WidgetFidget.setProperty(cont.unfocusedImageWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
				WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
				WidgetFidget.setProperty(cont.unfocusedImageWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			}
			if (cont.altFocusedImageWidg != null)
			{
//				WidgetFidget.setProperty(cont.altFocusedImageWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
//				WidgetFidget.setProperty(cont.altFocusedImageWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
				WidgetFidget.setProperty(cont.altFocusedImageWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			}
			if (cont.altUnfocusedImageWidg != null)
			{
//				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, ANCHOR_X, cont.widg.getProperty(ANCHOR_X));
//				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, ANCHOR_Y, cont.widg.getProperty(ANCHOR_Y));
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_WIDTH, cont.widg.getProperty(FIXED_WIDTH));
				WidgetFidget.setProperty(cont.altUnfocusedImageWidg, FIXED_HEIGHT, cont.widg.getProperty(FIXED_HEIGHT));
			}
//			WidgetFidget.setProperty(cont.widg, ANCHOR_X, "");
//			WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "");
			WidgetFidget.setProperty(cont.widg, FIXED_WIDTH, ""); // used to be 1.0
			WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, ""); // used to be 1.0
		}
		else if ("grouplist".equals(controlTypeLc))
		{
			boolean hor = "horizontal".equalsIgnoreCase(cont.orientation);
			WidgetFidget.setProperty(cont.widg, LAYOUT, !hor ? "Vertical" : "Horizontal");
			if ((!hor && (cont.upTargets == null || cont.upTargets.isEmpty()) && (cont.downTargets == null || cont.downTargets.isEmpty())) ||
				(hor && (cont.leftTargets == null || cont.leftTargets.isEmpty()) && (cont.rightTargets == null || cont.rightTargets.isEmpty())))
				WidgetFidget.setProperty(cont.widg, hor ? WRAP_HORIZONTAL_NAVIGATION : WRAP_VERTICAL_NAVIGATION, "true");
			WidgetFidget.setProperty(cont.widg, HALIGNMENT, "0.0");
			WidgetFidget.setProperty(cont.widg, VALIGNMENT, "0.0");
			if (cont.itemgap != 0)
				WidgetFidget.setProperty(cont.widg, hor ? PAD_X : PAD_Y, cont.itemgap + "");
			// PM3.HD uses this setting; but it looks like we should just ignore it
//			if (cont.usecontrolcoords && (cont.posx != null || cont.posy != null))
//				WidgetFidget.setProperty(cont.widg, INSETS, ((cont.posy != null) ? cont.posy : "0") + "," + ((cont.posx != null) ? cont.posx : "0") + ",0,0");
			// Insert the dummy panel which is used as the parent for the scrolling container + paging control
			Widget fakePanel = mgroup.addWidget(PANEL);
			WidgetFidget.setName(fakePanel, "ScrollContainer");
			WidgetFidget.setProperty(fakePanel, MOUSE_TRANSPARENCY, "true");
			if (cont.targetParent != null)
			{
				Widget oldParent = cont.widg.containers()[0];
				WidgetFidget.discontent(oldParent, cont.widg);
				WidgetFidget.contain(oldParent, fakePanel);
			}
			else
				cont.targetParent = fakePanel;
			WidgetFidget.contain(fakePanel, cont.widg);
			cont.pagingWidg = fakePanel;
			// To avoid rendering scrolling components that should not be visible yet
			addAttribute(cont.widg, "EnforceBounds", "true");
		}
		if (("grouplist".equals(controlTypeLc) || "group".equals(controlTypeLc)) && cont.id >= 0)
		{
			addAttribute(cont.widg, "ControlGroupXBMCID", "" + cont.id);
		}
		if (cont.onfocus != null)
		{
			Widget hook = addWidgetNamed(cont.widg, HOOK, "FocusGained");
			WidgetFidget.contain(hook, createProcessChainFromExpression(cont.onfocus, cont));
			addWidgetNamed(hook, ACTION, "Refresh()");
		}
		if (cont.viewtype != null)
		{
			// Put this with the XBMCID so its found properly
			addAttribute(cont.xbmcIdAttribute != null ? cont.xbmcIdAttribute.containers()[0] : cont.widg, "ThisViewType", "\"" + cont.viewtypeLabel + "\"");
		}
		if (cont.tableWidg != null)
		{
			// Put this with the XBMCID so its found properly
			addAttribute(cont.xbmcIdAttribute != null ? cont.xbmcIdAttribute.containers()[0] : cont.widg, "ContainerXBMCID", "" + cont.id);
		}

		// Custom controls
		if ("dialogsonginfo".equals(menuName) && cont.id == 12)
		{
			// AlbumInfo link from SongInfo
			WidgetFidget.contain(addWidgetNamed(cont.widg, ACTION, "Album = GetAlbumForFile(ListItem)"), resolveMenuWidget("dialogalbuminfo"));
		}
		else if ("dialogcontextmenu".equals(menuName))
		{
			if (cont.id == 1000) // button template
			{
				Widget tableSource = mgroup.addWidget(ACTION);
				WidgetFidget.setName(tableSource, "ButtonNames");
				Widget tableWidg = addWidgetNamed(tableSource, TABLE, "Table");
				addAttribute(tableWidg, "FreeformCellSize", "true");
				WidgetFidget.setProperty(tableWidg, NUM_COLS, "1");
				WidgetFidget.setProperty(tableWidg, NUM_ROWS, "0");
				WidgetFidget.setProperty(tableWidg, DIMENSIONS, "1"); // vertical
				Widget cellCompWidg = addWidgetNamed(tableWidg, TABLECOMPONENT, "ContextButton");
				WidgetFidget.setProperty(cellCompWidg, TABLE_SUBCOMP, "Cell");
				WidgetFidget.setProperty(cellCompWidg, PAD_Y, "2");
				WidgetFidget.contain(addWidgetNamed(cellCompWidg, PANEL, ""), cont.targetParent == null ? cont.widg : cont.targetParent);
				cont.targetParent = tableSource;

				// Now the action chain for the button selection
				addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "ContextSelection = ContextButton"), ACTION, "CloseOptionsMenu()");
			}
			if (cont.id == 998) // bottom image
				WidgetFidget.setProperty(cont.widg, ANCHOR_Y, "1.0");
			if (cont.id == 999) // background table image
			{
				// Find the height of the button template
				Control buttonTemplate = null;
				Control searchParent = cont.parent;
				while (searchParent != null && buttonTemplate == null)
				{
					buttonTemplate = searchParent.getControlForID(1000);
					searchParent = searchParent.parent;
				}
				if (buttonTemplate != null)
					WidgetFidget.setProperty(cont.widg, FIXED_HEIGHT, "=Size(ButtonNames) * (2 + " + parseInt(buttonTemplate.height) + ")");
			}
		}
		else if ("dialogkeyboard".equals(menuName))
		{
			if (cont.id == 310) // edit label
			{
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg, LISTENER, "Select"),
					ACTION, "KBEntryConfirmed = true"), ACTION, "CloseOptionsMenu()");
			}
		}
		else if ("dialognumeric".equals(menuName))
		{
			if (cont.id == 4) // edit label
			{
				addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg, LISTENER, "Select"),
					ACTION, "KBEntryConfirmed = true"), ACTION, "CloseOptionsMenu()");
			}
		}
		else if ("filebrowser".equals(menuName))
		{
			if (cont.id == 415)
			{
				Widget kbReturnWidg = null;
				WidgetFidget.contain(kbReturnWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg, ACTION, "KBEntryConfirmed = false"),
					ACTION, "KBText = \"\""), ACTION, "KBHeadingLabel = \"" + stringMap.get("119") + "\""),
					resolveMenuWidget("dialogkeyboard"));
				Widget createCond = null;
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(createCond = addWidgetNamed(addWidgetNamed(
					kbReturnWidg, CONDITIONAL, "KBEntryConfirmed"),
					CONDITIONAL, "CreateNewLocalDirectory(CreateFilePath(GetAbsoluteFilePath(CurrNode), KBText))"),
					BRANCH, "false"),
					// continue on if failed
					ACTION, "OKHeadingLabel = \"" + stringMap.get("20069") + "\""),
					ACTION, "OKLine1Label = \"" + stringMap.get("20072") + "\""),
					ACTION, "OKLine2Label = \"" + stringMap.get("20073") + "\""),
					ACTION, "OKLine3Label = null"),
					resolveMenuWidget("dialogok"));
				addWidgetNamed(addWidgetNamed(addWidgetNamed(createCond, BRANCH, "true"), ACTION, "RefreshNode(CurrNode)"), ACTION, "Refresh()");
			}
		}
		else if ("mymusicplaylist".equals(menuName))
		{
			if (cont.id == 21)
			{
				Widget kbReturnWidg = null;
				WidgetFidget.contain(kbReturnWidg = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(cont.widg,
					ACTION, "\"REM Get the name of the new playlist to save the now playing list as\""),
					ACTION, "KBEntryConfirmed = false"),
					ACTION, "KBText = \"\""), ACTION, "KBHeadingLabel = \"" + stringMap.get("16012") + "\""),
					resolveMenuWidget("dialogkeyboard"));
				Widget loopCond = null;
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(loopCond = addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(kbReturnWidg, CONDITIONAL, "KBEntryConfirmed"),
					ACTION, "NewPlaylist = AddPlaylist(KBText)"),
					ACTION, "i = 0"),
					CONDITIONAL, "i < GetNumberOfPlaylistItems(GetNowPlayingList())"),
					ACTION, "AddToPlaylist(NewPlaylist, GetPlaylistItemAt(GetNowPlayingList(), i))"),
					ACTION, "i = i + 1"), loopCond);
			}
		}
		else if ("mymusicplaylisteditor".equals(menuName))
		{
			if (cont.id == 8)
			{
				// Clear playlist
				Widget cond = null;
				Widget rootClear = addWidgetNamed(cont.widg, ACTION, "\"REM Clear the playlist\"");
				WidgetFidget.contain(addWidgetNamed(cond = addWidgetNamed(rootClear, CONDITIONAL, "GetNumberOfPlaylistItems(CurrPlaylist) > 0"),
					ACTION, "RemovePlaylistItemAt(CurrPlaylist, 0)"), cond);
				addWidgetNamed(rootClear, ACTION, "Refresh()");
			}
		}
		else if ("settingscategory".equals(menuName))
		{
			if (cont.id == 3)
			{
				// Category group list
				WidgetFidget.setName(cont.widg, "\"XOUT: CategorySettings-CategoryList\"");
			}
			else if (cont.id == 5)
			{
				// Category group list
				WidgetFidget.setName(cont.widg, "\"XOUT: CategorySettings-SettingsArea\"");
			}
			else if (cont.id == 10)
			{
				// Category group list default button style
				Widget defTheme;
				WidgetFidget.contain(defTheme = addWidgetNamed(null, THEME, "\"XIN: CategorySettings-CategoryListTheme\""), cont.widg);

				// Fix the Focused conditional so it shows the current area highlighted as well
				Widget[] kids = cont.widg.contents(CONDITIONAL);
				for (int i = 0; i < kids.length; i++)
				{
					if ("Focused".equals(kids[i].getName()))
						WidgetFidget.setName(kids[i], "Focused || (FindElementIndex(SetupAreas, If(false, \"Focused\", CurrSetupArea)) >= 0)");
					else if ("!Focused".equals(kids[i].getName()))
						WidgetFidget.setName(kids[i], "!Focused && (CurrSetupArea != SetupArea)");
				}

				if (cont.focusedImageWidg != null)
					WidgetFidget.setProperty(cont.focusedImageWidg, BACKGROUND_COMPONENT, "true");
				if (cont.unfocusedImageWidg != null)
					WidgetFidget.setProperty(cont.unfocusedImageWidg, BACKGROUND_COMPONENT, "true");

				Widget defText = addWidgetNamed(defTheme, TEXT, "");
				setInternalTextWidgetPositioning(cont, defText);
			}
			else if (cont.id == 7)
			{
				// Default button for the right side of settings
				Widget themer = addWidgetNamed(null, ITEM, "\"XIN: CategorySettings-RowPanelItemTheme\"");
				Widget[] kids = cont.widg.contents(CONDITIONAL);
				for (int i = 0; i < kids.length; i++)
				{
					if ("Focused".equals(kids[i].getName()) || "!Focused".equals(kids[i].getName()))
						WidgetFidget.contain(themer, kids[i]);
				}
				if (cont.focusedImageWidg != null)
					WidgetFidget.setProperty(cont.focusedImageWidg, BACKGROUND_COMPONENT, "true");
				if (cont.unfocusedImageWidg != null)
					WidgetFidget.setProperty(cont.unfocusedImageWidg, BACKGROUND_COMPONENT, "true");
			}
		}
		else if ("mytv".equals(menuName))
		{
			if (cont.id == 31)
			{
				// Button that toggles between the different EPG views: channel, now, next, timeline
				Widget cond = addWidgetNamed(cont.widg, CONDITIONAL, "true");
				Widget cleanupAct = addWidgetNamed(null, ACTION, "Refresh()");
				addWidgetNamed(cleanupAct, ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + menuName + "/\", ContainerViewType)");
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(cond, BRANCH, "ContainerViewType == \"ChannelPrograms\""),
					ACTION, "ContainerViewType = \"NowNextPrograms\""), ACTION, "NowNotNext = true"), cleanupAct);
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "(ContainerViewType == \"NowNextPrograms\") && !NowNotNext"),
					ACTION, "ContainerViewType = \"EPG\""), cleanupAct);
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "(ContainerViewType == \"NowNextPrograms\") && NowNotNext"),
					ACTION, "NowNotNext = false"), cleanupAct);
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "ContainerViewType == \"EPG\""),
					ACTION, "ContainerViewType = \"ChannelPrograms\""), cleanupAct);
				WidgetFidget.contain(addWidgetNamed(addWidgetNamed(cond, BRANCH, "else"),
					ACTION, "ContainerViewType = \"EPG\""), cleanupAct);
			}
		}
		return cont;
	}

	private void setInternalTextWidgetPositioning(Control cont, Widget defText)
	{
		if (cont.alignx == null || "left".equals(cont.alignx))
		{
			WidgetFidget.setProperty(defText, TEXT_ALIGNMENT, "0.0");
			if (cont.textoffsetx == null)
				WidgetFidget.setProperty(defText, ANCHOR_X, "0.0");
			else
				WidgetFidget.setProperty(defText, ANCHOR_X, parseInt(cont.textoffsetx) + "");
		}
		else if ("center".equals(cont.alignx))
		{
			WidgetFidget.setProperty(defText, TEXT_ALIGNMENT, "0.5");
			WidgetFidget.setProperty(defText, ANCHOR_X, "0.5");
		}
		else if ("right".equals(cont.alignx))
		{
			WidgetFidget.setProperty(defText, TEXT_ALIGNMENT, "1.0");
			WidgetFidget.setProperty(defText, ANCHOR_X, "1.0");
			if (cont.textoffsetx != null)
				WidgetFidget.setProperty(defText, INSETS, "0,0,0," + parseInt(cont.textoffsetx));
		}
		if (cont.aligny == null || "top".equals(cont.aligny))
		{
			WidgetFidget.setProperty(defText, VALIGNMENT, "0.0");
			if (cont.textoffsety == null)
				WidgetFidget.setProperty(defText, ANCHOR_Y, "0.0");
			else
				WidgetFidget.setProperty(defText, ANCHOR_Y, parseInt(cont.textoffsety) + "");
		}
		else if ("center".equals(cont.aligny))
		{
			WidgetFidget.setProperty(defText, VALIGNMENT, "0.5");
			WidgetFidget.setProperty(defText, ANCHOR_Y, "0.5");
		}
		else if ("bottom".equals(cont.aligny))
		{
			WidgetFidget.setProperty(defText, VALIGNMENT, "1.0");
			WidgetFidget.setProperty(defText, ANCHOR_Y, "1.0");
			if (cont.textoffsety != null)
				WidgetFidget.setProperty(defText, INSETS, "0,0," + parseInt(cont.textoffsety) + ",0");
		}
	}

	private void processAnimations(java.util.Vector anims, Widget widg, Control controlContext, int compx, int compy, int compw, int comph)
	{
		for (int i = 0; i < anims.size(); i++)
		{
			AnimData currAnim = (AnimData) anims.get(i);
			if (currAnim.effect == null) continue;
			Widget animCond = null;
			if (currAnim.condition != null && !"true".equals(currAnim.condition))
				animCond = addWidgetNamed(widg, CONDITIONAL, translateBooleanExpression(currAnim.condition, controlContext));
			String animKey = currAnim.getUniqueKey();
			if (animKey != null)
			{
				Widget cachedAnim = (Widget) animWidgMap.get(animKey);
				if (cachedAnim != null)
				{
					WidgetFidget.contain(animCond == null ? widg : animCond, cachedAnim);
					continue;
				}
			}
			Widget effect = addWidgetNamed(animCond == null ? widg : animCond, EFFECT, currAnim.trigger + currAnim.effect);
			boolean inEffect = false;
			if ("windowopen".equals(currAnim.trigger))
			{
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, MENULOADED_EFFECT);
				inEffect = true;
			}
			else if ("windowclose".equals(currAnim.trigger))
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, MENUUNLOADED_EFFECT);
			else if ("visible".equals(currAnim.trigger))
			{
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, SHOWN_EFFECT);
				inEffect = true;
			}
			else if ("hidden".equals(currAnim.trigger))
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, HIDDEN_EFFECT);
			else if ("focus".equals(currAnim.trigger))
			{
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, FOCUSGAINED_EFFECT);
				inEffect = true;
			}
			else if ("unfocus".equals(currAnim.trigger))
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, FOCUSLOST_EFFECT);
			else if ("conditional".equals(currAnim.trigger))
			{
				if ("true".equals(currAnim.condition))
					WidgetFidget.setProperty(effect, EFFECT_TRIGGER, STATIC_EFFECT);
				else
					WidgetFidget.setProperty(effect, EFFECT_TRIGGER, CONDITIONAL_EFFECT);
				inEffect = true;
			}
			else if ("visiblechange".equals(currAnim.trigger))
			{
				WidgetFidget.setProperty(effect, EFFECT_TRIGGER, VISIBLECHANGE_EFFECT);
				inEffect = true;
			}
			if (currAnim.reversible)
				WidgetFidget.setProperty(effect, REVERSIBLE, "true");
			if (currAnim.pulse)
				WidgetFidget.setProperty(effect, LOOP, "true");
			if (currAnim.time > 0)
				WidgetFidget.setProperty(effect, DURATION, Long.toString(currAnim.time));
			if (currAnim.delay > 0)
				WidgetFidget.setProperty(effect, DELAY, Long.toString(currAnim.delay));
			// My easing definitions are the opposite of XBMC's
			if ("in".equals(currAnim.easing))
				WidgetFidget.setProperty(effect, EASING, "Out");
			else if ("inout".equals(currAnim.easing))
				WidgetFidget.setProperty(effect, EASING, "InOut");
			else
				WidgetFidget.setProperty(effect, EASING, "In");
			if ("elastic".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Rebound");
			else if ("bounce".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Bounce");
			else if ("circle".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Circle");
			else if ("back".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Curl");
			else if ("sine".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Sine");
			else if ("cubic".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Cubic");
			else if ("quadratic".equals(currAnim.tween))
				WidgetFidget.setProperty(effect, TIMESCALE, "Quadratic");
			else
				WidgetFidget.setProperty(effect, TIMESCALE, "Linear");
			if (currAnim.center != null && currAnim.center.length > 1)
			{
				if (widg.hasProperty(ANCHOR_POINT_X))
					WidgetFidget.setProperty(effect, ANCHOR_POINT_X, "" + (currAnim.center[0] - Math.round(1.0f - Float.parseFloat(widg.getProperty(ANCHOR_POINT_X))) * compx));
				else
					WidgetFidget.setProperty(effect, ANCHOR_POINT_X, "" + (currAnim.center[0] - compx));
				if (widg.hasProperty(ANCHOR_POINT_Y))
					WidgetFidget.setProperty(effect, ANCHOR_POINT_Y, "" + (currAnim.center[1] - Math.round(1.0f - Float.parseFloat(widg.getProperty(ANCHOR_POINT_Y))) * compy));
				else
					WidgetFidget.setProperty(effect, ANCHOR_POINT_Y, "" + (currAnim.center[1] - compy));
			}
			if ("fade".equals(currAnim.effect))
			{
				if (currAnim.start != null)
					WidgetFidget.setProperty(effect, FOREGROUND_ALPHA, "" + (currAnim.start[0]/100.0f));
				else
					WidgetFidget.setProperty(effect, FOREGROUND_ALPHA, inEffect ? "0" : "1.0");
				if (currAnim.end != null)
					WidgetFidget.setProperty(effect, BACKGROUND_ALPHA, "" + (currAnim.end[0]/100.0f));
				else
					WidgetFidget.setProperty(effect, BACKGROUND_ALPHA, inEffect ? "1.0" : "0");
				if (currAnim.start == null && currAnim.end == null)
				{
					if (inEffect)
					{
						WidgetFidget.setProperty(effect, FOREGROUND_ALPHA, "0");
						WidgetFidget.setProperty(effect, BACKGROUND_ALPHA, "1.0");
					}
					else
					{
						WidgetFidget.setProperty(effect, FOREGROUND_ALPHA, "1.0");
						WidgetFidget.setProperty(effect, BACKGROUND_ALPHA, "0");
					}
				}
			}
			else if ("slide".equals(currAnim.effect))
			{
				if (currAnim.start != null)
				{
					WidgetFidget.setProperty(effect, START_RENDER_OFFSET_X, "" + currAnim.start[0]);
					if (currAnim.start.length > 1)
						WidgetFidget.setProperty(effect, START_RENDER_OFFSET_Y, "" + currAnim.start[1]);
				}
				if (currAnim.end != null)
				{
					WidgetFidget.setProperty(effect, ANCHOR_X, "" + currAnim.end[0]);
					if (currAnim.end.length > 1)
						WidgetFidget.setProperty(effect, ANCHOR_Y, "" + currAnim.end[1]);
				}
			}
			else if ("rotate".equals(currAnim.effect) || "rotatez".equals(currAnim.effect))
			{
				if (currAnim.start != null)
					WidgetFidget.setProperty(effect, START_RENDER_ROTATE_Z, "" + (-1*currAnim.start[0]));
				if (currAnim.end != null)
					WidgetFidget.setProperty(effect, RENDER_ROTATE_Z, "" + (-1*currAnim.end[0]));
			}
			else if ("rotatex".equals(currAnim.effect))
			{
				if (currAnim.start != null)
					WidgetFidget.setProperty(effect, START_RENDER_ROTATE_X, "" + currAnim.start[0]);
				if (currAnim.end != null)
					WidgetFidget.setProperty(effect, RENDER_ROTATE_X, "" + currAnim.end[0]);
				if (currAnim.center != null && currAnim.center.length == 1)
					WidgetFidget.setProperty(effect, ANCHOR_POINT_Y, "" + (currAnim.center[0] - compy));
			}
			else if ("rotatey".equals(currAnim.effect))
			{
				if (currAnim.start != null)
					WidgetFidget.setProperty(effect, START_RENDER_ROTATE_Y, "" + currAnim.start[0]);
				if (currAnim.end != null)
					WidgetFidget.setProperty(effect, RENDER_ROTATE_Y, "" + currAnim.end[0]);
				if (currAnim.center != null && currAnim.center.length == 1)
					WidgetFidget.setProperty(effect, ANCHOR_POINT_X, "" + (currAnim.center[0] - compx));
			}
			else if ("zoom".equals(currAnim.effect))
			{
				if (currAnim.start != null)
				{
					if (currAnim.start.length == 4)
					{
						if (!inEffect)
						{
							// From MediaStream - Videos - Media Preview view the out animations look like they use a slightly different coordinate system where
							// they negate the x/y start values and have the end values relative to the component position instead of absolute.
							if (currAnim.start[0] < 0)
								currAnim.start[0] *= -1;
							if (currAnim.start[1] < 0)
								currAnim.start[1] *= -1;
						}
						float scaleX = currAnim.start[2]/((float)compw);
						float scaleY = currAnim.start[3]/((float)comph);
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_X, "" + scaleX);
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_Y, "" + scaleY);
						if (!inEffect)
						{
							if (compw != currAnim.start[2])
								WidgetFidget.setProperty(effect, ANCHOR_POINT_X, "" + (Math.abs(compx - currAnim.start[0]) / (float)Math.abs(compw - currAnim.start[2])));
							if (comph != currAnim.start[3])
								WidgetFidget.setProperty(effect, ANCHOR_POINT_Y, "" + (Math.abs(compy - currAnim.start[1]) / (float)Math.abs(comph - currAnim.start[3])));
						}
					}
					else
					{
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_X, "" + currAnim.start[0]/100.0f);
						if (currAnim.start.length == 1)
							WidgetFidget.setProperty(effect, START_RENDER_SCALE_Y, "" + currAnim.start[0]/100.0f);
						else if (currAnim.start.length > 1)
							WidgetFidget.setProperty(effect, START_RENDER_SCALE_Y, "" + currAnim.start[1]/100.0f);
					}
				}
				if (currAnim.end != null)
				{
					if (currAnim.end.length == 4)
					{
						if (!inEffect)
						{
							// From MediaStream - Videos - Media Preview view the out animations look like they use a slightly different coordinate system where
							// they negate the x/y start values and have the end values relative to the component position instead of absolute.
//							currAnim.end[0] += compx;
//							currAnim.end[1] += compy;
						}
						float scaleX = currAnim.end[2]/((float)compw);
						float scaleY = currAnim.end[3]/((float)comph);
						WidgetFidget.setProperty(effect, RENDER_SCALE_X, "" + scaleX);
						WidgetFidget.setProperty(effect, RENDER_SCALE_Y, "" + scaleY);
						if (inEffect)
						{
							if (compw != currAnim.end[2])
								WidgetFidget.setProperty(effect, ANCHOR_POINT_X, "" + (Math.abs(compx - currAnim.end[0]) / (float)Math.abs(compw - currAnim.end[2])));
							if (comph != currAnim.end[3])
								WidgetFidget.setProperty(effect, ANCHOR_POINT_Y, "" + (Math.abs(compy - currAnim.end[1]) / (float)Math.abs(comph - currAnim.end[3])));
						}
					}
					else
					{
						WidgetFidget.setProperty(effect, RENDER_SCALE_X, "" + currAnim.end[0]/100.0f);
						if (currAnim.end.length == 1)
							WidgetFidget.setProperty(effect, RENDER_SCALE_Y, "" + currAnim.end[0]/100.0f);
						else if (currAnim.end.length > 1)
							WidgetFidget.setProperty(effect, RENDER_SCALE_Y, "" + currAnim.end[1]/100.0f);
					}
				}
				if (currAnim.start == null && currAnim.end == null)
				{
					if (inEffect)
					{
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_X, "0");
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_Y, "0");
						WidgetFidget.setProperty(effect, RENDER_SCALE_X, "1.0");
						WidgetFidget.setProperty(effect, RENDER_SCALE_Y, "1.0");
					}
					else
					{
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_X, "1.0");
						WidgetFidget.setProperty(effect, START_RENDER_SCALE_Y, "1.0");
						WidgetFidget.setProperty(effect, RENDER_SCALE_X, "0");
						WidgetFidget.setProperty(effect, RENDER_SCALE_Y, "0");
					}
				}
			}
			if (animKey != null)
				animWidgMap.put(animKey, effect);
		}
	}

	private String cleanImagePath(String path)
	{
		if (path != null && path.startsWith("special://skin/media/"))
			return path.substring(21);
		else if (path != null && path.startsWith("special://skin/backgrounds/"))
			return ".." + path.substring(14);
		else
			return path;
	}

	private String translateImagePath(String path)
	{
		if (path == null)
			return "";
		if (path.startsWith("special://skin/media/"))
			return "\"" + mediaPath + path.substring(20) + "\"";
		else if (path.startsWith("special://skin/backgrounds/"))
			return "\"" + path.substring(15) + "\"";
		else if (path.startsWith("$INFO["))
		{
			String orgExpr = path;
			String infoBlock;
			int endIdx = path.lastIndexOf(']');
			infoBlock = path.substring(6, endIdx);
			java.util.StringTokenizer toker = new java.util.StringTokenizer(infoBlock, ",", true);
			String infoExpr = toker.nextToken();
			if (toker.hasMoreTokens())
				toker.nextToken(); // comma
			String infoPrefix = "";
			String infoPostfix = "";
			String nexty = toker.hasMoreTokens() ? toker.nextToken() : ",";
			if (!nexty.equals(","))
			{
				infoPrefix = nexty;
				infoPrefix = infoPrefix.replace("$COMMA", ",");
				infoPrefix = infoPrefix.replace("$LBRACKET", "[");
				infoPrefix = infoPrefix.replace("$RBRACKET", "]");
				infoPrefix = infoPrefix.replace("$$", "$");
				if (toker.hasMoreTokens())
					toker.nextToken(); // comma
			}
			if (toker.hasMoreTokens())
			{
				infoPostfix = toker.nextToken();
				infoPostfix = infoPostfix.replace("$COMMA", ",");
				infoPostfix = infoPostfix.replace("$LBRACKET", "[");
				infoPostfix = infoPostfix.replace("$RBRACKET", "]");
				infoPostfix = infoPostfix.replace("$$", "$");
			}
			infoExpr = translateImageExpression(infoExpr, null);
			if (infoPrefix.length() > 0 || infoPostfix.length() > 0)
				return "If(!IsEmpty(" + infoExpr + "), \"" + infoPrefix + "\" + " + infoExpr + " + \"" + infoPostfix + "\", \"\")";
			else if (endIdx != path.length() - 1)
				return infoExpr + " + \"" + path.substring(endIdx + 1) + "\"";
			else
				return infoExpr;
		}
		else
			return "\"" + mediaPath + "/" + path + "\"";
	}

	private TextureInfo parseTextureAttributes(Element textureElement)
	{
		TextureInfo rv = new TextureInfo();
		String texturePath = textureElement.getTextContent().trim();
		if (texturePath.startsWith("$INFO["))
		{
			String orgExpr = texturePath;
			String infoBlock;
			int endIdx = texturePath.lastIndexOf(']');
			infoBlock = texturePath.substring(6, endIdx);
			java.util.StringTokenizer toker = new java.util.StringTokenizer(infoBlock, ",", true);
			String infoExpr = toker.nextToken();
			if (toker.hasMoreTokens())
				toker.nextToken(); // comma
			String infoPrefix = "";
			String infoPostfix = "";
			String nexty = toker.hasMoreTokens() ? toker.nextToken() : ",";
			if (!nexty.equals(","))
			{
				infoPrefix = nexty;
				infoPrefix = infoPrefix.replace("$COMMA", ",");
				infoPrefix = infoPrefix.replace("$LBRACKET", "[");
				infoPrefix = infoPrefix.replace("$RBRACKET", "]");
				infoPrefix = infoPrefix.replace("$$", "$");
				if (toker.hasMoreTokens())
					toker.nextToken(); // comma
			}
			if (toker.hasMoreTokens())
			{
				infoPostfix = toker.nextToken();
				infoPostfix = infoPostfix.replace("$COMMA", ",");
				infoPostfix = infoPostfix.replace("$LBRACKET", "[");
				infoPostfix = infoPostfix.replace("$RBRACKET", "]");
				infoPostfix = infoPostfix.replace("$$", "$");
			}
			infoExpr = translateImageExpression(infoExpr, null);
			if (infoPrefix.length() > 0 || infoPostfix.length() > 0)
				rv.texturePath = "If(!IsEmpty(" + infoExpr + "), \"" + infoPrefix + "\" + " + infoExpr + " + \"" + infoPostfix + "\", \"\")";
			else if (endIdx != texturePath.length() - 1)
				rv.texturePath = infoExpr + " + \"" + texturePath.substring(endIdx + 1) + "\"";
			else
				rv.texturePath = infoExpr;

//			exprDump.put(orgExpr, rv.texturePath);
		}
		else if (texturePath.startsWith("special://skin/media/"))
			rv.texturePath = "\"" + mediaPath + texturePath.substring(20) + "\"";
		else if (texturePath.startsWith("special://skin/backgrounds/"))
			rv.texturePath = "\"" + texturePath.substring(15) + "\"";
		else
			rv.texturePath = "\"" + mediaPath + "/" + texturePath + "\"";

		if (textureElement.hasAttribute("flipx"))
			rv.flipx = Boolean.parseBoolean(textureElement.getAttribute("flipx"));
		if (textureElement.hasAttribute("flipy"))
			rv.flipy = Boolean.parseBoolean(textureElement.getAttribute("flipy"));
		if (textureElement.hasAttribute("background"))
			rv.backgroundLoad = Boolean.parseBoolean(textureElement.getAttribute("background"));
		if (textureElement.hasAttribute("border"))
		{
			String borderText = textureElement.getAttribute("border");
			rv.scalingInsets = new int[4];
			if (borderText.indexOf(",") != -1 || borderText.indexOf(".") != -1)
			{
				java.util.StringTokenizer toker = new java.util.StringTokenizer(borderText, ",.");
// NOTE THIS ORDER DOES NOT MATCH THEIR DOCS; I THINK THE DOCS ARE WRONG
				rv.scalingInsets[1] = parseInt(toker.nextToken()); // left
				rv.scalingInsets[0] = parseInt(toker.nextToken()); // top
				rv.scalingInsets[3] = parseInt(toker.nextToken()); // right
				rv.scalingInsets[2] = parseInt(toker.nextToken()); // bottom

				if (rv.flipx)
				{
					int temp = rv.scalingInsets[1];
					rv.scalingInsets[1] = rv.scalingInsets[3];
					rv.scalingInsets[3] = temp;
				}
				if (rv.flipy)
				{
					int temp = rv.scalingInsets[0];
					rv.scalingInsets[0] = rv.scalingInsets[2];
					rv.scalingInsets[2] = temp;
				}
			}
			else
			{
				int val = parseInt(borderText);
				rv.scalingInsets[0] = rv.scalingInsets[1] = rv.scalingInsets[2] = rv.scalingInsets[3] = val;
			}
		}
		if (textureElement.hasAttribute("align"))
			rv.alignx = textureElement.getAttribute("align");
		if (textureElement.hasAttribute("aligny"))
			rv.aligny = textureElement.getAttribute("aligny");
		if (textureElement.hasAttribute("diffuse"))
			rv.diffuseImage = "" + mediaPath + "/" + textureElement.getAttribute("diffuse");
		return rv;
	}

	private Widget addWidgetNamed(Widget parent, byte type, String name)
	{
		Widget newWidg = mgroup.addWidget(type);
		WidgetFidget.setName(newWidg, name);
		if (parent != null)
			WidgetFidget.contain(parent, newWidg);
		return newWidg;
	}

	private Widget addAttribute(Widget parent, String name, String value)
	{
		Widget newWidg = mgroup.addWidget(ATTRIBUTE);
		WidgetFidget.setName(newWidg, name);
		WidgetFidget.setProperty(newWidg, VALUE, value);
		WidgetFidget.contain(parent, newWidg);
		return newWidg;
	}

	private class Window
	{
		int id = -1;
		int defaultControl = -1;
		boolean defaultAlways = false;
		Boolean allowOverlay = null;
		String windowType = null;
		java.util.Vector visibles = null;
		java.util.Vector anims;
		int zorder = -1;
		boolean systemCoords = false;
		int coordPosX = 0;
		int coordPosY = 0;
		// The forcedViews are specified in the window's XML file, the other views below are automatically determined by the existing ones
		java.util.Vector forcedViews = new java.util.Vector();
		// Conditional origins...
		String prevWindow = null;
		java.util.Vector controls = new java.util.Vector();
		String menuName;
		java.util.Vector views = new java.util.Vector();
		Widget menuWidget;
		Widget targetParent; // for dialogs
		String viewTypesSetupAction;

		java.util.Map idCacheMap = null;

		public Control getControlForID(int matchID)
		{
			if (idCacheMap == null)
				buildControlIDCacheMap(idCacheMap = new java.util.HashMap());
			java.util.Vector cachedList = (java.util.Vector) idCacheMap.get(new Integer(matchID));
			return (cachedList == null ||cachedList.isEmpty()) ? null : ((Control) cachedList.firstElement());
/*			for (int i = 0; i < controls.size(); i++)
			{
				Control c = (Control) controls.get(i);
				if (c.id == matchID)
					return c;
				c = c.getControlForID(matchID);
				if (c != null)
					return c;
			}
			return null;*/
		}

		public java.util.Vector getControlsWithID(int matchID)
		{
			if (idCacheMap == null)
				buildControlIDCacheMap(idCacheMap = new java.util.HashMap());
			return (java.util.Vector) idCacheMap.get(new Integer(matchID));
		}
		private void buildControlIDCacheMap(java.util.Map cacheMap)
		{
			java.util.Vector rv = new java.util.Vector();
			for (int i = 0; i < controls.size(); i++)
			{
				Control c = (Control) controls.get(i);
				c.buildControlIDCacheMap(cacheMap);
			}
		}
	}

	boolean evalBool(String s)
	{
		return s != null && (s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true"));
	}

	Boolean evalBoolObj(String s)
	{
		if (s != null)
			return null;
		else
			return new Boolean(s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true"));
	}

	// Handles ones like Window.Next(window)
	private String replaceArgumentExpression(String wholeExpr, String xbmcExpr, String myExprPrefix, String myExprPostfix, boolean preserveArg)
	{
		int idx;

		try
		{
			while ((idx = wholeExpr.toLowerCase().indexOf(xbmcExpr.toLowerCase())) != -1)
			{
				int paren1 = wholeExpr.indexOf('(', idx);
				int paren2 = wholeExpr.indexOf(')', idx);
				if (preserveArg)
				{
					wholeExpr = wholeExpr.substring(0, idx) + myExprPrefix + wholeExpr.substring(paren1 + 1, paren2) + myExprPostfix + wholeExpr.substring(paren2 + 1);
				}
				else
				{
					wholeExpr = wholeExpr.substring(0, idx) + myExprPrefix + myExprPostfix + wholeExpr.substring(paren2 + 1);
				}
			}
		}
		catch (RuntimeException e)
		{
			System.out.println("ERROR parsing expression:" + wholeExpr);
			throw e;
		}
		return wholeExpr;
	}

	private String localizeStrings(String expr, boolean withinExpression, Control controlContext)
	{
		if (expr == null) return "\"\"";
		String orgExpr = expr;
		// Check if its just a straight reference (these may still need processing though for char replacement
		if (stringMap.containsKey(expr))
			expr = (String) stringMap.get(expr);
		int idx;
		// XBMC Parsing Rules
		// XBMC runs through and replaces any $LOCALIZE[number] blocks with the real string from strings.xml.
		// XBMC then runs through and translates the $INFO[infolabel,prefix,postfix] blocks from left to right.
		// If the Info manager returns an empty string from the infolabel, then nothing is rendered for that block.
		// If the Info manager returns a non-empty string from the infolabel, then XBMC prints the prefix string, then the returned infolabel information, then the postfix string. Note that any $COMMA fields are replaced by real commas, and $$ is replaced by $.
		// Any pieces of information outside of the $INFO blocks are rendered unchanged.

		// First we'll go through and replace all the $LOCALIZE[number] block swith the literal text replacement
		while ((idx = expr.indexOf("$LOCALIZE[")) != -1)
		{
			int idx2 = idx + 10;
			// Enforce closing bracket matching here
			int depth = 0;
			while (idx2 < expr.length() && (expr.charAt(idx2) != ']' || depth > 0))
			{
				if (expr.charAt(idx2) == '[')
					depth++;
				else if (expr.charAt(idx2) == ']')
					depth--;
				idx2++;
			}
			String newString = (String)stringMap.get(expr.substring(idx + 10, idx2).trim());
			if (newString == null)
			{
				System.out.println("ERROR string reference not found for id=" + expr.substring(idx + 10, idx2));
				break;
			}
			else
			{
				expr = expr.substring(0, idx) + newString + expr.substring(idx2 + 1);
			}
		}
		// Escape any quotes that already exist in the expression
		expr = expr.replace("\"", "\\\"");
		// Now we convert the whole thing into a String-based expression format;
		// any $INFO blocks within it will be converted next
		if (withinExpression)
			expr = "\"" + expr + "\"";

		// Now convert all of the $INFO blocks to their corresponding function
		while (withinExpression && (idx = expr.indexOf("$INFO[")) != -1)
		{
			int idx2 = idx + 6;
			// Enforce closing bracket matching here
			int depth = 0;
			while (idx2 < expr.length() && (expr.charAt(idx2) != ']' || depth > 0))
			{
				if (expr.charAt(idx2) == '[')
					depth++;
				else if (expr.charAt(idx2) == ']')
					depth--;
				idx2++;
			}
			String infoBlock = expr.substring(idx + 6, idx2);
			java.util.StringTokenizer toker = new java.util.StringTokenizer(infoBlock, ",", true);
			String infoExpr = toker.nextToken();
			if (",".equals(infoExpr))
				infoExpr = toker.nextToken(); // weird special case where they have no infoLabel, but do specify a numeric prefix which should not be localized, used in the shutdown timer
			else if (toker.hasMoreTokens())
				toker.nextToken(); // comma
			String infoPrefix = "";
			String infoPostfix = "";
			String nexty = toker.hasMoreTokens() ? toker.nextToken() : ",";
			if (!nexty.equals(","))
			{
				infoPrefix = nexty;
				infoPrefix = infoPrefix.replace("$COMMA", ",");
				infoPrefix = infoPrefix.replace("$LBRACKET", "[");
				infoPrefix = infoPrefix.replace("$RBRACKET", "]");
				infoPrefix = infoPrefix.replace("$$", "$");
				if (toker.hasMoreTokens())
					toker.nextToken(); // comma
			}
			if (toker.hasMoreTokens())
			{
				infoPostfix = toker.nextToken();
				infoPostfix = infoPostfix.replace("$COMMA", ",");
				infoPostfix = infoPostfix.replace("$LBRACKET", "[");
				infoPostfix = infoPostfix.replace("$RBRACKET", "]");
				infoPostfix = infoPostfix.replace("$$", "$");
			}
			infoExpr = translateStringExpression(infoExpr, controlContext);
			if (infoPrefix.length() > 0 || infoPostfix.length() > 0)
				expr = expr.substring(0, idx) + "\" + If(!IsEmpty(" + infoExpr + "), \"" + infoPrefix + "\" + " + infoExpr + " + \"" + infoPostfix + "\", \"\") + \"" + expr.substring(idx2 + 1);
			else
				expr = expr.substring(0, idx) + "\" + " + infoExpr + " + \"" + expr.substring(idx2 + 1);
		}

// NOTE: WE NEED TO SUPPORT THE COLOR TAGS IN LABELS!!!
		// Now remove all of the [COLOR][/COLOR] tags since we don't support those yet; remove [B][/B] tags as well
		expr = java.util.regex.Pattern.compile("\\[\\/*COLOR[^\\]]*\\]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(expr).replaceAll("");
		expr = java.util.regex.Pattern.compile("\\[\\/*B[^\\]]*\\]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(expr).replaceAll("");

		expr = java.util.regex.Pattern.compile("\\[UPPERCASE\\]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(expr).replaceAll("\" + java_lang_String_toUpperCase(\"");
		expr = java.util.regex.Pattern.compile("\\[\\/UPPERCASE\\]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(expr).replaceAll("\") + \"");

		expr = expr.replace("[CR]", "\\n");
		expr = expr.replace("\r\n", "\\n");
		expr = expr.replace("\n", "\\n");

//		exprDump.put(orgExpr, expr);

		return expr;
	}

	private String resolveMenuName(String expr)
	{
		String rv;
		if (expr.toLowerCase().endsWith(".xml"))
			expr = expr.substring(0, expr.length() - 4);
		if (windowAliasMap.containsKey(expr.toLowerCase()))
			rv = (String) windowAliasMap.get(expr.toLowerCase());
		else if (windowIDMap.containsKey(expr.toLowerCase()))
			rv = (String) windowIDMap.get(expr.toLowerCase());
		else
			rv = expr;
		if ("home".equalsIgnoreCase(rv))
			return "Main Menu";
		else
			return rv;
	}

	private String resolveWindowID(String expr)
	{
		String rv = null;
		if (expr.toLowerCase().endsWith(".xml"))
			expr = expr.substring(0, expr.length() - 4);
		if (windowAliasMap.containsKey(expr.toLowerCase()))
			expr = (String) windowAliasMap.get(expr.toLowerCase());
		if (windowNameToIDMap.containsKey(expr.toLowerCase()))
			rv = (String) windowNameToIDMap.get(expr.toLowerCase());
		else if (dialogNameToIDMap.containsKey(expr.toLowerCase()))
			rv = (String) dialogNameToIDMap.get(expr.toLowerCase());
		else
		{
			if (expr.toLowerCase().startsWith("my"))
				expr = expr.substring(2);
			if (windowNameToIDMap.containsKey(expr.toLowerCase()))
				rv = (String) windowNameToIDMap.get(expr.toLowerCase());
			else if (dialogNameToIDMap.containsKey(expr.toLowerCase()))
				rv = (String) dialogNameToIDMap.get(expr.toLowerCase());
			else
			{
				try
				{
					Integer.parseInt(expr);
					rv = expr;
				}
				catch (NumberFormatException nfe)
				{
					rv = "\"" + expr + "\"";
				}
			}
		}
		// Unify the IDs so that they always resolve to the same ID for different aliases
		if (windowIDMap.containsKey(rv))
			return windowNameToIDMap.get(windowIDMap.get(rv)).toString();
		else if (dialogIDMap.containsKey(rv))
			return dialogNameToIDMap.get(dialogIDMap.get(rv)).toString();
		return rv;
	}

	private Widget resolveMenuWidget(String expr) throws Exception
	{
		if (expr.toLowerCase().endsWith(".xml"))
			expr = expr.substring(0, expr.length() - 4);
		if (expr.toLowerCase().startsWith("my"))
			expr = expr.substring(2);
		if (windowAliasMap.containsKey(expr.toLowerCase()))
			expr = (String) windowAliasMap.get(expr.toLowerCase());
		else if (windowIDMap.containsKey(expr.toLowerCase()))
			expr = (String) windowIDMap.get(expr.toLowerCase());
		else if (dialogIDMap.containsKey(expr.toLowerCase()))
			expr = (String) dialogIDMap.get(expr.toLowerCase());
		Widget rv = (Widget) windowWidgMap.get(expr.toLowerCase());
		if (rv == null)
		{
			System.out.println("Autocreating non-existent window of: " + expr);
			Widget newMenu;
			File winFile = new File(defaultResDir, ((String)winNameToFilenameMap.get(expr) == null ? (expr + ".xml") :(String)winNameToFilenameMap.get(expr)));
			if (!winFile.isFile())
			{
				// Try to find a custom window
				String[] fileList = defaultResDir.list();
				for (int i = 0; fileList != null && i < fileList.length; i++)
				{
					if (fileList[i].toLowerCase().startsWith("custom" + expr) && fileList[i].toLowerCase().endsWith(".xml"))
					{
						winFile = new File(defaultResDir, fileList[i]);
						windowIDMap.put(expr.toLowerCase(), fileList[i].toLowerCase().substring(0, fileList[i].length() - 4));
						break;
					}
				}
			}
			if (!winFile.isFile())
			{
				String settingsMode = null;
				// See if its a special case of a window name
				if ("picturessettings".equalsIgnoreCase(expr))
					settingsMode = "Pictures";
				else if ("programssettings".equalsIgnoreCase(expr))
					settingsMode = "Programs";
				else if ("weathersettings".equalsIgnoreCase(expr))
					settingsMode = "Weather";
				else if ("musicsettings".equalsIgnoreCase(expr))
					settingsMode = "Music";
				else if ("systemsettings".equalsIgnoreCase(expr))
					settingsMode = "System";
				else if ("videossettings".equalsIgnoreCase(expr))
					settingsMode = "Videos";
				else if ("networksettings".equalsIgnoreCase(expr))
					settingsMode = "Network";
				else if ("appearancesettings".equalsIgnoreCase(expr))
					settingsMode = "Appearance";
				else
				{
					System.out.println("ERROR MISSING MENU XML FILE FOR MENU " + expr);
				}
				if (settingsMode != null)
				{
					newMenu = mgroup.addWidget(MENU);
					WidgetFidget.setName(newMenu, expr);
					windowWidgMap.put(expr.toLowerCase(), newMenu);
					WidgetFidget.contain(addWidgetNamed(addWidgetNamed(newMenu, HOOK, "BeforeMenuLoad"), ACTION, "AddStaticContext(\"SettingsMode\", \"" + settingsMode + "\")"),
						resolveMenuWidget("SettingsCategory"));
				}
				else
				{
					// Don't create empty menus for ones that don't exist
					newMenu = null;
				}
			}
			else
				newMenu = loadWindow(winFile);
			return newMenu;
		}
		else
			return rv;
	}

	private int parseInt(String expr)
	{
		if (expr == null) return 0;
		if (constantsMap.containsKey(expr))
			return (int)Long.parseLong((String) constantsMap.get(expr));
		else
			return (int)Long.parseLong(expr);
	}

// NOTE: LATER THIS IS INTENDED TO DO AN EXPRESSION THAT USES THE CONSTANT
	private String resolveProperty(String expr)
	{
		if (constantsMap.containsKey(expr))
			return (String) constantsMap.get(expr);//"=" + expr;
		else
			return expr;
	}

	private java.awt.Color parseColor(String x) throws Exception
	{
		if (colorMap.containsKey(x))
			return new java.awt.Color((int)(Long.parseLong((String) colorMap.get(x), 16) & 0xFFFFFFFF), true);
		java.lang.reflect.Field colorField = null;
		try
		{
			if ((colorField = java.awt.Color.class.getDeclaredField(x.toLowerCase())) != null)
				return (java.awt.Color) colorField.get(null);
		}
		catch (Exception e){}
		java.awt.Color rv = java.awt.Color.getColor(x);
		if (rv != null)
			return rv;
		try
		{
			return new java.awt.Color((int)(Long.parseLong(x, 16) & 0xFFFFFFFF), true);
		}
		catch (NumberFormatException nfe)
		{
			System.out.println("INVALID COLOR SPECIFIED OF:" +  x);
			return java.awt.Color.white;
		}
	}

	private Widget getMoveFocusChain(String action, String targetId, Control containerContext)
	{
		Widget rv;
		int idVal = parseInt(targetId);
		int minID = 50;
		int maxID = 60;
		if (containerContext != null && containerContext.win.menuName.equalsIgnoreCase("filebrowser"))
		{
			minID = 450;
			maxID = 452;
		}
		else if (containerContext != null && containerContext.win.menuName.equalsIgnoreCase("mytv"))
		{
			minID = 10;
			maxID = 18;
		}
		if (idVal >= minID && idVal < maxID && (minID == 450 || minID == 10))
		{
			Widget topCond = mgroup.addWidget(CONDITIONAL);
			rv = topCond;
			WidgetFidget.setName(topCond, "!SetFocusForVariable(\"XBMCID\", " + targetId + ")");
			// Find alternate container view instead
			addWidgetNamed(addWidgetNamed(addWidgetNamed(topCond = addWidgetNamed(addWidgetNamed(
				topCond, ACTION, "i = " + minID),
				CONDITIONAL, "i < " + maxID),
				CONDITIONAL, "i != " + targetId),
				CONDITIONAL, "SetFocusForVariable(\"XBMCID\", i)"),
				ACTION, "i = " + maxID);
			WidgetFidget.contain(addWidgetNamed(topCond, ACTION, "i = i + 1"), topCond);
		}
		else if ((idVal >= minID && idVal < maxID) || (idVal >= 500 && idVal < 520))
		{
			if (moveFocusChainContainerSrc == null)
			{
				moveFocusChainContainerSrc = mgroup.addWidget(CONDITIONAL);
				WidgetFidget.setName(moveFocusChainContainerSrc, "!SetFocusForVariable(\"XBMCID\", TargetID)");
				// Find alternate container view instead
				Widget topCond;
				addWidgetNamed(addWidgetNamed(addWidgetNamed(topCond = addWidgetNamed(addWidgetNamed(
					moveFocusChainContainerSrc, ACTION, "i = 50"),
					CONDITIONAL, "i < 520"),
					CONDITIONAL, "i != TargetID"),
					CONDITIONAL, "SetFocusForVariable(\"XBMCID\", i)"),
					ACTION, "i = 520");
				addWidgetNamed(addWidgetNamed(topCond, CONDITIONAL, "i == 59"), ACTION, "i = 499");
				WidgetFidget.contain(addWidgetNamed(topCond, ACTION, "i = i + 1"), topCond);
			}
			WidgetFidget.contain(rv = addWidgetNamed(null, ACTION, "TargetID = " + targetId), moveFocusChainContainerSrc);
		}
		else
		{
			if (moveFocusChainSrc == null)
			{
				moveFocusChainSrc = mgroup.addWidget(CONDITIONAL);
				WidgetFidget.setName(moveFocusChainSrc, "!SetFocusForVariable(\"XBMCID\", TargetID)");
				addWidgetNamed(addWidgetNamed(moveFocusChainSrc, BRANCH, "false"), ACTION, "java_util_HashSet_clear(CircFocusCheck)");
				addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(moveFocusChainSrc, BRANCH, "true"),
					ACTION, "Originator = IsEmpty(CircFocusCheck)"),
					CONDITIONAL, "AddElement(CircFocusCheck, TargetID)"),
					CONDITIONAL, "!SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", TargetID), TargetAction, 1) && Originator"),
					ACTION, "PassiveListen()");
			}
			rv = addWidgetNamed(null, ACTION, "TargetID = " + targetId);
			WidgetFidget.contain(addWidgetNamed(rv, ACTION, "TargetAction = \"" + action + "\""), moveFocusChainSrc);
		}
		return rv;
	}

	private String getSetFocusExpr(String id)
	{
		return "SetFocusForVariable(\"XBMCID\", " + id + ")";
//		return "SetFocusForVariable(If(GetVariableFromContext(\"XBMCID\", " + id + ", \"RetainedFocusParentDashItemXBMCID\") == null, \"XBMCID\", \"ParentDashItemXBMCID\"), " +
//			"If(GetVariableFromContext(\"XBMCID\", " + id + ", \"RetainedFocusParentDashItemXBMCID\") == null, " + id + ", GetVariableFromContext(\"XBMCID\", " + id + ", \"RetainedFocusParentDashItemXBMCID\")))";
	}

/*	public static final Object[][] XBMC_REGEX_REPLACERS = {
// We don't need to translate IsEmpty since it matches perfectly
//		{ java.util.regex.Pattern.compile("IsEmpty\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE), "IsEmpty($1)" },
		{ java.util.regex.Pattern.compile("substring\\(([^()]*(\\([^()]*\\)[^()]*)*)\\,([^)]*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE),
			  "If(\"left\" == java_lang_String_toLowerCase(\"$4\"), StringStartsWith(java_lang_String_toLowerCase(\"\" + $1), java_lang_String_toLowerCase(\"$3\")), StringEndsWidth(java_lang_String_toLowerCase(\"\" + $1), java_lang_String_toLowerCase(\"$3\")))" },
		{ java.util.regex.Pattern.compile("substring\\(([^()]*(\\([^()]*\\)[^()]*)*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE),
			  "(StringIndexOf(java_lang_String_toLowerCase(\"\" + $1), java_lang_String_toLowerCase(\"$3\")) != -1)" },
		{ java.util.regex.Pattern.compile("stringcompare\\(([^()]*(\\([^()]*\\)[^()]*)*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE), "java_lang_String_equalsIgnoreCase(\"\" + $1, \"$3\")" },
// UNSUPPORTED!!!!!!!!!!!!!!
		{ java.util.regex.Pattern.compile("XBMC\\.AlarmClock\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE), "\"XBMCAlarmClock($1)\"" },

	};
*/
	// This will handle removal of $INFO[xxx] when its there and put quotes
	// around other string expressions which are constant
	public String getAsStringConstant(String s, boolean translateInternal)
	{
		if (s == null) return "";
		if (s.startsWith("$INFO[") && s.endsWith("]"))
		{
			if (translateInternal)
				return translateStringExpression(s.substring(6, s.length() - 1), null);
			else
				return s.substring(6, s.length() - 1);
		}
		else if (s.startsWith("$LOCALIZE[") && s.endsWith("]"))
		{
			return "\"" + stringMap.get(s.substring(10, s.length() - 1)) + "\"";
		}
		else
			return "\"" + s + "\"";
	}

	// Returns what's inside the first () in the expression; 1-based like regexp groups
	private String getParen(String expr)
	{
		return getParen(expr, 1);
	}
	private String getParen(String expr, int parenNum)
	{
		String rv = "";
		int idx0 = 0;
		while (parenNum > 0)
		{
			int idx1 = expr.indexOf('(', idx0);
			int idx2 = idx1 + 1;
			int depth = 0;
			while (idx2 < expr.length() && (expr.charAt(idx2) != ')' || depth > 0))
			{
				if (expr.charAt(idx2) == '(')
					depth++;
				else if (expr.charAt(idx2) == ')')
					depth--;
				idx2++;
			}
			if (parenNum == 1 && idx1 != -1 && idx2 != -1)
				return expr.substring(idx1 + 1, idx2);
			parenNum--;
			idx0 = idx2 + 1;
		}
		return rv;
	}

	// 0-based
	private String getArg(String expr, int num)
	{
		int lastIdx = -1;
		int nextIdx = expr.indexOf(',', lastIdx + 1);
		while (num > 0)
		{
			lastIdx = nextIdx;
			nextIdx = expr.indexOf(',', lastIdx + 1);
			num--;
		}
		return (nextIdx < 1) ? expr.substring(lastIdx + 1) : expr.substring(lastIdx + 1, nextIdx);
	}

	private String translateBooleanExpression(String xbmcExpr, Control controlContext)
	{
		return translateBooleanExpression(xbmcExpr, controlContext, -1, null);
	}
	private String translateBooleanExpression(String xbmcExpr, Control controlContext, int knownWinID)
	{
		return translateBooleanExpression(xbmcExpr, controlContext, knownWinID, null);
	}
	private String translateBooleanExpression(String xbmcExpr, Control controlContext, int knownWinID, java.util.Set loopVisIDs)
	{
		if (xbmcExpr == null) return null;
		String rv = (String) exprBoolCache.get(xbmcExpr);
		if (rv != null)
			return rv;
		StringBuffer sb = new StringBuffer();
		int parenDepth = 0;
		StringBuffer currFunction = new StringBuffer();
		int trueConstantDepth = 0;
		boolean lastWasTrue = false;
		for (int i = 0; i < xbmcExpr.length(); i++)
		{
			char c = xbmcExpr.charAt(i);
			if (parenDepth == 0)
			{
				if (c == '!')
				{
					if (trueConstantDepth <= 0)
						sb.append(c);
				}
				else if (c == '[')
				{
					if (trueConstantDepth > 0)
						trueConstantDepth++;
					else
						sb.append('(');
				}
				else if (c == ']' || c == '+' || c == '|' || c == ' ')
				{
					if (currFunction.length() > 0)
					{
						String newFunc = translateFunction(currFunction.toString(), controlContext, false, true, knownWinID, loopVisIDs);
						lastWasTrue = "true".equals(newFunc) && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '!');
						sb.append(newFunc);
						trueConstantDepth = 0;
						currFunction.setLength(0);
					}
					if (c == ']')
					{
						if (lastWasTrue && trueConstantDepth > 0)
							trueConstantDepth--;
						if (trueConstantDepth <= 0)
						{
							lastWasTrue = false;
							sb.append(')');
						}
					}
					else if (c == '+')
					{
						if (!lastWasTrue || trueConstantDepth <= 0)
						{
							sb.append("&&");
							lastWasTrue = false;
						}
					}
					else if (c == '|')
					{
						if (lastWasTrue)
						{
							if (trueConstantDepth == 0)
							{
								trueConstantDepth = 1;
								currFunction.setLength(0);
							}
						}
						else
							sb.append("||");
					}
					else if (trueConstantDepth <= 0)// if (c == ' ')
						sb.append(' ');
				}
				else if (c == '(')
				{
					if (trueConstantDepth <= 0)
					{
						currFunction.append(c);
						parenDepth++;
					}
				}
				else if (trueConstantDepth <= 0)
					currFunction.append(c);
			}
			else
			{
				if (c == ')')
					parenDepth--;
				currFunction.append(c);
			}
		}
		if (currFunction.length() > 0)
			sb.append(translateFunction(currFunction.toString(), controlContext, false, true, knownWinID, loopVisIDs));
		rv = sb.toString();
		if (xbmcExpr.indexOf("IsVisible") == -1 && xbmcExpr.indexOf("IsActive") == -1)
			exprBoolCache.put(xbmcExpr, rv);
//		exprDump.put(xbmcExpr.toLowerCase(), rv);
		return rv;
	}

	private String getMusicFunction(String argument, String xbmcAttrib)
	{
		if (xbmcAttrib.equals("title")) return "GetMediaTitle(" + argument + ")";
		else if (xbmcAttrib.equals("album")) return "GetAlbumName(" + argument + ")";
		else if (xbmcAttrib.equals("artist")) return "GetPeopleInShowInRole(" + argument + ", LocalizeString(\"Artist\"))";
		else if (xbmcAttrib.equals("year")) return "GetShowYear(" + argument + ")";
		else if (xbmcAttrib.equals("genre")) return "GetShowCategory(" + argument + ")";
		else if (xbmcAttrib.equals("duration")) return "GetFileDuration(" + argument + ")";
		else if (xbmcAttrib.equals("tracknumber")) return "If(GetTrackNumber(" + argument + ") == 0, \"\", GetTrackNumber(" + argument  + "))";
		else if (xbmcAttrib.equals("cover")) return "GetAlbumArt(" + argument + ")";
		else if (xbmcAttrib.equals("bitrate")) return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Bitrate\")";
		else if (xbmcAttrib.equals("playlistlength")) return "GetNumberOfPlaylistItems(GetCurrentPlaylist())";
		else if (xbmcAttrib.equals("playlistposition")) return "(GetCurrentPlaylistIndex() + 1)";
		else if (xbmcAttrib.equals("channels")) return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Channels\")";
		else if (xbmcAttrib.equals("bitspersample"))  return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.BitsPerSample\")";
		else if (xbmcAttrib.equals("samplerate"))  return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.SampleRate\")";
		else if (xbmcAttrib.equals("codec"))  return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Codec\")";
		else if (xbmcAttrib.equals("discnumber")) return "GetMediaFileMetadata(" + argument + ", \"DiscNumber\")";
		else if (xbmcAttrib.equals("rating")) return "GetMediaFileMetadata(" + argument + ", \"Rating\")";
		else if (xbmcAttrib.equals("comment")) return "GetMediaFileMetadata(" + argument + ", \"Comment\")";
		else if (xbmcAttrib.equals("lyrics")) return "GetMediaFileMetadata(" + argument + ", \"lyrics\")";
		else if (xbmcAttrib.equals("playlistplaying")) return "(IsCurrentMediaFileMusic() && GetCurrentPlaylist() != null)";
		// exists is special; it takes the offset # of the argument instead of the file object, its handled elsewhere
		//else if (xbmcAttrib.equals("exists")) return "(GetNumberOfPlaylistItems(GetCurrentPlaylist()) > (" + argument + " + GetCurrentPlaylistIndex()))";
		else if (xbmcAttrib.equals("hasprevious")) return "(GetCurrentPlaylistIndex() > 0)";
		else if (xbmcAttrib.equals("hasnext")) return "(GetCurrentPlaylistIndex() + 1 < GetNumberOfPlaylistItems(GetCurrentPlaylist()))";
		else if (xbmcAttrib.equals("channelname")) return "GetChannelName(" + argument + ")";
		else if (xbmcAttrib.equals("channelnumber")) return "GetChannelNumber(" + argument + ")";
		else if (xbmcAttrib.equals("channelgroup")) return "GetChannelNetwork(" + argument + ")";
		return "UNDEFINED-" + xbmcAttrib;
	}

	private String getListItemFunction(String argument, String xbmcAttrib)
	{
		if (xbmcAttrib.equals("thumb")) return "GetNodeThumbnail(" + argument + ")";
		else if (xbmcAttrib.equals("icon")) return "GetNodeIcon(" + argument + ")";
		else if (xbmcAttrib.equals("actualicon")) return "GetNodeIcon(" + argument + ")"; /* NOT IMPLEMENTED CORRECTLY */
		else if (xbmcAttrib.equals("overlay")) return "ListItemOverlay"; /* NOT IMPLEMENTED CORRECTLY */
		else if (xbmcAttrib.equals("label")) return "GetNodePrimaryLabel(" + argument + ")";
		else if (xbmcAttrib.equals("label2")) return "GetNodeSecondaryLabel(" + argument + ")";
		// We do the primary label for the title so we can fix episode titles to be correct in their view
		else if (xbmcAttrib.equals("title")) return "GetNodeProperty(" + argument + ", \"Title\")";//GetMediaTitle(" + argument + ")";
		else if (xbmcAttrib.equals("tracknumber")) return "If(GetTrackNumber(" + argument + ") == 0, \"\", GetTrackNumber(" + argument + "))";
		else if (xbmcAttrib.equals("artist")) return "GetPeopleInShowInRole(" + argument + ", LocalizeString(\"Artist\"))";
		else if (xbmcAttrib.equals("album")) return "GetAlbumName(" + argument + ")";
		else if (xbmcAttrib.equals("albumartist")) return "GetAlbumArtist(" + argument + ")";
		else if (xbmcAttrib.equals("year")) return "If(IsEmpty(GetShowYear(" + argument + ")), GetSeriesPremiereDate(" + argument + "), GetShowYear(" + argument + "))";
		else if (xbmcAttrib.equals("genre")) return "GetShowCategory(" + argument + ")";
		else if (xbmcAttrib.equals("director")) return "GetPeopleInShowInRole(" + argument + ", LocalizeString(\"Director\"))";
		else if (xbmcAttrib.equals("filename")) return "GetFileNameFromPath(" + argument + ")";
		else if (xbmcAttrib.equals("filenameandpath")) return "GetAbsoluteFilePath(" + argument + ")";
		else if (xbmcAttrib.equals("date")) return "GetNodeProperty(" + argument + ", \"Date\")";//(PrintDateShort(GetFileStartTime(" + argument + ")) + \" \" + PrintTimeShort(GetFileStartTime(" + argument + ")))";
		else if (xbmcAttrib.equals("size")) return "GetSize(" + argument + ")";
		else if (xbmcAttrib.equals("rating")) return "GetMediaFileMetadata(" + argument + ", \"Rating\")";
		else if (xbmcAttrib.equals("ratingandvotes")) return "GetMediaFileMetadata(" + argument + ", \"IMDBRatingAndVotes\")";
		else if (xbmcAttrib.equals("programcount")) return "\"ListItem.ProgramCount\"";  /*UNIMPLEMENTED!!!!!*/
		else if (xbmcAttrib.equals("duration")) return "PrintDurationShort(GetFileDuration(" + argument + "))";
		else if (xbmcAttrib.equals("isselected")) return "((" + argument + ") == MenuListItem)";
		else if (xbmcAttrib.equals("isplaying")) return "(GetCurrentMediaFile() == GetNodeDataObject(" + argument + "))";
		else if (xbmcAttrib.equals("plot")) return "GetShowDescription(" + argument + ")";
		else if (xbmcAttrib.equals("plotoutline")) return "GetShowEpisode(" + argument + ")"; // NOT PROPERLY IMPLEMENTED - maybe this should be episode name
		else if (xbmcAttrib.equals("episode")) return "GetNodeProperty(" + argument + ", \"EpisodeNumber\")";
		else if (xbmcAttrib.equals("season")) return "GetNodeProperty(" + argument + ", \"SeasonNumber\")";
		else if (xbmcAttrib.equals("tvshowtitle")) return "GetShowTitle(" + argument + ")";
		else if (xbmcAttrib.equals("premiered")) return "GetSeriesPremiereDate(" + argument + ")";
		else if (xbmcAttrib.equals("comment")) return "GetNodeProperty(" + argument + ", \"Comment\")";
		else if (xbmcAttrib.equals("path")) return "GetFileForSegment(" + argument + ", 0)";
		else if (xbmcAttrib.equals("foldername")) return "GetNodePrimaryLabel(GetNodeParent(" + argument + "))";
		else if (xbmcAttrib.equals("picturepath")) return "GetNodeFullPath(" + argument + ")";
		else if (xbmcAttrib.equals("pictureresolution")) return "GetMediaFileMetadata(" + argument + ", \"PictureResolution\")";
		else if (xbmcAttrib.equals("picturedatetime")) return "(PrintDateShort(GetAiringStartTime(" + argument + ")) + \" \" + PrintTimeShort(GetAiringStartTime(" + argument + ")))";
		else if (xbmcAttrib.equals("studio")) return "GetMediaFileMetadata(" + argument + ", \"Studio\")";
		else if (xbmcAttrib.equals("mpaa")) return "GetShowRated(" + argument + ")";
		else if (xbmcAttrib.equals("cast")) return "GetPeopleInShow(" + argument + ")";
		else if (xbmcAttrib.equals("castandrole")) return "GetPeopleInShow(" + argument + ")"; // NOT PROPERLY IMPLEMENTED
		else if (xbmcAttrib.equals("writer")) return "GetPeopleInShowInRole(" + argument + ", LocalizeString(\"Writer\"))";
		else if (xbmcAttrib.equals("tagline")) return "GetMediaFileMetadata(" + argument + ", \"Tagline\")";
		else if (xbmcAttrib.equals("top250")) return "GetMediaFileMetadata(" + argument + ", \"Top250\")";
		else if (xbmcAttrib.equals("trailer")) return "GetMediaFileMetadata(" + argument + ", \"Trailer\")";
		else if (xbmcAttrib.equals("starrating")) return "GetMediaFileMetadata(" + argument + ", \"StarRating\")";
		else if (xbmcAttrib.equals("sortletter")) return "Substring(GetNodePrimaryLabel(" + argument + "), 0, 1)";
		else if (xbmcAttrib.equals("videocodec")) return "GetMediaFileMetadata(" + argument + ", \"Format.Video.Codec\")";
		else if (xbmcAttrib.equals("videoresolution")) return "GetMediaFileMetadata(" + argument + ", \"Format.Video.Resolution\")";
		else if (xbmcAttrib.equals("videoaspect")) return "GetMediaFileMetadata(" + argument + ", \"Format.Video.Aspect\")";
		else if (xbmcAttrib.equals("audiocodec")) return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Codec\")";
		else if (xbmcAttrib.equals("audiochannels")) return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Channels\")";
		else if (xbmcAttrib.equals("audiolanguage")) return "GetMediaFileMetadata(" + argument + ", \"Format.Audio.Language\")";
		else if (xbmcAttrib.equals("subtitlelanguage")) return "GetMediaFileMetadata(" + argument + ", \"Format.Subtitle.Language\")";
		else if (xbmcAttrib.equals("isfolder")) return "IsNodeFolder(" + argument + ")";
		else if (xbmcAttrib.equals("starttime")) return "PrintTimeShort(GetAiringStartTime(" + argument + "))";
		else if (xbmcAttrib.equals("endtime")) return "PrintTimeShort(GetAiringEndTime(" + argument + "))";
		else if (xbmcAttrib.equals("startdate")) return "PrintDateShort(GetAiringStartTime(" + argument + "))";
		else if (xbmcAttrib.equals("enddate")) return "PrintDateShort(GetAiringEndTime(" + argument + "))";
		else if (xbmcAttrib.equals("nexttitle")) return "\"ListItem.NextTitle\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextgenre")) return "\"ListItem.NextGenre\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextplot")) return "\"ListItem.NextPlot\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextplotoutline")) return "\"ListItem.NextPlotOutline\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextstarttime")) return "\"ListItem.NextStartTime\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextendtime")) return "\"ListItem.NextEndTime\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextstartdate")) return "\"ListItem.NextStartDate\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("nextenddate")) return "\"ListItem.NextEndDate\""; /* UNIMPLEMENTED - NOT SURE WHAT THIS WOULD MEAN SINCE IT MAY HAVE NO CONTEXT*/
		else if (xbmcAttrib.equals("channelname")) return "GetChannelName(" + argument + ")";
		else if (xbmcAttrib.equals("channelnumber")) return "GetChannelNumber(" + argument + ")";
		else if (xbmcAttrib.equals("channelgroup")) return "GetChannelNetwork(" + argument + ")";
		else if (xbmcAttrib.equals("hastimer")) return "(FindElementIndex(GetScheduledRecordings(), " + argument + ") >= 0)";
		else if (xbmcAttrib.equals("isrecording")) return "IsFileCurrentlyRecording(" + argument + ")";
		else if (xbmcAttrib.equals("isencrypted")) return "false"; /* UNIMPLEMENTED */
		else //if (xbmcAttrib.startsWith("property("))
		{
			String subProp = getParen(xbmcAttrib);
			if (subProp.equals("runtime"))
				return "GetShowDuration(" + argument + ")";
			return "GetNodeProperty(" + argument + ", \"" + subProp + "\")";
		}
	}

	private String getTimeFormat(String argument, String timeFormat)
	{
		if (timeFormat == null || timeFormat.length() == 0)
			return "PrintDurationShort(" + argument + ")";
		else if (timeFormat.equals("(hh)"))
			return "DurFormat(\"%h\", " + argument + ")";
		else if (timeFormat.equals("(mm)"))
			return "DurFormat(\"%m\", " + argument + ")";
		else if (timeFormat.equals("(ss)"))
			return "DurFormat(\"%s\", " + argument + ")";
		else if (timeFormat.equals("(hh:mm)"))
			return "DurFormat(\"%rh:%m\", " + argument + ")";
		else if (timeFormat.equals("(mm:ss)"))
			return "DurFormat(\"%rm:%s\", " + argument + ")";
		else if (timeFormat.equals("(hh:mm:ss)"))
			return "DurFormat(\"%rh:%rm:%s\", " + argument + ")";
		else
			return "PrintDurationShort(" + argument + ")";
	}

	private String translateImageExpression(String xbmcExpr, Control controlContext)
	{
		if (xbmcExpr == null) return null;
		if (xbmcExpr.startsWith("special://skin/"))
			return "\"" + xbmcExpr.substring(15) + "\"";
		String rv = (String) exprImageCache.get(xbmcExpr);
		if (rv != null)
			return rv;
		String transMe = xbmcExpr;
		if (xbmcExpr.startsWith("$INFO[") && xbmcExpr.endsWith("]"))
			transMe = xbmcExpr.substring(6, xbmcExpr.length() - 1);
		rv = translateFunction(transMe, controlContext, true, false, -1, null);
		if (xbmcExpr.indexOf("IsVisible") == -1 && xbmcExpr.indexOf("IsActive") == -1)
			exprImageCache.put(xbmcExpr, rv);
//		exprDump.put(xbmcExpr.toLowerCase(), rv);
		return rv;
	}

	private String translateStringExpression(String xbmcExpr, Control controlContext)
	{
		if (xbmcExpr == null) return null;
		String rv = (String) exprStringCache.get(xbmcExpr);
		if (rv != null)
			return rv;
		rv = translateFunction(xbmcExpr, controlContext, false, false, -1, null);
		if (xbmcExpr.indexOf("IsVisible") == -1 && xbmcExpr.indexOf("IsActive") == -1)
			exprStringCache.put(xbmcExpr, rv);
//		exprDump.put(xbmcExpr.toLowerCase(), rv);
		return rv;
	}

	private String translateFunction(String xbmcExpr, Control controlContext, boolean imageSource, boolean preferBool, int knownWinID, java.util.Set loopVisIDs)
	{
		if (xbmcExpr == null) return null;
		if (xbmcExpr.indexOf("java_") != -1) return xbmcExpr; // these are from our own stuff
		String lcExpr = xbmcExpr.toLowerCase().trim();
		if (lcExpr.length() == 0) return "";
		if (controlContext != null && controlContext.win != null)
			knownWinID = controlContext.win.id;
		if (lcExpr.startsWith("isempty(") && lcExpr.endsWith(")"))
		{
			// Remove isempty wrapper and call this recursively
			return "IsEmpty(" + translateFunction(xbmcExpr.substring(8, xbmcExpr.length() - 1), controlContext, imageSource, preferBool, knownWinID, loopVisIDs) + ")";
		}
		if (lcExpr.equals("false") || lcExpr.equals("no") || lcExpr.equals("off"))
			return "false";
		if (lcExpr.equals("true") || lcExpr.equals("yes") || lcExpr.equals("on"))
			return "true";
		int dotIdx = lcExpr.indexOf(".");
		String exprPrefix = (dotIdx < 0) ? lcExpr : lcExpr.substring(0, dotIdx);
		String subfunc = (dotIdx < 0) ? "" : lcExpr.substring(dotIdx + 1);
		if (exprPrefix.equals("player"))
		{
			if (subfunc.equals("hasaudio")) return "IsCurrentMediaFileMusic()";
			else if (subfunc.equals("hasvideo")) return "DoesCurrentMediaFileHaveVideo()";
			else if (subfunc.equals("hasmedia")) return "HasMediaFile()";
			else if (subfunc.equals("playing")) return "IsPlaying()";
			else if (subfunc.equals("paused")) return "(!IsPlaying() && HasMediaFile())";
			else if (subfunc.equals("rewinding")) return "(GetPlaybackRate() < 0)";
			else if (subfunc.equals("forwarding")) return "(GetPlaybackRate() > 1.0)";
			else if (subfunc.equals("rewinding2x")) return "(GetPlaybackRate() == -2)";
			else if (subfunc.equals("rewinding4x")) return "(GetPlaybackRate() == -4)";
			else if (subfunc.equals("rewinding8x")) return "(GetPlaybackRate() == -8)";
			else if (subfunc.equals("rewinding16x")) return "(GetPlaybackRate() == -16)";
			else if (subfunc.equals("rewinding32x")) return "(GetPlaybackRate() == -32)";
			else if (subfunc.equals("forwarding2x")) return "(GetPlaybackRate() == 2)";
			else if (subfunc.equals("forwarding4x")) return "(GetPlaybackRate() == 4)";
			else if (subfunc.equals("forwarding8x")) return "(GetPlaybackRate() == 8)";
			else if (subfunc.equals("forwarding16x")) return "(GetPlaybackRate() == 16)";
			else if (subfunc.equals("forwarding32x")) return "(GetPlaybackRate() == 32)";
			else if (subfunc.equals("canrecord")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("recording")) return "IsCurrentMediaFileRecording()";
			else if (subfunc.equals("displayafterseek")) return "((Time() - Max(0,LastSeekCompleteTime)) < 2500)"; // has a 2500 msec timeout after a seek
			else if (subfunc.equals("caching")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("cachelevel")) return "\"Player.CacheLevel\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("seekbar")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("progress")) return "((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))*1.0)/GetMediaDuration()";
			else if (subfunc.equals("seeking")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("showtime")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("showcodec")) return "ShowCodecInfo";
			else if (subfunc.equals("showinfo")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.startsWith("seektime")) return getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", lcExpr.substring(15));
			else if (subfunc.startsWith("timeremaining")) return getTimeFormat("GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))", lcExpr.substring(20));
			else if (subfunc.startsWith("timespeed")) return "(" + getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", lcExpr.substring(16)) + "\" (\"GetPlaybackRate() + \"x)\")";
			else if (subfunc.startsWith("time")) return getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", lcExpr.substring(11));
			else if (subfunc.startsWith("duration")) return getTimeFormat("GetMediaDuration()", lcExpr.substring(15));
			else if (subfunc.startsWith("finishtime")) return getTimeFormat("Time() + GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))", lcExpr.substring(17));
			else if (subfunc.equals("volume")) return "GetVolume()";
			else if (subfunc.equals("subtitledelay")) return "GetSubtitleDelay()";
			else if (subfunc.equals("audiodelay")) return "GetAudioDelay()";
			else if (subfunc.equals("muted")) return "IsMuted()";
			else if (subfunc.equals("hasduration")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("chaptercount")) return "GetDVDNumberOfChapters()";
			else if (subfunc.equals("chaptername")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"ChapterName.\" + GetDVDCurrentChapter())";
			else if (subfunc.equals("chapter")) return "GetDVDCurrentChapter()";
			else if (subfunc.equals("starrating")) return "\"Player.StarRating\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("passthrough")) return "\"Player.passthrough\""; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("weather"))
		{
			if (subfunc.equals("conditions"))
			{
				if (imageSource)
					return "(\"WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_icon\") + \".png\")";
				else
					return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_conditions\")";
			}
			else if (subfunc.equals("temperature")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_temp\")";
			else if (subfunc.equals("location")) return "tv_sage_weather_WeatherDotCom_getLocationInfo(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_location\")";
			else if (subfunc.equals("isfetched")) return "true"; // updated in the main menu AfterMenuLoad hook
			else if (subfunc.equals("fanartcode")) return "\"Weather.FanArtCode\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("plugin")) return "\"WeatherDotCom\"";
		}
		else if (exprPrefix.equals("pvr"))
		{
			if (subfunc.equals("isrecording")) return "(Size(GetCurrentlyRecordingMediaFiles()) > 0)";
			else if (subfunc.equals("hastimer")) return "(Size(GetScheduledRecordings()) > 0)";
			else if (subfunc.equals("nowrecordingtitle")) return "GetAiringTitle(GetElement(GetCurrentlyRecordingMediaFiles(), 0))";
			else if (subfunc.equals("nowrecordingdatetime")) return "(PrintDateShort(GetAiringStartTime(GetElement(GetCurrentlyRecordingMediaFiles(), 0))) + \" \" + PrintTimeShort(GetAiringStartTime(GetElement(GetCurrentlyRecordingMediaFiles(), 0))))";
			else if (subfunc.equals("nowrecordingchannel")) return "GetChannelName(GetElement(GetCurrentlyRecordingMediaFiles(), 0))";
			else if (subfunc.equals("nextrecordingtitle")) return "GetAiringTitle(GetElement(GetScheduledRecordings(), 0))";
			else if (subfunc.equals("nextrecordingdatetime")) return "(PrintDateShort(GetAiringStartTime(GetElement(GetScheduledRecordings(), 0))) + \" \" + PrintTimeShort(GetAiringStartTime(GetElement(GetScheduledRecordings(), 0))))";
			else if (subfunc.equals("nextrecordingchannel")) return "GetChannelName(GetElement(GetScheduledRecordings(), 0))";
			else if (subfunc.equals("backendname")) return "\"SageTV\"";
			else if (subfunc.equals("backendversion")) return "GetProperty(\"version\", \"\")";
			else if (subfunc.equals("backendhost")) return "If(IsServerUI(), \"Local\", \"Server\")";
			else if (subfunc.equals("backenddiskspace") || subfunc.equals("totaldiscspace"))
				return "\"" + stringMap.get("18055") + " \" + ((GetTotalDiskspaceAvailable() + GetUsedVideoDiskspace())/1073741824) + \" GByte - " +
					stringMap.get("156") + ": \" + (GetUsedVideoDiskspace()/1073741824) + \" GByte\"";
			else if (subfunc.equals("backendchannels")) return "Size(FilterByBoolMethod(GetAllChannels(), \"IsChannelViewable\", true))";
			else if (subfunc.equals("backendtimers")) return "Size(GetScheduledRecordings())";
			else if (subfunc.equals("backendrecordings")) return "Size(GetMediaFiles(\"T\"))";
			else if (subfunc.equals("backendnumber")) return "1";
			else if (subfunc.equals("hasepg")) return "true"; /*UNIMLEMENTED---BUT IT ALSO ISN'T IN XBMC*/
			else if (subfunc.equals("hastxt")) return "true"; /*UNIMLEMENTED---BUT IT ALSO ISN'T IN XBMC*/
			else if (subfunc.equals("hasdirector")) return "true"; /*UNIMLEMENTED---BUT IT ALSO ISN'T IN XBMC*/
			else if (subfunc.equals("nexttimer")) return "\"" + stringMap.get("18190") + " \" + PrintDateShort(GetAiringStartTime(GetElement(GetScheduledRecordings(), 0))) + \" " +
				stringMap.get("18191") + " \" + PrintTimeShort(GetAiringStartTime(GetElement(GetScheduledRecordings(), 0)))";
			else if (subfunc.equals("isplayingtv") || subfunc.equals("isplayingrecording")) return "IsTVFile(GetCurrentMediaFile())";
			else if (subfunc.equals("isplayingradio")) return "false"; /*UNIMPLEMENTED*/
			else if (subfunc.equals("istimeshifting")) return "IsFileCurrentlyRecording(GetCurrentMediaFile())";
			else if (subfunc.equals("duration")) return getTimeFormat("GetAiringDuration(GetCurrentMediaFile())", null);
			else if (subfunc.equals("time")) return getTimeFormat("(GetMediaTime() - GetAiringStartTime(GetCurrentMediaFile()))", null);
			else if (subfunc.equals("progress")) return "((GetMediaTime() - GetAiringStartTime(GetCurrentMediaFile()))*1.0/GetAiringDuration(GetCurrendMediaFile()))";
			else if (subfunc.equals("actstreamclient")) return "GetUIContextName()";
			else if (subfunc.equals("actstreamdevice")) return "GetCaptureDeviceInputBeingViewed()";
			else if (subfunc.equals("actstreamstatus")) return "\"" + stringMap.get("13205") + "\""; /*UNIMPLEMENTED*/
			else if (subfunc.equals("actstreamsignal")) return "(GetSignalStrength(GetCaptureDeviceInputBeingViewed()) + \" %\")";
			else if (subfunc.equals("actstreamsnr") || subfunc.equals("actstreamber") || subfunc.equals("actstreamunc")) return "\"??\""; /*UNIMPLEMENTED*/
			else if (subfunc.equals("actstreamvideobitrate")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Video.Bitrate\")";
			else if (subfunc.equals("actstreamaudiobitrate")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Audio.Bitrate\")";
			else if (subfunc.equals("actstreamdolbybitrate")) return "If(GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Audio.Codec\") == \"AC3\", GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Audio.Bitrate\"), \"\")";
			else if (subfunc.equals("actstreamprogrsignal")) return "GetSignalStrength(GetCaptureDeviceInputBeingViewed())";
			else if (subfunc.equals("actstreamprogrsnr"))  return "\"??\""; /*UNIMPLEMENTED*/
			else if (subfunc.equals("actstreamisencrypted")) return "false"; /*UNIMPLEMENTED*/
			else if (subfunc.equals("actstreamencryptionname")) return "\"\""; /*UNIMPLEMENTED*/
			else if (subfunc.equals("timeshiftduration")) return "GetMediaDuration()";
			else if (subfunc.equals("timeshifttime")) return getTimeFormat("(GetMediaTime() - GetFileStartTime(GetCurrentMediaFile()))", null);
			else if (subfunc.equals("timeshiftprogress")) return "(GetMediaTime() - GetFileStartTime(GetCurrentMediaFile()))/GetMediaDuration()";
		}
		else if (exprPrefix.equals("addon"))
		{
			if (subfunc.equals("rating")) return "\"??\""; /*UNIMPLEMENTED*/
		}
		else if (exprPrefix.equals("bar"))
		{
			// These are all supposed to be used for progress displays...that's what bar means!
			if (subfunc.equals("gputemperature")) return "\"System.GPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("cputemperature")) return "\"System.CPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("cpuusage")) return "\"System.GPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("freememory")) return "(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()))";
			else if (subfunc.equals("usedmemory")) return "(1.0 - (java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime())))";
			else if (subfunc.equals("fanspeed")) return "\"System.FanSpeed\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("freespace")) return "(GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\")))";
			else if (subfunc.equals("usedspace")) return "(1.0 - (GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))))";
			else if (subfunc.startsWith("usedspace(")) return "(1.0 - (GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\")*1.0/GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\")))";
			else if (subfunc.startsWith("freespace(")) return "(GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\")*1.0/GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\"))";
			else if (subfunc.equals("hddtemperature")) return "\"System.HDDTemperature\"";
		}
		else if (exprPrefix.equals("system"))
		{
			if (subfunc.equals("date")) return "PrintDateFull(Time())";
			else if (subfunc.startsWith("date("))
			{
				if (lcExpr.indexOf(',') != -1)
					return "((java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"" + getArg(getParen(lcExpr), 0) +
						"\") >= 0) && (java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"" + getArg(getParen(lcExpr), 1) + "\") < 0))";
				else
					return "(java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"" + getParen(lcExpr) + "\") >= 0)";
			}
			else if (subfunc.startsWith("time("))
			{
				if (lcExpr.indexOf(',') != -1)
					return "((java_lang_String_compareTo(DateFormat(\"HH:mm\", Time()), \"" + getArg(getParen(lcExpr), 0) +
						"\") >= 0) && (java_lang_String_compareTo(DateFormat(\"HH:mm\", Time()), \"" + getArg(getParen(lcExpr), 1) + "\") < 0))";
				else
					return "DateFormat(\"" + getParen(lcExpr).replace("xx", "aa") + "\", Time())";
			}
			else if (subfunc.equals("time")) return "PrintTimeShort(Time())"; // Not in XBMC API; but Aeon uses it
			else if (subfunc.equals("cputemperature")) return "\"System.CPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("cpuusage")) return "\"System.GPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("gputemperature")) return "\"System.GPUTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("fanspeed")) return "\"System.FanSpeed\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("freespace")) return "(GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))/1048576 + \" MB \" + \"" + stringMap.get("160") + "\")";
			else if (subfunc.equals("usedspace")) return "((GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\")) - GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\")))/1048576 + \" MB \" + \"" + stringMap.get("20162") + "\")";
			else if (subfunc.equals("totalspace")) return "(GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))/1048576 + \" MB \" + \"" + stringMap.get("20161") + "\")";
			else if (subfunc.equals("usedspacepercent")) return "(100 - Round((GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\")))*100))";
			else if (subfunc.equals("freespacepercent")) return "Round((GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\\\", \"/\")))*100)";
			else if (subfunc.startsWith("freespace(")) return "(\"" + getParen(lcExpr).toUpperCase() + ": \" + GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\")/1048576 + \" MB \" + \"" + stringMap.get("160") + "\")";
			else if (subfunc.startsWith("usedspace(")) return "(\"" + getParen(lcExpr).toUpperCase() + ": \" + (GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\") - GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\"))/1048576 + \" MB \" + \"" + stringMap.get("20162") + "\")";
			else if (subfunc.startsWith("totalspace(")) return "(\"" + getParen(lcExpr).toUpperCase() + ": \" + GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\")/1048576 + \" MB \" + \"" + stringMap.get("20161") + "\")";
			else if (subfunc.startsWith("usedspacepercent(")) return "(100 - Round((GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\")*1.0/GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\"))*100))";
			else if (subfunc.startsWith("freespacepercent(")) return "Round((GetDiskFreeSpace(\"" + getParen(lcExpr) + ":\\\\\")*1.0/GetDiskTotalSpace(\"" + getParen(lcExpr) + ":\\\\\"))*100)";
			else if (subfunc.equals("buildversion")) return "GetProperty(\"version\", \"\")";
			else if (subfunc.equals("builddate")) return "PrintDateFull(java_io_File_lastModified(new_java_io_File(\"Sage.jar\")))";
			else if (subfunc.equals("hasnetwork")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("fps")) return "GetProperty(\"fps\", 0)";
			else if (subfunc.equals("hasmediadvd")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdready")) return "\"System.dvdready\"";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("trayopen")) return "\"System.trayopen\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdtraystate")) return "\"System.dvdtraystate\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("freememory") || subfunc.equals("memory(free)")) return "(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())/1048576 + \" MB\")";
			else if (subfunc.equals("memory(free.percent)")) return "Round(100*(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime())))";
			else if (subfunc.equals("usedmemory") || subfunc.equals("memory(used)")) return "((java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()) - java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime()))/1048576 + \" MB\")";
			else if (subfunc.equals("memory(used.percent)")) return "(100 - Round(100*(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()))))";
			else if (subfunc.equals("memory(total)")) return "(java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime())/1048576 + \" MB\")";
			else if (subfunc.equals("language")) return "GetUILanguage()";
			else if (subfunc.equals("temperatureunits")) return "(\"\u00b0\" + If(tv_sage_weather_WeatherDotCom_getUnits(tv_sage_weather_WeatherDotCom_getInstance()) == \"s\", \"F\", \"C\"))";
			else if (subfunc.equals("screenmode")) return "GetAnalogVideoFormat()";
			else if (subfunc.equals("screenwidth")) return "GetFullUIWidth()";
			else if (subfunc.equals("screenheight")) return "GetFullUIHeight()";
			else if (subfunc.equals("currentwindow")) return "GetWidgetName(GetCurrentMenuWidget())";
			else if (subfunc.equals("currentcontrol")) return "GetTextForUIComponent(GetUIComponentForVariable(\"Focused\", true))";
			else if (subfunc.equals("xbocnickname")) return "\"System.XBocNickName\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdlabel")) return "\"System.DVDLabel\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("haslocks")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hasloginscreen")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("ismaster")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("internetstate")) return "\"System.InternetState\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("loggedon")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hasdrivef")) return "IsDirectoryPath(\"F:\\\\\")";
			else if (subfunc.equals("hasdriveg")) return "IsDirectoryPath(\"G:\\\\\")";
			else if (subfunc.equals("hddtemperature")) return "\"System.HDDTemperature\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddinfomodel")) return "\"System.HDDInfoModel\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddinfofirmware")) return "\"System.HDDInfoFirmware\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddinfoserial")) return "\"System.HDDInfoSerial\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddinfopw")) return "\"System.HDDInfoPW\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddinfolockstate")) return "\"System.HDDInfoLockState\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddlockkey")) return "\"System.HDDLockKey\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddbootdate")) return "\"System.HDDBootDate\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hddcyclecount")) return "\"System.HDDCycleCount\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdinfomodel")) return "\"System.DVDInfoModel\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdinfofirmware")) return "\"System.DVDInfoFirmware\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("mplayerversion")) return "\"System.mplayerversion\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("kernelversion")) return "(java_lang_System_getProperty(\"os.name\") + \" \" + java_lang_System_getProperty(\"os.version\"))";
			else if (subfunc.equals("uptime")) return "PrintDuration(Time() - GetApplicationLaunchTime())";
			else if (subfunc.equals("totaluptime")) return "PrintDuration((Time() - GetApplicationLaunchTime()) + (GetProperty(\"uptime\", 0)*1))";
			else if (subfunc.equals("cpufrequency")) return "\"System.CPUFrequency\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("xboxversion")) return "\"System.XBoxVersion\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("avpackinfo")) return "\"System.AVPackInfo\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("screenresolution")) return "GetDisplayResolution()";
			else if (subfunc.equals("videoencoderinfo")) return "\"System.VideoEncoderInfo\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("xboxproduceinfo")) return "\"System.XBoxProduceInfo\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("xboxserial")) return "\"System.XBoxSerial\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("xberegion")) return "\"System.XBERegion\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dvdzone")) return "\"System.dvdzone\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("bios")) return "\"System.BIOS\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("modchip")) return "\"System.modchip\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.startsWith("controllerport(")) return "\"System.ControllerPort(" + getParen(lcExpr) + ")\"";
			else if (subfunc.startsWith("idletime(")) return "(GetTimeSinceLastInput() > " + getParen(lcExpr) + ")";
			else if (subfunc.startsWith("hasalarm(")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("alarmpos")) return "\"System.Alarmpos\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.startsWith("alarmlessorequal(")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("profilename")) return"java_lang_System_getProperty(\"user.name\")";
			else if (subfunc.equals("profilethumb")) return "\"System.ProfileThumb\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("launchxbe")) return "\"System.LaunchXBE\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("progressbar")) return "\"System.ProgressBar\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("platform.xbox")) return "false";
			else if (subfunc.equals("platform.linux")) return "IsLinuxOS()";
			else if (subfunc.equals("platform.windows")) return "IsWindowsOS()";
			else if (subfunc.equals("platform.osx")) return "IsMacOS()";
			else if (subfunc.startsWith("hascoreid(")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.startsWith("coreusage(")) return "0"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.startsWith("getbool(") || subfunc.startsWith("setting(")) return "GetProperty(\"xbmc/system/settings/" + getParen(lcExpr) + "\", false)";
			else if (subfunc.equals("canpowerdown")) return "false";
			else if (subfunc.equals("cansuspend")) return "false";
			else if (subfunc.equals("canhibernate")) return "false";
			else if (subfunc.equals("canreboot")) return "false";
		}
		else if (exprPrefix.equals("library"))
		{
			if (subfunc.equals("hascontent(music)")) return "!IsEmpty(GetMediaFiles(\"M\"))";
			else if (subfunc.equals("hascontent(video)") || subfunc.equals("hascontent(videos)") || subfunc.equals("hascontent(movie)") || subfunc.equals("hascontent(movies)"))
				return "!IsEmpty(GetMediaFiles(\"V\"))";
			else if (subfunc.equals("hascontent(tvshows)")) return "!IsEmpty(GetMediaFiles(\"T\"))";
			else if (subfunc.equals("hascontent(musicvideos)")) return "(FindElementIndex(GetAllCategories(\"V\"), LocalizeString(\"Music Videos\")) != -1)";
			else if (subfunc.equals("isscanning")) return "IsDoingLibraryImportScan()";
		}
		else if (lcExpr.startsWith("stringcompare("))
		{
			return "java_lang_String_equalsIgnoreCase(\"\" + " + translateFunction(getArg(getParen(lcExpr), 0), controlContext, imageSource, preferBool, knownWinID, loopVisIDs) +
				", " + getAsStringConstant(getArg(getParen(xbmcExpr), 1), false) + ")";
		}
		else if (lcExpr.startsWith("substring("))
		{
			return "(StringIndexOf(java_lang_String_toLowerCase(\"\" + " + translateFunction(getArg(getParen(lcExpr), 0), controlContext, imageSource, preferBool, knownWinID, loopVisIDs) +
				"), java_lang_String_toLowerCase(" + getAsStringConstant(getArg(getParen(xbmcExpr), 1), false) + ")) != -1)";
		}
		// NOTE: WE'RE MISSING THE WHOLE LCD API!!!!!! But I have no idea what its for yet....
		else if (exprPrefix.equals("network"))
		{
			if (subfunc.equals("ipaddress")) return "GetLocalIPAddress()";
			else if (subfunc.equals("isdhcp")) return "\"Network.IsDHCP\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("linkstate")) return "\"Network.LinkState\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("macaddress")) return "\"Network.MacAddress\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("subnetaddress")) return "\"Network.SubnetAddress\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("gatewayaddress")) return "\"Network.GatewayAddress\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dns1address")) return "\"Network.DNS1Address\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dns2address")) return "\"Network.DNS2Address\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("dhcpaddress")) return "\"Network.DHCPAddress\""; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("musicplayer"))
		{
			if (subfunc.endsWith(".exists"))
			{
				if (subfunc.startsWith("position("))
					return "(GetNumberOfPlaylistItems(GetCurrentPlaylist()) >= " + getParen(subfunc) + ")";
				else if (subfunc.startsWith("offset("))
					return "(GetNumberOfPlaylistItems(GetCurrentPlaylist()) >= (" + getParen(subfunc) + " + GetCurrentPlaylistIndex()))";
			}
			if (subfunc.startsWith("position("))
				return getMusicFunction("GetPlaylistItemAt(GetCurrentPlaylist(), " + getParen(subfunc) + " - 1)", subfunc.substring(subfunc.indexOf('.') + 1));
			else if (subfunc.startsWith("offset("))
				return getMusicFunction("GetPlaylistItemAt(GetCurrentPlaylist(), " + getParen(subfunc) + " + GetCurrentPlaylistIndex())", subfunc.substring(subfunc.indexOf('.') + 1));
			else if (subfunc.startsWith("timeremaining"))
				return getTimeFormat("GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))", subfunc.substring(13));
			else if (subfunc.startsWith("timespeed"))
				return "(" + getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", subfunc.substring(9)) + "\" (\"GetPlaybackRate() + \"x)\")";
			else if (subfunc.startsWith("time"))
				return getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", subfunc.substring(4));
			else if (subfunc.startsWith("duration"))
				return getTimeFormat("GetMediaDuration()", subfunc.substring(8));
			else
				return getMusicFunction("GetCurrentMediaFile()", subfunc);
		}
		else if (exprPrefix.equals("videoplayer"))
		{
			if (subfunc.equals("title")) return "If(IsTVFile(GetCurrentMediaFile()) && !IsEmpty(GetShowEpisode(GetCurrentMediaFile())), GetShowEpisode(GetCurrentMediaFile()), GetMediaTitle(GetCurrentMediaFile()))";
			else if (subfunc.equals("genre")) return "GetShowCategory(GetCurrentMediaFile())";
			else if (subfunc.equals("originaltitle")) return "GetMediaTitle(GetCurrentMediaFile())";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("director")) return "GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Director\"))";
			else if (subfunc.equals("year")) return "GetShowYear(GetCurrentMediaFile())";
			else if (subfunc.equals("timeremaining"))
				return getTimeFormat("GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))", lcExpr.substring(25));
			else if (subfunc.equals("timespeed"))
				return "(" + getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", lcExpr.substring(21)) + "\" (\"GetPlaybackRate() + \"x)\")";
			else if (subfunc.equals("time"))
				return getTimeFormat("GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))", lcExpr.substring(16));
			else if (subfunc.equals("duration"))
				return getTimeFormat("GetMediaDuration()", lcExpr.substring(20));
			else if (subfunc.equals("cover")) return "GetThumbnail(GetCurrentMediaFile())";
			else if (subfunc.equals("usingoverlays")) return "IsVideoRendererOverlay()";
			else if (subfunc.equals("isfullscreen")) return "FullScreenVideo"; // menu attribute
			else if (subfunc.equals("hasmenu")) return "IsShowingDVDMenu()";
			else if (subfunc.equals("playlistlength")) return "GetNumberOfPlaylistItems(GetCurrentPlaylist())";
			else if (subfunc.equals("playlistposition")) return "(GetCurrentPlaylistIndex() + 1)";
			else if (subfunc.equals("plot")) return "GetShowDescription(GetCurrentMediaFile())";
			else if (subfunc.equals("plotoutline")) return "GetShowDescription(GetCurrentMediaFile())"; // NOT PROPERLY IMPLEMENTED
			else if (subfunc.equals("episode")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"EpisodeNumber\")";
			else if (subfunc.equals("season")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"SeasonNumber\")";
			else if (subfunc.equals("rating")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Rating\")";
			else if (subfunc.equals("ratingandvotes")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"IMDBRatingAndVotes\")";
			else if (subfunc.equals("tvshowtitle")) return "GetMediaTitle(GetCurrentMediaFile())";
			else if (subfunc.equals("premiered")) return "GetSeriesPremiereDate(GetCurrentMediaFile())";
			else if (subfunc.startsWith("content"))
			{
				// NOTE: videoplayer.content doesn't deal with 'musicvideos' yet
				String argString = "\"" + getParen(lcExpr) + "\"";
				return "If(java_lang_String_equalsIgnoreCase(\"livetv\", " + argString + "), IsFileCurrentlyRecording(GetCurrentMediaFile()), If(java_lang_String_equalsIgnoreCase(\"files\", " + argString + "), true, If(java_lang_String_equalsIgnoreCase(\"movies\", " + argString + "), LocalizeString(\"Movie\") == GetShowCategory(GetCurrentMediaFile()), " +
					"If(java_lang_String_equalsIgnoreCase(\"episodes\", " + argString + "), StringStartsWith(GetShowExternalID(GetCurrentMediaFile()), \"EP\"), false))))";
			}
			else if (subfunc.equals("studio")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Studio\")";
			else if (subfunc.equals("mpaa")) return "GetShowRated(GetCurrentMediaFile())";
			else if (subfunc.equals("top250")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Top250\")";
			else if (subfunc.equals("cast")) return "GetPeopleInShow(GetCurrentMediaFile())";
			// NOTE: CASTANDROLE is missing the roles!!!!!!!!!!
			else if (subfunc.equals("castandrole")) return "GetPeopleInShow(GetCurrentMediaFile())";
			else if (subfunc.equals("artist")) return "GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Artist\"))";
			else if (subfunc.equals("album")) return "GetAlbumName(GetCurrentMediaFile())";
			else if (subfunc.equals("writer")) return "GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Writer\"))";
			else if (subfunc.equals("tagline")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Tagline\")";
			else if (subfunc.equals("hasinfo")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("trailer")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Trailer\")"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("videocodec")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Video.Codec\")";
			else if (subfunc.equals("videoresolution")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Video.Resolution\")";
			else if (subfunc.equals("videoaspect")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Video.Aspect\")";
			else if (subfunc.equals("audiocodec")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Audio.Codec\")";
			else if (subfunc.equals("audiochannels")) return "GetMediaFileMetadata(GetCurrentMediaFile(), \"Format.Audio.Channels\")";
			else if (subfunc.equals("hasteletext")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("starttime")) return "PrintTimeShort(GetAiringStartTime(GetCurrentMediaFile()))";
			else if (subfunc.equals("endtime")) return "PrintTimeShort(GetAiringEndTime(GetCurrentMediaFile()))";
			else if (subfunc.equals("starttime")) return "PrintTimeShort(GetAiringStartTime(GetCurrentMediaFile()))";
			else if (subfunc.equals("nexttitle")) return "GetAiringTitle(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("nextgenre")) return "GetShowCategory(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("nextplot") || subfunc.equals("nextplotoutline")) return "GetShowDescription(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("nextstarttime")) return "GetScheduleStartTime(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("nextendtime")) return "GetScheduleEndTime(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("nextduration")) return "GetScheduleDuration(GetElement(GetScheduledRecordingsForDeviceForTime(GetCaptureDeviceForInput(GetCaptureDeviceInputCurrentlyBeingViewed()), GetScheduleEndTime(GetCurrentMediaFile()), Time()*2), 0))";
			else if (subfunc.equals("channelname")) return "GetChannelName(GetCurrentMediaFile())";
			else if (subfunc.equals("channelnumber")) return "GetChannelNumber(GetCurrentMediaFile())";
			else if (subfunc.equals("channelgroup")) return "GetChannelNetwork(GetCurrentMediaFile())";
		}
		else if (exprPrefix.equals("playlist"))
		{
			if (subfunc.equals("length")) return "GetNumberOfPlaylistItems(GetCurrentPlaylist())";
			else if (subfunc.equals("position")) return "(GetCurrentPlaylistIndex() + 1)";
			else if (subfunc.equals("random")) return "If(((IsCurrentMediaFileMusic() && GetProperty(\"random_music_playback\", false)) || " +
				"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"random_video_playback\", false))), \"" + stringMap.get("590") + "\", \"" + stringMap.get("591") + "\")";
			else if (subfunc.equals("repeat")) return "If(((IsCurrentMediaFileMusic() && GetProperty(\"music/repeat_playback\", false)) || " +
				"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"video_lib/repeat_playback\", false))), \"" + stringMap.get("593") + "\", \"" + stringMap.get("594") + "\")";
			else if (subfunc.equals("israndom")) return "((IsCurrentMediaFileMusic() && GetProperty(\"random_music_playback\", false)) || " +
				"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"random_video_playback\", false)))";
			else if (subfunc.equals("isrepeat")) return "((IsCurrentMediaFileMusic() && GetProperty(\"music/repeat_playback\", false)) || " +
				"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"video_lib/repeat_playback\", false)))";
			else if (subfunc.equals("isrepeatone")) return "false"; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("musicpartymode"))
		{
			if (subfunc.equals("enabled")) return "false";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("songsplayed")) return "\"MusicPartyMode.SongsPlayed\"";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("matchingsongs")) return "\"MusicPartyMode.MatchingSongs\"";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("matchingsongspicked")) return "\"MusicPartyMode.MatchingSongsPicked\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("matchingsongsleft")) return "\"MusicPartyMode.MatchingSongsLeft\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("relaxedsongspicked")) return "\"MusicPartyMode.RelaxedSongsPicked\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("randomsongspicked")) return "\"MusicPartyMode.RandomSongsPicked\""; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("audioscrobbler"))
		{
			if (subfunc.equals("enabled")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("connectstate")) return "\"AudioScrobbler.ConnectState\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("submitinterval")) return "\"AudioScrobbler.SubmitInterval\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("filescached")) return "\"AudioScrobbler.FilesCached\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("submitstate")) return "\"AudioScrobbler.SubmitState\""; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("lastfm"))
		{
			if (subfunc.equals("radioplaying")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("canlove")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("canban")) return "false"; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("slideshow"))
			return "\"" + xbmcExpr + "\"";  /*UNIMPLEMENTED!!!!!*/ // Returns attribute of the current picture in the slideshow I think...
		else if (exprPrefix.startsWith("container"))
		{
			String containerID = getParen(exprPrefix);
			if (containerID.length() == 0)
				containerID = "GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID)";
			else
				containerID = "GetUIComponentForVariable(\"ContainerXBMCID\", " + containerID + ")";
			int dot2Idx = subfunc.indexOf('.');
			if (subfunc.startsWith("listitemnowrap"))
			{
				String itemOffset = getParen(subfunc.substring(0, dot2Idx));
				if (itemOffset.length() == 0)
					itemOffset = "0";
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableFocusedOffset(" + containerID + ", " + itemOffset + ", false)", subfunc.substring(dot2Idx + 1)) + ")";
			}
			else if (subfunc.startsWith("listitemposition"))
			{
				String itemOffset = getParen(subfunc.substring(0, dot2Idx));
				if (itemOffset.length() == 0)
					itemOffset = "0";
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableVisiblePosition(" + containerID + ", " + itemOffset + ", false)", subfunc.substring(dot2Idx + 1)) + ")";
			}
			else if (subfunc.startsWith("listitem"))
			{
				String itemOffset = getParen(subfunc.substring(0, dot2Idx));
				if (itemOffset.length() == 0)
					itemOffset = "0";
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableFocusedOffset(" + containerID + ", " + itemOffset + ", true)", subfunc.substring(dot2Idx + 1)) + ")";
			}
			else if (subfunc.equals("hasfiles")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hasfolders")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("isstacked")) return "true"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("folderthumb")) return (String)defaultImageMap.get("folderback"); /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("tvshowthumb")) return "\"Container.TVShowThumb\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("seasonthumb")) return "\"Container.SeasonThumb\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("folderpath")) return "GetNodeFullPath(CurrNode)";
			else if (subfunc.equals("foldername")) return "GetNodePrimaryLabel(CurrNode)";
			else if (subfunc.equals("pluginname")) return "\"\"";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("viewmode")) return "ContainerViewType";
			else if (subfunc.equals("onnext")) return "IsTableTransitionToNext(" + containerID + ")";
			else if (subfunc.equals("onprevious")) return "IsTableTransitionToPrevious(" + containerID + ")";
			else if (subfunc.equals("totaltime")) return "0";   /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("scrolling")) return "false";   /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("hasnext")) return "!GetVariableFromContext(\"ContainerXBMCID\", " + containerID + ", \"IsLastPage\")";
			else if (subfunc.equals("hasprevious")) return "!GetVariableFromContext(\"ContainerXBMCID\", " + containerID + ", \"IsFirstPage\")";
			else if (subfunc.startsWith("content("))
			{
				String containerType = getParen(subfunc);
				// XBMC wants 'seasons' to be seasons of a TV series (they don't have a TV Series container)
				// 'episodes' are then items within a season
				// If we're not doing a hiearachy of seasons->episodes, then the files are 'tvshows'
				// WRONG - TVShows are the titles
				if ("files".equals(containerType))
					return "(\"files\" == \"\" + ContainerContent)";
				else if ("musicvideos".equals(containerType))
					return "((\"musicvideos\" == \"\" + ContainerContent) && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))";
				else if ("songs".equals(containerType))
					return "((ContainerContent == \"music\") && (\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))))";
				else if ("artists".equals(containerType))
					return "(\"Artist\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("albums".equals(containerType))
					return "(\"Album\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("genres".equals(containerType))
					return "(\"Category\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("years".equals(containerType))
					return "(\"Year\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("actors".equals(containerType))
					return "(\"Actor\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("directors".equals(containerType))
					return "(\"Director\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("tvshows".equals(containerType))
					return "((\"\" + ContainerContent == \"tvshows\") && ((((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))) " +
						" && (GetShowSeriesInfo(GetNodeDataObject(GetNodeChildAt(CurrNode, 0))) == null)) || (\"SeriesInfo\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))";
//					return "((\"\" + ContainerContent == \"tvshows\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))";
				else if ("movies".equals(containerType))
					return "((\"\" + ContainerContent == \"movies\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"Title\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))";
				else if ("episodes".equals(containerType) || "episode".equals(containerType))
//					return "((\"\" + ContainerContent == \"tvshows\") && (\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) && (GetShowSeriesInfo(GetNodeDataObject(GetNodeChildAt(CurrNode, 0))) != null))";
//					return "(\"Episode\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
					return "((\"\" + ContainerContent == \"tvshows\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))) && " +
						"(GetShowSeriesInfo(GetNodeDataObject(GetNodeChildAt(CurrNode, 0))) != null))";
				else if ("playlists".equals(containerType))
					return "(\"Playlist\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("seasons".equals(containerType))
					return "(\"Season\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("studios".equals(containerType))
					return "(\"Studio\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else if ("scripts".equals(containerType))
					return "(\"Script\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
				else // turn these into functions so we spot unknown ones!
					return "(" + containerType + "() == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))";
			}
			else if (subfunc.startsWith("row(")) return "(GetTableFocusedVisibleRow(If(false, \"Focused\", " + containerID + ")) == (1 + " + getParen(subfunc) + "))";
			else if (subfunc.startsWith("column(")) return "(GetTableFocusedVisibleColumn(If(false, \"Focused\", " + containerID + ")) == (1 + " + getParen(subfunc) + "))";
			else if (subfunc.startsWith("position(")) return "(GetTableFocusedVisiblePosition(If(false, \"Focused\", " + containerID + ")) == (1 + " + getParen(subfunc) + "))";
			else if (subfunc.startsWith("subitem(")) return "(GetTableFocusedPosition(If(false, \"Focused\", " + containerID + ")) == (1 + " + getParen(subfunc) + "))";
			else if (subfunc.equals("hasthumb")) return "true";  /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("numpages")) return "GetVariableFromUIComponent(" + containerID + ", \"NumPages\")";
			// We subtract one here to remove the 'up directory' selector
			else if (subfunc.equals("numitems")) return "(GetNodeNumChildren(CurrNode) - If(GetNodePrimaryLabel(GetNodeChildAt(CurrNode, 0)) == \"..\", 1, 0))";
			else if (subfunc.equals("currentpage")) return "(1 + Max((Max(1, GetVariableFromUIComponent(" + containerID + ", \"VScrollIndex\")) - 1)/Max(1,GetVariableFromUIComponent(" + containerID + ", \"NumRowsPerPage\")), " +
				"(Max(1, GetVariableFromUIComponent(" + containerID + ", \"HScrollIndex\")) - 1)/Max(1,GetVariableFromUIComponent(" + containerID + ", \"NumColsPerPage\"))))";
			else if (subfunc.equals("sortmethod")) return "GetNodeSortTechnique(CurrNode)";
			else if (subfunc.startsWith("sortdirection(")) return "((IsNodeSortAscending(CurrNode) == (\"" + getParen(subfunc) + "\" == \"ascending\")) && (\"none\" != \"" + getParen(subfunc) + "\"))";
			else if (subfunc.startsWith("sort(")) return "(GetNodeSortTechnique(CurrNode) == \"" + getSortMethod(getParen(subfunc)) + "\")";
			else if (subfunc.startsWith("hasfocus(") && containerID.indexOf("Active") == -1)
				return "If(false, \"Focused\", GetVariableFromUIComponent(GetUIComponentLastFocusedChild(" + containerID + "), \"ItemXBMCID\") == " + getParen(subfunc) + ")";
			else if (subfunc.startsWith("property(")) return "\"Container.Property(" + getParen(subfunc) + ")\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("showplot")) return "GetSeriesDescription(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))";
		}
		else if (exprPrefix.startsWith("listitem"))
		{
			String itemOffset = getParen(exprPrefix);
			if (itemOffset.length() == 0)
				return getListItemFunction("If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))", lcExpr.substring(dotIdx + 1));
			else
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), " + itemOffset + ", true)", lcExpr.substring(dotIdx + 1)) + ")";
		}
		// NOTE: XBMC ALSO HAS listitemnowrap and listitemposition BUT THEY CHECK LISTITEM FIRST BASED ON THE PREFIX SO THE OTHER 2 ARE NEVER HIT!!!
		else if (exprPrefix.startsWith("listitemposition"))
		{
			String itemOffset = getParen(exprPrefix);
			if (itemOffset.length() == 0)
				return getListItemFunction("If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))", lcExpr.substring(dotIdx + 1));
			else
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableVisiblePosition(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), " + itemOffset + ", false)", lcExpr.substring(dotIdx + 1)) + ")";
		}
		else if (exprPrefix.startsWith("listitemnowrap"))
		{
			String itemOffset = getParen(exprPrefix);
			if (itemOffset.length() == 0)
				return getListItemFunction("If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))", lcExpr.substring(dotIdx + 1));
			else
				return "If(false, \"Focused\", " + getListItemFunction("GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), " + itemOffset + ", false)", lcExpr.substring(dotIdx + 1)) + ")";
		}
		else if (exprPrefix.equals("visualisation"))
		{
			if (subfunc.equals("locked")) return "false"; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("preset")) return "\"Visualisation.Preset\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("name")) return "\"Visualisation.Name\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("enabled")) return "true"; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("fanart"))
		{
			if (subfunc.equals("color1")) return "\"Fanart.Color1\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("color2")) return "\"Fanart.Color2\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("color3")) return "\"Fanart.Color3\""; /*UNIMPLEMENTED!!!!!*/
			else if (subfunc.equals("image")) return "GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"FanArtImage\")"; /*UNIMPLEMENTED!!!!!*/
		}
		else if (exprPrefix.equals("skin"))
		{
			if (subfunc.equals("currenttheme")) return "GetProperty(\"xbmc/skins/" + skinName + "/theme\", \"default\")";
			else if (subfunc.equals("currentcolourtheme")) return "GetProperty(\"xbmc/skins/" + skinName + "/colortheme\", \"default\")";
			else if (subfunc.startsWith("string("))
			{
				int commaIdx = lcExpr.indexOf(',');
				if (commaIdx >= 0)
				{
					return "(GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getArg(getParen(xbmcExpr), 0), false) + ", \"\") == \"" + getArg(getParen(xbmcExpr), 1) + "\")";
				}
				else
				{
					if (preferBool && !imageSource)
						return "(Size(GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getParen(xbmcExpr), false) + ", \"\")) > 0)";
					else
						return "GetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + getAsStringConstant(getParen(xbmcExpr), false) + ", \"\")";
				}
			}
			else if (subfunc.startsWith("hassetting(")) return "GetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + getAsStringConstant(getParen(xbmcExpr), false) + ", false)";
			else if (subfunc.startsWith("hastheme(")) return "(GetProperty(\"xbmc/skins/" + skinName + "/theme\", \"default\") == \"" + getParen(xbmcExpr) + "\")";
		}
		else if (exprPrefix.startsWith("window"))
		{
			if (subfunc.startsWith("xml)."))
				subfunc = subfunc.substring(5);
			if (subfunc.startsWith("property("))
			{
				String winStr = lcExpr.substring(7, lcExpr.indexOf(')'));
				if (winStr.equals("weather") || (!lcExpr.startsWith("window(") && knownWinID == 2600))
				{
					String prop = getParen(subfunc);
					if (prop.equals("location")) return "tv_sage_weather_WeatherDotCom_getLocationInfo(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_location\")";
					else if (prop.equals("updated")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_updated\")";
					else if (prop.equals("current.condition")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_conditions\")";
					else if (prop.equals("current.temperature")) return "SubstringBegin(tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_temp\"), 2)";
					else if (prop.equals("current.feelslike")) return "SubstringBegin(tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_windchill\"), 2)";
					else if (prop.equals("current.uvindex")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_uv_index\")";
					else if (prop.equals("current.wind")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_wind\")";
					else if (prop.equals("current.dewpoint")) return "SubstringBegin(tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_dewpoint\"), 2)";
					else if (prop.equals("current.humidity")) return "tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_humidity\")";
					else if (prop.startsWith("day") && prop.endsWith(".title")) return "Substring(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"date" + prop.charAt(3) + "\"), 0, " +
						"StringIndexOf(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"date" + prop.charAt(3) + "\"), \" \"))";
					else if (prop.startsWith("day") && prop.endsWith(".hightemp")) return "SubstringBegin(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"hi" + prop.charAt(3) + "\"), 2)";
					else if (prop.startsWith("day") && prop.endsWith(".lowtemp")) return "SubstringBegin(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"low" + prop.charAt(3) + "\"), 2)";
					else if (prop.startsWith("day") && prop.endsWith(".outlook")) return "tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"conditionsd" + prop.charAt(3) + "\")";
					else if (prop.startsWith("day") && prop.endsWith(".outlookicon")) return "(\"WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"icond" + prop.charAt(3) + "\") + \".png\")";
					else if (prop.equals("current.conditionicon")) return "(\"WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_icon\") + \".png\")";
				}
				else
				{
					String windowID = "0";
					if (lcExpr.startsWith("window("))
						windowID = resolveWindowID(winStr);
					return "GetWindowProperty(" + windowID + ", \"" + getParen(subfunc) + "\")";
				}
			}
			else if (subfunc.startsWith("isactive(") || subfunc.startsWith("isvisible("))
			{
				// NOTE: IsActive should be treated different than IsVisible!!!!!
				if (knownWinID >= 0 && (("" + knownWinID).equals(resolveWindowID(getParen(subfunc)))))
					return "true";
				return "GetVisibilityForVariable(\"MenuXBMCID\", " + resolveWindowID(getParen(subfunc)) + ")";
			}
			else if (subfunc.startsWith("ismedia")) return "IsMediaWindow"; // menu attribute
			else if (subfunc.startsWith("istopmost("))
			{
				/*UNIMPLEMENTED!!!!!*/
//				if (knownWinID >= 0)
//					return ("" + knownWinID).equals(resolveWindowID(getParen(subfunc))) ? "true" : "false";
//				else
					return "(MenuXBMCID == " + resolveWindowID(getParen(subfunc)) + ")";
			}
			else if (subfunc.startsWith("previous(")) return "IsTransitioningFromMenu(\"" + resolveMenuName(getParen(subfunc)) + "\")";
			else if (subfunc.startsWith("next(")) return "IsTransitioningToMenu(\"" + resolveMenuName(getParen(subfunc)) + "\")";
		}
		else if (lcExpr.startsWith("control.hasfocus(")) return "(GetVariableFromContext(\"Focused\", true, \"XBMCID\") == " + getParen(lcExpr) + ")";
		else if (lcExpr.startsWith("control.isvisible(") && controlContext != null)
		{
boolean debugControlVisibility = false;
// NOTE: WE CAN DO LOTS MORE OPTIMIZATION HERE SINCE WE'LL END UP WITH SOME TRUE/FALSE CONSTANTS IN THE MIX
			int targetID = parseInt(getParen(lcExpr));
			java.util.Vector condControls = controlContext.win.getControlsWithID(targetID);
			boolean cyclicIgnore = loopVisIDs != null && !loopVisIDs.add(new Integer(targetID));
			boolean emptyConds = true;
			// If there's containers in this list then only use them to avoid extraneous controls (happens in TV views)
			boolean hasContainers = false;
			for (int i = 0; condControls != null && i < condControls.size(); i++)
			{
				Control currCont = (Control) condControls.get(i);
				if (currCont.visible != null && !currCont.visible.isEmpty())
				{
					emptyConds = false;
					if (currCont.viewtype != null)
						hasContainers = true;
				}
			}
			if (hasContainers)
			{
				for (int i = 0; i < condControls.size(); i++)
				{
					Control currCont = (Control) condControls.get(i);
					if (currCont.viewtype == null)
						condControls.remove(i--);
				}
			}
			if (condControls == null || condControls.isEmpty())
				return (debugControlVisibility ? ("(false && ControlVisibility" + targetID + ")") : "false");
			else if (cyclicIgnore || emptyConds || controlContext.id == targetID)
				return (debugControlVisibility ? ("(true || ControlVisibility" + targetID + ")") : "true");
			else
			{
				String replaceStr = "";
				for (int j = 0; j < condControls.size(); j++)
				{
					String innerReplaceStr = "";
					Control currControl = (Control) condControls.get(j);
					if (currControl.visible == null || currControl.visible.isEmpty())
						continue;
					for (int i = 0; i < currControl.visible.size(); i++)
					{
						java.util.Set currLoop = loopVisIDs;
						if (currLoop == null)
						{
							currLoop = new java.util.HashSet();
							currLoop.add(new Integer(targetID));
						}
						String innerCond = translateBooleanExpression(currControl.visible.get(i).toString(), controlContext, knownWinID, currLoop);
						if ("false".equals(innerCond))
						{
							innerReplaceStr = "false";
							break;
						}
						else if ("true".equals(innerCond))
							continue;
						if (innerReplaceStr.length() > 0)
							innerReplaceStr += " && ";
						innerReplaceStr += "(" + innerCond + ")";
					}
					if (innerReplaceStr.length() > 0)
					{
						if ("true".equals(innerReplaceStr))
						{
							return "true";
						}
						else if  (!"false".equals(innerReplaceStr))
						{
							if (replaceStr.length() != 0)
								replaceStr += " || ";
							replaceStr += "(" + innerReplaceStr + ")";
						}
					}
				}
				if (replaceStr.length() == 0)
					return "true";
				replaceStr = "(" + replaceStr + ")";
				return replaceStr;
			}
		}
		else if (lcExpr.startsWith("control.isenabled(")) return xbmcExpr; /*UNIMPLEMENTED!!!!!*/
		else if (lcExpr.startsWith("control.getlabel("))
		{
			String compID = getParen(lcExpr);
			if ("2".equals(compID) && controlContext != null && controlContext.win != null && "settingscategory".equalsIgnoreCase(controlContext.win.menuName))
				return "If(false, \"Focused\", Substring(CurrSetupArea, 1, -1))";
			return "GetTextForUIComponent(GetUIComponentForVariable(\"XBMCID\", " + compID + "))";
		}
		else if (lcExpr.startsWith("controlgroup("))
		{
			String groupID = getParen(lcExpr);
			String controlID = getParen(lcExpr, 2);
			if (controlID.length() == 0 || controlID.equals("0"))
				return "GetVariableFromUIComponent(GetUIComponentForVariable(\"ControlGroupXBMCID\", " + groupID + "), \"FocusedChild\")";
			else
				return "If(false, \"Focused\", GetVariableFromUIComponent(GetUIComponentLastFocusedChild(GetUIComponentForVariable(\"ControlGroupXBMCID\", " + groupID + ")), \"XBMCID\") == " + controlID + ")";
		}
		else if (lcExpr.startsWith("buttonscroller.hasfocus(")) return "true"; /*UNIMPLEMENTED!!!!!*/

//		System.out.println("ERROR UNABLE TO TRANSLATE XMBC FUNCTION: " + xbmcExpr);
		return xbmcExpr;
	}

	private java.util.HashMap exprBoolCache = new java.util.HashMap();
	private java.util.HashMap exprStringCache = new java.util.HashMap();
	private java.util.HashMap exprImageCache = new java.util.HashMap();
	private java.util.HashMap exprDump = new java.util.HashMap();
/*	private String translateExpression(String xbmcExpr, Control controlContext)
	{
		return translateExpression(xbmcExpr, controlContext, false);
	}
	private String translateExpression(String xbmcExpr, Control controlContext, boolean imageSource)
	{
		return translateExpression(xbmcExpr, controlContext, imageSource, -1);
	}
	private String translateExpression(String xbmcExpr, Control controlContext, boolean imageSource, boolean preferBool)
	{
		return translateExpression(xbmcExpr, controlContext, imageSource, preferBool, -1);
	}
	private String translateExpression(String xbmcExpr, Control controlContext, boolean imageSource, int knownWinID)
	{
		return translateExpression(xbmcExpr, controlContext, imageSource, true, knownWinID);
	}
	private String translateExpression(String xbmcExpr, Control controlContext, boolean imageSource, boolean preferBool, int knownWinID)
	{
		return translateExpression(xbmcExpr, controlContext, imageSource, preferBool, knownWinID, null);
	}
	private String translateExpression(String xbmcExpr, Control controlContext, boolean imageSource, boolean preferBool, int knownWinID, java.util.Set loopVisIDs)
	{
		if (xbmcExpr == null) return null;
//		if (knownWinID < 0 && controlContext != null && controlContext.win.menuWidget != null && controlContext.win.menuWidget.type() == MENU)
//			knownWinID = controlContext.win.id;
		String orgLcExpr = xbmcExpr.toLowerCase();
		if (exprCache.containsKey(orgLcExpr))
			return (String) exprCache.get(orgLcExpr);

		boolean dontCache = false;
		// MISSING API CALLS!!!!
		// Control.isEnabled(id)

		// Need to do this first so we don't screw up any mathematical or conditional expressions we generate
		xbmcExpr = xbmcExpr.replace("+", "&&");
//		xbmcExpr = xbmcExpr.replaceAll("([^|])|([^|])", "$1||$2");
		xbmcExpr = xbmcExpr.replace("|", "||");

		// Fix our expressions since we can't use + for string concatentation since it would get replaced with '&&'
		xbmcExpr = xbmcExpr.replace(" @ ", " + ");

		// We don't need the $INFO blocks inside our expression evaluator; this is some weird thing they do sometimes in XBMC
//		xbmcExpr = xbmcExpr.replace("$INFO", "");

		// We have to do this one first since it matches calls in our API
		// NOTE: BE CAREFUL IF WE USE THE SetProperty API CALL IN ANYTHING THAT WILL END UP BEING TRANSLATED!!!!
// NOTE: WE DON'T WANT THIS TO BE REPLACED WITH SOMETHING THAT WORKS YET SINCE WE NEED TO SEE HOW ITS USED FIRST
		xbmcExpr = java.util.regex.Pattern.compile("SetProperty\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("XBMCSetProperty($1)");

		// Do all of the cached ones
		for (int i = 0; i < XBMC_REGEX_REPLACERS.length; i++)
		{
			xbmcExpr = ((java.util.regex.Pattern) XBMC_REGEX_REPLACERS[i][0]).matcher(xbmcExpr).replaceAll(XBMC_REGEX_REPLACERS[i][1].toString());
		}

		String testExpr = xbmcExpr;
		if (imageSource)
			xbmcExpr = java.util.regex.Pattern.compile("Weather\\.Conditions", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(\"media/WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_icon\") + \".png\")");
		else
			xbmcExpr = java.util.regex.Pattern.compile("Weather\\.Conditions", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_conditions\")");
		if (!testExpr.equals(xbmcExpr))
			dontCache = true;
		java.util.regex.Pattern patty = java.util.regex.Pattern.compile("Skin\\.String\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
		java.util.regex.Matcher matchy = patty.matcher(xbmcExpr);
		while (matchy.find())
		{
			dontCache = true;
			String settingStr = getAsStringConstant(matchy.group(1), false);
			if (preferBool && !imageSource)
				xbmcExpr = matchy.replaceFirst("(Size(GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + settingStr + ", \"\")) > 0)");
			else
				xbmcExpr = matchy.replaceFirst("GetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + settingStr + ", \"\")");
			matchy = patty.matcher(xbmcExpr);
		}
// NOTE: Nested parenthesis cause a problem with this replacement logic used
		// Nicely mapped boolean expressions
		if (xbmcExpr.toLowerCase().indexOf("player.") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.HasAudio", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsCurrentMediaFileMusic()");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.HasVideo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("DoesCurrentMediaFileHaveVideo()");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.HasMedia", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("HasMediaFile()");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Playing", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsPlaying()");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Paused", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(!IsPlaying() && HasMediaFile())");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding2x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == 2)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding4x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == 4)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding8x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == 8)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding16x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == 16)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding32x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == 32)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Forwarding", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() > 1.0)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding2x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == -2)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding4x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == -4)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding8x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == -8)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding16x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == -16)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding32x", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() == -32)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Rewinding", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetPlaybackRate() < 0)");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Recording", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsCurrentMediaFileRecording()");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Muted", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsMuted()");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.HasNext", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetCurrentPlaylistIndex() + 1 < GetNumberOfPlaylistItems(GetCurrentPlaylist()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.HasPrevious", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetCurrentPlaylistIndex() > 0)");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Exists", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetNumberOfPlaylistItems(GetCurrentPlaylist()) > ($1 + GetCurrentPlaylistIndex()))");
			xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.HasMenu", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsShowingDVDMenu()");
		}
		xbmcExpr = java.util.regex.Pattern.compile("((Player)|(Playlist))\\.IsRandom", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("((IsCurrentMediaFileMusic() && GetProperty(\"random_music_playback\", false)) || " +
			"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"random_video_playback\", false)))");
		xbmcExpr = java.util.regex.Pattern.compile("((Player)|(Playlist))\\.IsRepeatOne", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("((Player)|(Playlist))\\.IsRepeat", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("((IsCurrentMediaFileMusic() && GetProperty(\"music/repeat_playback\", false)) || " +
			"(DoesCurrentMediaFileHaveVideo() && GetProperty(\"video_lib/repeat_playback\", false)))");
		xbmcExpr = java.util.regex.Pattern.compile("Weather\\.IsFetched", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("tv_sage_weather_WeatherDotCom_updateNow(tv_sage_weather_WeatherDotCom_getInstance())");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.IdleTime\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetTimeSinceLastInput() > $1)");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.platform\\.xbox", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.platform\\.linux", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(IsLinuxOS() || IsMacOS())");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.platform\\.windows", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsWindowsOS()");
		patty = java.util.regex.Pattern.compile("Skin\\.HasSetting\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
		matchy = patty.matcher(xbmcExpr);
		while (matchy.find())
		{
			String settingStr = getAsStringConstant(matchy.group(1), false);
			xbmcExpr = matchy.replaceFirst("GetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + settingStr + ", false)");
			matchy = patty.matcher(xbmcExpr);
		}
//		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.HasSetting\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetProperty(\"xbmc/skins/" + skinName +
//			"/settings/$1\", false)");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.GetBool\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetProperty(\"xbmc/system/settings/$1\", false)");
		xbmcExpr = java.util.regex.Pattern.compile("ControlGroup\\(([^)]*)\\)\\.HasFocus\\([0]?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("GetVariableFromUIComponent(GetUIComponentForVariable(\"ControlGroupXBMCID\", $1), \"FocusedChild\")");
		xbmcExpr = java.util.regex.Pattern.compile("ControlGroup\\(([^)]*)\\)\\.HasFocus\\(([^)]+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetVariableFromUIComponent(GetUIComponentLastFocusedChild(GetUIComponentForVariable(\"ControlGroupXBMCID\", $1)), \"XBMCID\") == $2)");
//			replaceAll("GetVariableFromContext(\"RetainedFocusParentDashItemXBMCID\", \"$1-$2\", \"Focused\")");
		xbmcExpr = java.util.regex.Pattern.compile("ControlGroup\\(([^)]*)\\)\\.HasFocus", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("GetVariableFromUIComponent(GetUIComponentForVariable(\"ControlGroupXBMCID\", $1), \"FocusedChild\")");
		// For Control.HasFocus(id) XBMC will just check the ID of the focused control to see if it matches the value; so we don't want to lookup a control first
		// in case there's more than one control with the same ID
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.HasFocus\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("(GetVariableFromContext(\"Focused\", true, \"XBMCID\") == $1)");//replaceAll("GetVariableFromUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"FocusedChild\")");
		if (xbmcExpr.toLowerCase().indexOf("container") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.HasFocus\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("If(false, \"Focused\", GetVariableFromUIComponent(GetUIComponentLastFocusedChild(GetUIComponentForVariable(\"ContainerXBMCID\", $1)), \"ItemXBMCID\") == $2)");
	//			replaceAll("If(false, \"Focused\", GetVariableFromContext(\"XBMCID\", $1, \"RetainedFocusItemXBMCID\") == $2)");
	//			replaceAll("(GetVariableFromUIComponent(GetChildUIComponentForVariable(GetUIComponentForVariable(\"XBMCID\" $1), \"Focused\", true), \"ItemXBMCID\") == $2)");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.HasFocus\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetVariableFromUIComponent(GetChildUIComponentForVariable(GetUIComponentForVariable(\"ContainerXBMCID\" ActiveContainerXBMCID), \"Focused\", true), \"ItemXBMCID\") == $1)");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.Row\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisibleRow(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", $1))) == (1 + $2))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.Column\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisibleColumn(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", $1))) == (1 + $2))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.Position\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisiblePosition(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", $1))) == (1 + $2))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.Row\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisibleRow(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID))) == (1 + $1))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.Column\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisibleColumn(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID))) == (1 + $1))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.Position\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(GetTableFocusedVisiblePosition(If(false, \"Focused\", GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID))) == (1 + $1))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.NumPages", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetVariableFromContext(\"ContainerXBMCID\", $1, \"NumPages\")");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.NumPages", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"NumPages\")");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.NumItems", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("Size(GetVariableFromContext(\"ContainerXBMCID\", $1, \"TableData\"))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.NumItems", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("Size(GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"TableData\"))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.CurrentPage", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(1 + Max(GetVariableFromContext(\"ContainerXBMCID\", $1, \"VScrollIndex\")/Max(1,GetVariableFromContext(\"ContainerXBMCID\", $1, \"NumRowsPerPage\")), " +
				"GetVariableFromContext(\"ContainerXBMCID\", $1, \"HScrollIndex\")/Max(1,GetVariableFromContext(\"ContainerXBMCID\", $1, \"NumColsPerPage\"))))");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.CurrentPage", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(1 + Max(GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"VScrollIndex\")/Max(1,GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"NumRowsPerPage\")), " +
				"GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"HScrollIndex\")/Max(1,GetVariableFromContext(\"ContainerXBMCID\", ActiveContainerXBMCID, \"NumColsPerPage\"))))");
			patty = java.util.regex.Pattern.compile("Container\\.Content\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				String containerType = matchy.group(1);
				// XBMC wants 'seasons' to be seasons of a TV series (they don't have a TV Series container)
				// 'episodes' are then items within a season
				// If we're not doing a hiearachy of seasons->episodes, then the files are 'tvshows'
				if ("files".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"files\" == \"\" + ContainerContent)");
				else if ("musicvideos".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("((\"musicvideos\" == \"\" + ContainerContent) && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))");
				else if ("songs".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("((ContainerContent == \"music\") && (\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))))");
				else if ("artists".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Artist\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("albums".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Album\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("genres".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Category\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("years".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Year\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("actors".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Actor\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("directors".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Director\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("tvshows".equalsIgnoreCase(containerType))
//					xbmcExpr = matchy.replaceFirst("((\"\" + ContainerContent == \"tvshows\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))) " +
//						" && (GetShowSeriesInfo(GetNodeDataObject(GetNodeChildAt(CurrNode, 0))) == null))");
					xbmcExpr = matchy.replaceFirst("((\"\" + ContainerContent == \"tvshows\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))");
				else if ("movies".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("((\"\" + ContainerContent == \"movies\") && ((\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) || (\"File\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))))");
				else if ("episodes".equalsIgnoreCase(containerType) || "episode".equalsIgnoreCase(containerType))
//					xbmcExpr = matchy.replaceFirst("((\"\" + ContainerContent == \"tvshows\") && (\"MediaFile\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0))) && (GetShowSeriesInfo(GetNodeDataObject(GetNodeChildAt(CurrNode, 0))) != null))");
					xbmcExpr = matchy.replaceFirst("(\"Episode\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("playlists".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Playlist\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("seasons".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"SeriesInfo\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("studios".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Studio\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else if ("scripts".equalsIgnoreCase(containerType))
					xbmcExpr = matchy.replaceFirst("(\"Script\" == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				else // turn these into functions so we spot unknown ones!
					xbmcExpr = matchy.replaceFirst("(" + containerType + "() == GetNodeDataType(GetNodeChildAt(CurrNode, 0)))");
				matchy = patty.matcher(xbmcExpr);
			}
//			xbmcExpr = java.util.regex.Pattern.compile("Container\\.Content\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
//				replaceAll("java_lang_String_equalsIgnoreCase(\"$1\", \"\" + ContainerContent)");
			xbmcExpr = java.util.regex.Pattern.compile("Container\\.SortDirection\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("java_lang_String_equalsIgnoreCase(\"$1\", \"\" + ContainerSortDirectionAsc)");
		}
// NOTE Active & Visible are not the same thing; but only animations make them different
		if (knownWinID >= 0)
		{
			patty = java.util.regex.Pattern.compile("Window\\.IsVisible\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				dontCache = true;
				String menuStr = resolveWindowID(matchy.group(1));
				xbmcExpr = matchy.replaceFirst(("" + knownWinID).equals(menuStr) ? "true" : "false");
				matchy = patty.matcher(xbmcExpr);
			}
			patty = java.util.regex.Pattern.compile("Window\\.IsActive\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				dontCache = true;
				String menuStr = resolveWindowID(matchy.group(1));
				xbmcExpr = matchy.replaceFirst(("" + knownWinID).equals(menuStr) ? "true" : "false");
				matchy = patty.matcher(xbmcExpr);
			}
		}
		else
		{
			patty = java.util.regex.Pattern.compile("Window\\.IsVisible\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				dontCache = true;
				String menuStr = resolveWindowID(matchy.group(1));
				xbmcExpr = matchy.replaceFirst("(MenuXBMCID == " + menuStr + ")");
				matchy = patty.matcher(xbmcExpr);
			}
			patty = java.util.regex.Pattern.compile("Window\\.IsActive\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				dontCache = true;
				String menuStr = resolveWindowID(matchy.group(1));
				xbmcExpr = matchy.replaceFirst("(MenuXBMCID == " + menuStr + ")");
				matchy = patty.matcher(xbmcExpr);
			}
		}
//		xbmcExpr = java.util.regex.Pattern.compile("Control\\.IsVisible\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
//			replaceAll("GetVisibilityForVariable(\"XBMCID\", $1)");
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.GetLabel\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("GetTextForUIComponent(GetUIComponentForVariable(\"XBMCID\", $1))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.UsingOverlays", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsVideoRendererOverlay()");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.IsFullScreen", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("FullScreenVideo"); // menu attribute
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Content\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(java_lang_String_equalsIgnoreCase(\"files\", $1), true, If(java_lang_String_equalsIgnoreCase(\"movies\", $1), LocalizeString(\"Movie\") == GetShowCategory(GetCurrentMediaFile()), " +
			"If(java_lang_String_equalsIgnoreCase(\"episodes\", $1), StringStartsWith(GetShowExternalID(GetCurrentMediaFile()), \"EP\"), false)))"); // last one is 'musicvideos'
		xbmcExpr = java.util.regex.Pattern.compile("Window\\.IsMedia", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("IsMediaWindow"); // menu attribute
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.IsPlaying", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(If(ListItem == null, MenuListItem, ListItem) == GetCurrentMediaFile())");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.DisplayAfterSeek", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("((Time() - Max(0,LastSeekCompleteTime)) < 2500)"); // has a 2500 msec timeout after a seek
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.ShowCodec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("ShowCodecInfo");

		// Unsupported boolean expressions defaulting to true/false
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.HasDuration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.CanRecord", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.Caching", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("AudioScrobbler\\.Enabled", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.Enabled", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Visualisation\\.Enabled", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Visualisation\\.Locked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "Window.Next", "false", "", false);
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "Window.Previous", "false", "", false);
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "System.HasAlarm", "false", "", false);
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "System.AlarmLessOrEqual", "false", "", false);
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HasNetwork", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HasMediadvd", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.KaiConnected", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.AutoDetection", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.isloggedon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.hasloginscreen", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("LastFM\\.RadioPlaying", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("LastFM\\.CanLove", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("LastFM\\.CanBan", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.OnNext", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.OnPrevious", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.OnNext", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.OnPrevious", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.Scrolling", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.Scrolling", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("false");

		// Unsupported boolean expressions we could fix later easily
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.SeekBar", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.Seeking", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.ShowTime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.ShowInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.HasInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.canpowerdown", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.cansuspend", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.canhibernate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.canreboot", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.loggedon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.haslocks", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.hasdrivef", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.hasdriveg", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("system\\.ismaster", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("false");
		xbmcExpr = java.util.regex.Pattern.compile("xbmc\\.mastermode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMC.MasterMode\"");
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "ButtonScroller.HasFocus", "true", "", false);
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.HasTheme\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("(GetProperty(\"xbmc/skins/" + skinName + "/theme\", \"default\") == \"$1\")");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.HasThumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("true");
		xbmcExpr = replaceArgumentExpression(xbmcExpr, "Library.HasContent", "true", "", false);
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.HasFiles", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.HasFiles", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.HasFolders", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.HasFolders", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.IsStacked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.IsStacked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("true");


		// Nicely mapped non-boolean expressions
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\.Label2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeSecondaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), 0, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\.Label", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodePrimaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), 0, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\.Icon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeIcon(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), 0, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\.Thumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeThumbnail(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), 0, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Label2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(false, \"Focused\", GetNodeSecondaryLabel(If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Label", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(false, \"Focused\", GetNodePrimaryLabel(If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.icon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(false, \"Focused\", GetNodeIcon(If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.ActualIcon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(false, \"Focused\", GetNodeIcon(If(ListItem == null, MenuListItem, ListItem)))"); // What's the actual icon???
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.thumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(false, \"Focused\", GetNodeThumbnail(If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.duration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDurationShort(GetFileDuration(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem))))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.size", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetSize(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.mpaa", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowRated(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.mpaa", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowRated(GetCurrentMediaFile())");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.path", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetFileForSegment(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem)), 0)");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.filenameandpath", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetAbsoluteFilePath(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.filename", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetFileNameFromPath(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.TVShowTitle", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowTitle(If(false, \"Focused\", If(ListItem == null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Year", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowYear(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Property\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"$1\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Title", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetNodePrimaryLabel(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.SortLetter", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("Substring(GetNodePrimaryLabel(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))), 0, 1)");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.TrackNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("If(GetTrackNumber(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))) == 0, \"\", GetTrackNumber(If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Artist", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShowInRole(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), LocalizeString(\"Artist\"))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.album", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetAlbumName(GetAlbumForFile(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.genre", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowCategory(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.castandrole", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShow(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))"); // MISSING ROLES
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.cast", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShow(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.castandrole", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShow(GetCurrentMediaFile())"); // MISSING ROLES
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.cast", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShow(GetCurrentMediaFile())");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Director", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShowInRole(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), LocalizeString(\"Director\"))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Director", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Director\"))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Writer", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShowInRole(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), LocalizeString(\"Writer\"))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Writer", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Writer\"))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.PlotOutline", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowDescription(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Plot", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowDescription(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.PlotOutline", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowDescription(GetCurrentMediaFile())");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Plot", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetShowDescription(GetCurrentMediaFile())");
		xbmcExpr = java.util.regex.Pattern.compile("Fanart\\.Image", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"FanArtImage\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.StarRating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"StarRating\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.ratingandvotes", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"IMDBRatingAndVotes\")");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.ratingandvotes", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"IMDBRatingAndVotes\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.rating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"IMDBRating\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.PictureResolution", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"PictureResolution\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.PictureDateTime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDateFull(GetAiringStartTime(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.date", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDateShort(GetFileStartTime(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))) + \" \" + PrintTimeShort(GetFileStartTime(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem))))");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Studio", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Studio\")");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Studio", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"Studio\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Trailer", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Trailer\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Premiered", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Premiered\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Biography", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Biography\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Comment", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Comment\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Tagline", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"Tagline\")");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Tagline", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"Tagline\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.episode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"EpisodeNumber\")");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.episode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"EpisodeNumber\")");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Season", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)), \"SeasonNumber\")");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Season", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"SeasonNumber\")");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.ChapterCount", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetDVDNumberOfChapters()");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.ChapterName", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"ChapterName.\" + GetDVDCurrentChapter())");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.Chapter", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetDVDCurrentChapter()");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ShowPlot", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetSeriesDescription(If(false, \"Focused\", If(ListItem== null, MenuListItem, ListItem)))");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.Date\\(([^),]*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("((java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"$1\") >= 0) && (java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"$2\") < 0))");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.Date\\(([^),]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("(java_lang_String_compareTo(DateFormat(\"MM-dd\", Time()), \"$1\") >= 0)");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.Date", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("PrintDateFull(Time())");
		if (xbmcExpr.toLowerCase().indexOf("system.time") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("xx", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("aa");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Time\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("DateFormat(\"$1\", Time())");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Time", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("PrintTimeShort(Time())");
		}
		xbmcExpr = java.util.regex.Pattern.compile("SetFocus\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll(getSetFocusExpr("$1"));
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.SetFocus\\(([^),]*)\\,([^),]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetFocusForVariable(\"ListItem\", GetElement(GetVariableFromContext(\"XBMCID\", $1, \"TableData\"), $2))");
// NOTE: THIS DOESN'T HANDLE HORIZONTAL ORIENTATION OF THE COMPONENT
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Move\\(([^),]*)\\,([^),]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), If($2 < 0, \"Up\", \"Down\"), $2)");
// NOTE: WE'RE IGNORING THE WINDOW ID IN Control.Message SINCE WE DON'T KNOW THE PURPOSE OF IT YET, it's in $3
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Message\\(([^),]*)\\,movedown(\\,([^)]*))?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"Down\", 1)");
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Message\\(([^),]*)\\,moveup(\\,([^)]*))?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"Up\", 1)");
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Message\\(([^),]*)\\,pagedown(\\,([^)]*))?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"Page Down\", 1)");
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Message\\(([^),]*)\\,pageup(\\,([^)]*))?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"Page Up\", 1)");
		xbmcExpr = java.util.regex.Pattern.compile("Control\\.Message\\(([^),]*)\\,click(\\,([^)]*))?\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", $1), \"Select\", 1)");
		// We insert the extra If(false, "Focused"...) in here so it becomes a focus listener as well
/*		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Label2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetElement(GetVariableFromContext(\"XBMCID\", $1, \"ListItemLabels2\"), (GetVariableFromContext(\"XBMCID\", $1, \"ListFocusIndex\") + " +
			"GetVariableFromContext(\"XBMCID\", $1, \"VScrollIndex\") - 1 + $2 + GetVariableFromContext(\"XBMCID\", $1, \"NumRows\")) % Max(1, GetVariableFromContext(\"XBMCID\", $1, \"NumRows\"))))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Label", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetElement(GetVariableFromContext(\"XBMCID\", $1, \"ListItemLabels\"), (GetVariableFromContext(\"XBMCID\", $1, \"ListFocusIndex\") + " +
			"GetVariableFromContext(\"XBMCID\", $1, \"VScrollIndex\") - 1 + $2 + GetVariableFromContext(\"XBMCID\", $1, \"NumRows\")) % Max(1, GetVariableFromContext(\"XBMCID\", $1, \"NumRows\"))))");*/
/*		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Label2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeSecondaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), $2, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Label", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodePrimaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), $2, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Icon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeIcon(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), $2, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\(([^)]*)\\)\\.ListItem\\(([^)]*)\\).Thumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeThumbnail(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"XBMCID\", $1), $2, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ListItem\\(([^)]*)\\).Label2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeSecondaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), $1, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ListItem\\(([^)]*)\\).Label", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodePrimaryLabel(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), $1, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ListItem\\(([^)]*)\\).Icon", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeIcon(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), $1, true)))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ListItem\\(([^)]*)\\).Thumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("If(false, \"Focused\", GetNodeThumbnail(GetDataFromTableFocusedOffset(GetUIComponentForVariable(\"ContainerXBMCID\", ActiveContainerXBMCID), $1, true)))");
		if (xbmcExpr.toLowerCase().indexOf("weather") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("Weather\\.Temperature", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_temp\")");
			xbmcExpr = java.util.regex.Pattern.compile("Weather\\.Location", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getLocationInfo(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_location\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Location\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getLocationInfo(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_location\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Updated\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_updated\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.Condition\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_conditions\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.Temperature\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_temp\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.FeelsLike\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_windchill\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.UVIndex\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_uv_index\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.Wind\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_wind\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.DewPoint\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_dewpoint\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.Humidity\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_humidity\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Day([0-9])\\.Title\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("Substring(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"date$1\"), 0, " +
				"StringIndexOf(tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"date$1\"), \" \"))");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Day([0-9])\\.HighTemp\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"hi$1\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Day([0-9])\\.LowTemp\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"low$1\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Day([0-9])\\.Outlook\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"conditionsd$1\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Current\\.ConditionIcon\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(\"media/WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getCurrentCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"curr_icon\") + \".png\")");
			xbmcExpr = java.util.regex.Pattern.compile("Window\\(Weather\\)\\.Property\\(Day([0-9])\\.OutlookIcon\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(\"media/WeatherIcons/Images/\" + tv_sage_weather_WeatherDotCom_getForecastCondition(tv_sage_weather_WeatherDotCom_getInstance(), \"icond$1\") + \".png\")");
		}
		xbmcExpr = java.util.regex.Pattern.compile("System\\.TemperatureUnits", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("\"\u00b0\" + If(tv_sage_weather_WeatherDotCom_getUnits(tv_sage_weather_WeatherDotCom_getInstance()) == \"s\", \"F\", \"C\")");

		patty = java.util.regex.Pattern.compile("Skin\\.ToggleSetting\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
		matchy = patty.matcher(xbmcExpr);
		while (matchy.find())
		{
			String settingStr = getAsStringConstant(matchy.group(1), false);
			xbmcExpr = matchy.replaceFirst("SetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + settingStr + ", !GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + settingStr + ", false))");
			matchy = patty.matcher(xbmcExpr);
		}
		patty = java.util.regex.Pattern.compile("Skin\\.SetBool\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
		matchy = patty.matcher(xbmcExpr);
		while (matchy.find())
		{
			String settingStr = getAsStringConstant(matchy.group(1), false);
			xbmcExpr = matchy.replaceFirst("SetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + settingStr + ", true)");
			matchy = patty.matcher(xbmcExpr);
		}
		patty = java.util.regex.Pattern.compile("Skin\\.Reset\\(([^()]*(\\([^()]*\\)[^()]*)*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
		matchy = patty.matcher(xbmcExpr);
		while (matchy.find())
		{
			String settingStr = getAsStringConstant(matchy.group(1), false);
			xbmcExpr = matchy.replaceFirst("RemoveProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + settingStr + ")");
			matchy = patty.matcher(xbmcExpr);
		}
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.ResetSettings", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("RemovePropertyAndChildren(\"xbmc/skins/" + skinName + "/settings\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?ReloadSkin(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("Refresh()"); // ReloadSkin is used to just refresh a menu; like in Aeon's picture library after changing the grid size
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?RestartApp(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("LoadSTVFile(GetCurrentSTVFile())");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?Shutdown\\(\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("Exit()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?Quit\\(\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("Exit()");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.SetViewMode\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("ContainerViewType = GetVariableFromContext(\"XBMCID\", $1, \"ThisViewType\")");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.NextViewMode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("ContainerViewType = GetElement((FindElementIndex(ContainerViews, ContainerViewType) + 1) % Size(ContainerViews))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.PreviousViewMode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("ContainerViewType = GetElement((FindElementIndex(ContainerViews, ContainerViewType) - 1 + Size(ContainerViews)) % Size(ContainerViews))");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.ViewMode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("ContainerViewType");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.FolderPath", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("CurrFolder");
		if (xbmcExpr.toLowerCase().indexOf("player.") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Title", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaTitle(GetCurrentMediaFile())");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Title", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaTitle(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Title", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaTitle(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Album", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetAlbumName(GetAlbumForFile(GetCurrentMediaFile()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Album", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetAlbumName(GetAlbumForFile(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex())))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Album", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetAlbumName(GetAlbumForFile(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1)))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Artist", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetPeopleInShowInRole(GetCurrentMediaFile(), LocalizeString(\"Artist\"))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Artist", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetPeopleInShowInRole(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()), LocalizeString(\"Artist\"))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Artist", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetPeopleInShowInRole(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1), LocalizeString(\"Artist\"))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Genre", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowCategory(GetCurrentMediaFile())");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Genre", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowCategory(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Genre", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowCategory(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Year", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowYear(GetCurrentMediaFile())");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Year", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowYear(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Year", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetShowYear(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Rating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"Rating\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Rating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()), \"Rating\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Rating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1), \"Rating\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.DiscNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"DiscNumber\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.DiscNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()), \"DiscNumber\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.DiscNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1), \"DiscNumber\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Comment", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetCurrentMediaFile(), \"Comment\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.Comment", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()), \"Comment\")");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.Comment", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetMediaFileMetadata(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1), \"Comment\")");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.TimeRemaining", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("PrintDurationShort(GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Progress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))*1.0)/GetMediaDuration()");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.TimeSpeed", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(PrintDurationShort((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile())))) + \" (\"GetPlaybackRate() + \"x)\")");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.Time", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("PrintDurationShort((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.TrackNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("If(GetTrackNumber(GetCurrentMediaFile()) == 0, \"\", GetTrackNumber(GetCurrentMediaFile()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.TrackNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("If(GetTrackNumber(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex())) == 0, \"\", GetTrackNumber(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex())))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.TrackNumber", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("If(GetTrackNumber(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1)) == 0, \"\", GetTrackNumber(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1)))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.duration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetFileDuration(GetCurrentMediaFile())");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Offset\\(([^)]*)\\)\\.duration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetFileDuration(GetPlaylistItemAt(GetCurrentPlaylist(), $1 + GetCurrentPlaylistIndex()))");
			xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Position\\(([^)]*)\\)\\.duration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("GetFileDuration(GetPlaylistItemAt(GetCurrentPlaylist(), $1 - 1))");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.PlaylistPosition", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetCurrentPlaylistIndex() + 1)");
			xbmcExpr = java.util.regex.Pattern.compile("((Video)|(Music))Player\\.PlaylistLength", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetNumberOfPlaylistItems(GetCurrentPlaylist())");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.FinishTime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintTimeShort(Time() + GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.TimeRemaining", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDurationShort(GetMediaDuration() - (GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Time", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDurationShort((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.SeekTime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDurationShort((GetMediaTime() - If(IsDVD(GetCurrentMediaFile()), 0, GetAiringStartTime(GetCurrentMediaFile()))))");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Duration", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDurationShort(GetMediaDuration())");
			xbmcExpr = java.util.regex.Pattern.compile("Player\\.Volume", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetVolume()");
		}
		xbmcExpr = java.util.regex.Pattern.compile("Playlist\\.Position", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetCurrentPlaylistIndex() + 1)");
		xbmcExpr = java.util.regex.Pattern.compile("Playlist\\.Length", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetNumberOfPlaylistItems(GetCurrentPlaylist())");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.IPAddress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetLocalIPAddress()");
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.CurrentTheme", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("GetProperty(\"xbmc/skins/" + skinName + "/theme\", \"default\")");
		if (xbmcExpr.toLowerCase().indexOf("system.") != -1 || xbmcExpr.toLowerCase().indexOf("bar.") != -1)
		{
			xbmcExpr = java.util.regex.Pattern.compile("System\\.UsedspacePercent\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(100 - Round((GetDiskFreeSpace(\"$1\")*1.0/GetDiskTotalSpace(\"$1\"))*100))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.FreespacePercent\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("Round((GetDiskFreeSpace(\"$1\")*1.0/GetDiskTotalSpace(\"$1\"))*100)");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.Freespace\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetDiskFreeSpace(\"$4\")/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Totalspace\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetDiskTotalSpace(\"$1\")/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.Usedspace\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("((GetDiskTotalSpace(\"$4\") - GetDiskFreeSpace(\"$4\"))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.UsedspacePercent", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("(100 - Round((GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\", \"/\")))*100))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.FreespacePercent", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("Round((GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\", \"/\"))*1.0/GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\", \"/\")))*100)");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.Freespace", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\", \"/\"))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Totalspace", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\", \"/\"))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.Usedspace", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
				replaceAll("((GetDiskTotalSpace(If(IsWindowsOS(), \"C:\\\", \"/\")) - GetDiskFreeSpace(If(IsWindowsOS(), \"C:\\\", \"/\")))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.BuildVersion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetProperty(\"version\", \"\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.BuildDate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDateFull(java_io_File_lastModified(new_java_io_File(\"Sage.jar\")))");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.FreeMemory", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.memory\\(free\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.UsedMemory", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("((java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()) - java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime()))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Memory\\(used\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("((java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()) - java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime()))/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Memory\\(total\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime())/1048576 + \" MB\")");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.memory(free.percent)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("Round(100*(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime())))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.memory(used.percent)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(100 - Round(100*(java_lang_Runtime_freeMemory(java_lang_Runtime_getRuntime())*1.0/java_lang_Runtime_totalMemory(java_lang_Runtime_getRuntime()))))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.ScreenMode", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetAnalogVideoFormat()");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.ScreenWidth", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetFullUIWidth()");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.ScreenHeight", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetFullUIHeight()");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.CurrentWindow", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetWidgetName(GetCurrentMenuWidget())");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.CurrentControl", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetTextForUIComponent(GetUIComponentForVariable(\"Focused\", true))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.KernelVersion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("(java_lang_System_getProperty(\"os.name\") + \" \" + java_lang_System_getProperty(\"os.version\"))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.uptime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDuration(Time() - GetApplicationLaunchTime())");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.totaluptime", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PrintDuration((Time() - GetApplicationLaunchTime()) + (GetProperty(\"uptime\", 0)*1))");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.ScreenResolution", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetDisplayResolution()");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.Language", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetUILanguage()");
			xbmcExpr = java.util.regex.Pattern.compile("System\\.ProfileName", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("java_lang_System_getProperty(\"user.name\")");
		}

		// Unsupported non-boolean expressions defaulting to constants
		xbmcExpr = java.util.regex.Pattern.compile("AudioScrobbler\\.ConnectState", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"AudioScrobbler.ConnectState\"");
		xbmcExpr = java.util.regex.Pattern.compile("AudioScrobbler\\.SubmitInterval", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"AudioScrobbler.SubmitInterval\"");
		xbmcExpr = java.util.regex.Pattern.compile("AudioScrobbler\\.FilesCached", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"AudioScrobbler.FilesCached\"");
		xbmcExpr = java.util.regex.Pattern.compile("AudioScrobbler\\.SubmitState", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"AudioScrobbler.SubmitState\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.Overlay", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("ListItemOverlay");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.FolderPath", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.FolderPath\"");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.PluginName", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.PluginName\"");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.PluginCategory", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.PluginCategory\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.VideoCodec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.VideoCodec\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.VideoAspect", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.VideoAspect\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.VideoResolution", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.VideoResolution\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.AudioCodec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.AudioCodec\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.AudioChannels", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.AudioChannels\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.VideoCodec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.VideoCodec\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.VideoAspect", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.VideoAspect\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.VideoResolution", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.VideoResolution\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.AudioCodec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.AudioCodec\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.AudioChannels", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.AudioChannels\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.AudioLanguage", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.AudioLanguage\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.SubtitleLanguage", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.SubtitleLanguage\"");
		xbmcExpr = java.util.regex.Pattern.compile("ListItem\\.programcount", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"ListItem.ProgramCount\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.bitrate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPlayer.BitRate\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.channels", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPlayer.Channels\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.bitspersample", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPlayer.BitsPerSample\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.samplerate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPlayer.SampleRate\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.codec", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPlayer.Codec\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.SongsPlayed", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.SongsPlayed\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.MatchingSongsPicked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.MatchingSongsPicked\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.MatchingSongsLeft", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.MatchingSongsLeft\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.MatchingSongs", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.MatchingSongs\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.RelaxedSongsPicked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.RelaxedSongsPicked\"");
		xbmcExpr = java.util.regex.Pattern.compile("MusicPartyMode\\.RandomSongsPicked", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"MusicPartyMode.RandomSongsPicked\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.MacAddress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.MacAddress\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.IsDHCP", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.IsDHCP\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.LinkState", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.LinkState\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.SubnetAddress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.SubnetAddress\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.GatewayAddress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.GatewayAddress\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.DHCPAddress", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.DHCPAddress\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.DNS1Address", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.DNS1Address\"");
		xbmcExpr = java.util.regex.Pattern.compile("Network\\.DNS2Address", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Network.DNS2Address\"");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.CacheLevel", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Player.CacheLevel\"");
		xbmcExpr = java.util.regex.Pattern.compile("Playlist\\.Random", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Playlist.Random\"");
		xbmcExpr = java.util.regex.Pattern.compile("Playlist\\.Repeat", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Playlist.Repeat\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.Alarmpos", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.Alarmpos\"");
		xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.cputemperature", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.CPUTemperature\"");
		xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.gputemperature", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.GPUTemperature\"");
		xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.cpuusage", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.GPUTemperature\"");
		xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.fanspeed", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.FanSpeed\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.fps", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.FPS\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.XbocNickName", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.XBocNickName\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.DVDLabel", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.DVDLabel\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.dvdtraystate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.dvdtraystate\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.trayopen", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.trayopen\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.dvdready", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.dvdready\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.LaunchXBE", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.LaunchXBE\"");
		xbmcExpr = java.util.regex.Pattern.compile("((System)|(bar))\\.HDDTemperature", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDTemperature\"");

		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDInfoModel", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDInfoModel\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDInfoFirmware", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDInfoFirmware\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDInfoSerial", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDInfoSerial\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDInfoPW", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDInfoPW\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDInfoLockState", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDInfoLockState\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDLockKey", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDLockKey\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDBootDate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDBootDate\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.HDDCycleCount", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.HDDCycleCount\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.DVDInfoModel", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.DVDInfoModel\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.DVDInfoFirmware", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.DVDInfoFirmware\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.mplayerversion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.mplayerversion\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.cpufrequency", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.CPUFrequency\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.mplayerversion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.mplayerversion\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.XBoxVersion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.XBoxVersion\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.AVCablePackInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.AVCablePackInfo\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.AVPackInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.AVPackInfo\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.VideoEncoderInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.VideoEncoderInfo\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.XBoxSerial", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.XBoxSerial\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.XBoxProduceInfo", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.XBoxProduceInfo\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.VideoXBERegion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.VideoXBERegion\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.XBERegion", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.XBERegion\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.BIOS", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.BIOS\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.dvdzone", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.dvdzone\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.modchip", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.modchip\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.internetstate", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.InternetState\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.controllerport\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.ControllerPort($1)\"");

		xbmcExpr = java.util.regex.Pattern.compile("Visualisation\\.Preset", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Visualisation.Preset\"");
		xbmcExpr = java.util.regex.Pattern.compile("Visualisation\\.Name", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Visualisation.Name\"");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.TVShowTitle", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"VideoPlayer.TVShowTitle\"");
		xbmcExpr = java.util.regex.Pattern.compile("XLinkKai\\.UserName", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XLinkKai.UserName\"");

		xbmcExpr = java.util.regex.Pattern.compile("Fanart\\.Color1", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Fanart.Color1\"");
		xbmcExpr = java.util.regex.Pattern.compile("Fanart\\.Color2", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Fanart.Color2\"");
		xbmcExpr = java.util.regex.Pattern.compile("Fanart\\.Color3", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Fanart.Color3\"");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.property\\(fanart_image\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.FanArt_Image\"");

		// Window([window]).Property(key) .... Not sure what this one is yet; I want to see examples so hide it...images and strings it returns

		// Nicely mapped image methods
		xbmcExpr = java.util.regex.Pattern.compile("MusicPlayer\\.Cover", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetAlbumArt(GetAlbumForFile(GetCurrentMediaFile()))");
		xbmcExpr = java.util.regex.Pattern.compile("VideoPlayer\\.Cover", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("GetThumbnail(GetCurrentMediaFile())");

		// Unsupported image methods
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.FolderThumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"media/DefaultFolderBackBig.png\"");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.tvshowthumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.TVShowThumb\"");
		xbmcExpr = java.util.regex.Pattern.compile("Container\\.seasonthumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Container.SeasonThumb\"");
		xbmcExpr = java.util.regex.Pattern.compile("Player\\.StarRating", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"Player.StarRating\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.ProfileThumb", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"System.ProfileThumb\"");

		// Mapped built-in functions
		// I want to see example usage of this first before I replace it
//		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayMedia\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("Watch($1)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?Reboot(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Eject\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(play\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("PlayPause()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(stop\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("CloseAndWaitUntilClosed()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(forward\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Smooth Fast Forward\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(rewind\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Smooth Rewind\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(next\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Channel Up\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(previous\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Channel Down\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(showvideomenu\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"DVD Menu\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(record\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SageCommand(\"Record\")");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(BigSkipForward\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SkipForward2()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(BigSkipBackward\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SkipBackwards2()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(SmallSkipForward\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SkipForward()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(SmallSkipBackward\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("SkipBackwards()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(RandomOn\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), true)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(RandomOff\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), false)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(Random\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), !GetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), false))");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(RepeatAll\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), true)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(RepeatOff\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), false)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?PlayerControls?\\(Repeat\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), !GetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), false))");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?SetVolume\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetVolume(1.0*$1/100)");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?Mute", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetMute(!IsMuted())");
		xbmcExpr = java.util.regex.Pattern.compile("Dialog\\.Close\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("CloseOptionsMenu()");
		xbmcExpr = java.util.regex.Pattern.compile("(XBMC\\.)?UpdateLibrary\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("RunLibraryImportScan(false)");
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.SetString\\(([^),]*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(\"xbmc/skins/" + skinName + "/settings/$1\", \"$2\")");
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.SetPath\\(([^),]*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(\"xbmc/skins/" + skinName + "/settings/$1\", \"$2\")");
		xbmcExpr = java.util.regex.Pattern.compile("Skin\\.SetImage\\(([^),]*)\\,([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).
			replaceAll("SetProperty(\"xbmc/skins/" + skinName + "/settings/$1\", \"$2\")");

		// Unsupported built-in functions
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Reboot(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCReboot()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Restart(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCRestart()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Suspend(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCSuspend()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Hibernate(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCHibernate()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Dashboard(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCDashboard()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Credits(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCCredits()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Reset(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCReset()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.RunScript\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCRunScript($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.RunXBE\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCRunXBE($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.RunPlugin\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCRunPlugin($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(PartyMode\\(music\\)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl(PartyMode(music))\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(PartyMode\\(video\\)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl(PartyMode(video))\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(PartyMode\\(\\)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl(PartyMode())\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(PartyMode\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl(PartyMode)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(PartyMode(\\([^)]*\\))\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl.PartyMode($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.PlayerControls?\\(RepeatOne\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCPlayerControl(RepeatOne)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.CancelAlarm\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCCancelAlarm($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.TakeScreenshot(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCTakeScreenshot()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.Extract\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCExtract($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.LogOff(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCSystemLogOff()\"");
		xbmcExpr = java.util.regex.Pattern.compile("XBMC\\.BackupSystemInfo(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCBackupSystemInfo()\"");
		xbmcExpr = java.util.regex.Pattern.compile("System\\.PWMControl\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCSystemPWMControl($1)\"");
		xbmcExpr = java.util.regex.Pattern.compile("LastFM\\.Love(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCLastFMLove()\"");
		xbmcExpr = java.util.regex.Pattern.compile("LastFM\\.Ban(\\(\\))?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xbmcExpr).replaceAll("\"XBMCLastFMBan()\"");

		// Unsupported non-boolean expressions we could fix later easily defaulting to constants
// DO WE NEED TO TO DO THIS FOR STANDARD EXPRESSIONS??
//		xbmcExpr = localizeStrings(xbmcExpr, true);

		// Do this one last since it recurses and will re-translate anything that's already been translated
		if (controlContext != null)
		{
boolean debugControlVisibility = false;
			patty = java.util.regex.Pattern.compile("Control\\.IsVisible\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
			matchy = patty.matcher(xbmcExpr);
			while (matchy.find())
			{
				dontCache = true;
				int targetID = parseInt(matchy.group(1));
				java.util.Vector condControls = controlContext.win.getControlsWithID(targetID);
				boolean cyclicIgnore = loopVisIDs != null && !loopVisIDs.add(new Integer(targetID));
				boolean emptyConds = true;
				for (int i = 0; i < condControls.size(); i++)
				{
					Control currCont = (Control) condControls.get(i);
					if (currCont.visible != null && !currCont.visible.isEmpty())
					{
						emptyConds = false;
						break;
					}
				}
				if (condControls.isEmpty())
					xbmcExpr = matchy.replaceFirst(debugControlVisibility ? "(false && ControlVisibility$1)" : "false");
				else if (cyclicIgnore || emptyConds || controlContext.id == targetID)
					xbmcExpr = matchy.replaceFirst(debugControlVisibility ? "(true || ControlVisibility$1)" : "true");
				else
				{
					String replaceStr = "";
					for (int j = 0; j < condControls.size(); j++)
					{
						String innerReplaceStr = "";
						Control currControl = (Control) condControls.get(j);
						if (currControl.visible == null || currControl.visible.isEmpty())
							continue;
						for (int i = 0; i < currControl.visible.size(); i++)
						{
							if (i > 0)
								innerReplaceStr += " && ";
							java.util.Set currLoop = loopVisIDs;
							if (currLoop == null)
							{
								currLoop = new java.util.HashSet();
								currLoop.add(new Integer(targetID));
							}
							innerReplaceStr += "(" + translateExpression(currControl.visible.get(i).toString(), controlContext, imageSource, preferBool, knownWinID,
								currLoop) + ")";
						}
						if (replaceStr.length() != 0)
							replaceStr += " || ";
						replaceStr += "(" + innerReplaceStr + ")";
					}
					replaceStr = "(" + replaceStr + ")";
					xbmcExpr = matchy.replaceFirst(debugControlVisibility ? ("(" + replaceStr + " || ControlVisibility$1)") : ("(" + replaceStr + ")"));
				}
				matchy = patty.matcher(xbmcExpr);
			}
		}

		xbmcExpr = xbmcExpr.replace("[", "(");
		xbmcExpr = xbmcExpr.replace("]", ")");

		if (!dontCache && loopVisIDs == null)
			exprCache.put(orgLcExpr, xbmcExpr);

		exprDump.put(orgLcExpr, xbmcExpr);

		return xbmcExpr;
	}
*/
	private String getSortMethod(String sortStr)
	{
		int sortType = -1;
		try
		{
			sortType = Integer.parseInt(sortStr);
		}
		catch (NumberFormatException nfe)
		{
			return sortStr;
		}
		String sortName = "name"; // default;
		switch (sortType)
		{
			case 1:
				sortName = "name";
				break;
			case 2:
				sortName = "nameignorethe";
				break;
			case 3:
				sortName = "date";
				break;
			case 4:
				sortName = "size";
				break;
			case 5:
				sortName = "filename";
				break;
			case 7:
				sortName = "track";
				break;
			case 8:
				sortName = "duration";
				break;
			case 9:
			case 21:
				sortName = "title";
				break;
			case 10:
				sortName = "titleignorethe";
				break;
			case 11:
				sortName = "artist";
				break;
			case 12:
				sortName = "artistignorethe";
				break;
			case 13:
				sortName = "album";
				break;
			case 14:
				sortName = "albumignorethe";
				break;
			case 15:
				sortName = "category";
				break;
			case 16:
				sortName = "year";
				break;
			case 17:
			case 23:
				sortName = "rating";
				break;
			case 18:
				sortName = "count";
				break;
			case 19:
				sortName = "playlistorder";
				break;
			case 20:
				sortName = "episodename";
				break;
			case 22:
				sortName = "episodeid";
				break;
			case 24:
				sortName = "rated";
				break;
			case 25:
				sortName = "runtime";
				break;
			case 26:
				sortName = "studio";
				break;
			case 27:
				sortName = "studioignorethe";
				break;
			case 28:
			case 6:
				sortName = "fullpath";
				break;
		}
		return sortName;
	}
	private Widget createProcessChainFromExpression(String expr, Control containerContext) throws Exception
	{
		// This handles special cases when expressions aren't so simple that we need to execute in the process chain
		Widget rv = null;
		String lcexpr = expr.toLowerCase();
		if (lcexpr.startsWith("xbmc."))
		{
			lcexpr = lcexpr.substring(5);
			expr = expr.substring(5);
		}
		if (lcexpr.startsWith("reboot") || lcexpr.startsWith("restart"))
			expr = "Reboot()";
		else if (lcexpr.startsWith("shutdown"))
			expr = "Exit()";
		else if (lcexpr.startsWith("dashboard"))
			expr = "Dashboard()";
		else if (lcexpr.startsWith("powerdown"))
			expr = "Exit()";
		else if (lcexpr.startsWith("restartapp"))
			expr = "LoadSTVFile(GetCurrentSTVFile())";
		else if (lcexpr.startsWith("hibernate"))
			expr = "Hibernate()";
		else if (lcexpr.startsWith("suspend"))
			expr = "Suspend()";
		else if (lcexpr.startsWith("quit"))
			expr = "Exit()";
		else if (lcexpr.startsWith("mastermode"))
			expr = "MasterMode()";
		else if (lcexpr.startsWith("takescreenshot"))
			expr = "Screenshot()";
		else if (lcexpr.startsWith("credits"))
			expr = "RunCredits()";
		else if (lcexpr.startsWith("reset"))
			expr = "Reset()";
		// loadprofile is not defined!!!!!!!!!!!!!
		// NOTE: replaceWindow DOES NOT ACTUALLY DO THAT YET; WE NEED TO UPDATE THE CORE TO HANDLE THAT
		else if (lcexpr.startsWith("activatewindow") || lcexpr.startsWith("replacewindow"))
		{
			// Window activation!
			String winStr = expr.substring(expr.indexOf('(') + 1, expr.lastIndexOf(')'));
			if (winStr.indexOf(',') != -1)
			{
				rv = mgroup.addWidget(ACTION);
				Widget menuParent = rv;
				String menuParam1 = winStr.substring(winStr.indexOf(',') + 1);
				String menuParam2 = null;
				if (menuParam1.indexOf(',') != -1)
				{
					menuParam2 = menuParam1.substring(menuParam1.indexOf(',') + 1);
					menuParam1 = menuParam1.substring(0, menuParam1.indexOf(','));
				}
				WidgetFidget.setName(rv, "AddStaticContext(\"XBMCMenuMode\", \"" + menuParam1 + "\")");
				if (menuParam2 != null)
					menuParent = addWidgetNamed(rv, ACTION, "AddStaticContext(\"XBMCMenuDir\", \"" + menuParam2 + "\")");
				if (expr.toLowerCase().indexOf("replacewindow") != -1)
					menuParent = addWidgetNamed(rv, ACTION, "AddStaticContext(\"ReplaceMenu\", true)");
				Widget menuWidg = resolveMenuWidget(winStr.substring(0, winStr.indexOf(',')));
				WidgetFidget.contain(menuParent, menuWidg);
				return rv;
			}
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "\"REM LaunchMenu " + winStr + "\"");
			WidgetFidget.contain(rv, resolveMenuWidget(winStr));
			return rv;
		}
		else if (lcexpr.startsWith("setfocus") || lcexpr.startsWith("control.setfocus"))
		{
			String controlID = getArg(getParen(lcexpr), 0);
			String itemID = "0";
			if (lcexpr.indexOf(',') != -1)
				itemID = getArg(getParen(lcexpr), 1);
			if (itemID.equals("0"))
				expr = getSetFocusExpr(controlID);
			else
				expr = "SetFocusForVariable(\"ListItem\", GetElement(GetVariableFromContext(\"XBMCID\", " + controlID + ", \"TableData\"), " + itemID + "))";
		}
		else if (lcexpr.startsWith("runscript"))
			expr = expr;
		else if (lcexpr.startsWith("resolution"))
		{
			// NOTE: THIS NEEDS TO CONVERT BETWEEN AN XBMC RESOLUTION AND THE ONES WE USE
			expr = "SetDisplayResolution(" + getParen(lcexpr) + ")";
		}
		else if (lcexpr.startsWith("extract"))
			expr = expr;
		else if (lcexpr.startsWith("runxbe"))
			expr = expr;
		else if (lcexpr.startsWith("runplugin"))
			expr = expr;
// NOTE: DON'T FIX THESE YET; WE WANT TO LOOK AT THEIR USAGE FIRST
		else if (lcexpr.startsWith("playmedia"))
			expr = expr;
// NOTE: DON'T FIX THESE YET; WE WANT TO LOOK AT THEIR USAGE FIRST
		else if (lcexpr.startsWith("slideshow") || lcexpr.startsWith("recursiveslideshow"))
		{
			// Start a slideshow
			String winStr = expr.substring(expr.indexOf('(') + 1, expr.lastIndexOf(')'));
			java.util.StringTokenizer toker = new java.util.StringTokenizer(winStr, ",");
			String slideshowDir = toker.nextToken();
			String slideshowOpt = "";
			if (toker.hasMoreTokens())
				slideshowOpt = toker.nextToken();
			boolean recursive = expr.toLowerCase().indexOf("recursive") != -1;
			boolean forceRandom = "random".equalsIgnoreCase(slideshowOpt);
			boolean forceNonRandom = "notrandom".equalsIgnoreCase(slideshowOpt);

//			rv = mgroup.addWidget(ACTION);
		}
		else if (lcexpr.startsWith("reloadskin"))
			expr = "Refresh()"; // ReloadSkin is used to just refresh a menu; like in Aeon's picture library after changing the grid size
		else if (lcexpr.startsWith("refreshrss"))
			expr = expr;
		else if (lcexpr.startsWith("playercontrol"))
		{
			String command = getParen(lcexpr);
			if (command.equals("play")) expr = "PlayPause()";
			else if (command.equals("stop")) expr = "CloseAndWaitUntilClosed()";
			else if (command.equals("forward")) expr = "SageCommand(\"Smooth Fast Forward\")";
			else if (command.equals("rewind")) expr = "SageCommand(\"Smooth Rewind\")";
			else if (command.equals("next")) expr = "SageCommand(\"Channel Up\")";
			else if (command.equals("previous")) expr = "SageCommand(\"Channel Down\")";
			else if (command.equals("bigskipbackward")) expr = "SkipBackwards2()";
			else if (command.equals("bigskipforward")) expr = "SkipForward2()";
			else if (command.equals("smallskipbackward")) expr = "SkipBackwards()";
			else if (command.equals("smallskipforward")) expr = "SkipForward()";
			else if (command.equals("showvideomenu")) expr = "SageCommand(\"DVD Menu\")";
			else if (command.equals("record")) expr = "SageCommand(\"Record\")";
			else if (command.startsWith("partymodemusic")) expr = "XBMCPartyModeMusic()";
			else if (command.startsWith("partymodevideo")) expr = "XBMCPartyModeVideo()";
			else if (command.equals("partymode")) expr = "XBMCPartyModeToggle()";
			else if (command.equals("randomon")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), true)";
			else if (command.equals("randomoff")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), false)";
			else if (command.equals("random")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), !GetProperty(If(DoesCurrentMediaFileHaveVideo(), \"random_video_playback\", \"random_music_playback\"), false))";
			else if (command.equals("repeat")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), !GetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), false))";
			else if (command.equals("repeatall")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), true)";
			else if (command.equals("repeatoff")) expr = "SetProperty(If(DoesCurrentMediaFileHaveVideo(), \"video_lib/repeat_playback\", \"music/repeat_playback\"), false)";
			else if (command.equals("repeatone")) expr = "XBMCPlayerControl(RepeatOne)";
		}
		else if (lcexpr.startsWith("playwith"))
			expr = expr;
		else if (lcexpr.startsWith("mute")) expr = "SetMute(!IsMuted())";
		else if (lcexpr.startsWith("setvolume")) expr = "SetVolume(1.0*" + getParen(lcexpr) + "/100)";
		else if (lcexpr.startsWith("playlist.playoffset"))
			expr = "ChannelSet(" + getParen(lcexpr) + ")";
		else if (lcexpr.startsWith("playlist.clear"))
			expr = "RemovePlaylist(GetNowPlayingList())";
		else if (lcexpr.startsWith("ejecttray"))
			expr = "SageCommand(\"Eject\")";
		else if (lcexpr.startsWith("alarmclock"))
			expr = expr;
		else if (lcexpr.startsWith("notification"))
			expr = expr;
		else if (lcexpr.startsWith("cancelalarm"))
			expr = expr;
		else if (lcexpr.startsWith("playdvd"))
		{
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "Watch(GetElement(FilterByBoolMethod(GetMediaFiles(\"D\"), \"IsDVDDrive\", true), 0))");
			WidgetFidget.contain(rv, resolveMenuWidget("fullscreenvideo"));
			return rv;
		}
		else if (lcexpr.startsWith("skin.togglesetting"))
			expr = "SetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + getAsStringConstant(getParen(expr), false) +
				", !GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getParen(expr), false) + ", false))";
		else if (lcexpr.startsWith("skin.setbool"))
			expr = "SetProperty(\"xbmc/skins/" + skinName +	"/settings/\" + " + getAsStringConstant(getParen(expr), false) + ", true)";
		else if (lcexpr.startsWith("skin.resetsettings"))
			expr = "RemovePropertyAndChildren(\"xbmc/skins/" + skinName + "/settings\")";
		else if (lcexpr.startsWith("skin.reset"))
			expr = "RemoveProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getParen(expr), false) + ")";
// Skin.theme - cycles the skin theme
		else if (lcexpr.startsWith("skin.theme"))
			expr = expr;
		else if (lcexpr.startsWith("skin.setstring"))
		{
			if (lcexpr.indexOf(',') == -1)
			{
				Widget widgy = null;
				WidgetFidget.contain(widgy = addWidgetNamed(addWidgetNamed(rv = addWidgetNamed(null, ACTION, "KBHeadingLabel = \"" + stringMap.get("1029") + "\""),
					ACTION, "KBEntryConfirmed = false"), ACTION, "KBText = GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getArg(getParen(expr), 0), true) + ", \"\")"),
					resolveMenuWidget("dialogkeyboard"));
				addWidgetNamed(addWidgetNamed(widgy, CONDITIONAL, "KBEntryConfirmed && !IsEmpty(KBText)"),
					ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getArg(getParen(expr), 0), true) + ", KBText)");
				return rv;
			}
			else
				expr = "SetProperty(\"xbmc/skins/" + skinName + "/settings/" + getAsStringConstant(getArg(getParen(expr), 0), true) + "\", " + getAsStringConstant(getArg(getParen(expr), 1), true) + ")";
		}
		else if (lcexpr.startsWith("skin.setnumeric"))
		{
			if (lcexpr.indexOf(',') == -1)
			{
				Widget widgy = null;
				WidgetFidget.contain(widgy = addWidgetNamed(addWidgetNamed(rv = addWidgetNamed(null, ACTION, "KBHeadingLabel = \"" + stringMap.get("611") + "\""),
					ACTION, "KBEntryConfirmed = false"), ACTION, "KBText = GetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getArg(getParen(expr), 0), true) + ", \"\")"),
					resolveMenuWidget("dialognumeric"));
				addWidgetNamed(addWidgetNamed(widgy, CONDITIONAL, "KBEntryConfirmed && !IsEmpty(KBText)"),
					ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " + getAsStringConstant(getArg(getParen(expr), 0), true) + ", KBText)");
				return rv;
			}
			else
				expr = "SetProperty(\"xbmc/skins/" + skinName + "/settings/" + getAsStringConstant(getArg(getParen(expr), 0), true) + "\", " + getArg(getParen(expr), 1) + ")";
		}
		else if (lcexpr.startsWith("skin.setimage(") || lcexpr.startsWith("skin.setlargeimage("))
		{
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "DirBrowseTitle = \"" + stringMap.get("1030") + "\"");
			WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(rv, ACTION, "ThumbsFileView = true"),
				ACTION, "CurrNode = GetMediaSource(\"filesystem\")"),
				ACTION, "SetNodeFilter(CurrNode, \"Pictures\", true)"), ACTION, "FileSelectMode = true"),
				ACTION, "SelectedFilePath = null"), resolveMenuWidget("filebrowser"));
			addWidgetNamed(addWidgetNamed(rv, CONDITIONAL, "SelectedFilePath != null"), ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " +
				getAsStringConstant(getParen(expr), true) + ", SelectedFilePath)");
			return rv;
		}
		else if (lcexpr.startsWith("skin.setfile("))
		{
			// Skin.SetFile(string,mask,folderpath) - pops up a general file browser dialog
			// NOTE: We need to add support for the mask (file extension filter) and folderpath (initial directory) parameters
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "DirBrowseTitle = \"" + stringMap.get("1033") + "\"");
			WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(rv, ACTION, "ThumbsFileView = false"),
				ACTION, "CurrNode = GetMediaSource(\"filesystem\")"),
				ACTION, "FileSelectMode = true"),
				ACTION, "SelectedFilePath = null"), resolveMenuWidget("filebrowser"));
			addWidgetNamed(addWidgetNamed(rv, CONDITIONAL, "SelectedFilePath != null"), ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " +
				getAsStringConstant(getArg(getParen(expr), 0), true) + ", SelectedFilePath)");
			return rv;
		}
		else if (lcexpr.startsWith("skin.setpath("))
		{
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "DirBrowseTitle = \"" + stringMap.get("1031") + "\"");
			WidgetFidget.contain(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(addWidgetNamed(rv, ACTION, "ThumbsFileView = true"),
				ACTION, "CurrNode = GetMediaSource(\"filesystem\")"),
				ACTION, "SetNodeFilter(CurrNode, \"Pictures\", true)"), ACTION, "FileSelectMode = false"),
				ACTION, "AppendNodeFilter(CurrNode, \"Directory\", true)"),
				ACTION, "SelectedFilePath = null"), resolveMenuWidget("filebrowser"));
			addWidgetNamed(addWidgetNamed(rv, CONDITIONAL, "SelectedFilePath != null"), ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/settings/\" + " +
				getAsStringConstant(expr.substring(expr.indexOf('(') + 1, expr.lastIndexOf(')')), true) + ", SelectedFilePath)");
			return rv;
		}
		else if (lcexpr.startsWith("dialog.close"))
			expr = "CloseOptionsMenu()"; // NOTE: This can have an ID or also 'all'
		else if (lcexpr.startsWith("system.logoff"))
			expr = expr;
		else if (lcexpr.startsWith("system.pwmcontrol"))
			expr = expr;
		else if (lcexpr.startsWith("backupsysteminfo"))
			expr = expr;
		else if (lcexpr.startsWith("pagedown"))
			expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + getParen(lcexpr) + "), \"Page Down\", 1)";
		else if (lcexpr.startsWith("pageup"))
			expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + getParen(lcexpr) + "), \"Page Up\", 1)";
		else if (lcexpr.startsWith("updatelibrary"))
			expr = "RunLibraryImportScan(false)";
		else if (lcexpr.startsWith("lastfm.love"))
			expr = expr;
		else if (lcexpr.startsWith("lastfm.ban"))
			expr = expr;
		else if (lcexpr.startsWith("control.move"))
			expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + getArg(getParen(lcexpr), 0) + "), If(" +
				getArg(getParen(lcexpr), 1) + " < 0, \"Up\", \"Down\"), java_lang_Math_abs(" + getArg(getParen(lcexpr), 1) + "))";
		else if (lcexpr.startsWith("container.refresh"))
			expr = "Refresh()"; // ????????????????
		else if (lcexpr.startsWith("container.update"))
			expr = "Refresh()"; // ????????????????
		else if (lcexpr.startsWith("container.nextviewmode"))
		{
			expr = "ContainerViewType = GetElement(ContainerViews, (FindElementIndex(ContainerViews, ContainerViewType) + 1) % Size(ContainerViews))";
			if (containerContext != null && containerContext.win != null)
			{
				if (containerContext.win.viewTypesSetupAction != null)
				{
					rv = mgroup.addWidget(ACTION);
					WidgetFidget.setName(rv, containerContext.win.viewTypesSetupAction);
					addWidgetNamed(addWidgetNamed(rv, ACTION, expr), ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\" + " +
						"GetNodeTypePath(CurrNode), ContainerViewType)");
					return rv;
				}
				else
				{
					rv = mgroup.addWidget(ACTION);
					WidgetFidget.setName(rv, expr);
					addWidgetNamed(rv, ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\" + " +
						"GetNodeTypePath(CurrNode), ContainerViewType)");
					return rv;
				}
			}
		}
		else if (lcexpr.startsWith("container.previousviewmode"))
		{
			expr = "ContainerViewType = GetElement(ContainerViews, (FindElementIndex(ContainerViews, ContainerViewType) - 1 + Size(ContainerViews)) % Size(ContainerViews))";
			if (containerContext != null && containerContext.win != null)
			{
				if (containerContext.win.viewTypesSetupAction != null)
				{
					rv = mgroup.addWidget(ACTION);
					WidgetFidget.setName(rv, containerContext.win.viewTypesSetupAction);
					addWidgetNamed(addWidgetNamed(rv, ACTION, expr), ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\" + " +
						"GetNodeTypePath(CurrNode), ContainerViewType)");
					return rv;
				}
				else
				{
					rv = mgroup.addWidget(ACTION);
					WidgetFidget.setName(rv, expr);
					addWidgetNamed(rv, ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\" + " +
						"GetNodeTypePath(CurrNode), ContainerViewType)");
					return rv;
				}
			}
		}
		else if (lcexpr.startsWith("container.setviewmode"))
		{
			expr = "ContainerViewType = GetVariableFromContext(\"XBMCID\", " + getParen(expr) + ", \"ThisViewType\")";
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, expr);
			if (containerContext != null && containerContext.win != null && containerContext.win.menuName.equalsIgnoreCase("mytv"))
				addWidgetNamed(rv, ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\", ContainerViewType)");
			else
				addWidgetNamed(rv, ACTION, "SetProperty(\"xbmc/skins/" + skinName + "/views/" + containerContext.win.menuName + "/\" + " +
					"GetNodeTypePath(CurrNode), ContainerViewType)");
			return rv;
		}
		else if (lcexpr.startsWith("container.nextsortmethod"))
		{
			expr = "NewSortMethod = GetElement(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), " +
				"(FindElementIndex(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), GetNodeSortTechnique(CurrNode)) + 1) % Size(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods)))";
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, expr);
			addWidgetNamed(rv, ACTION, "SetNodeSort(CurrNode, NewSortMethod, IsNodeSortAscending(CurrNode))");
			return rv;
		}
		else if (lcexpr.startsWith("container.previoussortmethod"))
		{
			expr = "NewSortMethod = GetElement(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), " +
				"(FindElementIndex(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods), GetNodeSortTechnique(CurrNode)) - 1 + Size(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods))) % Size(If(CurrNode == null, ContainerSortMethodsTop, ContainerSortMethods)))";
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, expr);
			addWidgetNamed(rv, ACTION, "SetNodeSort(CurrNode, NewSortMethod, IsNodeSortAscending(CurrNode))");
			return rv;
		}
		else if (lcexpr.startsWith("container.setsortmethod("))
		{
			String sortType = expr.substring(expr.indexOf('(') + 1, expr.lastIndexOf(')'));
			String sortName = getSortMethod(sortType);
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "SetNodeSort(CurrNode, \"" + sortName + "\", IsNodeSortAscending(CurrNode))");
			return rv;
		}
		else if (lcexpr.startsWith("container.sortdirection()"))
		{
			rv = mgroup.addWidget(ACTION);
			WidgetFidget.setName(rv, "SetNodeSort(CurrNode, GetNodeSortTechnique(CurrNode), !IsNodeSortAscending(CurrNode))");
			return rv;
		}
		else if (lcexpr.startsWith("control.message"))
		{
			String controlID = getArg(getParen(lcexpr), 0);
			String command = getArg(getParen(lcexpr), 1);
// NOTE: WE'RE IGNORING THE WINDOW ID IN Control.Message SINCE WE DON'T KNOW THE PURPOSE OF IT YET, it's in $3
			String windowID = getArg(getParen(lcexpr), 2);
			if (command.equals("moveup"))
				expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Up\", 1)";
			else if (command.equals("movedown"))
				expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Down\", 1)";
			else if (command.equals("pageup"))
				expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Page Up\", 1)";
			else if (command.equals("pagedown"))
				expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Page Down\", 1)";
			else if (command.equals("click"))
				expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Select\", 1)";
		}
		else if (lcexpr.startsWith("sendclick"))
		{
			String controlID = getArg(getParen(lcexpr), 0);
// NOTE: WE'RE IGNORING THE WINDOW ID IN Control.Message SINCE WE DON'T KNOW THE PURPOSE OF IT YET, it's in $3
			String windowID = getArg(getParen(lcexpr), 1);
			expr = "SendEventToUIComponent(GetUIComponentForVariable(\"XBMCID\", " + controlID + "), \"Select\", 1)";
		}
		else if (lcexpr.startsWith("action")) // Window ID is the second argument....don't konw what to do with it yet
			expr = "SageCommand(\"" + getArg(getParen(lcexpr), 0) + "\")";
		else if (lcexpr.startsWith("setproperty"))
			expr = "XBMCSetProperty(" + getParen(lcexpr) + ")"; // NOT sure about what this is yet
		// These are all the actions that we can get as commands as well
		else if (lcexpr.equals("left"))
			expr = "SageCommand(\"Left\")";
		else if (lcexpr.equals("right"))
			expr = "SageCommand(\"Right\")";
		else if (lcexpr.equals("up"))
			expr = "SageCommand(\"Up\")";
		else if (lcexpr.equals("down"))
			expr = "SageCommand(\"Down\")";
		else if (lcexpr.equals("pageup"))
			expr = "SageCommand(\"Page Up\")";
		else if (lcexpr.equals("pagedown"))
			expr = "SageCommand(\"Page Down\")";
		else if (lcexpr.equals("select"))
			expr = "SageCommand(\"Select\")";
		else if (lcexpr.equals("highlight"))
			expr = "HighlightCmd()";
		else if (lcexpr.equals("parentdir"))
			expr = "SageCommand(\"-\")";
		else if (lcexpr.equals("previousmenu"))
		{
			if (containerContext != null && containerContext.win != null && containerContext.win.menuWidget.type() == OPTIONSMENU)
				expr = "CloseOptionsMenu()";
			else
				expr = "SageCommand(\"Back\")";
		}
		else if (lcexpr.equals("info"))
			expr = "SageCommand(\"Info\")";
		else if (lcexpr.equals("pause"))
			expr = "SageCommand(\"Pause\")";
		else if (lcexpr.equals("stop"))
			expr = "SageCommand(\"Stop\")";
		else if (lcexpr.equals("skipnext"))
			expr = "SageCommand(\"Channel Up\")";
		else if (lcexpr.equals("skipprevious"))
			expr = "SageCommand(\"Channel Down\")";
		else if (lcexpr.equals("fullscreen"))
			expr = "SageCommand(\"Full Screen\")";
		else if (lcexpr.equals("aspectratio"))
			expr = "SageCommand(\"Aspect Ratio Toggle\")";
		else if (lcexpr.equals("stepforward"))
			expr = "SageCommand(\"Skip Fwd/Page Right\")";
		else if (lcexpr.equals("stepback"))
			expr = "SageCommand(\"Skip Bkwd/Page Left\")";
		else if (lcexpr.equals("bigstepforward"))
			expr = "SageCommand(\"Skip Fwd #2\")";
		else if (lcexpr.equals("bigstepback"))
			expr = "SageCommand(\"Skip Bkwd #2\")";
		else if (lcexpr.equals("osd"))
			expr = "OSDCmd()";
		else if (lcexpr.equals("showsubtitles"))
			expr = "SageCommand(\"DVD Subtitle Toggle\")";
		else if (lcexpr.equals("nextsubtitle"))
			expr = "SageCommand(\"DVD Subtitle Change\")";
		else if (lcexpr.equals("codecinfo"))
			expr = "CodecInfoCmd()";
		else if (lcexpr.equals("nextpicture"))
			expr = "SageCommand(\"Up\")";
		else if (lcexpr.equals("previouspicture"))
			expr = "SageCommand(\"Down\")";
		else if (lcexpr.equals("zoomout"))
			expr = "SageCommand(\"Page Down\")";
		else if (lcexpr.equals("zoomin"))
			expr = "SageCommand(\"Page Up\")";
		else if (lcexpr.equals("playlist"))
			expr = "PlaylistCmd()";
		else if (lcexpr.equals("queue"))
			expr = "QueueCmd()";
		else if (lcexpr.equals("zoomnormal"))
			expr = "ZoomNormalCmd()";
		else if (lcexpr.startsWith("zoomlevel"))
			expr = "ZoomLevel" + lcexpr.substring(9) + "Cmd()";
		else if (lcexpr.equals("nextcalibration"))
			expr = "NextCalibrationCmd()";
		else if (lcexpr.equals("resetcalibration"))
			expr = "ResetCalibrationCmd()";
		else if (lcexpr.equals("analogmove"))
			expr = "AnalogMoveCmd()";
		else if (lcexpr.equals("rotate"))
			expr = "RotateCmd()";
		else if (lcexpr.equals("close"))
			expr = "CloseOptionsMenu()";
		else if (lcexpr.equals("subtitledelayminus"))
			expr = "SetSubtitleDelay(GetSubtitleDelay() - 25)";
		else if (lcexpr.equals("subtitledelay"))
			expr = "SubtitleDelayCmd()";
		else if (lcexpr.equals("subtitledelayplus"))
			expr = "SetSubtitleDelay(GetSubtitleDelay() + 25)";
		else if (lcexpr.equals("audiodelayminus"))
			expr = "SetAudioDelay(GetAudioDelay() - 25)";
		else if (lcexpr.equals("audiodelay"))
			expr = "AudioDelayCmd()";
		else if (lcexpr.equals("audiodelayplus"))
			expr = "SetAudioDelay(GetAudioDelay() + 25)";
		else if (lcexpr.equals("audionextlanguage"))
			expr = "SageCommand(\"DVD Audio Change\")";
		else if (lcexpr.equals("nextresolution"))
			expr = "SageCommand(\"Video Output\")";
		else if (lcexpr.equals("audiotoggledigital"))
			expr = "AudioToggleDigitalCmd()";
		else if (lcexpr.equals("number0"))
			expr = "SageCommand(\"Num 0\")";
		else if (lcexpr.equals("number1"))
			expr = "SageCommand(\"Num 1\")";
		else if (lcexpr.equals("number2"))
			expr = "SageCommand(\"Num 2\")";
		else if (lcexpr.equals("number3"))
			expr = "SageCommand(\"Num 3\")";
		else if (lcexpr.equals("number4"))
			expr = "SageCommand(\"Num 4\")";
		else if (lcexpr.equals("number5"))
			expr = "SageCommand(\"Num 5\")";
		else if (lcexpr.equals("number6"))
			expr = "SageCommand(\"Num 6\")";
		else if (lcexpr.equals("number7"))
			expr = "SageCommand(\"Num 7\")";
		else if (lcexpr.equals("number8"))
			expr = "SageCommand(\"Num 8\")";
		else if (lcexpr.equals("number9"))
			expr = "SageCommand(\"Num 9\")";
		else if (lcexpr.equals("osdleft"))
			expr = "OSDLeftCmd()";
		else if (lcexpr.equals("osdright"))
			expr = "OSDRightCmd()";
		else if (lcexpr.equals("osdup"))
			expr = "OSDUpCmd()";
		else if (lcexpr.equals("osddown"))
			expr = "OSDDownCmd()";
		else if (lcexpr.equals("osdselect"))
			expr = "OSDSelectCmd()";
		else if (lcexpr.equals("osdvalueplus"))
			expr = "OSDValuePlusCmd()";
		else if (lcexpr.equals("osdvalueminus"))
			expr = "OSDValueMinusCmd()";
		else if (lcexpr.equals("smallstepback"))
			expr = "SmallStepBackCmd()";
		else if (lcexpr.equals("fastforward"))
			expr = "SageCommand(\"Smooth Fast Forward\")";
		else if (lcexpr.equals("rewind"))
			expr = "SageCommand(\"Smooth Rewind\")";
		else if (lcexpr.equals("play"))
			expr = "SageCommand(\"Play\")";
		else if (lcexpr.equals("delete"))
			expr = "SageCommand(\"Delete\")";
		else if (lcexpr.equals("copy"))
			expr = "CopyCmd()";
		else if (lcexpr.equals("move"))
			expr = "MoveCmd()";
		else if (lcexpr.equals("mplayerosd"))
			expr = "MPlayerOSDCmd()";
		else if (lcexpr.equals("hidesubmenu"))
			expr = "HideSubmenuCmd()";
		else if (lcexpr.equals("screenshot"))
			expr = "ScreenshotCmd()";
		else if (lcexpr.equals("poweroff"))
			expr = "SageCommand(\"Power Off\")";
		else if (lcexpr.equals("rename"))
			expr = "RenameCmd()";
		else if (lcexpr.equals("togglewatched"))
			expr = "SageCommand(\"Watched\")";
		else if (lcexpr.equals("scanitem"))
			expr = "ScanItemCmd()";
		else if (lcexpr.equals("reloadkeymaps"))
			expr = "ReloadKeymapsCmd()";
		else if (lcexpr.equals("volumeup"))
			expr = "SageCommand(\"Volume Up\")";
		else if (lcexpr.equals("volumedown"))
			expr = "SageCommand(\"Volume Down\")";
		else if (lcexpr.equals("mute"))
			expr = "SageCommand(\"Mute\")";
		else if (lcexpr.equals("backspace"))
			expr = "Keystroke(\"\\b\", false)";
		else if (lcexpr.equals("scrollup"))
			expr = "SageCommand(\"Page Up\")";
		else if (lcexpr.equals("scrolldown"))
			expr = "SageCommand(\"Page Down\")";
		else if (lcexpr.equals("analogfastforward"))
			expr = "SageCommand(\"Smooth Fast Forward\")";
		else if (lcexpr.equals("analogrewind"))
			expr = "SageCommand(\"Smooth Rewind\")";
		else if (lcexpr.equals("moveitemup"))
			expr = "SageCommand(\"Up\")";
		else if (lcexpr.equals("moveitemdown"))
			expr = "SageCommand(\"Down\")";
		else if (lcexpr.equals("contextmenu"))
			expr = "SageCommand(\"Options\")";
		else if (lcexpr.equals("shift"))
			expr = "ShiftCmd()";
		else if (lcexpr.equals("symbols"))
			expr = "SymbolsCmd()";
		else if (lcexpr.equals("cursorleft"))
			expr = "CursorLeftCmd()";
		else if (lcexpr.equals("cursorright"))
			expr = "CursorRightCmd()";
		else if (lcexpr.equals("showtime"))
			expr = "ShowTimeCmd()";
		else if (lcexpr.equals("analogseekforward"))
			expr = "SageCommand(\"Skip Fwd/Page Right\")";
		else if (lcexpr.equals("analogseekback"))
			expr = "SageCommand(\"Skip Bkwd/Page Left\")";
		else if (lcexpr.equals("showpreset"))
			expr = "ShowPresetCmd()";
		else if (lcexpr.equals("presetlist"))
			expr = "PresetListCmd()";
		else if (lcexpr.equals("nextpreset"))
			expr = "NextPresetCmd()";
		else if (lcexpr.equals("previouspreset"))
			expr = "PreviousPresetCmd()";
		else if (lcexpr.equals("lockpreset"))
			expr = "LockPresetCmd()";
		else if (lcexpr.equals("randompreset"))
			expr = "RandomPresetCmd()";
		else if (lcexpr.equals("increasevisrating"))
			expr = "IncreaseVisRatingCmd()";
		else if (lcexpr.equals("decreasevisrating"))
			expr = "DecreaseVisRatingCmd()";
		else if (lcexpr.equals("showvideomenu"))
			expr = "ShowVideoMenuCmd()";
		else if (lcexpr.equals("enter"))
			expr = "Keystroke(\"Enter\", false)";
		else if (lcexpr.equals("increaserating"))
			expr = "IncreaseRatingCmd()";
		else if (lcexpr.equals("decreaserating"))
			expr = "DecreaseRatingCmd()";
		else if (lcexpr.equals("togglefullscreen"))
			expr = "SageCommand(\"Full Screen\")";
		else if (lcexpr.equals("nextscene"))
			expr = "SageCommand(\"DVD Next Chapter\")";
		else if (lcexpr.equals("previousscene"))
			expr = "SageCommand(\"DVD Prev Chapter\")";
		else if (lcexpr.equals("nextletter"))
			expr = "NextLetterCmd()";
		else if (lcexpr.equals("prevletter"))
			expr = "PrevLetterCmd()";
		else if (lcexpr.startsWith("jumpsms"))
			expr = "JumpSMS" + lcexpr.substring(7) + "Cmd()";
		else if (lcexpr.equals("filterclear"))
			expr = "FilterClearCmd()";
		else if (lcexpr.startsWith("filtersms"))
			expr = "FilterSMS" + lcexpr.substring(7) + "Cmd()";
		else if (lcexpr.equals("firstpage"))
			expr = "FirstPageCmd()";
		else if (lcexpr.equals("lastpage"))
			expr = "LastPageCmd()";
		else
		{
			System.out.println("ERROR UNDEFINED built-in function of:" + expr);
		}
		// For the simple single expression case
		rv = mgroup.addWidget(ACTION);
		WidgetFidget.setName(rv, expr);
		return rv;
	}

	public Widget addFlipAndBGEffect(Widget parentWidg, TextureInfo ti)
	{
		if (ti.backgroundLoad)
			WidgetFidget.setProperty(parentWidg, BACKGROUND_LOAD, "true");
		if (!ti.flipx && !ti.flipy) return null;
		Widget rv = addWidgetNamed(parentWidg, EFFECT, "Flip");
		WidgetFidget.setProperty(rv, EFFECT_TRIGGER, STATIC_EFFECT);
		if (ti.flipx)
			WidgetFidget.setProperty(rv, RENDER_SCALE_X, "-1.0");
		if (ti.flipy)
			WidgetFidget.setProperty(rv, RENDER_SCALE_Y, "-1.0");
		return rv;
	}

	private boolean setupItemLayouts(Control cont, Widget itemLayoutWidg, Vector itemLayouts)
	{
		WidgetFidget.setProperty(itemLayoutWidg, FIXED_WIDTH, "1.0");
		WidgetFidget.setProperty(itemLayoutWidg, FIXED_HEIGHT, "1.0");
		Widget priorBranch = null;
		String condHeightPrefix = null, condHeightSuffix = null;
		String condWidthPrefix = null, condWidthSuffix = null;
		// NOTE: I *think* that if there's no kids for an item layout then it uses zero size; that would
		// explain the header in the MediaStream Settings menu and why its still the first element when it has focusposition=1
		boolean hadKids = false;
		for (int i = 0; i < itemLayouts.size(); i++)
		{
			ItemLayout lay = (ItemLayout) itemLayouts.get(i);
			Widget currLayWidg = mgroup.addWidget(PANEL);
			if (lay.width != null)
				WidgetFidget.setProperty(currLayWidg, FIXED_WIDTH, resolveProperty(lay.width));
			else
				WidgetFidget.setProperty(currLayWidg, FIXED_WIDTH, "1.0");
			if (lay.height != null)
				WidgetFidget.setProperty(currLayWidg, FIXED_HEIGHT, resolveProperty(lay.height));
			else
				WidgetFidget.setProperty(currLayWidg, FIXED_HEIGHT, "1.0");
			for (int j = 0; lay.kids != null && j < lay.kids.size(); j++)
			{
				hadKids = true;
				Control kiddie = (Control) lay.kids.get(j);
				if (kiddie.targetParent != null)
					WidgetFidget.contain(currLayWidg, kiddie.targetParent);
				else if (kiddie.widg != null)
					WidgetFidget.contain(currLayWidg, kiddie.widg);
			}
			if (lay.condition == null)
			{
				// We also set the top level size since that's used to determine table dimensions
				if (lay.width != null)
					WidgetFidget.setProperty(itemLayoutWidg, FIXED_WIDTH, resolveProperty(lay.width));
				if (lay.height != null)
					WidgetFidget.setProperty(itemLayoutWidg, FIXED_HEIGHT, resolveProperty(lay.height));
				if (priorBranch == null)
				{
					WidgetFidget.contain(itemLayoutWidg, currLayWidg);
					break;
				}
				else
				{
					WidgetFidget.contain(priorBranch, currLayWidg);
					break;
				}
			}
			else
			{
				Widget topCond = mgroup.addWidget(CONDITIONAL);
				WidgetFidget.setName(topCond, translateBooleanExpression(lay.condition, cont));
				Widget trueBranch = mgroup.addWidget(BRANCH);
				WidgetFidget.setName(trueBranch, "true");
				Widget elseBranch = mgroup.addWidget(BRANCH);
				WidgetFidget.setName(elseBranch, "else");
				WidgetFidget.contain(priorBranch == null ? itemLayoutWidg : priorBranch, topCond);
				WidgetFidget.contain(topCond, trueBranch);
				WidgetFidget.contain(topCond, elseBranch);
				priorBranch = elseBranch;
				WidgetFidget.contain(trueBranch, currLayWidg);
				if (lay.height != null)
				{
					if (condHeightPrefix == null)
						condHeightPrefix = "=If(" + topCond.getName() + ", " + resolveProperty(lay.height) + ", ";
					else
						condHeightPrefix += "If(" + topCond.getName() + ", " + resolveProperty(lay.height) + ", ";
					if (condHeightSuffix == null)
						condHeightSuffix = ")";
					else
						condHeightSuffix = ")" + condHeightSuffix;
				}
				if (lay.width != null)
				{
					if (condWidthPrefix == null)
						condWidthPrefix = "=If(" + topCond.getName() + ", " + resolveProperty(lay.width) + ", ";
					else
						condWidthPrefix += "If(" + topCond.getName() + ", " + resolveProperty(lay.width) + ", ";
					if (condWidthSuffix == null)
						condWidthSuffix = ")";
					else
						condWidthSuffix = ")" + condWidthSuffix;
				}
			}
		}
		if (condWidthPrefix != null)
		{
			// 100 is arbitrary; this value should never get returned in reality or their conditionals are wrong
			WidgetFidget.setProperty(itemLayoutWidg, FIXED_WIDTH, condWidthPrefix + "100" + condWidthSuffix);
		}
		if (condHeightPrefix != null)
		{
			// 100 is arbitrary; this value should never get returned in reality or their conditionals are wrong
			WidgetFidget.setProperty(itemLayoutWidg, FIXED_HEIGHT, condHeightPrefix + "100" + condHeightSuffix);
		}
		return hadKids;
	}

	private void adjustChildWidths(Widget itemLayoutWidg, int timeblocks)
	{
		adjustChildWidths(itemLayoutWidg, timeblocks, new java.util.HashSet());
	}
	private void adjustChildWidths(Widget itemLayoutWidg, int timeblocks, java.util.HashSet doneWidgs)
	{
		if (itemLayoutWidg.hasProperty(FIXED_WIDTH))
		{
			String oldWidth = itemLayoutWidg.getProperty(FIXED_WIDTH);
			if (oldWidth.startsWith("="))
				WidgetFidget.setProperty(itemLayoutWidg, FIXED_WIDTH, "=(" + oldWidth.substring(1) + ")/" + timeblocks + ".0");
			else
				WidgetFidget.setProperty(itemLayoutWidg, FIXED_WIDTH, "=" + oldWidth + "/" + timeblocks + ".0");
		}
		Widget[] kids = itemLayoutWidg.contents();
		for (int i = 0; i < kids.length; i++)
		{
			if (doneWidgs.add(kids[i]))
				adjustChildWidths(kids[i], timeblocks, doneWidgs);
		}
	}

	String defaultResolution;
	String defaultResolutionWide;
	String defaultTheme;
	float effectslowdown;
	String version;
	float zoom;
	String skinName;
	String skinCredits;

	java.util.Map windowIdToNameMap = new java.util.HashMap();
	java.util.Map constantsMap = new java.util.HashMap();
	java.util.Map includeNameToNodeListMap = new java.util.HashMap(); // String->NodeList
	java.util.Map defaultControlIncludes = new java.util.HashMap(); // String->NodeList

	public class AnimData
	{
		public AnimData(){}
		public String trigger;
		public String effect;
		public long time;
		public long delay;
		public int[] start; // [1] or [2]
		public int[] end; // [1] or [2]
		public String acceleration;
		public int[] center; // [2]
		public String condition;
		public boolean reversible = true;
		public boolean pulse;
		public String tween;
		public String easing;
		// We do this for any effect except for Zooms that have a center specified
		public String getUniqueKey()
		{
			if ("zoom".equals(effect) && center != null && center.length > 0)
				return null;
			return trigger + effect + time + "x" + delay + reversible + pulse + tween + easing + (start == null ? "null" : (start.length == 1 ? Integer.toString(start[0]) : (start[0] + "x" + start[1]))) +
				(end == null ? "null" : (end.length == 1 ? Integer.toString(end[0]) : (end[0] + "x" + end[1])));
		}
	}

	public class FontData
	{
		public FontData() {}
		public String fontName;
		public String fontPath;
		public String size;
		public String style;
	}
	java.util.Map fontMap = new java.util.HashMap(); // String->FontData
	java.util.Map stringMap = new java.util.HashMap();
	java.util.Map colorMap = new java.util.HashMap();

	tv.sage.ModuleGroup mgroup;
	Widget dialogOrganizer;
	File skinDir;
	File defaultResDir;
	String rezWidth = "720";
	String rezHeight = "576";

	Widget viewValidationRoot;

	Widget moveFocusChainSrc = null;
	Widget moveFocusChainContainerSrc = null;

	java.util.Map animWidgMap = new java.util.HashMap();

	Widget menuTheme;

	java.util.Map windowIDMap = new java.util.HashMap(); // String(id)->String(name)
	java.util.Map dialogIDMap = new java.util.HashMap(); // String(id)->String(name)
	// dialog aliases are also in WindowID map
	java.util.Map windowAliasMap = new java.util.HashMap(); // String->String (alternate window names converted to XML filename)
	java.util.Map windowNameToIDMap = new java.util.HashMap(); // String(name)->String(id)
	java.util.Map dialogNameToIDMap = new java.util.HashMap(); // String(name)->String(id)

	java.util.Map windowWidgMap = new java.util.HashMap(); // String(name)->Widget
	java.util.Map dialogWidgMap = new java.util.HashMap(); // String(name)->Widget

	java.util.Set mediaWinIDs = new java.util.HashSet();
	java.util.Map widgToWindowObjMap = new java.util.HashMap(); // Widget->Window

	java.util.Map winNameToFilenameMap = new java.util.HashMap();

	String mediaPath = "media";
	java.util.Map defaultImageMap = new java.util.HashMap();
}
