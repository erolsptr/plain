import React, { useState, useEffect, useCallback } from 'react';
import './ProjectsPage.css'; // Bu CSS dosyasını birazdan oluşturacağız
import Modal from '../components/Modal';

function ProjectsPage() {
    const [projects, setProjects] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [newProjectName, setNewProjectName] = useState('');
    const [newProjectUrl, setNewProjectUrl] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
    const [projectToDelete, setProjectToDelete] = useState(null);

    const fetchProjects = useCallback(async () => {
        setIsLoading(true);
        const token = sessionStorage.getItem('token');
        try {
            const response = await fetch('/api/projects', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (!response.ok) throw new Error('Projeler yüklenemedi.');
            const data = await response.json();
            setProjects(data);
        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchProjects();
    }, [fetchProjects]);

    const handleCreateProject = async () => {
        if (!newProjectName.trim() || !newProjectUrl.trim()) return;
        setIsSubmitting(true);
        setError('');
        const token = sessionStorage.getItem('token');
        try {
            const response = await fetch('/api/projects', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ name: newProjectName, githubUrl: newProjectUrl })
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Proje oluşturulamadı.');
            }
            await fetchProjects(); // Listeyi yenile
            setIsModalOpen(false);
            setNewProjectName('');
            setNewProjectUrl('');
        } catch (err) {
            setError(err.message);
        } finally {
            setIsSubmitting(false);
        }
    };

    // --- BU İKİ YENİ FONKSİYONU EKLE ---

// Bu fonksiyon, "Sil" butonuna tıklandığında çalışır ve onay modalını açar
const openDeleteConfirmModal = (project) => {
    setProjectToDelete(project);
    setIsConfirmModalOpen(true);
};

// Bu fonksiyon, modal içindeki "Evet, Sil" butonuna tıklandığında çalışır
const confirmDeleteProject = async () => {
    if (!projectToDelete) return;

    const token = sessionStorage.getItem('token');
    try {
        const response = await fetch(`/api/projects/${projectToDelete.id}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) throw new Error('Proje silinemedi.');
        
        await fetchProjects(); // Listeyi yenile
    } catch (err) {
        setError(err.message);
    } finally {
        // Modal'ı kapat ve silinecek proje state'ini temizle
        setIsConfirmModalOpen(false);
        setProjectToDelete(null);
    }
};

const handleIndexProject = async (projectId) => {
    setError('');
    const token = sessionStorage.getItem('token');
    
    setProjects(prevProjects => 
        prevProjects.map(p => 
            p.id === projectId ? { ...p, indexingStatus: 'INDEXING', indexingError: null } : p
        )
    );

    try {
        const response = await fetch(`/api/projects/${projectId}/index`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) {
            throw new Error('İndeksleme işlemi başlatılamadı.');
        }
        
        // İşlem başarıyla başlatıldı, şimdi durumu kontrol etmeye başla
        pollIndexingStatus(projectId);

    } catch (err) {
        setError(err.message);
        setProjects(prevProjects => 
            prevProjects.map(p => 
                p.id === projectId ? { ...p, indexingStatus: 'FAILED', indexingError: err.message } : p
            )
        );
    }
};
// --- BU YENİ FONKSİYONU EKLE ---

const pollIndexingStatus = (projectId) => {
    const token = sessionStorage.getItem('token');
    
    const intervalId = setInterval(async () => {
        try {
            const response = await fetch('/api/projects', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (!response.ok) {
                // Eğer proje listesi çekilemezse, sormayı durdur.
                clearInterval(intervalId);
                return;
            }
            const allProjects = await response.json();
            const targetProject = allProjects.find(p => p.id === projectId);

            // Eğer proje listede yoksa (silinmiş olabilir) veya durum değiştiyse, sormayı durdur.
            if (!targetProject || targetProject.indexingStatus === 'COMPLETED' || targetProject.indexingStatus === 'FAILED') {
                clearInterval(intervalId); // Döngüyü durdur
                setProjects(allProjects); // Tüm listeyi en güncel haliyle güncelle
            }
            // Durum hala 'INDEXING' ise hiçbir şey yapma, bir sonraki kontrolü bekle.

        } catch (error) {
            console.error("Durum kontrolü sırasında hata:", error);
            clearInterval(intervalId); // Hata olursa döngüyü durdur
        }
    }, 5000); // Her 5 saniyede bir kontrol et
};

    return (
        <>
            <div className="projects-page-container">
                <header className="projects-header">
                    <h2>Projelerim</h2>
                    <button onClick={() => setIsModalOpen(true)}>Yeni Proje Ekle</button>
                </header>

                {isLoading && <p>Projeler yükleniyor...</p>}
                {error && <p className="error-message">{error}</p>}

                {!isLoading && projects.length === 0 && (
                     <div className="no-projects-placeholder">
                        <h3>Henüz Proje Eklemediniz</h3>
                        <p>Oylamalarda AI'ın kod analizi yapabilmesi için bir GitHub projesi ekleyerek başlayın.</p>
                    </div>
                )}
                
                {!isLoading && projects.length > 0 && (
                    <div className="projects-grid">
                        {projects.map(project => (
                            <div key={project.id} className="project-card panel">
                                <div className="project-card-header">
                                    <h3 className="project-name">{project.name}</h3>
                                    <div className="project-status" data-status={project.indexingStatus}>
                                        {project.indexingStatus}
                                    </div>
                                </div>
                                <p className="project-url">{project.githubUrl}</p>
                                <div className="project-card-actions">
                                    <button 
    className="btn-secondary" 
    onClick={() => handleIndexProject(project.id)}
    // Eğer proje zaten indeksleniyorsa veya tamamlandıysa butonu pasif yapabiliriz (isteğe bağlı)
    disabled={project.indexingStatus === 'INDEXING' || project.indexingStatus === 'COMPLETED'}
>
    {project.indexingStatus === 'INDEXING' ? 'İndeksleniyor...' : 
     project.indexingStatus === 'COMPLETED' ? 'Yeniden İndeksle' : 
     'İndeksle'}
</button>
                                    <button className="btn-danger" onClick={() => openDeleteConfirmModal(project)}>Sil</button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)}>
                <div className="create-project-modal">
                    <h2>Yeni GitHub Projesi Ekle</h2>
                    <input
                        type="text"
                        placeholder="Proje Adı (örn: plAIn Frontend)"
                        value={newProjectName}
                        onChange={(e) => setNewProjectName(e.target.value)}
                    />
                    <input
                        type="text"
                        placeholder="GitHub Repo URL (https://...)"
                        value={newProjectUrl}
                        onChange={(e) => setNewProjectUrl(e.target.value)}
                    />
                    <button onClick={handleCreateProject} disabled={isSubmitting}>
                        {isSubmitting ? 'Ekleniyor...' : 'Projeyi Ekle'}
                    </button>
                </div>
            </Modal>

<Modal isOpen={isConfirmModalOpen} onClose={() => setIsConfirmModalOpen(false)}>
    {projectToDelete && (
      <div className="confirm-delete-modal">
        <h3>Projeyi Sil</h3>
        <p>
          <strong>"{projectToDelete.name}"</strong> adlı projeyi kalıcı olarak silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.
        </p>
        <div className="modal-actions">
          <button 
            onClick={() => setIsConfirmModalOpen(false)} 
            className="btn-secondary"
          >
            Vazgeç
          </button>
          <button 
            onClick={confirmDeleteProject} 
            className="btn-danger"
          >
            Evet, Sil
          </button>
        </div>
      </div>
    )}
</Modal>
        </>
    );
}

export default ProjectsPage;