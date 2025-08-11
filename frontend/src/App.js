import React, { useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import './App.css';

import HomePage from './pages/HomePage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import Room from './pages/Room';
import ProfilePage from './pages/ProfilePage'; // YENİ SAYFA IMPORTU
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import PublicOnlyRoute from './components/PublicOnlyRoute';

function App() {
  const [currentUser, setCurrentUser] = useState(() => {
    const savedUser = sessionStorage.getItem('user');
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const handleLogin = (user, token) => {
    sessionStorage.setItem('user', JSON.stringify(user));
    sessionStorage.setItem('token', token);
    setCurrentUser(user);
  };

  // YENİ FONKSİYON: Kullanıcı bilgilerini (örn: avatar) günceller
  const handleUserUpdate = (updatedUserData) => {
    const updatedUser = { ...currentUser, ...updatedUserData };
    sessionStorage.setItem('user', JSON.stringify(updatedUser));
    setCurrentUser(updatedUser);
  };

  const handleLogout = () => {
    sessionStorage.removeItem('user');
    sessionStorage.removeItem('token');
    setCurrentUser(null);
  };

  return (
    <div className="App">
      <Navbar user={currentUser} onLogout={handleLogout} />
      
      <main className="app-content">
        <Routes>
          {/* Sadece halka açık rotalar */}
          <Route path="/" element={<HomePage />} />
          <Route 
            path="/register" 
            element={
              <PublicOnlyRoute user={currentUser}>
                <RegisterPage />
              </PublicOnlyRoute>
            } 
          />
          <Route 
            path="/login" 
            element={
              <PublicOnlyRoute user={currentUser}>
                <LoginPage onLogin={handleLogin} />
              </PublicOnlyRoute>
            } 
          />
          
          {/* Korumalı rotalar (giriş yapmayı gerektiren) */}
          <Route 
            path="/dashboard" 
            element={
              <ProtectedRoute user={currentUser}>
                <DashboardPage user={currentUser} />
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/room/:roomId" 
            element={
              <ProtectedRoute user={currentUser}>
                <Room user={currentUser} />
              </ProtectedRoute>
            } 
          />
          {/* YENİ ROTA: Profil sayfası */}
          <Route 
            path="/profile" 
            element={
              <ProtectedRoute user={currentUser}>
                <ProfilePage user={currentUser} onUserUpdate={handleUserUpdate} />
              </ProtectedRoute>
            } 
          />
        </Routes>
      </main>
    </div>
  );
}

export default App;