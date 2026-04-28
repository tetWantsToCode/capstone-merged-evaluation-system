import React, { useState, useEffect } from "react";
import { questionnaireAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";
import "./QuestionnaireTwoColumn.css";

const QuestionnaireDetailModal = ({ isOpen, onClose, questionnaireId, onUpdate }) => {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [questionnaire, setQuestionnaire] = useState(null);
  const [formData, setFormData] = useState({
    title: "",
    description: "",
    deadlineAt: "",
    sections: [],
    target: "ADVISER"
  });

  const [activeSectionIndex, setActiveSectionIndex] = useState(0);
  const [newQuestion, setNewQuestion] = useState({
    questionText: "",
    questionType: "NUMERIC_SCALE",
    minScore: 1,
    maxScore: 5,
    choices: [],
    correctAnswer: "",
    pointsValue: 1
  });

  useEffect(() => {
    if (isOpen && questionnaireId) {
      fetchQuestionnaireDetails();
    } else {
      setIsEditing(false);
      setQuestionnaire(null);
    }
  }, [isOpen, questionnaireId]);

  const fetchQuestionnaireDetails = async () => {
    try {
      setLoading(true);
      const data = await questionnaireAPI.getQuestionnaireById(questionnaireId);
      setQuestionnaire(data);
      let finalSections = data.sections || [];
      if (finalSections.length === 0) {
        finalSections = [{
          sectionTitle: "General",
          sectionDescription: "",
          orderIndex: 0,
          items: data.items || []
        }];
      }

      setFormData({
        title: data.title || "",
        description: data.description || "",
        deadlineAt: data.deadlineAt ? data.deadlineAt.slice(0, 16) : "",
        sections: finalSections,
        target: data.target || "ADVISER"
      });
      setActiveSectionIndex(0);
    } catch (err) {
      toast.error("Failed to fetch questionnaire details: " + err.message);
      onClose();
    } finally {
      setLoading(false);
    }
  };

  const handleSaveChanges = async (e) => {
    e.preventDefault();
    if (!formData.title.trim()) {
      toast.error("Title is required");
      return;
    }

    const totalQuestions = formData.sections.reduce((sum, sec) => sum + (sec.items?.length || 0), 0);
    if (totalQuestions === 0) {
      toast.error("Please add at least one question");
      return;
    }

    try {
      setLoading(true);
      const payload = {
        ...formData,
        deadlineAt: formData.deadlineAt || null,
      };
      await questionnaireAPI.updateQuestionnaire(questionnaireId, payload);
      toast.success("Questionnaire updated successfully!");
      setIsEditing(false);
      fetchQuestionnaireDetails();
      if (onUpdate) onUpdate();
    } catch (err) {
      toast.error("Error updating questionnaire: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAddQuestion = () => {
    if (!newQuestion.questionText.trim()) {
      toast.error("Please enter question text");
      return;
    }

    const updatedSections = [...formData.sections];
    if (!updatedSections[activeSectionIndex].items) {
      updatedSections[activeSectionIndex].items = [];
    }
    updatedSections[activeSectionIndex].items.push({ ...newQuestion });
    setFormData({ ...formData, sections: updatedSections });

    setNewQuestion({
      questionText: "",
      questionType: "NUMERIC_SCALE",
      minScore: 1,
      maxScore: 5,
      choices: [],
      correctAnswer: "",
      pointsValue: 1
    });
    toast.success("Question added!");
  };

  const handleRemoveQuestion = (index) => {
    const updatedSections = [...formData.sections];
    updatedSections[activeSectionIndex].items = updatedSections[activeSectionIndex].items.filter((_, i) => i !== index);
    setFormData({ ...formData, sections: updatedSections });
  };

  const handleAddSection = () => {
    const newSectionNumber = formData.sections.length + 1;
    setFormData({
      ...formData,
      sections: [
        ...formData.sections,
        {
          sectionTitle: `Section ${newSectionNumber}`,
          sectionDescription: "",
          orderIndex: formData.sections.length,
          items: []
        }
      ]
    });
    setActiveSectionIndex(formData.sections.length);
  };

  const handleRemoveSection = (index) => {
    if (formData.sections.length <= 1) {
      toast.error("Cannot delete the only section");
      return;
    }
    const updatedSections = formData.sections.filter((_, i) => i !== index);
    setFormData({ ...formData, sections: updatedSections });
    if (activeSectionIndex >= updatedSections.length) {
      setActiveSectionIndex(updatedSections.length - 1);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return "N/A";
    return new Date(dateString).toLocaleDateString();
  };

  const formatDeadline = (dateTimeString) => {
    if (!dateTimeString) return "No deadline";
    return new Date(dateTimeString).toLocaleString([], {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content questionnaire-detail-modal" onClick={(e) => e.stopPropagation()} style={{ width: '95%', maxWidth: '1000px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="modal-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '15px' }}>
          <h2 style={{ margin: 0, color: 'var(--dtm-gold)' }}>
            {isEditing ? "Edit Questionnaire" : "Questionnaire Details"}
          </h2>
          <button className="close-btn" onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--dtm-muted)', fontSize: '24px', cursor: 'pointer' }}>&times;</button>
        </div>

        {loading || !questionnaire ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            {loading ? 'Loading details...' : 'Failed to load details. Please try again.'}
          </div>
        ) : (
          <>
            {!isEditing ? (
              /* VIEW MODE */
              <div className="view-mode-container">
                <div className="detail-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '30px' }}>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Title</label>
                    <div style={{ fontSize: '18px', fontWeight: '600' }}>{questionnaire.title}</div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Target</label>
                    <div>
                      <span style={{
                        padding: '4px 12px',
                        borderRadius: '12px',
                        fontSize: '12px',
                        fontWeight: '700',
                        background: questionnaire.target === 'ADVISER' ? '#cce5ff' : '#fff3cd',
                        color: questionnaire.target === 'ADVISER' ? '#004085' : '#856404',
                      }}>
                        {questionnaire.target === 'ADVISER' ? 'Adviser' : 'Student'}
                      </span>
                    </div>
                  </div>
                  <div className="detail-item" style={{ gridColumn: 'span 2' }}>
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Description</label>
                    <div style={{ lineHeight: '1.5', opacity: 0.9 }}>{questionnaire.description || "No description provided."}</div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Question Count</label>
                    <div style={{ fontSize: '16px' }}>{questionnaire.questionCount} questions</div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Assigned Classes</label>
                    <div style={{ fontSize: '14px' }}>
                      {questionnaire.assignedClassNames && questionnaire.assignedClassNames.length > 0
                        ? questionnaire.assignedClassNames.join(', ')
                        : 'Not assigned to any classes'}
                    </div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Created Date</label>
                    <div style={{ fontSize: '14px' }}>{formatDate(questionnaire.createdAt)}</div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Deadline</label>
                    <div style={{ fontSize: '14px', color: questionnaire.deadlineAt ? 'inherit' : 'var(--dtm-muted)' }}>
                      {formatDeadline(questionnaire.deadlineAt)}
                    </div>
                  </div>
                  <div className="detail-item">
                    <label style={{ display: 'block', fontSize: '12px', color: 'var(--dtm-muted)', textTransform: 'uppercase', marginBottom: '5px' }}>Status</label>
                    <div>
                      <span className={`status-badge ${questionnaire.isActive ? 'status-active' : 'status-inactive'}`}>
                        {questionnaire.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="view-questions-section" style={{ borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '20px', marginBottom: '20px' }}>
                  <h3 style={{ fontSize: '16px', color: 'var(--dtm-gold)', marginBottom: '15px' }}>Questions</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                    {formData.sections.map((section, sIdx) => (
                      <div key={sIdx} className="view-section" style={{ marginBottom: '10px' }}>
                        {formData.sections.length > 1 && (
                          <h4 style={{ fontSize: '14px', color: 'var(--dtm-gold)', borderBottom: '1px solid rgba(242, 201, 76, 0.2)', paddingBottom: '5px', marginBottom: '10px' }}>
                            {section.sectionTitle}
                          </h4>
                        )}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                          {section.items?.map((q, qIdx) => (
                            <div key={qIdx} style={{ background: 'rgba(255,255,255,0.03)', padding: '10px', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.05)' }}>
                              <div style={{ fontSize: '13px', fontWeight: '500' }}>{qIdx + 1}. {q.questionText}</div>
                              <div style={{ fontSize: '11px', color: 'var(--dtm-muted)', marginTop: '4px' }}>
                                Type: {q.questionType} 
                                {(q.questionType === 'NUMERIC_SCALE' || q.questionType === 'RATING') && ` | Range: ${q.minScore} - ${q.maxScore}`}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '20px' }}>
                  <button className="btn btn-secondary" onClick={() => setIsEditing(true)}>Edit Details & Questions</button>
                  <button className="btn btn-primary" onClick={() => window.open(questionnaire.googleFormUrl, '_blank')}>View Form</button>
                  <button className="btn btn-secondary" onClick={onClose}>Close</button>
                </div>
              </div>
            ) : (
              /* EDIT MODE */
              <div className="edit-mode-container" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <form onSubmit={handleSaveChanges} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
                    {/* LEFT COLUMN - SECTION EDITOR & QUESTIONS */}
                    <div className="questionnaire-preview-panel" style={{ display: 'flex', flexDirection: 'column' }}>
                      {formData.sections[activeSectionIndex] && (
                        <>
                          <div className="section-editor">
                            <div className="form-group" style={{ marginBottom: '8px' }}>
                              <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--dtm-gold)', margin: 0 }}>Section Title</label>
                              <input
                                type="text"
                                value={formData.sections[activeSectionIndex].sectionTitle}
                                onChange={(e) => {
                                  const updatedSections = [...formData.sections];
                                  updatedSections[activeSectionIndex].sectionTitle = e.target.value;
                                  setFormData({ ...formData, sections: updatedSections });
                                }}
                                placeholder="Section Title"
                                style={{ padding: '8px 10px', fontSize: '11px', width: '100%', boxSizing: 'border-box' }}
                              />
                            </div>
                            <div className="form-group" style={{ marginBottom: '8px' }}>
                              <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--dtm-gold)', margin: 0 }}>Section Description</label>
                              <textarea
                                value={formData.sections[activeSectionIndex].sectionDescription || ''}
                                onChange={(e) => {
                                  const updatedSections = [...formData.sections];
                                  updatedSections[activeSectionIndex].sectionDescription = e.target.value;
                                  setFormData({ ...formData, sections: updatedSections });
                                }}
                                rows="2"
                                placeholder="Describe this section (optional)"
                                style={{ padding: '8px 10px', fontSize: '11px', width: '100%', boxSizing: 'border-box' }}
                              />
                            </div>
                          </div>

                          <div style={{
                            padding: '10px',
                            backgroundColor: 'rgba(138, 21, 31, 0.2)',
                            borderLeft: '3px solid var(--dtm-gold)',
                            borderRadius: '4px',
                            marginBottom: '8px',
                            fontSize: '10px',
                            color: 'var(--dtm-text)'
                          }}>
                            📄 {formData.sections.length} page(s) • {formData.sections[activeSectionIndex].items?.length || 0} question(s)
                          </div>

                          {/* Section Navigation */}
                          <div style={{ marginBottom: '8px' }}>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                              {formData.sections.map((section, idx) => (
                                <div key={idx} style={{ position: 'relative' }}>
                                  <button
                                    type="button"
                                    onClick={() => setActiveSectionIndex(idx)}
                                    style={{
                                      padding: '6px 10px',
                                      backgroundColor: activeSectionIndex === idx ? 'linear-gradient(135deg, rgba(138, 21, 31, 0.8), rgba(138, 21, 31, 0.5))' : 'rgba(138, 21, 31, 0.3)',
                                      color: activeSectionIndex === idx ? 'var(--dtm-gold)' : 'var(--dtm-muted)',
                                      border: activeSectionIndex === idx ? '1px solid var(--dtm-gold)' : '1px solid rgba(242, 201, 76, 0.2)',
                                      borderRadius: '4px',
                                      cursor: 'pointer',
                                      fontWeight: activeSectionIndex === idx ? '600' : '500',
                                      fontSize: '11px'
                                    }}
                                  >
                                    {section.sectionTitle || `Section ${idx + 1}`}
                                  </button>
                                  {formData.sections.length > 1 && activeSectionIndex === idx && (
                                    <button
                                      type="button"
                                      onClick={(e) => { e.stopPropagation(); handleRemoveSection(idx); }}
                                      style={{ position: 'absolute', right: '-8px', top: '-8px', width: '18px', height: '18px', borderRadius: '50%', background: 'var(--dtm-maroon)', color: 'white', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px' }}
                                      title="Delete Section"
                                    >
                                      &times;
                                    </button>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>

                          {/* Questions List */}
                          <div style={{ borderTop: '1px solid rgba(255, 255, 255, 0.1)', paddingTop: '8px', flex: 1, display: 'flex', flexDirection: 'column' }}>
                            <h4 style={{ margin: '0 0 8px 0', fontSize: '12px', fontWeight: '600', color: 'var(--dtm-gold)' }}>
                              Questions in this page
                            </h4>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '400px', overflowY: 'auto', paddingRight: '5px' }}>
                              {formData.sections[activeSectionIndex].items?.map((q, qIdx) => (
                                <div key={qIdx} style={{
                                  background: 'rgba(255, 255, 255, 0.04)',
                                  borderLeft: '3px solid var(--dtm-gold)',
                                  borderRadius: '4px',
                                  padding: '12px',
                                  display: 'flex',
                                  flexDirection: 'column',
                                  gap: '8px'
                                }}>
                                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <span style={{ fontSize: '11px', fontWeight: '600', color: 'var(--dtm-gold)' }}>Q{qIdx + 1}</span>
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-danger"
                                      onClick={() => handleRemoveQuestion(qIdx)}
                                      style={{ padding: '4px 8px', fontSize: '10px' }}
                                      title="Delete question"
                                    >
                                      ×
                                    </button>
                                  </div>
                                  
                                  <input
                                    type="text"
                                    value={q.questionText}
                                    onChange={(e) => {
                                      const updatedSections = [...formData.sections];
                                      updatedSections[activeSectionIndex].items[qIdx].questionText = e.target.value;
                                      setFormData({ ...formData, sections: updatedSections });
                                    }}
                                    placeholder="Question text"
                                    style={{ fontSize: '12px', padding: '6px' }}
                                  />
                                  
                                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center' }}>
                                    <select
                                      value={q.questionType}
                                      onChange={(e) => {
                                        const updatedSections = [...formData.sections];
                                        updatedSections[activeSectionIndex].items[qIdx].questionType = e.target.value;
                                        setFormData({ ...formData, sections: updatedSections });
                                      }}
                                      style={{ fontSize: '11px', minWidth: '130px', padding: '6px' }}
                                    >
                                      <option value="NUMERIC_SCALE">Numeric Scale</option>
                                      <option value="RATING">Rating</option>
                                      <option value="TEXT">Text Response</option>
                                      <option value="MULTIPLE_CHOICE">Multiple Choice</option>
                                    </select>
                                    
                                    {(q.questionType === 'NUMERIC_SCALE' || q.questionType === 'RATING') && (
                                      <div style={{ display: 'flex', gap: '6px', alignItems: 'center', background: 'rgba(0,0,0,0.2)', padding: '4px 8px', borderRadius: '4px' }}>
                                        <span style={{ fontSize: '11px', color: 'var(--dtm-muted)' }}>Range:</span>
                                        <input 
                                          type="number" 
                                          value={q.minScore} 
                                          onChange={(e) => {
                                            const updatedSections = [...formData.sections];
                                            updatedSections[activeSectionIndex].items[qIdx].minScore = parseInt(e.target.value);
                                            setFormData({ ...formData, sections: updatedSections });
                                          }}
                                          style={{ width: '45px', fontSize: '11px', padding: '4px', textAlign: 'center' }}
                                        />
                                        <span style={{ fontSize: '11px', color: 'var(--dtm-muted)' }}>to</span>
                                        <input 
                                          type="number" 
                                          value={q.maxScore} 
                                          onChange={(e) => {
                                            const updatedSections = [...formData.sections];
                                            updatedSections[activeSectionIndex].items[qIdx].maxScore = parseInt(e.target.value);
                                            setFormData({ ...formData, sections: updatedSections });
                                          }}
                                          style={{ width: '45px', fontSize: '11px', padding: '4px', textAlign: 'center' }}
                                        />
                                      </div>
                                    )}
                                  </div>
                                </div>
                              ))}
                              {(!formData.sections[activeSectionIndex].items || formData.sections[activeSectionIndex].items.length === 0) && (
                                <p style={{ fontSize: '10px', color: 'var(--dtm-muted)', marginBottom: 0, fontStyle: 'italic' }}>
                                  No questions yet. Add one on the right →
                                </p>
                              )}
                            </div>
                          </div>
                        </>
                      )}
                    </div>

                    {/* RIGHT COLUMN - CREATION FORM */}
                    <div className="questionnaire-creation-panel" style={{ display: 'flex', flexDirection: 'column' }}>
                      <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', marginBottom: '0px', position: 'relative' }}>
                        <div className="form-group" style={{ flex: 1 }}>
                          <label>Title *</label>
                          <input
                            type="text"
                            value={formData.title}
                            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                            required
                            placeholder="Questionnaire Title"
                            style={{ fontSize: '11px' }}
                          />
                        </div>
                        <div className="form-group" style={{ width: '120px' }}>
                          <label>Questionnaire For</label>
                          <select
                            value={formData.target}
                            onChange={(e) => setFormData({ ...formData, target: e.target.value })}
                            style={{ fontSize: '11px' }}
                          >
                            <option value="ADVISER">Adviser</option>
                            <option value="STUDENT">Student</option>
                          </select>
                        </div>
                      </div>

                      <div className="form-group" style={{ marginBottom: '10px' }}>
                        <label>Description</label>
                        <textarea
                          value={formData.description}
                          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                          rows="2"
                          placeholder="Description"
                          style={{ fontSize: '11px' }}
                        />
                      </div>

                      <div className="form-group" style={{ marginBottom: '15px' }}>
                        <label>Deadline (Optional)</label>
                        <input
                          type="datetime-local"
                          value={formData.deadlineAt}
                          onChange={(e) => setFormData({ ...formData, deadlineAt: e.target.value })}
                          style={{ fontSize: '11px' }}
                        />
                      </div>

                      <div style={{ marginBottom: '8px', marginTop: '4px' }}>
                        <h3 style={{ margin: '0 0 8px 0', fontSize: '12px', fontWeight: '600', color: 'var(--dtm-gold)' }}>Add Question</h3>
                        
                        <div className="add-question-box">
                          <div className="form-group" style={{ marginBottom: '10px' }}>
                            <label>Question Text *</label>
                            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', justifyContent: 'space-between' }}>
                              <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flex: 1 }}>
                                <input
                                  type="text"
                                  value={newQuestion.questionText}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, questionText: e.target.value })}
                                  placeholder="Ask your question..."
                                  style={{ fontSize: '11px', flex: '1 1 auto', minWidth: '150px' }}
                                />
                                <select
                                  value={newQuestion.questionType}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, questionType: e.target.value })}
                                  style={{ fontSize: '11px', width: '130px', flexShrink: 0 }}
                                >
                                  <option value="NUMERIC_SCALE">Numeric Scale</option>
                                  <option value="RATING">Rating</option>
                                  <option value="TEXT">Text Response</option>
                                  <option value="MULTIPLE_CHOICE">Multiple Choice</option>
                                </select>
                              </div>

                              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                <button
                                  type="button"
                                  onClick={handleAddQuestion}
                                  className="add-question-btn"
                                  style={{
                                    width: '32px',
                                    height: '32px',
                                    borderRadius: '50%',
                                    background: 'linear-gradient(135deg, rgba(138, 21, 31, 0.9), rgba(138, 21, 31, 0.6))',
                                    border: '1px solid rgba(242, 201, 76, 0.4)',
                                    color: 'var(--dtm-gold)',
                                    fontSize: '18px',
                                    fontWeight: 'bold',
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    lineHeight: '0',
                                    padding: '0',
                                    flexShrink: 0
                                  }}
                                  title="Add Question"
                                >
                                  +
                                </button>
                                <button
                                  type="button"
                                  onClick={handleAddSection}
                                  className="add-page-icon-btn"
                                  style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', lineHeight: '0', padding: '0', width: '32px', height: '32px' }}
                                  title="Add Section"
                                >
                                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <rect x="3" y="2" width="16" height="8" rx="1"></rect>
                                    <rect x="3" y="14" width="16" height="8" rx="1"></rect>
                                  </svg>
                                </button>
                              </div>
                            </div>
                          </div>

                          {(newQuestion.questionType === 'NUMERIC_SCALE' || newQuestion.questionType === 'RATING') && (
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '8px' }}>
                              <div className="form-group" style={{ marginBottom: 0 }}>
                                <label>Min</label>
                                <input
                                  type="number"
                                  value={newQuestion.minScore}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, minScore: parseInt(e.target.value) })}
                                  min="0"
                                  style={{ fontSize: '11px' }}
                                />
                              </div>
                              <div className="form-group" style={{ marginBottom: 0 }}>
                                <label>Max</label>
                                <input
                                  type="number"
                                  value={newQuestion.maxScore}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, maxScore: parseInt(e.target.value) })}
                                  min="1"
                                  style={{ fontSize: '11px' }}
                                />
                              </div>
                            </div>
                          )}

                          {newQuestion.questionType === 'MULTIPLE_CHOICE' && (
                            <div className="form-group">
                              <label>Choices (min. 2)</label>
                              {newQuestion.choices.map((choice, index) => (
                                <div key={index} className="choice-row">
                                  <span className="choice-label">
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
                                    placeholder={`Option ${index + 1}`}
                                    className="choice-input"
                                    style={{ fontSize: '11px' }}
                                  />
                                  {newQuestion.choices.length > 2 && (
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-danger"
                                      onClick={() => {
                                        const updatedChoices = newQuestion.choices.filter((_, i) => i !== index);
                                        setNewQuestion({ ...newQuestion, choices: updatedChoices });
                                      }}
                                      title="Remove choice"
                                      style={{ padding: '5px 8px', fontSize: '10px' }}
                                    >
                                      ×
                                    </button>
                                  )}
                                </div>
                              ))}

                              <button
                                type="button"
                                className="btn btn-sm btn-assign"
                                onClick={() => {
                                  setNewQuestion({
                                    ...newQuestion,
                                    choices: [...newQuestion.choices, '']
                                  });
                                }}
                                style={{ marginTop: '4px', padding: '5px 10px', fontSize: '10px' }}
                              >
                                + Add Option
                              </button>
                            </div>
                          )}

                          <div style={{ marginTop: '8px', borderTop: '1px solid rgba(255, 255, 255, 0.1)', paddingTop: '4px' }}>
                            <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start', marginBottom: '4px' }}>
                              <div className="form-group" style={{ flex: 1, margin: 0 }}>
                                <label style={{ marginBottom: '3px' }}>Correct Answer (Optional)</label>
                                <input
                                  type="text"
                                  placeholder="Leave blank for non-graded"
                                  value={newQuestion.correctAnswer}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, correctAnswer: e.target.value })}
                                  style={{ width: '100%' }}
                                />
                              </div>
                              <div className="form-group" style={{ minWidth: '80px', margin: 0 }}>
                                <label style={{ marginBottom: '3px' }}>Points</label>
                                <input
                                  type="number"
                                  min="1"
                                  value={newQuestion.pointsValue}
                                  onChange={(e) => setNewQuestion({ ...newQuestion, pointsValue: parseInt(e.target.value) || 1 })}
                                  style={{ width: '100%' }}
                                />
                              </div>
                            </div>
                            <small style={{ display: 'block', fontSize: '10px', color: 'var(--dtm-muted)' }}>
                              For MULTIPLE CHOICE: choice letter or text.
                            </small>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '15px', marginTop: 'auto' }}>
                    <button type="submit" className="btn btn-primary" disabled={loading}>Save Changes</button>
                    <button type="button" className="btn btn-secondary" onClick={() => setIsEditing(false)}>Cancel</button>
                  </div>
                </form>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default QuestionnaireDetailModal;
