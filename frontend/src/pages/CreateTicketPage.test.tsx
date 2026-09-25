import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import CreateTicketPage from './CreateTicketPage';
import { mockFetch, problem, renderAt } from '../test/helpers';

describe('CreateTicketPage', () => {
  it('shows server field errors next to their fields (AC-12, AC-13)', async () => {
    mockFetch(
      problem(400, 'One or more fields are invalid.', [
        { field: 'title', message: 'must not be blank' },
        { field: 'description', message: 'size must be between 0 and 5000' },
      ]),
    );
    renderAt('/tickets/new', '/tickets/new', <CreateTicketPage />);

    await userEvent.click(screen.getByRole('button', { name: 'Create ticket' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please fix the highlighted fields.');
    expect(screen.getByText('must not be blank')).toBeInTheDocument();
    expect(screen.getByText('size must be between 0 and 5000')).toBeInTheDocument();
  });

  it('navigates to the new ticket after a successful create (AC-1)', async () => {
    const fetchMock = mockFetch({ status: 201, body: { key: 'TKT-1001' } });
    renderAt('/tickets/new', '/tickets/new', <CreateTicketPage />);

    await userEvent.type(screen.getByLabelText('Title'), 'Card payments failing');
    await userEvent.type(screen.getByLabelText('Description'), 'Visa declined');
    await userEvent.click(screen.getByRole('button', { name: 'Create ticket' }));

    expect(await screen.findByTestId('navigated')).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toMatchObject({ title: 'Card payments failing', priority: 'MEDIUM' });
  });
});
