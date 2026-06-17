<template>
  <a-modal
    v-model:open="visible"
    :title="editingProject ? '编辑项目' : '新建项目'"
    width="560px"
  >
    <a-form layout="vertical" @submit.prevent>
      <a-form-item label="项目编码">
        <a-input
          v-model:value.trim="projectForm.code"
          :disabled="Boolean(editingProject)"
          placeholder="例如 loan-core"
        />
      </a-form-item>
      <a-form-item label="项目名称">
        <a-input v-model:value.trim="projectForm.name" placeholder="例如 信贷核心压测" />
      </a-form-item>
      <a-form-item label="项目负责人">
        <a-input v-model:value.trim="projectForm.ownerUsername" placeholder="例如 tester" />
      </a-form-item>
      <a-form-item label="项目说明">
        <a-textarea
          v-model:value="projectForm.description"
          :rows="3"
          placeholder="填写主要压测范围、环境或边界"
        />
      </a-form-item>
    </a-form>
    <template #footer>
      <a-button @click="visible = false">取消</a-button>
      <a-button type="primary" :loading="saving" @click="onSave">保存</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { Project } from '../../types';
import { useAuth } from '../../composables/useAuth';
import { useWorkspace } from '../../composables/useWorkspace';

const props = defineProps<{
  modelValue: boolean;
  editingProject: Project | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const visible = ref(props.modelValue);
const saving = ref(false);

const { currentUser } = useAuth();
const { saveProject } = useWorkspace();

const projectForm = ref({
  code: '',
  name: '',
  ownerUsername: '',
  description: '',
});

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      if (props.editingProject) {
        projectForm.value = {
          code: props.editingProject.code,
          name: props.editingProject.name,
          ownerUsername: props.editingProject.ownerUsername,
          description: props.editingProject.description,
        };
      } else {
        projectForm.value = {
          code: '',
          name: '',
          ownerUsername: currentUser.value?.username ?? 'admin',
          description: '',
        };
      }
    }
  },
);

watch(visible, (val) => emit('update:modelValue', val));

async function onSave() {
  if (!projectForm.value.code || !projectForm.value.name || !projectForm.value.ownerUsername) {
    message.error('项目编码、项目名称和负责人不能为空');
    return;
  }
  saving.value = true;
  const result = await saveProject(projectForm.value, props.editingProject);
  saving.value = false;
  if (!result.ok) {
    message.error(result.message);
    return;
  }
  message.success(result.message);
  visible.value = false;
}
</script>
