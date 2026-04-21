import React, { useEffect, useMemo, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { adviserAPI, questionnaireAPI, teamAPI } from "../../services/api";
import "./Adviser.css";

const Evaluations = () => {
  const { teamId } = useParams();
  const navigate = useNavigate();

  const [questionnaires, setQuestionnaires] = useState([]);
  const [statusByQuestionnaire, setStatusByQuestionnaire] = useState({});
  const [teamName, setTeamName] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [searchTerm, setSearchTerm] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadQuestionnaires = async () => {
      if (!teamId) {
        setError("Invalid team");
        setLoading(false);
        return;
      }

      try {
        const team = await teamAPI.getTeamById(teamId);
        setTeamName(team?.name || "");
        if (!team.classId) {
          setError("Team has no associated class");
          setLoading(false);
          return;
        }

        const questionnairesData = await questionnaireAPI.getQuestionnairesByClass(team.classId);
        const statusRows = await adviserAPI.getTeamEvaluationStatuses(teamId);

        const statusMap = {};
        (statusRows || []).forEach((row) => {
          if (row?.questionnaireId !== null && row?.questionnaireId !== undefined) {
            statusMap[row.questionnaireId] = row;
          }
        });

        setQuestionnaires(questionnairesData);
        setStatusByQuestionnaire(statusMap);
      } catch (err) {
        setError("Failed to load questionnaires: " + err.message);
      } finally {
        setLoading(false);
      }
    };

    loadQuestionnaires();
  }, [teamId]);

  const resolveQueueStatus = (questionnaire) => {
    const statusRow = statusByQuestionnaire[questionnaire.id];
    if (statusRow?.status) {
      return statusRow.status;
    }
    if (questionnaire.isLocked) {
      return "LOCKED";
    }
    return "READY";
  };

  const statusCounts = useMemo(() => {
    const counts = {
      ALL: questionnaires.length,
      READY: 0,
      IN_PROGRESS: 0,
      SUBMITTED: 0,
      LOCKED: 0,
    };

    questionnaires.forEach((q) => {
      const status = resolveQueueStatus(q);
      counts[status] = (counts[status] || 0) + 1;
    });

    return counts;
  }, [questionnaires, statusByQuestionnaire]);

  const filteredQuestionnaires = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase();
    const priority = { IN_PROGRESS: 0, READY: 1, SUBMITTED: 2, LOCKED: 3 };

    return questionnaires
      .filter((q) => {
        const status = resolveQueueStatus(q);
        if (statusFilter !== "ALL" && status !== statusFilter) {
          return false;
        }

        if (!normalizedSearch) {
          return true;
        }

        const title = (q.title || "").toLowerCase();
        const description = (q.description || "").toLowerCase();
        return title.includes(normalizedSearch) || description.includes(normalizedSearch);
      })
      .sort((a, b) => {
        const aStatus = resolveQueueStatus(a);
        const bStatus = resolveQueueStatus(b);

        if (priority[aStatus] !== priority[bStatus]) {
          return priority[aStatus] - priority[bStatus];
        }

        const aUpdated = new Date(statusByQuestionnaire[a.id]?.updatedAt || 0).getTime();
        const bUpdated = new Date(statusByQuestionnaire[b.id]?.updatedAt || 0).getTime();
        return bUpdated - aUpdated;
      });
  }, [questionnaires, searchTerm, statusFilter, statusByQuestionnaire]);

  return (
    <div className="adviser-container">
      <AdviserSidebar />

      <div className="adviser-content">
        <h1>Evaluations</h1>

        <section className="adviser-hero adviser-hero-tight">
          <div>
            <p className="adviser-hero-kicker">Team Evaluation Queue</p>
            <h2 className="adviser-hero-title">{teamName ? `${teamName} Questionnaires` : "Assigned Questionnaires"}</h2>
            <p className="adviser-hero-text">
              Track what is ready, what is locked, and focus your next evaluation submission.
            </p>
          </div>
          <div className="adviser-eval-metrics">
            <span><strong>{statusCounts.ALL}</strong> total</span>
            <span><strong>{statusCounts.IN_PROGRESS}</strong> in progress</span>
            <span><strong>{statusCounts.READY}</strong> ready</span>
          </div>
        </section>

        <section className="section adviser-queue-controls">
          <div className="adviser-status-tabs">
            {[
              { key: "ALL", label: "All" },
              { key: "READY", label: "Ready" },
              { key: "IN_PROGRESS", label: "In Progress" },
              { key: "SUBMITTED", label: "Submitted" },
              { key: "LOCKED", label: "Locked" },
            ].map((tab) => (
              <button
                key={tab.key}
                className={`adviser-status-tab ${statusFilter === tab.key ? "is-active" : ""}`}
                onClick={() => setStatusFilter(tab.key)}
              >
                {tab.label}
                <span className="adviser-status-tab-count">{statusCounts[tab.key] || 0}</span>
              </button>
            ))}
          </div>

          <input
            type="text"
            className="adviser-search-input"
            placeholder="Search questionnaire title or description..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </section>

        <div className="section">
          <div className="section-header-row">
            <h2>Assigned Questionnaires</h2>
          </div>

          {error && <div className="error-message">{error}</div>}

          {loading ? (
            <p>Loading questionnaires...</p>
          ) : filteredQuestionnaires.length === 0 ? (
            <p>No questionnaires assigned to this team.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Title</th>
                  <th>Description</th>
                  <th>Status</th>
                  <th>Progress</th>
                  <th>Last Updated</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredQuestionnaires.map((q, index) => {
                  const status = resolveQueueStatus(q);
                  const statusRow = statusByQuestionnaire[q.id];
                  const progress = statusRow?.progressPercent ?? 0;
                  const answered = statusRow?.answeredCount ?? 0;
                  const totalQuestions = statusRow?.totalQuestions ?? 0;
                  const lastUpdated = statusRow?.updatedAt;

                  return (
                  <tr key={q.id}>
                    <td>{index + 1}</td>
                    <td><strong>{q.title}</strong></td>
                    <td>{q.description || "No description"}</td>
                    <td>
                      {status === "LOCKED" ? (
                        <span className="status-badge status-inactive">Locked</span>
                      ) : status === "SUBMITTED" ? (
                        <span className="status-badge status-active">Submitted</span>
                      ) : status === "IN_PROGRESS" ? (
                        <span className="status-badge adviser-status-progress">In Progress</span>
                      ) : (
                        <span className="status-badge status-active">Ready</span>
                      )}
                    </td>
                    <td>
                      <div className="adviser-progress-wrap">
                        <div className="adviser-progress-track">
                          <div className="adviser-progress-fill" style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}></div>
                        </div>
                        <span className="adviser-progress-text">
                          {progress}%{totalQuestions > 0 ? ` (${answered}/${totalQuestions})` : ""}
                        </span>
                      </div>
                    </td>
                    <td>{lastUpdated ? new Date(lastUpdated).toLocaleDateString() : "—"}</td>
                    <td>
                      {status === "LOCKED" ? (
                        <span className="adviser-locked-label">Locked after submission</span>
                      ) : status === "SUBMITTED" ? (
                        <button
                          className="btn-secondary"
                          onClick={() => navigate("/adviser/completed")}
                        >
                          View Submission
                        </button>
                      ) : (
                        <button
                          className="btn adviser-open-btn"
                          onClick={() =>
                            navigate(
                              `/adviser/evaluate/${teamId}/${q.id}`
                            )
                          }
                        >
                          {status === "IN_PROGRESS" ? "Resume Draft" : "Start Evaluation"}
                        </button>
                      )}
                    </td>
                  </tr>
                );})}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default Evaluations;
