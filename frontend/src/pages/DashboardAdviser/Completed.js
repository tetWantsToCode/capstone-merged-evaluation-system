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

  return (
    <div className="adviser-container">
      <AdviserSidebar />

      <div className="adviser-content">
        <h1>Completed Evaluations</h1>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          {loading ? (
            <p>Loading...</p>
          ) : completed.length === 0 ? (
            <p>No completed evaluations.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Team</th>
                  <th>Status</th>
                  <th>Date Submitted</th>
                </tr>
              </thead>
              <tbody>
                {completed.map((e) => (
                  <tr key={e.id}>
                    <td>{e.team?.name}</td>
                    <td>
                      <span className="status-active">Submitted</span>
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
