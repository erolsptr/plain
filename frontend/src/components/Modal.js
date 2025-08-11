import React from 'react';
import './Modal.css';

function Modal({ isOpen, onClose, children }) {
  if (!isOpen) {
    return null; 
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <button className="modal-close-button" onClick={onClose}>
          × {/* Bu, 'X' (çarpı) işaretidir */}
        </button>
        {children}
      </div>
    </div>
  );
}

export default Modal;