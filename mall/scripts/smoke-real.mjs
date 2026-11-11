/**
 * smoke-real.mjs —— Mock→真实连接 R2 端到端冒烟脚本（文档 §8 用例）
 *
 * 运行前置：
 *   1) 已执行 start-infra.cmd（Nacos + WSL Redis/RabbitMQ 就绪）
 *   2) 已执行 start-real.cmd（5 服务 real 模式启动，网关 8080）
 *   3) Node 18+（使用原生 fetch）
 *
 * 用法：
 *   node scripts/smoke-real.mjs                # 跑核心全链路
 *   node scripts/smoke-real.mjs --delay       # 额外跑 B 延迟关单（需 yml 设 mq-timeout=true + pay-timeout-minutes=1）
 *
 * 说明：
 *   - 走网关 8080，所有接口加 /api 前缀（网关 StripPrefix=1 转发到各服务）。
 *   - 登录白名单仅 3 个：/api/user/login、/api/user/register、/api/dispatch/rider/login，其余需 Bearer token。
 *   - 专项 A（pending-paid）与 C（messages）接口仅在 real 模式存在（@ConditionalOnProperty(mall.mock.enabled=false)）。
 *   - B 延迟关单默认关闭：需临时把 mall-order/application-real.yml 的 pay-timeout-minutes 改成 1 再验，验后还原 15。
 */

const BASE = process.env.SMOKE_BASE || 'http://localhost:8080';
const ENABLE_DELAY_TEST = process.argv.includes('--delay');

const ACCOUNTS = {
  user: { username: 'user1', password: 'user123' },
  shop: { username: 'shop1', password: 'shop123' },
  rider: { username: 'rider1', password: 'rider123' },
};

let pass = 0;
let fail = 0;
const failures = [];

function log(msg) {
  console.log(msg);
}

async function req(method, path, { token, body, expectOk = true } = {}) {
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
  } catch {
    /* 非 JSON 响应 */
  }
  const ok = res.ok && (json === null || json.code === undefined || json.code === 0 || json.code === 200);
  if (expectOk && !ok) {
    fail++;
    failures.push(`${method} ${path} -> HTTP ${res.status} ${json ? JSON.stringify(json).slice(0, 120) : ''}`);
    log(`  [FAIL] ${method} ${path} -> ${res.status} ${json ? JSON.stringify(json).slice(0, 120) : ''}`);
  } else {
    pass++;
    log(`  [PASS] ${method} ${path} -> ${res.status}`);
  }
  return { status: res.status, json, ok };
}

