@echo off
setlocal
cd /d "%~dp0"

set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "JFX_LIB=%JFX_HOME%\lib"
if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo [!] JavaFX not found at %JFX_LIB%
    echo     Install JavaFX 26 SDK or edit JFX_HOME in this script.
    exit /b 1
)

if not exist out-pc mkdir out-pc

echo Compiling Darkstone PC Randomizer ^(JavaFX^)...
javac --release 21 -encoding UTF-8 ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -d out-pc ^
  src\com\serifsystemworks\darkstone\DarkstonePcApp.java ^
  src\com\serifsystemworks\darkstone\engine\LogSink.java ^
  src\com\serifsystemworks\darkstone\pc\*.java ^
  src\com\serifsystemworks\darkstone\ui\PcMainView.java
if errorlevel 1 (
    echo [!] Compilation failed.
    exit /b 1
)

if not exist "out-pc\com\serifsystemworks\darkstone\ui" mkdir "out-pc\com\serifsystemworks\darkstone\ui"
copy /Y "src\com\serifsystemworks\darkstone\ui\theme.css" "out-pc\com\serifsystemworks\darkstone\ui\theme.css" >nul

echo [OK] PC build complete. Output in .\out-pc
echo Run with run-pc.bat
endlocal
