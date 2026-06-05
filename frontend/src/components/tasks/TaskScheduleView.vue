<template>
  <TaskExecutionDetail
    v-if="detailTask"
    :task="detailTask"
    @back="backToList"
    @edit="openEdit"
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
          <el-button type="primary" @click="openCreate">新建任务</el-button>
        </div>

        <div class="task-filters">
          <el-input v-model="taskKeyword" clearable placeholder="搜索任务、脚本、文件" />
          <el-select v-model="taskStatusFilter">
            <el-option label="全部状态" value="ALL" />
            <el-option label="待执行" value="PENDING" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </div>

        <div class="task-table">
          <div class="task-table-head">
            <span>状态</span>
            <span>任务名称</span>
            <span>关联脚本</span>
            <span>版本</span>
            <span>最近执行</span>
            <span>操作</span>
          </div>
          <div
            v-for="task in filteredTasks"
            :key="task.id"
            class="task-table-row"
            :class="{ active: selectedTask?.id === task.id }"
            role="button"
            tabindex="0"
            @click="selectTask(task)"
            @keydown.enter="selectTask(task)"
          >
            <span class="status" :class="statusClass(task.status)">{{ taskStatusText(task.status) }}</span>
            <span>
              <strong>{{ task.name }}</strong>
              <small>线程 {{ task.threads }} · Ramp-Up {{ task.rampUp }}s · 持续 {{ task.duration }}s · {{ task.environment }}</small>
            </span>
            <span>
              <strong>{{ scriptById(task.scriptId)?.name ?? '未知脚本' }}</strong>
              <small>{{ scriptById(task.scriptId)?.sourceFile ?? '-' }}</small>
            </span>
            <span>v{{ scriptById(task.scriptId)?.latestVersion ?? '-' }}</span>
            <span>{{ task.lastRunAt ? formatDate(task.lastRunAt) : '未执行' }}</span>
            <span class="task-row-actions">
              <el-button class="task-fixed-button" type="primary" @click.stop="showTaskDetail(task)">详情</el-button>
              <el-button class="task-fixed-button" @click.stop="openEdit(task)">编辑</el-button>
              <el-button
                v-if="task.status !== 'RUNNING'"
                class="task-fixed-button"
                type="primary"
                plain
                @click.stop="runTask(task)"
              >执行</el-button>
            </span>
          </div>
        </div>
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
            <small>脚本 v{{ scriptById(selectedTask.scriptId)?.latestVersion ?? '-' }} · 线程 {{ selectedTask.threads }} · Ramp-Up {{ selectedTask.rampUp }}s · 持续 {{ selectedTask.duration }}s</small>
          </div>
          <div class="task-side-card">
            <span>最近结果</span>
            <strong>{{ selectedTask.summary.samples.toLocaleString() }} samples</strong>
            <small>TPS {{ selectedTask.summary.throughput }}/s · P95 {{ selectedTask.summary.p95 }}ms · 错误率 {{ selectedTask.summary.errorRate }}%</small>
          </div>
          <div class="task-side-card">
            <span>执行状态</span>
            <strong>{{ taskStatusText(selectedTask.status) }}</strong>
            <small>{{ selectedTask.lastRunAt ? `最近执行 ${formatDate(selectedTask.lastRunAt)}` : '尚未执行' }}</small>
          </div>
          <div class="task-side-actions">
            <el-button class="task-side-button" @click="openEdit(selectedTask)">编辑任务</el-button>
            <el-button class="task-side-button" type="primary" @click="showTaskDetail(selectedTask)">查看详情</el-button>
          </div>
        </template>
      </aside>
    </div>

    <TaskConfigDialog v-model="taskDialogVisible" :editing-task="editingTask" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
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
} = useTaskSchedule();

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
</script>
