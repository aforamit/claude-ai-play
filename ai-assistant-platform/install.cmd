@echo off
:: Install / reinstall npm dependencies using Node.js 18
:: Run this once after cloning, or after adding new packages to package.json

set NODE_HOME=C:\Workshop\Development\node-v18.20.8-win-x64
set PATH=%NODE_HOME%;%NODE_HOME%\node_modules\.bin;%PATH%

echo Using Node: %NODE_HOME%\node.exe
echo.

cd /d "%~dp0"

if exist node_modules (
    echo [install] Removing old node_modules (installed with wrong Node version)...
    rmdir /s /q node_modules
)

echo [install] Running npm install with Node 18...
npm install

echo.
echo [install] Done! Run dev.cmd to start the server.
pause
