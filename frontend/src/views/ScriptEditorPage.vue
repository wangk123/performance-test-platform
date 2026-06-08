<template>
  <section class="script-editor-page">
    <template v-if="script">
      <header class="script-editor-topbar">
        <div>
          <span class="eyebrow">Script Editor</span>
          <h1>{{ script.name }}</h1>
          <p>{{ script.sourceFile }} · v{{ script.latestVersion }} · {{ projectName(script.projectId) }}</p>
        </div>
        <div class="script-editor-actions">
          <el-button @click="editor.closeScriptEditor">返回工作台</el-button>
          <el-button type="primary" @click="onSave">保存编排</el-button>
        </div>
      </header>

      <main
        class="script-editor-layout"
        :class="{ 'is-resizing-sidebar': resizing }"
        :style="{ '--step-sidebar-width': `${sidebarWidth}px` }"
      >
        <StepSidebar />
        <div
          class="step-sidebar-resizer"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整步骤列表宽度"
          @mousedown="startSidebarResize"
        />
        <StepDetail />
      </main>

      <StepCreateDialog />
    </template>

    <div v-else class="script-editor-missing panel">
      <h1>脚本不存在或已被删除</h1>
      <p>返回项目工作台后重新选择脚本。</p>
      <el-button type="primary" @click="editor.closeScriptEditor">返回工作台</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useScriptEditor } from '../composables/useScriptEditor';
import { useWorkspace } from '../composables/useWorkspace';
import StepSidebar from '../components/editor/StepSidebar.vue';
import StepDetail from '../components/editor/StepDetail.vue';
import StepCreateDialog from '../components/editor/StepCreateDialog.vue';

const editor = useScriptEditor();
const { projectName } = useWorkspace();
const script = computed(() => editor.editorScriptAsset.value);

const sidebarWidth = ref(360);
const resizing = ref(false);
const SIDEBAR_MIN = 280;
const SIDEBAR_MAX = 560;

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
  if (await editor.saveEditorScript()) {
    ElMessage.success('脚本已保存为后端 JMX 新版本');
  }
}
</script>
