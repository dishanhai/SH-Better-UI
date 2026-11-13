@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"
rem Author: dishanhai
set "JAVA_HOME=%ProgramFiles%\Java\jdk-21.0.11"
set "TEMP=C:\jtmp"
set "TMP=C:\jtmp"
set "BUILD_EXIT=1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 找不到 JDK 21：%JAVA_HOME%
    goto finish
)
if not exist "%~dp0gradle-install\gradle-8.8\bin\gradle.bat" (
    echo 找不到本地 Gradle 8.8。
    goto finish
)
if not exist "%TEMP%\" mkdir "%TEMP%"
if exist "%~dp0build\libs" rmdir /s /q "%~dp0build\libs"
if exist "%~dp0build\libs" (
    echo 無法清除舊的 build\libs 目錄
    goto finish
)
call "%~dp0gradle-install\gradle-8.8\bin\gradle.bat" build --offline --no-daemon --console=plain
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo Forge 產物：
dir /b "%~dp0build\libs\*.jar"
if exist "%~dp0neoforge-1.21.1\build\libs" rmdir /s /q "%~dp0neoforge-1.21.1\build\libs"
if exist "%~dp0neoforge-1.21.1\build\libs" (
    echo 無法清除舊的 neoforge-1.21.1\build\libs 目錄
    set "BUILD_EXIT=1"
    goto finish
)
call "%~dp0neoforge-1.21.1\gradlew.bat" -p "%~dp0neoforge-1.21.1" build --offline --no-daemon --console=plain
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 1.21.1 產物：
dir /b "%~dp0neoforge-1.21.1\build\libs\*.jar"

:finish
echo.
if not "%BUILD_EXIT%"=="0" echo 建置失敗，請查看上方錯誤。
if /i not "%~1"=="--no-pause" pause
exit /b %BUILD_EXIT%
