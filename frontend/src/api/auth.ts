import { request } from './http';

export type LoginResult = {
  username: string;
  displayName: string;
  roles: string[];
  token: string;
};

export function loginApi(username: string, password: string) {
  return request<LoginResult>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
}

export function logoutApi() {
  return request<void>('/api/auth/logout', {
    method: 'POST',
  });
}
