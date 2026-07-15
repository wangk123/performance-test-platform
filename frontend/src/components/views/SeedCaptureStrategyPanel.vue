<template>
  <div>
    <div class="tab-toolbar">
      <a-space wrap>
        <a-select
          v-model:value="selectedId"
          style="width: 260px"
          placeholder="选择当前策略"
          :options="strategyOptions"
          @change="onSelect"
        />
        <a-button type="primary" @click="openCreate">新建策略</a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="strategies"
      :pagination="false"
      :loading="loading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'datasource'">
          {{ datasourceName(record.datasourceId) }}
        </template>
        <template v-else-if="column.key === 'filters'">
          <div class="filter-summary">
            Include {{ record.includes.length }} 条
            <span v-if="record.excludes.length">· Exclude {{ record.excludes.length }} 条</span>
          </div>
        </template>
        <template v-else-if="column.key === 'runtime'">
          {{ record.threadCount }} 线程 · {{ record.batchRows.toLocaleString() }} 行/批
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button
              size="small"
              type="primary"
              :loading="executingId === record.id"
              @click="execute(record)"
            >
              手动执行
            </a-button>
            <a-button size="small" danger @click="remove(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-empty v-if="!loading && !strategies.length" description="还没有录制策略" />

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑录制策略' : '新建录制策略'"
      :confirm-loading="saving"
      destroy-on-close
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="策略名称" required>
          <a-input v-model:value="form.name" placeholder="如：订单基线采集" />
        </a-form-item>
        <a-form-item label="数据源" required>
          <a-select v-model:value="form.datasourceId" placeholder="选择数据源" :options="datasourceOptions" />
        </a-form-item>
        <a-form-item label="Include（每行一条）" required>
          <a-textarea
            v-model:value="form.includesText"
            :rows="4"
            placeholder="db.table、db.order* 或 regex:..."
          />
        </a-form-item>
        <a-form-item label="Exclude（可选，每行一条）">
          <a-textarea v-model:value="form.excludesText" :rows="3" placeholder="db.order_audit" />
        </a-form-item>
        <a-space align="start" :size="16">
          <a-form-item label="工作线程数" required>
            <a-input-number v-model:value="form.threadCount" :min="1" :max="32" />
          </a-form-item>
          <a-form-item label="每批行数" required>
            <a-input-number v-model:value="form.batchRows" :min="100" :max="100000" />
          </a-form-item>
        </a-space>
      </a-form>
      <a-alert
        type="info"
        show-icon
        message="保存策略不会开始采集；需要在列表中单独点击「手动执行」。"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import { Modal, message } from 'ant-design-vue';
import {
  createSeedCaptureStrategy,
  deleteSeedCaptureStrategy,
  executeSeedCaptureStrategy,
  listSeedCaptureStrategies,
  updateSeedCaptureStrategy,
  type SeedCaptureSample,
  type SeedCaptureStrategy,
  type SeedDatasource,
} from '../../api/seed';

const props = defineProps<{ datasources: SeedDatasource[] }>();

const emit = defineEmits<{
  changed: [strategies: SeedCaptureStrategy[]];
  selected: [strategyId: number | undefined];
  executed: [sample: SeedCaptureSample];
}>();

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
const loading = ref(false);
const saving = ref(false);
const executingId = ref<number | null>(null);
const strategies = ref<SeedCaptureStrategy[]>([]);
const selectedId = ref<number>();
const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  name: '',
  datasourceId: undefined as number | undefined,
  includesText: '',
  excludesText: '',
  threadCount: 4,
  batchRows: 1000,
});

const columns: TableColumnsType<SeedCaptureStrategy> = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '数据源', key: 'datasource', width: 160 },
  { title: '过滤范围', key: 'filters', width: 190 },
  { title: '采集配置', key: 'runtime', width: 180 },
  { title: '版本', dataIndex: 'configVersion', key: 'configVersion', width: 70 },
  { title: '操作', key: 'actions', width: 260 },
];

const strategyOptions = computed(() =>
  strategies.value.map((strategy) => ({
    value: strategy.id,
    label: `#${strategy.id} ${strategy.name}`,
  })),
);
const datasourceOptions = computed(() =>
  props.datasources.map((datasource) => ({
    value: datasource.id,
    label: datasource.name,
  })),
);

