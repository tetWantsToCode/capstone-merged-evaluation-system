import React from "react";
import "./PendingEvaluationsModal.css";

const PendingEvaluationsModal = ({ isOpen, onClose, pendingEvaluations = [] }) => {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content pending-evaluations-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Pending Evaluations</h2>
          <button className="close-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <div className="modal-body">
          {pendingEvaluations.length === 0 ? (
            <p className="empty-state">No pending evaluations</p>
          ) : (
            <div className="pending-list">
              <table className="pending-table">
                <thead>
                  <tr>
                    <th>Adviser</th>
                    <th>Class</th>
                    <th>Team</th>
                    <th>Questionnaire</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingEvaluations.map((evaluation) => (
                    <tr key={evaluation.evaluationId}>
                      <td>{evaluation.adviserName}</td>
                      <td>{evaluation.className}</td>
                      <td>{evaluation.teamName}</td>
                      <td>{evaluation.questionnaireName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default PendingEvaluationsModal;
