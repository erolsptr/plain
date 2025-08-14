import React from 'react';
import { Navigate } from 'react-router-dom';

function PublicOnlyRoute({ user, children }) {
  if (user) {
    return <Navigate to="/dashboard" replace />;
  }
  return children; 
}

export default PublicOnlyRoute;