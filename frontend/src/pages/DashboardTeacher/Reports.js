import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const Reports = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const [questionnaires, setQuestionnaires] = useState([]);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [evaluations, setEvaluations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [aiMessages, setAiMessages] = useState([
    { role: 'assistant', text: 'Hi! Ask me to help you improve a questionnaire or interpret reports.' },
  ]);
  const [aiInput, setAiInput] = useState('');
  const [aiLoading, setAiLoading] = useState(false);

  const token = useMemo(() => {
    try {
      const user = JSON.parse(localStorage.getItem('user'));
      return user?.token || null;
    } catch {
      return null;
    }
  }, []);

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

  const sendAiMessage = async () => {
    const trimmed = aiInput.trim();
    if (!trimmed) return;

    if (trimmed.length > 2000) {
      toast.error('Message is too long (max 2000 characters).');
      return;
    }

    if (!token) {
      toast.error('You are not authenticated. Please log in again.');
      return;
    }

    const history = aiMessages.slice(-12);

    const reportContext = (() => {
      if (!selectedQuestionnaire) return '';
      const total = evaluations.length;
      const submitted = evaluations.filter((e) => e.status === 'SUBMITTED').length;
      const inProgress = total - submitted;

      const recentTeams = evaluations
        .slice(0, 20)
        .map((e) => `${e.teamName || 'Unknown team'}: ${e.status || 'UNKNOWN'}`)
        .join('\n');

      return [
        `Selected questionnaire: ${selectedQuestionnaire.title}`,
        selectedQuestionnaire.description ? `Description: ${selectedQuestionnaire.description}` : '',
        `Evaluations summary: total=${total}, submitted=${submitted}, in_progress=${inProgress}`,
        recentTeams ? `Sample teams/status (up to 20):\n${recentTeams}` : '',
      ]
        .filter(Boolean)
        .join('\n');
    })();
    setAiMessages((prev) => [...prev, { role: 'user', text: trimmed }]);
    setAiInput('');
    setAiLoading(true);

    try {
      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          message: trimmed,
          history,
          context: reportContext,
          contextType: selectedQuestionnaire ? 'reports' : 'reports-list',
        }),
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || `AI request failed (HTTP ${res.status})`);
      }

      setAiMessages((prev) => [...prev, { role: 'assistant', text: data.reply }]);
    } catch (e) {
      toast.error(e.message || 'AI request failed');
      setAiMessages((prev) => [...prev, { role: 'assistant', text: 'Sorry—something went wrong calling the AI.' }]);
    } finally {
      setAiLoading(false);
    }
  };

  const onAiKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!aiLoading) sendAiMessage();
    }
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Evaluation Reports</h1>

        {error && <div className="error-message">{error}</div>}

        {!selectedQuestionnaire ? (
          <div className="section">
            <h2>Select a Questionnaire</h2>
            {loading ? (
              <p>Loading questionnaires...</p>
            ) : questionnaires.length === 0 ? (
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

        <div className="section">
          <h2>AI Assistant</h2>
          <div className="ai-chat">
            <div className="ai-chat-messages">
              {aiMessages.map((m, idx) => (
                <div
                  key={idx}
                  className={`ai-chat-row ${m.role === 'user' ? 'is-user' : 'is-assistant'}`}
                >
                  <div className="ai-chat-bubble">
                    <div className="ai-chat-meta">{m.role === 'user' ? 'You' : 'AI'}</div>
                    <div className="ai-chat-text">{m.text}</div>
                  </div>
                </div>
              ))}
            </div>

            <div className="ai-chat-composer">
              <textarea
                className="form-input ai-chat-input"
                rows={2}
                placeholder="Ask the AI about questionnaires, improvements, or report interpretation..."
                value={aiInput}
                onChange={(e) => setAiInput(e.target.value)}
                onKeyDown={onAiKeyDown}
                disabled={aiLoading}
              />
              <button className="btn btn-primary ai-chat-send" onClick={sendAiMessage} disabled={aiLoading || !aiInput.trim()}>
                {aiLoading ? 'Sending...' : 'Send'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Reports;

