# 性能测试平台 (Performance Test Platform)

基于 Spring Boot 3 + Vue 3 的企业级性能测试管理平台，支持 JMeter 脚本管理、可视化编辑、分布式执行和实时监控。

## 功能矩阵

| 模块 | 核心功能 | 状态 |
|------|---------|------|
| 📋 项目管理 | 项目 CRUD、归档/恢复、成员管理、仪表盘 | ✅ |
| 📝 脚本管理 | JMX 上传/下载、版本管理、可视化步骤编辑器 | ✅ |
| ✏️ 脚本编辑器 | 拖拽排序、HTTP 调试、curl/XML 导入、7 种步骤类型 | ✅ |
| ⚡ 测试执行 | 计划+场景管理、单机/远程执行、SSE 实时流、采样浏览器 | ✅ |
| 📊 聚合报告 | 吞吐量/响应时间/错误率统计、按标签明细、图表快照 | ✅ |
| 🖥️ 执行节点 | SSH 远程节点注册、健康检查、批量初始化、密钥部署 | ✅ |
| 📈 监控采集 | Prometheus 集成、CPU/内存/JVM 指标、代理一键部署 | ✅ |
| 📄 报告生成 | 计划维度报告、HTML 预览、Word/PDF 导出、报告对比 | ✅ |
| 🏭 造数工厂 | 数据源/采集策略/快照样本/相邻 Diff 分析/模板确认/克隆写库 | ✅ |
| 📚 函数库 | Java JMeter Function 只读展示、函数包下载、编辑器插入 | ✅ |
| 🔧 辅助脚本 | 前置/后置脚本、失败策略、执行日志 | ✅ |
| 🔗 Git 日志 AI | Git 仓库/代码绑定、日志制品检索、确定性分析驱动的 AI 建议 | ✅ |

> 📋 = 远期规划 &nbsp;|&nbsp; 完整需求清单见 [需求规格说明书](docs/requirements-spec.md)。模块状态以 `docs/agent-platform-buildout-tasks.md` 与代码实现为准。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.14 |
| 语言 | Java | 17 |
| 构建 | Gradle | wrapper |
| ORM | Spring Data JPA + Hibernate | 6.x |
| 数据库 | MySQL 8.0（运行时唯一数据源，默认连测试服务器；H2 仅测试内存库） | - |
| 前端框架 | Vue 3 + TypeScript | 3.5 |
| 构建 | Vite | 7.x |
| UI 库 | Ant Design Vue | 4.x |
| 图表 | ECharts + vue-echarts | 6.x |
| 代码编辑器 | CodeMirror | 6.x |
| 测试引擎 | Apache JMeter | 5.6.3 |
| 远程执行 | Python + paramiko + Docker | 3.x |
| JMeter 指标采集 | JSR223 Groovy + HdrHistogram（自研） | - |
| 资源监控 | Prometheus + Exporters | - |

## 监控方案

平台采用**双通道**监控架构：压测性能指标走自研采集管线，被测系统资源指标走 Prometheus。

### 架构总览

