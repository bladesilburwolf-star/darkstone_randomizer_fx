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

if not exist out mkdir out

echo Compiling Darkstone PSX Randomizer ^(JavaFX^)...
javac --release 21 -encoding UTF-8 ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -d out ^
  src\com\serifsystemworks\darkstone\DarkstoneApp.java ^
  src\com\serifsystemworks\darkstone\engine\*.java ^
  src\com\serifsystemworks\darkstone\ui\*.java
if errorlevel 1 (
    echo [!] Compilation failed.
    exit /b 1
)

if not exist "out\com\serifsystemworks\darkstone\ui" mkdir "out\com\serifsystemworks\darkstone\ui"
copy /Y "src\com\serifsystemworks\darkstone\ui\theme.css" "out\com\serifsystemworks\darkstone\ui\theme.css" >nul

echo [OK] Build complete. Output in .\out
endlocal
