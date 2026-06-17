<template>
  <a-modal v-model:open="visible" title="项目成员" width="720px">
    <div v-if="project" class="member-dialog">
      <div class="member-context">
        <div>
          <strong>{{ project.name }}</strong>
          <span>{{ project.code }}</span>
        </div>
        <a-tag :color="project.status === 'ACTIVE' ? 'success' : 'default'">
          {{ projectStatusText(project.status) }}
        </a-tag>
      </div>

      <a-table
        :columns="memberColumns"
        :data-source="membersByProject(project.id)"
        :pagination="false"
        :row-key="(record: ProjectMember) => record.username"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'OWNER' ? 'success' : 'default'">
              {{ projectRoleText(record.role) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button
              type="link"
              danger
              :disabled="record.role === 'OWNER'"
              @click="removeMember(project!.id, record.username)"
            >移除</a-button>
          </template>
        </template>
      </a-table>

      <a-form class="member-form" inline @submit.prevent>
        <a-form-item label="成员账号">
          <a-input
            v-model:value.trim="memberForm.username"
            :disabled="project.status === 'ARCHIVED'"
            placeholder="例如 tester"
          />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input
            v-model:value.trim="memberForm.displayName"
            :disabled="project.status === 'ARCHIVED'"
            placeholder="例如 测试同学"
          />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="memberForm.role" :disabled="project.status === 'ARCHIVED'" class="role-select">
            <a-select-option label="项目成员" value="MEMBER" />
            <a-select-option label="项目负责人" value="OWNER" />
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            :disabled="project.status === 'ARCHIVED'"
            @click="onAdd"
          >添加成员</a-button>
        </a-form-item>
      </a-form>
      <p v-if="project.status === 'ARCHIVED'" class="form-hint">已归档项目不允许新增成员，先恢复项目后再维护。</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { Project, ProjectMember, ProjectRole } from '../../types';
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

const memberColumns: TableColumnsType<ProjectMember> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '项目角色', dataIndex: 'role', key: 'role', width: 150 },
  { title: '操作', key: 'actions', width: 108 },
];

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
    message.error(result.message);
    return;
  }
  message.success(result.message);
  memberForm.username = '';
  memberForm.displayName = '';
  memberForm.role = 'MEMBER';
}
</script>
