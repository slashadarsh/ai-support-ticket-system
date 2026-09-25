import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  api,
  ApiError,
  CATEGORIES,
  label,
  PRIORITIES,
  type Category,
  type Priority,
  type Ticket,
  type TicketStatus,
  type UpdateTicketBody,
} from '../api';
import { ErrorBanner, FieldError, formatDate, StatusBadge } from '../components/Common';

interface EditState {
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  assignee: string;
  resolutionNotes: string;
}

const toEdit = (t: Ticket): EditState => ({
  title: t.title,
  description: t.description,
  priority: t.priority,
  category: t.category,
  assignee: t.assignee ?? '',
  resolutionNotes: t.resolutionNotes ?? '',
});

export default function TicketDetailPage() {
  const { key = '' } = useParams();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [edit, setEdit] = useState<EditState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [author, setAuthor] = useState('');
  const [commentBody, setCommentBody] = useState('');
  const [commentErrors, setCommentErrors] = useState<Record<string, string>>({});

  const show = (t: Ticket) => {
    setTicket(t);
    setEdit(toEdit(t));
  };

  const fail = (err: unknown, setFields?: (f: Record<string, string>) => void) => {
    if (err instanceof ApiError) {
      if (setFields && Object.keys(err.fieldErrors).length > 0) {
        setFields(err.fieldErrors);
        setError('Please fix the highlighted fields.');
      } else {
        setError(err.userMessage);
      }
    } else {
      setError(String(err));
    }
  };

  useEffect(() => {
    api
      .getTicket(key)
      .then(show)
      .catch((err) => (err instanceof ApiError && err.status === 404 ? setNotFound(true) : fail(err)));
  }, [key]);

  if (notFound) {
    return (
      <section>
        <h1>Ticket {key} was not found</h1>
        <Link to="/tickets">Back to tickets</Link>
      </section>
    );
  }
  if (!ticket || !edit) {
    return (
      <section>
        <ErrorBanner message={error} />
        <p className="muted">Loading…</p>
      </section>
    );
  }

  const terminal = ticket.status === 'CLOSED' || ticket.status === 'CANCELLED';

  const clearMessages = () => {
    setError(null);
    setNotice(null);
    setFieldErrors({});
    setCommentErrors({});
  };

  const onSave = async (e: FormEvent) => {
    e.preventDefault();
    clearMessages();
    const original = toEdit(ticket);
    const changes: UpdateTicketBody = {};
    (Object.keys(edit) as (keyof EditState)[]).forEach((field) => {
      if (edit[field] !== original[field]) Object.assign(changes, { [field]: edit[field] });
    });
    if (Object.keys(changes).length === 0) {
      setNotice('Nothing to save.');
      return;
    }
    try {
      show(await api.updateTicket(ticket.key, changes));
      setNotice('Saved.');
    } catch (err) {
      fail(err, setFieldErrors);
    }
  };

  const onTransition = async (target: TicketStatus) => {
    clearMessages();
    try {
      show(await api.transition(ticket.key, target));
      setNotice(`Status changed to ${label(target)}.`);
    } catch (err) {
      fail(err);
    }
  };

  const onComment = async (e: FormEvent) => {
    e.preventDefault();
    clearMessages();
    try {
      await api.addComment(ticket.key, { author, body: commentBody });
      setCommentBody('');
      show(await api.getTicket(ticket.key));
    } catch (err) {
      fail(err, setCommentErrors);
    }
  };

  const set = (field: keyof EditState) => (e: { target: { value: string } }) =>
    setEdit({ ...edit, [field]: e.target.value });

  const similar = `/ask?q=${encodeURIComponent(`Show me similar resolved tickets about: ${ticket.title}`)}`;

  return (
    <section>
      <div className="page-header">
        <h1>
          {ticket.key} · {ticket.title}
        </h1>
        <StatusBadge status={ticket.status} />
      </div>
      <p className="muted">
        {label(ticket.priority)} priority · {label(ticket.category)} · created {formatDate(ticket.createdAt)} · updated{' '}
        {formatDate(ticket.updatedAt)}
      </p>

      <ErrorBanner message={error} />
      {notice && <div className="banner banner-info">{notice}</div>}

      <div className="actions">
        {ticket.allowedTransitions.map((s) => (
          <button key={s} onClick={() => onTransition(s)}>
            Move to {label(s)}
          </button>
        ))}
        <Link className="button" to={similar}>
          Find similar resolved tickets
        </Link>
      </div>

      <form className="form" onSubmit={onSave} noValidate>
        {terminal && <p className="muted">Closed and cancelled tickets can't be edited.</p>}
        <fieldset disabled={terminal}>
          <label>
            Title
            <input value={edit.title} onChange={set('title')} />
            <FieldError message={fieldErrors.title} />
          </label>
          <label>
            Description
            <textarea rows={5} value={edit.description} onChange={set('description')} />
            <FieldError message={fieldErrors.description} />
          </label>
          <div className="row">
            <label>
              Priority
              <select value={edit.priority} onChange={set('priority')}>
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {label(p)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Category
              <select value={edit.category} onChange={set('category')}>
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {label(c)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Assignee
              <input value={edit.assignee} placeholder="Unassigned" onChange={set('assignee')} />
              <FieldError message={fieldErrors.assignee} />
            </label>
          </div>
          <label>
            Resolution notes (required before resolving)
            <textarea rows={3} value={edit.resolutionNotes} onChange={set('resolutionNotes')} />
            <FieldError message={fieldErrors.resolutionNotes} />
          </label>
          <button className="primary" type="submit">
            Save changes
          </button>
        </fieldset>
      </form>

      <h2>Comments</h2>
      {ticket.comments.length === 0 && <p className="muted">No comments yet.</p>}
      <ul className="comments">
        {ticket.comments.map((c) => (
          <li key={c.id}>
            <strong>{c.author}</strong> <span className="muted">{formatDate(c.createdAt)}</span>
            <p>{c.body}</p>
          </li>
        ))}
      </ul>
      <form className="form" onSubmit={onComment} noValidate>
        <label>
          Your name
          <input value={author} onChange={(e) => setAuthor(e.target.value)} />
          <FieldError message={commentErrors.author} />
        </label>
        <label>
          Comment
          <textarea rows={3} value={commentBody} onChange={(e) => setCommentBody(e.target.value)} />
          <FieldError message={commentErrors.body} />
        </label>
        <button type="submit">Add comment</button>
      </form>
    </section>
  );
}
