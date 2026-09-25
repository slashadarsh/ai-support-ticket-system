import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import AskPage from './pages/AskPage';
import CreateTicketPage from './pages/CreateTicketPage';
import TicketDetailPage from './pages/TicketDetailPage';
import TicketListPage from './pages/TicketListPage';

export default function App() {
  return (
    <>
      <nav className="nav">
        <span className="brand">Support Tickets</span>
        <NavLink to="/tickets" end>
          Tickets
        </NavLink>
        <NavLink to="/ask">Ask</NavLink>
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<Navigate to="/tickets" replace />} />
          <Route path="/tickets" element={<TicketListPage />} />
          <Route path="/tickets/new" element={<CreateTicketPage />} />
          <Route path="/tickets/:key" element={<TicketDetailPage />} />
          <Route path="/ask" element={<AskPage />} />
        </Routes>
      </main>
    </>
  );
}
