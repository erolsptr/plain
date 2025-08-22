import React from 'react';
import './Modal.css';

function Modal({ isOpen, onClose, children, centerContent = false, isCloseButtonHidden = false }) {
  if (!isOpen) {
    return null;
  }

  // Overlay'e tıklandığında modalı kapatır, ama içeriğe tıklandığında kapatmaz.
  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className={`modal-content ${centerContent ? 'centered' : ''}`}>
        
        {!isCloseButtonHidden && (
          <button className="modal-close-btn" onClick={onClose}>
            &times; 
          </button>
        )}

        {children}
      </div>
    </div>
  );
}

export default Modal;