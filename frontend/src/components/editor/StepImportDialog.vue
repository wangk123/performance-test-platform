<template>
  <a-modal
    v-model:open="editor.stepImportVisible.value"
    class="step-import-dialog"
    :title="editor.stepImportMode.value === 'xml' ? 'XML 导入' : 'curl 导入'"
    width="720px"
    ok-text="导入"
    @ok="() => editor.submitStepImport()"
  >
    <p class="step-import-hint">
      {{
        editor.stepImportMode.value === 'xml'
          ? '粘贴 JMeter 组件 XML，将导入到当前选中步骤下。超出 3 级层级的部分会被截断。'
          : '粘贴 curl 命令，将自动转换为 HTTP 请求步骤并添加到当前选中步骤下。'
      }}
    </p>
    <a-textarea
      v-model:value="editor.stepImportText.value"
      :rows="12"
      :placeholder="editor.stepImportMode.value === 'xml' ? '<HTTPSamplerProxy ...>\n<hashTree/>' : 'curl -X POST https://example.com/api ...'"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { useScriptEditor } from '../../composables/useScriptEditor';

const editor = useScriptEditor();
</script>
