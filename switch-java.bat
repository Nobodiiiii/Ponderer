@echo off

:: ==========================================
::  Switch JAVA_HOME globally (system-wide)
::  Usage: switch-java [17|21]
::  No args = auto toggle between 17 and 21
::  Requires: Run as Administrator
:: ==========================================

:: --- Configure your JDK paths here ---
set "JDK17=C:\Program Files\Java\jdk-17.0.12.7-hotspot"
set "JDK21=C:\Users\Nobodiiiii\.jdks\ms-21.0.9"
:: ------------------------------------

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Please run as Administrator!
    echo         Right-click this script ^> Run as administrator
    pause
    exit /b 1
)

if "%~1"=="" goto :toggle
if "%~1"=="17" goto :set17
if "%~1"=="21" goto :set21
echo [ERROR] Unknown version: %~1
echo Usage: switch-java [17^|21]
exit /b 1

:toggle
if "%JAVA_HOME%"=="%JDK17%" goto :set21
if "%JAVA_HOME%"=="%JDK21%" goto :set17
echo [WARN] Current JAVA_HOME does not match JDK 17 or 21:
echo        JAVA_HOME = %JAVA_HOME%
echo        JDK 17    = %JDK17%
echo        JDK 21    = %JDK21%
echo.
echo        Please specify version explicitly: switch-java 17 or switch-java 21
exit /b 1

:set17
if not exist "%JDK17%\bin\javac.exe" (
    echo [ERROR] JDK 17 not found at: %JDK17%
    exit /b 1
)
setx JAVA_HOME "%JDK17%" /M >nul
echo [OK] JAVA_HOME set globally to JDK 17: %JDK17%
"%JDK17%\bin\java" -version
echo.
echo Please restart your terminal / IDE for changes to take effect.
exit /b 0

:set21
if not exist "%JDK21%\bin\javac.exe" (
    echo [ERROR] JDK 21 not found at: %JDK21%
    exit /b 1
)
setx JAVA_HOME "%JDK21%" /M >nul
echo [OK] JAVA_HOME set globally to JDK 21: %JDK21%
"%JDK21%\bin\java" -version
echo.
echo Please restart your terminal / IDE for changes to take effect.
exit /b 0