function assert(cond, label) {
  if (cond) {
    pass++;
    log(`  [PASS] ${label}`);
  } else {
    fail++;
    failures.push(label);
    log(`  [FAIL] ${label}`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(role) {
  const a = ACCOUNTS[role];
  const path = role === 'rider' ? '/api/dispatch/rider/login' : '/api/user/login';
  const { json } = await req('POST', path, { body: a, expectOk: true });
  const token = json?.data?.token;
  assert(!!token, `${role} 登录获取 token`);
  return token;
}

async function main() {
  log('==========================================');
  log(' [smoke-real] Mock→真实连接 端到端冒烟');
  log(` BASE=${BASE}  delayTest=${ENABLE_DELAY_TEST}`);
  log('==========================================');

  // 1. 用户登录 + 2. 注册(弹性) + 3. me
  const tokenU = await login('user');
  const regName = 'smoke_' + Date.now();
  await req('POST', '/api/user/register', { body: { username: regName, password: 'test123', nickname: '冒烟' }, expectOk: false });
  const me = await req('GET', '/api/user/me', { token: tokenU });
  assert(me.json?.data?.id != null, 'GET /api/user/me 返回用户');

  // 4. 店铺列表（3 店）
  const shops = await req('GET', '/api/shop/list', { token: tokenU });
  const shopList = shops.json?.data || [];
  assert(shopList.length >= 1, `GET /api/shop/list 返回店铺(${shopList.length})`);
  const shopId = shopList[0]?.id;

  // 5. 店铺详情 + 菜品（9 菜）
  if (shopId) {
    await req('GET', `/api/shop/${shopId}`, { token: tokenU });
    const dishes = await req('GET', `/api/shop/${shopId}/dishes`, { token: tokenU });
    const dishList = dishes.json?.data || [];
    assert(dishList.length >= 1, `GET /api/shop/${shopId}/dishes 返回菜品(${dishList.length})`);
    var dishId = dishList[0]?.id;
  }

  // 6. 默认地址
  const addr = await req('GET', '/api/user/address', { token: tokenU });
  const addrList = addr.json?.data || [];
  const defaultAddr = addrList.find((a) => a.isDefault) || addrList[0];
  assert(defaultAddr?.id != null, `GET /api/user/address 有默认地址(${addrList.length})`);

  // 7. 创建订单
  const order = await req('POST', '/api/order', {
    token: tokenU,
    body: {
      shopId,
      addressId: defaultAddr?.id,
      items: [{ dishId, num: 1 }],
      idempotentKey: 'smoke_' + Date.now() + '_' + Math.random().toString(36).slice(2),
    },
  });
  const orderNo = order.json?.data?.orderNo;
  assert(!!orderNo, `POST /api/order 创建订单 -> orderNo=${orderNo}`);

  // 8. 支付
  await req('PUT', `/api/order/${orderNo}/pay`, { token: tokenU });

  // 9. 商家接单
  const tokenS = await login('shop');
  await req('PUT', `/api/shop/order/${orderNo}/accept`, { token: tokenS });

  // 11. 骑手上线 + 抢单派单
  const tokenR = await login('rider');
  await req('PUT', '/api/dispatch/rider/online', { token: tokenR });
  await req('POST', `/api/dispatch/rider/pool/${orderNo}/grab`, { token: tokenR });

  // 10. 商家出餐
  await req('PUT', `/api/shop/order/${orderNo}/finish-cooking`, { token: tokenS });

  // 12. 骑手取餐 + 送达
  await req('PUT', `/api/dispatch/rider/order/${orderNo}/picked`, { token: tokenR });
  await req('PUT', `/api/dispatch/rider/order/${orderNo}/delivered`, { token: tokenR });

  // 13. 确认收货
  await req('PUT', `/api/order/${orderNo}/confirm`, { token: tokenU });

  // 14. 评价
  await req('POST', `/api/order/${orderNo}/review`, {
    token: tokenU,
    body: { shopScoreOverall: 5, shopScoreTaste: 5, shopScorePack: 5, riderScoreService: 5, riderScoreSpeed: 5, content: '冒烟测试评价' },
  });

  // 15. 专项 A：支付后 Redis 待接单缓存应含该 orderNo
  const pending = await req('GET', `/api/shop/orders/pending-paid?shopId=${shopId}`, { token: tokenU });
  const pendingList = pending.json?.data || [];
  assert(pendingList.includes(orderNo), `A: /pending-paid?shopId=${shopId} 含 orderNo (${pendingList.slice(0, 3)})`);

  // 16. 专项 C：商家站内消息（用 shop 账号登录）
  const msgList = await req('GET', '/api/user/messages', { token: tokenS });
  const records = msgList.json?.data?.records || [];
  assert(records.length >= 1, `C: /api/user/messages 含订单消息(${records.length})`);
  const unread = await req('GET', '/api/user/messages/unread-count', { token: tokenS });
  assert(unread.json?.data != null, `C: /unread-count=${unread.json?.data}`);
  if (records[0]?.id != null) {
    await req('PUT', `/api/user/messages/${records[0].id}/read`, { token: tokenS });
  }
  await req('PUT', '/api/user/messages/read-all', { token: tokenS });

  // B. 延迟关单（弹性项，默认关闭）
  if (ENABLE_DELAY_TEST) {
    log('--- B: 延迟关单验证（mq-timeout=true + pay-timeout-minutes=1）---');
    const noPay = await req('POST', '/api/order', {
      token: tokenU,
      body: {
        shopId,
        addressId: defaultAddr?.id,
        items: [{ dishId, num: 1 }],
        idempotentKey: 'smoke_delay_' + Date.now(),
      },
    });
    const delayNo = noPay.json?.data?.orderNo;
    log(`  创建不支付订单 ${delayNo}，等待 ~70s 由延迟队列自动取消...`);
    await sleep(70000);
    const after = await req('GET', `/api/order/${delayNo}`, { token: tokenU, expectOk: false });
    const o = after.json?.data?.order;
    // 注意：API 返回 status 为数字（CANCELLED=7），statusName 为中文（"已取消"），且嵌套在 data.order 下
    assert(o?.status === 7 || (o?.statusName && o.statusName.includes('取消')),
      `B: 订单 ${delayNo} 被延迟队列取消 (status=${o?.status}, ${o?.statusName})`);
  }

  log('==========================================');
  log(` 结果：PASS=${pass}  FAIL=${fail}`);
  if (failures.length) {
    log(' 失败项：');
    failures.forEach((f) => log('   - ' + f));
  }
  log('==========================================');
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('脚本异常：', e);
  process.exit(2);
});
