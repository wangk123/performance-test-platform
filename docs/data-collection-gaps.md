# 数据采集缺口清单

> 2026-09-21 盘点。按优先级排序，逐项完善后打勾。现状详表见本文末尾「已支持能力」。

## P0 — 定位根因能力

- [x] **P0-1 接入 trace 链路追踪**
  现状：5 类深度源只有 db-metrics 真实实现，trace 是 `UnavailableDeepProbe` 占位（`evidence/deep/`）。
  要做：APM 选型（OTel / SkyWalking）→ 写一个 `DeepEvidenceProbe` 适配器；失败样本收集器从响应头提取 traceId；执行配置加「容量轮/诊断轮」标记（agent 开销与压测轮次解耦）。
  Phase 2 余项：内置瀑布图（抽屉现为摘要+SkyWalking deep-link 位）、设置页观测数据源 UI、报告页曲线、span 维度服务分布。
- [ ] **P0-2 应用日志采集**
  压测时间窗内应用报错查不了，需对接日志平台，按 时间窗 + 服务 + traceId 过滤。
- [ ] **P0-3 慢 SQL + explain**
  现状只有 Prometheus 侧慢查询计数，拿不到具体 SQL 与执行计划。trace 落地后先拿到慢 SQL 语句，再补 explain 直连被测库。
- [ ] **P0-4 profiling / JFR 火焰图**
  CPU 高但 RT 正常时唯一手段。JFR 支持运行时动态开启（`jcmd JFR.start`，开销 <1%、零重启），四类深度源中唯一可「压测中按需开」，优先于 javaagent 注入研究。

## P1 — 中间件监控补全

- [ ] **P1-1 exporter 连接参数可配**
  `MonitorItem` 无连接串/凭据字段，`main.py:148-175` 硬编码 `127.0.0.1` 默认端口与 `user:pass` 占位，真环境连不上。模型加字段 + 打通部署参数。
- [ ] **P1-2 中间件指标查询与面板**
  `MetricKind` 只有 11 种（6 服务器 + 5 JVM），MySQL/Redis/Nginx/Kafka 采到了也无处展示。补 PromQL 模板 + 前端面板。
- [ ] **P1-3 JFR/javaagent 自动挂载可行性研究**
  javaagent 只能启动时挂（研究终点≈「改启动参数+重启」的自动化）；JFR 可运行时 attach，精力优先放 JFR。
- [ ] **P1-4 exporter 生命周期**
  只有「部署」无停止/卸载/存活巡检。
- [ ] **P1-5 容器化被测目标的监控方案**
  现有部署模型面向物理机/虚机：SSH 到宿主机装 node_exporter，看到的是整机资源。被测目标跑 Docker 时：①宿主机 exporter 看不到单容器 CPU/内存/网络/IO；②JMX javaagent 需容器启动时挂（改 Dockerfile 或 volume 挂载 agent + `JAVA_TOOL_OPTIONS`），平台无法事后注入。方向：cAdvisor 采集 per-container 指标、MonitorTarget 支持容器维度（容器 ID/名称/label 选择器）、JVM agent 容器化挂载指引；K8s 场景（kube-state-metrics + cAdvisor）远期。

## P2 — 中间件扩面

- [ ] **P2-1 RocketMQ exporter**（类型枚举 + 二进制 + 部署 + 查询 + 面板，全链无）
- [ ] **P2-2 Elasticsearch exporter**
- [ ] **P2-3 PostgreSQL / MongoDB / RabbitMQ**（按需）

## P3 — 压测数据细节

- [ ] **P3-1 秒级序列补 P90/P99**：现只存 avg+p95，长尾恶化跑的过程中看不到。
- [ ] **P3-2 节点级 TPS 拆分**：多压力机合计数，单机拉胯发现不了。
- [ ] **P3-3 JTL 落盘开关**：现为 `discard.jtl` 直接丢弃，事后查不了单笔请求。
- [ ] **P3-4 失败样本截断可见性**：per-label 50 / 全局 1000 条，截断时前端应提示「仅保留前 N 条」。
- [ ] **P3-5 报告补资源曲线**：报告页只有压测指标，同时间窗 CPU/内存曲线（监控快照已有数据）画到同一视图。

## P4 — 治理与杂项

- [ ] **P4-1 数据生命周期**：无 TTL/归档；删执行时 jmeter.log、JMX、直方图 blob 残留。
- [ ] **P4-2 告警**：Prometheus 无告警规则，错误率/CPU/exporter 存活无人通知。
- [ ] **P4-3 取证快照增厚**：现为纯统计 JSON，不带原始文件（JTL/日志/直方图 blob）。
- [ ] **P4-4 假配置清理**：`snapshot-flush-ms`、`histogram-*` 等配置项未接线到注入脚本（Groovy 内硬编码），接线或删除。
- [ ] **P4-5 JVM 面板多实例**：现单选一个 JVM 实例，多实例场景看不全。

---

## 已支持能力（现状速查）

| 通道 | 采集内容 | 状态 |
|---|---|---|
| 压测指标（自研管线） | 聚合报告 avg/median/p50-p99/error%/TPS；秒级 samples/throughput/avg/p95；失败样本完整请求响应（50/1000 条上限）；终态直方图 blob + jmeter.log + JMX；JTL 丢弃 | ✅ 完整 |
| 资源监控（Prometheus） | node_exporter：CPU/负载/内存/磁盘 IO/网络/TCP；JMX agent：堆/GC/线程/进程 CPU；执行结束序列快照固化；SSH 一键部署 + 文件服务发现 | ✅ 可用（中间件除外） |
| 深度证据 | db-metrics（Prometheus 探针：连接池/慢查询/锁等待）；trace/app-log/slow-sql/profiling 占位 | ⚠️ 1/5 |
| 执行辅助 | envcheck 环境检查（磁盘/ulimit/内核参数/端口/JVM/MySQL）；取证快照（审批后 4 源统计 JSON） | ✅ |
