@echo off
setlocal

set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%" >nul

set COMPOSE_ARGS=-f docker-compose.infrastructure.yml -f docker-compose.services.yml

docker compose %COMPOSE_ARGS% down
set EXIT_CODE=%ERRORLEVEL%

popd >nul

if not "%EXIT_CODE%"=="0" exit /b %EXIT_CODE%

echo BankFlow services and infrastructure stopped.
