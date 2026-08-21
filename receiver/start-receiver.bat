@echo off
setlocal
cd /d "%~dp0"
echo Starting BOS Receiver...
echo.
if not exist node_modules (
  echo Installing receiver dependencies...
  npm install
  if errorlevel 1 goto error
)
npm start
if errorlevel 1 goto error
goto end
:error
echo.
echo BOS Receiver failed to start. Make sure Node.js is installed.
pause
:end
