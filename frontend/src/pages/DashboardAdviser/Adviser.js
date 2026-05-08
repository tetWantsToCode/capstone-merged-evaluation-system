import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { teamAPI } from "../../services/api";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/Pagination/Pagination";
import "./Adviser.css";

const Adviser = () => {
  const navigate = useNavigate();
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const loadTeams = async () => {
      try {
        const allTeams = await teamAPI.getAllTeams();
        const assigned = allTeams.filter(
          t => Array.isArray(t.adviserIds) && t.adviserIds.includes(currentUser.id)
        );
        setTeams(assigned);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };

    loadTeams();
  }, [currentUser]);

  const { currentPage, totalPages, paginatedData, goToPage } = usePagination(teams, 10);

  const activeTeams = teams.filter((team) => team.isActive).length;
  const inactiveTeams = Math.max(0, teams.length - activeTeams);
  const totalMembers = teams.reduce((sum, team) => sum + (team.memberIds?.length || 0), 0);
  const adviserName = [currentUser?.firstName, currentUser?.lastName].filter(Boolean).join(" ") || "Adviser";

  return (
    <div className="adviser-container">
      <AdviserSidebar />
      <div className="adviser-content">
        <h1>Adviser Dashboard</h1>

        <section className="adviser-hero">
          <div>
            <p className="adviser-hero-kicker">Evaluation Command Center</p>
            <h2 className="adviser-hero-title">Welcome back, {adviserName}</h2>
            <p className="adviser-hero-text">
              Review team readiness, open assigned questionnaires, and drive consistent adviser feedback quality.
            </p>
          </div>
        </section>

        <div className="adviser-metric-grid">
          <article className="adviser-metric-card">
            <span className="adviser-metric-label">Teams Assigned</span>
            <span className="adviser-metric-value">{loading ? "-" : teams.length}</span>
          </article>
          <article className="adviser-metric-card">
            <span className="adviser-metric-label">Active Teams</span>
            <span className="adviser-metric-value">{loading ? "-" : activeTeams}</span>
          </article>
          <article className="adviser-metric-card">
            <span className="adviser-metric-label">Total Members</span>
            <span className="adviser-metric-value">{loading ? "-" : totalMembers}</span>
          </article>
          <article className="adviser-metric-card adviser-metric-card-alert">
            <span className="adviser-metric-label">Inactive Teams</span>
            <span className="adviser-metric-value">{loading ? "-" : inactiveTeams}</span>
          </article>
        </div>

        <div className="section">
          <div className="section-header-row">
            <h2>Assigned Teams</h2>
          </div>

          {loading ? <p>Loading...</p> : (
            <>
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Team</th>
                  <th>Members</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {paginatedData.map((team, index) => (
                  <tr key={team.id}>
                    <td>{(currentPage - 1) * 10 + index + 1}</td>
                    <td><strong>{team.name}</strong></td>
                    <td>{team.memberIds?.length || 0}</td>
                    <td>
                      <span className={`status-badge ${team.isActive ? "status-active" : "status-inactive"}`}>
                        {team.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: "8px", justifyContent: "center" }}>
                        <button
                          className="btn adviser-open-btn"
                          onClick={() => navigate(`/adviser/evaluations/${team.id}`)}
                        >
                          Team Evaluations
                        </button>
                        <button
                          className="btn-secondary adviser-open-btn"
                          onClick={() => navigate(`/adviser/student-evaluations/${team.id}`)}
                        >
                          Student Evaluations
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={goToPage} />
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Adviser;