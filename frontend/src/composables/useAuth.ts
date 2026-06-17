import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { User } from '../types';
import { CURRENT_USER_KEY } from '../constants';
import { loginApi } from '../api/auth';

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
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    loginLoading.value = false;
    message.error('请输入账号和密码');
    return false;
  }
  try {
    currentUser.value = await loginApi(loginForm.username.trim(), loginForm.password.trim());
    message.success('已登录');
    return true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败');
    return false;
  } finally {
    loginLoading.value = false;
  }
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
