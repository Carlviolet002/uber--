@echo off
chcp 65001 >nul
echo ==========================================
echo  [setup-rabbit] RabbitMQ 专用账号初始化（一次性）
echo   创建 mall/mall@123 (administrator)，删除 guest
echo ==========================================
wsl -u root rabbitmqctl add_user mall mall@123 2>nul
wsl -u root rabbitmqctl change_password mall mall@123 2>nul
wsl -u root rabbitmqctl set_user_tags mall administrator
wsl -u root rabbitmqctl set_permissions -p / mall ".*" ".*" ".*"
wsl -u root rabbitmqctl delete_user guest 2>nul
echo.
echo 验证（应只剩 mall [administrator]）：
wsl -u root rabbitmqctl list_users
pause
