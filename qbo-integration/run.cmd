@echo off
setlocal

echo ============================================================
echo  QBO Integration - QuickBooks Online API Platform
echo ============================================================
echo.

REM ── Check Java 21 ────────────────────────────────────────────────────────
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found. Please install JDK 21 and add to PATH.
    echo Download: https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

java -version 2>&1 | findstr /R "version \"2[1-9]\." >nul
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Java 21+ recommended.
    echo Current version:
    java -version
    echo.
)

REM ── Check Maven ──────────────────────────────────────────────────────────
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven not found. Please install Apache Maven 3.9+ and add to PATH.
    echo Download: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM ── Set QBO Credentials ──────────────────────────────────────────────────
REM Option 1: Edit these lines directly (not recommended for production)
REM set QBO_CLIENT_ID=your-client-id
REM set QBO_CLIENT_SECRET=your-client-secret

REM Option 2: Set via Windows environment variables (recommended)
REM   Right-click "This PC" → Properties → Advanced → Environment Variables

if "%QBO_CLIENT_ID%"=="" (
    echo WARNING: QBO_CLIENT_ID environment variable is not set.
    echo The app will start but API calls will fail until you configure credentials.
    echo.
    echo To set credentials, run these commands before starting:
    echo   set QBO_CLIENT_ID=your-client-id
    echo   set QBO_CLIENT_SECRET=your-client-secret
    echo.
)

REM ── Display Configuration ────────────────────────────────────────────────
echo Configuration:
echo   Port     : 8080
echo   Sandbox  : %QBO_SANDBOX%
echo   ClientId : %QBO_CLIENT_ID%
echo.
echo Starting application...
echo Once started, visit: http://localhost:8080/api/auth/connect
echo.

REM ── Start Application ────────────────────────────────────────────────────
mvn spring-boot:run

pause
