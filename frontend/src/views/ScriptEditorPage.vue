<template>
  <section class="script-editor-page">
    <template v-if="script">
      <header class="script-editor-platform-topbar">
        <button class="script-editor-exit" type="button" @click="leaveEditor">← 退出编辑</button>
        <div class="script-editor-title">
          <h1>{{ projectName(script.projectId) }} · {{ script.name }}</h1>
        </div>
        <div class="script-editor-platform-actions">
          <a-dropdown v-if="currentUser" trigger="click">
            <button class="user-menu-trigger" type="button">
              <a-avatar class="user-avatar" :size="26">{{ userInitial }}</a-avatar>
              <span>{{ currentUser.displayName }}</span>
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="theme">
                  <div class="user-menu-section" @click.stop>
                    <span>主题</span>
                    <a-segmented v-model:value="themeMode" :options="themeModeOptions" />
                  </div>
                </a-menu-item>
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
          <StepDetail :saving="saving" @save="onSave" />
        </section>
      </main>

      <StepCreateDialog />
      <StepImportDialog />
    </template>

    <div v-else class="script-editor-missing panel">
      <h1>脚本不存在或已被删除</h1>
      <p>返回项目工作台后重新选择脚本。</p>
      <a-button type="primary" @click="closeCurrentTab">关闭</a-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { useScriptEditor } from '../composables/useScriptEditor';
import { useTheme } from '../composables/useTheme';
import { useWorkspace } from '../composables/useWorkspace';
import StepSidebar from '../components/editor/StepSidebar.vue';
import StepDetail from '../components/editor/StepDetail.vue';
import StepCreateDialog from '../components/editor/StepCreateDialog.vue';
import StepImportDialog from '../components/editor/StepImportDialog.vue';

const editor = useScriptEditor();
const route = useRoute();
const router = useRouter();
const { currentUser } = useAuth();
const { projectName, loadProjectContext, fullLogout } = useWorkspace();
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

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (hasUnsavedChanges.value) {
    event.preventDefault();
    event.returnValue = '';
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload);
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload);
});

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

function leaveEditor() {
  const projectId = Number(route.params.projectId);
  if (projectId) {
    void router.push(`/projects/${projectId}/scripts`);
    return;
  }
  void router.push('/projects');
}

function closeCurrentTab() {
  leaveEditor();
}

function currentScriptSnapshot() {
  return JSON.stringify(script.value?.steps ?? []);
}
</script>
