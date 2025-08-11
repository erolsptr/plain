import React, { useState } from 'react';

function JoinPrompt({ onNameSubmit }) {
  const [name, setName] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault(); 
    if (name) {
      onNameSubmit(name);
    }
  };

  return (
    <div className="lobby-container">
      <h3>Odaya Katıl</h3>
      <p>Devam etmek için lütfen isminizi girin.</p>
      <form onSubmit={handleSubmit} className="lobby-form">
        <input
          type="text"
          placeholder="İsminiz"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoFocus
        />
        <button type="submit">Katıl</button>
      </form>
    </div>
  );
}

export default JoinPrompt;