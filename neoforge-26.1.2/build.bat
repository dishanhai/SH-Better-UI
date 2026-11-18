@echo off
chcp 65001 > nul
setlocal
rem Author: dishanhai
rem Use the caller's JDK 25 installation (JAVA_HOME or PATH).
set "BUILD_EXIT=1"

call "%~dp0..\neoforge-1.21.1\gradlew.bat" -p "%~dp0." build --no-daemon --console=plain
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 26.1.2 產物：
dir /b "%~dp0build\libs\*.jar"

:finish
echo.
if not "%BUILD_EXIT%"=="0" echo 建置失敗，請查看上方錯誤。
if /i not "%~1"=="--no-pause" pause
exit /b %BUILD_EXIT%
