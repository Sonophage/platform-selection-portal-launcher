@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ============================================================
REM  Play Field Portal - Release APK Builder
REM  Builds the signed release APK. Gradle's copyReleaseToDist
REM  task (app/build.gradle.kts) is what renames and drops it into
REM  <root>\dist as PSPLauncher-<version>.apk -- this script
REM  drives and verifies that, it never copies or renames on its own.
REM
REM  Usage: build-release-apk.bat
REM ============================================================

pushd "%~dp0"

set "LOG=%~dp0build-release.log"

echo.
echo ========================================
echo Play Field Portal - Release APK Builder
echo ========================================

REM -- Signing preflight ---------------------------------------
if not exist "%~dp0keystore.properties" (
    echo.
    echo WARNING: keystore.properties not found.
    echo The release build will be UNSIGNED and cannot be installed on a device.
)

REM -- Read versionName from app/build.gradle.kts --------------
REM  Only used to predict the output filename for verification and
REM  the summary. Gradle owns the actual naming.
set "_VERSION="
for /f "tokens=2 delims==" %%v in ('findstr /r /c:"versionName *=" "%~dp0app\build.gradle.kts"') do (
    set "_RAW=%%v"
    set "_RAW=!_RAW: =!"
    set _RAW=!_RAW:"=!
    set "_VERSION=!_RAW!"
)

if not defined _VERSION (
    echo ERROR: could not read versionName from app\build.gradle.kts. 1>&2
    popd
    exit /b 1
)

set "_TASKS= :app:assembleRelease"

echo.
echo Version : %_VERSION%
echo Tasks   :%_TASKS%
echo Log     : %LOG%
echo.
echo Building... this can take a few minutes.

echo Build started %DATE% %TIME% > "%LOG%"
echo Command: gradlew.bat --console=plain -Dorg.gradle.problems.report=false%_TASKS% >> "%LOG%"
echo. >> "%LOG%"

call "%~dp0gradlew.bat" --console=plain -Dorg.gradle.problems.report=false%_TASKS% >> "%LOG%" 2>&1

if errorlevel 1 (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    echo.
    echo Review the full log: %LOG% 1>&2
    echo.
    popd
    exit /b 1
)

REM -- Verify the versioned APKs landed in dist ----------------
set "_FAIL=0"
echo.
echo ========================================
echo BUILD SUCCESS
echo ========================================
echo.
echo Artifacts in %~dp0dist:
set "_APK=%~dp0dist\PSPLauncher-%_VERSION%.apk"
if exist "!_APK!" (
    for %%A in ("!_APK!") do echo   %%~nxA   ^(%%~zA bytes^)
) else (
    echo   MISSING: PSPLauncher-%_VERSION%.apk 1>&2
    set "_FAIL=1"
)

if "%_FAIL%"=="1" (
    echo.
    echo ERROR: Gradle succeeded but the expected APK is not in dist. 1>&2
    echo Check the copy task in app\build.gradle.kts and the log: %LOG% 1>&2
    popd
    exit /b 1
)

echo.
echo Install one with: install-apk.bat
echo.
popd
exit /b 0
