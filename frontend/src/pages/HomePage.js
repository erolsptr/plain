import React from 'react';
import { Link } from 'react-router-dom';
import './HomePage.css'; 

function HomePage() {
  return (
    <div className="home-container">
      <div className="hero-section">
        <h1>plAIn Poker'e Hoş Geldiniz!</h1>
        <p className="subtitle">Yapay Zeka Destekli, Akıllı ve Esnek Planlama Pokeri Deneyimi</p>
        <p className="description">
          Görevlerinizi daha isabetli tahminleyin. Ekip üyeleriyle birlikte anlık oylamalar yapın, 
          özelleştirilebilir kart setleriyle projenize en uygun değerleri kullanın ve bırakın yapay zeka
          size ikinci bir görüş sunsun.
        </p>
        <div className="cta-buttons">
          <Link to="/register" className="btn btn-primary">Hemen Başla (Kayıt Ol)</Link>
          <Link to="/login" className="btn btn-secondary">Zaten Hesabım Var (Giriş Yap)</Link>
        </div>
      </div>
    </div>
  );
}

export default HomePage;