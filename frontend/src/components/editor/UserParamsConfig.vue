<template>
  <div class="user-params-config">
    <div class="user-params-toolbar">
      <span class="user-params-summary"><b>{{ names.length }}</b> 变量 · <b>{{ users.length }}</b> 用户</span>
      <label class="user-params-iteration">
        <input
          type="checkbox"
          class="user-params-checkbox"
          :checked="perIteration"
          @change="updatePerIteration(($event.target as HTMLInputElement).checked)"
        />
        每次迭代更新一次（per iteration）
      </label>
    </div>

    <div v-if="names.length === 0 && users.length === 0" class="user-params-empty">
      <p>尚未定义变量。添加变量后，每个用户（虚拟线程）按列取各自的值。</p>
      <div class="user-params-empty-actions">
        <a-button size="small" type="primary" @click="addVariable">添加变量</a-button>
        <a-button size="small" @click="addUser">添加用户</a-button>
      </div>
    </div>

    <div v-else class="user-params-frame">
      <table class="user-params-table">
        <thead>
          <tr>
            <th class="user-params-name-col">变量名</th>
            <th
              v-for="(user, userIndex) in users"
              :key="userIndex"
              class="user-params-user-col"
              :class="{ 'is-active': activeColumn === userIndex }"
            >
              <span>用户 {{ userIndex + 1 }}</span>
              <button
                class="user-params-col-remove"
                type="button"
                title="删除该用户列"
                @click="removeUser(userIndex)"
              >×</button>
            </th>
            <th class="user-params-add-col">
              <button class="user-params-add" type="button" title="添加用户列" @click="addUser">＋</button>
            </th>
            <th class="user-params-action-col"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(name, nameIndex) in names" :key="nameIndex">
            <td class="user-params-name-col">
              <input
                class="user-params-input user-params-name-input"
                :value="name"
                :placeholder="nameIndex === 0 ? '变量名' : ''"
                @input="updateName(nameIndex, ($event.target as HTMLInputElement).value)"
              />
            </td>
            <td
              v-for="(user, userIndex) in users"
              :key="userIndex"
              class="user-params-user-col"
              :class="{ 'is-col-active': activeColumn === userIndex }"
            >
              <input
                class="user-params-input"
                :value="user[nameIndex] ?? ''"
                @input="updateValue(userIndex, nameIndex, ($event.target as HTMLInputElement).value)"
                @focus="activeColumn = userIndex"
                @blur="activeColumn = null"
              />
            </td>
            <td class="user-params-add-col"></td>
            <td class="user-params-action-col">
              <button
                class="user-params-row-remove"
                type="button"
                title="删除该变量行"
                @click="removeVariable(nameIndex)"
              >
                <svg viewBox="0 0 16 16" aria-hidden="true">
                  <path
                    d="M5.5 2h5l.5 1H13v1H3V3h1.5l.5-1zm-1 3h7l-.6 8.1a1 1 0 0 1-1 .9H7.1a1 1 0 0 1-1-.9L5.5 5z"
                    fill="currentColor"
                  />
                </svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="user-params-add-variable" type="button" @click="addVariable">＋ 添加变量</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ScriptStep } from '../../types';

const props = defineProps<{
  step: ScriptStep;
}>();

const activeColumn = ref<number | null>(null);

// 旧数据（paramsText：k=v 文本）在读取时转换为单用户列，保存后落新结构
const names = computed<string[]>(() => {
  const raw = props.step.config.names;
  if (Array.isArray(raw)) {
    return raw.map(String);
  }
  return legacyParams().map((item) => item[0]);
});

const users = computed<string[][]>(() => {
  const raw = props.step.config.users;
  if (Array.isArray(raw)) {
    return raw.map((column) => (Array.isArray(column) ? column.map(String) : []));
  }
  return [legacyParams().map((item) => item[1])];
});

const perIteration = computed(() => props.step.config.perIteration === true);

function legacyParams(): Array<[string, string]> {
  const text = typeof props.step.config.paramsText === 'string' ? props.step.config.paramsText : '';
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separator = line.indexOf('=');
      return separator > 0
        ? [line.substring(0, separator).trim(), line.substring(separator + 1).trim()]
        : [line, ''];
    });
}

function commit(nextNames: string[], nextUsers: string[][], nextPerIteration = perIteration.value) {
  props.step.config = {
    ...props.step.config,
    names: nextNames,
    users: nextUsers,
    perIteration: nextPerIteration,
    paramsText: undefined,
  };
}

function addVariable() {
  commit([...names.value, ''], users.value.map((column) => [...column, '']));
}

function removeVariable(index: number) {
  commit(
    names.value.filter((_, nameIndex) => nameIndex !== index),
    users.value.map((column) => column.filter((_, nameIndex) => nameIndex !== index)),
  );
}

function addUser() {
  commit(names.value, [...users.value, names.value.map(() => '')]);
}

function removeUser(index: number) {
  commit(names.value, users.value.filter((_, userIndex) => userIndex !== index));
}

