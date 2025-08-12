import React, { useState } from 'react';
import './VotingCards.css';

// KART DEĞERİNE GÖRE SEMBOL GRUBU ATAYAN YARDIMCI FONKSİYON
const getCardSuit = (value) => {
  // Özel kartlar için doğrudan atama yap
  if (value === '?') return 'joker';
  if (value === '☕') return 'coffee';
  if (value === '½') return 'clubs'; // ½ her zaman Sinek (Siyah) olsun

  // Değeri sayıya çevirmeyi dene
  const numericValue = parseFloat(String(value).replace(',', '.'));

  // Eğer sayı değilse, sembol atama
  if (isNaN(numericValue)) {
    return '';
  }

  // Sayının tam kısmını al ve 4'e bölümünden kalana göre grup ata
  const suitIndex = Math.floor(numericValue) % 4;
  
  // --- DEĞİŞTİRİLEN KISIM: Renklerin ardışık gelmesi için sıralama değiştirildi ---
  switch (suitIndex) {
    case 0: return 'spades';   // Siyah
    case 1: return 'hearts';   // Kırmızı
    case 2: return 'clubs';    // Siyah
    case 3: return 'diamonds'; // Kırmızı
    default: return '';
  }
};

function VotingCards({ cards, onVote, hasVoted }) {
  const [selectedVote, setSelectedVote] = useState(null);

  const handleVoteClick = (value) => {
    if (hasVoted) return;
    setSelectedVote(value);
    onVote(value);
  };

  return (
    <div className="voting-cards-container">
      <p>Lütfen görevin karmaşıklığını oylayın:</p>
      <div className="cards">
        {cards.map((value) => {
          const suit = getCardSuit(value); // Her kart için sembol grubunu hesapla
          return (
            <div
              key={value}
              className={`vote-card ${selectedVote === value ? 'selected' : ''}`}
              data-suit={suit} // CSS'in kullanması için data attribute olarak ata
              onClick={() => handleVoteClick(value)}
              role="button"
              tabIndex={hasVoted ? -1 : 0}
              onKeyPress={(e) => (e.key === 'Enter' || e.key === ' ') && handleVoteClick(value)}
            >
              <div className="card-corner top-left">{value}</div>
              <div className="card-value-main">{value}</div>
              <div className="card-corner bottom-right">{value}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default VotingCards;