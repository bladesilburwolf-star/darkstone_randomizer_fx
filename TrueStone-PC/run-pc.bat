@echo off
setlocal
if not defined JFX_HOME set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "PATH_TO_FX=%JFX_HOME%\lib"
java --module-path "%PATH_TO_FX%" --add-modules javafx.controls,javafx.graphics,javafx.base ^
  -cp out-pc com.serifsystemworks.darkstone.DarkstonePcApp
endlocal
