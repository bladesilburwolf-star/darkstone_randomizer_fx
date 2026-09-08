@echo off
setlocal EnableExtensions
REM Darkstone seed launcher
REM Usage:
REM   DarkstoneRando.bat
REM   DarkstoneRando.bat 12345
REM   DarkstoneRando.bat 12345 "C:\GOG\Delphine Software"
REM   DarkstoneRando.bat 12345 "C:\GOG\Delphine Software" "C:\path\to\Out"

set "SEED=%~1"
set "GAME=%~2"
set "OUT=%~3"

if "%GAME%"=="" set "GAME=%~dp0"
if "%GAME:~-1%"=="\" set "GAME=%GAME:~0,-1%"

if "%SEED%"=="" (
  if exist "%GAME%\config\seed.txt" (
    set /p SEED=<"%GAME%\config\seed.txt"
  ) else (
    echo Usage: DarkstoneRando.bat ^<seed^> [gameFolder] [outFolder]
    echo Example: DarkstoneRando.bat ABC123 "C:\GOG\Delphine Software"
    exit /b 1
  )
)

REM Sanitize-ish folder name (alphanumeric only for lookup)
set "SEEDDIR=%SEED%"

if not "%OUT%"=="" (
  set "PACK=%OUT%\seeds\%SEEDDIR%"
) else (
  set "PACK=%GAME%\seeds\%SEEDDIR%"
)

if not exist "%PACK%\" (
  echo Seed package not found: "%PACK%"
  echo Run the randomizer first ^(Randomize^) so seeds\^<seed^>\ is created.
  exit /b 2
)

echo Installing seed %SEED% from "%PACK%"

if not exist "%GAME%\config" mkdir "%GAME%\config"
if exist "%PACK%\config\seed.txt" (
  copy /Y "%PACK%\config\seed.txt" "%GAME%\config\seed.txt" >nul
) else (
  echo %SEED%> "%GAME%\config\seed.txt"
)

if not exist "%GAME%\data" mkdir "%GAME%\data"
if exist "%PACK%\data\monsterclass.dat" copy /Y "%PACK%\data\monsterclass.dat" "%GAME%\data\monsterclass.dat" >nul
if exist "%PACK%\data\itemobject.dat" copy /Y "%PACK%\data\itemobject.dat" "%GAME%\data\itemobject.dat" >nul
if not exist "%GAME%\data\pClass" mkdir "%GAME%\data\pClass"
if exist "%PACK%\data\pClass\pclass.txt" copy /Y "%PACK%\data\pClass\pclass.txt" "%GAME%\data\pClass\pclass.txt" >nul
if exist "%PACK%\PCLASS\PCLASS.TXT" copy /Y "%PACK%\PCLASS\PCLASS.TXT" "%GAME%\data\pClass\pclass.txt" >nul

REM Optional: set REPLACE_MTF=1 to also swap DATA.MTF
if "%REPLACE_MTF%"=="1" (
  for %%F in ("%PACK%\DATA_*.MTF") do (
    if not exist "%GAME%\DATA.MTF.launcher_bak" copy /Y "%GAME%\DATA.MTF" "%GAME%\DATA.MTF.launcher_bak" >nul
    copy /Y "%%F" "%GAME%\DATA.MTF" >nul
    echo DATA.MTF replaced from %%~nxF
  )
)

echo Launching Darkstone...
if exist "%GAME%\Darkstone.exe" (
  start "" /D "%GAME%" "%GAME%\Darkstone.exe"
) else if exist "%GAME%\Darkstone_patched.exe" (
  start "" /D "%GAME%" "%GAME%\Darkstone_patched.exe"
) else (
  echo Darkstone.exe not found in "%GAME%"
  exit /b 3
)
echo Done.
endlocal
