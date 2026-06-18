<template>
  <section class="script-editor-page">
    <template v-if="script">
      <header class="script-editor-platform-topbar">
        <div class="brand script-editor-brand">
          <span class="brand-mark">PT</span>
          <div>
            <strong>性能测试平台</strong>
            <small>Project Workspace</small>
          </div>
        </div>
        <div class="script-editor-title">
          <span class="eyebrow">Phase 2 Prototype</span>
          <h1>{{ projectName(script.projectId) }} · 脚本编辑</h1>
        </div>
        <div class="script-editor-platform-actions">
          <a-tag color="success">Backend API</a-tag>
          <a-tag v-if="currentProject" color="success">{{ currentProject.code }}</a-tag>
          <a-dropdown v-if="currentUser" trigger="click">
            <button class="user-menu-trigger" type="button">
              <a-avatar class="user-avatar">{{ userInitial }}</a-avatar>
              <span>{{ currentUser.displayName }}</span>
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" disabled>
                  <strong>{{ currentUser.displayName }}</strong>
                  <small>{{ currentUser.username }} · 平台管理员</small>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="theme">
                  <div class="user-menu-section" @click.stop>
                    <span>主题</span>
                    <a-segmented v-model:value="themeMode" :options="themeModeOptions" />
                  </div>
                </a-menu-item>
                <a-menu-item key="settings" disabled>用户设置（Mock）</a-menu-item>
                <a-menu-item key="notifications" disabled>消息中心（Mock）</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" danger @click="fullLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>

      <main
        class="script-editor-workbench"
        :class="{ 'is-resizing-sidebar': resizing }"
        :style="{ '--editor-sidebar-width': `${sidebarWidth}px` }"
      >
        <aside class="script-editor-left">
          <StepSidebar />
        </aside>
        <div
          class="step-sidebar-resizer"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整步骤列表宽度"
          @mousedown="startSidebarResize"
        />
        <section class="script-editor-detail-area">
          <StepDetail :saving="saving" :dirty="hasUnsavedChanges" @save="onSave" @close="onClose" />
        </section>
      </main>

      <StepCreateDialog />
    </template>

    <div v-else class="script-editor-missing panel">
      <h1>脚本不存在或已被删除</h1>
      <p>返回项目工作台后重新选择脚本。</p>
      <a-button type="primary" @click="closeCurrentTab">关闭</a-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useRoute } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { useScriptEditor } from '../composables/useScriptEditor';
import { useTheme } from '../composables/useTheme';
import { useWorkspace } from '../composables/useWorkspace';
import { confirmAction } from '../utils/feedback';
import StepSidebar from '../components/editor/StepSidebar.vue';
import StepDetail from '../components/editor/StepDetail.vue';
import StepCreateDialog from '../components/editor/StepCreateDialog.vue';

const editor = useScriptEditor();
const route = useRoute();
const { currentUser } = useAuth();
const { projectName, currentProject, loadProjectContext, fullLogout } = useWorkspace();
const { themeMode, themeModeOptions } = useTheme();
const script = computed(() => editor.editorScriptAsset.value);
const userInitial = computed(() => currentUser.value?.displayName?.slice(0, 1).toUpperCase() ?? 'U');
const savedSnapshot = ref('');
const hasUnsavedChanges = computed(() => Boolean(script.value) && currentScriptSnapshot() !== savedSnapshot.value);

const saving = ref(false);
const sidebarWidth = ref(380);
const resizing = ref(false);
const SIDEBAR_MIN = 340;
const SIDEBAR_MAX = 620;

watch(
  () => [route.params.projectId, route.params.scriptId] as const,
  async ([projectId]) => {
    const id = Number(projectId);
    if (id) {
      await loadProjectContext(id);
    }
    editor.syncEditorRoute();
  },
  { immediate: true },
);

watch(
  () => [script.value?.id, script.value?.latestVersion] as const,
  () => {
    savedSnapshot.value = currentScriptSnapshot();
  },
  { immediate: true },
);

function startSidebarResize(event: MouseEvent) {
  event.preventDefault();
  resizing.value = true;
  const startX = event.clientX;
  const startWidth = sidebarWidth.value;

  function onMove(moveEvent: MouseEvent) {
    sidebarWidth.value = Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, startWidth + moveEvent.clientX - startX));
  }

  function onUp() {
    resizing.value = false;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
  }

  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

async function onSave() {
  saving.value = true;
  try {
    if (await editor.saveEditorScript()) {
      savedSnapshot.value = currentScriptSnapshot();
      message.success('脚本已保存为后端 JMX 新版本');
    }
  } finally {
    saving.value = false;
  }
}

async function onClose() {
  if (hasUnsavedChanges.value) {
    try {
      await confirmAction({
        title: '关闭脚本编辑器',
        content: '当前脚本有未保存修改，确认关闭？',
        okText: '关闭',
        okType: 'danger',
      });
    } catch {
      return;
    }
  }
  closeCurrentTab();
}

function closeCurrentTab() {
  window.close();
}

function currentScriptSnapshot() {
  return JSON.stringify(script.value?.steps ?? []);
}
</script>
