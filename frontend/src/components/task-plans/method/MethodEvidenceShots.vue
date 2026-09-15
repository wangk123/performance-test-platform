<template>
  <!-- 规格：test-method-optimization-prototype.html 补充截图子区（两列大图网格，object-fit: contain 禁止 cover 裁切） -->
  <div class="shot-grid">
    <div v-for="(img, index) in sortedImages" :key="img.id" class="shot">
      <div class="thumb" title="点击放大" @click="lightboxUrl = imageUrls[img.id] ?? null">
        <img v-if="imageUrls[img.id]" :src="imageUrls[img.id]" :alt="img.caption || '补充截图'" />
        <div v-else-if="imageFailed[img.id]" class="noimg">图片加载失败</div>
        <div v-else class="noimg">加载中…</div>
      </div>
      <div class="meta">
        <a-input
          v-model:value="captions[img.id]"
          class="cap"
          size="small"
          :bordered="false"
          :maxlength="200"
          placeholder="填写图注…"
          :disabled="!canEdit"
          @blur="saveCaption(img)"
          @press-enter="blurCaption($event)"
        />
        <span v-if="img.executionId" class="bind-tag" :title="`挂执行 #${img.executionId}`">#{{ img.executionId }}</span>
        <span v-if="canEdit" class="ops">
          <button class="icon-btn" type="button" :disabled="sorting || index === 0" title="上移" @click="move(index, -1)">
            <svg viewBox="0 0 12 12"><path d="M6 2.5v7M2.8 5.7 6 2.5l3.2 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
          <button class="icon-btn" type="button" :disabled="sorting || index === sortedImages.length - 1" title="下移" @click="move(index, 1)">
            <svg viewBox="0 0 12 12"><path d="M6 9.5v-7M2.8 6.3 6 9.5l3.2-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
          <button class="icon-btn" type="button" title="删除" @click="askDelete(img)">
            <svg viewBox="0 0 12 12"><path d="M2.5 2.5l7 7M9.5 2.5l-7 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          </button>
        </span>
      </div>
    </div>
    <a-upload
      v-if="canEdit"
      class="shot-upload"
      accept=".png,.jpg,.jpeg,.webp"
      :show-upload-list="false"
      :before-upload="onBeforeUpload"
    >
      <div class="shot upload">
        <span class="plus">+</span>
        <span>上传截图（png / jpg / webp，≤5MB）</span>
        <span class="upload-bind">{{ executionId ? `挂执行 #${executionId}` : '仅挂场景' }}</span>
      </div>
    </a-upload>

    <div v-if="lightboxUrl" class="lightbox" @click="lightboxUrl = null">
      <img :src="lightboxUrl" alt="截图放大预览" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, toRaw, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { UploadFile } from 'ant-design-vue';
import type { EvidenceImage } from '../../../api/plan-method';
import {
  deleteEvidenceImageApi,
  updateEvidenceImageApi,
  uploadEvidenceImageApi,
} from '../../../api/plan-method';
import { AUTH_TOKEN_KEY } from '../../../api/http';

const props = withDefaults(
  defineProps<{
    planId: number;
    scenarioId: number;
    images: EvidenceImage[];
    executionId: number | null;
    /** 图注/排序/删除/上传需 EDIT 权限（spec §7）。 */
    canEdit?: boolean;
  }>(),
  { canEdit: true },
);
const emit = defineEmits<{ (e: 'refresh'): void }>();

/* ---------- 排序视图 ---------- */

const sortedImages = computed(() =>
  [...props.images].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id),
);

/* ---------- blob objectURL（文件流走 Bearer 头，img src 直连会 401） ---------- */

const imageUrls = reactive<Record<number, string>>({});
const imageFailed = reactive<Record<number, boolean>>({});

watch(
  () => sortedImages.value.map((img) => img.id).join(','),
  () => void syncImageUrls(),
  { immediate: true },
);

async function syncImageUrls() {
  const liveIds = new Set(sortedImages.value.map((img) => img.id));
  for (const [id, url] of Object.entries(imageUrls)) {
    if (!liveIds.has(Number(id))) {
      URL.revokeObjectURL(url);
      delete imageUrls[Number(id)];
      delete imageFailed[Number(id)];
    }
  }
  await Promise.all(
    sortedImages.value
      .filter((img) => !imageUrls[img.id] && !imageFailed[img.id])
      .map(async (img) => {
        try {
          imageUrls[img.id] = await fetchImageUrl(img.id);
        } catch {
          imageFailed[img.id] = true;
        }
      }),
  );
}

async function fetchImageUrl(imageId: number) {
  const token = localStorage.getItem(AUTH_TOKEN_KEY);
  const response = await fetch(`/api/images/${imageId}/file`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) throw new Error('图片加载失败');
  return URL.createObjectURL(await response.blob());
}

onUnmounted(() => {
  for (const url of Object.values(imageUrls)) URL.revokeObjectURL(url);
});

/* ---------- 图注：失焦保存 ---------- */

const captions = reactive<Record<number, string>>({});

