import { request } from './http';

export function loginApi(username: string, password: string) {
  return request<{ username: string; displayName: string; roles: string[] }>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
}
