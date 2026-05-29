import React from 'react';
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
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequireRole({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (!user || !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

export default function App() {
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
