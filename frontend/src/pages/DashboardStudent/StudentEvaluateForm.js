import React, { useEffect, useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import StudentSidebar from "../../components/Sidebar/StudentSidebar";
import { useToast } from "../../contexts/ToastContext";
import { generateDecimalRatingRange, generateNumericRange } from "../../utils/ratingUtils";
import "../DashboardTeacher/Teacher.css";
import "./StudentResponsive.css";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";

const API_BASE_URL = "http://localhost:8080";

const StudentEvaluateForm = () => {
  const { questionnaireId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  
  const [questionnaire, setQuestionnaire] = useState(null);
  const [members, setMembers] = useState([]);
  const [answers, setAnswers] = useState({}); // { evaluationId: { itemId: value } }
  const [status, setStatus] = useState("IN_PROGRESS");
  
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const fetchGroupForm = async () => {
      try {
        const token = currentUser?.token;
        if (!token) return;

        const res = await fetch(`${API_BASE_URL}/api/student/evaluations/group/${questionnaireId}`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to load evaluation form");
        const data = await res.json();
        
        if (data.status === 'SUBMITTED') {
          toast.info("Evaluation already submitted.");
          navigate('/student/dashboard');
          return;
        }

        setQuestionnaire(data.questionnaire);
        setMembers(data.members);
        setAnswers(data.answers || {});
        setStatus(data.status);
      } catch (err) {
        toast.error(err.message);
        navigate('/student/dashboard');
      } finally {
        setLoading(false);
      }
    };
    fetchGroupForm();
  }, [questionnaireId, currentUser, toast]);

  // Group items by sections for sequential navigation
  const pages = useMemo(() => {
    if (!questionnaire) return [];
    
    const pgs = [];
    
    // Top-level items (General)
    const topItems = (questionnaire.items || []).sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
    if (topItems.length > 0) {
      pgs.push({
        id: 'general',
        title: 'General',
        description: questionnaire.description || 'General questions for the evaluation.',
        items: topItems
      });
    }

    // Sections
    if (questionnaire.sections) {
      const sortedSections = [...questionnaire.sections].sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
      sortedSections.forEach(section => {
        if (section.items && section.items.length > 0) {
          const secItems = [...section.items].sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
          pgs.push({
            id: section.id || section.sectionTitle,
            title: section.sectionTitle,
            description: section.sectionDescription || "Please rate each team member based on this criteria.",
            items: secItems
          });
        }
      });
    }

    return pgs;
  }, [questionnaire]);

  const currentPage = pages[currentPageIndex];
  const isSubmitted = status === 'SUBMITTED';

  const handleScoreChange = (evaluationId, itemId, val) => {
    if (isSubmitted) return;
    setAnswers(prev => ({
      ...prev,
      [evaluationId]: {
        ...(prev[evaluationId] || {}),
        [itemId]: val
      }
    }));
  };

  const handleSaveDraft = async () => {
    setSaving(true);
    try {
      const token = currentUser?.token;
      const res = await fetch(`${API_BASE_URL}/api/student/evaluations/group/save`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ answers })
      });
      if (!res.ok) throw new Error("Failed to save draft");
      toast.success("Draft saved successfully!");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async () => {
    // Validation
    let isComplete = true;
    for (const page of pages) {
      for (const item of page.items) {
        for (const member of members) {
          const val = answers[member.evaluationId]?.[item.id];
          if (val === undefined || val === null || val === "") {
            isComplete = false;
            break;
          }
        }
        if (!isComplete) break;
      }
      if (!isComplete) break;
    }

    if (!isComplete) {
      toast.error("Please answer all questions for all team members before submitting.");
      return;
    }

    setShowConfirmModal(true);
  };

  const handleConfirmSubmit = async () => {
    setShowConfirmModal(false);
    setSubmitting(true);
    try {
      const token = currentUser?.token;
      
      // Save first
      await fetch(`${API_BASE_URL}/api/student/evaluations/group/save`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ answers })
      });

      // Submit all
      const evaluationIds = members.map(m => m.evaluationId);
      const res = await fetch(`${API_BASE_URL}/api/student/evaluations/group/submit`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}` 
        },
        body: JSON.stringify({ evaluationIds })
      });
      
      if (!res.ok) throw new Error("Failed to submit");
      toast.success("All evaluations submitted successfully!");
      navigate('/student/dashboard');
    } catch (err) {
      toast.error(err.message);
      setSubmitting(false);
    }
  };

  if (loading) return <div className="teacher-container"><StudentSidebar /><div className="teacher-content">Loading evaluation form...</div></div>;
  if (!questionnaire || pages.length === 0) return <div className="teacher-container"><StudentSidebar /><div className="teacher-content">No questions found.</div></div>;

  return (
    <div className="teacher-container">
      <StudentSidebar />
      <div className="teacher-content eval-content-wrapper">
        {/* Header */}
        <div className="eval-header">
          <div>
            <h1 className="eval-title" style={{ margin: 0, color: 'var(--dtm-gold)' }}>{questionnaire.title}</h1>
            <p className="eval-subtitle" style={{ margin: '4px 0 0 0', color: 'var(--dtm-muted)' }}>Evaluating all team members</p>
          </div>
          <button className="btn-secondary" onClick={() => navigate('/student/dashboard')}>Exit</button>
        </div>

        {/* Main Content Pane */}
        <div className="eval-main-pane">
          
          {/* Left Pane - Section Card */}
          <div className="eval-left-pane">
            <div className="evaluation-response-item" style={{ height: 'auto', padding: '30px', borderRadius: '16px', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.03)' }}>
              <h2 className="eval-section-title" style={{ marginBottom: '20px', lineHeight: '1.4' }}>{currentPage.title}</h2>
              <p className="eval-section-desc" style={{ color: 'var(--dtm-muted)', lineHeight: '1.6' }}>
                {currentPage.description}
              </p>
            </div>
            
            <div style={{ marginTop: '30px' }}>
                <div style={{ color: 'var(--dtm-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>
                    Progress: {currentPageIndex + 1} / {pages.length}
                </div>
                <div style={{ width: '100%', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                    <div style={{ 
                        width: `${((currentPageIndex + 1) / pages.length) * 100}%`, 
                        height: '100%', 
                        background: 'var(--dtm-gold)', 
                        borderRadius: '2px',
                        transition: 'width 0.3s ease'
                    }} />
                </div>
            </div>
          </div>

          {/* Right Pane - Members Rating */}
          <div className="eval-right-pane">
            <div style={{ maxWidth: '800px', margin: '0 auto' }}>
              {currentPage.items.map((item) => {
                const isRating = item.questionType === "RATING";
                let finalRange = [];
                if (isRating) {
                  finalRange = generateDecimalRatingRange(item.minScore, item.maxScore);
                } else {
                  finalRange = generateNumericRange(item.minScore, item.maxScore);
                }

                return (
                  <div key={item.id} style={{ marginBottom: '40px', padding: '24px', background: 'rgba(255,255,255,0.02)', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.05)' }}>
                    <h3 className="eval-question-title" style={{ marginBottom: '8px', color: 'var(--dtm-text)' }}>{item.questionText}</h3>
                    {item.questionDescription && (
                      <p className="eval-question-desc" style={{ color: 'var(--dtm-muted)', marginBottom: '20px' }}>{item.questionDescription}</p>
                    )}
                    
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                      {members.map((member) => (
                        <div key={member.id} className="student-member-row">
                          <div className="student-member-name">
                            {member.name} {member.isMe ? <span style={{ color: 'var(--dtm-gold)', fontSize: '0.8rem', marginLeft: '6px' }}>(Self)</span> : ''}
                          </div>
                          
                          <div className="student-member-input">
                            {item.questionType === "TEXT" ? (
                              <textarea
                                className="custom-textarea"
                                placeholder={`Enter response for ${member.name}...`}
                                value={answers[member.evaluationId]?.[item.id] || ""}
                                onChange={(e) => handleScoreChange(member.evaluationId, item.id, e.target.value)}
                                disabled={isSubmitted}
                              />
                            ) : item.questionType === "MULTIPLE_CHOICE" ? (
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                                {(item.choices || []).map((choice, idx) => {
                                  const isSelected = String(answers[member.evaluationId]?.[item.id]) === String(choice);
                                  return (
                                    <button
                                      key={idx}
                                      className="eval-choice-btn"
                                      onClick={() => handleScoreChange(member.evaluationId, item.id, choice)}
                                      disabled={isSubmitted}
                                      style={{
                                        padding: '6px 14px',
                                        borderRadius: '20px',
                                        border: `1px solid ${isSelected ? 'var(--dtm-gold)' : 'rgba(255,255,255,0.1)'}`,
                                        background: isSelected ? 'rgba(242, 201, 76, 0.1)' : 'rgba(255,255,255,0.02)',
                                        color: isSelected ? 'var(--dtm-gold)' : 'var(--dtm-muted)',
                                        cursor: isSubmitted ? 'default' : 'pointer',
                                        transition: 'all 0.2s ease'
                                      }}
                                    >
                                      {choice}
                                    </button>
                                  );
                                })}
                              </div>
                            ) : (
                              <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
                                {finalRange.map((num) => {
                                  const isSelected = String(answers[member.evaluationId]?.[item.id]) === String(num);
                                  
                                  const isChosenByOther = isRating && members.some(otherMem => 
                                    otherMem.evaluationId !== member.evaluationId && 
                                    String(answers[otherMem.evaluationId]?.[item.id]) === String(num)
                                  );
                                  
                                  const isDisabled = isSubmitted || isChosenByOther;

                                  return (
                                    <label key={num} style={{ 
                                      display: 'flex', 
                                      alignItems: 'center',
                                      gap: '8px', 
                                      cursor: isDisabled ? 'not-allowed' : 'pointer',
                                      background: isSelected ? 'rgba(242, 201, 76, 0.1)' : 'transparent',
                                      padding: '6px 12px',
                                      borderRadius: '20px',
                                      border: `1px solid ${isSelected ? 'var(--dtm-gold)' : 'transparent'}`,
                                      transition: 'all 0.2s ease',
                                      opacity: isDisabled && !isSelected ? 0.3 : 1
                                    }}>
                                      <div style={{
                                        width: '18px',
                                        height: '18px',
                                        borderRadius: '50%',
                                        border: `2px solid ${isSelected ? 'var(--dtm-gold)' : (isDisabled && !isSelected ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.3)')}`,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        position: 'relative'
                                      }}>
                                        <input 
                                          type="radio" 
                                          name={`member-${member.id}-item-${item.id}`} 
                                          value={num}
                                          checked={isSelected}
                                          onChange={() => {
                                            if (!isDisabled) {
                                              handleScoreChange(member.evaluationId, item.id, num);
                                            }
                                          }}
                                          disabled={isDisabled}
                                          style={{ opacity: 0, position: 'absolute', inset: 0, cursor: 'inherit', margin: 0, width: '100%', height: '100%' }}
                                        />
                                        {isSelected && <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--dtm-gold)' }} />}
                                      </div>
                                      <span className="eval-scale-num" style={{ color: isSelected ? 'var(--dtm-gold)' : 'var(--dtm-text)', fontWeight: isSelected ? 600 : 400 }}>{num}</span>
                                    </label>
                                  );
                                })}
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Footer Navigation */}
        <div className="eval-footer">
          <div>
            {!isSubmitted && (
                <button 
                className="btn-secondary" 
                onClick={handleSaveDraft}
                disabled={saving || submitting}
                >
                {saving ? 'Saving...' : 'Save Draft'}
                </button>
            )}
          </div>

          <div className="eval-footer-actions">
            <button 
              className="btn-secondary" 
              onClick={() => setCurrentPageIndex(prev => Math.max(0, prev - 1))}
              disabled={currentPageIndex === 0}
              style={{ marginRight: '12px' }}
            >
              Prev Page
            </button>
            {currentPageIndex < pages.length - 1 ? (
              <button 
                className="btn" 
                onClick={() => setCurrentPageIndex(prev => Math.min(pages.length - 1, prev + 1))}
              >
                Next Page
              </button>
            ) : (
              !isSubmitted ? (
                <button 
                  className="btn" 
                  onClick={handleSubmit}
                  disabled={saving || submitting}
                >
                  {submitting ? 'Submitting...' : 'Submit All Evaluations'}
                </button>
              ) : (
                <button className="btn" onClick={() => navigate('/student/dashboard')}>Finish Viewing</button>
              )
            )}
          </div>
        </div>
      </div>

      <ConfirmModal
        isOpen={showConfirmModal}
        title="Confirm Submission"
        message="Are you sure you want to submit all evaluations? You cannot edit them after submission."
        confirmText="Submit Evaluations"
        cancelText="Cancel"
        onConfirm={handleConfirmSubmit}
        onCancel={() => setShowConfirmModal(false)}
      />
    </div>
  );
};

export default StudentEvaluateForm;
