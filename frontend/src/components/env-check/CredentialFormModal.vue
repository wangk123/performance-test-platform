<template>
  <a-drawer :open="open" :title="editing ? '编辑凭据' : '新增凭据'" :width="420" destroy-on-close
            @update:open="(value: boolean) => emit('update:open', value)">
    <a-form layout="vertical">
      <a-form-item label="机器地址（IP）" required>
        <a-input v-model:value="form.host" placeholder="如 10.1.1.10" />
      </a-form-item>
      <a-form-item label="SSH 端口">
        <a-input-number v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
      </a-form-item>
      <a-form-item label="账号" required>
        <a-input v-model:value="form.username" placeholder="如 perftest" />
      </a-form-item>
      <a-form-item label="认证方式">
        <a-radio-group v-model:value="form.authType">
          <a-radio value="PASSWORD">密码</a-radio>
          <a-radio value="KEY">密钥</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.authType === 'PASSWORD'" label="密码" :extra="editing ? '留空则不修改原密码' : ''">
        <a-input-password v-model:value="form.password" placeholder="只写不回显" autocomplete="new-password" />
      </a-form-item>
      <a-form-item v-else label="私钥内容" :extra="editing ? '留空则不修改原密钥' : ''">
        <a-textarea v-model:value="form.keyMaterial" :rows="5" placeholder="PEM 私钥，只写不回显" />
      </a-form-item>
      <a-form-item label="备注">
        <a-input v-model:value="form.remark" placeholder="如 订单服务 / 非 22 端口" />
      </a-form-item>
      <a-form-item v-if="planId != null">
        <a-checkbox v-model:checked="form.planScoped">仅当前计划生效</a-checkbox>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-space>
        <a-button @click="emit('update:open', false)">取消</a-button>
        <a-button type="primary" :loading="saving" @click="save">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { EnvCheckCredential } from '../../types';
import { saveCredentialApi } from '../../api/env-check';

const props = defineProps<{
  open: boolean;
  projectId: number;
  /** 计划级覆盖入口传 planId；项目池入口不传（不出现覆盖勾选）。 */
  planId?: number;
  editing: EnvCheckCredential | null;
}>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'saved'): void }>();

const saving = ref(false);
const form = reactive({
  host: '',
  sshPort: 22,
  username: '',
  authType: 'PASSWORD' as EnvCheckCredential['authType'],
  password: '',
  keyMaterial: '',
  remark: '',
  planScoped: false,
});

/** 每次打开按 editing 重放表单（destroy-on-close 后状态复位，这里兜底首帧）。 */
watch(() => props.open, (open) => {
  if (!open) return;
  const record = props.editing;
  form.host = record?.host ?? '';
  form.sshPort = record?.sshPort ?? 22;
  form.username = record?.username ?? '';
  form.authType = record?.authType ?? 'PASSWORD';
  form.password = '';
  form.keyMaterial = '';
  form.remark = record?.remark ?? '';
  form.planScoped = record?.planId != null;
});

async function save() {
  if (!form.host.trim() || !form.username.trim()) {
    message.warning('请填写机器地址与账号');
    return;
  }
  if (form.authType === 'PASSWORD' && !props.editing && !form.password) {
    message.warning('请填写密码');
    return;
  }
  if (form.authType === 'KEY' && !props.editing && !form.keyMaterial.trim()) {
    message.warning('请填写私钥内容');
    return;
  }
  saving.value = true;
  try {
    await saveCredentialApi(props.projectId, {
      host: form.host.trim(),
      sshPort: form.sshPort,
      username: form.username.trim(),
      password: form.authType === 'PASSWORD' && form.password ? form.password : undefined,
      keyMaterial: form.authType === 'KEY' && form.keyMaterial.trim() ? form.keyMaterial.trim() : undefined,
      remark: form.remark.trim() || undefined,
      planId: props.planId != null && form.planScoped ? props.planId : undefined,
    });
    emit('update:open', false);
    message.success('凭据已保存');
    emit('saved');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '凭据保存失败');
  } finally {
    saving.value = false;
  }
}
</script>
