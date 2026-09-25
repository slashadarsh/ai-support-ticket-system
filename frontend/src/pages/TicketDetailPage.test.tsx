import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import TicketDetailPage from './TicketDetailPage';
import { mockFetch, problem, renderAt } from '../test/helpers';

const ticket = {
  key: 'TKT-1001',
  title: 'Card payments failing',
  description: 'Visa declined',
  priority: 'HIGH',
  category: 'PAYMENT',
  status: 'IN_PROGRESS',
  assignee: null,
  resolutionNotes: null,
  createdAt: '2026-09-25T09:00:00Z',
  updatedAt: '2026-09-25T09:00:00Z',
  allowedTransitions: ['RESOLVED', 'CANCELLED'],
  comments: [],
};

describe('TicketDetailPage', () => {
  it('shows the server message when a transition is rejected (AC-10, AC-13)', async () => {
    mockFetch(
      { status: 200, body: ticket },
      problem(409, 'Add resolution notes to TKT-1001 before moving it to RESOLVED'),
    );
    renderAt('/tickets/TKT-1001', '/tickets/:key', <TicketDetailPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Move to Resolved' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Add resolution notes to TKT-1001 before moving it to RESOLVED',
    );
  });

  it('only offers the transitions the server allows', async () => {
    mockFetch({ status: 200, body: ticket });
    renderAt('/tickets/TKT-1001', '/tickets/:key', <TicketDetailPage />);

    expect(await screen.findByRole('button', { name: 'Move to Resolved' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Move to Cancelled' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Move to Open' })).not.toBeInTheDocument();
  });

  it('shows a not-found message for an unknown key', async () => {
    mockFetch(problem(404, 'Ticket TKT-9999 was not found'));
    renderAt('/tickets/TKT-9999', '/tickets/:key', <TicketDetailPage />);

    expect(await screen.findByRole('heading', { name: 'Ticket TKT-9999 was not found' })).toBeInTheDocument();
  });
});
