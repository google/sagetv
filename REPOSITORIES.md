# SageTV Plugin Repositories

The SageTV Open Source version contains support for multiple repositories.  Out of the box, SageTV will continue to work with V7 repositories, but, if you are creating content for the Open Source version (also referred to as the V9 version) then you can also utilize an Open Source repository for those plugins.

The V9 repository extends the V7 repository, so, you can update and override V7 plugins, in the V9 repository, if that update takes advantage of V9 specific features.  This ensures that people that are remaining on V7 will not be affected by the V9 updates.

If your update, or new plugin, can work in V7 and does not require any V9 specific features, then you can still publish your plugin, in the usual way to the V7 repository.

## Publishing V7 Plugins
Creating and publishing V7 Plugins is covered by the [SageTV Plugin Developer Document](http://download.sagetv.com/DevelopingSageTVPlugins.doc)

## Publishing V9 Plugins
Creating a V9 plugin is the same as V7, but publishing the plugin, is covered by the [V9 Plugin Repo README](https://github.com/OpenSageTV/sagetv-plugin-repo)

## Using Additional Repositories
The plugin framework in V9 has been extended to allow users to add new Plugin Repositories.  To do this, ```Sage.properties``` needs to updated, and SageTV server needs to be stopped when editing this file, manually.

If you look in the Sage.properties, after updating the latest SageTV V9 version, you'll see that the Plugin Repositories are simply defined as properties in the Sage.properties file.

```
sagetv_repos/v7/local=SageTVPlugins.xml
sagetv_repos/v7/md5=http\://download.sagetv.com/SageTVPlugins.md5.txt
sagetv_repos/v7/url=http\://download.sagetv.com/SageTVPlugins.xml
sagetv_repos/v9/local=SageTVPluginsV9.xml
sagetv_repos/v9/md5=https\://raw.githubusercontent.com/OpenSageTV/sagetv-plugin-repo/master/SageTVPluginsV9.md5
sagetv_repos/v9/url=https\://raw.githubusercontent.com/OpenSageTV/sagetv-plugin-repo/master/SageTVPluginsV9.xml
```

You'll notice that each repository is listed under the ```sagetv_repos``` parent key, and the child, ie, ```v7``` or ```v9``` is the repository ID, there are 3 child keys under that, ```local```, ```md5```, and ```url```.  Each repository has an external URL and an external MD5 url where the MD5 URL should contain the MD5 value for the current URL.  SageTV uses this value to determine if it needs to re-download and update the local plugin file.

You can add your own repositories to SageTV V9.  You might do this to provide a BETA repository list for a plugin you are working on, and it might be easier than deploying a ```SageTVPluginsDev.xml``` file.

To add a new repository, you'll need to use a new repository id (```myrepo``` in this case), and add the 3 entries for your repository.  So, something like this.

```
sagetv_repos/myrepo/local=SageTVPluginsMYREPO.xml
sagetv_repos/myrepo/md5=http\://some_other_domain/SageTVPlugins.md5.txt
sagetv_repos/myrepo/url=http\://some_other_domain/SageTVPlugins.xml
```

And, you'll need to update the ```sagetv_additional_repo_list``` property and add your repo ID.
```
sagetv_additional_repo_list=myrepo
```

Restart SageTV, and if all is well, then you should see your local REPO file in the same location as the SageTVPlugins.xml.
