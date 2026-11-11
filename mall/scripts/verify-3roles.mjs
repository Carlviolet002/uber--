/**
 * verify-3roles.mjs —— 三类账号注册 / 各自端登录 / 商家端 & 骑手动作 验收
 *
 * 覆盖用户诉求：
 *   1) 分别注册 USER / SHOP / RIDER 三类账号（/api/user/register 带 role）
 *   2) 各自端登录：USER/SHOP 走 /api/user/login，RIDER 走 /api/dispatch/rider/login，校验返回角色
 *   3) 角色隔离：USER token 打骑手专属接口应被拦截（403）
 *   4) SHOP 进商家端：用 SHOP token 调商家端接口可用
 *   5) RIDER 能上线 + 抢单：注册的新骑手 online 后从抢单池抢下一单
 *
 * 用法：node scripts/verify-3roles.mjs
 */
const BASE = process.env.SMOKE_BASE || 'http://localhost:8080';

let pass = 0, fail = 0;
const failures = [];

function ok(cond, label, extra = '') {
  if (cond) { pass++; console.log(`  [PASS] ${label}${extra ? ' — ' + extra : ''}`); }
  else { fail++; failures.push(label); console.log(`  [FAIL] ${label}${extra ? ' — ' + extra : ''}`); }
}

async function req(method, path, { token, body, expectStatus } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try { json = await res.json(); } catch {}
  const statusOk = expectStatus ? res.status === expectStatus : res.ok;
  if (!statusOk) {
    console.log(`    ! ${method} ${path} -> HTTP ${res.status} ${json ? JSON.stringify(json).slice(0, 160) : ''}`);
  }
  return { status: res.status, json, ok: statusOk };
}

const ts = Date.now();
const acc = {
  user:  { username: 'vuser_'  + ts, password: 'test123', role: 'USER',  nickname: '验用户' },
  shop:  { username: 'vshop_'  + ts, password: 'test123', role: 'SHOP',  nickname: '验商家' },
  rider: { username: 'vrider_' + ts, password: 'test123', role: 'RIDER', nickname: '验骑手' },
};
const tokens = {};

