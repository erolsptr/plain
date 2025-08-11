import React, { useState } from 'react';

function Lobby({ onRoomCreated }) { 
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const handleCreateRoom = () => {
    if (!name) {
      setError('Oda oluşturmak için bir isim girmelisiniz.');
      return;
    }
    setError('');
    onRoomCreated({ name }); 
  };

  return (
    <div className="lobby-container">
      <h2>Odaya Hoşgeldin!</h2>
      <p>Yeni bir oda oluşturmak için ismini gir ve butona tıkla.</p>
      <div className="lobby-form">
        <input
          type="text"
          placeholder="İsminiz"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button onClick={handleCreateRoom}>Oda Oluştur</button>
      </div>
      {error && <p style={{ color: 'red', marginTop: '10px' }}>{error}</p>}
    </div>
  );
}

export default Lobby;