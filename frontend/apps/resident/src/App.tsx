import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from '@gemek/ui';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { MyTicketsPage } from './pages/MyTicketsPage';
import { TicketDetailPage } from './pages/TicketDetailPage';
// TEMP_HIDDEN_DEFERRED: amenities import — feature deferred, see PROGRESS.md
// import { AmenitiesPage } from './pages/AmenitiesPage';
// TEMP_HIDDEN_DEFERRED: bookings import — feature deferred, see PROGRESS.md
// import { MyBookingsPage } from './pages/MyBookingsPage';
// TEMP_HIDDEN_DEFERRED: parking import — feature deferred, see PROGRESS.md
// import { ParkingPage } from './pages/ParkingPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { ProfilePage } from './pages/ProfilePage';
import { MyVehiclesPage } from './pages/MyVehiclesPage';

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

export default function App() {
  const bootstrap = useAuthStore((s) => s.bootstrap);
  useEffect(() => { bootstrap(); }, [bootstrap]);

  return (
    <>
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<HomePage />} />
          <Route path="tickets" element={<MyTicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          {/* TEMP_HIDDEN_DEFERRED: amenities route — feature deferred, see PROGRESS.md */}
          <Route path="amenities" element={<Navigate to="/" replace />} />
          {/* TEMP_HIDDEN_DEFERRED: bookings route — feature deferred, see PROGRESS.md */}
          <Route path="bookings" element={<Navigate to="/" replace />} />
          {/* TEMP_HIDDEN_DEFERRED: parking route — feature deferred, see PROGRESS.md */}
          <Route path="parking" element={<Navigate to="/" replace />} />
          <Route path="vehicles" element={<MyVehiclesPage />} />
          <Route path="announcements" element={<AnnouncementsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
    <Toaster />
    </>
  );
}
