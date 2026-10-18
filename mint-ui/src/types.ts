export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserResponse;
}

export type Currency = 'INR' | 'USD' | 'EUR' | 'GBP';

export interface CreateAccountRequest {
  currency: Currency;
  initialDeposit?: number;
}

export interface AccountResponse {
  id: string;
  userId: string;
  ownerName: string;
  currency: Currency;
  balance: number;
  frozen: boolean;
  createdAt: string;
}

export type LedgerDirection = 'DEBIT' | 'CREDIT';

export interface LedgerEntryResponse {
  id: string;
  accountId: string;
  transferId: string;
  direction: LedgerDirection;
  amount: number;
  createdAt: string;
}

export interface PageableResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export type TransferStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
}

export interface TransferResponse {
  id: string;
  idempotencyKey: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
  status: TransferStatus;
  failureReason?: string | null;
  createdAt: string;
  completedAt?: string | null;
}

export interface HttpTrace {
  method: string;
  path: string;
  status: number;
  latencyMs: number;
  idempotencyKey?: string;
  responseBody: unknown;
}
