@echo off
:: Start the AI Assistant Platform in production mode (compiled JS)
:: Uses Node.js 18 from local folder without modifying system PATH

set NODE_HOME=C:\Workshop\Development\node-v18.20.8-win-x64
set PATH=%NODE_HOME%;%NODE_HOME%\node_modules\.bin;%PATH%

echo Using Node: %NODE_HOME%\node.exe
echo.

cd /d "%~dp0"

if not exist dist\app.js (
    echo [build] dist\app.js not found. Building first...
    npm run build
)

npm start
