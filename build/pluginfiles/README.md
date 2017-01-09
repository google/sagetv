# Build Scripts for Legacy SageTV UIs

The `build.xml` script will package any `stv` defined in the `stvs` folder and create a valid SageTV 9 STV package for it.

```
# ant -Dstv.name=SageTV3
```

Will create the `SageTV3-9.0.zip` theme package and xml in the `pluginrelease` folder.

To upload the theme package to `BinTray` you can use the `plugin-uploader.gradle` script in the root of the project.

```
# ./gradlew -b plugin-uploader.gradle -Dstv.name=SageTV3 bintrayUpload
```

These legacy SageTV UIs are not expected to change, so they are not a part of the standard build/deployment process.