<template>
  <div class="user-params-config">
    <div class="user-params-toolbar">
      <a-button size="small" @click="addVariable">添加变量</a-button>
      <a-button size="small" @click="addUser">添加用户</a-button>
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
      尚未定义变量。添加变量后，每个用户（虚拟线程）按列取各自的值。
    </div>

    <table v-else class="user-params-table">
      <thead>
        <tr>
          <th class="user-params-name-col">变量名</th>
          <th v-for="(user, userIndex) in users" :key="userIndex" class="user-params-user-col">
            <span>用户 {{ userIndex + 1 }}</span>
            <button
              class="user-params-remove"
              type="button"
              title="删除该用户列"
              @click="removeUser(userIndex)"
            >×</button>
          </th>
          <th class="user-params-add-col">
            <button class="user-params-add" type="button" title="添加用户列" @click="addUser">＋</button>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(name, nameIndex) in names" :key="nameIndex">
          <td class="user-params-name-col">
            <input
              class="user-params-input"
              :value="name"
              placeholder="变量名"
              @input="updateName(nameIndex, ($event.target as HTMLInputElement).value)"
            />
            <button
              class="user-params-remove"
              type="button"
              title="删除该变量行"
              @click="removeVariable(nameIndex)"
            >×</button>
          </td>
          <td v-for="(user, userIndex) in users" :key="userIndex" class="user-params-user-col">
            <input
              class="user-params-input"
              :value="user[nameIndex] ?? ''"
              placeholder="值"
              @input="updateValue(userIndex, nameIndex, ($event.target as HTMLInputElement).value)"
            />
          </td>
          <td class="user-params-add-col"></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ScriptStep } from '../../types';

const props = defineProps<{
  step: ScriptStep;
}>();

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
  gap: 8px;
  margin-bottom: 12px;
}

.user-params-iteration {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 13px;
  color: var(--text-secondary, #595959);
  cursor: pointer;
}

.user-params-checkbox {
  accent-color: var(--primary, #1677ff);
}

.user-params-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-tertiary, #8c8c8c);
  border: 1px dashed var(--border, #d9d9d9);
  border-radius: var(--radius, 8px);
}

.user-params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-params-table th,
.user-params-table td {
  border: 1px solid var(--border, #e5e7eb);
  padding: 6px 8px;
  text-align: left;
  vertical-align: middle;
}

.user-params-table th {
  background: var(--bg-secondary, #f5f5f5);
  font-weight: 600;
  color: var(--text-secondary, #595959);
  white-space: nowrap;
}

.user-params-name-col {
  width: 30%;
}

.user-params-name-col .user-params-input {
  width: calc(100% - 26px);
}

.user-params-user-col .user-params-input {
  width: 100%;
}

.user-params-add-col {
  width: 40px;
  text-align: center;
}

.user-params-user-col th span {
  margin-right: 6px;
}

.user-params-input {
  box-sizing: border-box;
  padding: 4px 8px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-data, monospace);
  background: transparent;
  color: inherit;
}

.user-params-input:focus {
  outline: none;
  border-color: var(--primary, #1677ff);
}

.user-params-remove,
.user-params-add {
  border: none;
  background: transparent;
  color: var(--text-tertiary, #8c8c8c);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.user-params-remove:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.user-params-add {
  color: var(--primary, #1677ff);
}

.user-params-add:hover {
  background: rgba(22, 119, 255, 0.1);
}
</style>
