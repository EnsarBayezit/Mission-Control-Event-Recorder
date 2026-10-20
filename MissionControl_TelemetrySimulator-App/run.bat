@echo off
cd /d "%~dp0"
echo Telemetry Simulator baslatiliyor...

REM Once yerel java klasoru varsa onu dene
if exist "java\bin\javaw.exe" (
    start "" "java\bin\javaw.exe" -Djava.library.path=lib -cp "lib/*" MissionControl_TelemetrySimulator
    exit /b 0
)

REM Sistemde kurulu Java ile baslat
where java >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    start "" javaw -Djava.library.path=lib -cp "lib/*" MissionControl_TelemetrySimulator 2>nul || java -Djava.library.path=lib -cp "lib/*" MissionControl_TelemetrySimulator
    exit /b 0
)

echo.
echo ========================================================
echo [HATA] Sisteminizde Java bulunamadi!
echo Lutfen Java Runtime Environment (JRE 8 veya uzeri) kurun.
echo Indirme linki: https://adoptium.net/
echo ========================================================
echo.
pause