watch(projectId, (id) => {
  if (id) void load();
  else strategies.value = [];
}, { immediate: true });

async function load() {
  const id = projectId.value;
  if (!id) return;
  loading.value = true;
  try {
    strategies.value = await listSeedCaptureStrategies(id);
    const selectedStillExists = strategies.value.some((strategy) => strategy.id === selectedId.value);
    if (!selectedStillExists) selectedId.value = strategies.value[0]?.id;
    emit('changed', strategies.value);
    emit('selected', selectedId.value);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载录制策略失败');
  } finally {
    loading.value = false;
  }
}

function onSelect(value: number) {
  selectedId.value = value;
  emit('selected', value);
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    name: '',
    datasourceId: props.datasources[0]?.id,
    includesText: '',
    excludesText: '',
    threadCount: 4,
    batchRows: 1000,
  });
  modalOpen.value = true;
}

function openEdit(strategy: SeedCaptureStrategy) {
  editingId.value = strategy.id;
  selectedId.value = strategy.id;
  Object.assign(form, {
    name: strategy.name,
    datasourceId: strategy.datasourceId,
    includesText: strategy.includes.join('\n'),
    excludesText: strategy.excludes.join('\n'),
    threadCount: strategy.threadCount,
    batchRows: strategy.batchRows,
  });
  modalOpen.value = true;
}

function lines(text: string) {
  return text.split('\n').map((value) => value.trim()).filter(Boolean);
}

function requiredText(value: string, label: string) {
  if (!value.trim()) throw new Error(`${label}不能为空`);
  return value.trim();
}

async function save() {
  const id = projectId.value;
  if (!id) return Promise.reject();
  try {
    const name = requiredText(form.name, '策略名称');
    if (form.datasourceId == null) throw new Error('请选择数据源');
    const includes = lines(form.includesText);
    if (!includes.length) throw new Error('Include 不能为空');
    if (form.threadCount == null || form.threadCount < 1 || form.threadCount > 32) {
      throw new Error('工作线程数应为 1–32');
    }
    if (form.batchRows == null || form.batchRows < 100 || form.batchRows > 100000) {
      throw new Error('每批行数应为 100–100000');
    }
    const body = {
      name,
      datasourceId: form.datasourceId,
      includes,
      excludes: lines(form.excludesText),
      threadCount: form.threadCount,
      batchRows: form.batchRows,
    };
    saving.value = true;
    const saved = editingId.value
      ? await updateSeedCaptureStrategy(id, editingId.value, body)
      : await createSeedCaptureStrategy(id, body);
    selectedId.value = saved.id;
    modalOpen.value = false;
    message.success(editingId.value ? '策略已更新' : '策略已保存');
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存策略失败');
    return Promise.reject();
  } finally {
    saving.value = false;
  }
}

function execute(strategy: SeedCaptureStrategy) {
  const id = projectId.value;
  if (!id || executingId.value != null) return;
  Modal.confirm({
    title: `执行策略「${strategy.name}」？`,
    content: '这会创建一个异步样本。同一数据源已有采集任务时，后端会拒绝本次执行。',
    okText: '开始采集',
    cancelText: '取消',
    async onOk() {
      executingId.value = strategy.id;
      try {
        const sample = await executeSeedCaptureStrategy(id, strategy.id);
        selectedId.value = strategy.id;
        message.success(`样本 #${sample.id} 已排队`);
        emit('executed', sample);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '执行策略失败');
        return Promise.reject();
      } finally {
        executingId.value = null;
      }
    },
  });
}

function remove(strategy: SeedCaptureStrategy) {
  const id = projectId.value;
  if (!id) return;
  Modal.confirm({
    title: `删除策略「${strategy.name}」？`,
    content: '已生成的样本和分析记录不会被删除。',
    okType: 'danger',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSeedCaptureStrategy(id, strategy.id);
        message.success('策略已删除');
        await load();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除策略失败');
        return Promise.reject();
      }
    },
  });
}

function datasourceName(datasourceId: number) {
  return props.datasources.find((datasource) => datasource.id === datasourceId)?.name
    ?? `数据源 #${datasourceId}`;
}
</script>

<style scoped>
.tab-toolbar { margin-bottom: 12px; }
.filter-summary { white-space: normal; }
</style>
