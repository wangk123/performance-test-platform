<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">性能测试平台</div>
      <el-menu :default-active="activeNav" class="nav">
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
          <div class="page-subtitle">登录权限、项目管理、项目归档边界</div>
        </div>
        <div class="topbar-actions">
          <el-tag type="success">Spring Boot API 已接入</el-tag>
          <el-button v-if="currentUser" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="content">
        <section v-if="!currentUser" class="login-panel">
          <div>
            <h1>登录平台</h1>
            <p>使用演示账号进入第一阶段项目管理闭环。</p>
          </div>
          <el-form class="login-form" label-position="top" @submit.prevent>
            <el-form-item label="账号">
              <el-input v-model="loginForm.username" autocomplete="username" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" autocomplete="current-password" show-password />
            </el-form-item>
            <el-button type="primary" :loading="loginLoading" @click="login">登录</el-button>
          </el-form>
        </section>

        <template v-else>
          <section class="summary">
            <div>
              <h1>项目资产入口</h1>
              <p>当前用户：{{ currentUser.displayName }}。这里管理压测项目边界，后续脚本、任务和报告都会归属到项目。</p>
            </div>
            <el-button type="primary" @click="openCreateDialog">新建项目</el-button>
          </section>

          <section class="metrics-grid">
            <div class="metric">
              <span>活跃项目</span>
              <strong>{{ activeProjectCount }}</strong>
            </div>
            <div class="metric">
              <span>归档项目</span>
              <strong>{{ archivedProjectCount }}</strong>
            </div>
            <div class="metric">
              <span>当前阶段</span>
              <strong>Phase 1</strong>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div>
                <h2>项目列表</h2>
                <p>创建项目后即可作为后续脚本上传、测试执行和报告沉淀的统一入口。</p>
              </div>
              <div class="panel-actions">
                <el-checkbox v-model="includeArchived" @change="loadProjects">显示归档</el-checkbox>
                <el-button :loading="projectLoading" @click="loadProjects">刷新</el-button>
              </div>
            </div>

            <el-table v-loading="projectLoading" :data="projects" border>
              <el-table-column prop="code" label="项目编码" width="160" />
              <el-table-column prop="name" label="项目名称" width="220" />
              <el-table-column prop="description" label="说明" min-width="260">
                <template #default="{ row }">
                  <span class="muted">{{ row.description || '未填写' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ownerUsername" label="负责人" width="120" />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                    {{ row.status === 'ACTIVE' ? '活跃' : '已归档' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openMemberDialog(row)">成员</el-button>
                  <el-button
                    v-if="row.status === 'ACTIVE'"
                    link
                    type="warning"
                    @click="archiveProject(row)"
                  >
                    归档
                  </el-button>
                  <el-button v-else link type="primary" @click="restoreProject(row)">恢复</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <div class="empty-state">暂无项目，先新建一个压测项目。</div>
              </template>
            </el-table>
          </section>
        </template>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="createDialogVisible" title="新建项目" width="520px">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="项目编码">
        <el-input v-model="projectForm.code" placeholder="例如 loan-core" />
      </el-form-item>
      <el-form-item label="项目名称">
        <el-input v-model="projectForm.name" placeholder="例如 信贷核心压测" />
      </el-form-item>
      <el-form-item label="项目说明">
        <el-input v-model="projectForm.description" type="textarea" :rows="3" placeholder="填写主要压测范围" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingProject" @click="createProject">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="memberDialogVisible" title="项目成员" width="680px">
    <div v-if="selectedProject" class="member-dialog">
      <div class="member-context">
        <strong>{{ selectedProject.name }}</strong>
        <span>{{ selectedProject.code }}</span>
      </div>

      <el-table v-loading="memberLoading" :data="members" border>
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="role" label="项目角色" width="160">
          <template #default="{ row }">
            <el-tag :type="row.role === 'OWNER' ? 'success' : 'info'">
              {{ row.role === 'OWNER' ? '负责人' : '成员' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-form class="member-form" inline @submit.prevent>
        <el-form-item label="成员账号">
          <el-input v-model="memberForm.username" placeholder="例如 tester" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" class="role-select">
            <el-option label="成员" value="MEMBER" />
            <el-option label="负责人" value="OWNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingMember" @click="addMember">添加成员</el-button>
        </el-form-item>
      </el-form>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

type User = {
  username: string;
  displayName: string;
  roles: string[];
};

type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

type Project = {
  id: number;
  code: string;
  name: string;
  description: string;
  ownerUsername: string;
  status: ProjectStatus;
};

type ProjectRole = 'OWNER' | 'MEMBER';

type ProjectMember = {
  projectId: number;
  username: string;
  role: ProjectRole;
};

const activeNav = 'projects';
const loginLoading = ref(false);
const projectLoading = ref(false);
const memberLoading = ref(false);
const savingProject = ref(false);
const savingMember = ref(false);
const includeArchived = ref(false);
const createDialogVisible = ref(false);
const memberDialogVisible = ref(false);
const currentUser = ref<User | null>(readStoredUser());
const projects = ref<Project[]>([]);
const allProjects = ref<Project[]>([]);
const members = ref<ProjectMember[]>([]);
const selectedProject = ref<Project | null>(null);

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
});

const projectForm = reactive({
  code: '',
  name: '',
  description: '',
});

const memberForm = reactive<{
  username: string;
  role: ProjectRole;
}>({
  username: '',
  role: 'MEMBER',
});

const activeProjectCount = computed(() => allProjects.value.filter((project) => project.status === 'ACTIVE').length);
const archivedProjectCount = computed(() => allProjects.value.filter((project) => project.status === 'ARCHIVED').length);

watch(currentUser, (user) => {
  if (user) {
    localStorage.setItem('perftest.currentUser', JSON.stringify(user));
  } else {
    localStorage.removeItem('perftest.currentUser');
  }
});

onMounted(() => {
  if (currentUser.value) {
    loadProjects();
  }
});

async function login() {
  loginLoading.value = true;
  try {
    const user = await request<User>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(loginForm),
    });
    currentUser.value = user;
    await loadProjects();
    ElMessage.success('已登录');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loginLoading.value = false;
  }
}

