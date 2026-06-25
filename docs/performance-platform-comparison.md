# 主流性能测试平台架构对比与选型建议

> 撰写日期：2026-06-15
> 目的：梳理市场主流性能测试平台的脚本设计架构与执行引擎方案，为当前项目后续演进提供参考。

---

## 一、对比范围

选择市场使用率最高的 **5 款** 性能测试工具/平台进行对比：

| 排名 | 工具 | 脚本语言 | 执行引擎 | 许可证 |
|------|------|---------|---------|--------|
| 1 | **Apache JMeter** | GUI → XML (.jmx) | JVM 多线程 | Apache 2.0 |
| 2 | **Grafana k6** | JavaScript (ES6) | Go 运行时 | AGPL-3.0 |
| 3 | **Gatling** | Scala/Kotlin/Java DSL | Akka + Netty (JVM) | Apache 2.0 |
| 4 | **Locust** | Python | Python + gevent | MIT |
| 5 | **OpenText LoadRunner** | C / Java / JavaScript | 多组件分离式 | 商业授权 |

---

## 二、执行引擎架构深度对比

### 2.1 并发模型

```
线程模型 (同步阻塞)              事件驱动 (异步非阻塞)
─────────────────────────       ─────────────────────────
JMeter: 1 VU = 1 OS Thread      k6:      1 goroutine = 1 VU
LoadRunner: Thread/Process混合   Gatling: 1 Akka Actor = 1 VU
                                 Locust:  1 greenlet = 1 VU

资源消耗高、单机并发低            资源消耗低、单机并发高
```

| 维度 | JMeter | k6 | Gatling | Locust | LoadRunner |
|------|--------|----|---------|--------|------------|
| **并发模型** | Thread-per-User | Goroutine 事件驱动 | Actor 异步非阻塞 | gevent 协程 | Thread/Process 混合 |
| **运行时** | JVM | Go | JVM (Akka+Netty) | CPython | C/Java 混合 |
| **单机并发上限** | 300~1000 VUs | 数万 VUs | 数万 VUs | 数千 VUs | 视 License 而定 |
| **资源消耗** | 高（CPU/内存线性增长） | 极低 | 低 | 中 | 高 |
| **分布式支持** | 原生 Master-Slave | 需云服务或自建 k6-operator | 需 Gatling Enterprise | 原生 Master-Worker | 原生企业级 |

### 2.2 各引擎核心原理

**JMeter — 线程模型（最传统、最重）**
- 每个虚拟用户占用 1 个 JVM 线程
- 高并发时 CPU 飙升 → GC 频繁 → 线程上下文切换成本巨大
- 实测案例：同一被测系统，JMeter 测出 TPS 3000 时自身 CPU 已打满，换 Gatling 直接上到 8000+
- **核心瓶颈：压测工具自身可能先于被测系统成为瓶颈**

**k6 — Go goroutine 事件驱动（最轻、云原生）**
- Go 编译为单二进制文件，goroutine 极为轻量（几 KB 栈空间）
- 每个 VU 运行在独立 goroutine 中，通过 goja (Go 实现的 JS 引擎) 执行测试脚本
- 单机可模拟数万 VU，内存占用常年在百 MB 级别
- CLI-first 理念，天然适配 CI/CD

**Gatling — Akka Actor + Netty NIO（单机并发最强）**
- 请求封装为 Akka Actor 消息，通过 Netty NIO 通道异步发出
- 请求发出后线程立即释放，响应到达时通过回调唤醒原 Actor
- 完全不阻塞、不等待 — 单机可产生惊人吞吐量
- 适合电商大促级压测

**Locust — gevent 协程（最灵活）**
- 利用 greenlet 在用户态切换执行上下文，避免 OS 线程切换开销
- 1 个 OS 线程可承载数千个 greenlet
- 受 Python GIL 限制，CPU 密集型操作仍受单核瓶颈

**LoadRunner — 多组件分离式（最重、最全）**
- VuGen（脚本录制）+ Controller（场景编排）+ Load Generators（负载生成）+ Analysis（结果分析）
- 每个组件都是独立的重量级进程
- 企业协议覆盖最全（SAP/Citrix/Oracle EBS/RDP 等）

---

## 三、脚本结构深度对比 — JMeter vs k6 vs Gatling

以下用同一个业务场景（电商用户登录 → 浏览商品 → 查看详情 → 加购 → 下单）在三款工具中的完整脚本实现，逐层拆解脚本结构的差异。

### 3.1 同一场景，三套实现

#### 场景描述

```
POST /api/login              — 登录获取 token
GET  /api/products           — 浏览商品列表
GET  /api/products/{id}      — 查看商品详情
POST /api/cart               — 加入购物车
POST /api/orders             — 提交订单
```

要求：线程数 100、爬坡 30s、持续 5min；登录 token 需提取并传递给后续请求；商品 ID 从 CSV 文件中参数化读取；响应需做断言。

---

#### JMeter (.jmx XML) 完整脚本结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <!-- ════════════════════════════════════════════
         层级-1：测试计划
         ════════════════════════════════════════════ -->
    <TestPlan guiclass="TestPlanGui" testname="电商下单压测">
      <elementProp name="TestPlan.user_defined_variables">
        <collectionProp name="Arguments.arguments">
          <elementProp name="BASE_URL" elementType="Argument">
            <stringProp name="Argument.value">https://api.example.com</stringProp>
          </elementProp>
          <elementProp name="ENV" elementType="Argument">
            <stringProp name="Argument.value">SIT</stringProp>
          </elementProp>
        </collectionProp>
      </elementProp>
    </TestPlan>
    <hashTree>

      <!-- ════════════════════════════════════════════
           层级-2：线程组（负载配置）
           ════════════════════════════════════════════ -->
      <ThreadGroup guiclass="ThreadGroupGui" testname="下单用户">
        <intProp name="ThreadGroup.num_threads">100</intProp>
        <intProp name="ThreadGroup.ramp_time">30</intProp>
        <longProp name="ThreadGroup.duration">300</longProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>

        <!-- ════════════════════════════════════════════
             层级-3：配置元件 — CSV 数据驱动
             ════════════════════════════════════════════ -->
        <CSVDataSet guiclass="TestBeanGUI" testname="商品参数">
          <stringProp name="filename">products.csv</stringProp>
          <stringProp name="variableNames">product_id</stringProp>
          <boolProp name="ignoreFirstLine">true</boolProp>
          <stringProp name="delimiter">,</stringProp>
        </CSVDataSet>
        <hashTree/>

        <!-- ════════════════════════════════════════════
             层级-3：配置元件 — HTTP 默认值
             ════════════════════════════════════════════ -->
        <HeaderManager guiclass="HeaderPanel" testname="HTTP Header">
          <collectionProp name="HeaderManager.headers">
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Content-Type</stringProp>
              <stringProp name="Header.value">application/json</stringProp>
            </elementProp>
          </collectionProp>
        </HeaderManager>
        <hashTree/>

        <!-- ════════════════════════════════════════════
             层级-3：取样器 — POST /api/login
             ════════════════════════════════════════════ -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testname="登录">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/login</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <stringProp name="Argument.value">{"username":"admin","password":"123456"}</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree>
          <!-- ─── 内嵌：后置处理器 — JSON 提取 token ─── -->
          <JSONPostProcessor guiclass="TestBeanGUI" testname="提取Token">
            <stringProp name="referenceName">auth_token</stringProp>
            <stringProp name="jsonPathExprs">$.data.token</stringProp>
          </JSONPostProcessor>
          <hashTree/>
          <!-- ─── 内嵌：断言 — 状态码 200 ─── -->
          <ResponseAssertion guiclass="AssertionGui" testname="断言200">
            <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
            <stringProp name="Assertion.test_type">Equals</stringProp>
            <stringProp name="Assertion.custom_message">必须返回200</stringProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- ════════════════════════════════════════════
             层级-3：取样器 — GET /api/products
             ════════════════════════════════════════════ -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testname="商品列表">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/products</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testname="Bearer Token">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">Bearer ${auth_token}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ResponseAssertion guiclass="AssertionGui" testname="断言200">
            <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
            <stringProp name="Assertion.test_type">Equals</stringProp>
            <stringProp name="Assertion.custom_message">必须返回200</stringProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- ════════════════════════════════════════════
             层级-3：取样器 — GET /api/products/{id}
             ════════════════════════════════════════════ -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testname="商品详情">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/products/${product_id}</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testname="Bearer Token">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">Bearer ${auth_token}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ResponseAssertion guiclass="AssertionGui" testname="断言200">
            <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
            <stringProp name="Assertion.test_type">Equals</stringProp>
            <stringProp name="Assertion.custom_message">必须返回200</stringProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- ════════════════════════════════════════════
             层级-3：取样器 — POST /api/cart
             ════════════════════════════════════════════ -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testname="加购">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/cart</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <stringProp name="Argument.value">{"productId":"${product_id}","quantity":1}</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testname="Bearer Token">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">Bearer ${auth_token}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ResponseAssertion guiclass="AssertionGui" testname="断言200">
            <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
            <stringProp name="Assertion.test_type">Equals</stringProp>
            <stringProp name="Assertion.custom_message">必须返回200</stringProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- ════════════════════════════════════════════
             层级-3：取样器 — POST /api/orders
             ════════════════════════════════════════════ -->
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testname="下单">
          <stringProp name="HTTPSampler.domain">${BASE_URL}</stringProp>
          <stringProp name="HTTPSampler.path">/api/orders</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <stringProp name="Argument.value">{"productId":"${product_id}"}</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testname="Bearer Token">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">Bearer ${auth_token}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ResponseAssertion guiclass="AssertionGui" testname="断言200">
            <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
            <stringProp name="Assertion.test_type">Equals</stringProp>
            <stringProp name="Assertion.custom_message">必须返回200</stringProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

      </hashTree> <!-- ThreadGroup 结束 -->
    </hashTree> <!-- TestPlan 结束 -->
  </hashTree>
