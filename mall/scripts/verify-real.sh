#!/usr/bin/env bash
# verify-real.sh —— Mock→真实连接 运行试验收编排
# 前置：5 个 real 服务已启动并在 Nacos 注册（见 start-real.cmd / 直接 java -jar --spring.profiles.active=real）
# 职责：1) 轮询 Nacos 等待 5 服务全部注册就绪；2) 跑 scripts/smoke-real.mjs 端到端冒烟；3) 输出结论
set -u

NACOS_API="http://localhost:8848/nacos/v1/ns/instance/list"
SERVICES=(mall-user mall-shop mall-order mall-dispatch mall-gateway)
MAX_WAIT=200
SLEEP_STEP=5

echo "=========================================="
echo " [verify-real] 等待服务在 Nacos 注册就绪"
echo "=========================================="
elapsed=0
ready=0
while [ "$elapsed" -lt "$MAX_WAIT" ]; do
  ready=0
  for s in "${SERVICES[@]}"; do
    resp=$(curl -s -m 3 "$NACOS_API?serviceName=$s")
    cnt=$(printf '%s' "$resp" | grep -o '"count":[0-9]*' | head -1 | grep -o '[0-9]*')
    cnt=${cnt:-0}
    if [ "$cnt" -ge 1 ]; then
      ready=$((ready + 1))
    else
      echo "  [wait] $s 尚未注册 (count=$cnt)"
    fi
  done
  if [ "$ready" -eq "${#SERVICES[@]}" ]; then
    echo "  => 全部 ${#SERVICES[@]} 个服务已注册 (耗时 ${elapsed}s)"
    break
  fi
  sleep "$SLEEP_STEP"
  elapsed=$((elapsed + SLEEP_STEP))
  echo "  [$elapsed s] 已就绪 $ready/${#SERVICES[@]}"
done

if [ "$ready" -ne "${#SERVICES[@]}" ]; then
  echo "超时：仍有服务未在 Nacos 注册，请检查 logs/*.log"
  exit 3
fi

# 网关路由基于 Nacos 服务发现，注册完成后给 5s 缓冲让网关刷新路由表
echo "等待网关路由刷新 (5s)..."
sleep 5

cd "$(dirname "$0")/.."
echo "=========================================="
echo " [verify-real] 运行端到端冒烟 scripts/smoke-real.mjs"
echo "=========================================="
node scripts/smoke-real.mjs
code=$?
echo "------------------------------------------"
if [ "$code" -eq 0 ]; then
  echo " [RESULT] 冒烟全部通过 (exit=0)"
else
  echo " [RESULT] 冒烟存在失败项 (exit=$code)"
fi
exit $code
