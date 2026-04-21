import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import SummaryCard from "../../components/Cards/SummaryCard";
import { teamAPI, adviserAPI } from "../../services/api";
import "./Adviser.css";

const Adviser = () => {
  const navigate = useNavigate();
  const [teams, setTeams] = useState([]);
  const [completedCount, setCompletedCount] = useState(0);
  const [loading, setLoading] = useState(true);

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const loadTeams = async () => {
      try {
        const [allTeams, completed] = await Promise.all([
          teamAPI.getAllTeams(),
          adviserAPI.getCompletedEvaluations().catch(() => []),
        ]);
        const assigned = allTeams.filter(
          t => Array.isArray(t.adviserIds) && t.adviserIds.includes(currentUser.id)
        );
        setTeams(assigned);
        setCompletedCount((completed || []).length);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };

    loadTeams();
  }, [currentUser]);

  return (
    <div className="adviser-container">
      <AdviserSidebar />
      <div className="adviser-content">
        <h1>Adviser Dashboard</h1>

        <div className="summary-row">
          <SummaryCard title="Teams Assigned" value={loading ? "-" : teams.length} icon="🤝" />
          <SummaryCard title="Completed" value={loading ? "-" : String(completedCount)} icon="✅" />
          <SummaryCard title="Pending" value={loading ? "-" : String(Math.max(0, teams.length - completedCount))} icon="⏳" />
        </div>

        <div className="section">
          <h2>Assigned Teams</h2>

          {loading ? <p>Loading...</p> : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Team</th>
                  <th>Members</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {teams.map(team => (
                  <tr key={team.id}>
                    <td>{team.name}</td>
                    <td>{team.memberIds?.length || 0}</td>
                    <td>
                      <span className={`status-badge ${team.isActive ? "status-active" : "status-inactive"}`}>
                        {team.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <button
                        className="btn"
                        onClick={() => navigate(`/adviser/evaluations/${team.id}`)}
                      >
                        Open
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

export default Adviser;
