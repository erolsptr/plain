import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import './AuthForm.css'; // Register sayfasıyla aynı stil dosyasını kullanıyoruz.

function LoginPage({ onLogin }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    try {
      // Backend'e giriş isteği gönder
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || 'Giriş yapılamadı. Lütfen bilgilerinizi kontrol edin.');
      }

      const data = await response.json(); 

      // Token'ı tarayıcının yerel depolamasına kaydet
      if (data.token) {
        localStorage.setItem('token', data.token);
      }

      onLogin(data.user, data.token);

      navigate('/dashboard');

    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="auth-container">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Giriş Yap</h2>
        
        {error && <div className="auth-error-message">{error}</div>}

        <div className="form-group">
          <label htmlFor="email">E-posta</label>
          <input
            type="email"
            id="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="form-group">
          <label htmlFor="password">Şifre</label>
          <input
            type="password"
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button type="submit" className="btn btn-primary auth-button">Giriş Yap</button>
        <div className="auth-switch-link">
          Hesabın yok mu? <Link to="/register">Kayıt Ol</Link>
        </div>
      </form>
    </div>
  );
}

export default LoginPage;