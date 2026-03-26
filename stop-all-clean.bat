@echo off
setlocal

set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%" >nul

set COMPOSE_ARGS=-f docker-compose.infrastructure.yml -f docker-compose.services.yml

echo This will stop BankFlow and DELETE local Docker data ^(containers, volumes, and orphaned resources^).
set /p CONFIRM=Type YES to continue: 
if /I not "%CONFIRM%"=="YES" (
  echo Operation cancelled.
  popd >nul
  exit /b 0
)

docker compose %COMPOSE_ARGS% down -v --remove-orphans
set EXIT_CODE=%ERRORLEVEL%

popd >nul

if not "%EXIT_CODE%"=="0" exit /b %EXIT_CODE%

echo BankFlow services, infrastructure, and Docker data removed.
