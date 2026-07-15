<template>
  <section class="seed-factory">
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2>造数工厂</h2>
          <p class="detail-description">测环境写库：配置数据源 → 过滤录制 → 确认模板 → 批量克隆。</p>
        </div>
      </div>

      <a-tabs v-model:activeKey="tab">
        <a-tab-pane key="datasource" tab="数据源">
          <SeedDatasourcePanel @changed="onDatasourcesChanged" />
        </a-tab-pane>

        <a-tab-pane key="capture" tab="录制">
          <div class="tab-toolbar">
            <a-space>
              <a-button type="primary" :disabled="!!capture.sessionId" @click="openCaptureModal">开始录制</a-button>
              <a-button :disabled="!capture.sessionId" @click="endSample">结束本样本</a-button>
              <a-button :disabled="!capture.sessionId" @click="finishCapture">结束并推断</a-button>
              <span v-if="capture.sessionId">会话 #{{ capture.sessionId }} · 样本 {{ capture.sampleCount }}</span>
            </a-space>
          </div>
          <p v-if="!capture.sessionId" class="detail-description">
            点击「开始录制」配置数据源与库表过滤；录制开始后在业务侧操作，再点「结束本样本」。
          </p>
        </a-tab-pane>

        <a-tab-pane key="template" tab="确认模板">
          <div class="tab-toolbar">
            <a-space>
              <a-select
                v-model:value="selectedTemplateId"
                style="width: 220px"
                placeholder="选择模板"
                :options="templateOptions"
                @change="loadTemplate"
              />
              <a-button
                type="primary"
                :disabled="!templateDetail || templateDetail.status === 'CONFIRMED'"
                :loading="confirming"
                @click="confirmTemplate"
              >
                确认生效
              </a-button>
              <a-button :disabled="!templateDetail || templateDetail.status === 'CONFIRMED'" @click="saveDraft">
                保存草稿
              </a-button>
              <span v-if="templateDetail">状态：{{ templateDetail.status }}</span>
            </a-space>
          </div>
          <div v-for="(op, oi) in draftOps" :key="oi" class="seed-op">
            <strong>{{ op.type }} {{ op.table }}</strong>
            <span v-if="op.riskyNoPk" class="seed-risk"> 无主键，不可执行</span>
            <a-table
              v-if="!op.riskyNoPk"
              size="small"
              :pagination="false"
              :data-source="op.columns"
              :row-key="(r: SeedTemplateColumn) => r.name"
              :columns="colColumns"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'role'">
                  <a-select v-model:value="record.role" style="width: 140px" :options="roleOptions" />
                </template>
                <template v-else-if="column.key === 'generator'">
                  <a-select v-model:value="record.generator" allow-clear style="width: 140px" :options="generatorOptions" />
                </template>
                <template v-else-if="column.key === 'lowAccepted'">
                  <a-checkbox v-model:checked="record.lowAccepted" />
                </template>
              </template>
            </a-table>
          </div>
        </a-tab-pane>

        <a-tab-pane key="clone" tab="克隆任务">
          <div class="tab-toolbar">
            <a-button type="primary" @click="openCloneModal">开始克隆</a-button>
          </div>
          <a-table :columns="jobColumns" :data-source="jobs" :pagination="false" row-key="id" />
        </a-tab-pane>
      </a-tabs>
    </div>

    <a-modal v-model:open="captureModalOpen" title="开始录制" :confirm-loading="capturing" destroy-on-close @ok="startCapture">
      <a-form layout="vertical">
        <a-form-item label="数据源" required>
          <a-select v-model:value="captureForm.datasourceId" placeholder="选择数据源" :options="dsOptions" />
        </a-form-item>
        <a-form-item label="Include（每行一条）" required>
          <a-textarea
            v-model:value="captureForm.includesText"
            :rows="3"
            placeholder="db.table 或 db.order* 或 regex:..."
          />
        </a-form-item>
        <a-form-item label="Exclude（可选，每行一条）">
          <a-textarea v-model:value="captureForm.excludesText" :rows="2" placeholder="db.order_audit" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="cloneModalOpen" title="开始克隆" :confirm-loading="cloning" destroy-on-close @ok="startClone">
      <a-form layout="vertical">
        <a-form-item label="已确认模板" required>
          <a-select v-model:value="cloneForm.templateId" placeholder="选择模板" :options="confirmedTemplateOptions" />
        </a-form-item>
        <a-form-item label="数据源" required>
          <a-select v-model:value="cloneForm.datasourceId" placeholder="选择数据源" :options="dsOptions" />
        </a-form-item>
        <a-form-item label="克隆份数" required>
          <a-input-number v-model:value="cloneForm.cloneCount" :min="1" :max="10000" style="width: 100%" />
        </a-form-item>
        <a-form-item label="失败策略">
          <a-select
            v-model:value="cloneForm.failurePolicy"
            :options="[
              { value: 'CONTINUE', label: '失败继续' },
              { value: 'STOP', label: '失败即停' },
            ]"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import { useWorkspace } from '../../composables/useWorkspace';
