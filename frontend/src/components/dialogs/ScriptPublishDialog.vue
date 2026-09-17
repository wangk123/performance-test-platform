<template>
  <a-modal
    :open="open"
    title="发布脚本版本"
    ok-text="发布"
    cancel-text="取消"
    :ok-button-props="{ disabled: !remark.trim() }"
    :confirm-loading="publishing"
    @ok="handlePublish"
    @cancel="close"
  >
    <p class="publish-hint">
      发布后生成不可变版本快照，场景绑定与执行只使用已发布版本；草稿可继续编辑再次发布新版本。
    </p>
    <a-form layout="vertical">
      <a-form-item label="版本号（仅限递增）" required>
        <a-input-number
          v-model:value="versionNo"
          :min="defaultVersionNo"
          :precision="0"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="变更说明" required>
        <a-textarea
          v-model:value="remark"
          :rows="4"
          :maxlength="500"
          show-count
          placeholder="说明本次脚本变更的内容，例如：新增登录前置线程组"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { ScriptAsset } from '../../types';
import { publishScriptApi } from '../../api/scripts';
import { useAuth } from '../../composables/useAuth';

const props = defineProps<{
  open: boolean;
  script: ScriptAsset | null;
  defaultVersionNo: number;
}>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'published'): void;
}>();

const { currentUser } = useAuth();
const versionNo = ref(1);
const remark = ref('');
const publishing = ref(false);

watch(() => props.open, (open) => {
  if (open) {
    versionNo.value = props.defaultVersionNo;
    remark.value = '';
  }
});

async function handlePublish() {
  if (!props.script || !remark.value.trim()) {
    return;
  }
  publishing.value = true;
  try {
    const version = await publishScriptApi(
      props.script.projectId,
      props.script.scriptId,
      versionNo.value,
      remark.value.trim(),
      currentUser.value?.username ?? 'admin',
    );
    message.success(`已发布 v${version.versionNo}`);
    emit('published');
    close();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发布失败');
  } finally {
    publishing.value = false;
  }
}

function close() {
  emit('update:open', false);
}
</script>

<style scoped>
.publish-hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
}
</style>
