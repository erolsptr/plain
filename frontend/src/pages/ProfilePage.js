import React, { useState, useEffect } from 'react';
// import { useNavigate } from 'react-router-dom'; // Artık kullanılmıyor
import './ProfilePage.css';

const AVATAR_IDS = [
  'cat', 'chicken', 'dog', 'duck', 'gorilla', 'hippopotamus',
  'panda', 'rabbit', 'shark', 'bot', 'default-avatar'
];

function ProfilePage({ user, onUserUpdate, onLogout }) {
  const [profile, setProfile] = useState(user);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('profile');

  const [newName, setNewName] = useState(user?.name || '');
  const [selectedAvatar, setSelectedAvatar] = useState(user?.avatarId || 'default-avatar');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isSaving, setIsSaving] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState({ type: '', text: '' });

  // const navigate = useNavigate(); // Artık kullanılmıyor

  useEffect(() => {
    const fetchProfile = async () => {
      setIsLoading(true);
      const token = sessionStorage.getItem('token');
      try {
        const response = await fetch('/api/profile', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Profil bilgileri alınamadı.');
        const data = await response.json();
        setProfile(data);
        setNewName(data.name);
        setSelectedAvatar(data.avatarId || 'default-avatar');
      } catch (error) {
        console.error("Profil alınırken hata:", error);
      } finally {
        setIsLoading(false);
      }
    };
    fetchProfile();
  }, []);

  const showFeedback = (type, text) => {
    setFeedbackMessage({ type, text });
    setTimeout(() => setFeedbackMessage({ type: '', text: '' }), 3000);
  };

  const handleProfileUpdate = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    const token = sessionStorage.getItem('token');
    
    try {
      const promises = [];
      if (newName !== profile.name) {
        promises.push(fetch('/api/profile/name', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify({ newName })
        }));
      }
      if (selectedAvatar !== profile.avatarId) {
        promises.push(fetch('/api/profile/avatar', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify({ avatarId: selectedAvatar })
        }));
      }

      const responses = await Promise.all(promises);
      for (const response of responses) {
        if (!response.ok) {
          if (response.status === 409) throw new Error("Bu isim zaten kullanılıyor.");
          throw new Error('Profil güncellenemedi.');
        }
      }
      
      onUserUpdate({ name: newName, avatarId: selectedAvatar });
      showFeedback('success', 'Profil başarıyla güncellendi!');
    } catch (error) {
      showFeedback('error', error.message);
    } finally {
      setIsSaving(false);
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      showFeedback('error', 'Yeni şifreler eşleşmiyor.');
      return;
    }
    setIsSaving(true);
    const token = sessionStorage.getItem('token');
    try {
      const response = await fetch('/api/profile/password', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ currentPassword, newPassword })
      });
      if (!response.ok) {
        if (response.status === 400) throw new Error('Mevcut şifre yanlış.');
        throw new Error('Şifre güncellenemedi.');
      }
      showFeedback('success', 'Şifre başarıyla değiştirildi!');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      showFeedback('error', error.message);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <div className="loading-screen">Profil yükleniyor...</div>;
  }

  return (
    <div className="profile-page-container">
      <div className="profile-tabs">
        <button onClick={() => setActiveTab('profile')} className={activeTab === 'profile' ? 'active' : ''}>Profil</button>
        <button onClick={() => setActiveTab('security')} className={activeTab === 'security' ? 'active' : ''}>Güvenlik</button>
      </div>

      <div className="profile-content">
        {activeTab === 'profile' && (
          <form onSubmit={handleProfileUpdate}>
            <h2>Genel Bilgiler</h2>
            <div className="form-group">
              <label>Görünen İsim</label>
              <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} />
            </div>
            <div className="form-group">
              <label>E-posta Adresi</label>
              <input type="email" value={profile.email} disabled />
            </div>
            <div className="form-group">
              <label>Avatarını Seç</label>
              <div className="avatar-grid">
                {AVATAR_IDS.map(avatarId => (
                  <img
                    key={avatarId}
                    src={`http://localhost:8080/avatars/${avatarId}.png`}
                    alt={`Avatar ${avatarId}`}
                    className={`avatar-option ${selectedAvatar === avatarId ? 'selected' : ''}`}
                    onClick={() => setSelectedAvatar(avatarId)}
                  />
                ))}
              </div>
            </div>
            <button type="submit" className="save-changes-btn" disabled={isSaving}>
              {isSaving ? 'Kaydediliyor...' : 'Değişiklikleri Kaydet'}
            </button>
          </form>
        )}

        {activeTab === 'security' && (
          <form onSubmit={handlePasswordChange}>
            <h2>Şifre Değiştir</h2>
            <div className="form-group">
              <label>Mevcut Şifre</label>
              <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
            </div>
            <div className="form-group">
              <label>Yeni Şifre</label>
              <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            </div>
            <div className="form-group">
              <label>Yeni Şifre (Tekrar)</label>
              <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
            </div>
            <button type="submit" className="save-changes-btn" disabled={isSaving}>
              {isSaving ? 'Kaydediliyor...' : 'Şifreyi Değiştir'}
            </button>
          </form>
        )}

        {feedbackMessage.text && (
          <div className={`feedback-message ${feedbackMessage.type}`}>
            {feedbackMessage.text}
          </div>
        )}
      </div>
    </div>
  );
}

export default ProfilePage;