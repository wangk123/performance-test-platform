const pendingRequests = new Map<string, Promise<unknown>>();

function requestKey(path: string, options: RequestInit) {
  return `${options.method ?? 'GET'} ${path}`;
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const key = requestKey(path, options);
  const shouldDedupe = (options.method ?? 'GET').toUpperCase() === 'GET';
  if (shouldDedupe && pendingRequests.has(key)) {
    return pendingRequests.get(key) as Promise<T>;
  }
  const promise = fetch(path, options).then(async (response) => {
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
