# 项目环境信息

> 记录平台开发/测试使用的服务器与外部环境。内网测试环境凭据，仅限本项目成员内部使用，勿外传。

## 测试服务器（2026-09-07 记录，216 部署实况 2026-09-08 更新）

三台内网测试服务器，可用于 MySQL 部署、平台验收走查、后续执行节点（ExecutionNode）或被测环境：

| 主机 | IP | 登录（SSH） | 用途 |
|------|----|------------|------|
| 测试服务器-216 | 192.168.17.216 | root / Yr@09876 | **MySQL 数据库服务器（P0-4 已实部署）**：MySQL 8.0 已部署为运行时唯一数据源，`application.yml` 默认直连 `192.168.17.216:3306/perftest`；Flyway V1 已应用（51 表 = 50 业务表 + flyway_schema_history），种子账号已建 |
| 测试服务器-217 | 192.168.17.217 | root / Yr@09876 | 待分配 |
| 测试服务器-218 | 192.168.17.218 | root / Yr@09876 | 待分配 |

- 系统版本 / 已装软件：216 为 Linux，Docker 29.1.3（详见下方部署记录）。
- 若某台分配了固定角色（如常驻 MySQL、平台部署机），更新本表并在下方追加部署记录。

## 216 MySQL 部署记录（2026-09-08，P0-4）

- **部署方式**：`/app/perftest/docker-compose.yml`（与仓库 `deploy/mysql/docker-compose.yml` 同构）→ 容器 `perftest-mysql`，镜像 `mysql:8.0`，端口 `3306:3306`，数据卷 `mysql-data` 持久化，`--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`（服务器级 utf8mb4）。
- **账号口径**：root / `perftest-root`；应用账号 `perftest` / `perftest`（库 `perftest`）。
- **应用侧连接**：`jdbc:mysql://192.168.17.216:3306/perftest?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`——注意 `characterEncoding` 用 Java 字符集名 `UTF-8`（Connector/J 9 拒绝 `utf8mb4` 词法，UTF-8 即映射 utf8mb4）。
- **schema 归属**：Flyway 管理（V1 全量基线，`ddl-auto: validate`），手工建表脚本已废弃删除。
- **compose CLI plugin 手装记录**：216 的 docker compose 为手动安装的 CLI 插件（v5.5.1，位于 `/usr/local/lib/docker/cli-plugins/`），非发行版包——机器初始化/重装时需补装。
- **网络事实**：216 **无法访问 Docker Hub**（拉取新镜像会失败）。已用镜像（mysql:8.0 等）可正常使用；Testcontainers 跑 `testMysql` 时 ryuk 边车拉不到，须 `TESTCONTAINERS_RYUK_DISABLED=true`（见 README 方案 A-5）。
