import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { User } from '../types';
import { CURRENT_USER_KEY } from '../constants';
import { AUTH_TOKEN_KEY, clearAuthSession, onAuthSessionCleared } from '../api/http';
import { loginApi, logoutApi } from '../api/auth';

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

if (currentUser.value && !localStorage.getItem(AUTH_TOKEN_KEY)) {
  clearAuthSession();
  currentUser.value = null;
}

onAuthSessionCleared(() => {
  currentUser.value = null;
});

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
    const result = await loginApi(loginForm.username.trim(), loginForm.password.trim());
    localStorage.setItem(AUTH_TOKEN_KEY, result.token);
    currentUser.value = {
      username: result.username,
      displayName: result.displayName,
      roles: result.roles,
    };
    message.success('已登录');
    return true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败');
    return false;
  } finally {
    loginLoading.value = false;
  }
}

async function logout() {
  try {
    await logoutApi();
  } catch {
    // ignore logout API failures; clear local session anyway
  } finally {
    clearAuthSession();
    currentUser.value = null;
  }
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
