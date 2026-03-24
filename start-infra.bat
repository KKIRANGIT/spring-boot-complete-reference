@echo off
setlocal EnableDelayedExpansion

set COMPOSE_FILE=docker-compose.infrastructure.yml

docker compose -f %COMPOSE_FILE% up -d
if errorlevel 1 exit /b 1

echo Waiting for services to be healthy...

call :wait_for bankflow-mysql
call :wait_for bankflow-redis
call :wait_for bankflow-kafka
call :wait_for bankflow-kafka-ui
call :wait_for bankflow-redis-ui
call :wait_for bankflow-mailhog
call :wait_for bankflow-prometheus
call :wait_for bankflow-grafana
call :wait_for bankflow-sonar-db
call :wait_for bankflow-sonarqube

goto :ready

:wait_for
set CONTAINER=%~1
<nul set /p "=  - %CONTAINER%"

:wait_loop
set STATUS=
for /f "delims=" %%S in ('docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" %CONTAINER% 2^>nul') do set STATUS=%%S
if /I "!STATUS!"=="healthy" (
  echo  healthy
  exit /b 0
)
if /I "!STATUS!"=="running" (
  echo  running
  exit /b 0
)
<nul set /p "=."
timeout /t 5 /nobreak >nul
goto wait_loop

:ready

echo.
echo BankFlow infrastructure is ready.
echo.
echo MySQL:           localhost:3306
echo Redis:           localhost:6379
echo Kafka broker:    localhost:9092
echo Kafka UI:        http://localhost:8090
echo Redis Commander: http://localhost:8091
echo MailHog UI:      http://localhost:8025
echo Prometheus:      http://localhost:9090
echo Grafana:         http://localhost:3000   ^(admin / bankflow_grafana^)
echo SonarQube:       http://localhost:9000   ^(admin / admin^)
