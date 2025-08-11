import React from 'react';
import { Navigate } from 'react-router-dom';

function PublicOnlyRoute({ user, children }) {
  if (user) {
    // Kullanıcı zaten giriş yapmışsa, onu kontrol paneline yönlendir.
    return <Navigate to="/dashboard" replace />;
  }
  return children; // Kullanıcı giriş yapmamışsa, gitmek istediği (login/register) sayfayı göster.
}

export default PublicOnlyRoute;