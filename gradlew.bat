@echo off
chcp 65001 > nul
setlocal EnableExtensions DisableDelayedExpansion
rem Author: dishanhai
rem Explicit tasks and options target Forge. No arguments build all three targets.
rem The legacy --no-pause flag is accepted as the first argument.
set "NO_PAUSE="
set GRADLE_ARGS=%*
if /i not "%~1"=="--no-pause" goto setup
set "NO_PAUSE=1"
rem SHIFT leaves %%* intact, so remove the leading flag while preserving argument quotes.
if /i %1=="--no-pause" goto stripQuotedFlag
set GRADLE_ARGS=%GRADLE_ARGS:~10%
goto setup

:stripQuotedFlag
set GRADLE_ARGS=%GRADLE_ARGS:~12%

:setup
if not defined ProgramFiles set "ProgramFiles=%SystemDrive%\Program Files"
if not defined JAVA_HOME set "JAVA_HOME=%ProgramFiles%\Java\jdk-21.0.11"
set "JAVA_HOME=%JAVA_HOME:"=%"
set "TEMP=C:\jtmp"
set "TMP=C:\jtmp"
set "FORGE_GRADLE=%~dp0gradle-install\gradle-8.8\bin\gradle.bat"
set "NEOFORGE_GRADLE=%~dp0neoforge-1.21.1\gradlew.bat"
set "BUILD_EXIT=1"
pushd "%~dp0" || goto report
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 找不到 Java："%JAVA_HOME%\bin\java.exe"
    goto finish
)
if not exist "%FORGE_GRADLE%" (
    echo 找不到本地 Gradle 8.8："%FORGE_GRADLE%"
    goto finish
)
if not exist "%TEMP%\" mkdir "%TEMP%"
if not exist "%TEMP%\" goto finish
if not defined GRADLE_ARGS goto buildAll
call "%FORGE_GRADLE%" %GRADLE_ARGS%
set "BUILD_EXIT=%ERRORLEVEL%"
goto finish

:buildAll
set "BUILD_ARGS=build --offline --no-daemon --console=plain"
if not exist "%NEOFORGE_GRADLE%" (
    echo 找不到 NeoForge Gradle 包裝器："%NEOFORGE_GRADLE%"
    goto finish
)
call "%FORGE_GRADLE%" %BUILD_ARGS%
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo Forge 產物：
dir /b "%~dp0build\libs\*.jar"

call "%NEOFORGE_GRADLE%" -p "%~dp0neoforge-1.21.1" %BUILD_ARGS%
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 1.21.1 產物：
dir /b "%~dp0neoforge-1.21.1\build\libs\*.jar"

rem The 26.1.2 build selects Java 25 through its Gradle toolchain.
call "%NEOFORGE_GRADLE%" -p "%~dp0neoforge-26.1.2" %BUILD_ARGS%
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto finish
echo NeoForge 26.1.2 產物：
dir /b "%~dp0neoforge-26.1.2\build\libs\*.jar"

:finish
popd
:report
if not "%BUILD_EXIT%"=="0" echo 建置失敗，請查看上方錯誤。
if defined NO_PAUSE exit /b %BUILD_EXIT%

rem Explorer opens a new console for the bare batch file. Keep its result visible.
set "WRAPPER_PATH=%~f0"
setlocal EnableDelayedExpansion
set "EXPLORER_COMMAND=""!WRAPPER_PATH!" ""
if /i "!CMDCMDLINE:* /c =!"=="!EXPLORER_COMMAND!" pause
exit /b %BUILD_EXIT%
