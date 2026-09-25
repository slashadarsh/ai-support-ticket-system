import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import type { ReactElement } from 'react';

/** Queue of fake fetch responses, consumed in call order. */
export function mockFetch(...responses: { status: number; body?: unknown }[]) {
  const fetchMock = vi.fn(async () => {
    const next = responses.shift();
    if (!next) throw new Error('unexpected fetch');
    return new Response(next.body === undefined ? '' : JSON.stringify(next.body), {
      status: next.status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function renderAt(path: string, routePath: string, element: ReactElement) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={routePath} element={element} />
        <Route path="*" element={<div data-testid="navigated" />} />
      </Routes>
    </MemoryRouter>,
  );
}

export const problem = (status: number, detail: string, errors?: { field: string; message: string }[]) => ({
  status,
  body: { type: 'https://tickets.example.com/problems/x', title: 'Problem', status, detail, ...(errors ? { errors } : {}) },
});
