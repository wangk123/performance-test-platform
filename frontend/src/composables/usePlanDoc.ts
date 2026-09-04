import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment, PlanPermissions, TaskPlan } from '../types';
import {
  addCommentApi,
  getPlanDocumentApi,
  listCommentsApi,
  transitionPlanApi,
  updatePlanDocumentApi,
} from '../api/plan-doc';

export function statusLabel(phase: string, status: string): string {
  if (phase === 'REVIEW') return { PENDING: '待评审', IN_REVIEW: '评审中', APPROVED: '评审通过' }[status] ?? status;
  if (phase === 'EXECUTION') return { PENDING: '待执行', RUNNING: '执行中', DONE: '执行完成' }[status] ?? status;
  if (phase === 'REPORT') return { PENDING: '待生成', GENERATING: '生成中', DONE: '已生成' }[status] ?? status;
  if (phase === 'PUBLISH') return '已发布';
  return '草稿';
}

export function usePlanDoc() {
  const plan = ref<TaskPlan | null>(null);
  const permissions = ref<PlanPermissions>({});
  const comments = ref<PlanComment[]>([]);
  const loading = ref(false);

  async function load(planId: number) {
    loading.value = true;
    try {
      const response = await getPlanDocumentApi(planId);
      plan.value = response.plan;
      permissions.value = response.permissions;
      comments.value = await listCommentsApi(planId).catch(() => []);
    } finally {
      loading.value = false;
    }
  }

  async function refresh() {
    if (plan.value) await load(plan.value.id);
  }

  /** 保存整篇原文；409 冲突时拉取服务器版并返回 'conflict'（调用方弹三选一）。 */
  async function saveDocument(markdown: string): Promise<'ok' | 'conflict' | 'error'> {
    if (!plan.value) return 'error';
    try {
      const updated = await updatePlanDocumentApi(plan.value.id, plan.value.revision, markdown);
      plan.value = updated;
      message.success('文档已保存');
      return 'ok';
    } catch (error) {
      const text = error instanceof Error ? error.message : '';
      if (text.includes('PLAN_REVISION_CONFLICT')) {
        await load(plan.value.id); // 冲突体里的 serverMarkdown 也可用；这里直接拉最新全文
        return 'conflict';
      }
      message.error(text || '保存失败');
      return 'error';
    }
  }

  async function transition(
    action: Parameters<typeof transitionPlanApi>[1],
    payload?: { comment?: string; conclusion?: string },
    successText = '操作成功',
  ) {
    if (!plan.value) return false;
    try {
      const response = await transitionPlanApi(plan.value.id, action, payload);
      plan.value = response.plan;
      permissions.value = response.permissions;
      message.success(successText);
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
      return false;
    }
  }

  async function addComment(content: string) {
    if (!plan.value) return;
    await addCommentApi(plan.value.id, content);
    comments.value = await listCommentsApi(plan.value.id);
  }

  return { plan, permissions, comments, loading, load, refresh, saveDocument, transition, addComment };
}
