import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { teamAPI, questionnaireAPI } from "../../services/api";
import "./Adviser.css";

const AdviserEvaluations = () => {
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        const allTeams = await teamAPI.getAllTeams();
        const assigned = allTeams.filter(
          (t) => Array.isArray(t.adviserIds) && t.adviserIds.includes(currentUser?.id)
        );

        const combined = [];
        for (const team of assigned) {
          if (!team.classId) continue;
          try {
            const questionnaires = await questionnaireAPI.getQuestionnairesByClass(team.classId);
            for (const q of questionnaires || []) {
              combined.push({ team, questionnaire: q });
            }
          } catch {
            combined.push({ team, questionnaire: null });
          }
        }
        setRows(combined);
      } catch (e) {
        setError("Failed to load evaluations: " + e.message);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [currentUser]);

  return (
    <div className="adviser-container">
      <AdviserSidebar />
      <div className="adviser-content">
        <h1>My Evaluations</h1>
        <p style={{ color: "var(--dtm-muted)", marginBottom: 20 }}>
          All questionnaires assigned to your teams.
        </p>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          {loading ? (
            <p>Loading...</p>
          ) : rows.length === 0 ? (
            <p>No evaluations found. You may not be assigned to any teams yet.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Team</th>
                  <th>Questionnaire</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, idx) =>
                  row.questionnaire ? (
                    <tr key={`${row.team.id}-${row.questionnaire.id}`}>
                      <td>{row.team.name}</td>
                      <td>{row.questionnaire.title}</td>
                      <td>
                        <span className={`status-badge ${row.questionnaire.isLocked ? "status-inactive" : "status-active"}`}>
                          {row.questionnaire.isLocked ? "Locked" : "Open"}
                        </span>
                      </td>
                      <td>
                        {!row.questionnaire.isLocked ? (
                          <button
                            className="btn"
                            onClick={() => navigate(`/adviser/evaluate/${row.team.id}/${row.questionnaire.id}`)}
                          >
                            Evaluate
                          </button>
                        ) : (
                          <span style={{ color: "var(--dtm-muted)", fontSize: 13 }}>Locked</span>
                        )}
                      </td>
                    </tr>
                  ) : (
                    <tr key={`${row.team.id}-none-${idx}`}>
                      <td>{row.team.name}</td>
                      <td colSpan={3} style={{ color: "var(--dtm-muted)" }}>No questionnaires assigned to this team's class.</td>
                    </tr>
                  )
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdviserEvaluations;
