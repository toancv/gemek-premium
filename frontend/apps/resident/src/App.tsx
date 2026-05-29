import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { MyTicketsPage } from './pages/MyTicketsPage';
import { TicketDetailPage } from './pages/TicketDetailPage';
import { AmenitiesPage } from './pages/AmenitiesPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { ParkingPage } from './pages/ParkingPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { ProfilePage } from './pages/ProfilePage';

function RequireAuth({ children }: { children: React.ReactNode }) {
  // SECURITY-FIX: Select scalar !!accessToken directly for a reactive Zustand subscription.
  // Selecting the isAuthenticated function reference does not subscribe to accessToken changes,
  // so the component would not re-render on logout.
  const isAuthenticated = useAuthStore((s) => !!s.accessToken);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<HomePage />} />
          <Route path="tickets" element={<MyTicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          <Route path="amenities" element={<AmenitiesPage />} />
          <Route path="bookings" element={<MyBookingsPage />} />
          <Route path="parking" element={<ParkingPage />} />
          <Route path="announcements" element={<AnnouncementsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
