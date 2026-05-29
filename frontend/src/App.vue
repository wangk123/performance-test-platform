<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">性能测试平台</div>
      <el-menu default-active="projects" class="nav">
        <el-menu-item index="projects">项目管理</el-menu-item>
        <el-menu-item index="scripts" disabled>脚本管理</el-menu-item>
        <el-menu-item index="tasks" disabled>测试执行</el-menu-item>
        <el-menu-item index="reports" disabled>报告管理</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <div class="page-title">Phase 1 工作台</div>
          <div class="page-subtitle">平台骨架、登录权限、项目管理</div>
        </div>
        <el-tag type="success">Spring Boot 精简单体</el-tag>
      </el-header>

      <el-main class="content">
        <section class="summary">
          <h1>项目资产入口</h1>
          <p>第一阶段优先建立项目、成员和权限边界，为脚本、任务和报告提供统一归属。</p>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2>待实现能力</h2>
          </div>
          <el-table :data="capabilities" border>
            <el-table-column prop="name" label="能力" width="180" />
            <el-table-column prop="status" label="状态" width="140" />
            <el-table-column prop="description" label="说明" />
          </el-table>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
const capabilities = [
  {
    name: '用户登录',
    status: '骨架准备',
    description: '后端已建立认证域模型，后续接入 Spring Security 和页面登录。',
  },
  {
    name: '项目管理',
    status: '核心行为已测',
    description: '已覆盖项目创建、重复编码校验、归档隐藏、成员访问控制。',
  },
  {
    name: '审计字段',
    status: '待持久化',
    description: '后续在数据库实体和应用服务中统一写入创建人、更新时间。',
  },
];
</script>
