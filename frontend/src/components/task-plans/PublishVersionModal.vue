<template>
  <a-modal
    :open="open"
    title="发布版本"
    :width="520"
    ok-text="发布"
    cancel-text="取消"
    :confirm-loading="submitting"
    @cancel="close"
    @ok="submit"
  >
    <a-form layout="vertical" class="version-publish-form">
      <a-form-item label="版本号" required>
        <a-input
          v-model:value="versionNo"
          placeholder="如：V1.0"
          :maxlength="32"
          aria-label="版本号"
        />
      </a-form-item>
      <a-form-item label="变更内容" required>
        <a-textarea
          v-model:value="changeNote"
          :rows="4"
          :maxlength="1000"
          show-count
          placeholder="本次发布包含哪些变更"
          aria-label="变更内容"
        />
      </a-form-item>
    </a-form>
    <p class="version-publish-hint">
      发布将冻结当前文档全文作为版本快照，修订人自动记录为当前用户。
      <template v-if="latest">版本号与最新版本相同（{{ latest.versionNo }}）时将覆盖该版本的快照与修订记录。</template>
    </p>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { listPlanVersionsApi, publishPlanVersionApi } from '../../api/plan-doc';
import type { PlanVersionView } from '../../types';

/**
 * 发布版本弹窗（spec 2026-09-10 §3.2/§3.3）：
 * 版本号默认显示最新版本号（无版本时留空）；与最新同号 → 确认告警后覆盖；
 * 低于最新/历史重号由服务端拒绝，错误信息直接透出。
 */
const props = defineProps<{ open: boolean; planId: number }>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'published'): void;
}>();

const versionNo = ref('');
const changeNote = ref('');
const latest = ref<PlanVersionView | null>(null);
const submitting = ref(false);

watch(() => props.open, (open) => {
  if (open) void reset();
});

async function reset() {
  versionNo.value = '';
  changeNote.value = '';
  latest.value = null;
  const response = await listPlanVersionsApi(props.planId).catch(() => null);
  if (response && response.versions.length) {
    latest.value = response.versions[0];
    versionNo.value = latest.value.versionNo;
  }
}

function close() {
  emit('update:open', false);
}

async function submit() {
  const no = versionNo.value.trim();
  const note = changeNote.value.trim();
  if (!no) {
    message.warning('请填写版本号');
    return;
  }
  if (!note) {
    message.warning('请填写变更内容');
    return;
  }
  const doPublish = async () => {
    submitting.value = true;
    try {
      await publishPlanVersionApi(props.planId, { versionNo: no, changeNote: note });
      message.success(latest.value && no === latest.value.versionNo ? `版本 ${no} 已覆盖更新` : `版本 ${no} 已发布`);
      emit('published');
      close();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败');
    } finally {
      submitting.value = false;
    }
  };
  if (latest.value && no === latest.value.versionNo) {
    Modal.confirm({
      title: `覆盖版本 ${no}？`,
      content: '版本号与最新版本相同，发布将覆盖该版本的快照与修订记录。',
      okText: '覆盖发布',
      cancelText: '再想想',
      onOk: doPublish,
    });
    return;
  }
  await doPublish();
}
</script>

<style scoped>
.version-publish-form {
  margin-bottom: 4px;
}

.version-publish-hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
}
</style>
