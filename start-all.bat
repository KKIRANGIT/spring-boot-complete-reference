@echo off
setlocal EnableDelayedExpansion

set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%" >nul

set COMPOSE_ARGS=-f docker-compose.infrastructure.yml -f docker-compose.services.yml

docker compose %COMPOSE_ARGS% up -d --build
if errorlevel 1 (
  set EXIT_CODE=%ERRORLEVEL%
  popd >nul
  exit /b %EXIT_CODE%
)

echo Waiting for BankFlow services to be healthy...

call :wait_for bankflow-mysql
call :wait_for bankflow-redis
call :wait_for bankflow-kafka
call :wait_for bankflow-mailhog
call :wait_for bankflow-prometheus
call :wait_for bankflow-grafana
call :wait_for bankflow-sonar-db
call :wait_for bankflow-sonarqube
call :wait_for bankflow-auth-service
call :wait_for bankflow-account-service
call :wait_for bankflow-payment-service
call :wait_for bankflow-notification-service
call :wait_for bankflow-api-gateway
call :wait_for bankflow-kafka-ui
call :wait_for bankflow-redis-ui

goto :ready

:wait_for
set CONTAINER=%~1
<nul set /p =  - %CONTAINER%

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
<nul set /p =.
timeout /t 5 /nobreak >nul
goto wait_loop

:ready
popd >nul

echo.
echo BankFlow full stack is ready.
echo.
echo Gateway:         http://localhost:8080
echo Gateway Swagger: http://localhost:8080/swagger-ui/index.html
echo Auth Swagger:    http://localhost:8081/swagger-ui/index.html
echo Account Swagger: http://localhost:8082/swagger-ui/index.html
echo Payment Swagger: http://localhost:8083/swagger-ui/index.html
echo Notify Swagger:  http://localhost:8084/swagger-ui/index.html
echo Kafka UI:        http://localhost:8090
echo Redis Commander: http://localhost:8091
echo MailHog UI:      http://localhost:8025
echo Prometheus:      http://localhost:9090
echo Grafana:         http://localhost:3000   ^(admin / bankflow_grafana^)
echo SonarQube:       http://localhost:9000   ^(admin / admin^)