function updateName(index: number, value: string) {
  commit(names.value.map((name, nameIndex) => (nameIndex === index ? value : name)), users.value);
}

function updateValue(userIndex: number, nameIndex: number, value: string) {
  commit(
    names.value,
    users.value.map((column, index) =>
      index === userIndex ? column.map((item, i) => (i === nameIndex ? value : item)) : column,
    ),
  );
}

function updatePerIteration(value: boolean) {
  commit(names.value, users.value, value);
}
</script>

<style scoped>
.user-params-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.user-params-summary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 12px;
  border: 1px solid var(--border, #e2e8ee);
  border-radius: 999px;
  background: var(--surface-soft, #f7fafb);
  font-size: 12.5px;
  color: var(--muted, #5c6b7a);
}

.user-params-summary b {
  color: var(--primary, #0b7f8a);
  font-weight: 600;
}

.user-params-iteration {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 13px;
  color: var(--text-secondary, #5c6b7a);
  cursor: pointer;
}

.user-params-checkbox {
  accent-color: var(--primary, #0b7f8a);
}

.user-params-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-tertiary, #8c8c8c);
  border: 1px dashed var(--border, #d9d9d9);
  border-radius: var(--radius, 8px);
}

.user-params-empty p {
  margin-bottom: 12px;
}

.user-params-empty-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.user-params-frame {
  border: 1px solid var(--border, #e2e8ee);
  border-radius: var(--radius, 8px);
  overflow: hidden;
  background: var(--surface, #fff);
}

.user-params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-params-table th,
.user-params-table td {
  border-bottom: 1px solid var(--border, #e2e8ee);
  border-right: 1px solid var(--border, #e2e8ee);
  padding: 0;
  text-align: left;
  vertical-align: middle;
}

.user-params-table tr > *:last-child {
  border-right: none;
}

.user-params-table tbody tr:last-child > * {
  border-bottom: none;
}

.user-params-table th {
  background: var(--surface-soft, #f7fafb);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: 0.02em;
  color: var(--muted, #5c6b7a);
  white-space: nowrap;
  min-height: 34px;
  padding: 8px 10px;
}

.user-params-table th.user-params-user-col.is-active {
  color: var(--primary, #0b7f8a);
  background: var(--primary-bg, #e6f5f6);
  box-shadow: inset 0 -2px 0 var(--primary, #0b7f8a);
}

.user-params-name-col {
  width: 30%;
}

.user-params-add-col {
  width: 40px;
  text-align: center;
}

.user-params-action-col {
  width: 36px;
}

.user-params-input {
  box-sizing: border-box;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border: none;
  outline: none;
  background: transparent;
  color: inherit;
  font-size: 13px;
  font-family: var(--font-data, monospace);
}

.user-params-name-input {
  font-family: var(--font-ui, sans-serif);
}

.user-params-input::placeholder {
  color: var(--text-tertiary, #8c8c8c);
}

.user-params-input:focus {
  background: color-mix(in srgb, var(--primary, #0b7f8a) 6%, var(--surface, #fff));
  box-shadow: inset 2px 0 0 var(--primary, #0b7f8a);
}

.user-params-table tbody tr {
  transition: background 0.12s ease;
}

.user-params-table tbody tr:hover {
  background: color-mix(in srgb, var(--primary, #0b7f8a) 4%, var(--surface, #fff));
}

.user-params-table td.is-col-active {
  background: color-mix(in srgb, var(--primary, #0b7f8a) 5%, var(--surface, #fff));
}

.user-params-col-remove {
  border: none;
  background: transparent;
  color: var(--muted, #5c6b7a);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  padding: 1px 4px;
  margin-left: 2px;
  vertical-align: middle;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.12s ease;
}

th.user-params-user-col:hover .user-params-col-remove,
th.user-params-user-col.is-active .user-params-col-remove {
  opacity: 1;
}

.user-params-col-remove:hover {
  color: #dc2626;
}

.user-params-add {
  border: none;
  background: transparent;
  color: var(--primary, #0b7f8a);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.user-params-add:hover {
  background: color-mix(in srgb, var(--primary, #0b7f8a) 10%, transparent);
}

.user-params-row-remove {
  width: 100%;
  height: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--muted, #5c6b7a);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s ease, color 0.12s ease;
}

.user-params-table tbody tr:hover .user-params-row-remove,
.user-params-table tbody tr:focus-within .user-params-row-remove {
  opacity: 1;
}

.user-params-row-remove:hover {
  color: #dc2626;
}

.user-params-row-remove svg {
  width: 14px;
  height: 14px;
}

.user-params-add-variable {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-top: 1px dashed var(--border-strong, #c8d3dc);
  background: transparent;
  color: var(--primary, #0b7f8a);
  font-size: 12.5px;
  cursor: pointer;
}

.user-params-add-variable:hover {
  background: var(--primary-bg, #e6f5f6);
}
</style>
