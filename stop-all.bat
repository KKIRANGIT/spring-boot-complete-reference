@echo off
setlocal

docker compose -f docker-compose.services.yml down
if errorlevel 1 exit /b 1

docker compose -f docker-compose.infrastructure.yml down
if errorlevel 1 exit /b 1

echo BankFlow services and infrastructure stopped.
