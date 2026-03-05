@echo off
echo ============================================
echo   Cash Deposit Receipt Extractor — Setup
echo ============================================
echo.

REM Check Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found on PATH.
    echo Download Java 21 from: https://adoptium.net
    pause
    exit /b 1
)

REM Check Maven
mvn -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven not found on PATH.
    echo Download Maven from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo Building project...
echo.
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo BUILD FAILED. Check the errors above.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Build successful!
echo ============================================
echo.
echo Before running the application, ensure:
echo.
echo   1. credentials.json is in this folder
echo      (download from Google Cloud Console)
echo.
echo   2. application.properties has your
echo      Anthropic API key set:
echo      ocr.claude.api-key=sk-ant-...
echo.
echo Then double-click run.bat to start.
echo.
echo See SETUP_GUIDE.md for detailed instructions.
echo.
pause
