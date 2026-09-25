import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, CATEGORIES, label, PRIORITIES, type Category, type Priority } from '../api';
import { ErrorBanner, FieldError } from '../components/Common';

export default function CreateTicketPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [category, setCategory] = useState<Category>('OTHER');
  const [assignee, setAssignee] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      const created = await api.createTicket({ title, description, priority, category, assignee });
      navigate(`/tickets/${created.key}`);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors);
        setError('Please fix the highlighted fields.');
      } else {
        setError(err instanceof ApiError ? err.userMessage : String(err));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section>
      <h1>New ticket</h1>
      <ErrorBanner message={error} />
      {/* noValidate: the server is the source of truth for validation (FR-11). */}
      <form className="form" onSubmit={onSubmit} noValidate>
        <label>
          Title
          <input value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
          <FieldError message={fieldErrors.title} />
        </label>
        <label>
          Description
          <textarea rows={6} value={description} onChange={(e) => setDescription(e.target.value)} />
          <FieldError message={fieldErrors.description} />
        </label>
        <div className="row">
          <label>
            Priority
            <select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {label(p)}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.priority} />
          </label>
          <label>
            Category
            <select value={category} onChange={(e) => setCategory(e.target.value as Category)}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {label(c)}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.category} />
          </label>
          <label>
            Assignee (optional)
            <input value={assignee} onChange={(e) => setAssignee(e.target.value)} />
            <FieldError message={fieldErrors.assignee} />
          </label>
        </div>
        <button className="primary" type="submit" disabled={saving}>
          {saving ? 'Creating…' : 'Create ticket'}
        </button>
      </form>
    </section>
  );
}
