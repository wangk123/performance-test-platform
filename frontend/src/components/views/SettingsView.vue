<template>
  <section class="hero-band">
    <div>
      <span class="eyebrow">System Configuration</span>
      <h1>系统配置</h1>
      <p>平台级配置不属于具体项目，先覆盖用户、角色、权限三类基础能力，后续可扩展组织、环境、审计策略。</p>
    </div>
  </section>

  <section class="settings-layout">
    <aside class="panel settings-nav">
      <button
        v-for="option in configTabOptions"
        :key="option.value"
        class="module-link"
        :class="{ active: activeConfigTab === option.value }"
        type="button"
        @click="activeConfigTab = option.value"
      >
        <span>{{ configIndex(option.value) }}</span>
        <strong>{{ option.label }}</strong>
      </button>
    </aside>

    <div class="panel settings-panel">
      <template v-if="activeConfigTab === 'users'">
        <div class="panel-header">
          <div>
            <h2>用户管理</h2>
            <p>维护平台登录账号、系统角色和账号状态。</p>
          </div>
          <a-button type="primary">新建用户</a-button>
        </div>
        <a-table
          :columns="userColumns"
          :data-source="systemUsers"
          :pagination="false"
          :row-key="(record: SystemUser) => record.username"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === '启用' ? 'success' : 'default'">{{ record.status }}</a-tag>
            </template>
          </template>
        </a-table>
      </template>

      <template v-else-if="activeConfigTab === 'roles'">
        <div class="panel-header">
          <div>
            <h2>角色管理</h2>
            <p>角色聚合权限，项目内负责人/成员权限仍由项目成员关系控制。</p>
          </div>
          <a-button type="primary">新建角色</a-button>
        </div>
        <div class="role-grid">
          <div v-for="role in systemRoles" :key="role.name">
            <strong>{{ role.name }}</strong>
            <span>{{ role.description }}</span>
            <small>{{ role.permissions.join(' / ') }}</small>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="panel-header">
          <div>
            <h2>权限配置</h2>
            <p>按平台模块定义权限点，供系统角色授权使用。</p>
          </div>
        </div>
        <div class="permission-list">
          <div v-for="permission in systemPermissions" :key="permission.code">
            <span>{{ permission.module }}</span>
            <strong>{{ permission.name }}</strong>
            <small>{{ permission.code }}</small>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue';
import { configTabOptions, systemPermissions, systemRoles, systemUsers } from '../../constants';
import { configIndex } from '../../utils/format';
import { useNavigation } from '../../composables/useNavigation';

const { activeConfigTab } = useNavigation();

type SystemUser = (typeof systemUsers)[number];

const userColumns: TableColumnsType<SystemUser> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '系统角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近登录', dataIndex: 'lastLogin', key: 'lastLogin' },
];
</script>
