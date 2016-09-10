# Developing Plugins for SageTV 9

For creating plugins and understanding the Plugin manifest, lifecycle, etc, checkout the [Developing SageTV Plugins](https://forums.sagetv.com/forums/showthread.php?t=51741) document.

## Adding Plugins to SageTV at Development Time
When you are developing Plugins, you can add them to the `SAGE_HOME/SageTVPluginsDev.xml` file (V7 compatible) or you can use the new V9 model that uses the `SAGE_HOME/SageTVPluginsDev.d/` directory.

To use the `SAGE_HOME/SageTVPluginsDev.d/` you will need to set `devmode=true`
 in the `Sage.properties`.

SageTV will load the plugins from the .xml file and the directory, by loading the xml file first, and then loading the directory based plugins.

The advantage of using the directory approach is that you can simply drop your plugin manifest xml AND your plugin zip files in the directory.  When SageTV loads the plugins from that directory it will update the plugin manifest and version numbers so that SageTV will always think it is newer and can install it.

Plugin resolver will look for filenames in the `SageTVPluginsDev.d/` dir when downloading and installing plugins.  ie, if your published url is something like `http://bintray/opensagetv/plugins/myplugin-1.0.zip` then when loading the xml from the `SAGE_HOME/SageTVPluginsDev.d/` directory, SageTV will check if a filename,  `myplugin-1.0.zip` exists, and if it does exist, SageTV will rewrite the download url to be a file reference and install it from that local location.  No need to run your http server and no need to publish your file to get sagetv to install it.
