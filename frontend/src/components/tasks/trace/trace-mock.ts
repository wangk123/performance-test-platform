// 链路追踪面板 mock 数据（原型态）。
// 纪律：数据结构对齐 spec §8 的 REST 响应字段——实施时仅替换数据来源，消费方不改。
// 时间字段当前用演示钟点字符串，接真实 API 时换成 startedAt 时间戳。

export type TraceSpan = {
  service: string;
  name: string;
  startMs: number;
  durationMs: number;
  level: number;
  error: boolean;
  errorMessage: string;
};

export type TraceListItem = {
  traceId: string;
  time: string;
  service: string;
  entry: string;
  durationMs: number;
  error: boolean;
  spans: TraceSpan[];
};

export const TRACE_SERVICE_COLORS: Record<string, string> = {
  'api-gateway': '#0b7f8a',
  'order-service': '#3b82f6',
  'cart-service': '#6366f1',
  'inventory-service': '#f59e0b',
  'user-service': '#8b5cf6',
  'MySQL': '#16a34a',
  'Redis': '#ec4899',
};

export const CHECKOUT_TRACE_ID = 'e9f3a2b7c1d84f05e9f3a2b7c1d84f05';

/** 交界点派生：真实失败样本（5xx）绑定演示错误 trace，其余为「无 traceId」置灰态。 */
export function demoTraceIdForSample(statusCode: string | number | null): string | null {
  const code = Number(statusCode);
  return Number.isFinite(code) && code >= 500 ? CHECKOUT_TRACE_ID : null;
}

function span(
  service: string, name: string, startMs: number, durationMs: number,
  level: number, error = false, errorMessage = '',
): TraceSpan {
  return { service, name, startMs, durationMs, level, error, errorMessage };
}

