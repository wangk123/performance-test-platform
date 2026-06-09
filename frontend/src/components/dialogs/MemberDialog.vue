<template>
  <el-dialog v-model="visible" title="项目成员" width="720px">
    <div v-if="project" class="member-dialog">
      <div class="member-context">
        <div>
          <strong>{{ project.name }}</strong>
          <span>{{ project.code }}</span>
        </div>
        <el-tag :type="project.status === 'ACTIVE' ? 'success' : 'info'">
          {{ projectStatusText(project.status) }}
        </el-tag>
      </div>

      <el-table :data="membersByProject(project.id)" border stripe>
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="displayName" label="姓名" />
        <el-table-column prop="role" label="项目角色" width="150">
          <template #default="{ row }">
            <el-tag :type="row.role === 'OWNER' ? 'success' : 'info'">
              {{ projectRoleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="108">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :disabled="row.role === 'OWNER'"
              @click="removeMember(project!.id, row.username)"
            >移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-form class="member-form" inline @submit.prevent>
        <el-form-item label="成员账号">
          <el-input
            v-model.trim="memberForm.username"
            :disabled="project.status === 'ARCHIVED'"
            placeholder="例如 tester"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model.trim="memberForm.displayName"
            :disabled="project.status === 'ARCHIVED'"
            placeholder="例如 测试同学"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" :disabled="project.status === 'ARCHIVED'" class="role-select">
            <el-option label="项目成员" value="MEMBER" />
            <el-option label="项目负责人" value="OWNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :disabled="project.status === 'ARCHIVED'"
            @click="onAdd"
          >添加成员</el-button>
        </el-form-item>
      </el-form>
      <p v-if="project.status === 'ARCHIVED'" class="form-hint">已归档项目不允许新增成员，先恢复项目后再维护。</p>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { Project, ProjectRole } from '../../types';
import { projectRoleText, projectStatusText } from '../../utils/format';
import { useWorkspace } from '../../composables/useWorkspace';

const props = defineProps<{
  modelValue: boolean;
  project: Project | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const visible = ref(props.modelValue);
const { membersByProject, addMember, removeMember, loadMembers } = useWorkspace();

const memberForm = reactive<{ username: string; displayName: string; role: ProjectRole }>({
  username: '',
  displayName: '',
  role: 'MEMBER',
});

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      memberForm.username = '';
      memberForm.displayName = '';
      memberForm.role = 'MEMBER';
      if (props.project) {
        void loadMembers(props.project.id);
      }
    }
  },
);

watch(visible, (val) => emit('update:modelValue', val));

async function onAdd() {
  if (!props.project) {
    return;
  }
  const result = await addMember(props.project.id, memberForm);
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  ElMessage.success(result.message);
  memberForm.username = '';
  memberForm.displayName = '';
  memberForm.role = 'MEMBER';
}
</script>
