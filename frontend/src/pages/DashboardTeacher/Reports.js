import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import "./Teacher.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const Reports = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const [questionnaires, setQuestionnaires] = useState([]);
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [evaluations, setEvaluations] = useState([]);
  const [rankings, setRankings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [aiMessages, setAiMessages] = useState([
    { role: 'assistant', text: 'Hi! Ask me to help you improve a questionnaire or interpret reports.' },
  ]);
  const [aiInput, setAiInput] = useState('');
  const [aiLoading, setAiLoading] = useState(false);

  const [analyticsResult, setAnalyticsResult] = useState(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

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
      setRankings([]);
      const [data, rankData] = await Promise.all([
        teacherReportAPI.getQuestionnaireEvaluations(questionnaire.id),
        teacherReportAPI.getRankings(questionnaire.id).catch(() => []),
      ]);
      setEvaluations(data);
      setRankings(rankData || []);
    } catch (err) {
      setError("Failed to load evaluations: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const backToQuestionnaires = () => {
    setSelectedQuestionnaire(null);
    setEvaluations([]);
    setRankings([]);
    setAnalyticsResult(null);
  };

  const generateAnalytics = async () => {
    if (!selectedQuestionnaire || !token) return;
    setAnalyticsLoading(true);
    setAnalyticsResult(null);
    try {
      const res = await fetch(`${API_BASE_URL}/ai/analytics`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ mode: 'evaluation_summary', questionnaireId: selectedQuestionnaire.id }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new Error(data?.message || `Request failed (HTTP ${res.status})`);
      setAnalyticsResult(data);
    } catch (e) {
      toast.error(e.message || 'Analytics request failed');
    } finally {
      setAnalyticsLoading(false);
    }
  };

  const exportPdf = () => {
    if (!selectedQuestionnaire) return;
    const win = window.open('', '_blank');
    const rankRows = rankings.map((r) =>
      `<tr><td>#${r.rank}</td><td>${r.teamName}</td><td>${r.adviserName || '—'}</td><td>${r.totalScore}</td><td>${r.averageScore}</td></tr>`
    ).join('');
    const evalRows = evaluations.map((e) =>
      `<tr><td>${e.teamName}</td><td>${e.adviserName}</td><td>${e.status === 'SUBMITTED' ? 'Submitted' : 'In Progress'}</td><td>${e.submittedAt ? new Date(e.submittedAt).toLocaleDateString() : '—'}</td></tr>`
    ).join('');
    win.document.write(`<!DOCTYPE html><html><head><title>Report — ${selectedQuestionnaire.title}</title>
      <style>body{font-family:Arial,sans-serif;padding:32px;color:#111}h1{font-size:20px;margin-bottom:4px}h2{font-size:15px;margin:20px 0 8px;color:#333}p{color:#555;font-size:13px;margin:0 0 16px}table{width:100%;border-collapse:collapse;font-size:13px}th{background:#8a151f;color:#fff;padding:8px 12px;text-align:left}td{padding:8px 12px;border-bottom:1px solid #ddd}tr:nth-child(even) td{background:#f9f9f9}@media print{body{padding:16px}}</style>
      </head><body>
      <h1>Evaluation Report</h1>
      <p>Questionnaire: <strong>${selectedQuestionnaire.title}</strong>${selectedQuestionnaire.description ? ` — ${selectedQuestionnaire.description}` : ''}</p>
      <p>Generated: ${new Date().toLocaleString()}</p>
      ${rankings.length > 0 ? `<h2>Team Performance Rankings</h2><table><thead><tr><th>Rank</th><th>Team</th><th>Adviser</th><th>Total Score</th><th>Avg Score</th></tr></thead><tbody>${rankRows}</tbody></table>` : ''}
      <h2>All Evaluations</h2>
      <table><thead><tr><th>Team</th><th>Adviser</th><th>Status</th><th>Submitted</th></tr></thead><tbody>${evalRows}</tbody></table>
      </body></html>`);
    win.document.close();
    win.focus();
    setTimeout(() => win.print(), 400);
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
      ].filter(Boolean).join('\n');
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
                        <button className="btn" onClick={() => viewQuestionnaireEvaluations(q)}>
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
            <div style={{ marginBottom: "20px", display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button className="btn btn-secondary" onClick={backToQuestionnaires}>
                ← Back to Questionnaires
              </button>
              <button
                className="btn btn-primary"
                onClick={generateAnalytics}
                disabled={analyticsLoading}
              >
                {analyticsLoading ? 'Generating...' : '✨ AI Analysis'}
              </button>
            </div>

            {analyticsResult && (
              <div style={{ background: 'rgba(138,21,31,0.12)', border: '1px solid rgba(242,201,76,0.25)', borderRadius: 10, padding: '18px 20px', marginBottom: 20 }}>
                <h3 style={{ margin: '0 0 12px 0', color: 'var(--dtm-gold)', fontSize: 15 }}>✨ AI Evaluation Analysis</h3>
                {analyticsResult.summary && (
                  <p style={{ margin: '0 0 10px 0', fontSize: 13, lineHeight: 1.7, color: 'var(--dtm-text)' }}>{analyticsResult.summary}</p>
                )}
                {analyticsResult.strengths?.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    <strong style={{ color: '#4cd97b', fontSize: 12 }}>Strengths</strong>
                    <ul style={{ margin: '4px 0 0 16px', padding: 0, fontSize: 12, color: 'var(--dtm-text)' }}>
                      {analyticsResult.strengths.map((s, i) => <li key={i}>{s}</li>)}
                    </ul>
                  </div>
                )}
                {analyticsResult.improvements?.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    <strong style={{ color: '#f2c94c', fontSize: 12 }}>Areas for Improvement</strong>
                    <ul style={{ margin: '4px 0 0 16px', padding: 0, fontSize: 12, color: 'var(--dtm-text)' }}>
                      {analyticsResult.improvements.map((s, i) => <li key={i}>{s}</li>)}
                    </ul>
                  </div>
                )}
                {analyticsResult.recommendations?.length > 0 && (
                  <div>
                    <strong style={{ color: '#a09890', fontSize: 12 }}>Recommendations</strong>
                    <ul style={{ margin: '4px 0 0 16px', padding: 0, fontSize: 12, color: 'var(--dtm-text)' }}>
                      {analyticsResult.recommendations.map((s, i) => <li key={i}>{s}</li>)}
                    </ul>
                  </div>
                )}
                {!analyticsResult.summary && !analyticsResult.strengths && (
                  <pre style={{ fontSize: 12, color: 'var(--dtm-muted)', whiteSpace: 'pre-wrap' }}>{JSON.stringify(analyticsResult, null, 2)}</pre>
                )}
              </div>
            )}

            <h2>{selectedQuestionnaire.title} - Evaluations</h2>

            {loading ? (
              <p>Loading evaluations...</p>
            ) : evaluations.length === 0 ? (
              <p>No evaluations submitted yet for this questionnaire.</p>
            ) : (
              <>
                {rankings.length > 0 && (
                  <>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, marginTop: 20 }}>
                      <h3 style={{ margin: 0, color: "var(--dtm-gold)", fontSize: 16, fontWeight: 700 }}>Team Performance Rankings</h3>
                      <div style={{ display: "flex", gap: 8 }}>
                        <button
                          className="btn btn-secondary"
                          onClick={() => teacherReportAPI.exportCsv(selectedQuestionnaire.id)}
                        >
                          ⬇ Export CSV
                        </button>
                        <button
                          className="btn btn-secondary"
                          onClick={exportPdf}
                        >
                          🖨 Export PDF
                        </button>
                      </div>
                    </div>
                    <ResponsiveContainer width="100%" height={220}>
                      <BarChart
                        data={rankings.map(r => ({ name: r.teamName, Score: r.totalScore }))}
                        margin={{ top: 4, right: 16, left: 0, bottom: 4 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                        <XAxis dataKey="name" tick={{ fontSize: 12, fill: "#a09890" }} axisLine={{ stroke: "rgba(255,255,255,0.1)" }} />
                        <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: "#a09890" }} axisLine={{ stroke: "rgba(255,255,255,0.1)" }} />
                        <Tooltip contentStyle={{ background: "#3b252b", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 10, color: "#f5f0eb" }} cursor={{ fill: "rgba(242,201,76,0.06)" }} />
                        <defs>
                          <linearGradient id="rankGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#f2c94c" stopOpacity={0.9} />
                            <stop offset="100%" stopColor="#8a151f" stopOpacity={0.9} />
                          </linearGradient>
                        </defs>
                        <Bar dataKey="Score" fill="url(#rankGrad)" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                    <table className="class-table" style={{ marginTop: 16 }}>
                      <thead>
                        <tr>
                          <th>Rank</th>
                          <th>Team</th>
                          <th>Adviser</th>
                          <th>Total Score</th>
                          <th>Average Score</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rankings.map((r) => (
                          <tr key={r.teamId}>
                            <td><strong>#{r.rank}</strong></td>
                            <td>{r.teamName}</td>
                            <td>{r.adviserName}</td>
                            <td>{r.totalScore}</td>
                            <td>{r.averageScore}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <h3 style={{ marginTop: 24, marginBottom: 12, color: "var(--dtm-gold)", fontSize: 16, fontWeight: 700 }}>All Evaluations</h3>
                  </>
                )}
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
                            <button className="btn" onClick={() => viewEvaluationDetails(evaluation.id)}>
                              View Details
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}
          </div>
        )}

        <div className="section">
          <h2>AI Assistant</h2>
          <div className="ai-chat">
            <div className="ai-chat-messages">
              {aiMessages.map((m, idx) => (
                <div key={idx} className={`ai-chat-row ${m.role === 'user' ? 'is-user' : 'is-assistant'}`}>
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
              <button
                className="btn btn-primary ai-chat-send"
                onClick={sendAiMessage}
                disabled={aiLoading || !aiInput.trim()}
              >
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
