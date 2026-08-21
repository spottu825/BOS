@echo off
setlocal
cd /d "%~dp0"
echo Starting BOS Receiver...
echo.

echo Opening standalone receiver page...
start "" "%~dp0BOS-Receiver.html"

echo.
echo Optional ADB/global helper server will start at http://127.0.0.1:9090
where npm >nul 2>nul
if errorlevel 1 goto no_node

if not exist node_modules (
  echo Installing receiver dependencies...
  call npm install
  if errorlevel 1 goto error
)

echo Opening helper server page...
start "" "http://127.0.0.1:9090"

echo Starting local helper server. Keep this window open for ADB terminal.
echo Press Ctrl+C to stop.
echo.
call npm start
if errorlevel 1 goto error
goto end

:no_node
echo Node.js / npm was not found. The standalone receiver page is already open.
echo Install Node.js only if you want the ADB terminal/helper server.
pause
goto end

:error
echo.
echo BOS helper server failed to start. The standalone receiver page can still be used.
pause

:end