function fmtTime(sec: number): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(Math.floor(sec / 3600))}:${p(Math.floor((sec % 3600) / 60))}:${p(sec % 60)}`;
}

function buildMockTraces(): TraceListItem[] {
  const traces: TraceListItem[] = [
    {
      traceId: CHECKOUT_TRACE_ID, time: '14:35:41', service: 'api-gateway',
      entry: 'POST /api/orders/checkout', durationMs: 2034, error: true,
      spans: [
        span('api-gateway', 'GatewayFilterChain', 0, 2034, 0),
        span('user-service', 'RiskController.check', 18, 96, 1),
        span('MySQL', 'SELECT t_risk_rule', 30, 64, 2),
        span('order-service', 'OrderController.checkout', 122, 1905, 1),
        span('inventory-service', 'POST /inventory/deduct', 150, 1850, 2, true, 'Read timeout after 1850ms'),
        span('MySQL', 'UPDATE t_stock (deduct)', 190, 1790, 3, true, 'Lock wait timeout exceeded'),
        span('MySQL', 'INSERT t_order', 1978, 40, 2),
        span('Redis', 'DEL cart:lock', 2020, 10, 2),
      ],
    },
    {
      traceId: '3f8d91c0ab47e2d63f8d91c0ab47e2d6', time: '14:33:12', service: 'api-gateway',
      entry: 'GET /api/orders', durationMs: 486, error: false,
      spans: [
        span('api-gateway', 'GatewayFilterChain', 0, 486, 0),
        span('order-service', 'OrderController.list', 16, 458, 1),
        span('Redis', 'GET orders:cache:user7', 24, 14, 2),
        span('MySQL', 'SELECT t_order WHERE uid', 44, 178, 2),
        span('user-service', 'GET /users/{id}', 236, 154, 2),
        span('MySQL', 'SELECT t_user', 252, 106, 3),
        span('MySQL', 'SELECT t_order_item', 398, 62, 2),
      ],
    },
    {
      traceId: 'b71e0f4c9a2d5e8fb71e0f4c9a2d5e8f', time: '14:36:58', service: 'api-gateway',
      entry: 'POST /api/cart/items', durationMs: 1562, error: true,
      spans: [
        span('api-gateway', 'GatewayFilterChain', 0, 1562, 0),
        span('cart-service', 'CartController.addItem', 14, 1536, 1),
        span('Redis', 'SETNX cart:lock:u7', 30, 1490, 2, true, 'timeout waiting lock 1490ms'),
        span('MySQL', 'SELECT t_sku', 1530, 16, 2),
      ],
    },
    {
      traceId: '0c5a9e31d7b84f2a0c5a9e31d7b84f2a', time: '14:32:41', service: 'api-gateway',
      entry: 'GET /api/products', durationMs: 45, error: false,
      spans: [
        span('api-gateway', 'GatewayFilterChain', 0, 45, 0),
        span('order-service', 'ProductController.list', 8, 33, 1),
        span('Redis', 'GET products:all', 12, 9, 2),
        span('MySQL', 'SELECT t_product', 24, 14, 2),
      ],
    },
    {
      traceId: 'd24b68103e9f7ac5d24b68103e9f7ac5', time: '14:34:20', service: 'api-gateway',
      entry: 'GET /api/users/me', durationMs: 132, error: false,
      spans: [
        span('api-gateway', 'GatewayFilterChain', 0, 132, 0),
        span('user-service', 'UserController.me', 10, 116, 1),
        span('MySQL', 'SELECT t_user WHERE id', 22, 88, 2),
      ],
    },
  ];

  // 常规 trace 程序化生成，凑足分页演示（26 条 → 2 页）
  const fillers: Array<[string, string, number] | [string, string, number, true]> = [
    ['api-gateway', 'GET /api/products', 64], ['user-service', 'GET /users/{id}', 142],
    ['api-gateway', 'GET /api/users/me', 118], ['order-service', 'POST /internal/stock/deduct', 231],
    ['api-gateway', 'GET /api/orders', 392], ['api-gateway', 'POST /api/cart/items', 156],
    ['user-service', 'GET /users/{id}', 97], ['api-gateway', 'GET /api/products', 73],
    ['order-service', 'GET /internal/orders', 288], ['api-gateway', 'GET /api/orders', 517, true],
    ['api-gateway', 'GET /api/users/me', 131], ['inventory-service', 'POST /deduct', 204],
    ['api-gateway', 'GET /api/products', 58], ['order-service', 'GET /internal/orders', 244],
    ['api-gateway', 'POST /api/orders/checkout', 743], ['user-service', 'GET /users/{id}', 109],
    ['api-gateway', 'GET /api/products', 81], ['api-gateway', 'POST /api/cart/items', 187],
    ['order-service', 'POST /internal/stock/deduct', 176], ['api-gateway', 'GET /api/orders', 341],
    ['api-gateway', 'GET /api/products', 69],
  ];
  fillers.forEach(([service, entry, durationMs, error], i) => {
    const h = (((i + 7) * 2654435761) >>> 0).toString(16).padStart(8, '0');
    const downstream = entry.includes('users') ? 'user-service'
      : entry.includes('cart') ? 'cart-service' : 'order-service';
    const spans = service === 'api-gateway'
      ? [
          span('api-gateway', 'GatewayFilterChain', 0, durationMs, 0),
          span(downstream, entry, Math.round(durationMs * 0.08), Math.round(durationMs * 0.8), 1),
          span('MySQL', 'SELECT …', Math.round(durationMs * 0.14), Math.round(durationMs * 0.45), 2),
        ]
      : [
          span(service, entry, 0, durationMs, 0),
          span('MySQL', 'SELECT …', Math.round(durationMs * 0.12), Math.round(durationMs * 0.6), 1),
        ];
    traces.push({
      traceId: (h + h + h + h).slice(0, 32),
      time: fmtTime(14 * 3600 + 32 * 60 + 10 + i * 13),
      service, entry, durationMs, error: error === true, spans,
    });
  });

  return traces;
}

export const MOCK_TRACES: TraceListItem[] = buildMockTraces();