```
                            JMeter 执行节点
                                  │
                    ┌─────────────┴─────────────┐
                    │  JSR223 Groovy 监听器注入   │
                    │  (JmeterBackendListener   │
                    │   Injector)                │
                    └─────────────┬─────────────┘
                                  │
              ┌───────────────────┴───────────────────┐
              ▼                                       ▼
    聚合快照收集器                              失败样本收集器
    HdrHistogram 直方图                         JSONL 行式文件
    每 ~1s 写 .bin 快照                          仅记录失败请求
              │                                       │
              └───────────────────┬───────────────────┘
                                  │ SSH 轮询拉取
                                  ▼
    ┌─────────────────────────────────────────────────────┐
    │                   后端 Java                          │
    │                                                     │
    │  AggregateSnapshotCodec  ExecutionMetricSeriesService│
    │  解码 + 合并快照          计算增量 → MySQL           │
    │                          SSE 广播 metric-tick       │
    │                                                     │
    │  FailureSampleIngestor    FailureSampleStore         │
    │  解析 JSONL               SQLite 持久化              │
    │  SSE 广播 sample                                    │
    └─────────────────────────┬───────────────────────────┘
                              │ SSE + HTTP
                              ▼
    ┌─────────────────────────────────────────────────────┐
    │                   前端 Vue                           │
    │                                                     │
    │  TaskMonitoringCharts    ExecutionDetailView         │
    │  ECharts 实时 TPS/RT 图   聚合报告 + 采样浏览器       │
    └─────────────────────────────────────────────────────┘


    资源指标通道（Prometheus）：

    Node / MySQL / Redis / Nginx / Kafka / JMX Exporter
                              │
                              ▼
                        Prometheus
                              │ HTTP API (PromQL)
                              ▼
                    PrometheusQueryClient (后端)
                              │ REST API
                              ▼
              TargetServerMetricsPanel / TargetJvmMetricsPanel (前端)
```

### 通道一：JMeter 性能指标（自研管线）

传统的 JMeter + InfluxDB + Grafana 方案需要在执行节点上额外部署和维护 InfluxDB 实例，且指标聚合粒度和展示形式受 Grafana Dashboard 限制。本平台用自定义 JSR223 Groovy 监听器替代了这个链路。

**核心组件**：

| 组件 | 文件 | 职责 |
|------|------|------|
| 监听器注入器 | `JmeterBackendListenerInjector.java` | 移除脚本中的 InfluxDB Backend Listener，注入自研 JSR223 监听器 |
| 聚合快照收集器 | (注入的 Groovy 脚本) | 每个样本更新 HdrHistogram，每秒输出二进制快照 |
| 失败样本收集器 | (注入的 Groovy 脚本) | 仅采集失败请求的完整请求/响应信息 |
| 快照解码器 | `AggregateSnapshotCodec.java` | 解码并合并多节点 HdrHistogram 快照 |
| 指标序列服务 | `ExecutionMetricSeriesService.java` | 计算增量指标（吞吐量/响应时间/P95），持久化到 MySQL，SSE 广播 |
| 失败样本摄取 | `FailureSampleIngestor.java` | SSH 增量拉取 JSONL，存入 SQLite，SSE 广播 |
| 实时图表 | `TaskMonitoringCharts.vue` | ECharts 渲染 TPS/响应时间/错误率趋势图 |

**相比 InfluxDB + Grafana 方案的优势**：

- 零额外依赖，无需在执行节点上部署 InfluxDB
- 指标计算在平台后端完成，可自定义聚合逻辑和分位值精度（P90/P95/P99）
- 失败样本自动采集完整请求/响应，无需额外抓包
- SSE 实时推送比轮询延迟更低
- 前端图表集成在平台内，无需跳转 Grafana

### 通道二：被测系统资源指标（Prometheus）

用于采集压测期间被测服务器的 CPU、内存、磁盘、网络、JVM 等资源指标。

**核心组件**：

| 组件 | 职责 |
|------|------|
| `PrometheusQueryClient.java` | 通过 PromQL 查询 Prometheus API |
| `PrometheusFileSdWriter.java` | 动态写入 Prometheus 文件服务发现目标 |
| `MonitorDeployService.java` | 通过 SSH 一键部署 Exporter 到目标服务器 |
| `TargetMetricsService.java` | 编排目标指标查询 |
| `usePrometheusSeries.ts` | 前端轮询 Prometheus 时序数据 |
| `TargetServerMetricsPanel.vue` | 服务端指标面板 |
| `TargetJvmMetricsPanel.vue` | JVM 指标面板 |

**部署 Prometheus**（可选，仅资源指标通道需要）：

```bash
cd deploy/monitoring
docker compose up -d     # 仅启动 Prometheus
# 验证：http://localhost:9090
```

