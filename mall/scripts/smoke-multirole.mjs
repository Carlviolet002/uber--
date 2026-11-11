/**
 * smoke-multirole.mjs —— 一号多端（统一账号 + 位掩码多角色）专项验证
 *
 * 验证目标：一个账号同时拥有 USER|SHOP|RIDER 三个角色，
 *   只用【一次登录】拿到的【同一个 token】，即可访问三端各自的接口；
 *   且没有 ADMIN 位时，ADMIN 接口应被 403 拦截（证明角色门控正确）。
 *
 * 前置：5 服务 real 模式已启动，网关 8080 在线。
 * 用法：node scripts/smoke-multirole.mjs
 */

const BASE = process.env.SMOKE_BASE || 'http://localhost:8080';
const stamp = Date.now().toString().slice(-8);
const username = 'mr_' + stamp;
const password = 'Test123456';

function b64urlDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  return Buffer.from(str, 'base64').toString('utf8');
}
function decodeJwt(token) {
  try {
    return JSON.parse(b64urlDecode(token.split('.')[1]));
  } catch {
    return {};
  }
}

let pass = 0,
  fail = 0;
const lines = [];
function check(cond, label, extra = '') {
  if (cond) {
    pass++;
    lines.push(`  [PASS] ${label}${extra ? '  ' + extra : ''}`);
  } else {
    fail++;
    lines.push(`  [FAIL] ${label}${extra ? '  ' + extra : ''}`);
  }
}

async function req(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try {
    json = await res.json();
  } catch {}
  return { status: res.status, json };
}

async function main() {
  console.log('==========================================');
  console.log(' [一号多端] 统一账号·位掩码多角色专项');
  console.log(` BASE=${BASE}  测试账号=${username}`);
  console.log('==========================================');

  // 1) 注册多角色账号（USER|SHOP|RIDER）
  const reg = await req('POST', '/api/user/register', {
    body: { username, password, nickname: '多角色测试', roles: ['USER', 'SHOP', 'RIDER'] },
  });
  check(
    reg.status === 200 && (reg.json?.code === 0 || reg.json?.code === 200),
    '① 注册多角色账号(USER|SHOP|RIDER)',
    `status=${reg.status}`
  );

  // 2) 统一登录一次，拿同一个 token
  const login = await req('POST', '/api/user/login', { body: { username, password } });
  const token = login.json?.data?.token;
  check(!!token, '② 统一登录获取 token（仅一次）', `status=${login.status}`);

  const p = decodeJwt(token || '');
  const rolesClaim = p.roles;
  const roleClaim = p.role;
  lines.push(`  [INFO] JWT: userId=${p.sub} roles(位掩码)=${rolesClaim} legacy role=${roleClaim}`);
  check(rolesClaim === 7, '③ JWT 携带位掩码 roles=7 (USER|SHOP|RIDER)', `roles=${rolesClaim}`);
  // primary() 优先级 ADMIN>RIDER>SHOP>USER，多角色(7)取最高位 RIDER 作为 legacy 单值（设计如此）
  check(String(roleClaim).toUpperCase() === 'RIDER', '④ legacy role 取主角色 RIDER（primary 优先级，兼容旧字段）', `role=${roleClaim}`);

  // 3) 用「同一个 token」打三端接口
  const okCode = (r) => r.status === 200 && (r.json?.code === 200 || r.json?.code === 0 || r.json?.code === undefined);

  const me = await req('GET', '/api/user/me', { token });
  check(okCode(me) && me.json?.data?.id != null, '⑤ 【USER 端】GET /api/user/me 通过', `status=${me.status} code=${me.json?.code}`);

  const mine = await req('GET', '/api/shop/mine', { token });
  check(
    okCode(mine) && mine.json?.data?.id != null,
    '⑥ 【SHOP 端】GET /api/shop/mine 通过（懒建档店铺）',
    `status=${mine.status} code=${mine.json?.code} shopId=${mine.json?.data?.id}`
  );

  const online = await req('PUT', '/api/dispatch/rider/online', { token });
  check(okCode(online), '⑦ 【RIDER 端】PUT /api/dispatch/rider/online 通过（懒建档骑手）', `status=${online.status} code=${online.json?.code}`);

  const pool = await req('GET', '/api/dispatch/rider/pool', { token });
  check(
    okCode(pool),
    '⑧ 【RIDER 端·显式门控】GET /api/dispatch/rider/pool 通过（has(RIDER) 校验放行）',
    `status=${pool.status} code=${pool.json?.code}`
  );

  // 4) 反例：token 无 ADMIN 位 → ADMIN 接口应被拦截（统一 Result 返回 HTTP 200 + body.code=403）
  const adminList = await req('GET', '/api/user/list?page=1&pageSize=10', { token });
  const adminCode = adminList.json?.code;
  check(
    adminList.status === 200 && (adminCode === 403 || adminCode === '403'),
    '⑨ 【反向】GET /api/user/list 应被 403 拒绝（无 ADMIN 位）',
    `status=${adminList.status} body.code=${adminCode}`
  );

  lines.forEach((l) => console.log(l));
  console.log('==========================================');
  console.log(` 结果：PASS=${pass}  FAIL=${fail}`);
  console.log(` 结论：${fail === 0 ? '一号多端✅ 同一 token 打通三端 + ADMIN 门控正确' : '存在失败项，需排查'}`);
  console.log(` 测试账号=${username}（roles=${rolesClaim}）`);
  console.log('==========================================');
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('脚本异常：', e);
  process.exit(2);
});
