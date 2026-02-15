import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { questionnaireAPI, teamAPI } from "../../services/api";
import "./Adviser.css";

const Evaluations = () => {
  const { teamId } = useParams();
  const navigate = useNavigate();

  const [questionnaires, setQuestionnaires] = useState([]);
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
        if (!team.classId) {
          setError("Team has no associated class");
          setLoading(false);
          return;
        }

        const questionnairesData = await questionnaireAPI.getQuestionnairesByClass(team.classId);
        setQuestionnaires(questionnairesData);
      } catch (err) {
        setError("Failed to load questionnaires: " + err.message);
      } finally {
        setLoading(false);
      }
    };

    loadQuestionnaires();
  }, [teamId]);

  return (
    <div className="adviser-container">
      <AdviserSidebar />

      <div className="adviser-content">
        <h1>Evaluations</h1>

        <div className="section">
          <h2>Assigned Questionnaires</h2>

          {error && <div className="error-message">{error}</div>}

          {loading ? (
            <p>Loading questionnaires...</p>
          ) : questionnaires.length === 0 ? (
            <p>No questionnaires assigned to this team.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Description</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {questionnaires.map((q) => (
                  <tr key={q.id}>
                    <td>{q.title}</td>
                    <td>{q.description}</td>
                    <td>
                      <button
                        className="btn"
                        onClick={() =>
                          navigate(
                            `/adviser/evaluate/${teamId}/${q.id}`
                          )
                        }
                      >
                        Evaluate
                      </button>
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

export default Evaluations;
