@echo off
pushd ..\..\..
call gradlew.bat %1
set "gradleExitCode=%ERRORLEVEL%"
popd
exit /b %gradleExitCode%