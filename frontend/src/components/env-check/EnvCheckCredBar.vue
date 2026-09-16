<template>
  <section class="ec-cred-bar">
    <span class="stat">
      目标机 <b>{{ preview.total }}</b> 台 · 凭据就绪
      <b :class="preview.ready === preview.total ? 'ok' : 'danger'">{{ preview.ready }}</b> 台
    </span>
    <span v-if="preview.missing.length" class="missing">
      ✗ {{ preview.missing.join('、') }} 未配置凭据，勾选远程检查项将被拦截
    </span>
    <span class="spacer"></span>
    <CredentialFormModal v-model:open="overrideOpen" :project-id="projectId" :plan-id="planId" :editing="null" @saved="emit('saved')" />
    <a-button size="small" @click="overrideOpen = true">🔒 计划级覆盖</a-button>
    <a-button size="small" type="primary" @click="goProject">去项目配置凭据 ↗</a-button>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import type { EnvCheckTargetsPreview } from '../../types';
import CredentialFormModal from './CredentialFormModal.vue';

const props = defineProps<{ planId: number; projectId: number; preview: EnvCheckTargetsPreview }>();
const emit = defineEmits<{ (e: 'saved'): void }>();

const overrideOpen = ref(false);
const router = useRouter();

function goProject() {
  void router.push(`/projects/${props.projectId}/env-check`);
}
</script>

<style scoped>
.ec-cred-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13px;
}
.ec-cred-bar .stat b.ok { color: var(--ok); }
.ec-cred-bar .stat b.danger { color: var(--danger); }
.ec-cred-bar .missing { color: var(--danger); font-size: 12.5px; }
.ec-cred-bar .spacer { flex: 1; }
</style>
