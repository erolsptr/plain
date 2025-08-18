import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useLocation } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import JoinPrompt from '../JoinPrompt';
import TaskDisplay from '../TaskDisplay';
import TaskForm from '../TaskForm';
import VotingCards from '../VotingCards';
import Modal from '../components/Modal';
import RevealedCard from '../components/RevealedCard';
import '../VotingCards.css';
import '../Room.css';

const SOCKET_URL = 'http://localhost:8080/ws-poker';
const AI_PARTICIPANT_NAME = 'plAIn Asistanı';

const formatDuration = (ms) => {
  if (ms === null || typeof ms === 'undefined') {
    return ''; 
  }
  const totalSeconds = Math.round(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  
  return formattedTime;
};


const getVoteResult = (votes) => {
    const voteEntries = Object.entries(votes);
    if (!voteEntries.length) return null;

    const parseVoteValue = (voteString) => {
        if (voteString === '½') return 0.5;
        const num = parseFloat(voteString);
        return isNaN(num) ? null : num;
    };
    
    const simplifiedVotes = {};
    voteEntries.forEach(([voter, voteData]) => {
        simplifiedVotes[voter] = voteData.voteValue;
    });

    const humanNumericVotes = Object.entries(simplifiedVotes)
        .map(([voter, vote]) => {
            if (voter !== AI_PARTICIPANT_NAME) {
                return parseVoteValue(vote);
            }
            return null;
        })
        .filter(vote => vote !== null);

    if (humanNumericVotes.length === 0) {
        const aiVoteData = votes[AI_PARTICIPANT_NAME];
        return aiVoteData ? { text: aiVoteData.voteValue } : { text: "Oylama Yok" };
    }

    const voteCounts = humanNumericVotes.reduce((acc, vote) => {
        acc[vote] = (acc[vote] || 0) + 1;
        return acc;
    }, {});

    const maxCount = Math.max(...Object.values(voteCounts));
    const winners = Object.keys(voteCounts).filter(vote => voteCounts[vote] === maxCount);

    if (winners.length === 1) {
        const winnerVote = winners[0];
        return { text: winnerVote === '0.5' ? '½' : winnerVote };
    }

    const sum = humanNumericVotes.reduce((a, b) => a + b, 0);
    const average = sum / humanNumericVotes.length;
    const roundedAverage = Math.round(average * 10) / 10;
    const averageText = roundedAverage === 0.5 ? '½' : roundedAverage.toString();

    return {
        text: "Anlaşma Yok",
        average: averageText
    };
};
function Room({ user: currentUser }) {
  const { roomId } = useParams();
  const location = useLocation();
  
  const [user, setUser] = useState(currentUser || location.state?.user);
  const [stompClient, setStompClient] = useState(null);
  const [isConnected, setIsConnected] = useState(false);
  const [roomOwnerEmail, setRoomOwnerEmail] = useState(null);
  const [participants, setParticipants] = useState({});
  const [activeTask, setActiveTask] = useState({ title: 'Henüz bir görev belirlenmedi.', description: '', cardSet: '' });
  const [votes, setVotes] = useState({});
  const [hasVoted, setHasVoted] = useState(false);
  const [revealVotes, setRevealVotes] = useState(false);
  const [showTaskForm, setShowTaskForm] = useState(location.state?.isNewRoom || false);
  const [completedTasks, setCompletedTasks] = useState([]);
  const [pendingTasks, setPendingTasks] = useState([]);
  const [activeTab, setActiveTab] = useState('pending');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState(null);
  const [roomName, setRoomName] = useState('Oda Yükleniyor...');
  const [showCopyTooltip, setShowCopyTooltip] = useState(false);
  const [aiReasoning, setAiReasoning] = useState(null);
  const [isKickModalOpen, setIsKickModalOpen] = useState(false);
  const [userToKick, setUserToKick] = useState(null);
  const [activeParticipants, setActiveParticipants] = useState(new Set());
  const [votingStartTime, setVotingStartTime] = useState(null);
  const [timer, setTimer] = useState('00:00');
  const [jiraStatus, setJiraStatus] = useState({ state: 'idle', message: '' }); // idle, sending, success, error

  const fetchTasks = useCallback(async () => {
    const token = sessionStorage.getItem('token');
    if (!token) return;
    try {
      const [completedResponse, pendingResponse] = await Promise.all([
        fetch(`/api/rooms/${roomId}/tasks`, { headers: { 'Authorization': `Bearer ${token}` } }),
        fetch(`/api/rooms/${roomId}/pending-tasks`, { headers: { 'Authorization': `Bearer ${token}` } })
      ]);
      if (!completedResponse.ok || !pendingResponse.ok) { throw new Error('Görevler alınamadı.'); }
      const completedData = await completedResponse.json();
      const pendingData = await pendingResponse.json();
      setCompletedTasks(completedData);
      setPendingTasks(pendingData);
    } catch (error) {
      console.error("Görevleri çekerken hata:", error);
    }
  }, [roomId]);
  
  useEffect(() => {
    const fetchRoomDetails = async () => {
      const token = sessionStorage.getItem('token');
      if (!token) return;
      try {
        const response = await fetch(`/api/room-details?roomIds=${roomId}`, { headers: { 'Authorization': `Bearer ${token}` } });
        if (!response.ok) throw new Error('Oda detayları alınamadı.');
        const data = await response.json();
        if (data.length > 0) {
          setRoomName(data[0].roomName);
        } else {
          setRoomName('İsimsiz Oda');
        }
      } catch (error) {
        console.error("Oda detaylarını çekerken hata:", error);
        setRoomName('Bilinmeyen Oda');
      }
    };
    fetchRoomDetails();
  }, [roomId]);

  useEffect(() => {
    if (!user?.name || !user?.email) return;
    
    fetchTasks();

    let stateSub, revealSub, historySub; 
    const client = new Client({
      webSocketFactory: () => new SockJS(SOCKET_URL),
      connectHeaders: { Authorization: `Bearer ${sessionStorage.getItem('token')}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setIsConnected(true);
        stateSub = client.subscribe(`/topic/room/${roomId}/state`, (message) => {
            const roomState = JSON.parse(message.body);
            setRoomOwnerEmail(roomState.ownerEmail);
            setVotingStartTime(roomState.votingStartTime);
            setParticipants(roomState.participants || {});
            setActiveTask(roomState.activeTask || { title: 'Henüz bir görev belirlenmedi.', description: '', cardSet: '' });
            setVotes(roomState.votes || {});
            setAiReasoning(roomState.aiReasoning || null);
            setActiveParticipants(new Set(roomState.activeParticipants || []));
            setRevealVotes(false);
            setHasVoted(false);
        });
        revealSub = client.subscribe(`/topic/room/${roomId}/reveal`, () => setRevealVotes(true));
        historySub = client.subscribe(`/topic/room/${roomId}/history-updated`, () => {
          fetchTasks();
        });
        client.publish({ destination: `/app/room/${roomId}/register`, body: JSON.stringify({ sender: user.name }) });
      },
      onDisconnect: () => setIsConnected(false),
    });
    client.activate();
    setStompClient(client);
    return () => {
      if (stateSub) stateSub.unsubscribe();
      if (revealSub) revealSub.unsubscribe();
      if (historySub) historySub.unsubscribe(); 
      if (client) client.deactivate();
    };
  }, [user, roomId, fetchTasks]); 
    useEffect(() => {
    if (!votingStartTime || revealVotes) {
      setTimer('00:00');
      return;
    }

    const intervalId = setInterval(() => {
      const now = Date.now();
      const elapsed = Math.floor((now - votingStartTime) / 1000); 

      const minutes = Math.floor(elapsed / 60);
      const seconds = elapsed % 60;

      const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
      setTimer(formattedTime);
    }, 1000); 

    return () => clearInterval(intervalId);
  }, [votingStartTime, revealVotes]);

  const isModerator = user?.email === roomOwnerEmail;
  const allVotesIn = activeParticipants.size > 0 && activeParticipants.size === Object.keys(votes).length;

  const handleVote = (voteValue) => {
    if (stompClient && user?.name && votingStartTime) {
        const voteTime = Date.now();
        const durationMs = voteTime - votingStartTime; 

        setHasVoted(true);
        stompClient.publish({ 
            destination: `/app/room/${roomId}/vote`, 
            body: JSON.stringify({ 
                sender: user.name, 
                content: voteValue, 
                durationMs: durationMs, 
                type: 'VOTE' 
            }) 
        });
    }
};

  const openKickConfirmModal = (usernameToKick) => {
    if (!isModerator) return;
    setUserToKick(usernameToKick);
    setIsKickModalOpen(true);
  };

  const confirmKickUser = () => {
    if (!isModerator || !stompClient || !userToKick) return;
    
    stompClient.publish({
      destination: `/app/room/${roomId}/kick`,
      body: JSON.stringify({ sender: user.name, content: userToKick })
    });
    
    setIsKickModalOpen(false);
    setUserToKick(null);
  };

  const handleRevealVotes = () => {
    if (stompClient && user?.name) {
      stompClient.publish({ destination: `/app/room/${roomId}/reveal`, body: JSON.stringify({ sender: user.name }) });
    }
  };

  const handleNewRound = () => {
    if (stompClient && user?.name && isModerator) {
        stompClient.publish({
            destination: `/app/room/${roomId}/new-round`,
            body: JSON.stringify({ sender: user.name })
        });
    }
  };

  const handleSaveResult = async () => {
    if (!isModerator) return;
    const token = sessionStorage.getItem('token');
    if (!token) { alert("Yetkilendirme anahtarı bulunamadı."); return; }
    try {
      const response = await fetch(`/api/rooms/${roomId}/save-result`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!response.ok) {
        if (response.status === 403) { throw new Error("Sadece oda sahibi sonuçları kaydedebilir."); }
        throw new Error("Sonuçlar sunucuya kaydedilemedi.");
      }
    } catch (error) {
      console.error("Sonuç kaydetme hatası:", error);
      alert(error.message);
    }
  };
    const handleSendToJira = async () => {
    if (!isModerator || !activeTask?.id || !consensus?.text) return;

    // "Anlaşma Yok" durumunda Jira'ya göndermeyi engelle
    if (consensus.text === "Anlaşma Yok") {
        alert("Karar oyunda anlaşma sağlanamadığı için görev Jira'ya gönderilemez.");
        return;
    }

    setJiraStatus({ state: 'sending', message: 'Jira\'ya gönderiliyor...' });
    const token = sessionStorage.getItem('token');
    if (!token) {
        setJiraStatus({ state: 'error', message: 'Yetkilendirme hatası.' });
        return;
    }

    try {
        const response = await fetch(`/api/rooms/${roomId}/tasks/${activeTask.id}/send-to-jira`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ consensusScore: consensus.text })
        });

        const data = await response.json();

        if (!response.ok) {
            // Backend'den gelen detaylı hata mesajını kullan
            throw new Error(data.error || 'Jira\'ya gönderme başarısız oldu.');
        }
        
        setJiraStatus({ state: 'success', message: `Başarılı! Jira Görev Kodu: ${data.issueKey}` });

    } catch (error) {
        console.error("Jira'ya gönderme hatası:", error);
        setJiraStatus({ state: 'error', message: error.message });
    }
  };

  const handleStartVoting = (task) => {
    if (stompClient && isModerator) {
      const payload = { ...task, sender: user.name };
      stompClient.publish({ destination: `/app/room/${roomId}/set-task`, body: JSON.stringify(payload) });
    }
  };
  
  const handleTaskCreated = () => { fetchTasks(); setShowTaskForm(false); };
  const toggleTaskForm = () => setShowTaskForm(prev => !prev);
  const handleHistoryCardClick = (task) => { setSelectedTask(task); setIsModalOpen(true); };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href);
    setShowCopyTooltip(true);
    setTimeout(() => { setShowCopyTooltip(false); }, 2000);
  };

  if (!user) return <JoinPrompt onNameSubmit={(name) => setUser({ name })} />;
  if (!isConnected) return <div className="loading-screen">Odaya bağlanılıyor...</div>;

  const consensus = getVoteResult(votes);

  return (
    <>
      <div className="room-container">
        <div className="side-panel">
          <div className="room-header">
            <h3>{roomName}</h3>
            <div className="room-invite-controls">
                <span>Oda Kodu: {roomId}</span>
                <button onClick={handleCopyLink} className="copy-link-btn" title="Davet Linkini Kopyala">
                  {showCopyTooltip && <span className="copy-tooltip">Kopyalandı!</span>}
                  <svg xmlns="http://www.w.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                </button>
            </div>
          </div>
          <div>
            <h4>Katılımcılar ({Object.keys(votes).length}/{Object.keys(participants).length})</h4>
            <ul>
  {Object.entries(participants).map(([name, participantData]) => (
    <li key={name}>
      <div className="participant-details">
        <div className="participant-avatar-container">
          <img 
            src={`http://localhost:8080/avatars/${participantData.avatarId}.png`} 
            alt={`${name} avatar`}
            className={`participant-avatar ${participantData.email === roomOwnerEmail ? 'moderator' : ''}`}
            onError={(e) => { e.target.onerror = null; e.target.src="http://localhost:8080/avatars/default-avatar.png" }}
          />
          <div className={`status-indicator ${activeParticipants.has(name) ? 'active' : 'inactive'}`}></div>
          {isModerator && user.name !== name && name !== AI_PARTICIPANT_NAME && (
            <button onClick={() => openKickConfirmModal(name)} className="kick-user-btn" title={`${name} kullanıcısını at`}>
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
            </button>
          )}
        </div>
        <span className="participant-name">{name}</span>
      </div>
      <div className="participant-vote-status">
  {votes[name] && !revealVotes && (
    <span className="vote-check">
      ✓ 
      {votes[name].durationMs && (
        <span className="vote-duration">
  ({formatDuration(votes[name].durationMs)})
</span>
      )}
    </span>
  )}
  {votes[name] && revealVotes && <span className="vote-value">{votes[name].voteValue}</span>}
</div>
    </li>
  ))}
</ul>
          </div>
          {activeTask && activeTask.id && votingStartTime && !revealVotes && (
            <div className="voting-timer-container">
              <span>Geçen Süre:</span>
              <div className="timer-display">{timer}</div>
            </div>
          )}
<div className="moderator-controls">
  
  {isModerator && activeTask.title !== 'Henüz bir görev belirlenmedi.' && !revealVotes && (
    <button onClick={handleRevealVotes} disabled={!allVotesIn} className="reveal-button side-panel-button">
      Oyları Göster
    </button>
  )}
  
  {revealVotes && isModerator && (
    <div className="moderator-actions">
      <button onClick={handleNewRound} className="reveal-button side-panel-button"> 
        Yeni Tur Başlat
      </button>
      <button onClick={handleSaveResult} className="reveal-button side-panel-button primary"> 
        Sonucu Kaydet
      </button>
      <button 
        onClick={handleSendToJira} 
        className="reveal-button side-panel-button jira"
        disabled={jiraStatus.state === 'sending' || consensus?.text === "Anlaşma Yok"}
      >
        Jira'ya Gönder
      </button>
    </div>
    
  )}
      {/* YENİ JIRA DURUM GÖSTERGESİ */}
    {jiraStatus.state !== 'idle' && jiraStatus.state !== 'sending' && (
        <div className={`jira-status-message ${jiraStatus.state}`}>
            {jiraStatus.message}
        </div>
    )}
  
  {isModerator && (
      <button onClick={toggleTaskForm} className="reveal-button new-task-button side-panel-button"> 
          {showTaskForm ? 'Formu Kapat' : 'Yeni Görev Ekle'}
      </button>
  )}

</div>
          
          
        </div>
        <div className="main-panel">
          <TaskDisplay task={activeTask} />
          
          {showTaskForm && isModerator ? (
              <TaskForm roomId={roomId} onTaskCreated={handleTaskCreated} />
          ) : activeTask.title !== 'Henüz bir görev belirlenmedi.' ? (
              revealVotes ? (
                <div className="results-container">
                    <h2>Oylama Sonuçları</h2>
                    <div className="results-grid">
                      {consensus && (
  <RevealedCard 
    isConsensus={true} 
    consensusValue={consensus.text} 
    consensusAverage={consensus.average} 
  />
)}
                      
                      {Object.entries(votes).map(([name, voteData]) => (
  <RevealedCard
    key={name}
    name={name}
    vote={voteData.voteValue} 
    avatarId={participants[name]?.avatarId}
    isAI={name === AI_PARTICIPANT_NAME}
  />
))}
                    </div>
                    {aiReasoning && (
                      <div className="ai-reasoning-box">
                        <h4>{AI_PARTICIPANT_NAME}'ın Düşüncesi</h4>
                        <p>{aiReasoning}</p>
                      </div>
                    )}
                </div>
              ) : (
                  <VotingCards cards={activeTask.cardSet.split(',')} onVote={handleVote} hasVoted={hasVoted} />
              )
          ) : null}

          <div className="task-list-section">
            <div className="task-list-tabs">
              <button onClick={() => setActiveTab('pending')} className={activeTab === 'pending' ? 'active' : ''}>
                Hazır Olanlar ({pendingTasks.length})
              </button>
              <button onClick={() => setActiveTab('completed')} className={activeTab === 'completed' ? 'active' : ''}>
                Tamamlananlar ({completedTasks.length})
              </button>
            </div>
            
            <div className="task-list-content">
              {activeTab === 'pending' && (
                pendingTasks.length > 0 ? (
                  pendingTasks.map(task => (
                    <div key={task.id} className="pending-task-card">
                      <span>{task.title}</span>
                      {isModerator && (
                        <button onClick={() => handleStartVoting(task)} className="start-voting-btn">
                          Oylamayı Başlat
                        </button>
                      )}
                    </div>
                  ))
                ) : <p className="placeholder-text">Oylanacak hazır görev yok.</p>
              )}

              {activeTab === 'completed' && (
                completedTasks.length > 0 ? (
                  completedTasks.map(task => (
                    <div key={task.taskId} className="task-history-card" onClick={() => handleHistoryCardClick(task)}>
                      <div className="task-history-card-header">
                        <span className="task-history-card-title">{task.title}</span>
                        <span className="task-history-card-score">{task.consensusScore}</span>
                      </div>
                      <div className="task-history-card-footer">
                        <span>{Object.keys(task.votes).length} Katılımcı</span>
                      </div>
                    </div>
                  ))
                ) : <p className="placeholder-text">Bu odada henüz tamamlanmış bir oylama yok.</p>
              )}
            </div>
          </div>
        </div>
      </div>
      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)}>
        {selectedTask && (
          <div className="task-detail-modal">
            <h2>{selectedTask.title}</h2>
            {selectedTask.description && <p className="task-detail-description">{selectedTask.description}</p>}
            <div className="task-detail-grid">
              <div className="task-detail-consensus">
                <h4>Karar Oyu</h4>
                <div className="task-detail-score">{selectedTask.consensusScore}</div>
              </div>
              <div className="task-detail-votes">
                <h4>Verilen Oylar</h4>
                <ul>
                  {Object.entries(selectedTask.votes).map(([voter, vote]) => (
                    <li key={voter}>
                      <div className="voter-info-container">
                        <span className="voter-name">{voter}</span>
                        {voter === AI_PARTICIPANT_NAME && selectedTask.aiReasoning && (
                           <div className="ai-reasoning-container">
                              <svg className="ai-reasoning-icon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
                              <div className="ai-reasoning-tooltip">{selectedTask.aiReasoning}</div>
                           </div>
                        )}
                      </div>
                      <span className="vote-value">{vote}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal isOpen={isKickModalOpen} onClose={() => setIsKickModalOpen(false)}>
        {userToKick && (
          <div className="kick-confirm-modal">
            <h3>Kullanıcıyı Onayla</h3>
            <p>
              <strong>'{userToKick}'</strong> adlı kullanıcıyı odadan kalıcı olarak atmak istediğinizden emin misiniz?
            </p>
            <div className="modal-actions">
              <button 
                onClick={() => setIsKickModalOpen(false)} 
                className="modal-button secondary"
              >
                Vazgeç
              </button>
              <button 
                onClick={confirmKickUser} 
                className="modal-button danger"
              >
                Evet, At
              </button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}

export default Room;