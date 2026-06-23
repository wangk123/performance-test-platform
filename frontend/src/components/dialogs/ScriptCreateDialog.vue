<template>
  <a-modal
    v-model:open="scriptCreateDialogVisible"
    title="新建脚本"
    width="480px"
    @after-close="resetScriptCreateForm"
  >
    <a-form class="upload-form" layout="vertical" @submit.prevent>
      <a-form-item label="脚本名称" required>
        <a-input
          v-model:value="scriptCreateForm.name"
          placeholder="例如 登录链路压测"
          @press-enter="createScriptAsset"
        />
      </a-form-item>
    </a-form>

    <template #footer>
      <a-button @click="scriptCreateDialogVisible = false">取消</a-button>
      <a-button
        type="primary"
        :loading="scriptCreating"
        :disabled="!scriptCreateForm.name.trim()"
        @click="createScriptAsset"
      >创建</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { useScriptCreate } from '../../composables/useScriptCreate';

const {
  scriptCreateDialogVisible,
  scriptCreating,
  scriptCreateForm,
  resetScriptCreateForm,
  createScriptAsset,
} = useScriptCreate();
</script>
