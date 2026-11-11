@echo off
chcp 65001 >nul
echo ==========================================
echo  [install-delayed-plugin] RabbitMQ 延迟消息插件（一次性）
echo   插件：rabbitmq_delayed_message_exchange-3.8.0.ez（匹配 RabbitMQ 3.8.3）
echo ==========================================
wsl -u root sh -c "curl -sL --max-time 90 -o /tmp/delayed.ez https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.8.0/rabbitmq_delayed_message_exchange-3.8.0.ez && cp /tmp/delayed.ez /usr/lib/rabbitmq/lib/rabbitmq_server-3.8.3/plugins/rabbitmq_delayed_message_exchange-3.8.0.ez && rabbitmq-plugins enable rabbitmq_delayed_message_exchange && service rabbitmq-server restart"
echo.
echo 验证（应显示 [E*] rabbitmq_delayed_message_exchange 3.8.0）：
wsl -u root rabbitmq-plugins list | findstr delayed
echo.
echo 若 GitHub 直连下载失败，改用镜像后重跑本脚本：
echo   wsl -u root sh -c "curl -sL -o /tmp/delayed.ez https://ghproxy.com/https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.8.0/rabbitmq_delayed_message_exchange-3.8.0.ez && cp /tmp/delayed.ez /usr/lib/rabbitmq/lib/rabbitmq_server-3.8.3/plugins/rabbitmq_delayed_message_exchange-3.8.0.ez"
pause
