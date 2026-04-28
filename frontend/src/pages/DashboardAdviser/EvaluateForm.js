import React, { useEffect, useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { adviserAPI } from "../../services/api";
import "../DashboardTeacher/Teacher.css";
import "./Adviser.css";

const EvaluateForm = () => {
  const { teamId, questionnaireId } = useParams();
  const navigate = useNavigate();

  const goBackToTeamDetails = () => {
    if (teamId) {
      navigate(`/adviser/evaluations/${teamId}`);
      return;
    }
    navigate("/adviser/dashboard");
  };

  const [evaluation, setEvaluation] = useState(null);
  const [answers, setAnswers] = useState({});
  const [comments, setComments] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);

  useEffect(() => {
    const load = async () => {
      try {
        const evalData = await adviserAPI.getEvaluation(
          teamId,
          questionnaireId
        );

        if (!evalData) {
          setError("Failed to load evaluation: No data returned from server");
          setLoading(false);
          return;
        }

        setEvaluation(evalData);

        const existing = {};
        if (evalData.scores && Array.isArray(evalData.scores)) {
          evalData.scores.forEach((s) => {
            const itemId = s.questionnaireItem?.id || s.questionnaireItemId;
            if (itemId) {
              existing[itemId] = s.numericScore ?? s.textResponse;
            }
          });
        }

        setAnswers(existing);
        setComments(evalData.generalComments || "");
      } catch (e) {
        setError(e.message || "Failed to load evaluation");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [teamId, questionnaireId]);

  // Flatten items for sequential navigation
  const allItems = useMemo(() => {
    if (!evaluation?.questionnaire) return [];
    const items = [...(evaluation.questionnaire.items || [])];
    if (evaluation.questionnaire.sections) {
      evaluation.questionnaire.sections.forEach(section => {
        if (section.items) {
          items.push(...section.items.map(item => ({ ...item, sectionTitle: section.sectionTitle })));
        }
      });
    }
    return items.sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0));
  }, [evaluation]);

  const currentItem = allItems[currentQuestionIndex];
  const isSubmitted = evaluation?.status === 'SUBMITTED' || evaluation?.status === 'REVIEWED';

  const handleChange = (itemId, value) => {
    if (isSubmitted) return;
    setAnswers((prev) => ({ ...prev, [itemId]: value }));
  };

  const handleSaveDraft = async () => {
    setSaving(true);
    try {
      await adviserAPI.saveEvaluation({
        evaluationId: evaluation.id,
        answers,
        generalComments: comments,
      });
      alert("Draft saved successfully!");
    } catch (e) {
      alert("Error saving evaluation: " + e.message);
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async () => {
    if (!window.confirm("Are you sure you want to submit this evaluation? You cannot edit it after submission.")) return;
    
    setSubmitting(true);
    try {
      await adviserAPI.saveEvaluation({
        evaluationId: evaluation.id,
        answers,
        generalComments: comments,
      });
      await adviserAPI.submitEvaluation(evaluation.id);
      navigate("/adviser/completed");
    } catch (e) {
      alert("Error submitting evaluation: " + e.message);
      setSubmitting(false);
    }
  };

  if (loading) return (
    <div className="teacher-container">
      <AdviserSidebar />
      <div className="teacher-content">Loading evaluation form...</div>
    </div>
  );

  if (error) return (
    <div className="teacher-container">
      <AdviserSidebar />
      <div className="teacher-content"><p className="error-message">{error}</p></div>
    </div>
  );

  if (!evaluation || allItems.length === 0) return (
    <div className="teacher-container">
      <AdviserSidebar />
      <div className="teacher-content">No questions found.</div>
    </div>
  );

  const min = currentItem.minScore ?? 1;
  const max = currentItem.maxScore ?? 5;
  const range = Array.from({ length: Math.abs(max - min) + 1 }, (_, i) => {
    return max > min ? max - i : min - i;
  });

  return (
    <div className="teacher-container">
      <AdviserSidebar />
      <div className="teacher-content" style={{ display: 'flex', flexDirection: 'column', height: '100vh', padding: 0 }}>
        {/* Header */}
        <div style={{ padding: '20px 40px', background: 'rgba(255,255,255,0.02)', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.5rem', color: 'var(--dtm-gold)' }}>{evaluation.questionnaire.title}</h1>
            <p style={{ margin: '4px 0 0 0', color: 'var(--dtm-muted)', fontSize: '0.9rem' }}>Evaluating Team: {evaluation.teamName}</p>
          </div>
          <button className="btn-secondary" onClick={goBackToTeamDetails}>Exit</button>
        </div>

        {/* Main Content Pane */}
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
          
          {/* Left Pane - Criteria Card */}
          <div style={{ flex: '0 0 400px', padding: '40px', borderRight: '1px solid rgba(255,255,255,0.08)', background: 'rgba(255,255,255,0.01)' }}>
            <div className="evaluation-response-item" style={{ height: 'auto', padding: '30px', borderRadius: '16px', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.03)' }}>
              {currentItem.sectionTitle && (
                <div style={{ color: 'var(--dtm-gold)', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '12px' }}>
                  {currentItem.sectionTitle}
                </div>
              )}
              <h2 style={{ fontSize: '1.4rem', marginBottom: '20px', lineHeight: '1.4' }}>{currentItem.questionText}</h2>
              <p style={{ color: 'var(--dtm-muted)', lineHeight: '1.6', fontSize: '1rem' }}>
                {currentItem.questionDescription || "Please provide your evaluation for this criteria."}
              </p>
            </div>
            
            <div style={{ marginTop: '30px' }}>
                <div style={{ color: 'var(--dtm-muted)', fontSize: '0.9rem', marginBottom: '8px' }}>
                    Progress: {currentQuestionIndex + 1} / {allItems.length}
                </div>
                <div style={{ width: '100%', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px' }}>
                    <div style={{ 
                        width: `${((currentQuestionIndex + 1) / allItems.length) * 100}%`, 
                        height: '100%', 
                        background: 'var(--dtm-gold)', 
                        borderRadius: '2px',
                        transition: 'width 0.3s ease'
                    }} />
                </div>
            </div>
          </div>

          {/* Right Pane - Team Rating */}
          <div style={{ flex: 1, padding: '40px', overflowY: 'auto' }}>
            <div style={{ maxWidth: '800px' }}>
                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  padding: '20px 0', 
                  borderBottom: '1px solid rgba(255,255,255,0.05)' 
                }}>
                  <div style={{ width: '200px', fontWeight: 500, fontSize: '1.1rem' }}>
                    Team: {evaluation.teamName}
                  </div>
                  
                  <div style={{ flex: 1 }}>
                    {currentItem.questionType === "TEXT" ? (
                      <textarea
                        className="custom-textarea"
                        placeholder="Enter response..."
                        value={answers[currentItem.id] || ""}
                        onChange={(e) => handleChange(currentItem.id, e.target.value)}
                        disabled={isSubmitted}
                      />
                    ) : currentItem.questionType === "MULTIPLE_CHOICE" ? (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                        {(currentItem.choices || []).map((choice, idx) => {
                          const isSelected = String(answers[currentItem.id]) === String(choice);
                          return (
                            <button
                              key={idx}
                              onClick={() => handleChange(currentItem.id, choice)}
                              disabled={isSubmitted}
                              style={{
                                padding: '8px 16px',
                                borderRadius: '20px',
                                border: `1px solid ${isSelected ? 'var(--dtm-gold)' : 'rgba(255,255,255,0.1)'}`,
                                background: isSelected ? 'rgba(242, 201, 76, 0.1)' : 'rgba(255,255,255,0.02)',
                                color: isSelected ? 'var(--dtm-gold)' : 'var(--dtm-muted)',
                                cursor: isSubmitted ? 'default' : 'pointer',
                                transition: 'all 0.2s ease',
                                fontSize: '0.9rem'
                              }}
                            >
                              {choice}
                            </button>
                          );
                        })}
                      </div>
                    ) : (
                      <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
                        {range.map((num) => {
                          const isSelected = String(answers[currentItem.id]) === String(num);
                          return (
                            <label key={num} style={{ 
                              display: 'flex', 
                              flexDirection: 'column', 
                              alignItems: 'center', 
                              gap: '6px', 
                              cursor: isSubmitted ? 'default' : 'pointer' 
                            }}>
                              <div style={{
                                width: '32px',
                                height: '32px',
                                borderRadius: '50%',
                                border: `2px solid ${isSelected ? 'var(--dtm-gold)' : 'rgba(255,255,255,0.15)'}`,
                                background: isSelected ? 'rgba(242, 201, 76, 0.2)' : 'transparent',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                transition: 'all 0.2s ease',
                                position: 'relative'
                              }}>
                                <input 
                                  type="radio" 
                                  name={`item-${currentItem.id}`} 
                                  value={num}
                                  checked={isSelected}
                                  onChange={() => handleChange(currentItem.id, num)}
                                  disabled={isSubmitted}
                                  style={{ opacity: 0, position: 'absolute', inset: 0, cursor: 'inherit' }}
                                />
                                {isSelected && <div style={{ width: '12px', height: '12px', borderRadius: '50%', background: 'var(--dtm-gold)' }} />}
                              </div>
                              <span style={{ fontSize: '0.8rem', color: isSelected ? 'var(--dtm-gold)' : 'var(--dtm-muted)' }}>{num}</span>
                            </label>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>

                {/* General Comments on the last question */}
                {currentQuestionIndex === allItems.length - 1 && (
                  <div style={{ marginTop: '30px' }}>
                      <div style={{ 
                        padding: '20px 0', 
                        borderBottom: '1px solid rgba(255,255,255,0.05)' 
                      }}>
                        <div style={{ marginBottom: '12px', fontWeight: 500, fontSize: '1.1rem' }}>
                          General Comments (Optional)
                        </div>
                        <textarea
                          style={{
                            width: '100%',
                            minHeight: '100px',
                            padding: '12px',
                            borderRadius: '8px',
                            background: 'rgba(255,255,255,0.05)',
                            border: '1px solid rgba(255,255,255,0.1)',
                            color: '#fff',
                            fontFamily: 'inherit',
                            resize: 'vertical'
                          }}
                          placeholder="Any additional feedback for the team..."
                          value={comments}
                          onChange={(e) => setComments(e.target.value)}
                          disabled={isSubmitted}
                        />
                      </div>
                  </div>
                )}
            </div>
          </div>
        </div>

        {/* Footer Navigation */}
        <div style={{ padding: '20px 40px', background: 'rgba(255,255,255,0.02)', borderTop: '1px solid rgba(255,255,255,0.08)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <button 
              className="btn-secondary" 
              onClick={() => setCurrentQuestionIndex(prev => Math.max(0, prev - 1))}
              disabled={currentQuestionIndex === 0}
            >
              Previous Criteria
            </button>
            {!isSubmitted && (
                <button 
                className="btn-secondary" 
                style={{ marginLeft: '12px' }}
                onClick={handleSaveDraft}
                disabled={saving || submitting}
                >
                {saving ? 'Saving...' : 'Save Draft'}
                </button>
            )}
          </div>

          <div>
            {currentQuestionIndex < allItems.length - 1 ? (
              <button 
                className="btn" 
                onClick={() => setCurrentQuestionIndex(prev => Math.min(allItems.length - 1, prev + 1))}
              >
                Next Criteria
              </button>
            ) : (
              !isSubmitted ? (
                <button 
                  className="btn" 
                  onClick={handleSubmit}
                  disabled={saving || submitting}
                >
                  {submitting ? 'Submitting...' : 'Submit Evaluation'}
                </button>
              ) : (
                <button className="btn" onClick={goBackToTeamDetails}>Finish Viewing</button>
              )
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default EvaluateForm;
