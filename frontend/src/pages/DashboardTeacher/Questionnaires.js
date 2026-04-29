import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { questionnaireAPI, classAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";
import QuestionnaireDetailModal from "./QuestionnaireDetailModal";
import "./Teacher.css";

const API_BASE_URL = (process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api').replace(/\/api\/?$/, '');
const LOCAL_API_BASE_URL = 'http://localhost:8080';

const fetchWithLocalFallback = async (path, options = {}) => {
  try {
    return await fetch(`${API_BASE_URL}${path}`, options);
  } catch (error) {
    if (API_BASE_URL === LOCAL_API_BASE_URL) {
      throw error;
    }
    return fetch(`${LOCAL_API_BASE_URL}${path}`, options);
  }
};

const Questionnaires = () => {
  const toast = useToast();
  const navigate = useNavigate();
  const [questionnaires, setQuestionnaires] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [confirmModal, setConfirmModal] = useState({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: null
  });
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [googleLinked, setGoogleLinked] = useState(false);
  const [selectedClasses, setSelectedClasses] = useState([]);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [selectedQuestionnaireId, setSelectedQuestionnaireId] = useState(null);

  useEffect(() => {
    fetchQuestionnaires();
    fetchClasses();
    checkGoogleLink();
  }, []);

  const checkGoogleLink = async () => {
    try {
      const userStr = localStorage.getItem('user');
      const token = userStr ? JSON.parse(userStr)?.token : null;
      if (!token) {
        setGoogleLinked(false);
        return false;
      }

      const response = await fetchWithLocalFallback('/api/google-auth/status', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      const data = await response.json();
      setGoogleLinked(data.isLinked);
      return !!data.isLinked;
    } catch (err) {
      toast.error('Error checking Google link status');
      setGoogleLinked(false);
      return false;
    }
  };

  const fetchQuestionnaires = async () => {
    try {
      setLoading(true);
      const data = await questionnaireAPI.getAllQuestionnaires();
      setQuestionnaires(data);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchClasses = async () => {
    try {
      const data = await classAPI.getAllClasses();
      // Filter classes for this teacher only
      const user = JSON.parse(localStorage.getItem('user'));
      if (user && user.id) {
        const teacherClasses = data.filter(c => String(c.teacherId) === String(user.id));
        setClasses(teacherClasses);
      } else {
        setClasses(data);
      }
    } catch (err) {
      toast.error('Error fetching classes');
    }
  };

  const handleDeleteQuestionnaire = (id) => {
    setConfirmModal({
      isOpen: true,
      title: "Delete Questionnaire",
      message: "Are you sure you want to delete this questionnaire? This action cannot be undone.",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await questionnaireAPI.deleteQuestionnaire(id);
          fetchQuestionnaires();
          toast.success('Questionnaire deleted successfully!');
        } catch (err) {
          toast.error('Error deleting questionnaire: ' + err.message);
        }
      }
    });
  };

  const handleToggleQuestionnaireStatus = (questionnaire) => {
    const nextActive = !questionnaire.isActive;
    setConfirmModal({
      isOpen: true,
      title: nextActive ? "Activate Questionnaire" : "Deactivate Questionnaire",
      message: nextActive
        ? "Activate this questionnaire? It will become available to advisers again."
        : "Deactivate this questionnaire? It will stay assigned to classes but advisers will no longer be able to use it.",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await questionnaireAPI.updateQuestionnaireStatus(questionnaire.id, nextActive);
          await fetchQuestionnaires();
          toast.success(nextActive ? 'Questionnaire activated successfully!' : 'Questionnaire deactivated successfully!');
        } catch (err) {
          toast.error('Error updating questionnaire status: ' + err.message);
        }
      }
    });
  };

  const handleAssignToClasses = async () => {
    try {
      const existingClassIds = selectedQuestionnaire?.assignedClassIds || [];
      const toAdd = selectedClasses.filter(id => !existingClassIds.includes(id));
      const toRemove = existingClassIds.filter(id => !selectedClasses.includes(id));

      if (toAdd.length > 0) {
        await questionnaireAPI.assignToClasses(selectedQuestionnaire.id, toAdd);
      }

      if (toRemove.length > 0) {
        await questionnaireAPI.unassignFromClasses(selectedQuestionnaire.id, toRemove);
      }

      toast.success('Class assignments updated successfully!');
      setShowAssignModal(false);
      setSelectedClasses([]);
      await fetchQuestionnaires();
    } catch (err) {
      toast.error('Error updating class assignments: ' + err.message);
    }
  };

  const handleDuplicateQuestionnaire = async (questionnaire) => {
    try {
      await questionnaireAPI.duplicateQuestionnaire(questionnaire.id);
      toast.success('Questionnaire duplicated successfully!');
      await fetchQuestionnaires();
    } catch (err) {
      toast.error('Error duplicating questionnaire: ' + err.message);
    }
  };

  const openAssignModal = (questionnaire) => {
    setSelectedQuestionnaire(questionnaire);
    setSelectedClasses(questionnaire.assignedClassIds || []);
    setShowAssignModal(true);
  };

  const openDetailsModal = (id) => {
    setSelectedQuestionnaireId(id);
    setShowDetailsModal(true);
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString();
  };

  const formatDeadline = (dateTimeString) => {
    if (!dateTimeString) return 'No deadline';
    return new Date(dateTimeString).toLocaleString([], {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h1>Questionnaires</h1>
          <button className="btn" onClick={() => navigate('/teacher/questionnaires/create')}>
            + Create New Questionnaire
          </button>
        </div>

        {!googleLinked && (
          <div className="alert-warning">
            <strong>⚠️ Google Account Not Linked</strong>
            <p>Please link your Google account in the Profile page to create questionnaires.</p>
          </div>
        )}

        {error && (
          <div className="alert-danger">
            {error}
          </div>
        )}

        <div className="section">
          <h2>Your Questionnaires</h2>
          {loading ? (
            <p>Loading questionnaires...</p>
          ) : questionnaires.length === 0 ? (
            <p>No questionnaires created yet. Click "Create New Questionnaire" to get started.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Assigned Class</th>
                  <th>Target</th>
                  <th>Created Date</th>
                  <th>Deadline</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {questionnaires.map((q) => (
                  <tr key={q.id}>
                    <td>{q.title}</td>
                    <td>
                      {q.assignedClassNames && q.assignedClassNames.length > 0
                        ? q.assignedClassNames.join(', ')
                        : 'Not assigned'}
                    </td>
                    <td>
                      <span style={{
                        padding: '3px 10px',
                        borderRadius: '12px',
                        fontSize: '11px',
                        fontWeight: '700',
                        background: q.target === 'ADVISER' ? '#cce5ff' : '#fff3cd',
                        color: q.target === 'ADVISER' ? '#004085' : '#856404',
                      }}>
                        {q.target === 'ADVISER' ? 'Adviser' : 'Student'}
                      </span>
                    </td>
                    <td>{formatDate(q.createdAt)}</td>
                    <td style={{ whiteSpace: 'nowrap', color: q.deadlineAt ? 'inherit' : 'var(--dtm-muted)' }}>
                      {formatDeadline(q.deadlineAt)}
                    </td>
                    <td>
                      <button
                        type="button"
                        className={`status-badge status-toggle ${q.isActive ? 'status-active' : 'status-inactive'}`}
                        onClick={() => handleToggleQuestionnaireStatus(q)}
                        title={q.isActive ? 'Click to deactivate' : 'Click to activate'}
                      >
                        {q.isActive ? 'Active' : 'Inactive'}
                      </button>
                    </td>
                    <td>
                      <div className="action-buttons">
                        <button
                          className="btn btn-sm"
                          onClick={() => openDetailsModal(q.id)}
                        >
                          View Details
                        </button>
                        <button
                          className="btn btn-sm"
                          onClick={() => handleDuplicateQuestionnaire(q)}
                        >
                          Duplicate
                        </button>
                        <button
                          className="btn btn-sm btn-assign"
                          onClick={() => openAssignModal(q)}
                          disabled={q.isLocked}
                          title={q.isLocked ? "Cannot assign - questionnaire is locked" : "Assign to classes"}
                        >
                          Assign
                        </button>
                        <button
                          className="btn btn-sm btn-danger"
                          onClick={() => handleDeleteQuestionnaire(q.id)}
                          disabled={q.isLocked}
                          title={q.isLocked ? "Cannot delete - questionnaire is locked" : "Delete"}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Assign to Classes Modal */}
        {showAssignModal && selectedQuestionnaire && (
          <div className="modal-overlay" onClick={() => setShowAssignModal(false)}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <h2 className="assign-modal-title">Assign Questionnaire to Classes</h2>
              <p className="assign-modal-subtitle"><strong>{selectedQuestionnaire.title}</strong></p>

              <div className="form-group">
                <div className="assign-toolbar">
                  <label>Select Classes</label>
                  <div className="assign-toolbar-actions">
                    <span className="selection-count">{selectedClasses.length} selected</span>
                    <button
                      type="button"
                      className="link-btn"
                      onClick={() => setSelectedClasses(classes.map((c) => c.id))}
                    >
                      Select all
                    </button>
                    <button
                      type="button"
                      className="link-btn"
                      onClick={() => setSelectedClasses([])}
                    >
                      Clear
                    </button>
                  </div>
                </div>
                {classes.length === 0 ? (
                  <p>No classes available. Please create classes first.</p>
                ) : (
                  <div className="class-checklist">
                    {classes.map((cls) => (
                      <div key={cls.id} className="class-check-item">
                        <label className="class-check-label">
                          <input
                            type="checkbox"
                            checked={selectedClasses.includes(cls.id)}
                            onChange={(e) => {
                              if (e.target.checked) {
                                setSelectedClasses([...selectedClasses, cls.id]);
                              } else {
                                setSelectedClasses(selectedClasses.filter(id => id !== cls.id));
                              }
                            }}
                            className="class-checkbox"
                          />
                          <span className="class-check-text">
                            <span className="class-check-name">{cls.name} {cls.section ? `- ${cls.section}` : ''}</span>
                            <span className="class-check-meta">School Year: {cls.schoolYear}</span>
                          </span>
                        </label>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="form-actions">
                <button onClick={handleAssignToClasses} className="btn btn-primary">Save Class Assignments</button>
                <button onClick={() => { setShowAssignModal(false); setSelectedClasses([]); }} className="btn btn-secondary">
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Confirm Modal */}
        <ConfirmModal
          isOpen={confirmModal.isOpen}
          title={confirmModal.title}
          message={confirmModal.message}
          onConfirm={confirmModal.onConfirm}
          onCancel={() => setConfirmModal({ ...confirmModal, isOpen: false })}
          isDanger={true}
        />

        {/* Questionnaire Details & Edit Modal */}
        <QuestionnaireDetailModal
          isOpen={showDetailsModal}
          onClose={() => setShowDetailsModal(false)}
          questionnaireId={selectedQuestionnaireId}
          onUpdate={fetchQuestionnaires}
        />
      </div>


    </div>
  );
};

export default Questionnaires;

