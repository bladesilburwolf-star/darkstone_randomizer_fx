@echo off
setlocal
cd /d "%~dp0"

set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "JFX_LIB=%JFX_HOME%\lib"
set "JFX_BIN=%JFX_HOME%\bin"

if not exist "out-pc\com\serifsystemworks\darkstone\DarkstonePcApp.class" (
    call "%~dp0build-pc.bat"
    if errorlevel 1 exit /b 1
)

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo [!] JavaFX not found at %JFX_LIB%
    echo     Edit JFX_HOME in run-pc.bat / build-pc.bat
    exit /b 1
)

echo Launching Darkstone PC Randomizer...
java -Xmx1G ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -Djava.library.path="%JFX_BIN%" ^
  -cp out-pc ^
  com.serifsystemworks.darkstone.DarkstonePcApp %*
endlocal
