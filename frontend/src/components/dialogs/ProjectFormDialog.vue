<template>
  <el-dialog
    v-model="visible"
    :title="editingProject ? '编辑项目' : '新建项目'"
    width="560px"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="项目编码">
        <el-input
          v-model.trim="projectForm.code"
          :disabled="Boolean(editingProject)"
          placeholder="例如 loan-core"
        />
      </el-form-item>
      <el-form-item label="项目名称">
        <el-input v-model.trim="projectForm.name" placeholder="例如 信贷核心压测" />
      </el-form-item>
      <el-form-item label="项目负责人">
        <el-input v-model.trim="projectForm.ownerUsername" placeholder="例如 tester" />
      </el-form-item>
      <el-form-item label="项目说明">
        <el-input
          v-model="projectForm.description"
          type="textarea"
          :rows="3"
          placeholder="填写主要压测范围、环境或边界"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { Project } from '../../types';
import { delay } from '../../utils/format';
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
    ElMessage.error('项目编码、项目名称和负责人不能为空');
    return;
  }
  saving.value = true;
  await delay(160);
  const result = saveProject(projectForm.value, props.editingProject);
  saving.value = false;
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  ElMessage.success(result.message);
  visible.value = false;
}
</script>
