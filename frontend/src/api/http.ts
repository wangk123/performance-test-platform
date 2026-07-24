import { CURRENT_USER_KEY } from '../constants';

const pendingRequests = new Map<string, Promise<unknown>>();

export const AUTH_TOKEN_KEY = 'perftest.authToken';

type SessionClearListener = () => void;
const sessionClearListeners = new Set<SessionClearListener>();

export function onAuthSessionCleared(listener: SessionClearListener) {
  sessionClearListeners.add(listener);
  return () => sessionClearListeners.delete(listener);
}

function requestKey(path: string, options: RequestInit) {
  return `${options.method ?? 'GET'} ${path}`;
}

function readAuthToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function clearAuthSession() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(CURRENT_USER_KEY);
  sessionClearListeners.forEach((listener) => listener());
}

function redirectToLogin() {
  if (window.location.pathname === '/login') {
    return;
  }
  const redirect = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const key = requestKey(path, options);
  const shouldDedupe = (options.method ?? 'GET').toUpperCase() === 'GET';
  if (shouldDedupe && pendingRequests.has(key)) {
    return pendingRequests.get(key) as Promise<T>;
  }

  const headers = new Headers(options.headers ?? undefined);
  const token = readAuthToken();
  if (token && !headers.has('Authorization') && path !== '/api/auth/login') {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const promise = fetch(path, { ...options, headers }).then(async (response) => {
    if (response.status === 401 && path !== '/api/auth/login') {
      clearAuthSession();
      redirectToLogin();
      const err = new Error('未登录或登录已失效') as Error & { status?: number };
      err.status = 401;
      throw err;
    }
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败' }));
      const message = error.message ?? error.code ?? `请求失败 (${response.status})`;
      const err = new Error(message) as Error & { status?: number; code?: string };
      err.status = response.status;
      err.code = error.code;
      throw err;
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
  });
  if (shouldDedupe) {
    pendingRequests.set(key, promise);
    promise.finally(() => pendingRequests.delete(key));
  }
  return promise;
}
