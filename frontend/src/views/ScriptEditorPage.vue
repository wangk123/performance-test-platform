<template>
  <section class="script-editor-page">
    <template v-if="script">
      <header class="script-editor-platform-topbar">
        <button class="script-editor-exit" type="button" @click="leaveEditor">← 退出编辑</button>
        <div class="script-editor-title">
          <h1>{{ projectName(script.projectId) }} · {{ script.name }}</h1>
        </div>
        <div class="script-editor-platform-actions">
          <a-tag v-if="script.draftVersionId === script.id" color="orange">草稿</a-tag>
          <a-tag v-else color="green">{{ script.latestVersionLabel || `v${script.latestVersion}` }} 已发布</a-tag>
          <a-button size="small" @click="publishDialogOpen = true">发布</a-button>
          <UserMenu />
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
      <ScriptPublishDialog
        v-model:open="publishDialogOpen"
        :script="script"
        :default-version-label="nextPatchLabel(script.latestVersionLabel)"
        :before-publish="ensureDraftBeforePublish"
        @published="onPublished"
      />
    </template>

    <div v-else-if="loading" class="script-editor-loading">
      <a-spin size="large" tip="加载脚本..." />
    </div>

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
import { useScriptEditor } from '../composables/useScriptEditor';
import { useWorkspace } from '../composables/useWorkspace';
import StepSidebar from '../components/editor/StepSidebar.vue';
import StepDetail from '../components/editor/StepDetail.vue';
import StepCreateDialog from '../components/editor/StepCreateDialog.vue';
import StepImportDialog from '../components/editor/StepImportDialog.vue';
import ScriptPublishDialog from '../components/dialogs/ScriptPublishDialog.vue';
import UserMenu from '../components/layout/UserMenu.vue';

const editor = useScriptEditor();
const route = useRoute();
const router = useRouter();
const { projectName, loadProjectContext } = useWorkspace();
const script = computed(() => editor.editorScriptAsset.value);
const savedSnapshot = ref('');
const hasUnsavedChanges = computed(() => Boolean(script.value) && currentScriptSnapshot() !== savedSnapshot.value);

const saving = ref(false);
const loading = ref(false);
const publishDialogOpen = ref(false);
const sidebarWidth = ref(380);
const resizing = ref(false);
const SIDEBAR_MIN = 340;
const SIDEBAR_MAX = 620;

watch(
  () => [route.params.projectId, route.params.scriptId] as const,
  async ([projectId]) => {
    const id = Number(projectId);
    if (id) {
      // 加载期间 editorScriptId 尚未同步，不能闪现“脚本不存在”兜底页
      loading.value = true;
      try {
        await loadProjectContext(id);
      } finally {
        loading.value = false;
      }
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
      message.success('草稿已保存');
    }
  } finally {
    saving.value = false;
  }
}

// 后端发布只消费草稿：编辑器停在已发布版本（无草稿）或有未保存改动时，先保存草稿再发布
async function ensureDraftBeforePublish() {
  if (!script.value) {
    return false;
  }
  if (script.value.draftVersionId === script.value.id && !hasUnsavedChanges.value) {
    return true;
  }
  saving.value = true;
  try {
    if (!await editor.saveEditorScript()) {
      return false;
    }
    savedSnapshot.value = currentScriptSnapshot();
    return true;
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

function nextPatchLabel(label: string): string {
  if (!/^\d+\.\d+\.\d+$/.test(label)) {
    return '1.0.0';
  }
  const [major, minor, patch] = label.split('.').map(Number);
  return `${major}.${minor}.${patch + 1}`;
}

async function onPublished() {
  const projectId = Number(route.params.projectId);
  if (projectId) {
    await loadProjectContext(projectId);
  }
  editor.syncEditorRoute();
}
</script>