import SeedDatasourcePanel from './SeedDatasourcePanel.vue';
import {
  confirmSeedTemplate,
  createSeedCloneJob,
  endSeedSample,
  finishSeedCapture,
  getSeedTemplate,
  listSeedCloneJobs,
  listSeedTemplates,
  startSeedCapture,
  updateSeedTemplate,
  type SeedCloneJob,
  type SeedDatasource,
  type SeedTemplateColumn,
  type SeedTemplateDetail,
  type SeedTemplateOperation,
} from '../../api/seed';

const { currentProject } = useWorkspace();
const tab = ref('datasource');
const capturing = ref(false);
const confirming = ref(false);
const cloning = ref(false);
const datasources = ref<SeedDatasource[]>([]);
const templates = ref<Array<Record<string, unknown>>>([]);
const jobs = ref<SeedCloneJob[]>([]);
const selectedTemplateId = ref<number>();
const templateDetail = ref<SeedTemplateDetail | null>(null);
const draftOps = ref<SeedTemplateOperation[]>([]);
const captureModalOpen = ref(false);
const cloneModalOpen = ref(false);

const capture = reactive({
  sessionId: undefined as number | undefined,
  sampleCount: 0,
});
const captureForm = reactive({
  datasourceId: undefined as number | undefined,
  includesText: '',
  excludesText: '',
});
const cloneForm = reactive({
  templateId: undefined as number | undefined,
  datasourceId: undefined as number | undefined,
  cloneCount: 10,
  failurePolicy: 'CONTINUE',
});

const colColumns: TableColumnsType = [
  { title: '列', dataIndex: 'name', width: 120 },
  { title: '角色', key: 'role', width: 160 },
  { title: '置信', dataIndex: 'confidence', width: 80 },
  { title: '依据', dataIndex: 'rationale' },
  { title: '生成器', key: 'generator', width: 160 },
  { title: '采纳LOW', key: 'lowAccepted', width: 90 },
];
const jobColumns: TableColumnsType = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '模板', dataIndex: 'templateId', width: 80 },
  { title: 'N', dataIndex: 'cloneCount', width: 70 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '成功', dataIndex: 'successBatches', width: 70 },
  { title: '失败', dataIndex: 'failedBatches', width: 70 },
  { title: '操作人', dataIndex: 'createdBy' },
];
const roleOptions = ['LITERAL', 'UNIQUE_REGEN', 'FK_REF', 'BIZ_KEY', 'FORMATTED_RAND', 'TIMESTAMP', 'UPDATE_KEY', 'UPDATE_SET', 'IGNORE']
  .map((value) => ({ value, label: value }));
const generatorOptions = ['seq', 'uuid', 'randomMobile', 'randomIdCard', 'NOW'].map((value) => ({ value, label: value }));

const dsOptions = computed(() => datasources.value.map((d) => ({ value: d.id, label: d.name })));
const templateOptions = computed(() => templates.value.map((t) => ({ value: t.id as number, label: `#${t.id} ${t.status}` })));
const confirmedTemplateOptions = computed(() =>
  templates.value.filter((t) => t.status === 'CONFIRMED').map((t) => ({ value: t.id as number, label: `#${t.id}` })),
);

onMounted(() => {
  void refreshMeta();
});

function onDatasourcesChanged(items: SeedDatasource[]) {
  datasources.value = items;
}

async function refreshMeta() {
  const projectId = currentProject.value?.id;
  if (!projectId) return;
  try {
    templates.value = await listSeedTemplates(projectId);
    jobs.value = await listSeedCloneJobs(projectId);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载失败');
  }
}

