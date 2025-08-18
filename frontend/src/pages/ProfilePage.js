import React, { useState, useEffect, useCallback } from 'react';
import './ProfilePage.css';

const AVATAR_IDS = [
  'cat', 'chicken', 'dog', 'duck', 'gorilla', 'hippopotamus',
  'panda', 'rabbit', 'shark','beaver','hen','lion','snake', 'default-avatar'
];

// YENİ: Jira formunu ayrı bir bileşen olarak oluşturalım
function JiraIntegrationForm({ showFeedback }) {
  const [jiraDetails, setJiraDetails] = useState({
    jiraUrl: '',
    jiraEmail: '',
    jiraApiToken: '',
    jiraProjectKey: '',
    jiraPointHourRatio: ''
  });
  const [hasToken, setHasToken] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchJiraDetails = async () => {
      setIsLoading(true);
      const token = sessionStorage.getItem('token');
      try {
        const response = await fetch('/api/profile/jira', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Jira bilgileri alınamadı.');
        const data = await response.json();
        setJiraDetails({
          jiraUrl: data.jiraUrl || '',
          jiraEmail: data.jiraEmail || '',
          jiraApiToken: '', 
          jiraProjectKey: data.jiraProjectKey || '',
          jiraPointHourRatio: data.jiraPointHourRatio || ''
        });
        setHasToken(data.hasApiToken);
      } catch (error) {
        console.error("Jira bilgileri alınırken hata:", error);
        showFeedback('error', 'Jira bilgileri sunucudan alınamadı.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchJiraDetails();
  }, [showFeedback]);

  const handleChange = (e) => {
    setJiraDetails({ ...jiraDetails, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    const token = sessionStorage.getItem('token');
    
    try {
      const response = await fetch('/api/profile/jira', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(jiraDetails)
      });
      if (!response.ok) throw new Error('Jira bilgileri güncellenemedi.');
      
      showFeedback('success', 'Jira entegrasyon bilgileri başarıyla kaydedildi!');
      if (jiraDetails.jiraApiToken) {
          setHasToken(true);
      }
      setJiraDetails(prev => ({ ...prev, jiraApiToken: '' }));

    } catch (error) {
      showFeedback('error', error.message);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
      return <p>Jira bilgileri yükleniyor...</p>
  }

  return (
    <form onSubmit={handleSubmit}>
      <h2>Jira Entegrasyonu</h2>
      <p className="form-description">Oylaması tamamlanan görevleri doğrudan Jira projenize göndermek için bu alanları doldurun.</p>
      
      <div className="form-group">
        <label htmlFor="jiraUrl">Jira URL</label>
        <input type="text" id="jiraUrl" name="jiraUrl" placeholder="https://sirketiniz.atlassian.net" value={jiraDetails.jiraUrl} onChange={handleChange} required />
      </div>
      <div className="form-group">
        <label htmlFor="jiraEmail">Jira E-posta Adresi</label>
        <input type="email" id="jiraEmail" name="jiraEmail" placeholder="jira-kullanici@sirket.com" value={jiraDetails.jiraEmail} onChange={handleChange} required />
      </div>
      <div className="form-group">
        <label htmlFor="jiraProjectKey">Jira Proje Anahtarı</label>
        <input type="text" id="jiraProjectKey" name="jiraProjectKey" placeholder="PROJ, DEV, KAN" value={jiraDetails.jiraProjectKey} onChange={handleChange} required />
      </div>
      <div className="form-group">
        <label htmlFor="jiraApiToken">Jira API Token</label>
        <input type="password" id="jiraApiToken" name="jiraApiToken" placeholder={hasToken ? 'Değiştirmek için yeni token girin' : 'API Token buraya yapıştırın'} value={jiraDetails.jiraApiToken} onChange={handleChange} />
        <small className="form-hint">Jira API Token'ınızı <a href="https://id.atlassian.com/manage-profile/security/api-tokens" target="_blank" rel="noopener noreferrer">bu adresten</a> oluşturabilirsiniz. Güvenlik nedeniyle token'ınız burada gösterilmez.</small>
      </div>
      <div className="form-group">
    <label htmlFor="jiraPointHourRatio">Dönüşüm Oranı (1 Puan = ? Saat)</label>
    <input 
        type="number" 
        id="jiraPointHourRatio" 
        name="jiraPointHourRatio" 
        placeholder="Örn: 8" 
        value={jiraDetails.jiraPointHourRatio || ''} 
        onChange={handleChange} 
        min="0.1" 
        step="0.1"
    />
    <small className="form-hint">Bir story point'in kaç saatlik efora denk geldiğini belirtin.</small>
</div>
      <button type="submit" className="save-changes-btn" disabled={isSaving}>
        {isSaving ? 'Kaydediliyor...' : 'Entegrasyon Bilgilerini Kaydet'}
      </button>
    </form>
  );
}


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
    if(user) fetchProfile(); else setIsLoading(false);
  }, [user]);

  const showFeedback = useCallback((type, text) => {
    setFeedbackMessage({ type, text });
    setTimeout(() => setFeedbackMessage({ type: '', text: '' }), 3000);
  }, []);

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
      
      onUserUpdate({ ...user, name: newName, avatarId: selectedAvatar });
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
        <button onClick={() => setActiveTab('integrations')} className={activeTab === 'integrations' ? 'active' : ''}>Entegrasyonlar</button>
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

        {activeTab === 'integrations' && (
            <JiraIntegrationForm showFeedback={showFeedback} />
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