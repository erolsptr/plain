import React from 'react';
import './VotingCards.css';


function VotingCards({ cards, onVote, hasVoted }) {
  return (
    <div className="voting-cards-container">
      <p>Lütfen görevin karmaşıklığını oylayın:</p>
      <div className="cards">
        {/* Sabit dizi yerine, props'tan gelen 'cards' dizisini map'liyoruz. */}
        {cards.map((value) => (
          <button
            key={value}
            className="vote-card"
            disabled={hasVoted}
            onClick={() => onVote(value)}
          >
            {value}
          </button>
        ))}
      </div>
    </div>
  );
}

export default VotingCards;