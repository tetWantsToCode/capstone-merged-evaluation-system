import React, { useState, useEffect } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { questionnaireAPI, classAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";
import "./Teacher.css";

const Questionnaires = () => {
  const toast = useToast();
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
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [googleLinked, setGoogleLinked] = useState(false);

  const [formData, setFormData] = useState({
    title: "",
    description: "",
    questions: []
  });

  const [newQuestion, setNewQuestion] = useState({
    questionText: "",
    questionType: "NUMERIC_SCALE",
    minScore: 1,
    maxScore: 5,
    choices: [],
    orderIndex: 0
  });

  const [selectedClasses, setSelectedClasses] = useState([]);

  useEffect(() => {
    fetchQuestionnaires();
    fetchClasses();
    checkGoogleLink();
  }, []);

  const checkGoogleLink = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/google-auth/status', {
        headers: {
          'Authorization': `Bearer ${JSON.parse(localStorage.getItem('user')).token}`
        }
      });
      const data = await response.json();
      setGoogleLinked(data.isLinked);
    } catch (err) {
      toast.error('Error checking Google link status');
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
        const teacherClasses = data.filter(c => c.teacherId === user.id);
        setClasses(teacherClasses);
      } else {
        setClasses(data);
      }
    } catch (err) {
      toast.error('Error fetching classes');
    }
  };

  const handleCreateQuestionnaire = async (e) => {
    e.preventDefault();
    
    if (!googleLinked) {
      alert('Please link your Google account in the Profile page first!');
      return;
    }

    try {
      toast.info('Creating questionnaire...');
      await questionnaireAPI.createQuestionnaire(formData);
      toast.success('Questionnaire created successfully!');
      setShowCreateModal(false);
      resetForm();
      fetchQuestionnaires();
    } catch (err) {
      toast.error('Error creating questionnaire: ' + err.message);
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

  const handleAddQuestion = () => {
    if (!newQuestion.questionText.trim()) {
      alert('Please enter a question text');
      return;
    }

    // Validate multiple choice questions have at least 2 choices
    if (newQuestion.questionType === 'MULTIPLE_CHOICE') {
      if (!newQuestion.choices || newQuestion.choices.length < 2) {
        alert('Multiple choice questions must have at least 2 choices');
        return;
      }
    }

    setFormData({
      ...formData,
      questions: [...formData.questions, { ...newQuestion, orderIndex: formData.questions.length }]
    });

    setNewQuestion({
      questionText: "",
      questionType: "NUMERIC_SCALE",
      minScore: 1,
      maxScore: 5,
      choices: [],
      orderIndex: 0
    });
  };

  const handleRemoveQuestion = (index) => {
    const updatedQuestions = formData.questions.filter((_, i) => i !== index);
    setFormData({ ...formData, questions: updatedQuestions });
  };

  const handleAssignToClasses = async () => {
    if (selectedClasses.length === 0) {
      alert('Please select at least one class');
      return;
    }

    try {
      await questionnaireAPI.assignToClasses(selectedQuestionnaire.id, selectedClasses);
      alert('Questionnaire assigned to classes successfully!');
      setShowAssignModal(false);
      setSelectedClasses([]);
      fetchQuestionnaires();
    } catch (err) {
      alert('Error assigning questionnaire: ' + err.message);
    }
  };

  const resetForm = () => {
    setFormData({
      title: "",
      description: "",
      questions: []
    });
    setNewQuestion({
      questionText: "",
      questionType: "NUMERIC_SCALE",
      minScore: 1,
      maxScore: 5,
      choices: [],
      orderIndex: 0
    });
  };

  const openAssignModal = (questionnaire) => {
    setSelectedQuestionnaire(questionnaire);
    setSelectedClasses(questionnaire.assignedClassIds || []);
    setShowAssignModal(true);
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString();
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h1>Questionnaires</h1>
          <button className="btn" onClick={() => setShowCreateModal(true)}>
            + Create New Questionnaire
          </button>
        </div>

        {!googleLinked && (
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#fff3cd', 
            border: '1px solid #ffc107',
            borderRadius: '5px',
            marginBottom: '20px'
          }}>
            <strong>⚠️ Google Account Not Linked</strong>
            <p>Please link your Google account in the Profile page to create questionnaires.</p>
          </div>
        )}

        {error && (
          <div style={{ 
            padding: '15px', 
            backgroundColor: '#f8d7da', 
            border: '1px solid #f5c6cb',
            borderRadius: '5px',
            marginBottom: '20px',
            color: '#721c24'
          }}>
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
                  <th>Description</th>
                  <th>Questions</th>
                  <th>Assigned Classes</th>
                  <th>Created Date</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {questionnaires.map((q) => (
                  <tr key={q.id}>
                    <td>{q.title}</td>
                    <td>{q.description || 'N/A'}</td>
                    <td>{q.questionCount}</td>
                    <td>
                      {q.assignedClassNames && q.assignedClassNames.length > 0
                        ? q.assignedClassNames.join(', ')
                        : 'Not assigned'}
                    </td>
                    <td>{formatDate(q.createdAt)}</td>
                    <td>
                      <span style={{ 
                        padding: '5px 10px',
                        borderRadius: '5px',
                        backgroundColor: q.isActive ? '#d4edda' : '#f8d7da',
                        color: q.isActive ? '#155724' : '#721c24'
                      }}>
                        {q.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <button 
                        className="btn" 
                        onClick={() => window.open(q.googleFormUrl, '_blank')}
                        style={{ marginRight: '5px', fontSize: '12px' }}
                      >
                        View Form
                      </button>
                      <button 
                        className="btn btn-assign" 
                        onClick={() => openAssignModal(q)}
                        style={{ marginRight: '5px', fontSize: '12px' }}
                      >
                        Assign
                      </button>
                      <button 
                        className="btn" 
                        onClick={() => handleDeleteQuestionnaire(q.id)}
                        style={{ backgroundColor: '#dc3545', fontSize: '12px' }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Create Questionnaire Modal */}
        {showCreateModal && (
          <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '800px', maxHeight: '90vh', overflowY: 'auto' }}>
              <h2>Create New Questionnaire</h2>
              <form onSubmit={handleCreateQuestionnaire}>
                <div className="form-group">
                  <label>Title *</label>
                  <input
                    type="text"
                    value={formData.title}
                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Description</label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    rows="3"
                  />
                </div>

                <div className="form-group">
                  <h3>Questions ({formData.questions.length} added)</h3>
                  
                  {formData.questions.length > 0 && (
                    <div style={{ marginBottom: '15px', padding: '10px', backgroundColor: '#e8f5e8', borderRadius: '5px' }}>
                      <small style={{ color: '#155724' }}>
                        ✅ Questions added below. Click "Create Questionnaire" when done adding all questions.
                      </small>
                    </div>
                  )}
                  
                  {formData.questions.map((q, index) => (
                    <div key={index} style={{ 
                      padding: '10px', 
                      border: '1px solid #ddd', 
                      borderRadius: '5px',
                      marginBottom: '10px',
                      backgroundColor: '#f9f9f9'
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                        <div style={{ flex: 1 }}>
                          <strong>Q{index + 1}:</strong> {q.questionText}
                          <br />
                          <small>Type: {q.questionType}</small>
                          {(q.questionType === 'NUMERIC_SCALE' || q.questionType === 'RATING') && (
                            <small> | Range: {q.minScore} - {q.maxScore}</small>
                          )}
                          {q.questionType === 'MULTIPLE_CHOICE' && q.choices && q.choices.length > 0 && (
                            <div style={{ marginTop: '5px' }}>
                              <small>Choices: {q.choices.join(', ')}</small>
                            </div>
                          )}
                        </div>
                        <button 
                          type="button"
                          onClick={() => handleRemoveQuestion(index)}
                          style={{ 
                            backgroundColor: '#dc3545', 
                            color: 'white',
                            border: 'none',
                            padding: '5px 10px',
                            borderRadius: '3px',
                            cursor: 'pointer'
                          }}
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  ))}

                  <div style={{ 
                    padding: '15px', 
                    border: '2px dashed #ddd', 
                    borderRadius: '5px',
                    marginTop: '10px'
                  }}>
                    <h4>Add New Question</h4>
                    <div className="form-group">
                      <label>Question Text *</label>
                      <input
                        type="text"
                        value={newQuestion.questionText}
                        onChange={(e) => setNewQuestion({ ...newQuestion, questionText: e.target.value })}
                      />
                    </div>

                    <div className="form-group">
                      <label>Question Type</label>
                      <select
                        value={newQuestion.questionType}
                        onChange={(e) => setNewQuestion({ ...newQuestion, questionType: e.target.value })}
                      >
                        <option value="NUMERIC_SCALE">Numeric Scale</option>
                        <option value="RATING">Rating</option>
                        <option value="TEXT">Text Response</option>
                        <option value="MULTIPLE_CHOICE">Multiple Choice</option>
                      </select>
                    </div>

                    {(newQuestion.questionType === 'NUMERIC_SCALE' || newQuestion.questionType === 'RATING') && (
                      <div style={{ display: 'flex', gap: '10px' }}>
                        <div className="form-group" style={{ flex: 1 }}>
                          <label>Min Score</label>
                          <input
                            type="number"
                            value={newQuestion.minScore}
                            onChange={(e) => setNewQuestion({ ...newQuestion, minScore: parseInt(e.target.value) })}
                            min="0"
                          />
                        </div>
                        <div className="form-group" style={{ flex: 1 }}>
                          <label>Max Score</label>
                          <input
                            type="number"
                            value={newQuestion.maxScore}
                            onChange={(e) => setNewQuestion({ ...newQuestion, maxScore: parseInt(e.target.value) })}
                            min="1"
                          />
                        </div>
                      </div>
                    )}

                    {newQuestion.questionType === 'MULTIPLE_CHOICE' && (
                      <div className="form-group">
                        <label>Choices</label>
                        {newQuestion.choices.map((choice, index) => (
                          <div key={index} style={{ 
                            display: 'flex', 
                            gap: '10px', 
                            marginBottom: '10px',
                            alignItems: 'center'
                          }}>
                            <span style={{ 
                              minWidth: '30px', 
                              fontWeight: 'bold',
                              color: '#666'
                            }}>
                              {String.fromCharCode(65 + index)}.
                            </span>
                            <input
                              type="text"
                              value={choice}
                              onChange={(e) => {
                                const updatedChoices = [...newQuestion.choices];
                                updatedChoices[index] = e.target.value;
                                setNewQuestion({ ...newQuestion, choices: updatedChoices });
                              }}
                              placeholder={`Choice ${index + 1}`}
                              style={{ 
                                flex: 1,
                                padding: '8px 12px',
                                border: '1px solid #ddd',
                                borderRadius: '4px',
                                fontSize: '14px'
                              }}
                            />
                            {newQuestion.choices.length > 2 && (
                              <button
                                type="button"
                                onClick={() => {
                                  const updatedChoices = newQuestion.choices.filter((_, i) => i !== index);
                                  setNewQuestion({ ...newQuestion, choices: updatedChoices });
                                }}
                                style={{
                                  backgroundColor: '#dc3545',
                                  color: 'white',
                                  border: 'none',
                                  padding: '6px 10px',
                                  borderRadius: '4px',
                                  cursor: 'pointer',
                                  fontSize: '12px'
                                }}
                                title="Remove choice"
                              >
                                ×
                              </button>
                            )}
                          </div>
                        ))}
                        
                        <button
                          type="button"
                          onClick={() => {
                            setNewQuestion({ 
                              ...newQuestion, 
                              choices: [...newQuestion.choices, ''] 
                            });
                          }}
                          style={{
                            backgroundColor: '#28a745',
                            color: 'white',
                            border: 'none',
                            padding: '8px 16px',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '14px',
                            marginTop: '5px'
                          }}
                        >
                          + Add Choice
                        </button>
                        
                        <small style={{ 
                          color: '#666', 
                          display: 'block',
                          marginTop: '8px'
                        }}>
                          Minimum 2 choices required. Click "+ Add Choice" for more options.
                        </small>
                      </div>
                    )}

                    <button type="button" onClick={handleAddQuestion} className="btn" style={{ marginTop: '10px' }}>
                      + Add Question
                    </button>
                  </div>
                </div>

                <div className="form-actions" style={{ marginTop: '20px' }}>
                  <button type="submit" className="btn">Create Questionnaire</button>
                  <button type="button" onClick={() => { setShowCreateModal(false); resetForm(); }} className="btn" style={{ marginLeft: '10px', backgroundColor: '#6c757d' }}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Assign to Classes Modal */}
        {showAssignModal && selectedQuestionnaire && (
          <div className="modal-overlay" onClick={() => setShowAssignModal(false)}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
              <h2>Assign Questionnaire to Classes</h2>
              <p><strong>{selectedQuestionnaire.title}</strong></p>
              
              <div className="form-group">
                <label>Select Classes:</label>
                {classes.length === 0 ? (
                  <p>No classes available. Please create classes first.</p>
                ) : (
                  <div style={{ maxHeight: '300px', overflowY: 'auto', border: '1px solid #ddd', padding: '10px', borderRadius: '5px' }}>
                    {classes.map((cls) => (
                      <div key={cls.id} style={{ marginBottom: '10px' }}>
                        <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
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
                            style={{ marginRight: '10px' }}
                          />
                          <span>{cls.name} {cls.section ? `- ${cls.section}` : ''} ({cls.schoolYear})</span>
                        </label>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="form-actions" style={{ marginTop: '20px' }}>
                <button onClick={handleAssignToClasses} className="btn">Assign to Selected Classes</button>
                <button onClick={() => { setShowAssignModal(false); setSelectedClasses([]); }} className="btn" style={{ marginLeft: '10px', backgroundColor: '#6c757d' }}>
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
      </div>

      <style dangerouslySetInnerHTML={{__html: `
        .modal-overlay {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background-color: rgba(0, 0, 0, 0.5);
          display: flex;
          justify-content: center;
          align-items: center;
          z-index: 1000;
        }

        .modal-content {
          background: white;
          padding: 30px;
          border-radius: 10px;
          width: 90%;
          max-width: 600px;
        }

        .form-group {
          margin-bottom: 15px;
        }

        .form-group label {
          display: block;
          margin-bottom: 5px;
          font-weight: bold;
        }

        .form-group input,
        .form-group textarea,
        .form-group select {
          width: 100%;
          padding: 8px;
          border: 1px solid #ddd;
          border-radius: 5px;
          font-size: 14px;
        }

        .form-actions {
          display: flex;
          gap: 10px;
        }
      `}} />
    </div>
  );
};

export default Questionnaires;

