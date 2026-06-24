<template>
  <TaskExecutionDetail
    v-if="detailTask"
    :task="detailTask"
    @back="backToList"
  />

  <section v-else class="task-schedule">
    <div class="metrics-grid compact">
      <div class="metric">
        <span>任务总数</span>
        <strong>{{ projectTasks.length }}</strong>
      </div>
      <div class="metric">
        <span>运行中</span>
        <strong>{{ runningTaskCount }}</strong>
      </div>
      <div class="metric">
        <span>待执行</span>
        <strong>{{ pendingTaskCount }}</strong>
      </div>
      <div class="metric">
        <span>失败任务</span>
        <strong>{{ failedTaskCount }}</strong>
      </div>
    </div>

    <div class="task-list-layout">
      <section class="panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Task Queue</span>
            <h2>任务列表</h2>
            <p>任务是脚本和执行配置的快照，列表页只处理任务状态、执行和详情入口。</p>
          </div>
          <a-button type="primary" @click="openCreate">新建任务</a-button>
        </div>

        <div class="task-filters">
          <a-input v-model:value="taskKeyword" allow-clear placeholder="搜索任务、脚本、文件" />
          <a-select v-model:value="taskStatusFilter">
            <a-select-option label="全部状态" value="ALL" />
            <a-select-option label="待执行" value="PENDING" />
            <a-select-option label="运行中" value="RUNNING" />
            <a-select-option label="成功" value="SUCCESS" />
            <a-select-option label="失败" value="FAILED" />
          </a-select>
        </div>

        <a-table
          class="workspace-table"
          :columns="taskColumns"
          :data-source="filteredTasks"
          :pagination="false"
          :row-key="(record: TestTask) => record.id"
          :custom-row="taskRowEvents"
          :row-class-name="taskRowClassName"
          :locale="{ emptyText: '暂无匹配任务。' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <span class="status" :class="statusClass(record.status)">{{ taskStatusText(record.status) }}</span>
            </template>
            <template v-else-if="column.key === 'name'">
              <div class="table-main-cell">
                <strong>{{ record.name }}</strong>
                <small>{{ formatScriptThreadGroups(record.scriptId) }}</small>
              </div>
            </template>
            <template v-else-if="column.key === 'script'">
              <div class="table-main-cell">
                <strong>{{ scriptById(record.scriptId)?.name ?? '未知脚本' }}</strong>
                <small>{{ scriptById(record.scriptId)?.sourceFile ?? '-' }}</small>
              </div>
            </template>
            <template v-else-if="column.key === 'version'">v{{ scriptById(record.scriptId)?.latestVersion ?? '-' }}</template>
            <template v-else-if="column.key === 'lastRunAt'">{{ record.lastRunAt ? formatDate(record.lastRunAt) : '未执行' }}</template>
            <template v-else-if="column.key === 'actions'">
              <span class="task-row-actions">
                <a-button class="task-fixed-button" type="primary" @click.stop="showTaskDetail(record)">详情</a-button>
                <a-button class="task-fixed-button" @click.stop="openEdit(record)">编辑</a-button>
                <a-button
                  v-if="record.status !== 'RUNNING'"
                  class="task-fixed-button"
                  type="primary"
                  @click.stop="runTask(record)"
                >执行</a-button>
                <a-button
                  v-if="record.status !== 'RUNNING'"
                  class="task-fixed-button"
                  danger
                  @click.stop="deleteTask(record)"
                >删除</a-button>
              </span>
            </template>
          </template>
        </a-table>
      </section>

      <aside class="panel task-side-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Selected Task</span>
            <h2>当前任务</h2>
            <p>选中任务后展示执行快照和最近结果。</p>
          </div>
        </div>
        <template v-if="selectedTask">
          <div class="task-side-card">
            <span>任务名称</span>
            <strong>{{ selectedTask.name }}</strong>
            <small>脚本 v{{ scriptById(selectedTask.scriptId)?.latestVersion ?? '-' }} · {{ formatScriptThreadGroups(selectedTask.scriptId) }}</small>
          </div>
          <div class="task-side-card">
            <span>最近结果</span>
            <strong>{{ selectedTask.summary.samples.toLocaleString() }} samples</strong>
            <small>TPS {{ selectedTask.summary.throughput }}/s · P95 {{ selectedTask.summary.p95 }}ms · 错误率 {{ selectedTask.summary.errorRate }}%</small>
          </div>
          <div class="task-side-card">
            <span>执行状态</span>
            <strong>{{ taskStatusText(selectedTask.status) }}</strong>
            <small v-if="selectedTask.status === 'FAILED' && selectedTask.errorMessage">{{ selectedTask.errorMessage }}</small>
            <small v-else>{{ selectedTask.lastRunAt ? `最近执行 ${formatDate(selectedTask.lastRunAt)}` : '尚未执行' }}</small>
          </div>
          <div class="task-side-actions">
            <a-button class="task-side-button" @click="openEdit(selectedTask)">编辑任务</a-button>
            <a-button class="task-side-button" type="primary" @click="showTaskDetail(selectedTask)">查看详情</a-button>
          </div>
        </template>
      </aside>
    </div>

    <TaskConfigDialog v-model="taskDialogVisible" :editing-task="editingTask" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { TaskStatus, TestTask } from '../../types';
import { formatDate } from '../../utils/format';
import { useTaskSchedule } from '../../composables/useTaskSchedule';
import TaskConfigDialog from './TaskConfigDialog.vue';
import TaskExecutionDetail from './TaskExecutionDetail.vue';

const taskDialogVisible = ref(false);
const editingTask = ref<TestTask | null>(null);
const {
  taskKeyword,
  taskStatusFilter,
  projectTasks,
  filteredTasks,
  selectedTask,
  detailTask,
  runningTaskCount,
  pendingTaskCount,
  failedTaskCount,
  taskStatusText,
  scriptById,
  selectTask,
  showTaskDetail,
  backToList,
  runTask,
  deleteTask,
} = useTaskSchedule();

const taskColumns: TableColumnsType<TestTask> = [
  { title: '状态', key: 'status', width: 86 },
  { title: '任务名称', key: 'name', minWidth: 220 },
  { title: '关联脚本', key: 'script', minWidth: 180 },
  { title: '版本', key: 'version', width: 70 },
  { title: '最近执行', key: 'lastRunAt', width: 124 },
  { title: '操作', key: 'actions', width: 254 },
];

function statusClass(status: TaskStatus) {
  return {
    pending: status === 'PENDING',
    running: status === 'RUNNING',
    success: status === 'SUCCESS',
    error: status === 'FAILED',
  };
}

function openCreate() {
  editingTask.value = null;
  taskDialogVisible.value = true;
}

function openEdit(task: TestTask) {
  editingTask.value = task;
  taskDialogVisible.value = true;
}

function taskRowEvents(record: TestTask) {
  return {
    onClick: () => selectTask(record),
  };
}

function taskRowClassName(record: TestTask) {
  return selectedTask.value?.id === record.id ? 'selected-table-row' : '';
}

function formatScriptThreadGroups(scriptId: number): string {
  const script = scriptById(scriptId);
  if (!script) return '未知脚本';
  const groups = script.steps.filter(s => s.type === 'THREAD_GROUP');
  if (groups.length === 0) return '无线程组';
  return `${groups.length} 个线程组`;
}
</script>
