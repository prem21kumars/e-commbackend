@echo off
echo Stopping Prometheus and Grafana...
taskkill /IM prometheus.exe /F 2>nul
taskkill /IM grafana.exe /F 2>nul
echo Monitoring stopped successfully.
pause

