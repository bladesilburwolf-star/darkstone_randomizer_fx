@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "JFX_LIB=%JFX_HOME%\lib"
set "LOG=%~dp0build_error.log"

if exist "%LOG%" del "%LOG%"

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo [!] JavaFX not found at %JFX_LIB%
    echo [!] JavaFX not found at %JFX_LIB% > "%LOG%"
    exit /b 1
)

if not exist out mkdir out

echo Compiling Darkstone PSX Randomizer (JavaFX)...
echo Compiling Darkstone PSX Randomizer (JavaFX)... > "%LOG%"
echo Timestamp: %DATE% %TIME% >> "%LOG%"
echo. >> "%LOG%"

javac --release 21 -encoding UTF-8 ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -d out ^
  src\com\serifsystemworks\darkstone\config\*.java ^
  src\com\serifsystemworks\darkstone\math\*.java ^
  src\com\serifsystemworks\darkstone\engine\*.java ^
  src\com\serifsystemworks\psxdisc\*.java ^
  src\com\serifsystemworks\darkstone\ui\MainView.java ^
  src\com\serifsystemworks\darkstone\DarkstoneApp.java ^
  1>>"%LOG%" 2>>&1

if errorlevel 1 (
    echo.
    echo [!] Compilation FAILED — see build_error.log
    echo.
    type "%LOG%"
    echo.
    echo Full log saved to: %LOG%
    exit /b 1
)

if not exist "out\com\serifsystemworks\darkstone\ui" mkdir "out\com\serifsystemworks\darkstone\ui"
copy /Y "src\com\serifsystemworks\darkstone\ui\theme.css" "out\com\serifsystemworks\darkstone\ui\theme.css" >nul

echo [OK] Build complete. Output in .\out
echo [OK] Build complete. Output in .\out >> "%LOG%"
endlocal
