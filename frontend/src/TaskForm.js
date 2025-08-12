import React, { useState, useEffect } from 'react';

const CARD_SETS = {
  FIBONACCI: ['0', '1', '2', '3', '5', '8', '13', '21', '?', '☕'],
  MODIFIED_FIB: ['0', '1', '1.5', '2', '2.5', '3', '3.5', '4', '4.5', '5', '8', '13', '?', '☕'],
  SCRUM: ['0', '½', '1', '2', '3', '5', '8', '13', '20', '40', '100', '?', '☕'],
  SEQUENTIAL: ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
  HOURS: ['1', '2', '4', '8', '16', '24', '32', '40'],
};
const MANUAL_CARDS = [...new Set([...CARD_SETS.FIBONACCI, ...CARD_SETS.MODIFIED_FIB, ...CARD_SETS.SCRUM, ...CARD_SETS.SEQUENTIAL, ...CARD_SETS.HOURS])].sort((a,b) => {
    const valA = a === '½' ? 0.5 : Number(a);
    const valB = b === '½' ? 0.5 : Number(b);
    if (isNaN(valA) || isNaN(valB)) return 1; 
    return valA - valB;
});

const getDefaultSelectedCards = (cardSetArray) => {
  const defaultSelection = new Set();
  cardSetArray.forEach(card => {
    if (card === '?' || card === '☕') {
      return;
    }
    const numericValue = card === '½' ? 0.5 : parseFloat(card);
    if (!isNaN(numericValue) && numericValue <= 5) {
      defaultSelection.add(card);
    }
  });
  return defaultSelection;
};


function TaskForm({ roomId, onTaskCreated }) { 
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [cardSet, setCardSet] = useState('FIBONACCI');
  const [selectedCards, setSelectedCards] = useState(getDefaultSelectedCards(CARD_SETS.FIBONACCI));
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (cardSet === 'MANUAL') {
      setSelectedCards(new Set(MANUAL_CARDS));
    } else {
      setSelectedCards(getDefaultSelectedCards(CARD_SETS[cardSet]));
    }
  }, [cardSet]);

  const handleCardToggle = (cardValue) => {
    setSelectedCards(prevSelected => {
      const newSelected = new Set(prevSelected);
      if (newSelected.has(cardValue)) {
        newSelected.delete(cardValue);
      } else {
        newSelected.add(cardValue);
      }
      return newSelected;
    });
  };

  // --- BU FONKSİYON TAMAMEN YENİLENDİ (ARTIK FormData KULLANIYOR) ---
  const handleSetTask = async () => {
    if (!title.trim() || isSubmitting) {
      return;
    }
    setIsSubmitting(true);

    // JSON yerine bir FormData nesnesi oluştur
    const formData = new FormData();
    formData.append('title', title.trim());
    formData.append('description', description.trim());
    formData.append('cardSet', Array.from(selectedCards).join(','));

    const token = sessionStorage.getItem('token');
    if (!token) {
        alert("Yetkilendirme anahtarı bulunamadı.");
        setIsSubmitting(false);
        return;
    }

    try {
      const response = await fetch(`/api/rooms/${roomId}/tasks`, {
        method: 'POST',
        headers: {
          // 'Content-Type' başlığını KESİNLİKLE belirtme!
          // Tarayıcı, FormData kullandığında bunu kendisi otomatik olarak
          // 'multipart/form-data' ve gerekli boundary ile ayarlar.
          'Authorization': `Bearer ${token}`
        },
        body: formData // JSON.stringify yerine doğrudan formData nesnesini gönder
      });

      if (!response.ok) {
        // Hata mesajını sunucudan almayı dene (daha bilgilendirici olabilir)
        const errorText = await response.text();
        console.error("Sunucu hatası:", errorText);
        throw new Error(`Görev oluşturulamadı. Sunucu durumu: ${response.status}`);
      }
      
      const createdTask = await response.json();
      onTaskCreated(createdTask);
      setTitle('');
      setDescription('');

    } catch (error) {
      console.error("Görev oluşturma hatası:", error);
      alert(error.message);
    } finally {
      setIsSubmitting(false);
    }
  };
  // --- YENİLEME SONU ---


  const currentVisibleCards = cardSet === 'MANUAL' ? MANUAL_CARDS : CARD_SETS[cardSet];

  return (
    <div className="task-form">
      <h4>Yeni Görev Belirle</h4>
      <input
        type="text"
        placeholder="Görev Başlığı"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
      />
      <textarea
        placeholder="Açıklama (opsiyonel)"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      
      <div className="card-set-selector">
        <label htmlFor="card-set">Oylama Seti:</label>
        <select 
          id="card-set"
          value={cardSet} 
          onChange={(e) => setCardSet(e.target.value)}
        >
          <option value="FIBONACCI">Fibonacci</option>
          <option value="MODIFIED_FIB">Değiştirilmiş Fibonacci (Ara Değerli)</option>
          <option value="SCRUM">Scrum </option>
          <option value="SEQUENTIAL">Sıralı</option>
          <option value="HOURS">Saat</option>
          <option value="MANUAL">Tümünü Göster/Düzenle</option>
        </select>
      </div>

      <div className="customize-cards-container">
        {currentVisibleCards.map(card => (
          <label key={card} className="card-checkbox-item">
            <input 
              type="checkbox"
              value={card}
              checked={selectedCards.has(card)}
              onChange={() => handleCardToggle(card)}
            />
            {card} 
          </label>
        ))}
      </div>

      <button onClick={handleSetTask} disabled={isSubmitting}>
        {isSubmitting ? 'Kaydediliyor...' : 'Görevi Listeye Ekle'}
      </button>
    </div>
  );
}

export default TaskForm;