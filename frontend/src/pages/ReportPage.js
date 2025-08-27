import React, { useState, useEffect } from "react";
import "./ReportPage.css";

const AccordionItem = ({ roomName, tasks, totalPoints }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="accordion-item">
      <button className="accordion-header" onClick={() => setIsOpen(!isOpen)}>
        <span className="accordion-title">{roomName}</span>
        <div className="accordion-summary">
          <span>{tasks.length} Görev</span>
          <span>Toplam {totalPoints} Puan</span>
        </div>
        <span className={`accordion-icon ${isOpen ? "open" : ""}`}>
          &#9660;
        </span>
      </button>
      {isOpen && (
        <div className="accordion-content">
          <table>
            <thead>
              <tr>
                <th>Görev Başlığı</th>
                <th>Karar Oyu</th>
                <th>Katılımcı Oyları</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map((task, index) => (
                <tr key={index}>
                  <td>{task.title}</td>
                  <td>{task.consensusScore}</td>
                  <td>
                    {task.votes
                      .map((vote) => `${vote.voterName}: ${vote.voteValue}`)
                      .join(", ")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

function ReportPage() {
  const [reportData, setReportData] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchReport = async () => {
      setIsLoading(true);
      const token = sessionStorage.getItem("token");
      try {
        const response = await fetch("/api/reports/user-summary", {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) throw new Error("Rapor verileri yüklenemedi.");
        const data = await response.json();
        setReportData(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setIsLoading(false);
      }
    };

    fetchReport();
  }, []);

  if (isLoading) {
    return <div className="loading-screen">Rapor oluşturuluyor...</div>;
  }

  if (error) {
    return <div className="error-message">{error}</div>;
  }

  if (!reportData || reportData.totalOwnedRooms === 0) {
    return (
      <div className="report-page-container">
        <header className="report-header">
          <h1>Raporlar</h1>
        </header>
        <div className="no-projects-placeholder">
          <h3>Raporlanacak Veri Bulunamadı</h3>
          <p>Henüz sahip olduğunuz bir oda veya tamamlanmış bir oylama yok.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="report-page-container">
      <header className="report-header">
        <h1>Raporlar</h1>
      </header>

      <div className="summary-cards-grid">
        <div className="summary-card panel">
          <h3>Toplam Oda</h3>
          <p>{reportData.totalOwnedRooms}</p>
        </div>
        <div className="summary-card panel">
          <h3>Toplam Oylanan Görev</h3>
          <p>{reportData.totalVotedTasks}</p>
        </div>
        <div className="summary-card panel">
          <h3>Toplam Puan (Story Points)</h3>
          <p>{reportData.totalStoryPoints}</p>
        </div>
      </div>

      <div className="detailed-report-section">
        <h2>Oda Bazında Detaylar</h2>
        <div className="accordion">
          {Object.entries(reportData.roomReports).map(([roomName, tasks]) => {
            const totalPoints = tasks.reduce((sum, task) => {
              const score = parseFloat(
                String(task.consensusScore).replace("½", "0.5")
              );
              return isNaN(score) ? sum : sum + score;
            }, 0);
            return (
              <AccordionItem
                key={roomName}
                roomName={roomName}
                tasks={tasks}
                totalPoints={totalPoints}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default ReportPage;
