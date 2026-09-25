import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, ApiError, label, STATUSES, type Page, type TicketSummary } from '../api';
import { ErrorBanner, formatDate, StatusBadge } from '../components/Common';

export default function TicketListPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? '0');

  const [search, setSearch] = useState(q);
  const [data, setData] = useState<Page<TicketSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .listTickets({ q, status, page })
      .then((result) => !cancelled && (setData(result), setError(null)))
      .catch((e) => !cancelled && setError(e instanceof ApiError ? e.userMessage : String(e)))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [q, status, page]);

  const update = (next: Record<string, string>) => {
    const merged: Record<string, string> = { q, status, page: '0', ...next };
    // Keep the URL clean: drop empty values and the default first page.
    setParams(Object.fromEntries(Object.entries(merged).filter(([k, v]) => v !== '' && !(k === 'page' && v === '0'))));
  };

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    update({ q: search.trim() });
  };

  return (
    <section>
      <div className="page-header">
        <h1>Tickets</h1>
        <Link className="button primary" to="/tickets/new">
          New ticket
        </Link>
      </div>

      <form className="toolbar" onSubmit={onSearch}>
        <input
          aria-label="Search tickets"
          placeholder="Search title or description"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit">Search</button>
        <select aria-label="Filter by status" value={status} onChange={(e) => update({ status: e.target.value })}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {label(s)}
            </option>
          ))}
        </select>
      </form>

      <ErrorBanner message={error} />
      {loading && <p className="muted">Loading…</p>}

      {data && !loading && data.content.length === 0 && <p className="empty">No tickets match your search.</p>}

      {data && data.content.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Key</th>
              <th>Title</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Category</th>
              <th>Assignee</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((t) => (
              <tr key={t.key}>
                <td>
                  <Link to={`/tickets/${t.key}`}>{t.key}</Link>
                </td>
                <td>
                  <Link to={`/tickets/${t.key}`}>{t.title}</Link>
                </td>
                <td>
                  <StatusBadge status={t.status} />
                </td>
                <td>{label(t.priority)}</td>
                <td>{label(t.category)}</td>
                <td>{t.assignee ?? <span className="muted">Unassigned</span>}</td>
                <td>{formatDate(t.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {data && data.totalPages > 1 && (
        <div className="pager">
          <button disabled={page <= 0} onClick={() => update({ page: String(page - 1) })}>
            Previous
          </button>
          <span>
            Page {page + 1} of {data.totalPages}
          </span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>
            Next
          </button>
        </div>
      )}
    </section>
  );
}
