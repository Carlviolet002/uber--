@echo off
chcp 65001 >nul
set SERVER__PORT=
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar mall-shop\target\mall-shop-0.0.1-SNAPSHOT.jar --server.port=8082 --spring.profiles.active=real --logging.file.name=logs\mall-shop-restart.log
