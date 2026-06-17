@echo off
echo ========================================
echo Cleanbot Backend Service Startup
echo ========================================
echo.
echo Starting backend service...
echo MySQL Password: 123456 (default)
echo.

cd /d "%~dp0"
mvn spring-boot:run -DMYSQL_PASSWORD=123456

echo.
echo Backend service stopped.
pause
