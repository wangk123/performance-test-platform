<template>
  <a-modal
    :open="open"
    title="上传数据文件"
    :confirm-loading="submitting"
    ok-text="上传"
    cancel-text="取消"
    :mask-closable="false"
    @ok="submit"
    @cancel="close"
  >
    <div class="df-upload-body">
      <a-upload-dragger
        accept=".csv"
        :max-count="1"
        v-model:file-list="fileList"
        :before-upload="onBeforeUpload"
      >
        <p class="dragger-hint">点击或拖拽 CSV 文件到此处</p>
        <p class="dragger-sub">仅支持 .csv，单个文件上限 100 MB；上传后自动解析表头、统计行数并计算 SHA-256</p>
      </a-upload-dragger>

      <a-form layout="vertical" class="df-upload-form">
        <a-form-item label="名称" required>
          <a-input v-model:value="name" :maxlength="100" placeholder="如：用户数据" />
        </a-form-item>
        <div class="df-form-row">
          <a-form-item label="首行为表头">
            <a-switch v-model:checked="hasHeader" />
          </a-form-item>
          <a-form-item label="编码">
            <a-select v-model:value="encoding" style="width: 120px">
              <a-select-option value="UTF-8">UTF-8</a-select-option>
              <a-select-option value="GBK">GBK</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item label="备注">
          <a-textarea v-model:value="remark" :rows="2" :maxlength="200" placeholder="选填，说明数据来源或用途" />
        </a-form-item>
      </a-form>

      <p class="df-upload-note">同名再上传 = 版本 +1，执行装配时始终取最新版本。</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { UploadFile } from 'ant-design-vue';
import { uploadDataFileApi } from '../../api/data-files';
import { useAuth } from '../../composables/useAuth';

const props = defineProps<{ projectId: number | null; open: boolean }>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'uploaded'): void }>();

const { currentUser } = useAuth();

const MAX_SIZE = 100 * 1024 * 1024;

const fileList = ref<UploadFile[]>([]);
const name = ref('');
const hasHeader = ref(true);
const encoding = ref('UTF-8');
const remark = ref('');
const submitting = ref(false);

const selectedFile = computed<File | null>(
  () => (fileList.value[0]?.originFileObj as File | undefined) ?? null,
);

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetForm();
    }
  },
);

function resetForm() {
  fileList.value = [];
  name.value = '';
  hasHeader.value = true;
  encoding.value = 'UTF-8';
  remark.value = '';
}

function onBeforeUpload(file: UploadFile) {
  const raw = file as unknown as File;
  if (!raw.name.toLowerCase().endsWith('.csv')) {
    message.warning('仅支持 .csv 文件');
    return false;
  }
  if (raw.size > MAX_SIZE) {
    message.warning('文件不能超过 100 MB');
    return false;
  }
  if (!name.value.trim()) {
    name.value = raw.name.replace(/\.csv$/i, '');
  }
  return false; // 手动模式：拦截自动上传，由「上传」按钮统一提交
}

function close() {
  emit('update:open', false);
}

async function submit() {
  if (props.projectId == null) {
    message.warning('请先选择项目');
    return;
  }
  const file = selectedFile.value;
  if (!file) {
    message.warning('请先选择 CSV 文件');
    return;
  }
  if (!name.value.trim()) {
    message.warning('请填写数据文件名称');
    return;
  }
  submitting.value = true;
  try {
    await uploadDataFileApi(
      props.projectId,
      file,
      name.value.trim(),
      hasHeader.value,
      encoding.value,
      remark.value.trim(),
      currentUser.value?.username ?? 'admin',
    );
    message.success('数据文件已上传');
    emit('uploaded');
    close();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.df-upload-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dragger-hint {
  margin: 8px 0 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.dragger-sub {
  margin: 0 0 8px;
  font-size: 11.5px;
  color: var(--muted);
}

.df-upload-form {
  margin-top: 12px;
}

.df-upload-form :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.df-form-row {
  display: flex;
  gap: 18px;
}

.df-form-row .ant-form-item {
  margin-bottom: 12px;
}

.df-upload-note {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--muted);
}
</style>
