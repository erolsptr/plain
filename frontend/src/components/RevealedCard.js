import React from 'react';
import './RevealedCard.css'; // Yeni CSS dosyamızı import ediyoruz

// Sembolleri ve renkleri atayan yardımcı fonksiyon
const getCardSuit = (value) => {
  if (value === '?') return 'joker';
  if (value === '☕') return 'coffee';
  if (value === '½') return 'clubs';

  const numericValue = parseFloat(String(value).replace(',', '.'));
  if (isNaN(numericValue)) return '';

  const suitIndex = Math.floor(numericValue) % 4;
  switch (suitIndex) {
    case 0: return 'spades';
    case 1: return 'hearts';
    case 2: return 'clubs';
    case 3: return 'diamonds';
    default: return '';
  }
};

// Bu bileşen, gösterilen her bir sonuç kartını temsil edecek
function RevealedCard({ name, vote, avatarId, isAI, isConsensus, consensusValue }) {
  
  // Karar Oyu kartı için özel mantık
  if (isConsensus) {
    return (
      <div className="revealed-card consensus-card">
        <div className="consensus-label">Karar Oyu</div>
        <div className="consensus-value">{consensusValue}</div>
        <div className="consensus-icon">👑</div>
      </div>
    );
  }

  // Diğer tüm kartlar için (Kullanıcı ve AI)
  const suit = getCardSuit(vote);
  const cardClasses = `revealed-card ${isAI ? 'ai-card' : ''}`;
  const avatarSrc = `http://localhost:8080/avatars/${avatarId}.png`;
  const defaultAvatarSrc = "http://localhost:8080/avatars/default-avatar.png";

  return (
    <div className={cardClasses} data-suit={suit}>
      <div className="card-corner top-left">{vote}</div>
      <div className="card-value-main revealed">{vote}</div>
      <div className="card-corner bottom-right">{vote}</div>
      
      <div className="voter-info">
        <img 
          src={avatarSrc} 
          alt={`${name} avatar`}
          className="voter-avatar"
          onError={(e) => { e.target.onerror = null; e.target.src = defaultAvatarSrc; }}
        />
        <span className="voter-name">{name}</span>
      </div>
    </div>
  );
}

export default RevealedCard;