import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ApartmentsPage } from './pages/ApartmentsPage';
import { ResidentsPage } from './pages/ResidentsPage';
import { TicketsPage } from './pages/TicketsPage';
import { TicketDetailPage } from './pages/TicketDetailPage';
import { ContractorsPage } from './pages/ContractorsPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { AmenitiesPage } from './pages/AmenitiesPage';
import { ParkingPage } from './pages/ParkingPage';
import { ReportsPage } from './pages/ReportsPage';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const authStatus = useAuthStore((s) => s.authStatus);
  // Hold render while bootstrap resolves — prevents synchronous redirect before refresh completes.
  if (authStatus === 'loading') return (
    <div className="min-h-screen flex items-center justify-center">
      <svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
    </div>
  );
  if (authStatus === 'unauthenticated') return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequireRole({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (!user || !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

export default function App() {
  const bootstrap = useAuthStore((s) => s.bootstrap);
  useEffect(() => { bootstrap(); }, [bootstrap]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="apartments" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ApartmentsPage /></RequireRole>} />
          <Route path="residents" element={<RequireRole roles={['ADMIN']}><ResidentsPage /></RequireRole>} />
          <Route path="tickets" element={<TicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          <Route path="contractors" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ContractorsPage /></RequireRole>} />
          <Route path="announcements" element={<RequireRole roles={['ADMIN']}><AnnouncementsPage /></RequireRole>} />
          <Route path="amenities" element={<RequireRole roles={['ADMIN']}><AmenitiesPage /></RequireRole>} />
          <Route path="parking" element={<RequireRole roles={['ADMIN']}><ParkingPage /></RequireRole>} />
          <Route path="reports" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ReportsPage /></RequireRole>} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
