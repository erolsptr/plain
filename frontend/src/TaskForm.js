import React, { useState, useEffect } from 'react';

// Yeni "Sık Kullanılanlar" destesi tanımlandı
const FAVORITES_DECK = ['0', '½', '1', '1.5', '2', '2.5', '3', '3.5', '4', '4.5', '5'];

const CARD_SETS = {
  // Yeni deste en üste eklendi
  FAVORITES: FAVORITES_DECK,
  FIBONACCI: ['0', '1', '2', '3', '5', '8', '13', '21', '?', '☕'],
  MODIFIED_FIB: ['0', '1', '1.5', '2', '2.5', '3', '3.5', '4', '4.5', '5', '8', '13', '?', '☕'],
  SCRUM: ['0', '½', '1', '2', '3', '5', '8', '13', '20', '40', '100', '?', '☕'],
  SEQUENTIAL: ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
  HOURS: ['1', '2', '4', '8', '16', '24', '32', '40'],
};

// 'MANUAL_CARDS' ile ilgili tüm mantık kaldırıldı.

const getDefaultSelectedCards = (cardSetArray) => {
  const defaultSelection = new Set();
  // '?' ve '☕' gibi özel kartları her zaman hariç tut
  const filteredCards = cardSetArray.filter(card => card !== '?' && card !== '☕');
  filteredCards.forEach(card => {
    defaultSelection.add(card);
  });
  return defaultSelection;
};


function TaskForm({ roomId, onTaskCreated }) { 
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  // Varsayılan deste olarak 'FAVORITES' ayarlandı
  const [cardSet, setCardSet] = useState('FAVORITES');
  const [selectedCards, setSelectedCards] = useState(getDefaultSelectedCards(CARD_SETS.FAVORITES));
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    // 'MANUAL' kontrolü kaldırıldı, mantık basitleştirildi.
    // Her deste değişiminde, o destenin tüm kartları varsayılan olarak seçilir.
    setSelectedCards(getDefaultSelectedCards(CARD_SETS[cardSet]));
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

  const handleSetTask = async () => {
    if (!title.trim() || isSubmitting) {
      return;
    }
    // Seçili kart yoksa göndermeyi engelle
    if (selectedCards.size === 0) {
        alert("Lütfen en az bir kart seçin.");
        return;
    }
    setIsSubmitting(true);

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
          'Authorization': `Bearer ${token}`
        },
        body: formData
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error("Sunucu hatası:", errorText);
        throw new Error(`Görev oluşturulamadı. Sunucu durumu: ${response.status}`);
      }
      
      const createdTask = await response.json();
      onTaskCreated(createdTask);
      setTitle('');
      setDescription('');
      // Form gönderildikten sonra varsayılan desteye geri dön
      setCardSet('FAVORITES'); 

    } catch (error) {
      console.error("Görev oluşturma hatası:", error);
      alert(error.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // 'MANUAL' mantığı kaldırıldığı için bu satır basitleştirildi.
  const currentVisibleCards = CARD_SETS[cardSet];

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
          {/* Seçenekler CARD_SETS nesnesinden dinamik olarak oluşturuluyor */}
          <option value="FAVORITES">Sık Kullanılanlar</option>
          <option value="FIBONACCI">Fibonacci</option>
          <option value="MODIFIED_FIB">Değiştirilmiş Fibonacci</option>
          <option value="SCRUM">Scrum</option>
          <option value="SEQUENTIAL">Sıralı</option>
          <option value="HOURS">Saat</option>
          {/* 'MANUAL' seçeneği kaldırıldı */}
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