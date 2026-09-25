import { label, type TicketStatus } from '../api';

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="banner banner-error" role="alert">
      {message}
    </div>
  );
}

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{label(status)}</span>;
}

export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <div className="field-error">{message}</div>;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}
