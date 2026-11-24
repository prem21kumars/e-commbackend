@echo off
echo ===================================================
echo  Starting Prometheus and Grafana Locally (Windows)
echo ===================================================

set PROMETHEUS_DIR=C:\Users\Admin\Downloads\prometheus-2.51.0.windows-amd64\prometheus-2.51.0.windows-amd64
set GRAFANA_DIR=C:\Users\Admin\Downloads\grafana-enterprise_13.3.0_40412481033_windows_amd64\grafana-13.3.0

echo [1/2] Starting Prometheus on http://localhost:9090 ...
start "Prometheus" /D "%PROMETHEUS_DIR%" "%PROMETHEUS_DIR%\prometheus.exe" --config.file="%PROMETHEUS_DIR%\prometheus.yml"

echo [2/2] Starting Grafana on http://localhost:3000 ...
start "Grafana" /D "%GRAFANA_DIR%\bin" "%GRAFANA_DIR%\bin\grafana.exe" server --homepath "%GRAFANA_DIR%"

echo.
echo Both services launched!
echo Prometheus : http://localhost:9090
echo Grafana    : http://localhost:3000 (admin / admin)
echo Dashboard  : http://localhost:3000/d/ecommerce-spring-boot-prod
echo ===================================================
pause

