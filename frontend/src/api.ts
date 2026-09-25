// Typed client for the backend API (spec/api-contract.md, spec/rag-api-contract.md).

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Category = 'PAYMENT' | 'SHIPPING' | 'ACCOUNT' | 'TECHNICAL' | 'OTHER';

export const STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED'];
export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const CATEGORIES: Category[] = ['PAYMENT', 'SHIPPING', 'ACCOUNT', 'TECHNICAL', 'OTHER'];

export interface Comment {
  id: number;
  author: string;
  body: string;
  createdAt: string;
}

export interface Ticket {
  key: string;
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  status: TicketStatus;
  assignee: string | null;
  resolutionNotes: string | null;
  createdAt: string;
  updatedAt: string;
  allowedTransitions: TicketStatus[];
  comments: Comment[];
}

export type TicketSummary = Pick<
  Ticket,
  'key' | 'title' | 'priority' | 'category' | 'status' | 'assignee' | 'createdAt' | 'updatedAt'
>;

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Citation {
  ticketId: string;
  title: string;
  status: TicketStatus;
}

export interface AskResponse {
  question: string;
  grounded: boolean;
  answer: string;
  citations: Citation[];
  noMatchReason: string | null;
  retrieval: {
    topK: number;
    similarityThreshold: number;
    filter: string | null;
    matches: { ticketId: string; chunkType: string; score: number }[];
  };
}

export interface CreateTicketBody {
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  assignee?: string;
}

export type UpdateTicketBody = Partial<{
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  assignee: string;
  resolutionNotes: string;
}>;

/** A ProblemDetail response, parsed. `status` 0 means the server could not be reached. */
export class ApiError extends Error {
  constructor(
    public status: number,
    public title: string,
    public detail: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(detail);
  }

  /** Human-readable message; never a stack trace (FR-12). */
  get userMessage(): string {
    if (this.status === 0) return 'Cannot reach the server. Is the backend running?';
    if (this.status === 503) return 'The AI assistant is unavailable right now. Ticket management still works.';
    if (this.status >= 500) return 'Something went wrong. Please try again.';
    return this.detail;
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Network error', 'Cannot reach the server.');
  }
  const text = await response.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!response.ok) {
    const problem = (data ?? {}) as { title?: string; detail?: string; errors?: { field: string; message: string }[] };
    const fieldErrors: Record<string, string> = {};
    for (const e of problem.errors ?? []) fieldErrors[e.field] = e.message;
    throw new ApiError(response.status, problem.title ?? 'Error', problem.detail ?? 'Request failed', fieldErrors);
  }
  return data as T;
}

export const api = {
  listTickets: (params: { q?: string; status?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (params.q) query.set('q', params.q);
    if (params.status) query.set('status', params.status);
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 20));
    return request<Page<TicketSummary>>('GET', `/api/tickets?${query}`);
  },
  getTicket: (key: string) => request<Ticket>('GET', `/api/tickets/${encodeURIComponent(key)}`),
  createTicket: (body: CreateTicketBody) => request<Ticket>('POST', '/api/tickets', body),
  updateTicket: (key: string, body: UpdateTicketBody) =>
    request<Ticket>('PATCH', `/api/tickets/${encodeURIComponent(key)}`, body),
  transition: (key: string, targetStatus: TicketStatus) =>
    request<Ticket>('POST', `/api/tickets/${encodeURIComponent(key)}/transitions`, { targetStatus }),
  addComment: (key: string, body: { author: string; body: string }) =>
    request<Comment>('POST', `/api/tickets/${encodeURIComponent(key)}/comments`, body),
  ask: (question: string) => request<AskResponse>('POST', '/api/ai/ask', { question }),
};

export function label(value: string): string {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}
