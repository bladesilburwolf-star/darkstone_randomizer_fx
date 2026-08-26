@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JFX_HOME=C:\Program Files\Java\javafx-sdk-26.0.2"
set "JFX_LIB=%JFX_HOME%\lib"
set "LOG=%~dp0build_pc_error.log"

if exist "%LOG%" del "%LOG%"

if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo [!] JavaFX not found at %JFX_LIB%
    echo [!] JavaFX not found at %JFX_LIB% > "%LOG%"
    exit /b 1
)

if not exist out-pc mkdir out-pc

echo Compiling Darkstone PC Randomizer (JavaFX)...
echo Compiling Darkstone PC Randomizer (JavaFX)... > "%LOG%"
echo Timestamp: %DATE% %TIME% >> "%LOG%"

javac --release 21 -encoding UTF-8 ^
  --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics ^
  -d out-pc ^
  src\com\serifsystemworks\darkstone\engine\LogSink.java ^
  src\com\serifsystemworks\darkstone\pc\*.java ^
  src\com\serifsystemworks\darkstone\ui\PcMainView.java ^
  src\com\serifsystemworks\darkstone\DarkstonePcApp.java ^
  1>>"%LOG%" 2>>&1

if errorlevel 1 (
    echo [!] PC compilation FAILED — see build_pc_error.log
    type "%LOG%"
    exit /b 1
)

if not exist "out-pc\com\serifsystemworks\darkstone\ui" mkdir "out-pc\com\serifsystemworks\darkstone\ui"
copy /Y "src\com\serifsystemworks\darkstone\ui\theme.css" "out-pc\com\serifsystemworks\darkstone\ui\theme.css" >nul

echo [OK] PC build complete. Output in .\out-pc
echo [OK] PC build complete >> "%LOG%"
endlocal
