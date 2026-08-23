@echo off
setlocal
cd /d "%~dp0"

set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "JFX_LIB=%JFX_HOME%\lib"
set "JFX_BIN=%JFX_HOME%\bin"

if not exist "out\com\serifsystemworks\darkstone\DarkstoneApp.class" (
    call "%~dp0build.bat"
    if errorlevel 1 exit /b 1
)

echo Launching Darkstone PSX Randomizer with 12G heap...
java -Xmx12G -Xms2G ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -Djava.library.path="%JFX_BIN%" ^
  -cp out ^
  com.serifsystemworks.darkstone.DarkstoneApp %*
endlocal
