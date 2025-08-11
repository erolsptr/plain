import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import './Navbar.css';

import logo from '../assets/logo.png'; 

function Navbar({ user, onLogout }) {
  const navigate = useNavigate();

  const handleLogoutClick = () => {
    onLogout();
    navigate('/');
  };

  return (
    <nav className="navbar">
      <div className="navbar-container">
        <Link to={user ? "/dashboard" : "/"} className="navbar-logo">
          <img src={logo} alt="plAIn Logo" className="navbar-logo-img" />
        </Link>
        <ul className="navbar-menu">
          {user ? (
            <>
              <li className="navbar-item">
                {/* DEĞİŞİKLİK: Span'ı Link'e çevirdik */}
                <Link to="/profile" className="navbar-user-greeting">
                  Hoşgeldin, {user.name}!
                </Link>
              </li>
              <li className="navbar-item">
                <Link to="/dashboard" className="navbar-links">
                  Kontrol Paneli
                </Link>
              </li>
              <li className="navbar-item">
                <button onClick={handleLogoutClick} className="navbar-button">
                  Çıkış Yap
                </button>
              </li>
            </>
          ) : (
            <>
              <li className="navbar-item">
                <Link to="/login" className="navbar-links">
                  Giriş Yap
                </Link>
              </li>
              <li className="navbar-item">
                <Link to="/register" className="navbar-links btn-register">
                  Kayıt Ol
                </Link>
              </li>
            </>
          )}
        </ul>
      </div>
    </nav>
  );
}

export default Navbar;