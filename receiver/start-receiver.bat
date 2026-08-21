@echo off
setlocal
cd /d "%~dp0"
echo Starting BOS Receiver...
echo.

where npm >nul 2>nul
if errorlevel 1 goto no_node

if not exist node_modules (
  echo Installing receiver dependencies...
  call npm install
  if errorlevel 1 goto error
)

echo Opening BOS Receiver at http://127.0.0.1:9090 ...
start "" "http://127.0.0.1:9090"

echo Starting local receiver server. Keep this window open.
echo Press Ctrl+C to stop.
echo.
call npm start
if errorlevel 1 goto error
goto end

:no_node
echo Node.js / npm was not found. Install Node.js first, then run this file again.
pause
goto end

:error
echo.
echo BOS Receiver failed to start.
pause

:end
