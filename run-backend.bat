@echo off
title Enterprise E-Commerce Spring Boot Backend (Port 8080)
color 0A
echo =====================================================================
echo           Starting Enterprise E-Commerce Spring Boot Backend
echo =====================================================================
echo.
cd /d "%~dp0"

echo [1/3] Checking MySQL connection on port 3306...
netstat -ano | findstr :3306 >nul
if %errorlevel% equ 0 (
    echo   [OK] MySQL is active.
) else (
    echo   [WARN] MySQL not detected on port 3306. Ensure MySQL is running!
)

echo [2/3] Checking if port 8080 is currently occupied...
netstat -ano | findstr :8080 >nul
if %errorlevel% equ 0 (
    echo   [INFO] Port 8080 is already active. Terminating old instance...
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080') do taskkill /PID %%a /F 2>nul
)

echo [3/3] Launching Spring Boot with 'dev' profile...
echo Backend URL  : http://localhost:8080
echo API Docs     : http://localhost:8080/scalar
echo Health Check : http://localhost:8080/api/v1/health
echo =====================================================================
echo.

java -Dspring.profiles.active=dev -jar target\e-comm-backend-1.0.0-SNAPSHOT.jar

pause

