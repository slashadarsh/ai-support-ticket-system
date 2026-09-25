import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import AskPage from './AskPage';
import { mockFetch, problem, renderAt } from '../test/helpers';

const retrieval = { topK: 5, similarityThreshold: 0.45, filter: null, matches: [] };

describe('AskPage', () => {
  it('shows a grounded answer with linked sources (FR-23, AC-17)', async () => {
    mockFetch({
      status: 200,
      body: {
        question: 'q',
        grounded: true,
        answer: 'The gateway API key had expired [TKT-1001].',
        citations: [{ ticketId: 'TKT-1001', title: 'Card payments failing', status: 'CLOSED' }],
        noMatchReason: null,
        retrieval: { ...retrieval, matches: [{ ticketId: 'TKT-1001', chunkType: 'SUMMARY', score: 0.61 }] },
      },
    });
    renderAt('/ask?q=Why%20did%20payments%20fail%3F', '/ask', <AskPage />);

    expect(screen.getByLabelText('Question')).toHaveValue('Why did payments fail?');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText('The gateway API key had expired [TKT-1001].')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'TKT-1001' })).toHaveAttribute('href', '/tickets/TKT-1001');
  });

  it('shows the no-match state distinctly, not as an error (AC-18)', async () => {
    mockFetch({
      status: 200,
      body: {
        question: 'q',
        grounded: false,
        answer: 'No relevant tickets found.',
        citations: [],
        noMatchReason: 'NO_RELEVANT_TICKETS',
        retrieval,
      },
    });
    renderAt('/ask', '/ask', <AskPage />);

    await userEvent.type(screen.getByLabelText('Question'), 'What is the capital of France?');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByTestId('no-match')).toHaveTextContent('No relevant tickets found.');
    expect(screen.getByText('Reason: No relevant tickets')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows an assistant-unavailable message on 503 (NFR-11)', async () => {
    mockFetch(problem(503, 'The AI assistant is unavailable right now.'));
    renderAt('/ask', '/ask', <AskPage />);

    await userEvent.type(screen.getByLabelText('Question'), 'Any payment issues?');
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The AI assistant is unavailable right now.');
  });
});
