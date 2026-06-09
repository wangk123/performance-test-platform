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
      throw new Error(error.message ?? '请求失败');
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
