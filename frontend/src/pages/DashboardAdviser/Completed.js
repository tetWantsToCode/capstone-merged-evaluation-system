import React, { useEffect, useState } from "react";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { adviserAPI } from "../../services/api";
import "./Adviser.css";

const Completed = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [completed, setCompleted] = useState([]);

  useEffect(() => {
    const loadCompleted = async () => {
      try {
        setLoading(true);
        const data = await adviserAPI.getCompletedEvaluations();
        setCompleted(data || []);
      } catch (e) {
        setError(e.message || "Failed to fetch evaluations");
      } finally {
        setLoading(false);
      }
    };

    loadCompleted();
  }, []);

  const submittedCount = completed.length;
  const withTeamCount = completed.filter((e) => (e.teamName || e.team?.name)).length;
  const withClassCount = completed.filter((e) => e.className).length;

  return (
    <div className="adviser-container">
      <AdviserSidebar />

      <div className="adviser-content">
        <h1>Completed Evaluations</h1>

        <section className="adviser-hero adviser-hero-tight">
          <div>
            <p className="adviser-hero-kicker">Submission Archive</p>
            <h2 className="adviser-hero-title">Completed Evaluation Records</h2>
            <p className="adviser-hero-text">
              Review all submitted evaluations with team and class context for quick reporting.
            </p>
          </div>
        </section>

        <section className="completed-kpi-row" aria-label="completed evaluation summary">
          <article className="completed-kpi-card">
            <span className="completed-kpi-label">Submitted</span>
            <span className="completed-kpi-value">{submittedCount}</span>
          </article>
          <article className="completed-kpi-card">
            <span className="completed-kpi-label">With Team</span>
            <span className="completed-kpi-value">{withTeamCount}</span>
          </article>
          <article className="completed-kpi-card">
            <span className="completed-kpi-label">With Class</span>
            <span className="completed-kpi-value">{withClassCount}</span>
          </article>
        </section>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div className="section-header-row">
            <h2>Submitted Evaluations</h2>
            <span className="section-helper-text">Latest submissions with team/class visibility</span>
          </div>

          {loading ? (
            <p>Loading...</p>
          ) : completed.length === 0 ? (
            <p>No completed evaluations.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Team</th>
                  <th>Class</th>
                  <th>Questionnaire</th>
                  <th>Status</th>
                  <th>Date Submitted</th>
                </tr>
              </thead>
              <tbody>
                {completed.map((e, index) => (
                  <tr key={e.id}>
                    <td>{index + 1}</td>
                    <td><strong>{e.teamName || e.team?.name || `Team #${e.teamId || "N/A"}`}</strong></td>
                    <td>{e.className || "N/A"}</td>
                    <td>{e.questionnaire?.title || "N/A"}</td>
                    <td>
                      <span className="status-badge status-active">Submitted</span>
                    </td>
                    <td>
                      {e.submittedAt
                        ? new Date(e.submittedAt).toLocaleDateString()
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default Completed;
