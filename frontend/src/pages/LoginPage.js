import React, { useState, useEffect } from 'react'; // useEffect import edildi
import { useNavigate, Link } from 'react-router-dom';
import './AuthForm.css';

function LoginPage({ onLogin }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [infoMessage, setInfoMessage] = useState(null); // YENİ STATE
  const navigate = useNavigate();

  // YENİ useEffect: Sayfa yüklendiğinde flash mesajı kontrol et
  useEffect(() => {
    const flashMessage = sessionStorage.getItem('flashMessage');
    if (flashMessage) {
      setInfoMessage(flashMessage);
      // Mesajı gösterdikten sonra temizle ki tekrar görünmesin
      sessionStorage.removeItem('flashMessage');
    }
  }, []); // Boş dizi, bu etkinin sadece ilk render'da çalışmasını sağlar

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setInfoMessage(null); // Giriş denemesi yapıldığında bilgi mesajını temizle
    sessionStorage.removeItem('user');
    sessionStorage.removeItem('token');

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        // Gelen cevabı metin olarak almayı dene
        const errorData = await response.text();
        try {
          // Eğer metin JSON formatındaysa parse et
          const errorJson = JSON.parse(errorData);
          throw new Error(errorJson.message || 'Giriş yapılamadı. Lütfen bilgilerinizi kontrol edin.');
        } catch (jsonError) {
          // Eğer JSON değilse, gelen metni doğrudan hata olarak kullan
          throw new Error(errorData || 'Giriş yapılamadı. Sunucudan beklenmedik bir yanıt alındı.');
        }
      }

      const data = await response.json(); 

      // Token'ı sessionStorage'a kaydet (localStorage yerine)
      // Bu, tarayıcı kapandığında oturumun da kapanmasını sağlar
      sessionStorage.setItem('token', data.token);
      
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
        
        {/* Hata ve bilgi mesajları için ayrı kutular */}
        {error && <div className="auth-error-message">{error}</div>}
        {infoMessage && <div className="auth-info-message">{infoMessage}</div>}

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