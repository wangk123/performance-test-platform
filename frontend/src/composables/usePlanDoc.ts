import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { PlanComment, PlanCommentAnchor, PlanCommentThread, PlanPermissions, TaskPlan } from '../types';
import {
  addCommentApi,
  editCommentApi,
  getPlanDocumentApi,
  listCommentsApi,
  resolveCommentApi,
  transitionPlanApi,
  updatePlanDocumentApi,
} from '../api/plan-doc';
import { STATUS_LABEL } from '../utils/plan-status';

/** 单一状态 → 展示文案（spec 2026-09-11 §3.1），推导规则收敛于 utils/plan-status。 */
export function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

export function usePlanDoc() {
  const plan = ref<TaskPlan | null>(null);
  const permissions = ref<PlanPermissions>({});
  const comments = ref<PlanComment[]>([]);
  /** 软门禁数据：活跃执行数（QUEUED/RUNNING/STOPPING），执行完成/发布前二次确认用。 */
  const activeExecutions = ref(0);
  const loading = ref(false);

  async function load(planId: number) {
    loading.value = true;
    try {
      const response = await getPlanDocumentApi(planId);
      plan.value = response.plan;
      permissions.value = response.permissions;
      activeExecutions.value = response.activeExecutions ?? 0;
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
    payload?: { comment?: string; conclusion?: string; versionNo?: string },
    successText = '操作成功',
  ) {
    if (!plan.value) return false;
    try {
      const response = await transitionPlanApi(plan.value.id, action, payload);
      plan.value = response.plan;
      permissions.value = response.permissions;
      activeExecutions.value = response.activeExecutions ?? 0;
      message.success(successText);
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
      return false;
    }
  }

  const REVIEW_ROOTS = (list: PlanComment[]) =>
    list.filter((c) => c.kind === 'REVIEW' && c.parentId == null);

  /** 线程视图：根批注（保持 id 升序）+ 各自一层回复（spec §2）。 */
  const threads = computed<PlanCommentThread[]>(() => {
    const byParent = new Map<number, PlanComment[]>();
    for (const comment of comments.value) {
      if (comment.parentId != null) {
        byParent.set(comment.parentId, [...(byParent.get(comment.parentId) ?? []), comment]);
      }
    }
    return REVIEW_ROOTS(comments.value).map((root) => ({
      root,
      replies: byParent.get(root.id) ?? [],
    }));
  });

  const unresolvedCount = computed(() => threads.value.filter((t) => !t.root.resolved).length);

  /** 无锚点根批注（历史批注、驳回原因）——面板/工作台「未锚定」分组。 */
  const unanchoredThreads = computed(() =>
    threads.value.filter((t) => t.root.anchorLine == null || t.root.sectionTitle == null));

  /** deriveAnchors 入参：带完整锚点的根批注。 */
  const anchoredRoots = computed(() =>
    REVIEW_ROOTS(comments.value).filter((c) => c.anchorLine != null && c.sectionTitle != null));

  // ---- 面板开关（spec §3.2）：评审中缺省开；手动选择记 localStorage ----
  const PANEL_KEY = 'plan-comment-panel-open';
  const panelOpen = ref<boolean | null>(readPanelPref());
  const panelEffective = computed(() => panelOpen.value ?? (plan.value?.status === 'IN_REVIEW'));

  function readPanelPref(): boolean | null {
    const stored = localStorage.getItem(PANEL_KEY);
    return stored === null ? null : stored === 'true';
  }

  function togglePanel() {
    panelOpen.value = !panelEffective.value;
    localStorage.setItem(PANEL_KEY, String(panelOpen.value));
  }

  async function addAnchoredComment(input: { content: string; parentId?: number; anchor?: PlanCommentAnchor }) {
    if (!plan.value) return false;
    try {
      await addCommentApi(plan.value.id, input);
      comments.value = await listCommentsApi(plan.value.id);
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批注提交失败');
      return false;
    }
  }

  async function resolveComment(commentId: number, resolved: boolean) {
    if (!plan.value) return false;
    try {
      await resolveCommentApi(plan.value.id, commentId, resolved);
      comments.value = await listCommentsApi(plan.value.id);
      message.success(resolved ? '已解决' : '已重新打开');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
      return false;
    }
  }

  async function editComment(commentId: number, content: string) {
    if (!plan.value) return false;
    try {
      await editCommentApi(plan.value.id, commentId, content);
      comments.value = await listCommentsApi(plan.value.id);
      message.success('批注已更新');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批注更新失败');
      return false;
    }
  }

  // 兼容旧调用（PlanDetailReview，Task 10 重写后移除）。
  async function addComment(content: string) {
    await addAnchoredComment({ content });
  }

  return {
    plan, permissions, comments, loading, activeExecutions, load, refresh, saveDocument, transition, addComment,
    threads, unresolvedCount, unanchoredThreads, anchoredRoots,
    panelOpen, panelEffective, togglePanel, addAnchoredComment, resolveComment, editComment,
  };
}
