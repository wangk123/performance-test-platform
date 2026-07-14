<template>
  <section class="panel settings-shell">
    <SettingsTabBar v-model="activeConfigTab" :options="tabOptions" />

    <div class="settings-body">
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

      <template v-else-if="activeConfigTab === 'permissions'">
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
import { useNavigation } from '../../composables/useNavigation';
import SettingsTabBar from '../settings/SettingsTabBar.vue';

const { activeConfigTab } = useNavigation();

const tabOptions = configTabOptions.map((option) => ({
  label: option.label,
  value: option.value,
}));

type SystemUser = (typeof systemUsers)[number];

const userColumns: TableColumnsType<SystemUser> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '系统角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '最近登录', dataIndex: 'lastLogin', key: 'lastLogin' },
];
</script>
