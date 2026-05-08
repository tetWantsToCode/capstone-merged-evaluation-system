import React, { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { CheckCircle2, Clock3 } from "lucide-react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/Pagination/Pagination";
import "./Teacher.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const Reports = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const [questionnaires, setQuestionnaires] = useState([]);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [selectedTeamName, setSelectedTeamName] = useState(null);
  const [evaluations, setEvaluations] = useState([]);
  const [studentEvaluations, setStudentEvaluations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [aiMessages, setAiMessages] = useState([
    { role: 'assistant', text: 'Hi! Ask me to help you improve a questionnaire or interpret reports.' },
  ]);
  const [aiInput, setAiInput] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [isAiOpen, setIsAiOpen] = useState(false);
  const aiMessagesEndRef = useRef(null);
  const restoreHandledRef = useRef(false);

  const token = useMemo(() => {
    try {
      const user = JSON.parse(localStorage.getItem('user'));
      return user?.token || null;
    } catch {
      return null;
    }
  }, []);

  const adviserEvalsForTeam = useMemo(() => evaluations.filter(e => e.teamName === selectedTeamName), [evaluations, selectedTeamName]);
  const studentSelfEvalsForTeam = useMemo(() => studentEvaluations.filter(e => e.teamName === selectedTeamName && e.isSelf), [studentEvaluations, selectedTeamName]);
  const studentPeerEvalsForTeam = useMemo(() => studentEvaluations.filter(e => e.teamName === selectedTeamName && !e.isSelf), [studentEvaluations, selectedTeamName]);

  const { currentPage: curPageQuest, totalPages: totPageQuest, paginatedData: pagQuest, goToPage: goPageQuest } = usePagination(questionnaires, 10);
  const { currentPage: curPageAdv, totalPages: totPageAdv, paginatedData: pagAdv, goToPage: goPageAdv } = usePagination(adviserEvalsForTeam, 10);
  const { currentPage: curPageSelf, totalPages: totPageSelf, paginatedData: pagSelf, goToPage: goPageSelf } = usePagination(studentSelfEvalsForTeam, 10);
  const { currentPage: curPagePeer, totalPages: totPagePeer, paginatedData: pagPeer, goToPage: goPagePeer } = usePagination(studentPeerEvalsForTeam, 10);

  useEffect(() => {
    loadQuestionnaires();
  }, []);

  useEffect(() => {
    if (isAiOpen) {
      aiMessagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [aiMessages, aiLoading, isAiOpen]);

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
      setSelectedTeamName(null); // Reset team selection
      setLoading(true);

      // Fetch both types of evaluations in parallel
      const [adviserData, studentData] = await Promise.all([
        teacherReportAPI.getQuestionnaireEvaluations(questionnaire.id),
        teacherReportAPI.getStudentQuestionnaireEvaluations(questionnaire.id)
      ]);

      setEvaluations(adviserData);
      setStudentEvaluations(studentData);
    } catch (err) {
      setError("Failed to load evaluations: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const backToQuestionnaires = () => {
    setSelectedQuestionnaire(null);
    setSelectedTeamName(null);
    setEvaluations([]);
    setStudentEvaluations([]);
  };

  const backToTeams = () => {
    setSelectedTeamName(null);
  };

  const viewEvaluationDetails = (evaluationId) => {
    navigate(`/teacher/reports/evaluation/${evaluationId}`);
  };

  const viewStudentEvaluationDetails = (evaluationId) => {
    navigate(`/teacher/reports/student-evaluation/${evaluationId}`, {
      state: {
        questionnaireId: selectedQuestionnaire?.id ?? null,
        teamName: selectedTeamName ?? null,
      },
    });
  };

  useEffect(() => {
    const restoreState = location.state;
    if (restoreHandledRef.current || !restoreState?.questionnaireId || !restoreState?.teamName) {
      return;
    }
    if (!questionnaires || questionnaires.length === 0) {
      return;
    }

    const questionnaire = questionnaires.find(
      (q) => String(q.id) === String(restoreState.questionnaireId)
    );
    if (!questionnaire) {
      restoreHandledRef.current = true;
      return;
    }

    restoreHandledRef.current = true;
    (async () => {
      await viewQuestionnaireEvaluations(questionnaire);
      setSelectedTeamName(restoreState.teamName);
    })();
  }, [location.state, questionnaires]);

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
      if (!selectedQuestionnaire) {
        const titles = questionnaires.slice(0, 20).map((q) => q.title).join(', ');
        return [
          `Teacher reports overview`,
          `Total active questionnaires: ${questionnaires.length}`,
          titles ? `Questionnaire titles (up to 20): ${titles}` : '',
          `No specific questionnaire selected yet.`,
        ]
          .filter(Boolean)
          .join('\n');
      }

      const currentAdviserEvals = selectedTeamName
        ? evaluations.filter(e => e.teamName === selectedTeamName)
        : evaluations;

      const currentStudentEvals = selectedTeamName
        ? studentEvaluations.filter(e => e.teamName === selectedTeamName)
        : studentEvaluations;

      const total = currentAdviserEvals.length + currentStudentEvals.length;
      const submitted = currentAdviserEvals.filter((e) => e.status === 'SUBMITTED').length +
        currentStudentEvals.filter(e => e.status === 'SUBMITTED').length;

      const inProgress = total - submitted;
      const progressRate = total > 0 ? ((submitted / total) * 100).toFixed(1) : '0.0';

      return [
        `Selected questionnaire: ${selectedQuestionnaire.title}`,
        selectedTeamName ? `Filtered by Team: ${selectedTeamName}` : `Overview for all teams`,
        `Evaluations summary: total=${total}, submitted=${submitted}, in_progress=${inProgress}`,
        `Progress rate: ${progressRate}%`,
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
              <>
              <table className="class-table">
                <thead>
                  <tr>
                    <th>Questionnaire Title</th>
                    <th>Description</th>
                    <th>Target</th>
                    <th>Created Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {pagQuest.map((q) => (
                    <tr key={q.id}>
                      <td>{q.title}</td>
                      <td>{q.description || "N/A"}</td>
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
                      <td>{new Date(q.createdAt).toLocaleDateString()}</td>
                      <td>
                        <button
                          className="btn"
                          onClick={() => viewQuestionnaireEvaluations(q)}
                        >
                          View Reports
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination currentPage={curPageQuest} totalPages={totPageQuest} onPageChange={goPageQuest} />
              </>
            )}
          </div>
        ) : !selectedTeamName ? (
          <div className="section">
            <div style={{ marginBottom: "20px" }}>
              <button className="btn-secondary" onClick={backToQuestionnaires}>
                ← Back to Questionnaires
              </button>
            </div>

            <h2>{selectedQuestionnaire.title} - Select a Team</h2>

            {loading ? (
              <p>Loading teams...</p>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '20px', marginTop: '20px' }}>
                {Array.from(new Set([
                  ...evaluations.map(e => e.teamName),
                  ...studentEvaluations.map(e => e.teamName)
                ])).filter(Boolean).length === 0 ? (
                  <p className="pending">No teams have data for this questionnaire yet.</p>
                ) : (
                  Array.from(new Set([
                    ...evaluations.map(e => e.teamName),
                    ...studentEvaluations.map(e => e.teamName)
                  ])).filter(Boolean).sort().map(teamName => {
                    const adviserCount = evaluations.filter(e => e.teamName === teamName && e.status === 'SUBMITTED').length;
                    const studentCount = studentEvaluations.filter(e => e.teamName === teamName && e.status === 'SUBMITTED').length;
                    const totalCount = studentEvaluations.filter(e => e.teamName === teamName).length;
                    const adviserSubmitted = adviserCount > 0;

                    return (
                      <div
                        key={teamName}
                        className="evaluation-response-item"
                        style={{ cursor: 'pointer', background: 'rgba(255,255,255,0.05)', padding: '24px', transition: 'transform 0.2s' }}
                        onClick={() => setSelectedTeamName(teamName)}
                        onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-4px)'}
                        onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
                      >
                        <h3 style={{ margin: '0 0 12px 0', color: 'var(--dtm-gold)' }}>{teamName}</h3>
                        <div style={{ fontSize: '0.85rem', color: 'var(--dtm-muted)' }}>
                          {selectedQuestionnaire.target === 'ADVISER' ? (
                            <p className={`report-team-status ${adviserSubmitted ? 'is-submitted' : 'is-pending'}`}>
                              <span className="report-team-status-label">Adviser Eval</span>
                              <span className="report-team-status-value">
                                {adviserSubmitted ? <CheckCircle2 size={14} aria-hidden="true" /> : <Clock3 size={14} aria-hidden="true" />}
                                {adviserSubmitted ? 'Submitted' : 'Pending'}
                              </span>
                            </p>
                          ) : (
                            <p className="report-team-status">
                              <span className="report-team-status-label">Student Evals</span>
                              <span className="report-team-status-value">{studentCount}/{totalCount} Submitted</span>
                            </p>
                          )}
                        </div>
                        <button className="btn btn-assign" style={{ marginTop: '16px', width: '100%', fontSize: '0.85rem' }}>
                          View Team Details
                        </button>
                      </div>
                    );
                  })
                )}
              </div>
            )}
          </div>
        ) : (
          <div className="section">
            <div style={{ marginBottom: "20px", display: 'flex', gap: '10px' }}>
              <button className="btn-secondary" onClick={backToTeams}>
                ← Back to Teams
              </button>
              <button className="btn-secondary" onClick={backToQuestionnaires}>
                Back to Questionnaires
              </button>
            </div>

            <div style={{ marginBottom: '30px' }}>
              <p style={{ color: 'var(--dtm-gold)', fontWeight: 600, margin: 0 }}>Questionnaire: {selectedQuestionnaire.title}</p>
              <h2 style={{ margin: '4px 0' }}>Team: {selectedTeamName}</h2>
            </div>

            {loading ? (
              <p>Loading evaluations...</p>
            ) : (
              <>
                {selectedQuestionnaire.target === 'ADVISER' ? (
                  <div style={{ marginBottom: '40px' }}>
                    <h3 style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '10px' }}>Adviser Evaluation</h3>
                    {adviserEvalsForTeam.length === 0 ? (
                      <p className="pending">No adviser evaluation for this team yet.</p>
                    ) : (
                      <>
                      <table className="class-table">
                        <thead>
                          <tr>
                            <th>Adviser Name</th>
                            <th>Status</th>
                            <th>Submitted Date</th>
                            <th>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {pagAdv.map((evaluation) => (
                            <tr key={evaluation.id}>
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
                      <Pagination currentPage={curPageAdv} totalPages={totPageAdv} onPageChange={goPageAdv} />
                      </>
                    )}
                  </div>
                ) : (
                  <>
                    <div style={{ marginBottom: '40px' }}>
                      <h3 style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '10px' }}>Student Self-Evaluations</h3>
                      {studentSelfEvalsForTeam.length === 0 ? (
                        <p className="pending">No student self-evaluations for this team yet.</p>
                      ) : (
                        <>
                        <table className="class-table">
                          <thead>
                            <tr>
                              <th>Student Name</th>
                              <th>Status</th>
                              <th>Answers</th>
                              <th>Avg Score</th>
                              <th>Submitted Date</th>
                              <th>Action</th>
                            </tr>
                          </thead>
                          <tbody>
                            {pagSelf.map((evaluation) => (
                              <tr key={evaluation.id}>
                                <td>{evaluation.evaluatorName}</td>
                                <td>
                                  <span className={evaluation.status === "SUBMITTED" ? "completed" : "pending"}>
                                    {evaluation.status === "SUBMITTED" ? "Submitted" : "In Progress"}
                                  </span>
                                </td>
                                <td>{evaluation.scoreCount}</td>
                                <td>
                                  {evaluation.averageScore !== null && evaluation.averageScore !== undefined
                                    ? <strong style={{ color: 'var(--dtm-gold)' }}>{evaluation.averageScore}</strong>
                                    : "N/A"}
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
                                      onClick={() => viewStudentEvaluationDetails(evaluation.id)}
                                    >
                                      View Details
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                        <Pagination currentPage={curPageSelf} totalPages={totPageSelf} onPageChange={goPageSelf} />
                        </>
                      )}
                    </div>

                    <div>
                      <h3 style={{ borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '10px' }}>Student Peer Evaluations</h3>
                      {studentPeerEvalsForTeam.length === 0 ? (
                        <p className="pending">No peer-to-peer evaluations for this team yet.</p>
                      ) : (
                        <>
                        <table className="class-table">
                          <thead>
                            <tr>
                              <th>Evaluator</th>
                              <th>Evaluatee (Peer)</th>
                              <th>Status</th>
                              <th>Answers</th>
                              <th>Avg Score</th>
                              <th>Submitted Date</th>
                              <th>Action</th>
                            </tr>
                          </thead>
                          <tbody>
                            {pagPeer.map((evaluation) => (
                              <tr key={evaluation.id}>
                                <td>{evaluation.evaluatorName}</td>
                                <td>{evaluation.evaluateeName}</td>
                                <td>
                                  <span className={evaluation.status === "SUBMITTED" ? "completed" : "pending"}>
                                    {evaluation.status === "SUBMITTED" ? "Submitted" : "In Progress"}
                                  </span>
                                </td>
                                <td>{evaluation.scoreCount}</td>
                                <td>
                                  {evaluation.averageScore !== null && evaluation.averageScore !== undefined
                                    ? <strong style={{ color: 'var(--dtm-gold)' }}>{evaluation.averageScore}</strong>
                                    : "N/A"}
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
                                      onClick={() => viewStudentEvaluationDetails(evaluation.id)}
                                    >
                                      View Details
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                        <Pagination currentPage={curPagePeer} totalPages={totPagePeer} onPageChange={goPagePeer} />
                        </>
                      )}
                    </div>
                  </>
                )}
              </>
            )}
          </div>
        )}

        <button
          className="ai-fab"
          onClick={() => setIsAiOpen((prev) => !prev)}
          aria-label="Open AI assistant"
          title="AI Assistant"
        >
          AI
        </button>

        {isAiOpen && (
          <div className="ai-fab-panel" role="dialog" aria-label="AI assistant chat">
            <div className="ai-fab-header">
              <div>
                <strong>AI Report Assistant</strong>
                <p>Ask for progress, insights, and next actions.</p>
              </div>
              <button className="btn-secondary" onClick={() => setIsAiOpen(false)}>
                Close
              </button>
            </div>

            <div className="ai-chat ai-chat-fab">
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

                {aiLoading && (
                  <div className="ai-chat-row is-assistant ai-chat-typing">
                    <div className="ai-chat-bubble">
                      <div className="ai-chat-meta">AI</div>
                      <div className="ai-typing-dots" aria-label="AI is typing" role="status">
                        <span></span>
                        <span></span>
                        <span></span>
                      </div>
                    </div>
                  </div>
                )}

                <div ref={aiMessagesEndRef}></div>
              </div>

              <div className="ai-chat-composer">
                <textarea
                  className="form-input ai-chat-input"
                  rows={2}
                  placeholder="Ask for summary, trends, weak areas, and recommendations..."
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
        )}
      </div>
    </div>
  );
};

export default Reports;
