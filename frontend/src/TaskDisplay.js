import React from 'react';

function TaskDisplay({ task }) {
  return (
    <div className="task-display">
      <h3>Aktif Görev</h3>
      <h2>{task.title}</h2>
      <p>{task.description}</p>
    </div>
  );
}
export default TaskDisplay;