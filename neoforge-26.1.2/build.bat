@echo off
chcp 65001 > nul
setlocal
rem Author: dishanhai
set "JAVA_HOME=%ProgramFiles%\Java\jdk-21.0.11"
set "TEMP=C:\jtmp"
set "TMP=C:\jtmp"
set "BUILD_EXIT=1"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 找不到 JDK 21：%JAVA_HOME%
    goto finish
)
if not exist "%TEMP%\" mkdir "%TEMP%"
call "%~dp0..\neoforge\gradlew.bat" -p "%~dp0." build --offline --no-daemon --console=plain
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 26.1.2 產物：
dir /b "%~dp0build\libs\*.jar"

:finish
echo.
if not "%BUILD_EXIT%"=="0" echo 建置失敗，請查看上方錯誤。
if /i not "%~1"=="--no-pause" pause
exit /b %BUILD_EXIT%
