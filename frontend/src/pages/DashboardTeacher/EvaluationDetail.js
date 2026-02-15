import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import "./Teacher.css";

const EvaluationDetail = () => {
  const { evaluationId } = useParams();
  const navigate = useNavigate();
  const [evaluation, setEvaluation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadEvaluation();
  }, [evaluationId]);

  const loadEvaluation = async () => {
    try {
      setLoading(true);
      const data = await teacherReportAPI.getEvaluationDetails(evaluationId);
      setEvaluation(data);
    } catch (err) {
      setError("Failed to load evaluation: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content">
          <p>Loading evaluation...</p>
        </div>
      </div>
    );
  }

  if (error || !evaluation) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content">
          <div className="error-message">{error || "Evaluation not found"}</div>
          <button className="btn-secondary" onClick={() => navigate("/teacher/reports")}>
            Back to Reports
          </button>
        </div>
      </div>
    );
  }

  const getAnswerForQuestion = (questionId) => {
    const score = evaluation.scores.find(s => s.questionnaireItemId === questionId);
    if (!score) return "Not answered";
    return score.numericScore !== null ? score.numericScore : score.textResponse;
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <div style={{ marginBottom: "20px" }}>
          <button className="btn-secondary" onClick={() => navigate("/teacher/reports")}>
            ← Back to Reports
          </button>
        </div>

        <h1>Evaluation Details</h1>

        <div className="section">
          <h2>Evaluation Information</h2>
          <div style={{ marginBottom: "20px" }}>
            <p><strong>Questionnaire:</strong> {evaluation.questionnaire.title}</p>
            <p><strong>Team:</strong> {evaluation.teamName}</p>
            <p><strong>Adviser:</strong> {evaluation.adviserName}</p>
            <p><strong>Status:</strong> <span className={evaluation.status === "SUBMITTED" ? "completed" : "pending"}>
              {evaluation.status}
            </span></p>
            <p><strong>Submitted:</strong> {evaluation.submittedAt 
              ? new Date(evaluation.submittedAt).toLocaleString()
              : "Not submitted"}</p>
          </div>
        </div>

        <div className="section">
          <h2>Responses</h2>
          {evaluation.questionnaire.items && evaluation.questionnaire.items.length > 0 ? (
            evaluation.questionnaire.items.map((item, index) => (
              <div key={item.id} className="evaluation-item" style={{
                padding: "16px",
                marginBottom: "16px",
                background: "#f9f9f9",
                borderRadius: "8px",
                border: "1px solid #e8e8e8"
              }}>
                <div style={{ marginBottom: "8px" }}>
                  <strong>Question {index + 1}:</strong> {item.questionText}
                </div>
                <div style={{ 
                  padding: "12px", 
                  background: "#ffffff", 
                  borderRadius: "4px",
                  border: "1px solid #e0e0e0"
                }}>
                  <strong>Answer:</strong> {getAnswerForQuestion(item.id)}
                  {item.questionType === "NUMERIC_SCALE" || item.questionType === "RATING" ? (
                    <span style={{ color: "#666", marginLeft: "8px" }}>
                      (Scale: {item.minScore} - {item.maxScore})
                    </span>
                  ) : null}
                </div>
              </div>
            ))
          ) : (
            <p>No questions in this questionnaire.</p>
          )}
        </div>

        {evaluation.generalComments && (
          <div className="section">
            <h2>General Comments</h2>
            <div style={{
              padding: "16px",
              background: "#f9f9f9",
              borderRadius: "8px",
              border: "1px solid #e8e8e8",
              whiteSpace: "pre-wrap"
            }}>
              {evaluation.generalComments}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EvaluationDetail;
