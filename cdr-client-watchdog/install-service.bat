@echo off
setlocal enabledelayedexpansion
REM Install CDRClientWatchdog Service
REM Run this script as Administrator

echo Installing CDRClientWatchdog Service...

REM Stop service if it exists
sc stop "CDRClientWatchdog" >nul 2>&1

REM Delete service if it exists
sc delete "CDRClientWatchdog" >nul 2>&1

REM Create the service
sc create "CDRClientWatchdog" ^
    binPath= "\"%~dp0watchdog\CdrClientWatchdog.exe\"" ^
    start= auto ^
    DisplayName= "curaLINE Client Service"

set "WAIT_SECONDS=7"

if %ERRORLEVEL% == 0 (
    echo Service installed successfully.

    echo Add description to service...
    sc description "CDRClientWatchdog" "Monitors and restarts curaLINE Client service when it fails"
    
    REM Start the service
    echo Starting service...
    sc start "CDRClientWatchdog" >nul 2>&1

    REM Wait a fixed number of seconds, then check once if the service is RUNNING
    REM Increase WAIT_SECONDS if your service needs more time to initialize on slower machines.
    echo "Waiting %WAIT_SECONDS% second(s) for the service to transition to RUNNING..."
    timeout /t %WAIT_SECONDS% >nul

    REM Check if the service reports RUNNING (case-insensitive)
    sc query "CDRClientWatchdog" | findstr /I "RUNNING" >nul && (
        echo Service reached RUNNING state.
        echo.
        echo The CDRClientWatchdog service is now monitoring your curaLINE Client service.
        echo It will automatically restart the service if it exits with a non-zero exit code.
    ) || (
        echo Warning: Service did not reach RUNNING within %WAIT_SECONDS% seconds. Manual check is required.
        REM Show current sc query output for debugging
        sc query "CDRClientWatchdog"
    )
) else (
    echo Error: Failed to install service. Make sure you're running as Administrator.
)

echo.
pause
