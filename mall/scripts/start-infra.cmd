@echo off
chcp 65001 >nul
echo ==========================================
echo  [start-infra] 一键启动基础设施（幂等，可重复执行）
echo ==========================================

echo [1/3] 启动 Nacos (standalone, 8848)...
cd /d F:\WinData\毕设\nacos\bin
start "Nacos-Standalone" cmd /c "startup.cmd -m standalone"

echo [2/3] 拉起 WSL Redis + RabbitMQ（已运行时自动跳过）...
wsl -u root service redis-server start
wsl -u root service rabbitmq-server start

echo [3/3] 连通性自检提示（Windows 侧）：
echo    - Nacos    : http://localhost:8848/nacos   （登录 nacos/nacos，约 20-30 秒就绪）
echo    - RabbitMQ : http://localhost:15672        （登录 mall/mall@123）
echo    - Redis    : localhost:6379                （密码 redispass）
echo.
echo 就绪检查命令：
echo    curl http://localhost:8848/nacos/v1/console/health/readiness
pause
