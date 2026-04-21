import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { adviserAPI } from "../../services/api";
import "./Adviser.css";

const EvaluateForm = () => {
  const { teamId, questionnaireId } = useParams();
  const navigate = useNavigate();

  const [evaluation, setEvaluation] = useState(null);
  const [answers, setAnswers] = useState({});
  const [comments, setComments] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saveError, setSaveError] = useState(null);
  const [currentSectionIndex, setCurrentSectionIndex] = useState(0);

  useEffect(() => {
    const load = async () => {
      try {
        const evalData = await adviserAPI.getEvaluation(
          teamId,
          questionnaireId
        );

        console.log("Evaluation data:", evalData);
        console.log("Questionnaire:", evalData?.questionnaire);
        console.log("Items:", evalData?.questionnaire?.items);
        console.log("Scores:", evalData?.scores);

        if (!evalData) {
          setError("Failed to load evaluation: No data returned from server");
          setLoading(false);
          return;
        }

        setEvaluation(evalData);

        const existing = {};
        if (evalData.scores && Array.isArray(evalData.scores)) {
          evalData.scores.forEach((s) => {
            // Handle both nested structure (questionnaireItem.id) and flat structure (questionnaireItemId)
            const itemId = s.questionnaireItem?.id || s.questionnaireItemId;
            if (itemId) {
              existing[itemId] =
                s.numericScore ?? s.textResponse;
            } else {
              console.warn("Score missing item ID:", s);
            }
          });
        }

        setAnswers(existing);
        setComments(evalData.generalComments || "");
      } catch (e) {
        console.error("Error loading evaluation:", e);
        setError(e.message || "Failed to load evaluation");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [teamId, questionnaireId]);

  const handleChange = (itemId, value) => {
    setAnswers((prev) => ({ ...prev, [itemId]: value }));
  };

  const saveDraft = async () => {
    try {
      setSaveError(null);
      await adviserAPI.saveEvaluation({
        evaluationId: evaluation.id,
        answers,
        generalComments: comments,
      });
      alert("Draft saved");
    } catch (e) {
      console.error("Error saving evaluation:", e);
      setSaveError(e.message || "Failed to save evaluation");
    }
  };

  const submit = async () => {
    try {
      setSaveError(null);
      await adviserAPI.saveEvaluation({
        evaluationId: evaluation.id,
        answers,
        generalComments: comments,
      });
      await adviserAPI.submitEvaluation(evaluation.id);
      navigate("/adviser/completed");
    } catch (e) {
      console.error("Error submitting evaluation:", e);
      setSaveError(e.message || "Failed to submit evaluation");
    }
  };

  if (loading) return <p>Loading...</p>;
  if (error) return <p className="error-message">{error}</p>;
  
  if (!evaluation || !evaluation.questionnaire) {
    return <p className="error-message">Evaluation data is incomplete</p>;
  }

  // Determine if questionnaire has sections
  const hasSections = evaluation.questionnaire.sections && evaluation.questionnaire.sections.length > 0;
  const sections = evaluation.questionnaire.sections || [];
  const looseItems = evaluation.questionnaire.items || [];

  let itemsToDisplay = [];
  let currentSectionInfo = null;

  if (hasSections) {
    // Get items from current section
    if (currentSectionIndex < sections.length) {
      currentSectionInfo = sections[currentSectionIndex];
      itemsToDisplay = currentSectionInfo.items || [];
    }
  } else {
    // Display all loose items if no sections
    itemsToDisplay = looseItems;
  }

  // Helper function to check if all questions in current section are answered
  const isCurrentSectionComplete = () => {
    return itemsToDisplay.every(item => answers[item.id]);
  };

  // Helper function to get all items for validation
  const getAllItems = () => {
    if (hasSections) {
      return sections.flatMap(section => section.items || []);
    }
    return looseItems;
  };

  const allItems = getAllItems();
  const allAnswered = allItems.every(item => answers[item.id]);

  const handleNext = () => {
    if (hasSections && currentSectionIndex < sections.length - 1) {
      setCurrentSectionIndex(currentSectionIndex + 1);
    }
  };

  const handlePrevious = () => {
    if (hasSections && currentSectionIndex > 0) {
      setCurrentSectionIndex(currentSectionIndex - 1);
    }
  };

  return (
    <div className="adviser-container">
      <AdviserSidebar />

      <div className="adviser-content">
        <h1>{evaluation.questionnaire.title}</h1>

        {/* Section Progress Indicator */}
        {hasSections && currentSectionInfo && (
          <div style={{
            padding: '10px 12px',
            backgroundColor: 'rgba(139, 35, 35, 0.08)',
            borderRadius: '4px',
            marginBottom: '18px',
            borderLeft: '3px solid #d4af37'
          }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '8px'
            }}>
              <span style={{
                fontSize: '13px',
                color: '#d4af37',
                fontWeight: '500'
              }}>
                Section {currentSectionIndex + 1} of {sections.length}
              </span>
              <span style={{
                fontSize: '14px',
                fontWeight: '600',
                color: '#fff'
              }}>
                {currentSectionInfo.sectionTitle}
              </span>
            </div>
            
            {/* Progress bar */}
            <div style={{
              width: '100%',
              height: '4px',
              backgroundColor: 'rgba(212, 175, 55, 0.2)',
              borderRadius: '2px',
              overflow: 'hidden'
            }}>
              <div style={{
                height: '100%',
                width: `${((currentSectionIndex + 1) / sections.length) * 100}%`,
                backgroundColor: '#d4af37',
                transition: 'width 0.3s ease'
              }}></div>
            </div>
          </div>
        )}

        {itemsToDisplay.length === 0 && hasSections ? (
          <div className="error-message">
            <p>No questions found in this section.</p>
          </div>
        ) : null}

        {itemsToDisplay.length === 0 && !hasSections ? (
          <div className="error-message">
            <p>No questions found in this questionnaire.</p>
            <p>Please contact the teacher to add questions to this questionnaire.</p>
          </div>
        ) : null}

        {saveError && (
          <div className="error-message" style={{ marginBottom: "20px" }}>
            <p>{saveError}</p>
            <button onClick={() => setSaveError(null)} style={{ marginTop: "10px" }}>
              Dismiss
            </button>
          </div>
        )}

        {itemsToDisplay.map((item) => (
          <div key={item.id} className="form-group">
            <label>{item.questionText}</label>

            {item.questionType === "TEXT" && (
              <textarea
                value={answers[item.id] || ""}
                onChange={(e) =>
                  handleChange(item.id, e.target.value)
                }
              />
            )}

            {(item.questionType === "NUMERIC_SCALE" ||
              item.questionType === "RATING") && (
              <div>
                <input
                  type="number"
                  min={item.minScore}
                  max={item.maxScore}
                  value={answers[item.id] || ""}
                  onChange={(e) =>
                    handleChange(item.id, e.target.value)
                  }
                />
                <small style={{ marginLeft: "10px", color: "#666" }}>
                  ({item.minScore} - {item.maxScore})
                </small>
              </div>
            )}

            {item.questionType === "MULTIPLE_CHOICE" && (
              <div className="radio-group">
                {item.choices && item.choices.length > 0 ? (
                  item.choices.map((choice, index) => (
                    <label key={index} className="radio-label">
                      <input
                        type="radio"
                        name={`question-${item.id}`}
                        value={choice}
                        checked={answers[item.id] === choice}
                        onChange={(e) =>
                          handleChange(item.id, e.target.value)
                        }
                      />
                      <span>{choice}</span>
                    </label>
                  ))
                ) : (
                  <p className="error-message">No choices available for this question</p>
                )}
              </div>
            )}
          </div>
        ))}

        {/* General Comments - Only show on last section or if no sections */}
        {(!hasSections || currentSectionIndex === sections.length - 1) && (
          <div className="form-group">
            <label>General Comments</label>
            <textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
            />
          </div>
        )}

        {/* Navigation and Action Buttons */}
        <div className="form-actions" style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
          {hasSections && currentSectionIndex > 0 && (
            <button className="btn-secondary" onClick={handlePrevious}>
              ← Previous Section
            </button>
          )}

          {hasSections && currentSectionIndex < sections.length - 1 && (
            <button 
              className="btn" 
              onClick={handleNext}
              disabled={!isCurrentSectionComplete()}
              style={{
                opacity: isCurrentSectionComplete() ? 1 : 0.5,
                cursor: isCurrentSectionComplete() ? 'pointer' : 'not-allowed'
              }}
            >
              Next Section →
            </button>
          )}

          {(!hasSections || currentSectionIndex === sections.length - 1) && (
            <>
              <button className="btn-secondary" onClick={saveDraft}>
                Save Draft
              </button>
              <button 
                className="btn" 
                onClick={submit}
                disabled={!allAnswered}
                style={{
                  opacity: allAnswered ? 1 : 0.5,
                  cursor: allAnswered ? 'pointer' : 'not-allowed'
                }}
              >
                Submit Evaluation
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default EvaluateForm;
