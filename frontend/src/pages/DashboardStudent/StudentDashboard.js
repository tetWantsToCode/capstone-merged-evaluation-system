import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import StudentSidebar from "../../components/Sidebar/StudentSidebar";
import SummaryCard from "../../components/Cards/SummaryCard";
import { apeerActivityAPI, apeerSubmissionAPI, apeerStudentInsightsAPI } from "../../services/apeerApi";
import { authAPI } from "../../services/api";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import "../DashboardTeacher/Teacher.css";

const StudentDashboard = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activities, setActivities] = useState([]);
  const [statusMap, setStatusMap] = useState({});
  const [history, setHistory] = useState([]);
  const [aiSummary, setAiSummary] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);
  const [user] = useState(() => authAPI.getCurrentUser());

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const [list, hist] = await Promise.all([
          apeerActivityAPI.getActivitiesForStudent(),
          apeerStudentInsightsAPI.getHistory().catch(() => []),
        ]);
        setActivities(list || []);
        setHistory(hist || []);
        const map = {};
        for (const a of list || []) {
          try {
            const st = await apeerSubmissionAPI.getSubmissionStatus(a.id);
            map[a.id] = st;
          } catch {
            map[a.id] = { submittedCount: 0, totalMembers: 0, complete: false };
          }
        }
        setStatusMap(map);
      } catch (e) {
        setError(e?.message || "Failed to load evaluations");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const loadAiSummary = async () => {
    setAiLoading(true);
    setAiError(null);
    try {
      const data = await apeerStudentInsightsAPI.getAiSummary();
      setAiSummary(data);
    } catch (e) {
      setAiError(e?.message || "Failed to load AI summary");
    } finally {
      setAiLoading(false);
    }
  };

  const activeActivities = (activities || []).filter((a) => a.isActive);
  const completedCount = Object.values(statusMap).filter((s) => s.complete).length;

  const chartData = history.map((h) => ({
    name: h.activityTitle ? (h.activityTitle.length > 20 ? h.activityTitle.slice(0, 18) + "…" : h.activityTitle) : `Eval #${h.id}`,
    Score: h.totalScore || 0,
  }));

  const getInitials = () => {
    const f = user?.firstName?.[0] || '';
    const l = user?.lastName?.[0] || '';
    return (f + l).toUpperCase() || user?.email?.[0]?.toUpperCase() || '?';
  };

  return (
    <div className="teacher-container">
      <StudentSidebar />
      <div className="teacher-content">
        <h1>Student Dashboard</h1>

        {/* Account Info — avatar + fields */}
        <div className="section" style={{ display: "flex", alignItems: "center", gap: 24 }}>
          <div style={{
            width: 64, height: 64, borderRadius: "50%", flexShrink: 0,
            background: "linear-gradient(135deg, #f2c94c, #d4a843)",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 22, fontWeight: 700, color: "#1a0a0c",
            boxShadow: "0 4px 16px rgba(242,201,76,0.35)",
          }}>
            {getInitials()}
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: "var(--dtm-gold)", marginBottom: 4 }}>
              {user?.firstName} {user?.lastName}
            </div>
            <div style={{ fontSize: 13, color: "var(--dtm-muted)", marginBottom: 2 }}>{user?.email}</div>
            <span style={{
              display: "inline-block", fontSize: 11, fontWeight: 700, letterSpacing: 1,
              padding: "3px 10px", borderRadius: 999,
              background: "rgba(138,21,31,0.35)", border: "1px solid rgba(138,21,31,0.5)",
              color: "var(--dtm-gold)",
            }}>{user?.role}</span>
          </div>
        </div>

        <div className="summary-row">
          <SummaryCard title="Active Evaluations" value={loading ? "-" : String(activeActivities.length)} icon="📝" />
          <SummaryCard title="Completed" value={loading ? "-" : String(completedCount)} icon="✅" />
          <SummaryCard title="Total Received" value={loading ? "-" : String(history.length)} icon="📊" />
        </div>

        {error && <div className="error-message">{error}</div>}

        {/* Peer Evaluations */}
        <div className="section">
          <h2>Peer Evaluations</h2>
          {loading ? (
            <p style={{ color: "var(--dtm-muted)" }}>Loading...</p>
          ) : activeActivities.length === 0 ? (
            <p style={{ color: "var(--dtm-muted)" }}>No active peer evaluations at the moment.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {activeActivities.map((activity) => {
                const st = statusMap[activity.id] || {};
                const submitted = st.complete;
                const isPastDeadline = activity.deadline && new Date() > new Date(activity.deadline);
                const progress = st.totalMembers > 0 ? Math.round((st.submittedCount / st.totalMembers) * 100) : 0;
                return (
                  <div
                    key={activity.id}
                    style={{
                      padding: "16px 20px",
                      background: submitted
                        ? "rgba(39,174,96,0.08)"
                        : isPastDeadline
                        ? "rgba(138,21,31,0.12)"
                        : "rgba(255,255,255,0.04)",
                      border: `1px solid ${submitted ? "rgba(39,174,96,0.25)" : isPastDeadline ? "rgba(176,0,32,0.3)" : "rgba(255,255,255,0.08)"}`,
                      borderRadius: 12,
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 16,
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 6 }}>
                        <strong style={{ color: "var(--dtm-text)", fontSize: 15 }}>{activity.title}</strong>
                        {submitted && (
                          <span style={{
                            fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 999,
                            background: "rgba(39,174,96,0.2)", border: "1px solid rgba(39,174,96,0.4)",
                            color: "#4cd97b",
                          }}>✓ Completed</span>
                        )}
                        {isPastDeadline && !submitted && (
                          <span style={{
                            fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 999,
                            background: "rgba(176,0,32,0.2)", border: "1px solid rgba(176,0,32,0.4)",
                            color: "#ff7b7b",
                          }}>Closed</span>
                        )}
                      </div>
                      <div style={{ fontSize: 13, color: isPastDeadline ? "#ff7b7b" : "var(--dtm-muted)" }}>
                        Due: {activity.deadline ? new Date(activity.deadline).toLocaleDateString() : "—"}
                      </div>
                      {!submitted && st.totalMembers > 0 && (
                        <div style={{ marginTop: 8 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--dtm-muted)", marginBottom: 4 }}>
                            <span>Progress</span>
                            <span>{st.submittedCount || 0}/{st.totalMembers} submitted</span>
                          </div>
                          <div style={{ height: 4, background: "rgba(255,255,255,0.08)", borderRadius: 99 }}>
                            <div style={{
                              height: "100%", width: `${progress}%`, borderRadius: 99,
                              background: "linear-gradient(90deg, #8a151f, #f2c94c)",
                              transition: "width 0.4s ease",
                            }} />
                          </div>
                        </div>
                      )}
                    </div>
                    {!submitted && !isPastDeadline && (
                      <button
                        type="button"
                        className="btn btn-primary"
                        style={{ flexShrink: 0 }}
                        onClick={() => navigate(`/student/evaluation/${activity.id}`)}
                      >
                        {st.submittedCount > 0 ? "Continue" : "Start"}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Score Trends Chart */}
        {history.length > 0 && (
          <div className="section">
            <h2>My Score Trends</h2>
            <p style={{ color: "var(--dtm-muted)", fontSize: 13, marginBottom: 16 }}>Total peer evaluation scores received across activities.</p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: "#a09890" }} axisLine={{ stroke: "rgba(255,255,255,0.1)" }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: "#a09890" }} axisLine={{ stroke: "rgba(255,255,255,0.1)" }} />
                <Tooltip
                  contentStyle={{ background: "#3b252b", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 10, color: "#f5f0eb" }}
                  cursor={{ fill: "rgba(242,201,76,0.06)" }}
                />
                <Bar dataKey="Score" fill="url(#scoreGrad)" radius={[4, 4, 0, 0]} />
                <defs>
                  <linearGradient id="scoreGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#f2c94c" stopOpacity={0.9} />
                    <stop offset="100%" stopColor="#8a151f" stopOpacity={0.9} />
                  </linearGradient>
                </defs>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Evaluation History Table */}
        {history.length > 0 && (
          <div className="section">
            <h2>Evaluation History</h2>
            <table className="class-table">
              <thead>
                <tr>
                  <th>Activity</th>
                  <th>Total Score</th>
                  <th>Date Received</th>
                </tr>
              </thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.id}>
                    <td>{h.activityTitle || `Activity #${h.activityId}`}</td>
                    <td>
                      <span style={{
                        fontWeight: 700, color: "var(--dtm-gold)",
                        background: "rgba(242,201,76,0.1)", padding: "3px 10px",
                        borderRadius: 999, fontSize: 13,
                      }}>{h.totalScore}</span>
                    </td>
                    <td>{h.submittedAt ? new Date(h.submittedAt).toLocaleDateString() : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* AI Feedback Summary */}
        <div className="section">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
            <h2 style={{ margin: 0 }}>AI Feedback Summary</h2>
            <button type="button" className="btn btn-primary" onClick={loadAiSummary} disabled={aiLoading}>
              {aiLoading ? "Generating…" : aiSummary ? "↺ Refresh" : "✦ Generate"}
            </button>
          </div>

          {aiError && <div className="error-message">{aiError}</div>}

          {!aiSummary && !aiLoading && (
            <p style={{ color: "var(--dtm-muted)", fontSize: 14 }}>
              Click <strong style={{ color: "var(--dtm-gold)" }}>Generate</strong> to get a personalized AI summary of your peer feedback — strengths, areas for improvement, and suggestions.
            </p>
          )}

          {aiSummary && aiSummary.status === "no_data" && (
            <p style={{ color: "var(--dtm-muted)" }}>{aiSummary.message}</p>
          )}

          {aiSummary && aiSummary.status === "success" && (
            <div>
              <p style={{ color: "var(--dtm-muted)", fontSize: 13, marginBottom: 16 }}>
                Based on <strong style={{ color: "var(--dtm-text)" }}>{aiSummary.totalEvaluations}</strong> peer evaluation(s) received.
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 16 }}>
                <div style={{
                  background: "rgba(39,174,96,0.08)", borderRadius: 12, padding: 16,
                  borderLeft: "4px solid #27ae60", border: "1px solid rgba(39,174,96,0.2)",
                  borderLeftWidth: 4, borderLeftColor: "#27ae60",
                }}>
                  <h4 style={{ color: "#4cd97b", marginBottom: 10, fontSize: 14, fontWeight: 700 }}>✓ Strengths</h4>
                  {(aiSummary.strengths || []).length === 0
                    ? <p style={{ color: "var(--dtm-muted)", fontSize: 13 }}>None identified yet.</p>
                    : <ul style={{ margin: 0, paddingLeft: 16 }}>
                        {(aiSummary.strengths || []).map((s, i) => (
                          <li key={i} style={{ fontSize: 13, marginBottom: 6, color: "var(--dtm-text)", lineHeight: 1.5 }}>{s}</li>
                        ))}
                      </ul>
                  }
                </div>
                <div style={{
                  background: "rgba(245,158,11,0.08)", borderRadius: 12, padding: 16,
                  border: "1px solid rgba(245,158,11,0.2)", borderLeftWidth: 4, borderLeftColor: "#f59e0b",
                }}>
                  <h4 style={{ color: "#fbbf24", marginBottom: 10, fontSize: 14, fontWeight: 700 }}>⚠ Areas to Improve</h4>
                  {(aiSummary.weaknesses || []).length === 0
                    ? <p style={{ color: "var(--dtm-muted)", fontSize: 13 }}>None identified yet.</p>
                    : <ul style={{ margin: 0, paddingLeft: 16 }}>
                        {(aiSummary.weaknesses || []).map((w, i) => (
                          <li key={i} style={{ fontSize: 13, marginBottom: 6, color: "var(--dtm-text)", lineHeight: 1.5 }}>{w}</li>
                        ))}
                      </ul>
                  }
                </div>
                <div style={{
                  background: "rgba(59,130,246,0.08)", borderRadius: 12, padding: 16,
                  border: "1px solid rgba(59,130,246,0.2)", borderLeftWidth: 4, borderLeftColor: "#3b82f6",
                }}>
                  <h4 style={{ color: "#60a5fa", marginBottom: 10, fontSize: 14, fontWeight: 700 }}>💡 Suggestions</h4>
                  {(aiSummary.suggestions || []).length === 0
                    ? <p style={{ color: "var(--dtm-muted)", fontSize: 13 }}>No suggestions yet.</p>
                    : <ul style={{ margin: 0, paddingLeft: 16 }}>
                        {(aiSummary.suggestions || []).map((sg, i) => (
                          <li key={i} style={{ fontSize: 13, marginBottom: 6, color: "var(--dtm-text)", lineHeight: 1.5 }}>{sg}</li>
                        ))}
                      </ul>
                  }
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default StudentDashboard;
