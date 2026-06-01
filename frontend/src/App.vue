<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">性能测试平台</div>
      <el-menu :default-active="activeNav" class="nav" @select="selectNav">
        <el-menu-item index="projects">项目管理</el-menu-item>
        <el-menu-item index="scripts">脚本管理</el-menu-item>
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

        <template v-else-if="activeNav === 'projects'">
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

        <template v-else>
          <section class="summary">
            <div>
              <h1>脚本版本管理</h1>
              <p>在项目下上传 JMeter JMX 文件，平台会生成脚本版本，供后续测试执行选择。</p>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div>
                <h2>上传 JMX</h2>
                <p>当前阶段只接收 `.jmx` 文件。</p>
              </div>
            </div>
            <div class="script-toolbar">
              <el-select v-model="selectedScriptProjectId" class="project-select" placeholder="选择项目" @change="loadScripts">
                <el-option
                  v-for="project in allProjects"
                  :key="project.id"
                  :label="`${project.name} (${project.code})`"
                  :value="project.id"
                />
              </el-select>
              <input class="file-input" type="file" accept=".jmx" @change="handleScriptFileChange" />
              <el-button type="primary" :loading="scriptUploading" :disabled="!selectedScriptProjectId || !scriptFile" @click="uploadScript">
                上传脚本
              </el-button>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div>
                <h2>脚本版本</h2>
                <p>最新版本排在前面。</p>
              </div>
              <el-button :loading="scriptLoading" @click="loadScripts">刷新</el-button>
            </div>
            <el-table v-loading="scriptLoading" :data="scriptVersions" border>
              <el-table-column prop="versionNo" label="版本" width="100">
                <template #default="{ row }">v{{ row.versionNo }}</template>
              </el-table-column>
              <el-table-column prop="originalFilename" label="文件名" min-width="240" />
              <el-table-column prop="uploadedBy" label="上传人" width="120" />
              <el-table-column prop="uploadedAt" label="上传时间" width="220">
                <template #default="{ row }">{{ formatTime(row.uploadedAt) }}</template>
              </el-table-column>
              <template #empty>
                <div class="empty-state">暂无脚本版本。</div>
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

type ScriptVersion = {
  id: number;
  projectId: number;
  versionNo: number;
  originalFilename: string;
  storedPath: string;
  uploadedBy: string;
  uploadedAt: string;
};

const activeNav = ref('projects');
const loginLoading = ref(false);
const projectLoading = ref(false);
const memberLoading = ref(false);
const scriptLoading = ref(false);
const savingProject = ref(false);
const savingMember = ref(false);
const scriptUploading = ref(false);
const includeArchived = ref(false);
const createDialogVisible = ref(false);
const memberDialogVisible = ref(false);
const currentUser = ref<User | null>(readStoredUser());
const projects = ref<Project[]>([]);
const allProjects = ref<Project[]>([]);
const members = ref<ProjectMember[]>([]);
const scriptVersions = ref<ScriptVersion[]>([]);
const selectedProject = ref<Project | null>(null);
const selectedScriptProjectId = ref<number | null>(null);
const scriptFile = ref<File | null>(null);

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

async function selectNav(index: string) {
  activeNav.value = index;
  if (index === 'scripts') {
    await prepareScriptView();
  }
}

function logout() {
  currentUser.value = null;
  projects.value = [];
  allProjects.value = [];
  scriptVersions.value = [];
  selectedScriptProjectId.value = null;
  scriptFile.value = null;
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

async function prepareScriptView() {
  await loadProjects();
  if (!selectedScriptProjectId.value && allProjects.value.length > 0) {
    selectedScriptProjectId.value = allProjects.value[0].id;
  }
  await loadScripts();
}

async function loadScripts() {
  if (!selectedScriptProjectId.value) {
    scriptVersions.value = [];
    return;
  }
  scriptLoading.value = true;
  try {
    scriptVersions.value = await request<ScriptVersion[]>(`/api/projects/${selectedScriptProjectId.value}/scripts`);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    scriptLoading.value = false;
  }
}

function handleScriptFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  scriptFile.value = input.files?.[0] ?? null;
}

async function uploadScript() {
  if (!selectedScriptProjectId.value || !scriptFile.value) {
    return;
  }
  scriptUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', scriptFile.value);
    const response = await fetch(`/api/projects/${selectedScriptProjectId.value}/scripts`, {
      method: 'POST',
      headers: {
        'X-User': currentUser.value?.username ?? 'admin',
      },
      body: formData,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(payload?.message ?? `请求失败：${response.status}`);
    }
    scriptFile.value = null;
    await loadScripts();
    ElMessage.success('脚本已上传');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    scriptUploading.value = false;
  }
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
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