Prometheus 需要能访问目标服务器上的 Exporter 端口。平台通过 `MonitorDeployService` 将 `deploy/monitoring/prometheus/` 下的 Exporter 二进制通过 SSH 部署到目标服务器。

## 项目结构

```
performance-test-platform/
├── backend/                        # Spring Boot 后端
│   ├── src/main/java/com/yr/perftest/platform/
│   │   ├── api/                    # REST 控制器
│   │   ├── config/                 # Spring 配置
│   │   ├── execution/              # JMeter 执行引擎
│   │   │   ├── aggregate/          #   聚合报告 + 时序指标
│   │   │   ├── distributed/        #   执行节点管理
│   │   │   └── failure/            #   失败样本存储
│   │   ├── identity/               # 用户认证
│   │   ├── monitoring/             # Prometheus 监控
│   │   ├── project/                # 项目 + 成员
│   │   ├── script/                 # JMX Parser/Renderer + 版本
│   │   └── task/                   # 测试计划 + 场景 + 执行
│   └── src/main/resources/
│       ├── application.yml         # 应用配置（默认连测试服务器 MySQL）
│       └── db/migration/           # Flyway 迁移脚本（V1__baseline.sql 全量建表基线）
├── frontend/                       # Vue 3 前端
│   └── src/
│       ├── api/                    # HTTP 客户端 + API 模块
│       ├── components/             # 可复用组件
│       │   ├── dialogs/            #   对话框
│       │   ├── editor/             #   脚本编辑器（步骤树/详情/HTTP配置）
│       │   ├── execution/          #   执行监控图表
│       │   ├── task-plans/         #   测试计划+场景+执行详情
│       │   └── views/              #   页面级组件
│       ├── composables/            # 组合式 API（状态管理）
│       ├── router/                 # 路由配置
│       └── utils/                  # 工具函数
├── deploy/                         # 部署配置
│   └── monitoring/                 # Prometheus 监控栈
│       ├── docker-compose.yml
│       └── prometheus/             # Exporter 二进制 + 配置
├── remote-runner/                  # Python SSH 远程执行器
│   └── remote_jmeter_runner/
│       └── main.py                 # 远程 JMeter 执行脚本
├── docs/                           # 文档
│   ├── requirements-spec.md        # 功能需求规格说明书
│   ├── requirements-and-architecture.md  # 需求与架构总览
│   ├── development-plan.md         # 开发计划
│   ├── platform-vision.md          # 远期目标
│   ├── modules/                    # 模块详细设计（11个）
│   └── README.md                   # 文档索引
└── openspec/                       # OpenSpec 规格文件
    └── specs/                      # 当前规格
```

---

## 快速开始（本地开发）

### 环境要求

| 工具 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 17+ | 推荐 17.0.17 |
| Node.js | 20+ | 通过 nvm 管理 |
| Gradle | wrapper | `./gradlew` 自动下载 |
| JMeter | 5.6.3 | 本地执行压测需要 |
| Python | 3.x | 远程执行需要（可选） |

### 1. 启动后端

