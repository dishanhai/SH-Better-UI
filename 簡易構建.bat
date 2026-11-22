@echo off
chcp 65001 > nul
setlocal
rem Author: dishanhai
if not defined ProgramFiles set "ProgramFiles=%SystemDrive%\Program Files"
set "JAVA_HOME=%ProgramFiles%\Java\jdk-21.0.11"
set "TEMP=C:\jtmp"
set "TMP=C:\jtmp"
set "FORGE_GRADLE=%~dp0gradle-install\gradle-8.8\bin\gradle.bat"
set "NEOFORGE_GRADLE=%~dp0neoforge-1.21.1\gradlew.bat"
set "BUILD_ARGS=verifyLibraryJar --offline --no-daemon --console=plain"
set "BUILD_EXIT=1"
pushd "%~dp0" || goto report
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 找不到 JDK 21：%JAVA_HOME%
    goto finish
)
if not exist "%FORGE_GRADLE%" (
    echo 找不到本地 Gradle 8.8。
    goto finish
)
if not exist "%NEOFORGE_GRADLE%" (
    echo 找不到 NeoForge 1.21.1 Gradle 包裝器。
    goto finish
)
if not exist "%TEMP%\" mkdir "%TEMP%"
if not exist "%TEMP%\" goto finish

echo 建置 Forge 1.20.1 模組 JAR
call "%FORGE_GRADLE%" %BUILD_ARGS%
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo Forge 產物目錄："%~dp0build\libs"

echo 建置 NeoForge 1.21.1 模組 JAR
rem Only the outer script should wait for input.
call "%NEOFORGE_GRADLE%" -p "%~dp0neoforge-1.21.1" %BUILD_ARGS% <nul
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 1.21.1 產物目錄："%~dp0neoforge-1.21.1\build\libs"

:finish
popd
:report
echo.
if not "%BUILD_EXIT%"=="0" echo 建置失敗，請查看上方錯誤。
if /i not "%~1"=="--no-pause" pause
exit /b %BUILD_EXIT%