watch(
  () => sortedImages.value.map((img) => img.id).join(','),
  () => {
    for (const img of sortedImages.value) {
      if (captions[img.id] === undefined) captions[img.id] = img.caption ?? '';
    }
  },
  { immediate: true },
);

function blurCaption(event: Event) {
  (event.target as HTMLInputElement).blur();
}

async function saveCaption(img: EvidenceImage) {
  const value = (captions[img.id] ?? '').trim();
  if (value === (img.caption ?? '').trim()) return;
  try {
    await updateEvidenceImageApi(img.id, { caption: value });
    message.success('图注已保存');
    emit('refresh');
  } catch (error) {
    captions[img.id] = img.caption ?? '';
    message.error(error instanceof Error ? error.message : '图注保存失败');
  }
}

/* ---------- 排序：上移/下移，按最终位置持久化 sortOrder ---------- */

const sorting = ref(false);

async function move(index: number, direction: -1 | 1) {
  const target = index + direction;
  if (sorting.value || target < 0 || target >= sortedImages.value.length) return;
  sorting.value = true;
  try {
    const reordered = [...toRaw(sortedImages.value)];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    await Promise.all(
      reordered.map((img, order) => (img.sortOrder === order ? null : updateEvidenceImageApi(img.id, { sortOrder: order }))),
    );
    emit('refresh');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '排序失败');
  } finally {
    sorting.value = false;
  }
}

/* ---------- 删除：二次确认 ---------- */

function askDelete(img: EvidenceImage) {
  Modal.confirm({
    title: '删除补充截图？',
    content: `「${img.caption || '未命名截图'}」将永久删除，不可恢复。`,
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteEvidenceImageApi(img.id);
        message.success('已删除');
        emit('refresh');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '删除失败');
      }
    },
  });
}

/* ---------- 上传：手动模式，可选挂当前选中执行 ---------- */

const MAX_SIZE = 5 * 1024 * 1024;
const ACCEPT_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

function onBeforeUpload(file: UploadFile) {
  const raw = file as unknown as File;
  if (!ACCEPT_TYPES.includes(raw.type)) {
    message.warning('仅支持 png / jpg / webp 格式');
    return false;
  }
  if (raw.size > MAX_SIZE) {
    message.warning('截图不能超过 5MB');
    return false;
  }
  void doUpload(raw);
  return false; // 手动模式：拦截自动上传
}

async function doUpload(file: File) {
  try {
    await uploadEvidenceImageApi(props.planId, props.scenarioId, file, undefined, props.executionId ?? undefined);
    message.success('截图已上传');
    emit('refresh');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传失败');
  }
}

/* ---------- 灯箱 ---------- */

const lightboxUrl = ref<string | null>(null);
</script>

<style scoped>
.shot-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  align-items: start;
}

.shot {
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
  overflow: hidden;
  transition: border-color .12s;
}

.shot:hover { border-color: var(--accent); }

.shot .thumb {
  position: relative;
  cursor: zoom-in;
  overflow: hidden;
  background: var(--surface-soft);
}

.shot .thumb img {
  width: 100%;
  height: auto;
  object-fit: contain;
  display: block;
}

.shot .thumb .noimg {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  margin: 8px;
  border-radius: 6px;
  border: 1px dashed var(--line-strong);
  color: var(--muted);
  font-size: 11px;
}

.shot .meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px 4px 4px;
  border-top: 1px solid var(--line);
}

.shot .meta .cap {
  flex: 1;
  min-width: 0;
  font-size: 11.5px;
}

.shot .meta .ops {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity .12s;
}

.shot:hover .meta .ops { opacity: 1; }
.shot .meta .ops:focus-within { opacity: 1; }

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--muted);
  cursor: pointer;
  padding: 0;
  border-radius: 5px;
}

.icon-btn svg { width: 12px; height: 12px; display: block; }
.icon-btn:hover { color: var(--accent); background: var(--accent-soft); }
.icon-btn:disabled { opacity: .35; cursor: not-allowed; }
.icon-btn:last-child:hover { color: var(--danger); background: var(--danger-soft); }

.bind-tag {
  flex: none;
  font: 500 10.5px var(--font-data);
  color: var(--muted);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: 5px;
  padding: 0 6px;
}

.shot-upload { display: block; }

.shot.upload {
  border-style: dashed;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  min-height: 180px;
}

.shot.upload:hover { color: var(--accent); border-color: var(--accent); }
.shot.upload .plus { font-size: 20px; font-weight: 300; line-height: 1; }

.shot.upload .upload-bind {
  font: 500 10.5px var(--font-data);
  color: var(--muted);
}

.lightbox {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgb(10 16 22 / 72%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  cursor: zoom-out;
}

.lightbox img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 8px;
  background: var(--surface);
}

/* a-upload 包装层让位，上传卡直接作为网格单元 */
.shot-grid :deep(.ant-upload-wrapper),
.shot-grid :deep(.ant-upload-select) {
  display: contents;
}

.shot-grid :deep(span.ant-upload) {
  display: block;
  width: 100%;
}
</style>
