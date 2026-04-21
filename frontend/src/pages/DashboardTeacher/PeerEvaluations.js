import React, { useEffect, useMemo, useState } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import SummaryCard from "../../components/Cards/SummaryCard";
import { apeerActivityAPI, apeerClassAPI } from "../../services/apeerApi";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const AI_TAG_STYLES = {
  Constructive: { background: 'rgba(39,174,96,0.15)', color: '#4cd97b', border: '1px solid rgba(39,174,96,0.3)' },
  Vague:        { background: 'rgba(242,201,76,0.15)', color: '#f2c94c', border: '1px solid rgba(242,201,76,0.3)' },
  'Off-topic':  { background: 'rgba(176,0,32,0.15)',  color: '#ff7b7b', border: '1px solid rgba(176,0,32,0.3)' },
};

const PeerEvaluations = () => {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activities, setActivities] = useState([]);
  const [classes, setClasses] = useState([]);
  const [showCreate, setShowCreate] = useState(false);
  const [drillDown, setDrillDown] = useState(null);
  const [drillLoading, setDrillLoading] = useState(false);
  const [drillError, setDrillError] = useState(null);
  const [studentAiSummaries, setStudentAiSummaries] = useState({});
  const [studentAiLoading, setStudentAiLoading] = useState({});

  const token = useMemo(() => {
    try { return JSON.parse(localStorage.getItem('user'))?.token || null; } catch { return null; }
  }, []);
  const [createTitle, setCreateTitle] = useState("");
  const [createCriteria, setCreateCriteria] = useState("");
  const [createDeadline, setCreateDeadline] = useState("");
  const [createClassId, setCreateClassId] = useState("");

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const [acts, cls] = await Promise.all([
          apeerActivityAPI.getActivities(),
          apeerClassAPI.getAllClasses(),
        ]);
        setActivities(acts || []);
        setClasses(cls || []);
      } catch (e) {
        setError(e?.message || "Failed to load");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      const rubricCriteria = createCriteria
        .split(/[,;]/)
        .map((s) => s.trim())
        .filter(Boolean);
      await apeerActivityAPI.createActivity({
        title: createTitle,
        rubricCriteria,
        deadline: createDeadline || null,
        classId: createClassId ? Number(createClassId) : null,
      });
      setShowCreate(false);
      setCreateTitle("");
      setCreateCriteria("");
      setCreateDeadline("");
      setCreateClassId("");
      const acts = await apeerActivityAPI.getActivities();
      setActivities(acts || []);
    } catch (err) {
      setError(err?.message || "Failed to create");
    }
  };

  const activeCount = (activities || []).filter((a) => a.isActive).length;

  const generateStudentAiSummary = async (student) => {
    const key = student.studentId;
    if (!token) { toast.error('Not authenticated'); return; }
    setStudentAiLoading((p) => ({ ...p, [key]: true }));
    const comments = (student.comments || []).map((c) => (typeof c === 'object' ? c.content : c)).filter(Boolean);
    const context = `Student: ${student.studentName}\nTotal Score: ${student.totalScore}\nEvaluations received: ${student.evaluationCount}\nPeer comments:\n${comments.map((c, i) => `${i + 1}. ${c}`).join('\n')}`;
    try {
      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          message: `Summarize this student's peer evaluation feedback. Identify strengths, areas to improve, and give a brief recommendation.`,
          context,
          contextType: 'peer-student-summary',
        }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new Error(data?.message || `HTTP ${res.status}`);
      setStudentAiSummaries((p) => ({ ...p, [key]: data.reply }));
    } catch (e) {
      toast.error(e.message || 'AI request failed');
    } finally {
      setStudentAiLoading((p) => ({ ...p, [key]: false }));
    }
  };

  const viewSummary = async (activity) => {
    setDrillLoading(true);
    setDrillError(null);
    setDrillDown(null);
    try {
      const data = await apeerActivityAPI.getActivitySummary(activity.id);
      setDrillDown({ activity, data });
    } catch (e) {
      setDrillError(e?.message || "Failed to load summary");
    } finally {
      setDrillLoading(false);
    }
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Peer Evaluations</h1>
        <div className="summary-row">
          <SummaryCard title="Total Activities" value={loading ? "-" : String(activities.length)} icon="📋" />
          <SummaryCard title="Active" value={loading ? "-" : String(activeCount)} icon="⚡" />
        </div>
        {error && <div className="error-message">{error}</div>}
        <div className="section">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <h2>Evaluation Activities</h2>
            <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
              + Create Activity
            </button>
          </div>
          {showCreate && (
            <form onSubmit={handleCreate} style={{
              marginBottom: 24,
              padding: "20px 24px",
              background: "rgba(255,255,255,0.04)",
              border: "1px solid rgba(255,255,255,0.1)",
              borderRadius: 12
            }}>
              <div className="form-group" style={{ marginBottom: 14 }}>
                <label>Title</label>
                <input
                  type="text"
                  value={createTitle}
                  onChange={(e) => setCreateTitle(e.target.value)}
                  required
                  className="form-control"
                  style={{ width: "100%" }}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 14 }}>
                <label>Rubric Criteria (comma-separated, e.g. Participation, Cooperation, Leadership)</label>
                <input
                  type="text"
                  value={createCriteria}
                  onChange={(e) => setCreateCriteria(e.target.value)}
                  placeholder="Participation, Cooperation, Leadership"
                  className="form-control"
                  style={{ width: "100%" }}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 14 }}>
                <label>Deadline (optional)</label>
                <input
                  type="datetime-local"
                  value={createDeadline}
                  onChange={(e) => setCreateDeadline(e.target.value)}
                  className="form-control"
                  style={{ width: "100%" }}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 14 }}>
                <label>Class (optional)</label>
                <select
                  value={createClassId}
                  onChange={(e) => setCreateClassId(e.target.value)}
                  className="form-control"
                  style={{ width: "100%" }}
                >
                  <option value="">— Select class —</option>
                  {classes.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} {c.section ? `(${c.section})` : ""}
                    </option>
                  ))}
                </select>
              </div>
              <div style={{ display: "flex", gap: 12 }}>
                <button type="submit" className="btn btn-primary">Create</button>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              </div>
            </form>
          )}
          {loading ? (
            <p>Loading...</p>
          ) : activities.length === 0 ? (
            <p>No peer evaluation activities yet. Create one to get started.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Deadline</th>
                  <th>Criteria</th>
                  <th>Class</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {activities.map((a) => (
                  <tr key={a.id}>
                    <td>{a.title}</td>
                    <td>{a.deadline ? new Date(a.deadline).toLocaleString() : "—"}</td>
                    <td>{(a.rubricCriteria || []).join(", ") || "—"}</td>
                    <td>{a.classId ? classes.find((c) => c.id === a.classId)?.name || a.classId : "—"}</td>
                    <td>
                      <span className={`status-badge ${a.isActive ? "status-active" : "status-inactive"}`}>
                        {a.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <button type="button" className="btn" onClick={() => viewSummary(a)}>
                        View Summary
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {drillLoading && <p style={{ marginTop: 16 }}>Loading summary...</p>}
        {drillError && <div className="error-message" style={{ marginTop: 16 }}>{drillError}</div>}
        {drillDown && (
          <div className="section" style={{ marginTop: 24 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <h2>{drillDown.data.activityTitle} — Per-Student Summary</h2>
              <button type="button" className="btn btn-secondary" onClick={() => setDrillDown(null)}>Close</button>
            </div>
            {(drillDown.data.students || []).length === 0 ? (
              <p>No submissions yet for this activity.</p>
            ) : (
              <table className="class-table">
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>Email</th>
                    <th>Evaluations Received</th>
                    <th>Total Score</th>
                    <th>Comments</th>
                  </tr>
                </thead>
                <tbody>
                  {(drillDown.data.students || []).map((s) => (
                    <tr key={s.studentId}>
                      <td>{s.studentName}</td>
                      <td>{s.email}</td>
                      <td>{s.evaluationCount}</td>
                      <td>{s.totalScore}</td>
                      <td>
                        {(s.comments || []).length === 0 ? (
                          <span style={{ color: "var(--dtm-muted)" }}>—</span>
                        ) : (
                          <ul style={{ margin: 0, paddingLeft: 16 }}>
                            {(s.comments || []).map((c, i) => {
                              const content = typeof c === 'object' ? c.content : c;
                              const tag = typeof c === 'object' ? c.aiTag : null;
                              const tagStyle = tag && AI_TAG_STYLES[tag];
                              return (
                                <li key={i} style={{ fontSize: 13, marginBottom: 6, color: "var(--dtm-text)", listStyle: 'none' }}>
                                  {tagStyle && (
                                    <span style={{ ...tagStyle, fontSize: 10, fontWeight: 700, padding: '1px 6px', borderRadius: 999, marginRight: 6, letterSpacing: '0.4px' }}>
                                      {tag}
                                    </span>
                                  )}
                                  {content}
                                </li>
                              );
                            })}
                          </ul>
                        )}
                        {studentAiLoading[s.studentId] ? (
                          <p style={{ fontSize: 12, color: 'var(--dtm-muted)', marginTop: 8 }}>Generating summary…</p>
                        ) : studentAiSummaries[s.studentId] ? (
                          <div style={{ marginTop: 10, padding: '10px 12px', background: 'rgba(242,201,76,0.07)', border: '1px solid rgba(242,201,76,0.2)', borderRadius: 8, fontSize: 13, color: 'var(--dtm-text)', whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                            <strong style={{ color: 'var(--dtm-gold)', fontSize: 11, display: 'block', marginBottom: 6 }}>✦ AI Summary</strong>
                            {studentAiSummaries[s.studentId]}
                          </div>
                        ) : null}
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ marginTop: 8, fontSize: 12, padding: '4px 10px' }}
                          onClick={() => generateStudentAiSummary(s)}
                          disabled={studentAiLoading[s.studentId]}
                        >
                          {studentAiSummaries[s.studentId] ? '↺ Refresh AI' : '✦ AI Summary'}
                        </button>
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

export default PeerEvaluations;
