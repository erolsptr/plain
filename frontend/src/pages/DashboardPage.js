import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import './DashboardPage.css';
import Modal from '../components/Modal';

const EditIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
);
const DeleteIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>
);

function DashboardPage({ user }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [roomCode, setRoomCode] = useState('');
  const [rooms, setRooms] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [newRoomName, setNewRoomName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchRoomsAndDetails = useCallback(async () => {
    setIsLoading(true);
    const token = localStorage.getItem('token');
    if (!token) {
      setIsLoading(false);
      return;
    }
    try {
      // Adım 1: Temel oda listesini çek
      const roomsResponse = await fetch('/api/rooms', { headers: { 'Authorization': `Bearer ${token}` } });
      if (!roomsResponse.ok) throw new Error('Odalar alınamadı.');
      const baseRooms = await roomsResponse.json();

      if (baseRooms.length === 0) {
        setRooms([]);
        setIsLoading(false);
        return;
      }

      // Adım 2: Çekilen odaların ID'leriyle, bu odaların detaylarını (isimlerini) çek
      const roomIds = baseRooms.map(room => room.roomId).join(',');
      const detailsResponse = await fetch(`/api/room-details?roomIds=${roomIds}`, { headers: { 'Authorization': `Bearer ${token}` } });
      
      let roomDetails = [];
      if (detailsResponse.ok) {
        roomDetails = await detailsResponse.json();
      } else {
        console.error('Oda detayları alınamadı. Sadece temel bilgiler gösterilecek.');
      }

      // Adım 3: İki listeyi birleştir
      const enrichedRooms = baseRooms.map(room => {
        const detail = roomDetails.find(d => d.roomId === room.roomId);
        return {
          ...room,
          roomName: detail ? detail.roomName : room.roomId,
          creationDate: detail ? detail.creationDate : null
        };
      });
      
      setRooms(enrichedRooms);

    } catch (error) {
      console.error("Veri çekerken hata:", error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      fetchRoomsAndDetails();
    }
  }, [user, location, fetchRoomsAndDetails]);

  const handleCreateRoom = async () => {
    if (!newRoomName.trim() || isSubmitting) {
      return;
    }
    setIsSubmitting(true);
    const token = localStorage.getItem('token');
    if (!token) {
      alert("Yetkilendirme anahtarı bulunamadı.");
      setIsSubmitting(false);
      return;
    }
    try {
      const roomResponse = await fetch('/api/rooms', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!roomResponse.ok) throw new Error('Oda oluşturulamadı.');
      const newRoom = await roomResponse.json();
      const newRoomId = newRoom.roomId;

      const detailsData = {
        roomId: newRoomId,
        roomName: newRoomName.trim()
      };
      const detailsResponse = await fetch('/api/room-details', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(detailsData)
      });
      if (!detailsResponse.ok) throw new Error('Oda ismi kaydedilemedi.');

      navigate(`/room/${newRoomId}`, { state: { user: user, isNewRoom: true } });
    } catch (error) {
      console.error("Oda oluşturma hatası:", error);
      alert(error.message);
    } finally {
      setIsSubmitting(false);
      setIsCreateModalOpen(false);
      setNewRoomName('');
    }
  };
  
  const handleJoinRoom = (e) => {
    e.preventDefault();
    if (!roomCode.trim()) { alert("Lütfen geçerli bir oda kodu girin."); return; }
    navigate(`/room/${roomCode.trim().toUpperCase()}`, { state: { user: user } });
  };
  
  const handleCardClick = (roomId) => {
    navigate(`/room/${roomId}`, { state: { user: user } });
  };

  const handleEditRoom = (e, roomId) => {
    e.stopPropagation(); 
    console.log(`Edit butonuna tıklandı. Oda ID: ${roomId}`);
  };

  const handleDeleteRoom = async (e, roomId) => {
    e.stopPropagation(); 
    const isConfirmed = window.confirm("Bu odayı ve tüm geçmişini kalıcı olarak silmek istediğinizden emin misiniz?");
    if (!isConfirmed) return;
    const token = localStorage.getItem('token');
    if (!token) { alert("Yetkilendirme anahtarı bulunamadı."); return; }
    try {
      // Önce asıl odayı silmeyi dene, bu ana işlem
      const roomDeleteResponse = await fetch(`/api/rooms/${roomId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${token}` } });
      if (!roomDeleteResponse.ok) {
        if (roomDeleteResponse.status === 403) { throw new Error("Bu odayı silme yetkiniz bulunmuyor."); }
        throw new Error("Oda sunucudan silinemedi.");
      }
      
      // Ana oda başarıyla silindiyse, ilişkili detayları da sil
      await fetch(`/api/room-details/${roomId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${token}` } });
      
      setRooms(currentRooms => currentRooms.filter(room => room.roomId !== roomId));
    } catch (error) {
      console.error("Oda silme hatası:", error);
      alert(error.message); 
    }
  };

  if (!user) {
    return <div className="loading-screen">Kullanıcı bilgisi bekleniyor...</div>;
  }

  return (
    <>
      <div className="dashboard-container">
        <h2>Kontrol Paneli</h2>
        <p>Hoşgeldin, <strong>{user.name}</strong>!</p>
        
        <div className="dashboard-actions-grid">
          <div className="action-card">
            <h3>Yeni Oda Oluştur</h3>
            <p>Yeni bir oylama oturumu başlatmak için butona tıkla.</p>
            <button onClick={() => setIsCreateModalOpen(true)} className="btn btn-primary">
              Oda Oluştur
            </button>
          </div>
          <div className="action-card">
            <h3>Bir Odaya Katıl</h3>
            <p>Mevcut bir odaya katılmak için oda kodunu gir.</p>
            <form onSubmit={handleJoinRoom} className="join-room-form">
              <input type="text" placeholder="Oda Kodu" value={roomCode} onChange={(e) => setRoomCode(e.target.value)} className="room-code-input" />
              <button type="submit" className="btn btn-secondary">Katıl</button>
            </form>
          </div>
        </div>

        <div className="past-rooms-section">
          <h3>Geçmiş Odalar</h3>
          {isLoading ? (
            <p className="placeholder-text">Odalar yükleniyor...</p>
          ) : rooms.length > 0 ? (
            <div className="rooms-grid">
              {rooms.map(room => (
                <div key={room.roomId} className="room-card" onClick={() => handleCardClick(room.roomId)}>
                  <div className="room-card-header">
                    <h4 className="room-card-title">{room.roomName || room.roomId}</h4>
                    <div className="room-card-actions">
                      <button onClick={(e) => handleEditRoom(e, room.roomId)} className="btn-icon btn-edit" title="Düzenle">
                        <EditIcon />
                      </button>
                      <button onClick={(e) => handleDeleteRoom(e, room.roomId)} className="btn-icon btn-delete" title="Sil">
                        <DeleteIcon />
                      </button>
                    </div>
                  </div>
                  <div className="room-card-body">
                    <span>Sahip: <strong>{room.ownerName}</strong></span>
                    <span>{room.taskCount} görev</span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="placeholder-text">
              Bu listede şimdilik sadece sizin oluşturduğunuz odalar görüntülenir.
            </p>
          )}
        </div>
      </div>

      <Modal isOpen={isCreateModalOpen} onClose={() => setIsCreateModalOpen(false)}>
        <div className="create-room-modal">
          <h2>Yeni Oda Oluştur</h2>
          <p>Oylama odanız için bir isim belirleyin.</p>
          <input
            type="text"
            placeholder="Oda İsmi (örn: Sprint 24 Planlama)"
            value={newRoomName}
            onChange={(e) => setNewRoomName(e.target.value)}
            className="room-name-input-modal"
          />
          <button onClick={handleCreateRoom} disabled={isSubmitting || !newRoomName.trim()} className="btn btn-primary">
            {isSubmitting ? 'Oluşturuluyor...' : 'Odayı Oluştur ve Katıl'}
          </button>
        </div>
      </Modal>
    </>
  );
}

export default DashboardPage;