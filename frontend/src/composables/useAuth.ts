import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { User } from '../types';
import { CURRENT_USER_KEY } from '../constants';
import { delay } from '../utils/format';

function readStoredUser(): User | null {
  const stored = localStorage.getItem(CURRENT_USER_KEY);
  if (!stored) {
    return null;
  }
  try {
    return JSON.parse(stored) as User;
  } catch {
    return null;
  }
}

const currentUser = ref<User | null>(readStoredUser());
const loginLoading = ref(false);

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
});

watch(currentUser, (user) => {
  if (user) {
    localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(CURRENT_USER_KEY);
  }
});

async function login() {
  loginLoading.value = true;
  await delay(220);
  loginLoading.value = false;
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    ElMessage.error('请输入账号和密码');
    return false;
  }
  currentUser.value = {
    username: loginForm.username.trim(),
    displayName: loginForm.username.trim() === 'admin' ? '平台管理员' : loginForm.username.trim(),
    roles: ['ADMIN'],
  };
  ElMessage.success('已进入 Mock 工作台');
  return true;
}

function logout() {
  currentUser.value = null;
}

export function useAuth() {
  return {
    currentUser,
    loginLoading,
    loginForm,
    login,
    logout,
  };
}