function openCaptureModal() {
  Object.assign(captureForm, { datasourceId: undefined, includesText: '', excludesText: '' });
  captureModalOpen.value = true;
}

function openCloneModal() {
  Object.assign(cloneForm, { templateId: undefined, datasourceId: undefined, cloneCount: 10, failurePolicy: 'CONTINUE' });
  cloneModalOpen.value = true;
}

function lines(text: string) {
  return text.split('\n').map((s) => s.trim()).filter(Boolean);
}

async function startCapture() {
  const projectId = currentProject.value?.id;
  if (!projectId) return Promise.reject();
  if (captureForm.datasourceId == null) {
    message.warning('请选择数据源');
    return Promise.reject();
  }
  const includes = lines(captureForm.includesText);
  if (!includes.length) {
    message.warning('Include 不能为空');
    return Promise.reject();
  }
  capturing.value = true;
  try {
    const result = await startSeedCapture(projectId, {
      datasourceId: captureForm.datasourceId,
      provider: 'SNAPSHOT',
      includes,
      excludes: lines(captureForm.excludesText),
    });
    capture.sessionId = result.id as number;
    capture.sampleCount = 0;
    captureModalOpen.value = false;
    message.success('录制已开始，请在业务侧操作后点「结束本样本」');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '开始录制失败');
    return Promise.reject();
  } finally {
    capturing.value = false;
  }
}

async function endSample() {
  const projectId = currentProject.value?.id;
  if (!projectId || !capture.sessionId) return;
  try {
    const result = await endSeedSample(projectId, capture.sessionId);
    capture.sampleCount = result.sampleCount as number;
    message.success(`已记录样本 ${capture.sampleCount}`);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '样本失败');
  }
}

async function finishCapture() {
  const projectId = currentProject.value?.id;
  if (!projectId || !capture.sessionId) return;
  try {
    const result = await finishSeedCapture(projectId, capture.sessionId);
    message.success(`已生成模板 #${result.templateId}`);
    capture.sessionId = undefined;
    await refreshMeta();
    selectedTemplateId.value = result.templateId;
    tab.value = 'template';
    await loadTemplate();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '结束录制失败');
  }
}

async function loadTemplate() {
  const projectId = currentProject.value?.id;
  if (!projectId || !selectedTemplateId.value) return;
  try {
    templateDetail.value = await getSeedTemplate(projectId, selectedTemplateId.value);
    draftOps.value = templateDetail.value.body.operations.map((op) => ({
      ...op,
      columns: op.columns.map((c) => ({ ...c })),
    }));
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载模板失败');
  }
}

async function saveDraft() {
  const projectId = currentProject.value?.id;
  if (!projectId || !selectedTemplateId.value) return;
  try {
    templateDetail.value = await updateSeedTemplate(projectId, selectedTemplateId.value, { operations: draftOps.value });
    message.success('草稿已保存');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  }
}

async function confirmTemplate() {
  const projectId = currentProject.value?.id;
  if (!projectId || !selectedTemplateId.value) return;
  confirming.value = true;
  try {
    await updateSeedTemplate(projectId, selectedTemplateId.value, { operations: draftOps.value });
    templateDetail.value = await confirmSeedTemplate(projectId, selectedTemplateId.value);
    message.success('模板已确认');
    await refreshMeta();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '确认失败');
  } finally {
    confirming.value = false;
  }
}

async function startClone() {
  const projectId = currentProject.value?.id;
  if (!projectId) return Promise.reject();
  if (cloneForm.templateId == null) {
    message.warning('请选择已确认模板');
    return Promise.reject();
  }
  if (cloneForm.datasourceId == null) {
    message.warning('请选择数据源');
    return Promise.reject();
  }
  if (cloneForm.cloneCount == null || cloneForm.cloneCount < 1) {
    message.warning('克隆份数无效');
    return Promise.reject();
  }
  cloning.value = true;
  try {
    await createSeedCloneJob(projectId, { ...cloneForm, operator: 'web' });
    message.success('克隆完成');
    cloneModalOpen.value = false;
    await refreshMeta();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '克隆失败');
    return Promise.reject();
  } finally {
    cloning.value = false;
  }
}
</script>

<style scoped>
.tab-toolbar { margin-bottom: 12px; }
.seed-op { margin-bottom: 16px; }
.seed-risk { color: #cf1322; }
</style>