function logout() {
  currentUser.value = null;
  projects.value = [];
  allProjects.value = [];
}

async function loadProjects() {
  if (!currentUser.value) {
    return;
  }
  projectLoading.value = true;
  try {
    allProjects.value = await request<Project[]>('/api/projects?includeArchived=true');
    projects.value = includeArchived.value
      ? allProjects.value
      : allProjects.value.filter((project) => project.status === 'ACTIVE');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    projectLoading.value = false;
  }
}

function openCreateDialog() {
  projectForm.code = '';
  projectForm.name = '';
  projectForm.description = '';
  createDialogVisible.value = true;
}

async function createProject() {
  savingProject.value = true;
  try {
    await request<Project>('/api/projects', {
      method: 'POST',
      headers: {
        'X-User': currentUser.value?.username ?? 'admin',
      },
      body: JSON.stringify(projectForm),
    });
    createDialogVisible.value = false;
    await loadProjects();
    ElMessage.success('项目已创建');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    savingProject.value = false;
  }
}

async function archiveProject(project: Project) {
  await updateProjectStatus(project, 'archive', '项目已归档');
}

async function restoreProject(project: Project) {
  await updateProjectStatus(project, 'restore', '项目已恢复');
}

async function updateProjectStatus(project: Project, action: 'archive' | 'restore', successMessage: string) {
  projectLoading.value = true;
  try {
    await request<Project>(`/api/projects/${project.id}/${action}`, {
      method: 'PATCH',
      headers: {
        'X-User': currentUser.value?.username ?? 'admin',
      },
    });
    await loadProjects();
    ElMessage.success(successMessage);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    projectLoading.value = false;
  }
}

async function openMemberDialog(project: Project) {
  selectedProject.value = project;
  memberForm.username = '';
  memberForm.role = 'MEMBER';
  memberDialogVisible.value = true;
  await loadMembers(project.id);
}

async function loadMembers(projectId: number) {
  memberLoading.value = true;
  try {
    members.value = await request<ProjectMember[]>(`/api/projects/${projectId}/members`);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    memberLoading.value = false;
  }
}

async function addMember() {
  if (!selectedProject.value) {
    return;
  }
  savingMember.value = true;
  try {
    await request<ProjectMember>(`/api/projects/${selectedProject.value.id}/members`, {
      method: 'POST',
      headers: {
        'X-User': currentUser.value?.username ?? 'admin',
      },
      body: JSON.stringify(memberForm),
    });
    memberForm.username = '';
    memberForm.role = 'MEMBER';
    await loadMembers(selectedProject.value.id);
    ElMessage.success('成员已添加');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    savingMember.value = false;
  }
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.message ?? `请求失败：${response.status}`);
  }
  return payload as T;
}

function readStoredUser(): User | null {
  const stored = localStorage.getItem('perftest.currentUser');
  if (!stored) {
    return null;
  }
  try {
    return JSON.parse(stored) as User;
  } catch {
    return null;
  }
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败';
}
</script>
