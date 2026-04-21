import React, { useEffect, useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import StudentSidebar from "../../components/Sidebar/StudentSidebar";
import { apeerActivityAPI, apeerSubmissionAPI } from "../../services/apeerApi";
import "../DashboardTeacher/Teacher.css";

const StudentEvaluationForm = () => {
  const { activityId } = useParams();
  const navigate = useNavigate();
  const [activity, setActivity] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [scores, setScores] = useState({});
  const [comments, setComments] = useState({});

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const [act, mems] = await Promise.all([
          apeerActivityAPI.getActivity(activityId),
          apeerActivityAPI.getMembers(activityId),
        ]);
        setActivity(act);
        setMembers(mems || []);
      } catch (e) {
        setError(e?.message || "Failed to load");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [activityId]);

  const criteria = activity?.rubricCriteria || [];
  const setScore = (targetId, criterion, value) => {
    setScores((prev) => ({
      ...prev,
      [targetId]: { ...(prev[targetId] || {}), [criterion]: value === "" ? null : Number(value) },
    }));
  };

  const getDuplicateErrors = () => {
    const errors = {};
    for (const c of criteria) {
      const seen = {};
      for (const m of members) {
        const val = scores[m.studentId]?.[c];
        if (val == null) continue;
        if (seen[val] !== undefined) {
          if (!errors[m.studentId]) errors[m.studentId] = {};
          errors[m.studentId][c] = true;
          if (!errors[seen[val]]) errors[seen[val]] = {};
          errors[seen[val]][c] = true;
        } else {
          seen[val] = m.studentId;
        }
      }
    }
    return errors;
  };
  const setComment = (targetId, value) => {
    setComments((prev) => ({ ...prev, [targetId]: value }));
  };

  const isComplete = () => {
    const dupes = getDuplicateErrors();
    if (Object.keys(dupes).length > 0) return false;
    for (const m of members) {
      const sc = scores[m.studentId] || {};
      for (const c of criteria) {
        const v = sc[c];
        if (v == null || v < 0 || v > 10) return false;
      }
      const com = (comments[m.studentId] || "").trim();
      if (!com) return false;
    }
    return true;
  };

  const handleSubmit = async () => {
    if (!currentUser?.id || !activity || !isComplete()) return;
    setSubmitting(true);
    setError(null);
    try {
      for (const m of members) {
        const rubricScores = {};
        for (const c of criteria) {
          rubricScores[c] = scores[m.studentId]?.[c] ?? 0;
        }
        await apeerSubmissionAPI.submit({
          evaluatorId: currentUser.id,
          targetStudentId: m.studentId,
          activityId: Number(activityId),
          rubricScores,
          commentContent: (comments[m.studentId] || "").trim(),
        });
      }
      navigate("/thank-you");
    } catch (e) {
      setError(e?.message || "Failed to submit");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="teacher-container">
        <StudentSidebar />
        <div className="teacher-content">
          <p>Loading...</p>
        </div>
      </div>
    );
  }

  if (!activity) {
    return (
      <div className="teacher-container">
        <StudentSidebar />
        <div className="teacher-content">
          <p>Activity not found.</p>
          <button type="button" className="btn btn-secondary" onClick={() => navigate("/student/dashboard")}>
            Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="teacher-container">
      <StudentSidebar />
      <div className="teacher-content">
        <h1>{activity.title}</h1>
        <p style={{ color: "var(--dtm-muted)", marginBottom: 24 }}>
          Due {activity.deadline ? new Date(activity.deadline).toLocaleDateString() : "—"}
        </p>
        {error && <div className="error-message">{error}</div>}
        <div className="section">
          <p style={{ marginBottom: 16, color: "var(--dtm-muted)" }}>
            Evaluate each member (0–10 per criterion). Each member must receive a unique score within the same criterion.
            Comment is required per member.
          </p>
          {(() => { const dupeErrors = getDuplicateErrors(); return members.map((m) => (
            <div key={m.studentId} style={{
              marginBottom: 20,
              padding: "16px 20px",
              background: "rgba(255,255,255,0.04)",
              border: `1px solid ${dupeErrors[m.studentId] ? "rgba(255,100,100,0.35)" : "rgba(255,255,255,0.1)"}`,
              borderRadius: 12
            }}>
              <h3 style={{ fontSize: 15, marginBottom: 14, color: "var(--dtm-gold)", fontWeight: 600 }}>
                Member {m.orderIndex}: {m.firstName} {m.lastName}
              </h3>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: 12, marginBottom: 14 }}>
                {criteria.map((c) => {
                  const isDupe = dupeErrors[m.studentId]?.[c];
                  return (
                  <div key={c}>
                    <label style={{ fontSize: 11, color: isDupe ? "#ff7b7b" : "var(--dtm-muted)", display: "block", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.5px" }}>{c}</label>
                    <input
                      type="number"
                      min={0}
                      max={10}
                      value={scores[m.studentId]?.[c] ?? ""}
                      onChange={(e) => setScore(m.studentId, c, e.target.value)}
                      className="form-control"
                      style={{ width: "100%", borderColor: isDupe ? "rgba(255,100,100,0.6)" : undefined }}
                    />
                    {isDupe && (
                      <span style={{ fontSize: 11, color: "#ff7b7b", marginTop: 3, display: "block" }}>
                        Duplicate score
                      </span>
                    )}
                  </div>
                  );
                })}
              </div>
              <div>
                <label style={{ fontSize: 11, color: "var(--dtm-muted)", display: "block", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.5px" }}>Comment (required)</label>
                <textarea
                  value={comments[m.studentId] || ""}
                  onChange={(e) => setComment(m.studentId, e.target.value)}
                  rows={2}
                  className="form-control"
                  style={{ width: "100%", marginTop: 2, resize: "vertical" }}
                />
              </div>
            </div>
          ))})()}
          <div style={{ display: "flex", gap: 12, marginTop: 16 }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate("/student/dashboard")}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSubmit}
              disabled={!isComplete() || submitting}
            >
              {submitting ? "Submitting..." : "Submit Evaluation"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StudentEvaluationForm;