async function main() {
  console.log('==========================================');
  console.log(' [verify-3roles] 三类账号注册/登录/动作验收');
  console.log(` BASE=${BASE}`);
  console.log('==========================================');

  // ---------- 1) 注册三类账号 ----------
  console.log('\n[1] 注册三类账号 (USER / SHOP / RIDER)');
  for (const k of ['user', 'shop', 'rider']) {
    const a = acc[k];
    const r = await req('POST', '/api/user/register', { body: a });
    ok(r.ok && (r.json?.code === undefined || r.json?.code === 0 || r.json?.code === 200),
       `注册 ${a.role} 账号 ${a.username}`, `HTTP ${r.status}`);
  }

  // ---------- 2) 各自端登录 + 角色校验 ----------
  console.log('\n[2] 各自端登录并校验返回角色');
  // USER / SHOP 走 /api/user/login
  for (const k of ['user', 'shop']) {
    const a = acc[k];
    const r = await req('POST', '/api/user/login', { body: { username: a.username, password: a.password } });
    const role = r.json?.data?.role;
    const tk = r.json?.data?.token;
    tokens[k] = tk;
    ok(r.ok && !!tk, `${a.role} 登录获取 token`);
    ok(role === a.role, `${a.role} 登录返回角色=${role}`, `期望 ${a.role}`);
  }
  // RIDER 走 /api/dispatch/rider/login
  {
    const a = acc.rider;
    const r = await req('POST', '/api/dispatch/rider/login', { body: { username: a.username, password: a.password } });
    const role = r.json?.data?.role;
    const tk = r.json?.data?.token;
    tokens.rider = tk;
    ok(r.ok && !!tk, `RIDER 登录获取 token (走 /api/dispatch/rider/login)`);
    ok(role === 'RIDER', `RIDER 登录返回角色=${role}`, '期望 RIDER');
  }
  // 反向：用 SHOP 账号去骑手登录接口 → 业务码应 403
  {
    const a = acc.shop;
    const r = await req('POST', '/api/dispatch/rider/login', { body: { username: a.username, password: a.password } });
    if (r.json?.code !== 403) console.log('   [DEBUG] SHOP->/rider/login 响应:', JSON.stringify(r.json).slice(0,200));
    ok(r.json?.code === 403, `SHOP 账号走骑手登录接口被拦截`, `HTTP ${r.status} code=${r.json?.code}`);
  }

  // ---------- 3) 角色隔离：USER 打骑手专属接口业务码应 403 ----------
  console.log('\n[3] 角色隔离校验');
  {
    const r = await req('GET', '/api/dispatch/rider/pool', { token: tokens.user });
    if (r.json?.code !== 403) console.log('   [DEBUG] USER->/pool 响应:', JSON.stringify(r.json).slice(0,200), 'token头role=', (()=>{try{return JSON.parse(Buffer.from(tokens.user.split('.')[1],'base64').toString()).role;}catch(e){return '?';}})());
    ok(r.json?.code === 403, `USER token 访问 /api/dispatch/rider/pool 被拦截`, `HTTP ${r.status} code=${r.json?.code}`);
  }

  // ---------- 4) SHOP 进商家端：用 SHOP token 调商家端接口 ----------
  console.log('\n[4] SHOP 进商家端（商家端接口可用）');
  {
    const r = await req('GET', '/api/shop/orders/pending-paid?shopId=1', { token: tokens.shop });
    ok(r.ok, `SHOP token 调 /api/shop/orders/pending-paid 可用`, `HTTP ${r.status}`);
  }

  // ---------- 5) RIDER 上线 + 抢单 ----------
  console.log('\n[5] RIDER 上线 + 抢单（造一单进抢单池）');
  // 5.1 用种子账号造单：user1 下单 -> 支付 -> shop1 接单
  const seedUser = await req('POST', '/api/user/login', { body: { username: 'user1', password: 'user123' } });
  const tU = seedUser.json?.data?.token;
  ok(!!tU, '种子 user1 登录');
  const addr = await req('GET', '/api/user/address', { token: tU });
  const defaultAddr = (addr.json?.data || []).find(a => a.isDefault) || (addr.json?.data || [])[0];
  ok(!!defaultAddr?.id, '获取默认地址');
  const dishes = await req('GET', '/api/shop/1/dishes', { token: tU });
  const dishId = (dishes.json?.data || [])[0]?.id;
  ok(!!dishId, '获取菜品');
  const order = await req('POST', '/api/order', {
    token: tU,
    body: {
      shopId: 1, addressId: defaultAddr?.id, items: [{ dishId, num: 1 }],
      idempotentKey: 'verify3_' + ts + '_' + Math.random().toString(36).slice(2),
    },
  });
  const orderNo = order.json?.data?.orderNo;
  ok(!!orderNo, `user1 创建订单 ${orderNo}`);
  await req('PUT', `/api/order/${orderNo}/pay`, { token: tU });
  const seedShop = await req('POST', '/api/user/login', { body: { username: 'shop1', password: 'shop123' } });
  const tS = seedShop.json?.data?.token;
  const accept = await req('PUT', `/api/shop/order/${orderNo}/accept`, { token: tS });
  ok(accept.ok, `shop1 接单 ${orderNo}`, `HTTP ${accept.status}`);

  // 5.2 新骑手上线
  const on = await req('PUT', '/api/dispatch/rider/online', { token: tokens.rider });
  ok(on.json?.code === 200 || on.json?.code === 0, '新注册 RIDER 上线(online) 成功', `HTTP ${on.status} code=${on.json?.code}`);

  // 5.3 新骑手抢单
  const grab = await req('POST', `/api/dispatch/rider/pool/${orderNo}/grab`, { token: tokens.rider });
  ok(grab.json?.code === 200 || grab.json?.code === 0, `新注册 RIDER 抢单 ${orderNo} 成功`, `HTTP ${grab.status} code=${grab.json?.code}`);

  // 5.4 抢单后该单应出现在骑手在途列表（assign 经 MQ 异步置状态，稍等再查）
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  await sleep(1500);
  const myOrders = await req('GET', '/api/dispatch/rider/orders', { token: tokens.rider });
  const inList = (myOrders.json?.data || []).some(o => o.orderNo === orderNo);
  ok(inList, `抢单后 ${orderNo} 出现在骑手订单列表`, `共 ${myOrders.json?.data?.length || 0} 单`);

  // ---------- 汇总 ----------
  console.log('\n==========================================');
  console.log(` 结果：PASS=${pass}  FAIL=${fail}`);
  if (failures.length) { console.log(' 失败项：'); failures.forEach(f => console.log('   - ' + f)); }
  console.log('==========================================');
  console.log(`注册账号(可留存供复测)：USER=${acc.user.username} SHOP=${acc.shop.username} RIDER=${acc.rider.username}`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch(e => { console.error('脚本异常：', e); process.exit(2); });
