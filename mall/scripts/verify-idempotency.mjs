/**
 * verify-idempotency.mjs —— 验证 OP-8 幂等双层防护（Redis + DB 唯一约束兜底）
 *
 * 流程：
 *   1) user1 登录，取 shop/dish/默认地址
 *   2) 用固定 idempotentKey 创建订单 #1 -> 期望成功，得到 orderNoA，Redis 缓存该 key
 *   3) 直连 Redis 删除 idempotent:<key>（模拟 Redis 过期）
 *   4) 用【同一 idempotentKey】再次创建订单 #2
 *        - 期望命中 DB 唯一约束 uk_uniq_token，被捕获后返回【已存在的同一 orderNo】
 *        - 不应生成重复订单
 *   5) 统计 t_order 中 uniq_token='idempotent:<key>' 的行数，必须是 1
 *
 * 前置：5 服务 real 模式运行中，网关 8080，Redis 6379/pass redispass，MySQL mall_db
 */

import net from 'net';

const BASE = process.env.SMOKE_BASE || 'http://localhost:8080';
const KEY = 'verify_dup_' + Date.now();
const IDEM = 'idempotent:' + KEY;

let pass = 0, fail = 0;
const fails = [];
function ok(cond, label) {
  if (cond) { pass++; console.log('  [PASS] ' + label); }
  else { fail++; fails.push(label); console.log('  [FAIL] ' + label); }
}

async function req(method, path, { token, body, expectOk = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let json = null;
  try { json = await res.json(); } catch {}
  const good = res.ok && (json == null || json.code === undefined || json.code === 0 || json.code === 200);
  if (expectOk && !good) console.log(`    ! ${method} ${path} -> ${res.status} ${json ? JSON.stringify(json).slice(0,160) : ''}`);
  return { status: res.status, json, ok: good };
}

async function login(role) {
  const ACC = { user: { username: 'user1', password: 'user123' } };
  const { json } = await req('POST', '/api/user/login', { body: ACC[role], expectOk: true });
  return json?.data?.token;
}

// ---- Redis 直连（同一连接内 AUTH + DEL，模拟 key 过期）----
function redisAuthDel(key) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(6379, 'localhost');
    let buf = '';
    sock.on('error', reject);
    sock.on('connect', () => sock.write('AUTH redispass\r\nDEL ' + key + '\r\n'));
    sock.on('data', (d) => {
      buf += d.toString();
      // 期望两条回复：+OK(\r\n) 与 :N(\r\n)
      if (buf.split('\r\n').length >= 3) { resolve(buf.trim()); sock.end(); }
    });
    sock.on('close', () => resolve(buf.trim()));
    setTimeout(() => { try { sock.destroy(); } catch {} resolve(buf.trim() || '(timeout)'); }, 2000);
  });
}
async function clearRedisIdempotent(key) {
  const r = await redisAuthDel(key);
  console.log(`    (redis AUTH+DEL ${key} -> ${r})`);
}

async function main() {
  console.log('=== OP-8 幂等兜底验证 (idempotentKey=' + KEY + ') ===');

  const token = await login('user');
  ok(!!token, 'user1 登录获取 token');

  const shops = (await req('GET', '/api/shop/list', { token })).json?.data || [];
  const shopId = shops[0]?.id;
  const dishes = (await req('GET', `/api/shop/${shopId}/dishes`, { token })).json?.data || [];
  const dishId = dishes[0]?.id;
  const addrList = (await req('GET', '/api/user/address', { token })).json?.data || [];
  const addrId = (addrList.find(a => a.isDefault) || addrList[0])?.id;
  ok(shopId && dishId && addrId, `取得 shopId=${shopId} dishId=${dishId} addrId=${addrId}`);

  const body = { shopId, addressId: addrId, items: [{ dishId, num: 1 }], idempotentKey: KEY };

  // 第 1 次创建
  const r1 = await req('POST', '/api/order', { token, body });
  const orderNoA = r1.json?.data?.orderNo;
  ok(!!orderNoA, `第1次创建订单成功 orderNo=${orderNoA}`);

  // 模拟 Redis 过期：删除幂等 key
  await clearRedisIdempotent(IDEM);

  // 第 2 次用【同一 key】重试
  const r2 = await req('POST', '/api/order', { token, body });
  const orderNoB = r2.json?.data?.orderNo;
  ok(!!orderNoB, `第2次同key重试返回 orderNo=${orderNoB}`);
  ok(orderNoB === orderNoA, `同key重试返回【同一订单】(A=${orderNoA} B=${orderNoB})`);

  // DB 行数核对（移到脚本外用 mysql 客户端执行，避免中文路径/cmd 重定向问题）
  console.log(`\n[CHECK] 请在 Bash 执行：`);
  console.log(`  mysql -h localhost -u mall -pmall@123 mall_db -N -e "SELECT COUNT(*) FROM t_order WHERE uniq_token='${IDEM}';"`);
  console.log(`  期望返回 1（同一 uniq_token 仅一行，证明 DB 唯一约束兜底生效）`);
  console.log(`[KEY] ${IDEM}`);
  console.log(`[ORDER] ${orderNoA}`);

  console.log(`\n结果：PASS=${pass} FAIL=${fail}`);
  if (fails.length) { console.log('失败项：\n - ' + fails.join('\n - ')); process.exit(1); }
}

main().catch(e => { console.error('脚本异常:', e); process.exit(2); });