</jmeterTestPlan>
```

**JMeter 脚本结构分析：**

```
TestPlan (根)
 └─ hashTree
     ├─ 用户自定义变量 (BASE_URL, ENV)
     └─ ThreadGroup (线程数/爬坡/持续时间)
         └─ hashTree
             ├─ CSVDataSet (products.csv → product_id)
             ├─ HeaderManager (Content-Type: application/json)
             ├─ HTTPSamplerProxy "登录"
             │   └─ hashTree
             │       ├─ JSONPostProcessor (提取 token)
             │       └─ ResponseAssertion (断言 200)
             ├─ HTTPSamplerProxy "商品列表"
             │   └─ hashTree
             │       ├─ HeaderManager (Authorization: Bearer ${auth_token})
             │       └─ ResponseAssertion
             ├─ HTTPSamplerProxy "商品详情"
             │   └─ hashTree
             │       ├─ HeaderManager (Authorization)
             │       └─ ResponseAssertion
             ├─ HTTPSamplerProxy "加购"
             │   └─ hashTree
             │       ├─ HeaderManager (Authorization)
             │       └─ ResponseAssertion
             └─ HTTPSamplerProxy "下单"
                 └─ hashTree
                     ├─ HeaderManager (Authorization)
                     └─ ResponseAssertion
```

关键结构特征：
1. **树形嵌套层级**：TestPlan → ThreadGroup → Sampler → (PreProcessor/PostProcessor/Assertion/HeaderManager)，每个节点自带一个 `hashTree` 装子节点
2. **配置与执行混合**：HeaderManager、CSVDataSet 作为配置元件与 HTTPSampler 平级放在 ThreadGroup 下
3. **变量通过字符串模板引用**：`${BASE_URL}`、`${auth_token}`、`${product_id}`
4. **前后置处理器附着在 Sampler 的 child hashTree 中**：JSONPostProcessor 是登录 Sampler 的子节点

---

#### k6 (JavaScript) 完整脚本结构

```javascript
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// ════════════════════════════════════════════
// 模块层：测试数据加载
// ════════════════════════════════════════════
const productIds = new SharedArray('产品ID列表', function () {
  return papaparse.parse(open('./products.csv'), { header: true }).data;
});

// ════════════════════════════════════════════
// 模块层：全局配置
// ════════════════════════════════════════════
const BASE_URL = __ENV.BASE_URL || 'https://api.example.com';

// ════════════════════════════════════════════
// 模块层：测试选项（负载模型 + 阈值断言）
// ════════════════════════════════════════════
export const options = {
  stages: [
    { duration: '30s', target: 100 },   // 爬坡
    { duration: '4m30s', target: 100 }, // 稳态
    { duration: '30s', target: 0 },     // 下降
  ],
  thresholds: {
    // 全局级别：所有请求的聚合指标
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],

    // 按标签分组级别：针对特定接口的阈值
    'http_req_duration{name:登录}': ['p(95)<300'],
    'http_req_duration{name:下单}': ['p(95)<800'],
  },
};

// ════════════════════════════════════════════
// 工具函数层：可复用的业务动作
// ════════════════════════════════════════════

/**
 * 登录并返回 token
 */
function login(baseUrl) {
  const payload = JSON.stringify({ username: 'admin', password: '123456' });
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: '登录' },  // 标签用于细分指标
  };

  const res = http.post(`${baseUrl}/api/login`, payload, params);

  check(res, {
    '登录-状态码200': (r) => r.status === 200,
    '登录-返回token': (r) => r.json('data.token') !== '',
  });

  return res.json('data.token');
}

/**
 * 创建带认证头的请求参数
 */
function authParams(token, tags = {}) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    tags,
  };
}

