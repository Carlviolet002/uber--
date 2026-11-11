@echo off
chcp 65001 >nul
echo ==========================================
echo  [start-real] 按序启动 5 个服务（real 模式）
echo   顺序：mall-user(8081) -> mall-shop(8082) -> mall-order(8083)
echo         -> mall-dispatch(8084) -> mall-gateway(8080)
echo   前置：先运行 start-infra.cmd 并确认 8848/6379/5672 就绪
echo   回退 mock：关掉这 5 个窗口，无参启动服务即可（默认 mock）
echo ==========================================
set ROOT=%~dp0..

echo 启动 mall-user (8081)...
start "mall-user-real" cmd /k "cd /d %ROOT%\mall-user && mvn spring-boot:run -Dspring-boot.run.profiles=real"
timeout /t 15 /nobreak >nul

echo 启动 mall-shop (8082)...
start "mall-shop-real" cmd /k "cd /d %ROOT%\mall-shop && mvn spring-boot:run -Dspring-boot.run.profiles=real"
timeout /t 15 /nobreak >nul

echo 启动 mall-order (8083)...
start "mall-order-real" cmd /k "cd /d %ROOT%\mall-order && mvn spring-boot:run -Dspring-boot.run.profiles=real"
timeout /t 15 /nobreak >nul

echo 启动 mall-dispatch (8084)...
start "mall-dispatch-real" cmd /k "cd /d %ROOT%\mall-dispatch && mvn spring-boot:run -Dspring-boot.run.profiles=real"
timeout /t 15 /nobreak >nul

echo 启动 mall-gateway (8080)...
start "mall-gateway-real" cmd /k "cd /d %ROOT%\mall-gateway && mvn spring-boot:run -Dspring-boot.run.profiles=real"

echo.
echo 全部窗口已拉起（每窗口 Ctrl+C 可停单个服务）。
echo 验证清单：
echo   1. Nacos 服务列表：http://localhost:8848/nacos  （5 个服务 UP）
echo   2. 网关登录：
echo      curl -X POST http://localhost:8080/api/user/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
echo   3. 店铺列表：curl http://localhost:8080/api/shop/shops -H "Authorization: Bearer ^<token^>"
pause
