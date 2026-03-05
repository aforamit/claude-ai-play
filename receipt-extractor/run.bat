@echo off
echo ============================================
echo   Cash Deposit Receipt Extractor v1.0
echo ============================================
echo.

if not exist "target\receipt-extractor-1.0.0.jar" (
    echo JAR not found. Run setup.bat first.
    pause
    exit /b 1
)

if not exist "credentials.json" (
    echo ERROR: credentials.json not found.
    echo See SETUP_GUIDE.md for instructions.
    pause
    exit /b 1
)

java -jar target\receipt-extractor-1.0.0.jar

echo.
echo Done. Check deposits.csv for results.
pause
