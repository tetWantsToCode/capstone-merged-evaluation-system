import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import "./Teacher.css";

const Reports = () => {
  const navigate = useNavigate();
  const [questionnaires, setQuestionnaires] = useState([]);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [evaluations, setEvaluations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadQuestionnaires();
  }, []);

  const loadQuestionnaires = async () => {
    try {
      setLoading(true);
      const data = await teacherReportAPI.getQuestionnaires();
      setQuestionnaires(data);
    } catch (err) {
      setError("Failed to load questionnaires: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const viewQuestionnaireEvaluations = async (questionnaire) => {
    try {
      setSelectedQuestionnaire(questionnaire);
      setLoading(true);
      const data = await teacherReportAPI.getQuestionnaireEvaluations(questionnaire.id);
      setEvaluations(data);
    } catch (err) {
      setError("Failed to load evaluations: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const backToQuestionnaires = () => {
    setSelectedQuestionnaire(null);
    setEvaluations([]);
  };

  const viewEvaluationDetails = (evaluationId) => {
    navigate(`/teacher/reports/evaluation/${evaluationId}`);
  };

  if (loading && !selectedQuestionnaire) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content">
          <p>Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Evaluation Reports</h1>

        {error && <div className="error-message">{error}</div>}

        {!selectedQuestionnaire ? (
          <div className="section">
            <h2>Select a Questionnaire</h2>
            {questionnaires.length === 0 ? (
              <p>No questionnaires found. Create a questionnaire first.</p>
            ) : (
              <table className="class-table">
                <thead>
                  <tr>
                    <th>Questionnaire Title</th>
                    <th>Description</th>
                    <th>Created Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {questionnaires.map((q) => (
                    <tr key={q.id}>
                      <td>{q.title}</td>
                      <td>{q.description || "N/A"}</td>
                      <td>{new Date(q.createdAt).toLocaleDateString()}</td>
                      <td>
                        <button
                          className="btn"
                          onClick={() => viewQuestionnaireEvaluations(q)}
                        >
                          View Evaluations
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        ) : (
          <div className="section">
            <div style={{ marginBottom: "20px" }}>
              <button className="btn-secondary" onClick={backToQuestionnaires}>
                ← Back to Questionnaires
              </button>
            </div>

            <h2>{selectedQuestionnaire.title} - Evaluations</h2>

            {loading ? (
              <p>Loading evaluations...</p>
            ) : evaluations.length === 0 ? (
              <p>No evaluations submitted yet for this questionnaire.</p>
            ) : (
              <table className="class-table">
                <thead>
                  <tr>
                    <th>Team Name</th>
                    <th>Adviser Name</th>
                    <th>Status</th>
                    <th>Submitted Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {evaluations.map((evaluation) => (
                    <tr key={evaluation.id}>
                      <td>{evaluation.teamName}</td>
                      <td>{evaluation.adviserName}</td>
                      <td>
                        <span className={evaluation.status === "SUBMITTED" ? "completed" : "pending"}>
                          {evaluation.status === "SUBMITTED" ? "Submitted" : "In Progress"}
                        </span>
                      </td>
                      <td>
                        {evaluation.submittedAt
                          ? new Date(evaluation.submittedAt).toLocaleDateString()
                          : "Not submitted"}
                      </td>
                      <td>
                        {evaluation.status === "SUBMITTED" && (
                          <button
                            className="btn"
                            onClick={() => viewEvaluationDetails(evaluation.id)}
                          >
                            View Details
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default Reports;

