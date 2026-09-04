<template>
  <section class="panel publish-tab">
    <div v-if="can('PUBLISH')" class="publish-form">
      <h3>发布</h3>
      <p class="publish-hint">前置：报告已生成、无活跃执行；发布将冻结文档并固化快照。</p>
      <a-textarea v-model:value="conclusion" :rows="3" placeholder="总体结论（发布人确认，必填）" />
      <a-button type="primary" :disabled="!conclusion.trim()" @click="publish">发布</a-button>
    </div>
    <a-alert v-else-if="doc.plan.value?.phase === 'PUBLISH'" type="success" show-icon message="该计划已发布（终态）。变更请发起修订。" />

    <div v-if="can('NEW_REVISION')" class="publish-revision">
      <a-button @click="doc.transition('new-revision', undefined, '已发起新修订')">发起新修订</a-button>
    </div>

    <h3>发布快照</h3>
    <a-table :columns="snapshotColumns" :data-source="snapshots" :pagination="false" row-key="id" size="small" :locale="{ emptyText: '暂无快照' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'publishedAt'">
          {{ new Date(record.publishedAt).toLocaleString() }}
        </template>
      </template>
    </a-table>

    <h3>只读分享链接</h3>
    <div class="share-actions" v-if="can('SHARE')">
      <a-button @click="createShare">创建分享链接（默认 30 天）</a-button>
    </div>
    <a-table :columns="shareColumns" :data-source="shares" :pagination="false" row-key="id" size="small" :locale="{ emptyText: '暂无分享' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'url'">
          <code>{{ shareUrl(record.token) }}</code>
        </template>
        <template v-else-if="column.key === 'expiresAt'">
          {{ record.expiresAt ? new Date(record.expiresAt).toLocaleString() : '永久' }}
        </template>
        <template v-else-if="column.key === 'state'">
          {{ shareState(record) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button v-if="can('SHARE') && !record.revokedAt" type="link" danger size="small" @click="revoke(record)">撤销</a-button>
        </template>
      </template>
    </a-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createShareApi, listSharesApi, listSnapshotsApi, revokeShareApi } from '../../api/plan-doc';
import type { PlanShareTokenView, PlanSnapshotView } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();

const conclusion = ref('');
const snapshots = ref<PlanSnapshotView[]>([]);
const shares = ref<PlanShareTokenView[]>([]);

const snapshotColumns = [
  { title: 'revision', dataIndex: 'revision', key: 'revision' },
  { title: '发布人', dataIndex: 'publishedBy', key: 'publishedBy' },
  { title: '发布时间', key: 'publishedAt' },
];
const shareColumns = [
  { title: '链接', key: 'url' },
  { title: '过期时间', key: 'expiresAt' },
  { title: '状态', key: 'state' },
  { title: '操作', key: 'actions' },
];

onMounted(() => void reload());

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  snapshots.value = await listSnapshotsApi(planId).catch(() => []);
  shares.value = await listSharesApi(planId).catch(() => []);
}

async function publish() {
  const ok = await props.doc.transition('publish', { conclusion: conclusion.value.trim() }, '已发布');
  if (ok) await reload();
}

async function createShare() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  try {
    await createShareApi(planId);
    await reload();
    message.success('分享链接已创建');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建分享链接失败');
  }
}

async function revoke(record: PlanShareTokenView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  try {
    await revokeShareApi(planId, record.id);
    await reload();
    message.success('已撤销');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '撤销失败');
  }
}

function shareUrl(token: string) {
  return `${window.location.origin}/share/plans/${token}`;
}

function shareState(record: PlanShareTokenView) {
  if (record.revokedAt) return '已撤销';
  if (record.expiresAt && new Date(record.expiresAt) < new Date()) return '已过期';
  return '有效';
}
</script>

<style scoped>
.publish-form { display: flex; flex-direction: column; gap: 8px; max-width: 520px; margin-bottom: 16px; }
.publish-hint { color: var(--muted); }
</style>
