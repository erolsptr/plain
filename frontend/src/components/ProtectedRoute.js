import React from 'react';
import { Navigate } from 'react-router-dom';

function ProtectedRoute({ user, children }) {
  if (!user) {
    // Kullanıcı giriş yapmamışsa, onu giriş sayfasına yönlendir.
    return <Navigate to="/login" replace />;
  }
  return children; // Kullanıcı giriş yapmışsa, gitmek istediği sayfayı göster.
}

export default ProtectedRoute;