import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, ApiError, label, type AskResponse } from '../api';
import { ErrorBanner, FieldError } from '../components/Common';

export default function AskPage() {
  const [params] = useSearchParams();
  const [question, setQuestion] = useState(params.get('q') ?? '');
  const [result, setResult] = useState<AskResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [asking, setAsking] = useState(false);

  const onAsk = async (e: FormEvent) => {
    e.preventDefault();
    setAsking(true);
    setError(null);
    setFieldError(undefined);
    setResult(null);
    try {
      setResult(await api.ask(question));
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.question) {
        setFieldError(err.fieldErrors.question);
      } else {
        setError(err instanceof ApiError ? err.userMessage : String(err));
      }
    } finally {
      setAsking(false);
    }
  };

  return (
    <section>
      <h1>Ask the ticket history</h1>
      <p className="muted">Answers come only from existing support tickets, with the tickets used listed as sources.</p>
      <form className="form" onSubmit={onAsk} noValidate>
        <label>
          Question
          <textarea
            rows={3}
            value={question}
            placeholder="e.g. Have we seen payment failures before?"
            onChange={(e) => setQuestion(e.target.value)}
          />
          <FieldError message={fieldError} />
        </label>
        <button className="primary" type="submit" disabled={asking}>
          {asking ? 'Asking…' : 'Ask'}
        </button>
      </form>

      <ErrorBanner message={error} />

      {result && result.grounded && (
        <div className="answer">
          <p>{result.answer}</p>
          <h3>Sources</h3>
          <ul>
            {result.citations.map((c) => (
              <li key={c.ticketId}>
                <Link to={`/tickets/${c.ticketId}`}>{c.ticketId}</Link> · {c.title} · {label(c.status)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {result && !result.grounded && (
        <div className="no-match" data-testid="no-match">
          <p>{result.answer}</p>
          {result.noMatchReason && <p className="muted">Reason: {label(result.noMatchReason)}</p>}
        </div>
      )}

      {result && (
        <details className="retrieval">
          <summary>Retrieval details</summary>
          <p>
            top-K {result.retrieval.topK} · threshold {result.retrieval.similarityThreshold}
            {result.retrieval.filter && <> · filter: {result.retrieval.filter}</>}
          </p>
          <ul>
            {result.retrieval.matches.map((m, i) => (
              <li key={i}>
                {m.ticketId} ({m.chunkType}) — similarity {m.score}
              </li>
            ))}
            {result.retrieval.matches.length === 0 && <li>No chunk passed the threshold.</li>}
          </ul>
        </details>
      )}
    </section>
  );
}