后端默认直连测试服务器上的 MySQL（`192.168.17.216:3306/perftest`，已由 Docker Compose 常驻部署，见[完整部署方案](#完整部署方案)）。首次启动 Flyway 自动建表（V1 全量基线）并写入种子账号，无需手工初始化。

```bash
# 设置 JDK 17
export JAVA_HOME=/path/to/jdk-17/Contents/Home/

# 启动 Spring Boot（默认端口 8080，连 MySQL，Flyway 自动建表）
./gradlew :backend:bootRun
```

> 测试服务器不可达时，可本机起一个 MySQL 后用环境变量覆盖数据源，见方案 A-2。

后端启动后：
- API 服务：`http://localhost:8080`
- 默认账户：`admin` / `admin123` 和 `tester` / `tester123`（Flyway 建表后由平台 seed 写入）

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev        # 开发服务器 → http://localhost:5173
```

前端开发服务器自动将 `/api` 请求代理到 `http://127.0.0.1:8080`。

### 3. 运行后端测试

```bash
# 全量测试（H2 内存库 + Flyway V1，不需要 Docker）
./gradlew :backend:test

# 真 MySQL 集成测试（Testcontainers，需 Docker 可达，见方案 A-5）
./gradlew :backend:testMysql

# 单个测试类
./gradlew :backend:test --tests "com.yr.perftest.platform.api.PlatformApiBehaviorTest"
```

### 4. 构建前端

```bash
cd frontend
npm run build      # type-check + 生产构建
npm run preview    # 预览生产构建
```

---

## 完整部署方案

数据库口径（D14）：**MySQL 8.0 是运行时唯一数据源**。schema 由 Flyway 管理（`backend/src/main/resources/db/migration/V1__baseline.sql` 全量基线，50 表；`ddl-auto: validate`），**没有手工建表脚本**——应用首次启动自动建表并写入种子账号。H2 仅作为测试内存库。

### 方案 A：本地开发（连 MySQL）

#### A-1 默认直连测试服务器（零配置）

测试服务器 216 已通过 Docker Compose 常驻部署 MySQL（容器 `perftest-mysql`，utf8mb4，账号口径见 `deploy/mysql/docker-compose.yml`）。`application.yml` 默认指向 `192.168.17.216:3306/perftest`，内网环境 `./gradlew :backend:bootRun` 即可。

#### A-2 本机 MySQL（离线/隔离环境）

```bash
# 1. 本机起 MySQL（mysql:8.0，utf8mb4 服务器级，root/perftest-root，应用账号 perftest/perftest）
docker compose -f deploy/mysql/docker-compose.yml up -d

# 2. 环境变量覆盖数据源（Spring Boot 宽松绑定：SPRING_DATASOURCE_* 覆盖 application.yml 的 spring.datasource.*）
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/perftest?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME=perftest
export SPRING_DATASOURCE_PASSWORD=perftest

# 3. 启动后端
./gradlew :backend:bootRun
```

> `characterEncoding` 必须用 Java 字符集名 `UTF-8`——Connector/J 9 拒绝 MySQL 词法 `utf8mb4`，而 UTF-8 即映射 MySQL utf8mb4。

#### A-3 完整启动示例

```bash
# 1. 克隆仓库
git clone <repo-url> && cd performance-test-platform

# 2. 配置 JDK 与 JMeter 路径（macOS/Linux）
export JAVA_HOME=/path/to/jdk-17/Contents/Home/
export JMETER_EXECUTABLE=/path/to/apache-jmeter-5.6.3/bin/jmeter
export JMETER_JAVA_HOME=$JAVA_HOME

# 3. 启动后端（Flyway 自动建表 + 种子账号）
./gradlew :backend:bootRun

# 4. 启动前端（另一个终端）
cd frontend && npm install && npm run dev

# 5. 访问
open http://localhost:5173
```

#### A-4 已有 H2 数据迁往 MySQL（一次性）

存量 H2 文件库（旧版默认 `./storage/perftest`，即 `perftest.mv.db`）由内置迁移工具一次带入：挂 `app.data-migration.h2-source` 属性启动一次即可（旧库只读；目标非空拒跑，确认覆盖加 `app.data-migration.overwrite=true`）：

```bash
java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.jar \
  --app.data-migration.h2-source=./storage/perftest
```

- 迁移完成（日志输出 `MigrationSummary`：表数/行数）后，**人工归档旧 `perftest.mv.db`**（工具不删不改源文件）。
- 迁移完成后**移除 `app.data-migration.h2-source` 属性**再正常启动。
- 表清单维护：新增 JPA 实体/表时须同步 `DataMigrationRunner.MIGRATION_TABLES`（见下方贡献者约定）。

#### A-5 运行 testMysql（真 MySQL 集成测试）

`testMysql` 用 Testcontainers 起真 MySQL 8.0 容器（`@Tag("mysql")`，默认 `test` 套件已显式排除）。需要 Docker 可达；本机无 Docker 时可经 SSH unix-socket 隧道驱动远程 Docker（已在 216 验证通过）：

```bash
# 1) 隧道：本机 → 远端 /var/run/docker.sock
ssh -fNT -L /tmp/p024-docker.sock:/var/run/docker.sock root@<server>

# 2) 远程 Docker 跑 testMysql（DOCKER_HOST 等须在 Gradle daemon 启动时在场，必要时先 ./gradlew --stop）
DOCKER_HOST=unix:///tmp/p024-docker.sock \
TESTCONTAINERS_HOST_OVERRIDE=<server-ip> \
TESTCONTAINERS_RYUK_DISABLED=true \
./gradlew :backend:testMysql
```

> `TESTCONTAINERS_RYUK_DISABLED=true`：远端无外网拉不到 `testcontainers/ryuk` 边车镜像时必须禁用（否则用例被 skip），容器清理由测试自身 `@AfterAll` 负责。

#### 贡献者约定（数据库）

- 新增 JPA 实体/表：DDL 进新的 Flyway 增量脚本（**禁止**改写已应用的 V1），并同步 `datamigration/DataMigrationRunner.java` 的 `MIGRATION_TABLES` 清单（与 V1 基线表集合一致，由 `DataMigrationRunnerTableListTest` 强制；当前按字母序、V1 无外键约束故顺序无关；未来 V2+ 引入外键的表加入时须确保父表先于子表），否则存量 H2 迁移会漏表。
- 列类型注意：`@Lob String` 在主配置方言（`MysqlLongtextDialect`）下落 `longtext`；H2 测试侧由 `src/test/resources` 的占位符渲染为 clob，勿在实体上自行指定 `columnDefinition`。

### 方案 B：生产环境（MySQL + Nginx）

适用于生产部署、持续运行。MySQL 为唯一数据源（Flyway 首次启动自动建表，无手工 schema 脚本、无 prod profile 文件——一律用环境变量/启动参数覆盖），前端通过 Nginx 托管。

#### 第一步：环境准备

```bash
# 安装 OpenJDK 17
sudo apt update
sudo apt install openjdk-17-jdk

# 安装 Node.js 20（用于前端构建）
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# 安装 JMeter 5.6.3
wget https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz -C /opt/
```

#### 第二步：部署 MySQL（二选一）

```bash
# 方式一：Docker Compose（推荐，与测试服务器同构）
docker compose -f deploy/mysql/docker-compose.yml up -d
# root/perftest-root，应用账号 perftest/perftest，utf8mb4 服务器级

# 方式二：自装 MySQL 8.0（utf8mb4）
mysql -u root -p

CREATE DATABASE perftest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'perftest'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON perftest.* TO 'perftest'@'localhost';
FLUSH PRIVILEGES;

# 无需导入任何建表脚本：应用首次启动时 Flyway 自动执行 V1 全量基线建表
```

#### 第三步：配置后端（环境变量覆盖，无需 profile 文件）

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/perftest?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME=perftest
export SPRING_DATASOURCE_PASSWORD=your_password
export PLATFORM_STORAGE_ROOT=/data/perftest/storage          # 持久化存储路径
export JMETER_EXECUTABLE=/opt/apache-jmeter-5.6.3/bin/jmeter
export JMETER_JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

> `ddl-auto` 已固定为 `validate`（表结构变化只经 Flyway）；如自装 MySQL，请保持与应用 `application.yml` 相同的 URL 参数（`characterEncoding=UTF-8`，勿写 `utf8mb4`）。

#### 第四步：构建并启动后端

```bash
# 构建
./gradlew :backend:bootJar

# 启动（生产环境）
java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.jar --server.port=8080
```

建议使用 systemd 管理后端进程：

```ini
# /etc/systemd/system/perftest-backend.service
[Unit]
Description=Performance Test Platform Backend
After=network.target mysql.service

[Service]
Type=simple
User=perftest
WorkingDirectory=/opt/perftest
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/perftest?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
Environment=SPRING_DATASOURCE_USERNAME=perftest
Environment=SPRING_DATASOURCE_PASSWORD=your_password
Environment=PLATFORM_STORAGE_ROOT=/data/perftest/storage
ExecStart=/usr/bin/java -jar /opt/perftest/backend.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable perftest-backend
sudo systemctl start perftest-backend
```

#### 第五步：构建并部署前端

```bash
# 构建生产版本
cd frontend
npm install
npm run build
# 产出在 frontend/dist/
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name perftest.example.com;

    # 前端静态文件
    root /opt/perftest/frontend/dist;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;      # 执行日志和 SSE 需要长连接
        proxy_buffering off;          # SSE 需要关闭缓冲
    }
}
```

```bash
# 部署前端文件
sudo cp -r frontend/dist/* /opt/perftest/frontend/dist/
sudo nginx -t && sudo nginx -s reload
```

#### 第六步：启动 Prometheus（可选）

仅资源指标通道需要。JMeter 性能指标采集无需任何外部服务。

```bash
cd deploy/monitoring
docker compose up -d

# 验证：http://localhost:9090
```

#### 第七步：配置 JMeter 分布式执行（可选）

分布式执行依赖 Python SSH runner：

```bash
cd remote-runner
pip install -r requirements.txt    # paramiko==3.5.1

# 配置 application.yml
# platform.distributed.runner.python=python3
# platform.distributed.runner.entry=./remote-runner/remote_jmeter_runner/main.py
```

远程节点需要：
- SSH 可达
- Docker 已安装
- 在平台上注册节点（管理界面 → 执行节点 → 初始化）

#### 验证部署

```bash
# 检查后端
curl http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 检查前端
curl http://localhost/

# 检查后端健康（如有 actuator）
# curl http://localhost:8080/actuator/health
```

### 配置要点

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL（`characterEncoding=UTF-8`，勿写 utf8mb4） | `jdbc:mysql://192.168.17.216:3306/perftest?...` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 数据库账号 | `perftest` / `perftest` |
| `app.data-migration.h2-source` | H2→MySQL 一次性迁移源路径（挂上即迁，迁完移除） | 未设置 |
| `platform.storage.root` | 脚本/日志/结果存储根目录 | `./storage` |
| `platform.execution.max-concurrent-tasks` | 最大并发执行数 | `1` |
| `platform.jmeter.executable` | JMeter CLI 路径 | 本地 JMeter 安装路径 |
| `platform.jmeter.java-home` | JMeter 使用的 JDK | 当前 JDK |
| `platform.monitoring.prometheus.base-url` | Prometheus API 地址（空默认，需显式配置） | 空 |
| `platform.monitoring.prometheus.file-sd-path` | JMX 服务发现文件路径 | - |
| `platform.distributed.runner.python` | Python 解释器 | `python3` |
| `platform.distributed.runner.entry` | Remote runner 脚本路径 | `./remote-runner/.../main.py` |

> 完整配置项见 `backend/src/main/resources/application.yml`。

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [需求规格说明书](docs/requirements-spec.md) | 全部功能需求 + 实现状态 |
| [需求与架构总览](docs/requirements-and-architecture.md) | 产品定位、架构决策、评审清单 |
| [开发计划](docs/development-plan.md) | 三阶段开发计划 |
| [远期目标](docs/platform-vision.md) | 多引擎、AI 生成、远程 Agent |
| [Flyway V1 基线](backend/src/main/resources/db/migration/V1__baseline.sql) | 全量建表 DDL（50 表，`@Lob` 方言参数化） |
| [MySQL 部署 Compose](deploy/mysql/docker-compose.yml) | 测试/生产 MySQL 8.0 容器（utf8mb4） |
| [模块设计文档](docs/modules/) | 11 个模块的详细设计 |
| [实现记录](docs/implementation-log.md) | 开发历程 |

---

## 许可

内部项目，未开源。
