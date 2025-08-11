import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './ProfilePage.css';

// DEĞİŞİKLİK: .png uzantıları kaldırıldı
const AVATAR_IDS = [
  'cat', 'chicken', 'dog',
  'duck', 'gorilla', 'hippopotamus',
  'panda', 'rabbit', 'shark',
];

function ProfilePage({ user, onUserUpdate }) {
  const [profile, setProfile] = useState(null);
  // DEĞİŞİKLİK: Varsayılan değer de uzantısız
  const [selectedAvatar, setSelectedAvatar] = useState(user?.avatarId || 'default-avatar');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchProfile = async () => {
      const token = sessionStorage.getItem('token');
      if (!token) {
        navigate('/login');
        return;
      }
      try {
        const response = await fetch('/api/profile', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Profil bilgileri alınamadı.');
        const data = await response.json();
        setProfile(data);
        // DEĞİŞİKLİK: Varsayılan değer de uzantısız
        setSelectedAvatar(data.avatarId || 'default-avatar');
      } catch (error) {
        console.error("Profil alınırken hata:", error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchProfile();
  }, [navigate]);

  const handleAvatarSelect = (avatarId) => {
    setSelectedAvatar(avatarId);
  };

  const handleSaveChanges = async () => {
    if (isSaving || selectedAvatar === profile.avatarId) return;

    setIsSaving(true);
    const token = sessionStorage.getItem('token');
    try {
      const response = await fetch('/api/profile/avatar', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ avatarId: selectedAvatar })
      });

      if (!response.ok) throw new Error('Avatar güncellenemedi.');
      
      onUserUpdate({ avatarId: selectedAvatar });
      navigate('/dashboard');

    } catch (error) {
      console.error("Avatar güncellenirken hata:", error);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <div className="loading-screen">Profil yükleniyor...</div>;
  }

  if (!profile) {
    return <div>Profil bilgileri yüklenemedi.</div>;
  }

  return (
    <div className="profile-page-container">
      <div className="profile-card">
        <h1>Profilim</h1>
        <div className="profile-info">
          <img 
            // DEĞİŞİKLİK: .png burada ekleniyor
            src={`http://localhost:8080/avatars/${profile.avatarId}.png`} 
            alt="Mevcut Avatar" 
            className="profile-avatar-large"
          />
          <div className="profile-details">
            <span className="profile-name">{profile.name}</span>
            <span className="profile-email">{profile.email}</span>
          </div>
        </div>
        
        <div className="avatar-selection">
          <h2>Avatarını Seç</h2>
          <div className="avatar-grid">
            {AVATAR_IDS.map(avatarId => (
              <img
                key={avatarId}
                // DEĞİŞİKLİK: .png burada ekleniyor
                src={`http://localhost:8080/avatars/${avatarId}.png`}
                alt={`Avatar ${avatarId}`}
                className={`avatar-option ${selectedAvatar === avatarId ? 'selected' : ''}`}
                onClick={() => handleAvatarSelect(avatarId)}
              />
            ))}
          </div>
        </div>
        
        <button 
          className="save-changes-btn"
          onClick={handleSaveChanges}
          disabled={isSaving || selectedAvatar === profile.avatarId}
        >
          {isSaving ? 'Kaydediliyor...' : 'Değişiklikleri Kaydet'}
        </button>
      </div>
    </div>
  );
}

export default ProfilePage;