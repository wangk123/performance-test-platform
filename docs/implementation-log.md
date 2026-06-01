# 性能测试平台 - 实现记录

## 2026-05-29

已完成：

1. 初始化 GitHub 公开仓库。
2. 建立 Spring Boot 3 后端和 Vue 3 前端骨架。
3. 实现登录演示接口。
4. 实现项目创建、列表、归档和恢复接口。
5. 前端从静态页面升级为可操作项目工作台。
6. 后端项目与用户从内存态切换为 JPA + H2 文件库。
7. 增加 API 行为测试和持久化测试。

提交：

1. `9a4b561 feat: scaffold performance test platform`
2. `02246cd feat: add phase one project workspace`
3. `437112a feat: persist phase one identity and projects`

## 2026-06-01

已完成：

1. 项目成员管理接口。
2. 项目成员弹窗。
3. 基础负责人权限约束。
4. 需求规格与阶段计划归档到项目 `docs/`。

验证：

1. `gradle :backend:test --tests com.yr.perftest.platform.api.PlatformApiBehaviorTest` 通过。
2. `gradle :backend:test` 通过。
3. `npm run build` 通过。
4. 本地接口验证：
   - `GET /api/projects/1/members`
   - `POST /api/projects/1/members`

说明：

浏览器自动化刷新被 Browser 插件 URL 策略阻止，未通过自动化完成页面点击验证。后端服务已重启，前端开发服务仍运行在 `http://127.0.0.1:5173/`。
