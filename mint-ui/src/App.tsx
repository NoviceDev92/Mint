import React, { useEffect, useState, useCallback } from 'react';
import type {
  AccountResponse,
  ApiErrorResponse,
  AuthResponse,
  Currency,
  HttpTrace,
  LedgerEntryResponse,
  PageableResponse,
  TransferResponse,
} from './types';
import { getStoredSession, mintApi, onHttpTrace, setStoredSession } from './api';

const CURRENCIES: Currency[] = ['INR', 'USD', 'EUR', 'GBP'];

function formatCurrency(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : 'en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(amount);
  } catch {
    return `${currency} ${Number(amount).toFixed(2)}`;
  }
}

export default function App() {
  const [session, setSession] = useState<AuthResponse | null>(() => getStoredSession());
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [authError, setAuthError] = useState<ApiErrorResponse | null>(null);

  // Accounts & Ledger State
  const [accounts, setAccounts] = useState<AccountResponse[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>('');
  const [ledgerPage, setLedgerPage] = useState<PageableResponse<LedgerEntryResponse> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);

  // Create Wallet Modal State
  const [showWalletModal, setShowWalletModal] = useState(false);
  const [newCurrency, setNewCurrency] = useState<Currency>('INR');
  const [initialDeposit, setInitialDeposit] = useState('1000.00');

  // Transfer Console State
  const [sourceId, setSourceId] = useState('');
  const [destinationId, setDestinationId] = useState('');
  const [transferAmount, setTransferAmount] = useState('100.00');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID());
  const [lastTransfer, setLastTransfer] = useState<TransferResponse | null>(null);
  const [transferError, setTransferError] = useState<ApiErrorResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Telemetry & Transfer Lookup Inspector
  const [lastTrace, setLastTrace] = useState<HttpTrace | null>(null);
  const [inspectedTransfer, setInspectedTransfer] = useState<TransferResponse | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    onHttpTrace((trace) => setLastTrace(trace));
  }, []);

  const loadAccounts = useCallback(async () => {
    if (!session) return;
    try {
      const list = await mintApi.getMyAccounts();
      setAccounts(list);
      if (list.length > 0 && !selectedAccountId) {
        setSelectedAccountId(list[0].id);
        setSourceId(list[0].id);
      }
    } catch (err) {
      const apiErr = err as ApiErrorResponse;
      if (apiErr.status === 401) {
        setSession(null);
      }
    }
  }, [session, selectedAccountId]);

  const loadLedgerEntries = useCallback(async (accId: string, page: number) => {
    if (!accId) return;
    try {
      const res = await mintApi.getLedgerEntries(accId, page, 10);
      setLedgerPage(res);
    } catch (err) {
      console.error('Failed to load ledger entries:', err);
    }
  }, []);

  useEffect(() => {
    loadAccounts();
  }, [loadAccounts]);

  useEffect(() => {
    if (selectedAccountId) {
      loadLedgerEntries(selectedAccountId, pageNumber);
    }
  }, [selectedAccountId, pageNumber, loadLedgerEntries]);

  const copyUuid = (uuid: string) => {
    navigator.clipboard.writeText(uuid);
    setCopiedId(uuid);
    setTimeout(() => setCopiedId(null), 1200);
  };

  // -------------------- AUTH HANDLERS --------------------
  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    try {
      const res =
        authMode === 'login'
          ? await mintApi.login({ email, password })
          : await mintApi.register({ email, password, fullName });
      setStoredSession(res);
      setSession(res);
    } catch (err) {
      setAuthError(err as ApiErrorResponse);
    }
  };

  const handleLogout = () => {
    setStoredSession(null);
    setSession(null);
    setAccounts([]);
    setSelectedAccountId('');
    setLedgerPage(null);
  };

  const handleManualTokenRefresh = async () => {
    try {
      const updated = await mintApi.manualRefresh();
      setSession(updated);
    } catch {
      handleLogout();
    }
  };

  // -------------------- WALLET CREATION --------------------
  const handleCreateWallet = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const deposit = parseFloat(initialDeposit);
      const created = await mintApi.createAccount({
        currency: newCurrency,
        initialDeposit: isNaN(deposit) ? 0 : deposit,
      });
      setShowWalletModal(false);
      await loadAccounts();
      setSelectedAccountId(created.id);
      if (!sourceId) setSourceId(created.id);
    } catch (err) {
      alert((err as ApiErrorResponse).message || 'Failed to create account');
    }
  };

  // -------------------- TRANSFER EXECUTION --------------------
  const executeTransferCall = async (keyToUse: string) => {
    setIsSubmitting(true);
    setTransferError(null);
    const sourceAcc = accounts.find((a) => a.id === sourceId);
    const currency = sourceAcc?.currency || 'INR';

    try {
      const res = await mintApi.createTransfer(
        {
          sourceAccountId: sourceId,
          destinationAccountId: destinationId.trim(),
          amount: parseFloat(transferAmount),
          currency,
        },
        keyToUse
      );
      setLastTransfer(res);
      await loadAccounts();
      if (selectedAccountId) {
        await loadLedgerEntries(selectedAccountId, pageNumber);
      }
    } catch (err) {
      setTransferError(err as ApiErrorResponse);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleInspectTransfer = async (transferId: string) => {
    try {
      const tx = await mintApi.getTransferById(transferId);
      setInspectedTransfer(tx);
    } catch (err) {
      alert((err as ApiErrorResponse).message || 'Could not fetch transfer details');
    }
  };

  // -------------------- UNAUTHENTICATED VIEW --------------------
  if (!session) {
    return (
      <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-center items-center p-4 font-sans antialiased">
        <div className="w-full max-w-sm bg-white border border-slate-200 rounded-sm p-6">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3 mb-5">
            <div>
              <h1 className="text-sm font-semibold uppercase tracking-wider text-slate-900">
                Mint // Ledger Engine
              </h1>
              <p className="text-xs text-slate-500 mt-0.5">Spring Boot 3.3 &bull; Double-Entry ACID</p>
            </div>
            <span className="text-[11px] font-mono bg-slate-100 border border-slate-200 px-2 py-0.5 text-slate-600">
              v1
            </span>
          </div>

          <div className="flex border border-slate-200 rounded-sm mb-4 text-xs font-medium">
            <button
              type="button"
              onClick={() => {
                setAuthMode('login');
                setAuthError(null);
              }}
              className={`flex-1 py-1.5 ${
                authMode === 'login' ? 'bg-slate-900 text-white' : 'bg-white text-slate-600'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => {
                setAuthMode('register');
                setAuthError(null);
              }}
              className={`flex-1 py-1.5 ${
                authMode === 'register' ? 'bg-slate-900 text-white' : 'bg-white text-slate-600'
              }`}
            >
              Register User
            </button>
          </div>

          {authError && (
            <div className="mb-4 p-2.5 bg-rose-50 border border-rose-200 text-xs font-mono text-rose-800">
              <div className="font-semibold">
                [{authError.status}] {authError.code}
              </div>
              <div className="mt-0.5 text-rose-700">{authError.message}</div>
            </div>
          )}

          <form onSubmit={handleAuthSubmit} className="space-y-3 text-xs">
            {authMode === 'register' && (
              <div>
                <label className="block text-slate-600 mb-1 font-medium">Full Name</label>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Alice Doe"
                  className="w-full border border-slate-300 px-2.5 py-1.5 rounded-sm focus:outline-none focus:border-slate-900"
                />
              </div>
            )}
            <div>
              <label className="block text-slate-600 mb-1 font-medium">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="engineer@mint.local"
                className="w-full border border-slate-300 px-2.5 py-1.5 rounded-sm focus:outline-none focus:border-slate-900"
              />
            </div>
            <div>
              <label className="block text-slate-600 mb-1 font-medium">
                Password {authMode === 'register' && '(min 8 chars)'}
              </label>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full border border-slate-300 px-2.5 py-1.5 rounded-sm focus:outline-none focus:border-slate-900"
              />
            </div>
            <button
              type="submit"
              className="w-full bg-slate-900 text-white font-medium py-2 rounded-sm hover:bg-slate-800"
            >
              {authMode === 'login' ? 'POST /api/v1/auth/login' : 'POST /api/v1/auth/register'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);

  // -------------------- AUTHENTICATED CONSOLE --------------------
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans antialiased">
      {/* Top Session & JWT Bar */}
      <header className="bg-white border-b border-slate-200 px-6 py-2.5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-900">
            Mint // Core Ledger Console
          </span>
          <span className="text-[11px] font-mono bg-slate-100 border border-slate-200 px-2 py-0.5 text-slate-700">
            {session.user.fullName} ({session.user.email})
          </span>
          <span className="text-[10px] font-mono bg-slate-900 text-white px-1.5 py-0.5">
            {session.user.role}
          </span>
        </div>

        <div className="flex items-center gap-2 text-xs font-mono">
          <button
            onClick={handleManualTokenRefresh}
            title="Rotates single-use refreshToken via POST /api/v1/auth/refresh"
            className="border border-slate-300 bg-white px-2.5 py-1 hover:bg-slate-50 text-slate-700"
          >
            Rotate Refresh Token
          </button>
          <button
            onClick={loadAccounts}
            className="border border-slate-300 bg-white px-2.5 py-1 hover:bg-slate-50 text-slate-700"
          >
            Sync State
          </button>
          <button
            onClick={handleLogout}
            className="border border-slate-300 bg-white px-2.5 py-1 hover:bg-rose-50 hover:border-rose-300 text-rose-700"
          >
            Sign Out
          </button>
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-6 space-y-6">
        {/* 1. Account Balances Grid */}
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
              Wallets &amp; Accounts ({accounts.length})
            </h2>
            <button
              onClick={() => setShowWalletModal(true)}
              className="text-xs font-medium bg-slate-900 text-white px-3 py-1.5 rounded-sm hover:bg-slate-800"
            >
              + New Account
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {accounts.map((acc) => {
              const isSelected = acc.id === selectedAccountId;
              return (
                <div
                  key={acc.id}
                  onClick={() => {
                    setSelectedAccountId(acc.id);
                    setPageNumber(0);
                  }}
                  className={`cursor-pointer bg-white border p-4 rounded-sm transition-colors ${
                    isSelected
                      ? 'border-slate-900 ring-1 ring-slate-900'
                      : 'border-slate-200 hover:border-slate-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-mono font-semibold px-1.5 py-0.5 bg-slate-100 border border-slate-200">
                      {acc.currency}
                    </span>
                    {acc.frozen ? (
                      <span className="text-[10px] font-mono px-1.5 py-0.5 bg-rose-50 text-rose-700 border border-rose-200">
                        FROZEN
                      </span>
                    ) : (
                      <span className="text-[10px] font-mono px-1.5 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200">
                        ACTIVE
                      </span>
                    )}
                  </div>

                  <div className="text-lg font-mono font-semibold tabular-nums text-slate-900 my-1">
                    {formatCurrency(acc.balance, acc.currency)}
                  </div>

                  <div className="mt-3 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] font-mono text-slate-500">
                    <span className="truncate max-w-[160px]" title={acc.id}>
                      {acc.id}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        copyUuid(acc.id);
                      }}
                      className="text-slate-700 hover:text-slate-950 underline ml-2 shrink-0"
                    >
                      {copiedId === acc.id ? 'Copied' : 'Copy ID'}
                    </button>
                  </div>
                </div>
              );
            })}

            {accounts.length === 0 && (
              <div className="col-span-full bg-white border border-slate-200 p-6 text-center text-xs font-mono text-slate-500">
                No wallets provisioned for this user. Click "+ New Account" to initialize a ledger account.
              </div>
            )}
          </div>
        </section>

        {/* 2. Main Two-Column Workspace: Transfer Console + Double-Entry Statement */}
        <div className="grid grid-cols-12 gap-6">
          {/* Left Column: Transfer Dispatch & Idempotency Exerciser */}
          <div className="col-span-12 lg:col-span-5 space-y-4">
            <section className="bg-white border border-slate-200 rounded-sm p-4">
              <div className="border-b border-slate-200 pb-2.5 mb-4 flex items-center justify-between">
                <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                  Transfer Console
                </h2>
                <span className="text-[11px] font-mono text-slate-400">POST /api/v1/transfers</span>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  executeTransferCall(idempotencyKey);
                }}
                className="space-y-3 text-xs"
              >
                <div>
                  <label className="block text-slate-600 mb-1 font-medium">Source Account</label>
                  <select
                    value={sourceId}
                    onChange={(e) => setSourceId(e.target.value)}
                    required
                    className="w-full font-mono border border-slate-300 px-2.5 py-1.5 rounded-sm bg-white focus:outline-none focus:border-slate-900"
                  >
                    <option value="" disabled>
                      Select source account...
                    </option>
                    {accounts.map((a) => (
                      <option key={a.id} value={a.id}>
                        [{a.currency}] {formatCurrency(a.balance, a.currency)} — {a.id.slice(0, 8)}...
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="text-slate-600 font-medium">Destination Account UUID</label>
                    {accounts.length > 1 && (
                      <button
                        type="button"
                        onClick={() => {
                          const other = accounts.find((a) => a.id !== sourceId);
                          if (other) setDestinationId(other.id);
                        }}
                        className="text-[11px] font-mono text-slate-500 underline hover:text-slate-900"
                      >
                        Fill 2nd Wallet
                      </button>
                    )}
                  </div>
                  <input
                    type="text"
                    required
                    value={destinationId}
                    onChange={(e) => setDestinationId(e.target.value)}
                    placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                    className="w-full font-mono border border-slate-300 px-2.5 py-1.5 rounded-sm focus:outline-none focus:border-slate-900"
                  />
                </div>

                <div>
                  <label className="block text-slate-600 mb-1 font-medium">
                    Amount ({accounts.find((a) => a.id === sourceId)?.currency || 'Currency'})
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={transferAmount}
                    onChange={(e) => setTransferAmount(e.target.value)}
                    className="w-full font-mono tabular-nums border border-slate-300 px-2.5 py-1.5 rounded-sm focus:outline-none focus:border-slate-900"
                  />
                </div>

                {/* Visible Idempotency Key Chip */}
                <div className="pt-1">
                  <div className="flex justify-between items-center mb-1">
                    <label className="text-slate-600 font-medium">Header: Idempotency-Key</label>
                    <button
                      type="button"
                      onClick={() => setIdempotencyKey(crypto.randomUUID())}
                      className="text-[11px] font-mono text-slate-700 underline hover:text-slate-950"
                    >
                      Generate New UUID
                    </button>
                  </div>
                  <div className="flex items-center bg-slate-50 border border-slate-300 px-2.5 py-1.5 rounded-sm font-mono text-[11px] text-slate-700">
                    <span className="truncate flex-1">{idempotencyKey}</span>
                  </div>
                </div>

                <div className="pt-2 flex gap-2">
                  <button
                    type="submit"
                    disabled={isSubmitting || !sourceId}
                    className="flex-1 bg-slate-900 text-white font-medium py-2 rounded-sm hover:bg-slate-800 disabled:opacity-50"
                  >
                    Execute Transfer
                  </button>
                  <button
                    type="button"
                    disabled={isSubmitting || !sourceId}
                    onClick={() => executeTransferCall(idempotencyKey)}
                    title="Re-sends the exact same payload and Idempotency-Key without rotating it"
                    className="px-3 bg-white border border-slate-300 text-slate-800 font-mono py-2 rounded-sm hover:bg-slate-100 disabled:opacity-50"
                  >
                    Simulate Retry
                  </button>
                </div>
              </form>

              {/* Structured Domain Error Display */}
              {transferError && (
                <div className="mt-4 p-3 bg-rose-50 border border-rose-200 font-mono text-xs text-rose-900">
                  <div className="flex justify-between font-semibold">
                    <span>{transferError.code}</span>
                    <span>HTTP {transferError.status}</span>
                  </div>
                  <div className="mt-1 text-rose-700">{transferError.message}</div>
                </div>
              )}

              {/* Last Transfer Receipt */}
              {lastTransfer && !transferError && (
                <div className="mt-4 p-3 bg-emerald-50/60 border border-emerald-200 font-mono text-xs text-slate-800 space-y-1">
                  <div className="flex justify-between items-center">
                    <span className="font-semibold text-emerald-800">
                      Transfer {lastTransfer.status}
                    </span>
                    <span className="text-[11px] text-slate-500">
                      {formatCurrency(lastTransfer.amount, lastTransfer.currency)}
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-600 truncate">
                    TX ID: {lastTransfer.id}
                  </div>
                  <div className="text-[11px] text-slate-600 truncate">
                    Idempotency: {lastTransfer.idempotencyKey}
                  </div>
                </div>
              )}
            </section>

            {/* Live HTTP Wire Trace Inspector */}
            {lastTrace && (
              <section className="bg-slate-900 text-slate-100 rounded-sm p-4 font-mono text-xs">
                <div className="flex justify-between items-center border-b border-slate-800 pb-2 mb-2 text-[11px]">
                  <span className="text-slate-400">
                    {lastTrace.method} {lastTrace.path}
                  </span>
                  <span
                    className={
                      lastTrace.status >= 200 && lastTrace.status < 300
                        ? 'text-emerald-400'
                        : 'text-rose-400'
                    }
                  >
                    HTTP {lastTrace.status} &bull; {lastTrace.latencyMs}ms
                  </span>
                </div>
                {lastTrace.idempotencyKey && (
                  <div className="text-[11px] text-slate-400 mb-2 truncate">
                    Idempotency-Key: {lastTrace.idempotencyKey}
                  </div>
                )}
                <pre className="overflow-x-auto max-h-48 text-[11px] text-slate-300 leading-relaxed">
                  {JSON.stringify(lastTrace.responseBody, null, 2)}
                </pre>
              </section>
            )}
          </div>

          {/* Right Column: Paginated Double-Entry Ledger Statement Table */}
          <div className="col-span-12 lg:col-span-7">
            <section className="bg-white border border-slate-200 rounded-sm">
              <div className="px-4 py-3 border-b border-slate-200 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                    Double-Entry Ledger Statement
                  </h2>
                  {selectedAccount && (
                    <p className="text-[11px] font-mono text-slate-400 mt-0.5">
                      Account: {selectedAccount.id} ({selectedAccount.currency})
                    </p>
                  )}
                </div>

                {ledgerPage && (
                  <div className="flex items-center gap-2 text-xs font-mono">
                    <span className="text-slate-500">
                      Page {ledgerPage.number + 1} of {Math.max(1, ledgerPage.totalPages)} (
                      {ledgerPage.totalElements} entries)
                    </span>
                    <button
                      disabled={ledgerPage.first}
                      onClick={() => setPageNumber((p) => Math.max(0, p - 1))}
                      className="border border-slate-300 px-2 py-0.5 disabled:opacity-40 hover:bg-slate-50"
                    >
                      Prev
                    </button>
                    <button
                      disabled={ledgerPage.last}
                      onClick={() => setPageNumber((p) => p + 1)}
                      className="border border-slate-300 px-2 py-0.5 disabled:opacity-40 hover:bg-slate-50"
                    >
                      Next
                    </button>
                  </div>
                )}
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                      <th className="py-2.5 px-4">Direction</th>
                      <th className="py-2.5 px-4 text-right">Amount</th>
                      <th className="py-2.5 px-4">Entry ID</th>
                      <th className="py-2.5 px-4">Linked Transfer ID</th>
                      <th className="py-2.5 px-4">Timestamp (UTC)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono text-xs">
                    {ledgerPage?.content.map((entry) => {
                      const isCredit = entry.direction === 'CREDIT';
                      const curr = selectedAccount?.currency || 'INR';
                      return (
                        <tr key={entry.id} className="hover:bg-slate-50">
                          <td className="py-2.5 px-4">
                            <span
                              className={`inline-block px-1.5 py-0.5 text-[10px] font-semibold border ${
                                isCredit
                                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                  : 'bg-rose-50 text-rose-700 border-rose-200'
                              }`}
                            >
                              {entry.direction}
                            </span>
                          </td>
                          <td
                            className={`py-2.5 px-4 text-right tabular-nums font-medium ${
                              isCredit ? 'text-emerald-700' : 'text-rose-700'
                            }`}
                          >
                            {isCredit ? '+' : '-'}
                            {formatCurrency(entry.amount, curr)}
                          </td>
                          <td className="py-2.5 px-4 text-slate-500" title={entry.id}>
                            {entry.id.slice(0, 8)}...
                          </td>
                          <td className="py-2.5 px-4">
                            <button
                              type="button"
                              onClick={() => handleInspectTransfer(entry.transferId)}
                              className="text-slate-800 underline hover:text-slate-950"
                              title="Click to inspect full TransferResponse via GET /api/v1/transfers/{id}"
                            >
                              {entry.transferId.slice(0, 12)}...
                            </button>
                          </td>
                          <td className="py-2.5 px-4 text-slate-400 tabular-nums">
                            {new Date(entry.createdAt).toISOString().replace('T', ' ').slice(0, 19)}
                          </td>
                        </tr>
                      );
                    })}

                    {(!ledgerPage || ledgerPage.empty) && (
                      <tr>
                        <td colSpan={5} className="py-10 text-center text-slate-400">
                          No immutable ledger entries recorded for this account yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </main>

      {/* Modal: Provision New Wallet */}
      {showWalletModal && (
        <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white border border-slate-200 rounded-sm w-full max-w-sm p-5">
            <div className="flex justify-between items-center border-b border-slate-200 pb-2.5 mb-4">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-800">
                Provision New Account
              </h3>
              <button
                onClick={() => setShowWalletModal(false)}
                className="text-xs font-mono text-slate-400 hover:text-slate-800"
              >
                [ESC]
              </button>
            </div>
            <form onSubmit={handleCreateWallet} className="space-y-3 text-xs">
              <div>
                <label className="block text-slate-600 mb-1 font-medium">Currency</label>
                <select
                  value={newCurrency}
                  onChange={(e) => setNewCurrency(e.target.value as Currency)}
                  className="w-full font-mono border border-slate-300 px-2.5 py-1.5 rounded-sm bg-white"
                >
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-slate-600 mb-1 font-medium">
                  Initial Deposit (Optional)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={initialDeposit}
                  onChange={(e) => setInitialDeposit(e.target.value)}
                  className="w-full font-mono tabular-nums border border-slate-300 px-2.5 py-1.5 rounded-sm"
                />
              </div>
              <div className="pt-2 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowWalletModal(false)}
                  className="px-3 py-1.5 border border-slate-300 text-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-slate-900 text-white font-medium"
                >
                  POST /api/v1/accounts
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Linked Transfer Inspector */}
      {inspectedTransfer && (
        <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white border border-slate-200 rounded-sm w-full max-w-md p-5 font-mono text-xs">
            <div className="flex justify-between items-center border-b border-slate-200 pb-2.5 mb-3">
              <span className="font-sans font-semibold uppercase tracking-wider text-slate-700">
                Transfer Audit Record
              </span>
              <button
                onClick={() => setInspectedTransfer(null)}
                className="text-slate-400 hover:text-slate-900"
              >
                [CLOSE]
              </button>
            </div>
            <div className="space-y-2 text-[11px]">
              <div className="flex justify-between">
                <span className="text-slate-500">Transfer ID:</span>
                <span className="text-slate-900">{inspectedTransfer.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Idempotency Key:</span>
                <span className="text-slate-900">{inspectedTransfer.idempotencyKey}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Source Account:</span>
                <span className="text-slate-900">{inspectedTransfer.sourceAccountId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Destination Account:</span>
                <span className="text-slate-900">{inspectedTransfer.destinationAccountId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Amount:</span>
                <span className="text-slate-900 font-semibold">
                  {formatCurrency(inspectedTransfer.amount, inspectedTransfer.currency)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Status:</span>
                <span className="text-slate-900 font-semibold">{inspectedTransfer.status}</span>
              </div>
              {inspectedTransfer.failureReason && (
                <div className="flex justify-between text-rose-700">
                  <span>Failure Reason:</span>
                  <span>{inspectedTransfer.failureReason}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-slate-500">Created At:</span>
                <span className="text-slate-900">{inspectedTransfer.createdAt}</span>
              </div>
              {inspectedTransfer.completedAt && (
                <div className="flex justify-between">
                  <span className="text-slate-500">Completed At:</span>
                  <span className="text-slate-900">{inspectedTransfer.completedAt}</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
