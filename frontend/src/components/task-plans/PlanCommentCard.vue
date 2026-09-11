<template>
  <div class="anno-card" :class="{ grey: thread.root.resolved }">
    <div class="anno-card-head">
      <span class="anno-avatar" :class="avatarClass(thread.root.author)">{{ avatarInitial }}</span>
      <span class="anno-card-author">{{ thread.root.author }}</span>
      <span class="anno-card-time" :title="fullTime">{{ shortTime }}</span>
      <span class="anno-card-flex" />
      <span v-if="thread.root.anchorText" class="anno-card-state" :class="{ done: thread.root.resolved }">
        {{ thread.root.resolved ? '已解决' : '待处理' }}
      </span>
      <span class="anno-card-acts">
        <a-tooltip v-if="showLocate && thread.root.anchorLine != null" title="定位到正文">
          <button type="button" class="anno-ibtn" aria-label="定位到正文" @click="emit('locate', thread)">
            <AimOutlined />
          </button>
        </a-tooltip>
        <a-tooltip v-if="!editing && thread.root.canEdit" title="编辑批注">
          <button type="button" class="anno-ibtn" aria-label="编辑批注" @click="beginEdit">
            <EditOutlined />
          </button>
        </a-tooltip>
        <a-tooltip v-if="thread.root.canResolve" :title="thread.root.resolved ? '重新打开' : '标记解决'">
          <button
            type="button" class="anno-ibtn"
            :aria-label="thread.root.resolved ? '重新打开' : '标记解决'"
            @click="emit('resolve', thread, !thread.root.resolved)"
          >
            <UndoOutlined v-if="thread.root.resolved" />
            <CheckOutlined v-else />
          </button>
        </a-tooltip>
        <a-tooltip v-if="thread.root.canDelete" title="删除批注">
          <button type="button" class="anno-ibtn danger" aria-label="删除批注" @click="emit('remove', thread, thread.root)">
            <DeleteOutlined />
          </button>
        </a-tooltip>
      </span>
    </div>

    <PlanCommentComposer
      v-if="editing"
      :initial="thread.root.content"
      placeholder="编辑批注…"
      :busy="busy"
      @submit="submitEdit"
      @cancel="editing = false"
    />
    <div v-else class="anno-card-body">{{ thread.root.content }}</div>

    <div v-for="reply in thread.replies" :key="reply.id" class="anno-card-reply">
      <p><b>{{ reply.author }}：</b>{{ reply.content }}</p>
      <a-tooltip v-if="reply.canDelete" title="删除回复">
        <button type="button" class="anno-ibtn danger anno-ibtn-sm" aria-label="删除回复" @click="emit('remove', thread, reply)">
          <DeleteOutlined />
        </button>
      </a-tooltip>
    </div>

    <template v-if="replying">
      <PlanCommentComposer
        placeholder="回复…"
        :busy="busy"
        @submit="submitReply"
        @cancel="replying = false"
      />
    </template>
    <div v-else-if="canReply" class="anno-card-reply-entry" @click="replying = true">回复…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { AimOutlined, CheckOutlined, DeleteOutlined, EditOutlined, UndoOutlined } from '@ant-design/icons-vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentComposer from './PlanCommentComposer.vue';

const props = withDefaults(defineProps<{
  thread: PlanCommentThread;
  showLocate?: boolean;
  doc: ReturnType<typeof usePlanDoc>;
}>(), { showLocate: true });
const emit = defineEmits<{
  (e: 'locate', thread: PlanCommentThread): void;
  (e: 'resolve', thread: PlanCommentThread, resolved: boolean): void;
  (e: 'reply', thread: PlanCommentThread, content: string): void;
  (e: 'edit', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const replying = ref(false);
const editing = ref(false);
const busy = ref(false);
const canReply = computed(() => Boolean(props.doc.permissions.value.COMMENT) && !props.thread.root.resolved);

// ---- 首行空间预算（340px 侧栏）：时间压短、完整时间入 title；作者超宽省略 ----
const created = computed(() => new Date(props.thread.root.createdAt));
const fullTime = computed(() => created.value.toLocaleString());
const shortTime = computed(() => created.value.toDateString() === new Date().toDateString()
  ? created.value.toLocaleTimeString()
  : created.value.toLocaleString());

// ---- 头像占位：首字符 + 人名哈希取色（配色与效果图一致：admin→青、wshg df→橙），后续可替换为真实头像 ----
const avatarInitial = computed(() => props.thread.root.author.trim().charAt(0).toUpperCase() || '?');
function avatarClass(name: string): string {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + (ch.codePointAt(0) ?? 0)) >>> 0;
  return `c${hash % 6}`;
}

function beginEdit() {
  editing.value = true;
}

function submitEdit(content: string) {
  busy.value = true;
  editing.value = false;
  emit('edit', props.thread, content);
  busy.value = false;
}

async function submitReply(content: string) {
  busy.value = true;
  emit('reply', props.thread, content);
  busy.value = false;
  replying.value = false;
}
</script>
