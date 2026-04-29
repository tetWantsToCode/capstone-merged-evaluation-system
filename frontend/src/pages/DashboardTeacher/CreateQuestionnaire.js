import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { questionnaireAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";
import "./QuestionnaireTwoColumn.css";

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

const CreateQuestionnaire = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const [googleLinked, setGoogleLinked] = useState(false);

  const [formData, setFormData] = useState({
    title: "",
    description: "",
    deadlineAt: "",
    questions: [],
    sections: [
      {
        sectionTitle: "Section 1",
        sectionDescription: "",
        orderIndex: 0,
        items: []
      }
    ],
    target: "ADVISER"
  });

  const [newSection, setNewSection] = useState({
    sectionTitle: "",
    sectionDescription: "",
    items: []
  });

  const [newQuestion, setNewQuestion] = useState({
    questionText: "",
    questionType: "NUMERIC_SCALE",
    minScore: 1,
    maxScore: 5,
    choices: [],
    correctAnswer: "",
    pointsValue: 1,
    required: true,
  });

  const [activeSectionIndex, setActiveSectionIndex] = useState(0);
  const [usesSections, setUsesSections] = useState(true);

  useEffect(() => {
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

  const handleCreateQuestionnaire = async (e) => {
    e.preventDefault();

    if (!googleLinked) {
      const linkedNow = await checkGoogleLink();
      if (!linkedNow) {
        toast.error('Please link your Google account in the Profile page first.');
        return;
      }
    }

    if (!formData.title.trim()) {
      toast.error('Please enter a questionnaire title');
      return;
    }

    const sectionedQCount = formData.sections.reduce((sum, sec) => sum + sec.items.length, 0);

    if (sectionedQCount === 0) {
      toast.error('Please add at least one question');
      return;
    }

    if (formData.deadlineAt) {
      const deadline = new Date(formData.deadlineAt);
      if (Number.isNaN(deadline.getTime())) {
        toast.error('Please provide a valid deadline');
        return;
      }
      if (deadline <= new Date()) {
        toast.error('Deadline must be in the future');
        return;
      }
    }

    try {
      toast.info('Creating questionnaire...');
      const payload = {
        ...formData,
        deadlineAt: formData.deadlineAt || null,
      };
      await questionnaireAPI.createQuestionnaire(payload);
      toast.success('Questionnaire created successfully!');
      navigate('/teacher/questionnaires');
    } catch (err) {
      toast.error('Error creating questionnaire: ' + err.message);
    }
  };

  const handleAddQuestion = () => {
    if (!newQuestion.questionText.trim()) {
      toast.error('Please enter a question text');
      return;
    }

    if (newQuestion.questionType === 'MULTIPLE_CHOICE') {
      if (!newQuestion.choices || newQuestion.choices.length < 2) {
        toast.error('Multiple choice questions must have at least 2 choices');
        return;
      }
    }

    if (activeSectionIndex !== null) {
      const updatedSections = [...formData.sections];
      updatedSections[activeSectionIndex].items.push({ ...newQuestion });
      setFormData({ ...formData, sections: updatedSections });
    }

    setNewQuestion({
      questionText: "",
      questionType: "NUMERIC_SCALE",
      minScore: 1,
      maxScore: 5,
      choices: [],
      correctAnswer: "",
      pointsValue: 1,
      required: true,
    });

    toast.success('Question added!');
  };

  const handleRemoveQuestion = (index) => {
    if (activeSectionIndex !== null) {
      const updatedSections = [...formData.sections];
      updatedSections[activeSectionIndex].items = updatedSections[activeSectionIndex].items.filter((_, i) => i !== index);
      setFormData({ ...formData, sections: updatedSections });
    }
    toast.success('Question removed');
  };

  const handleCreateSection = () => {
    if (!newSection.sectionTitle.trim()) {
      toast.error('Please enter a section title');
      return;
    }

    setFormData({
      ...formData,
      sections: [
        ...formData.sections,
        {
          sectionTitle: newSection.sectionTitle,
          sectionDescription: newSection.sectionDescription,
          orderIndex: formData.sections.length,
          items: []
        }
      ]
    });

    setNewSection({
      sectionTitle: "",
      sectionDescription: "",
      items: []
    });

    toast.success('Section created!');
    setActiveSectionIndex(formData.sections.length);
  };

  const handleRemoveSection = (index) => {
    const updatedSections = formData.sections.filter((_, i) => i !== index);
    setFormData({ ...formData, sections: updatedSections });
    if (activeSectionIndex === index) {
      setActiveSectionIndex(null);
    }
    toast.success('Section removed');
  };

  const handleToggleSectionsMode = () => {
    // Create a new section with an incremented title
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
    // Set the newly created section as active
    setActiveSectionIndex(formData.sections.length);
  };

  const getCurrentQuestions = () => {
    if (activeSectionIndex !== null && formData.sections[activeSectionIndex]) {
      return formData.sections[activeSectionIndex].items;
    }
    return [];
  };

  const totalQuestions = formData.sections.reduce((sum, sec) => sum + sec.items.length, 0);

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <div style={{ marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h1 style={{ margin: '0' }}>Create New Questionnaire</h1>
          <button
            className="btn btn-secondary"
            onClick={() => navigate('/teacher/questionnaires')}
          >
            ← Back to Questionnaires
          </button>
        </div>

        {!googleLinked && (
          <div className="alert-warning">
            <strong>⚠️ Google Account Not Linked</strong>
            <p>Please link your Google account in the Profile page to create questionnaires.</p>
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginTop: '20px' }}>
          {/* LEFT COLUMN - SECTION EDITOR & QUESTIONS */}
          <div className="questionnaire-preview-panel">
            {activeSectionIndex !== null && formData.sections[activeSectionIndex] && (
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
                      placeholder="e.g., Communication Skills"
                      style={{ padding: '8px 10px', fontSize: '11px', width: '100%', boxSizing: 'border-box' }}
                    />
                  </div>

                  <div className="form-group" style={{ marginBottom: '8px' }}>
                    <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--dtm-gold)', margin: 0 }}>Description</label>
                    <textarea
                      value={formData.sections[activeSectionIndex].sectionDescription}
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
                  📄 {formData.sections.length} page(s) • {formData.sections[activeSectionIndex].items.length} question(s)
                </div>

                {/* Section Navigation */}
                {formData.sections.length > 1 && (
                  <div className="section-tabs" style={{ marginBottom: '8px' }}>
                    {formData.sections.map((section, idx) => (
                      <button
                        key={idx}
                        type="button"
                        className={`section-tab ${activeSectionIndex === idx ? 'active' : ''}`}
                        onClick={() => setActiveSectionIndex(idx)}
                      >
                        {section.sectionTitle}
                      </button>
                    ))}
                  </div>
                )}

                {/* Questions List */}
                <div style={{ borderTop: '1px solid rgba(255, 255, 255, 0.1)', paddingTop: '8px' }}>
                  <h4 style={{ margin: '0 0 8px 0', fontSize: '12px', fontWeight: '600', color: 'var(--dtm-gold)' }}>
                    Questions in this page
                  </h4>

                  {formData.sections[activeSectionIndex].items && formData.sections[activeSectionIndex].items.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '400px', overflowY: 'auto' }}>
                      {formData.sections[activeSectionIndex].items.map((q, index) => (
                        <div key={index} style={{
                          background: 'rgba(255, 255, 255, 0.04)',
                          borderLeft: '3px solid var(--dtm-gold)',
                          borderRadius: '4px',
                          padding: '8px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'flex-start',
                          gap: '8px'
                        }}>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <p style={{ margin: '0 0 4px 0', fontSize: '11px', fontWeight: '500', color: 'var(--dtm-text)', wordBreak: 'break-word' }}>
                              Q{index + 1}: {q.questionText}
                            </p>
                            <small style={{ color: 'var(--dtm-muted)', fontSize: '10px' }}>
                              {q.questionType}
                              {(q.questionType === 'NUMERIC_SCALE' || q.questionType === 'RATING') && ` (${q.minScore}-${q.maxScore})`}
                              {q.required === false ? ' • Optional' : ' • Required'}
                            </small>
                          </div>
                          <button
                            type="button"
                            className="btn btn-sm btn-danger"
                            onClick={() => handleRemoveQuestion(index)}
                            style={{ padding: '4px 8px', fontSize: '10px', flexShrink: 0 }}
                            title="Delete question"
                          >
                            ×
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p style={{ fontSize: '10px', color: 'var(--dtm-muted)', marginBottom: 0, fontStyle: 'italic' }}>
                      No questions yet. Add one on the right →
                    </p>
                  )}
                </div>
              </>
            )}
          </div>

          {/* RIGHT COLUMN - CREATION FORM */}
          <div className="questionnaire-creation-panel">
            <form onSubmit={handleCreateQuestionnaire} className="creation-form">
              <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-start', marginBottom: '0px', position: 'relative' }}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label>Title *</label>
                  <input
                    type="text"
                    value={formData.title}
                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                    required
                    placeholder="e.g., Team Performance Evaluation"
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
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={!googleLinked || totalQuestions === 0}
                  style={{ padding: '8px 12px', fontSize: '11px', marginTop: '24px' }}
                >
                  Create
                </button>
              </div>

              <div className="form-group" style={{ marginBottom: '4px', marginTop: '-6px' }}>
                <label>Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  rows="2"
                  placeholder="What is this questionnaire about?"
                  style={{ fontSize: '11px' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: '8px' }}>
                <label>Deadline (Optional)</label>
                <input
                  type="datetime-local"
                  value={formData.deadlineAt}
                  min={new Date(Date.now() + 60000).toISOString().slice(0, 16)}
                  onChange={(e) => setFormData({ ...formData, deadlineAt: e.target.value })}
                  style={{ fontSize: '11px' }}
                />
                <small style={{ color: 'var(--dtm-muted)', fontSize: '10px' }}>
                  Leave blank for no deadline. When reached, this questionnaire closes automatically.
                </small>
              </div>

              {/* Questions Display */}
              <div style={{ marginBottom: '8px', marginTop: '4px' }}>
                <h3 style={{ margin: '0 0 8px 0', fontSize: '12px', fontWeight: '600', color: 'var(--dtm-gold)' }}>Add Question</h3>

                {/* Add Question Form */}
                {activeSectionIndex !== null && (
                  <div className="add-question-box">
                    <div className="form-group">
                      <label>Question Text *</label>
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flex: 1 }}>
                          <input
                            type="text"
                            value={newQuestion.questionText}
                            onChange={(e) => setNewQuestion({ ...newQuestion, questionText: e.target.value })}
                            placeholder="Ask your question..."
                            style={{ fontSize: '11px', flex: 1 }}
                          />

                          <select
                            value={newQuestion.questionType}
                            onChange={(e) => setNewQuestion({ ...newQuestion, questionType: e.target.value })}
                            style={{ fontSize: '11px', minWidth: '140px' }}
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
                              width: '36px',
                              height: '36px',
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
                              flexShrink: 0,
                              position: 'relative'
                            }}
                          >
                            +
                          </button>

                          <button
                            type="button"
                            onClick={handleToggleSectionsMode}
                            className="add-page-icon-btn"
                            style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', lineHeight: '0', padding: '0' }}
                          >
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <rect x="3" y="2" width="16" height="8" rx="1"></rect>
                              <rect x="3" y="14" width="16" height="8" rx="1"></rect>
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>

                    {(newQuestion.questionType === 'NUMERIC_SCALE' || newQuestion.questionType === 'RATING') && (
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                        <div className="form-group">
                          <label>Min</label>
                          <input
                            type="number"
                            value={newQuestion.minScore}
                            onChange={(e) => setNewQuestion({ ...newQuestion, minScore: parseInt(e.target.value) })}
                            min="0"
                            style={{ fontSize: '11px' }}
                          />
                        </div>
                        <div className="form-group">
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

                    <div style={{ marginTop: '8px', borderTop: '1px solid rgba(255, 255, 255, 0.1)', paddingTop: '8px' }}>
                      <div className="form-group" style={{ margin: 0 }}>
                        <label>Requirement</label>
                        <select
                          value={newQuestion.required ? 'REQUIRED' : 'OPTIONAL'}
                          onChange={(e) => setNewQuestion({ ...newQuestion, required: e.target.value === 'REQUIRED' })}
                        >
                          <option value="REQUIRED">Required</option>
                          <option value="OPTIONAL">Optional</option>
                        </select>
                      </div>
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
                )}

              </div>


            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CreateQuestionnaire;