// ════════════════════════════════════════════
// 主函数层：VU 执行入口
// ════════════════════════════════════════════
export default function () {
  // ── 前置：登录（每个 VU 执行一次）──
  const token = login(BASE_URL);

  // ── 步骤1：浏览商品列表 ──
  group('浏览商品列表', function () {
    const res = http.get(`${BASE_URL}/api/products`, authParams(token, { name: '商品列表' }));
    check(res, { '商品列表-状态码200': (r) => r.status === 200 });
  });
  sleep(1);

  // ── 步骤2：随机取一个商品 ID（模拟参数化）──
  const product = productIds[Math.floor(Math.random() * productIds.length)];

  // ── 步骤3：查看商品详情 ──
  group('查看商品详情', function () {
    const res = http.get(
      `${BASE_URL}/api/products/${product.product_id}`,
      authParams(token, { name: '商品详情' })
    );
    check(res, { '商品详情-状态码200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // ── 步骤4：加入购物车 ──
  group('加入购物车', function () {
    const payload = JSON.stringify({ productId: product.product_id, quantity: 1 });
    const res = http.post(
      `${BASE_URL}/api/cart`,
      payload,
      authParams(token, { name: '加购' })
    );
    check(res, { '加购-状态码200': (r) => r.status === 200 });
  });
  sleep(0.5);

  // ── 步骤5：提交订单 ──
  group('提交订单', function () {
    const payload = JSON.stringify({ productId: product.product_id });
    const res = http.post(
      `${BASE_URL}/api/orders`,
      payload,
      authParams(token, { name: '下单' })
    );
    check(res, { '下单-状态码200': (r) => r.status === 200 });
  });
  sleep(1);
}

// ════════════════════════════════════════════
// 生命周期钩子：setup/teardown（全局执行一次）
// ════════════════════════════════════════════
export function setup() {
  // 所有 VU 启动前执行一次：如预置测试数据
  console.log(`开始压测，环境: ${__ENV.ENV || 'SIT'}`);
  return { startTime: Date.now() };
}

export function teardown(data) {
  // 所有 VU 结束后执行一次：如清理测试数据
  console.log(`压测结束，持续时间: ${(Date.now() - data.startTime) / 1000}s`);
}
```

**k6 脚本结构分析：**

```
k6 脚本 (单文件 .js)
│
├─ import 区              模块导入
├─ SharedArray 定义        测试数据（VU 间共享，只读）
├─ 全局常量                BASE_URL 等
├─ export const options   负载模型 + 阈值定义（声明式）
│
├─ 工具函数层              可复用业务函数
│   ├─ login(baseUrl)     → 返回 token
│   └─ authParams(token)  → 返回 headers 对象
│
├─ export default function  VU 主逻辑（每个 VU 循环执行）
│   ├─ 变量作用域：VU 本地
│   ├─ group("浏览商品列表") { ... }
│   ├─ group("查看商品详情") { ... }
│   ├─ group("加入购物车")   { ... }
│   └─ group("提交订单")     { ... }
│
├─ export function setup()    全局前置（执行 1 次）
└─ export function teardown() 全局后置（执行 1 次）
```

关键结构特征：
1. **扁平模块化**：所有内容在一个 JS 文件中，通过 import/export 实现模块边界
2. **配置/逻辑分离**：`options`（声明式配置）与 `default function`（执行逻辑）严格分离
3. **函数即步骤**：业务动作封装为普通 JS 函数，可复用、可测试
4. **group() 嵌套**：`group()` 用于逻辑分组，k6 会自动为 group 内的请求生成子指标
5. **标签驱动的指标细分**：通过 `tags: { name: 'xxx' }` 为每个请求打标签，在 thresholds 中按标签精准断言
6. **三级生命周期**：`setup`（全局1次）→ `default`（每VU循环）→ `teardown`（全局1次）

---

#### Gatling (Scala DSL) 完整脚本结构

```scala
package com.yr.perftest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.structure.ChainBuilder
import scala.concurrent.duration._

// ════════════════════════════════════════════
// 模块层：数据模型
// ════════════════════════════════════════════
case class Product(id: String, name: String, price: Double)

// ════════════════════════════════════════════
// 模块层：配置对象（不可变）
// ════════════════════════════════════════════
object Config {
  val baseUrl = sys.env.getOrElse("BASE_URL", "https://api.example.com")
  val users = 100
  val rampUp = 30.seconds
  val steadyState = 270.seconds
}

// ════════════════════════════════════════════
// 模块层：HTTP 协议配置（全局默认值）
// ════════════════════════════════════════════
object HttpProtocol {
  val default = http
    .baseUrl(Config.baseUrl)
    .header("Content-Type", "application/json")
    .check(status.is(200))  // 全局默认断言
}

// ════════════════════════════════════════════
// 模块层：数据 feeder（外部数据源）
// ════════════════════════════════════════════
object Feeders {
  val productFeeder = csv("products.csv").random  // csv feeder，随机取行
  val userFeeder = csv("users.csv").circular      // 循环取行
}

// ════════════════════════════════════════════
// 模块层：可复用的请求构建器（Request Builders）
// ════════════════════════════════════════════
object Requests {
  val login = http("登录")
    .post("/api/login")
    .body(StringBody("""{"username":"admin","password":"123456"}"""))
    .check(
      status.is(200),
      jsonPath("$.data.token").saveAs("auth_token")  // 提取 token
    )

  val listProducts = http("商品列表")
    .get("/api/products")
    .header("Authorization", "Bearer ${auth_token}")

  val productDetail = http("商品详情")
    .get("/api/products/${product_id}")
    .header("Authorization", "Bearer ${auth_token}")

  val addToCart = http("加购")
    .post("/api/cart")
    .header("Authorization", "Bearer ${auth_token}")
    .body(StringBody("""{"productId":"${product_id}","quantity":1}"""))

  val placeOrder = http("下单")
    .post("/api/orders")
    .header("Authorization", "Bearer ${auth_token}")
    .body(StringBody("""{"productId":"${product_id}"}"""))
}

// ════════════════════════════════════════════
// 模块层：业务链路（ChainBuilder 组合）
// ════════════════════════════════════════════

/**
 * 登录链路
 */
val loginChain: ChainBuilder = exec(Requests.login)

/**
 * 浏览与下单主链路（参数化 + 条件分支）
 */
val shoppingChain: ChainBuilder =
  feed(Feeders.productFeeder)                     // 注入 CSV 参数
    .exec(Requests.listProducts)
    .pause(1.second)
    .exec(Requests.productDetail)
    .pause(500.milliseconds)
    .exec(Requests.addToCart)
    .pause(500.milliseconds)
    .doIf("${auth_token}".nonEmpty) {             // 条件逻辑：token 非空才下单
      exec(Requests.placeOrder)
    }
    .pause(1.second)

/**
 * 场景 = 多个 Chain 按顺序串联
 */
val ecommerceScenario = scenario("电商下单场景")
  .exec(loginChain)
  .during(5.minutes) {                            // 循环执行 5 分钟
    exec(shoppingChain)
  }

// ════════════════════════════════════════════
// 入口层：负载注入策略
// ════════════════════════════════════════════
setUp(
  ecommerceScenario.inject(
    rampUsers(Config.users).during(Config.rampUp),      // 爬坡
    constantUsersPerSec(Config.users).during(Config.steadyState) // 稳态
  )
).protocols(HttpProtocol.default)
  .assertions(                                          // 全局断言（与 k6 thresholds 等价）
    global.responseTime.percentile95.lt(500),
    global.failedRequests.percent.lt(1.0),
    details("登录").responseTime.percentile95.lt(300),  // 按请求名断言
    details("下单").responseTime.percentile95.lt(800)
  )
```

**Gatling 脚本结构分析：**

```
Gatling 项目 (SBT/Maven 工程)
│
├─ build.sbt / pom.xml            构建依赖
└─ src/test/scala/
    └─ EcommerceSimulation.scala   仿真类（单文件或多文件）
        │
        ├─ case class Product      数据模型（类型安全）
        ├─ object Config           配置常量（不可变对象）
        ├─ object HttpProtocol     协议默认配置
        ├─ object Feeders          CSV 外部数据源
        ├─ object Requests         HTTP 请求定义（Request Builder）
        │
        ├─ val loginChain          ChainBuilder 链路片段
        ├─ val shoppingChain       ChainBuilder 链路片段（含条件）
        │
        ├─ val scenario            场景 = 链路串联
        │   └─ .exec(loginChain)
        │       .during(5.minutes) { exec(shoppingChain) }
        │
        └─ setUp(...)              负载注入 + 协议 + 断言
```

关键结构特征：
1. **类型安全的分层架构**：case class/Object/val 逐层定义，编译期检查语法和类型
2. **配置与执行严格分离**：`http {...}` 定义协议、`scenario(...)` 定义行为、`setUp(...)` 定义负载
3. **ChainBuilder 是核心组合单元**：每个 `val chain: ChainBuilder` 是一段可复用的链路片段，通过 `.exec(chainA).exec(chainB)` 链式组合
4. **Feeder 注入式参数化**：`feed(csv("products.csv").random)` 将 CSV 数据以声明式方式注入到链路中，自动映射列名为 Session 变量
5. **Session 变量作用域**：`saveAs("auth_token")` 将提取的值存入 Gatling Session（类似 ThreadLocal），在后续请求中通过 `${auth_token}` 引用
6. **DSL 即文档**：`rampUsers(100).during(30.seconds)` — 读起来就是自然语言，产物经理也能理解
7. **编译为 .class 运行**：脚本通过 SBT/Maven 编译为 JVM 字节码，启动时加载执行

---

### 3.2 脚本结构九大维度对比

#### 维度 1：层次组织模型

```
JMeter  ─── 单根树 (TestPlan → ThreadGroup → Sampler → child-elements)
k6      ─── 扁平模块 (imports → options → export functions)
Gatling ─── 分层 Object (Config → Protocol → Feeder → Request → Chain → Scenario → setUp)
```

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **组织方式** | XML 树嵌套 | JS 模块导出 | Scala 对象分层 |
| **层级深度** | 不可控（XML 自然嵌套很深） | 1-2 层 | 3-4 层（高度结构化） |
| **父子关系表达** | Sampler 的 hashTree 子节点 | 函数调用顺序 | `.exec()` 链式组合 |
| **跨层级引用** | `${变量名}` 字符串模板全局可引用 | JS 闭包/变量作用域 | Gatling Session `${变量名}` |

**优点比较：**
- **JMeter**: 树形结构天然映射 GUI 编辑器的组件树，可视化编辑直观；每个步骤的子节点（断言/提取器/Header）位置明确
- **k6**: 扁平模块最灵活，开发者可按自己风格组织代码；`group()` 只在需要时才加层级
- **Gatling**: 分层最清晰，每个 concern 一个 Object，大型脚本依然可维护

**缺点比较：**
- **JMeter**: XML 嵌套深度不可控，5 步请求的脚本轻松 200+ 行 XML；深层嵌套难以阅读和排查
- **k6**: 缺乏强制的结构约定，团队多人协作时风格可能不一致
- **Gatling**: 分层带来认知负担，新手需要理解 DSL/ChainBuilder/Feeder/Session 等多个概念才能写出脚本

---

#### 维度 2：变量与参数管理

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **变量来源** | 用户自定义变量 / CSV / 正则提取 / JSON 提取 / 属性 | JS 变量 / 环境变量 / SharedArray / 响应 JSON | Session 变量 / Feeder / 环境变量 / jsonPath 提取 |
| **变量作用域** | 全局（TestPlan 级）/ ThreadGroup 级 / 迭代级 | JS 词法作用域（函数/模块级） | Gatling Session（请求链路级） |
| **变量引用语法** | `${var_name}` 字符串替换 | JS 原生变量访问 | `${var_name}` EL 表达式 |
| **变量污染风险** | ⚠️ 高（全局变量名空间扁平） | ✅ 低（JS 作用域隔离） | ⚠️ 中（Session 键名扁平） |
| **动态变量** | 函数 `${__Random(1,100)}` / `${__UUID}` | 原生 JS `Math.random()` / `crypto.randomUUID()` | Feeder + `session.set()` |

**举例 — 同一个提取 token 操作：**

```xml                                             <!-- JMeter: 需要单独的 JSONPostProcessor 元素 -->
<JSONPostProcessor guiclass="TestBeanGUI" testname="提取Token">
  <stringProp name="referenceName">auth_token</stringProp>
  <stringProp name="jsonPathExprs">$.data.token</stringProp>
</JSONPostProcessor>
```

```javascript                                       // k6: 直接 JS 变量赋值
const token = res.json('data.token');
```

```scala                                           // Gatling: Session 变量
.check(jsonPath("$.data.token").saveAs("auth_token"))
```

**优点比较：**
- **JMeter**: `${var_name}` 语法统一，所有组件都能识别，新手容易理解
- **k6**: 利用 JS 原生作用域，变量生命周期清晰可控；`SharedArray` 解决 VU 间数据共享同时保证线程安全
- **Gatling**: Session 变量是类型安全的不可变数据结构，每次 `.saveAs()` 返回新的 Session 副本，避免并发修改

**缺点比较：**
- **JMeter**: 全局变量名空间扁平，复杂场景下变量名冲突难以排查；变量值是字符串，非字符串需要手动转换
- **k6**: `SharedArray` 是只读的，不能运行时修改；多 VU 间如果需要共享可变状态需要额外方案
- **Gatling**: Session 变量在 Chain 间隐式传递，出问题时追踪困难；`${var}` 在 Scala 编译期不检查（运行时注入）

---

#### 维度 3：断言/验证模型

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **断言粒度** | 附加在 Sampler 的 child 下 | `check()` 紧跟在请求后 | `.check()` 链在请求定义上 |
| **断言类型** | 状态码/响应文本/JSON/XML/Duration | `check()` 函数 + 自定义条件 | `status.is()` / `jsonPath()` / `regex()` / `responseTimeInMillis.lt()` |
| **失败行为** | 标记 SampleResult 为失败，继续执行 | `check()` 失败记录指标，继续执行 | 默认继续，可通过 `exitHereIfFailed` 终止 |
| **全局断言** | 需额外配置（如 Duration Assertion） | `options.thresholds` 声明式 | `setUp(...).assertions(...)` |

**举例 — 同一个复杂断言（状态码 200 + JSON 包含 success + 响应时间 < 500ms）：**

```xml                                              <!-- JMeter: 需要多个断言元件 -->
<ResponseAssertion guiclass="AssertionGui" testname="状态码200">
  <stringProp name="Assertion.test_field">HTTP Status Code</stringProp>
</ResponseAssertion>
<JSONPathAssertion guiclass="JSONPathAssertionGui" testname="包含success字段">
  <stringProp name="JSON_PATH">$.success</stringProp>
  <stringProp name="EXPECTED_VALUE">true</stringProp>
</JSONPathAssertion>
<!-- Duration 断言需要单独 DurationAssertion 元件 -->
```

```javascript                                       // k6: check() 一个调用完成
const res = http.post(url, payload, params);
check(res, {
  '状态码 200':    (r) => r.status === 200,
  '返回 success':  (r) => r.json('success') === true,
  '响应时间<500ms': (r) => r.timings.duration < 500,
});
```

```scala                                           // Gatling: 链式 .check()
http("下单")
  .post("/api/orders")
  .body(StringBody("""{"productId":"${product_id}"}"""))
  .check(
    status.is(200),
    jsonPath("$.success").is("true"),
    responseTimeInMillis.lte(500)
  )
```

**优点比较：**
- **JMeter**: 断言元件化，可在 GUI 中拖拽配置；支持正则/JSON/XML/XPATH/Duration 多种断言类型
- **k6**: `check()` 是 JS 函数，可写任意复杂逻辑；`options.thresholds` 在全局层做 P95/P99 断言，这是其他工具不具备的一等公民能力
- **Gatling**: `.check()` 以链式 API 附着在请求定义上，与请求同在一处，阅读和维护最自然

**缺点比较：**
- **JMeter**: 每个 Sampler 可能需要 2-3 个断言元件，脚本膨胀严重；断言失败默认不终止场景（可能导致后续误报）
- **k6**: `check()` 返回 true/false 但不抛异常，很容易忘记处理失败情况
- **Gatling**: 复杂 JSON 断言需要写多行，不如 k6 的箭头函数简洁

---

#### 维度 4：数据驱动（参数化）

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **数据源** | CSV / JDBC / Redis / 自定义函数 | SharedArray + CSV解析库 | CSV / JSON / JDBC / Redis / 自定义 Feeder |
| **数据加载时机** | 运行时逐行读取（或预编译） | VU 初始化时一次性加载到内存 | 运行时流式读取（Feeder） |
| **数据消费策略** | 顺序/随机/循环 | 需手动实现 | `.random` / `.circular` / `.shuffle` / `.queue` |
| **数据隔离** | 每个线程独立读取 | SharedArray 所有 VU 共享 | 每个 VU 独立的 Feeder 游标 |

**举例 — CSV 参数化：**

```xml                                              <!-- JMeter: CSVDataSet 配置元件 -->
<CSVDataSet guiclass="TestBeanGUI" testname="商品参数">
  <stringProp name="filename">products.csv</stringProp>
  <stringProp name="variableNames">product_id,product_name</stringProp>
  <stringProp name="delimiter">,</stringProp>
  <boolProp name="recycle">false</boolProp>
  <boolProp name="stopThread">true</boolProp>
</CSVDataSet>
<!-- 引用: ${product_id} -->
```

```javascript                                       // k6: SharedArray + JS 解析
const products = new SharedArray('products', function () {
  return papaparse.parse(open('./products.csv'), { header: true }).data;
});
// 引用: products[Math.floor(Math.random() * products.length)].product_id
```

```scala                                           // Gatling: csv() feeder 链式注入
val productFeeder = csv("products.csv").random
val chain = feed(productFeeder)
  .exec(http("详情").get("/api/products/${product_id}"))
```

**优点比较：**
- **JMeter**: CSVDataSet 配置简单，GUI 拖拽即可；支持 JDBC 直连数据库做数据源
- **k6**: `SharedArray` 设计优秀 — VU 间共享内存只读数据，零拷贝，适合大数据量；可接入任意 JS 数据源
- **Gatling**: Feeder API 最优雅，`.random` / `.circular` 语义清晰；支持多种格式（CSV/JSON/JDBC/Redis/自定义）

**缺点比较：**
- **JMeter**: CSVDataSet 不支持 JSON 格式；大数据文件（百万行）时内存管理不佳
- **k6**: `SharedArray` 必须全量加载到内存；读写决策不直观（它是只读的）；没有内置的 CSV 行选择策略，需手动实现
- **Gatling**: Feeder 注入位置影响变量作用域，不熟悉 Session 机制容易出错（例如在 feed 之前引用 `${product_id}`）

---

#### 维度 5：模块化与复用

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **复用单元** | Test Fragment / Module Controller / include | JS 函数 / import 模块 | ChainBuilder / object / import |
| **跨脚本复用** | `IncludeController` 引用 .jmx 文件 | `import` / `export` ES6 模块 | Scala `import` / SBT 多模块 |
| **参数化复用** | 通过变量传递参数 | 函数传参 | 函数传参 + Session 注入 |

**举例 — 复用登录逻辑：**

```xml                                              <!-- JMeter: Test Fragment + Module Controller -->
<TestFragmentController guiclass="TestFragmentControllerGui" testname="LoginFragment">
  <!-- 包含 HTTPSampler + JSONPostProcessor -->
</TestFragmentController>
<!-- 其他脚本中: <ModuleController><stringProp name="path">LoginFragment.jmx</stringProp></ModuleController> -->
```

```javascript                                       // k6: 普通 JS 函数 + import
// login.js
export function login(baseUrl) {
  const res = http.post(`${baseUrl}/api/login`, payload, params);
  return res.json('data.token');
}

// main.js
import { login } from './login.js';
const token = login(BASE_URL);
```

```scala                                           // Gatling: ChainBuilder val + import
// LoginChains.scala
object LoginChains {
  val login: ChainBuilder = exec(http("登录")
    .post("/api/login")
    .check(jsonPath("$.data.token").saveAs("auth_token")))
}

// EcommerceSimulation.scala
import com.yr.perftest.LoginChains
val scn = scenario("场景").exec(LoginChains.login).exec(...)
```

**优点比较：**
- **JMeter**: Test Fragment 与 GUI 编辑器完美配合，可视化管理可复用片段
- **k6**: 标准 ES6 模块系统，任意 JS 生态工具（npm/webpack/vitest）都可用；单元测试天然支持
- **Gatling**: ChainBuilder 是不可变的函数组合，天然适合抽象和复用；类型安全保证重构时不会遗漏

**缺点比较：**
- **JMeter**: IncludeController 引用外部 .jmx 文件通过文件名匹配，文件移动后断开；不支持传参给 Fragment
- **k6**: 自定义模块不能使用 k6 的 `init context` API（如 `open()`），限制了模块能力
- **Gatling**: ChainBuilder 类型签名复杂，写泛型复用组件时需要较深的 Scala 知识

---

#### 维度 6：条件控制与流程编排

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **条件分支** | If Controller / Switch Controller | `if/else` 原生 JS | `.doIf()` / `.doIfEquals()` / `.doSwitch()` |
| **循环** | Loop Controller / While Controller | `for/while` 原生 JS | `.during()` / `.repeat()` / `.forever()` |
| **事务** | Transaction Controller | 手动 `Date.now()` 计时 | `.transaction()` 或自定义 |
| **错误处理** | 响应断言失败后的动作 | `try/catch` 原生 JS | `.exitHereIfFailed` / `.tryMax()` / `.exitBlockOnFail()` |

**举例 — 条件分支（登录成功才继续）：**

```xml                                              <!-- JMeter: If Controller + 条件表达式 -->
<IfController guiclass="IfControllerPanel" testname="Token不为空则继续">
  <stringProp name="IfController.condition">"${auth_token}" != ""</stringProp>
</IfController>
<hashTree>
  <!-- 后续步骤放在这里 -->
</hashTree>
```

```javascript                                       // k6: 原生 JS if
if (token) {
  const res = http.get(`${BASE_URL}/api/products`, authParams(token));
}
```

```scala                                           // Gatling: .doIf()
.doIf("${auth_token}".nonEmpty) {
  exec(http("下单").post("/api/orders").body(...))
}
```

**优点比较：**
- **JMeter**: 逻辑控制器覆盖全面（If/Switch/While/Loop/OnceOnly/Throughput），适合不写代码的 QA
- **k6**: 利用 JS 原生控制流（if/for/while/try-catch），开发者零学习成本
- **Gatling**: DSL 方法语义化（`doIf` / `during` / `tryMax`），比纯代码更接近测试意图

**缺点比较：**
- **JMeter**: If Controller 的条件表达式语法奇怪（需引号包裹），不支持 else 分支（需要两个 If Controller）
- **k6**: `group()` 内不能使用 `return` 提前退出；没有内置的重试机制
- **Gatling**: `doIf` 条件在运行时求值，不能用编译期检查；嵌套多层后链式代码可读性下降

---

#### 维度 7：脚本可编辑性与可视化

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **原生编辑器** | GUI (Swing) 全可视化 | 无 GUI（纯文本编辑器） | 无 GUI（IDE + DSL） |
| **可视化能力** | ⭐⭐⭐⭐⭐ 树形组件拖拽 | ⭐ 纯文本 | ⭐⭐ IDE 自动补全 |
| **第三方GUI** | BlazeMeter / OctoPerf | k6 Studio (社区) | Gatling Enterprise |
| **脚本即代码** | ❌ XML | ✅ JavaScript | ✅ Scala/Kotlin/Java |
| **Git diff 友好度** | ❌ XML 大量冗余 diff | ✅ | ✅ |
| **平台化改造难度** | ⭐⭐ XML Parser/Renderer 可实现 | ⭐⭐⭐⭐ 需 AST 解析 | ⭐⭐⭐⭐⭐ 需编译器集成 |

**这是本项目最重要的维度。** 你的项目通过自研 `JmeterScriptParser` + `JmeterScriptRenderer` 将 JMX XML 解析为 `ScriptStepDefinition` 树，在前端可视化编辑后再渲染回 JMX。这个模式的可迁移性分析：

```
JMeter (当前)       →  Parser/Renderer 模式最成熟，XML 结构确定，元素类型有限
k6                  →  需要 JavaScript AST 解析器（如 acorn/babel），实现成本高
Gatling             →  需要 Scala 编译器集成或直接操作 AST，实现成本最高
```

**优点比较：**
- **JMeter**: XML Schema 稳定，Parser 实现难度最低；GUI 可视化已有大量实践参考
- **k6**: JS 生态有成熟的 AST 工具链（acorn/esprima）；脚本可以模块化拆分，编辑单个模块影响范围小
- **Gatling**: 类型安全，IDE 自动补全和重构支持最好；DSL 结构稳定

**缺点比较：**
- **JMeter**: XML 中 `hashTree` 嵌套结构与前端树形组件的映射需要精细处理（你已通过 `ScriptStepDefinition.children` 解决）
- **k6**: 没有标准化的脚本结构规范（options/scenarios/export function 只是约定）；不同团队写出的脚本差异大，Parser 难以覆盖
- **Gatling**: 脚本编译依赖 SBT/Maven，平台化编辑器的"即时预览"或"语法校验"需要完整的编译器环境

---

#### 维度 8：结果与度量模型

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **输出格式** | .jtl (CSV/XML) | JSON 流（stdout）+ 结构化摘要 | 自动生成 HTML 报告 |
| **度量指标** | 每个 Sampler 的 elapsed/connect/latency/bytes/success | 内置 http_req_* 系列 + 自定义 Trend/Counter/Gauge | 每个 request group 的 percentile/mean/stddev/rps |
| **实时监控** | 需后端监听器或 Grafana 插件 | ✅ 原生 `--out` 对接多种后端 | 需 Gatling Enterprise |
| **可编程提取** | ⭐⭐ 需额外解析 .jtl | ⭐⭐⭐⭐⭐ JSON 原生 | ⭐⭐⭐ HTML DOM 解析 |
| **自定义指标** | 需写 Java 代码或插件 | ✅ `Trend`/`Counter`/`Gauge`/`Rate` | 需自定义 Collector |

**JMeter .jtl 输出片段：**
```csv
timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
1686883200000,245,登录,200,OK,Thread Group 1-1,text,true,,1024,512,1,1,https://api.example.com/api/login,240,0,12
```

**k6 JSON 输出片段：**
```json
{
  "type": "Point",
  "metric": "http_req_duration",
  "data": {
    "time": "2026-06-16T10:00:00.000Z",
    "value": 245.3,
    "tags": { "name": "登录", "method": "POST", "status": "200" }
  }
}
```

**Gatling HTML 报告（自动生成）：**
- 包含全局/分组统计表、百分位图、响应时间分布图、请求时间线、每秒请求数曲线
- 零配置、开箱即用

**优点比较：**
- **JMeter**: .jtl 格式兼容性最好（几乎所有的分析工具都支持）；数据完整（包含 connect/latency 等底层指标）
- **k6**: JSON 结构化输出最适合程序化处理；`Trend`/`Counter`/`Gauge` 让自定义指标和业务指标一样一等公民
- **Gatling**: 自动 HTML 报告业界最佳，P95/P99 百分位图直观，零配置即可用于向非技术人员汇报

**缺点比较：**
- **JMeter**: .jtl CSV 格式缺少结构（纯平面表格），JSON 模式下文件巨大；实时监控需额外搭建 Grafana + InfluxDB
- **k6**: 开源版无历史趋势分析（需 k6 Cloud 付费）；JSON 输出流式写入，不熟悉 jq 等工具的 QA 难以处理
- **Gatling**: HTML 报告是静态的，不支持交互式钻取；自定义度量需要较深的 Scala 知识

---

#### 维度 9：CI/CD 与工程化集成

| 对比项 | JMeter | k6 | Gatling |
|-------|--------|----|---------|
| **命令行执行** | `jmeter -n -t script.jmx -l result.jtl` | `k6 run script.js` | `gatling.sh -s SimulationClass` |
| **退出码** | 0 (即使有断言失败) | 非 0（thresholds 不满足时） | 非 0（assertions 不满足时） |
| **CI/CD 插件** | Jenkins Performance Plugin | 官方 GitHub Action / GitLab CI 模板 | 官方 Maven/Gradle 插件 |
| **Docker 镜像** | 社区镜像 | ✅ 官方镜像 `grafana/k6` | ✅ 官方镜像 `denvazh/gatling` |
| **容器内执行** | 需挂载 .jmx + 依赖文件 | ✅ 单二进制，最小镜像 | 需挂载编译产物或源码 |

**优点比较：**
- **JMeter**: 生态最广，几乎所有 CI 平台都有现成插件；Jenkins Performance Plugin 提供历史趋势对比
- **k6**: CI/CD 一等公民 — 退出码由 thresholds 决定，测试通过/失败直接反馈到 CI 流水线；Docker 镜像极小
- **Gatling**: Maven/Gradle 插件让脚本和项目代码在同一工程中，开发即测试

**缺点比较：**
- **JMeter**: 即使断言全部失败，JMeter 退出码仍为 0，需要额外脚本解析 .jtl 判断成功/失败
- **k6**: AGPL 许可证对在 CI 环境中使用有限制（特别是企业内部自建平台）
- **Gatling**: 需要 JDK + SBT/Maven 环境，CI 镜像体积大，冷启动慢

---

### 3.3 脚本结构本质差异总结

```
                    JMeter                     k6                    Gatling
                    ──────                     ──                    ───────
核心抽象         Tree (XML DOM)            Function (JS)         ChainBuilder (Scala)
组织范式         配置式 + 树形嵌套          代码式 + 模块导出       DSL式 + 分层组合
状态管理         ${var} 全局字符串           JS 作用域 + 闭包       Session 不可变 Map
参数化           元件注入                   代码逻辑               Feeder 声明式注入
断言             附着元件                   check() 函数           .check() DSL
控制流           逻辑控制器                 if/for/while           .doIf/.during
可视化           GUI 原生                  需自建                 需自建
平台化改造       低（XML → AST 双向）       高（JS AST 不稳定）    极高（需编译器）
```

**一句话总结各工具脚本结构的核心取舍：**

- **JMeter**: 用 **XML 树形结构** 换取了 **GUI 可视化编辑** 的可行性，代价是代码可读性和版本控制友好度。你的项目已经在用 Parser/Renderer 解决这个代价。
- **k6**: 用 **纯 JS 代码** 换取了 **最大的灵活性和 CI/CD 集成度**，代价是没有 GUI、没有结构化约束、没有平台化编辑的天然锚点。
- **Gatling**: 用 **Scala DSL + 多层抽象** 换取了 **极致的类型安全和可维护性**，代价是学习曲线陡峭、平台化改造困难。

### 3.4 与你项目 ScriptStepDefinition 模型的对照

你当前的脚本步骤模型 `ScriptStepDefinition(id, type, name, config, children)` 本质上是：

```
JMX XML Element  →  ScriptStepDefinition (中间表示 IR)
                                ↓
                    前端 Vue 组件渲染 + 编辑
                                ↓
                    ScriptStepDefinition  →  JMX XML Element
```

这个 IR 模型与三种工具的映射关系：

| 你的 IR 概念 | JMeter 对应 | k6 对应 | Gatling 对应 |
|-------------|------------|--------|-------------|
| `THREAD_GROUP` | `<ThreadGroup>` 元素 | `options.stages` | `scenario(...).inject(...)` |
| `HTTP_REQUEST` | `<HTTPSamplerProxy>` 元素 | `http.get/post()` 调用 | `http("name").get/post()` |
| `CSV_DATA` | `<CSVDataSet>` 元素 | `SharedArray` + `papaparse` | `csv("file").random` |
| `HEADER_CONFIG` | `<HeaderManager>` 元素 | `params.headers` 对象 | `.header("k","v")` |
| `ASSERTION` | `<ResponseAssertion>` / `<JSONPathAssertion>` | `check()` | `.check(status.is(200))` |
| `JSR223_PRE_PROCESSOR` | `<JSR223PreProcessor>` | 请求前普通 JS 代码 | 请求前 `.exec(session => ...)` |
| `JSR223_POST_PROCESSOR` | `<JSR223PostProcessor>` / `<JSONPostProcessor>` | 请求后普通 JS 代码 | `.check(jsonPath(...).saveAs(...))` |
| `children` 嵌套 | Sampler → child hashTree | `group()` 嵌套 | `.exec()` 链式嵌套 |

**关键发现：** 你的 IR 模型最接近 JMeter 的树形结构（因为你本来就是从 JMX 解析出来的），而 k6/Gatling 的"Flat Chain"模型与树形 IR 有根本性差异。如果你未来要支持多引擎，需要考虑：

1. **k6 导出**：将 `ScriptStepDefinition` 树扁平化为 `default export function` 中的顺序调用，`children` 映射为 `group()` 嵌套
2. **Gatling 导出**：将 `ScriptStepDefinition` 树映射为 ChainBuilder 链，ThreadGroup 配置映射为 `setUp(...).inject(...)`
3. **共同局限**：这两种映射都会丢失一些引擎特有的高级能力（如 k6 的 `thresholds`、Gatling 的 `during()` 循环控制）

---

---

## 四、各自擅长的应用场景

### 4.1 JMeter — "全能协议覆盖王"

| 场景 | 说明 |
|------|------|
| **多协议混合测试** | 同时压测 HTTP + JDBC + JMS + FTP + LDAP 等 30+ 协议的复杂企业环境。这是 JMeter 最不可替代的能力 |
| **GUI 驱动 / QA 主导团队** | 不写代码的测试人员通过 GUI 拖拽 + 录制回放即可构建测试计划 |
| **遗留/传统系统** | 需要测试 SOAP/WebService、FTP 文件传输、数据库存储过程、邮件服务器等传统协议 |
| **快速原型验证** | 临时抓一个接口快速验证性能表现，不追求工程化 |
| **非 HTTP 短板场景** | 数据库直连压测（JDBC）、消息队列消费能力（JMS）、LDAP 认证服务——这些 k6 和 Gatling 都做不了 |
| **企业合规/采购流程** | 很多传统企业的采购清单只认 JMeter（因为它是 Apache 基金会项目） |

**典型工作负载适配（ICTSS 2025 学术研究）：**
- 读密集型场景表现强劲（HTTP GET 为主的 API 压测）
- 写密集型场景受线程模型拖累

### 4.2 k6 — "云原生 DevOps 首选"

| 场景 | 说明 |
|------|------|
| **CI/CD 流水线性能门禁** | 每次代码提交自动触发压测，thresholds 不达标阻止部署——这正是 k6 设计的核心场景 |
| **微服务/API 持续验证** | HTTP/1.1、HTTP/2、gRPC、GraphQL、WebSocket 全覆盖，正好对齐微服务最常见的协议栈 |
| **Kubernetes 原生压测** | 通过 `k6-operator` 在 K8s 集群内按需启动压测 Job，与 HPA/VPA 联动验证弹性伸缩 |
| **可观测性驱动压测** | 原生对接 Grafana/Prometheus/Datadog，压测指标自动流入现有监控体系，不需要"压完再看" |
| **前端/全栈团队** | 脚本即 JavaScript，前后端开发都能写，不需要培训专项测试技能 |
| **极低资源环境** | 边缘节点、IoT 设备、CI Runner 等资源受限环境，单二进制 + 百 MB 内存即可执行 |
| **写密集型场景** | ICTSS 2025 学术研究发现 k6 在写密集型场景中表现最优（请求生成能力最强） |

### 4.3 Gatling — "高性能 + 精美报告"

| 场景 | 说明 |
|------|------|
| **大促级瞬时高并发** | 电商秒杀/抢购场景，单机即可产生数万 RPS，异步非阻塞模型保证压测工具不先于被测系统崩溃 |
| **向非技术干系人汇报** | 内置 HTML 报告业界公认最精美，P95/P99 图表+颜色标记，无需额外配置即可发给管理层 |
| **JVM 技术栈工程化** | Scala/Kotlin/Java 团队可将压测脚本与业务代码放在同一工程，共享类库（如加密/序列化）和 CI 流程 |
| **代码即文档** | DSL 语法接近自然语言（`rampUsers(1000).during(60.seconds)`），产物经理和架构师都能 review |
| **长期维护的大型脚本** | 分层架构（Config/Protocol/Feeder/Request/Chain/Scenario/setUp）让万行级脚本仍然可维护 |
| **混合负载场景** | ICTSS 2025 学术研究发现 Gatling 在混合负载场景中内存使用效率最佳 |

### 4.4 按团队画像快速匹配

```
你的团队画像                          最佳拍档
──────────                          ────────
QA 为主 + 不懂代码 + 多协议          → JMeter
开发团队 + JS/TS 栈 + Kubernetes     → k6
开发团队 + JVM 栈 + 大促压测         → Gatling
开发团队 + Python 栈 + 复杂业务链路  → Locust
大型企业 + SAP/Oracle/合规要求       → LoadRunner
```

---

## 五、知名企业与平台应用情况

### 5.1 JMeter — 行业覆盖率最广

由于 JMeter 是完全开源（Apache 2.0）且不需要注册即可使用，无法精确统计企业用户，但以下渠道可以确认其行业渗透率：

**国内大厂（公开分享/招聘 JD 可查证）：**

| 企业 | 使用场景 | 来源 |
|------|---------|------|
| **阿里巴巴** | 全链路压测平台基础引擎之一（早期），天猫双11压测体系参考了 JMeter 模式 | 公开技术博客 |
| **腾讯** | 内部压测平台底层引擎之一，配合自研调度系统 | 腾讯云性能测试服务文档 |
| **华为** | 云性能测试服务 CPTS 支持 JMeter 脚本导入和集群化执行 | 华为云官方文档 |
| **京东** | 618 大促压测体系中 JMeter 作为基础执行单元 | 京东技术博客 |
| **字节跳动** | 多个业务线的接口压测使用 JMeter | 招聘 JD |
| **美团** | 压测平台集成 JMeter 引擎 | 美团技术博客 |

**国际企业（公开案例）：**
- 几乎所有采用 JMeter 的公司都是通过自建平台使用，而非直接使用 GUI。JMeter 在 **LinkedIn、eBay、Spotify、Adobe、Twitter** 等公司的招聘 JD 和工程师博客中频繁出现。

### 5.2 k6 — Grafana 生态 + 互联网原生企业

| 企业 | 行业 | 使用场景 | 规模/效果 |
|------|------|---------|----------|
| **Just Eat Takeaway** | 在线外卖 | 全生产环境压测，覆盖 17 个国家 | 17,500+ RPS，4500万 请求/小时 |
| **Monta** | 电动汽车充电 | EV 充电基础设施的性能验证 | 22 万+ 充电设备监控 |
| **Synchrony Financial** | 银行/金融 | 金融 API 性能测试 | 年营收 161 亿美元 |
| **LiveRamp** | AdTech/数据 | Kubernetes 多集群可观测性 | 多云 Clean Room 环境 |
| **cronn GmbH** | 政府/医疗 | 公共卫生系统性能验证 | 本地 k6 + InfluxDB + Grafana |
| **Jimdo** | 网站建设 | 电商网站 Builder 性能 | - |
| **SpotOn** | POS 零售 | 销售点系统高并发验证 | - |
| **DrFirst** | 医疗 | 医疗数据 API 负载测试 | - |

**特征总结：** k6 的用户有明显共性——**云原生、自建 K8s、DevOps 文化、开发者自主选型**。典型替换路径：JMeter/Taurus/自研工具 → k6。

### 5.3 Gatling — 金融/支付/零售等对性能极度敏感的行业

| 企业 | 行业 | 关键成果 |
|------|------|---------|
| **Intuit** (TurboTax/QuickBooks) | 金融科技 | 1 亿+ 用户；从 <100 开发者 → 3000+ 开发者使用；年运行 **80,000+ 次压测**；97% 关键服务覆盖率 |
| **Attentive** | AI 移动营销 | 8000+ 品牌服务；从 6K RPS → **160K RPS/节点**；黑五/网一零故障 |
| **EPI Company (Wero)** | 欧洲支付 | 16 家欧洲主要银行支持；15+ Gatling Enterprise 用户；加密脚本优化降低基础设施成本 |
| **Shutterfly** | 电商/照片 | 10,000+ 员工；27 亿美元被收购；CI 集成性能门禁 |
| **TRAY** | POS 系统 | **响应时间降低 90%**（18-20s → 2s）；数据分析从 2 天 → 3 小时 |
| **SNCF** (法国国铁) | 交通运输 | 从专用压测团队 → 各项目自主压测 |
| **Popken Fashion** | 零售时尚 | 99.9% 全球请求 <1143ms |
| **InPost** | 物流 | 自动伸缩负载生成器，节省人力 |
| **Nickel** | 银行 | 部署从"数天的压力"变为"稳定且用户无感知" |

**数据规模：** 300,000+ 组织使用、3000万+ 开发者社区。Gatling 的用户画像明确——**需要极高并发吞吐量、精美报表用于向管理层汇报、愿意投入学习成本换取长期可维护性**。

---

## 六、开源参考平台

以下是与本项目定位最接近的开源性能测试**平台**（不是工具本身，而是在工具之上提供 Web 管理、脚本编辑、任务调度、结果展示等能力的平台）。

### 6.1 MeterSphere ⭐ ~14K

| 项目 | 详情 |
|------|------|
| **GitHub** | [metersphere/metersphere](https://github.com/metersphere/metersphere) |
| **许可证** | GPL v2 |
| **技术栈** | Spring Boot + Vue.js + MySQL + Kafka + MinIO + Docker |
| **定位** | 一站式开源持续测试平台 |

**核心能力：**
```
测试跟踪 + 接口测试 + UI测试 + 性能测试 + 团队协作
```

**性能测试模块特点：**
- 底层执行引擎：**JMeter**（完全兼容 .jmx）
- **脚本创建方式**（三者本质上都是生成 .jmx 文件然后上传）：
  1. **上传 .jmx 文件** — 先在 JMeter GUI 里做好脚本，上传到平台
  2. **引用接口自动化场景** — 把已有的接口测试用例一键转为性能测试
  3. **Chrome 插件录制** — 录制 HTTP 请求导出 .jmx，再上传到平台
- **⚠️ 没有步骤级可视化编辑** — 上传后的 .jmx 是黑盒，不能在 Web 上增删改 HTTP 请求、断言、提取器等步骤。只能调整执行参数（并发数/时长/Ramp-Up）和运行环境参数（CSV/Hosts/Properties）
- 分布式压测：Node Controller 在 Docker 中动态启动 JMeter 容器
- 压测结果通过 Kafka 流式回传，实时 ECharts 图表
- 定时任务调度（Quartz），CI/CD 集成（Jenkins/GitLab CI）
- 多次压测结果对比、历史趋势分析

**脚本管理的本质：**
```
JMeter GUI 创建脚本(.jmx) → 上传文件到平台 → Web 调执行参数 → 执行
                              ↑
                     .jmx 在此处是不可拆分的黑盒
```

**与你项目的对比：**

| 维度 | MeterSphere | 你的项目 |
|------|------------|---------|
| **执行引擎** | JMeter（固定） | JMeter（当前）+ 远期多引擎 |
| **脚本创建** | 上传.jmx / 录制 / 接口场景转 | 上传.jmx / 可视化新建 |
| **步骤级可视化编辑** | ❌ .jmx 是黑盒 | ✅ Parser解析为可编辑步骤 |
| **在线改 HTTP 请求** | ❌ | ✅ |
| **在线改断言/提取器** | ❌ | ✅ |
| **调整执行参数** | ✅ | ✅ |
| **分布式** | Node Controller + Docker | 规划中：Agent 远程代理 |
| **定位差异** | 大而全的测试管理平台 | 聚焦性能测试的专业平台 |
| **可参考点** | Node Controller 架构、Kafka 流式结果回传 | Agent 设计、多引擎抽象 |

### 6.2 stressTestSystem

| 项目 | 详情 |
|------|------|
| **GitHub** | [dasu23/stressTestSystem](https://github.com/dasu23/stressTestSystem) |
| **Gitee** | [smooth00/stressTestSystem](https://gitee.com/smooth00/stressTestSystem) |
| **技术栈** | Spring Boot + JMeter API + Vue 2.x + MySQL |
| **定位** | 基于 JMeter 的轻量级在线压测平台 |

**特点：**
- 基于人人-fast（renren-fast）开发平台二次开发
- 内核基于 JMeter 5.4.1 API，通过程序化方式操控 JMeter（而非 CLI `ProcessBuilder`）
- **脚本创建方式：必须先在本机 JMeter GUI 做好 .jmx，上传到平台**
- **仅有的"在线编辑"：用 dom4j 解析 JMX XML，提取线程组参数（线程数/Ramp-Up/循环次数/调度时长）入库，允许在 Web 上修改这些参数并同步回 JMX。HTTP 请求、断言、提取器等核心内容仍然是黑盒**
- ECharts 实时监控压测指标
- Quartz 定时任务支持
- 支持 JMeter 分布式压测

**脚本管理的本质：**
```
JMeter GUI 创建脚本(.jmx) → 上传文件 → Web 调线程组参数 → 同步参数回 JMX → 执行
                                           ↑
                              只有线程组参数可调，其余全是黑盒
```

**与你项目的差异：**
- stressTestSystem 直接使用 JMeter Java API（`StandardJMeterEngine`），你用的是 CLI `ProcessBuilder`。Java API 方式更紧密集成但 JMeter 版本升级时改动大；CLI 方式解耦好但过程控制弱。
- 前端技术栈旧（Vue 2.x），你用的是 Vue 3 + Element Plus

### 6.3 Perfana — 多引擎性能可观测性平台

| 项目 | 详情 |
|------|------|
| **定位** | 性能测试的"可观测性中间层"——不管理脚本，专注结果验证和可视化 |
| **GitHub** | [github.com/perfana](https://github.com/perfana) |
| **许可证** | Apache 2.0 |
| **集成引擎** | JMeter、Gatling、k6、NeoLoad |
| **技术栈** | Java + Grafana + InfluxDB/Prometheus |

**核心能力：**
- **统一结果收集**：`x2i` 工具将 JMeter/Gatling/k6 的原始结果统一写入 InfluxDB
- **自动化阈值验证**：测试结束后自动对比预设阈值
- **Grafana 仪表盘自动配置**：按 Profile 自动生成标准化看板
- **CI/CD 质量门禁**：作为 Pipeline 的 Gate 节点
- **时间序列对比**：多次压测结果在同一看板中对比

**与你项目的参考价值：**
- Perfana 的**多引擎统一结果层**设计思路值得借鉴——如果未来你要支持多引擎，可以设计一个 `ExecutionResult` 统一接口，不同引擎的结果 parser 实现同一个接口
- 但它**不管理脚本和任务调度**，这恰好是你的平台的核心价值，两者是互补关系

### 6.4 Taurus — 多引擎 YAML 抽象层

| 项目 | 详情 |
|------|------|
| **GitHub** | [Blazemeter/taurus](https://github.com/Blazemeter/taurus) |
| **Stars** | ~2.1K |
| **许可证** | Apache 2.0 |
| **定位** | 用 YAML 描述测试，自动翻译到 JMeter/Gatling/k6/Locust 执行 |

**特点：**
```yaml
execution:
- concurrency: 100
  ramp-up: 30s
  hold-for: 5m
  scenario: ecommerce

scenarios:
  ecommerce:
    requests:
    - url: /api/login
      method: POST
      body: '{"username":"admin","password":"123456"}'
      extract-jsonpath:
        auth_token: $.data.token
    - url: /api/products
      headers:
        Authorization: Bearer ${auth_token}
```

- YAML 中间层屏蔽了不同引擎的脚本差异
- 支持自动选择执行引擎（默认 JMeter）
- CI/CD 友好（`bzt test.yml` 单命令）

**与你项目的参考价值：**
- Taurus 证明了一个重要假设：**在引擎之上建立通用 DSL/IR 层是可行的**
- 你的 `ScriptStepDefinition` 可以类比 Taurus 的 YAML 作为中间表示
- 但 Taurus 的 YAML 表达能力有限（复杂逻辑仍需回退到原生脚本），你的可视化编辑器可以在这一点上做得更好

### 6.5 开源平台总结 — 与本项目的定位差异

```
                        脚本管理    步骤可视化    多引擎    远程代理    结果分析    开源
                        ────────    ────────    ──────    ────────    ────────    ──
MeterSphere             ✅ 上传.jmx  ❌ 黑盒      ❌ JMX    ✅ 容器      ✅ ECharts  ✅
stressTestSystem        ✅ 上传.jmx  ❌ 黑盒      ❌ JMX    ❌           ✅ ECharts  ✅
Perfana                 ❌          ❌           ✅       ❌           ✅ Grafana  ✅
Taurus                  ❌          ❌           ✅       ❌           ❌           ✅
BlazeMeter (商业)        ✅          ❌ JMX属性   ✅       ✅ 云节点     ✅          ❌
OctoPerf (商业)          ✅          ❌ JMX属性   ❌ JMX    ✅ 云节点     ✅          ❌
你的项目                  ✅ 上传+新建 ✅ 步骤级     ❌→规划中 ❌→规划中    ⚠️ 基础     ✅
```

**各平台"脚本编辑"的真实水平：**

| 平台 | 能做什么 | 不能做什么 |
|------|---------|-----------|
| **MeterSphere** | 上传.jmx、调整执行参数（并发/时长/RPS） | 不能在线增删改 HTTP 请求、断言、提取器 |
| **stressTestSystem** | 上传.jmx、调整线程组参数（线程数/Ramp-Up/循环） | 除了线程组参数，其余全是黑盒 |
| **BlazeMeter/OctoPerf** | 上传.jmx、在 Web 上编辑 JMX 属性（模拟 JMeter GUI 的表单填写） | 本质上还是填 XML 属性值，没有步骤化抽象 |
| **你的项目** | 解析 JMX → `ScriptStepDefinition` 步骤树 → 可视化增删改每一步（请求/断言/提取器/Header/CSV） | 目前只支持 JMeter 引擎 |

**你的差异化机会：**
1. MeterSphere 虽然功能全面但**大而全**，性能测试只是其四大模块之一，专业性不如专注做压测的平台
2. 你的 Parser/Renderer 步骤级可视化编辑器是**所有已知开源平台都没有的能力**——MeterSphere/stressTestSystem 把 .jmx 当黑盒管理，BlazeMeter/OctoPerf 只是在 Web 上模拟 JMeter GUI 填属性值，只有你做到了真正把脚本拆成结构化可编辑的步骤树
3. 市场上**没有同时满足"步骤级可视化编辑 + 多引擎支持 + 远程代理执行 + 开源"的平台**——这是你的蓝海

---

## 七、架构流派总结

```
┌──────────────────────────────────────────────────────┐
│                执行引擎三大架构流派                     │
├─────────────────┬────────────────┬───────────────────┤
│  线程模型        │  事件驱动       │  多组件分离式      │
│  (同步阻塞)      │  (异步非阻塞)   │  (商业企业级)     │
├─────────────────┼────────────────┼───────────────────┤
│  JMeter         │  k6 (goroutine)│  LoadRunner       │
│  1线程=1VU      │  Gatling(Akka) │  VuGen+Controller │
│  资源重/上限低   │  Locust(gevent)│  +LG+Analysis     │
│                 │  资源轻/上限高  │  全套件/最贵       │
└─────────────────┴────────────────┴───────────────────┘
```

**2025-2026 行业趋势：**
1. **事件驱动成为主流** — 异步非阻塞模型在资源效率上有数量级优势
2. **平台化/多引擎调度** — BlazeMeter 等抽象层允许"自带脚本"，平台负责调度和聚合
3. **Test-as-Code** — 脚本以代码形式存入 Git，CI/CD 自动触发，性能左移
4. **JMeter 仍占最大份额**（社区惯性 + 协议覆盖），但新增项目更多选择 k6 或 Gatling

---

## 八、针对本项目的选型分析

### 8.1 本项目当前架构

```
┌──────────────┐     ┌────────────────┐     ┌──────────────┐
│  Vue 3 前端   │────▶│  Spring Boot   │────▶│  JMeter CLI  │
│  脚本编辑器    │     │  REST API      │     │  ProcessBuilder│
│  任务调度      │     │                │     │  本地进程执行  │
└──────────────┘     └────────────────┘     └──────────────┘
                             │
                     ┌───────┴───────┐
                     │  文件系统存储   │
                     │  .jmx / .jtl   │
                     └───────────────┘
```

**现状特点：**
- 脚本层：基于 JMeter .jmx，自研 `JmeterScriptParser`/`JmeterScriptRenderer` 做可视化编辑，已解决 XML 维护痛点
- 执行层：`JmeterCommandExecutor` 通过 `ProcessBuilder` 在本服务器启动 JMeter 进程
- 存储层：脚本和执行结果落本地文件系统

### 8.2 远期架构设想 — 远程代理执行

> **核心需求：脚本执行不会在当前平台服务器上运行，需要将执行任务下发到与被测系统同网段/同机房的代理设备上执行，减少网络因素对性能测试结果的干扰。**

这意味着当前架构需要从"本地进程调用"演进为"远程代理调度"：

```
                        未来目标架构

 ┌──────────────┐        ┌─────────────────────┐
 │  管理平台      │───────▶│  执行调度层           │
 │  (Spring Boot) │       │  - 代理注册/发现      │
 │  - 脚本管理    │        │  - 任务分发           │
 │  - 任务编排    │        │  - 结果回传           │
 │  - 结果展示    │        │  - 心跳/健康检查       │
 └──────────────┘        └──────┬──────┬──────┘
                                │      │
                    ┌───────────┘      └───────────┐
                    ▼                               ▼
        ┌──────────────────┐          ┌──────────────────┐
        │  Agent (同网段A)   │          │  Agent (同网段B)   │
        │  - JMeter/k6 CLI  │          │  - JMeter/k6 CLI  │
        │  - 脚本下载/缓存    │          │  - 脚本下载/缓存    │
        │  - 结果上报        │          │  - 结果上报        │
        └──────┬───────────┘          └──────┬───────────┘
               │                              │
               ▼                              ▼
        ┌──────────┐                   ┌──────────┐
        │ 被测系统A  │                   │ 被测系统B  │
        └──────────┘                   └──────────┘
```

### 8.3 选型影响分析

在"远程代理执行"架构下，各工具的适配评估：

| 评估维度 | JMeter (当前) | k6 | Gatling | Locust | LoadRunner |
|---------|:-----------:|:--:|:-------:|:------:|:----------:|
| **代理端部署复杂度** | 需要 JDK + JMeter 完整安装包 | ✅ 单二进制文件，零依赖 | 需要 JDK + Scala/SBT | 需要 Python + 依赖包 | 需要 Windows + 专有组件 |
| **CLI 执行友好度** | ⭐⭐ 参数冗长 | ⭐⭐⭐⭐⭐ 单命令 | ⭐⭐⭐ Maven/Gradle | ⭐⭐⭐ Python 命令 | ⭐⭐ PC 插件依赖 |
| **脚本分发体积** | 纯 .jmx 文本，极轻 | 纯 .js 文本，极轻 | 编译 .jar 或源码 | 纯 .py 文本，极轻 | 专有格式，体积大 |
| **结果回传格式** | .jtl (XML/CSV) | JSON 结构化 | HTML 报告 | CSV/JSON | 专有 Analysis 格式 |
| **代理资源开销** | ⭐⭐ 需较多内存 | ⭐⭐⭐⭐⭐ 极低 | ⭐⭐⭐⭐ 低 | ⭐⭐⭐ 中 | ⭐⭐ 高 |
| **Docker/容器化** | ✅ | ✅ 原生 | ✅ | ✅ | ❌ 需 Windows |
| **社区生态/国产化** | ✅ 最成熟 | ✅ 成长中 | ⚠️ 国内较小 | ⚠️ 中等 | ❌ 商业闭源 |

### 8.4 核心建议

**短期（维持 JMeter，构建代理层）— 推荐**

当前你的项目已经在 JMeter 之上做了大量投入（JMX Parser/Renderer、可视化编辑器），建议继续以 JMeter 为核心执行引擎，将重心放在构建 **远程代理调度层**：

- **Agent 设计**：在被测环境同网段部署轻量 Agent（Spring Boot 子模块或独立 Go 程序），负责接收任务、拉取脚本、执行 JMeter CLI、上报结果
- **通信方式**：管理平台 ↔ Agent 之间通过 HTTP/gRPC 通信，Agent 主动注册并维持心跳
- **脚本下发**：平台推送 scriptVersionId + 配置，Agent 从平台 API 拉取 .jmx 文件到本地临时目录
- **结果回传**：执行完成后 Agent 将 .jtl 和日志回传或由平台主动拉取

**中长期（多引擎支持）— 可选演进**

当代理层稳定后，可以考虑在 Agent 中支持多执行引擎：

- **新增 k6 引擎**：对于纯 HTTP API 场景，k6 的脚本更简洁（JS）、单机并发更高、资源更省。你的可视化脚本编辑器可以扩展一个"导出为 k6 脚本"的能力
- **新增 Gatling 引擎**：如果团队有 JVM 背景，Gatling 的单机并发能力和报告质量可覆盖大促级场景

**不建议引入 LoadRunner**：商业闭源、许可昂贵、部署笨重，与你的自研平台理念冲突。

### 8.5 远程代理执行的技术关键点

```
代理架构核心关注点：

1. 网络近源部署
   - Agent 与被测系统必须在同一网段/可用区
   - 避免跨机房/跨公网导致网络延迟污染性能数据
   - 建议按"环境标签"（SIT/UAT/PRD）自动匹配 Agent

2. 任务调度策略
   - 同一 Agent 的并发执行数需要限制（受 Agent 机器资源约束）
   - 支持排队机制：Agent 繁忙时任务进入等待队列
   - 支持优先级调度：手动触发 > 定时触发

3. 断连容错
   - Agent 断连后，已下发的任务应能在 Agent 本地继续执行
   - 重连后自动上报结果和状态
   - 平台侧需要有超时检测：超过 N 分钟未收到心跳则标记 Agent 离线

4. 脚本和结果文件管理
   - 脚本由平台统一管理，Agent 仅按需下载到临时目录
   - 执行结果优先回传到平台存储（统一查询和分析）
   - Agent 本地定期清理过期文件，避免磁盘占用

5. 安全
   - Agent 注册需要 Token 认证
   - 通信链路加密（HTTPS/mTLS）
   - 脚本内容不落地明文存储（或执行后立即清理）
```

---

## 九、选型速查

| 你的场景 | 推荐工具 | 核心理由 |
|---------|---------|---------|
| QA 团队、多协议、零代码 | **JMeter** | 协议最广、GUI 上手快、社区最大（当前已采用） |
| DevOps/云原生、CI/CD 集成 | **k6** | CLI 原生、资源最省、可观测性强 |
| 大促压测、精美报告、JVM 团队 | **Gatling** | 单机并发最强、HTML 报告业界最佳 |
| Python 团队、复杂业务链路 | **Locust** | 纯 Python 最灵活、自定义协议最容易 |
| 大型企业、SAP/Oracle/合规 | **LoadRunner** | 企业协议全覆盖、商业支持保障 |
| **本项目短期策略** | **JMeter + 远程 Agent** | 复用已有 JMX 工具链，重点建设代理调度层 |
| **本项目中长期策略** | **多引擎 (JMeter + k6)** | JMeter 覆盖多协议，k6 覆盖高性能 API 场景 |
