import React from 'react';
import ReactDOM from 'react-dom';
import './ConfirmModal.css';

const ConfirmModal = ({ isOpen, title, message, onConfirm, onCancel, confirmText = 'Confirm', cancelText = 'Cancel', isDanger = false, showCancel = true }) => {
  if (!isOpen) return null;

  return ReactDOM.createPortal(
    <div className="confirm-modal-overlay" onClick={onCancel}>
      <div className="confirm-modal-content" onClick={(e) => e.stopPropagation()}>
        <h3 className="confirm-modal-title">{title}</h3>
        <p className="confirm-modal-message">{message}</p>
        <div className="confirm-modal-actions">
          <button 
            className={`confirm-modal-btn ${isDanger ? 'confirm-modal-btn-danger' : 'confirm-modal-btn-primary'}`}
            onClick={onConfirm}
          >
            {confirmText}
          </button>
          {showCancel && (
            <button 
              className="confirm-modal-btn confirm-modal-btn-secondary"
              onClick={onCancel}
            >
              {cancelText}
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};

export default ConfirmModal;
