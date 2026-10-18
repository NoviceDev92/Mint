import type {
  AccountResponse,
  ApiErrorResponse,
  AuthResponse,
  CreateAccountRequest,
  HttpTrace,
  LedgerEntryResponse,
  LoginRequest,
  PageableResponse,
  RegisterRequest,
  TransferRequest,
  TransferResponse,
  UserResponse,
} from './types';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const STORAGE_KEY = 'mint_auth_session';

// Single-flight lock so concurrent 401s don't burn single-use refresh tokens
let refreshPromise: Promise<AuthResponse | null> | null = null;
let traceListener: ((trace: HttpTrace) => void) | null = null;

export function onHttpTrace(cb: (trace: HttpTrace) => void) {
  traceListener = cb;
}

export function getStoredSession(): AuthResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthResponse;
  } catch {
    return null;
  }
}

export function setStoredSession(session: AuthResponse | null) {
  if (!session) {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

async function rotateRefreshToken(refreshToken: string): Promise<AuthResponse | null> {
  try {
    const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      setStoredSession(null);
      return null;
    }
    const nextSession: AuthResponse = await res.json();
    setStoredSession(nextSession);
    return nextSession;
  } catch {
    setStoredSession(null);
    return null;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  retryOn401 = true
): Promise<T> {
  const session = getStoredSession();
  const headers = new Headers(options.headers || {});
  headers.set('Content-Type', 'application/json');

  if (session?.accessToken && !path.startsWith('/api/v1/auth/login') && !path.startsWith('/api/v1/auth/register')) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const start = performance.now();
  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  const latencyMs = Math.round(performance.now() - start);

  // Automatic 401 Refresh & Replay
  if (res.status === 401 && retryOn401 && session?.refreshToken && !path.includes('/api/v1/auth/')) {
    if (!refreshPromise) {
      refreshPromise = rotateRefreshToken(session.refreshToken).finally(() => {
        refreshPromise = null;
      });
    }
    const refreshed = await refreshPromise;
    if (refreshed) {
      return request<T>(path, options, false);
    }
  }

  const body = await res.json().catch(() => null);

  if (traceListener) {
    traceListener({
      method: options.method || 'GET',
      path,
      status: res.status,
      latencyMs,
      idempotencyKey: headers.get('Idempotency-Key') || undefined,
      responseBody: body,
    });
  }

  if (!res.ok) {
    throw (body as ApiErrorResponse) || {
      timestamp: new Date().toISOString(),
      status: res.status,
      error: res.statusText,
      code: 'NETWORK_OR_HTTP_ERROR',
      message: `Request failed with status ${res.status}`,
      path,
    };
  }

  return body as T;
}

export const mintApi = {
  register: (payload: RegisterRequest) =>
    request<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  login: (payload: LoginRequest) =>
    request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  manualRefresh: async () => {
    const session = getStoredSession();
    if (!session?.refreshToken) throw new Error('No refresh token available');
    const updated = await request<AuthResponse>(
      '/api/v1/auth/refresh',
      {
        method: 'POST',
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      },
      false
    );
    setStoredSession(updated);
    return updated;
  },

  getMe: () => request<UserResponse>('/api/v1/auth/me'),

  getMyAccounts: () => request<AccountResponse[]>('/api/v1/accounts/me'),

  getAccountById: (id: string) => request<AccountResponse>(`/api/v1/accounts/${id}`),

  createAccount: (payload: CreateAccountRequest) =>
    request<AccountResponse>('/api/v1/accounts', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getLedgerEntries: (accountId: string, page = 0, size = 10) =>
    request<PageableResponse<LedgerEntryResponse>>(
      `/api/v1/accounts/${accountId}/entries?page=${page}&size=${size}`
    ),

  createTransfer: (payload: TransferRequest, idempotencyKey: string) =>
    request<TransferResponse>('/api/v1/transfers', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(payload),
    }),

  getTransferById: (id: string) => request<TransferResponse>(`/api/v1/transfers/${id}`),
};
