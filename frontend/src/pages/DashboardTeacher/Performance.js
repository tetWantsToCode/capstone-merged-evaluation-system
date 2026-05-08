import React, { useEffect, useMemo, useState, useCallback } from "react";
import ReactDOM from "react-dom";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Users, ChevronRight, X, UserCircle2, BarChart3 } from "lucide-react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { performanceAPI } from "../../services/api";
import "./Teacher.css";

const Performance = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [teams, setTeams] = useState([]);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [teamsError, setTeamsError] = useState(null);

  const [teamStudentsData, setTeamStudentsData] = useState(null); // { teamId, teamName, students }
  const [loadingStudents, setLoadingStudents] = useState(false);
  const [studentsError, setStudentsError] = useState(null);

  // Read open team from URL ?team=42
  const openTeamId = searchParams.get("team");

  useEffect(() => {
    const load = async () => {
      try {
        setLoadingTeams(true);
        const data = await performanceAPI.getTeams();
        setTeams(Array.isArray(data) ? data : []);
      } catch (err) {
        setTeamsError("Failed to load teams: " + err.message);
      } finally {
        setLoadingTeams(false);
      }
    };
    load();
  }, []);

  // When URL ?team param changes, load that team's students
  useEffect(() => {
    if (!openTeamId) {
      setTeamStudentsData(null);
      return;
    }
    const load = async () => {
      try {
        setLoadingStudents(true);
        setStudentsError(null);
        const data = await performanceAPI.getTeamStudents(openTeamId);
        setTeamStudentsData(data);
      } catch (err) {
        setStudentsError("Failed to load students: " + err.message);
      } finally {
        setLoadingStudents(false);
      }
    };
    load();
  }, [openTeamId]);

  const openTeam = useCallback(
    (teamId) => {
      setSearchParams({ team: teamId });
    },
    [setSearchParams]
  );

  const closeModal = useCallback(() => {
    setSearchParams({});
  }, [setSearchParams]);

  // Close modal on Escape key
  useEffect(() => {
    if (!openTeamId) return;
    const handleKey = (e) => {
      if (e.key === "Escape") closeModal();
    };
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKey);
    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener("keydown", handleKey);
    };
  }, [openTeamId, closeModal]);

  const handleStudentClick = useCallback(
    (studentId) => {
      navigate(
        `/teacher/performance/student/${studentId}?from=${openTeamId}`
      );
    },
    [navigate, openTeamId]
  );

  const getPerformanceBadge = (count) => {
    if (count >= 3) return { label: "High Activity", cls: "perf-badge--high" };
    if (count >= 1) return { label: "Active", cls: "perf-badge--mid" };
    return { label: "Minimal", cls: "perf-badge--low" };
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Performance</h1>

        <div className="section">
          <div className="section-header-row">
            <h2>Teams with Completed Evaluations</h2>
            {!loadingTeams && (
              <span className="perf-count-badge">{teams.length} teams</span>
            )}
          </div>

          {loadingTeams && (
            <div className="perf-skeleton-grid">
              {[1, 2, 3].map((n) => (
                <div key={n} className="perf-team-card perf-team-card--skeleton" />
              ))}
            </div>
          )}

          {!loadingTeams && teamsError && (
            <div className="error-message">{teamsError}</div>
          )}

          {!loadingTeams && !teamsError && teams.length === 0 && (
            <div className="perf-empty-state">
              <BarChart3 size={40} className="perf-empty-icon" />
              <p>No teams with completed evaluations yet.</p>
              <span>Evaluations need to be submitted before they appear here.</span>
            </div>
          )}

          {!loadingTeams && !teamsError && teams.length > 0 && (
            <div className="perf-team-grid">
              {teams.map((team) => {
                const badge = getPerformanceBadge(team.completedEvalCount);
                return (
                  <button
                    key={team.id}
                    type="button"
                    className="perf-team-card"
                    onClick={() => openTeam(team.id)}
                  >
                    <div className="perf-team-card-top">
                      <span className="perf-team-name">{team.name}</span>
                      <span className={`perf-badge ${badge.cls}`}>
                        {badge.label}
                      </span>
                    </div>
                    <span className="perf-team-class">{team.className}</span>
                    <div className="perf-team-meta">
                      <span className="perf-team-meta-item">
                        <Users size={13} />
                        {team.memberCount} member{team.memberCount !== 1 ? "s" : ""}
                      </span>
                      <span className="perf-team-meta-item">
                        <BarChart3 size={13} />
                        {team.completedEvalCount} eval{team.completedEvalCount !== 1 ? "s" : ""}
                      </span>
                    </div>
                    <div className="perf-team-card-arrow">
                      <ChevronRight size={16} />
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Team Students Modal — portalled to body to escape overflow containment */}
      {openTeamId && ReactDOM.createPortal(
        <div
          className="perf-modal-overlay"
          onClick={closeModal}
          role="dialog"
          aria-modal="true"
          aria-label="Team Students"
        >
          <div
            className="perf-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="perf-modal-header">
              <div className="perf-modal-title-group">
                <h2 className="perf-modal-title">
                  {teamStudentsData?.teamName || "Team"}
                </h2>
                <span className="perf-modal-subtitle">
                  Select a student to view their performance analysis
                </span>
              </div>
              <button
                className="perf-modal-close"
                onClick={closeModal}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <div className="perf-modal-body">
              {loadingStudents && (
                <div className="perf-student-list">
                  {[1, 2, 3].map((n) => (
                    <div key={n} className="perf-student-row perf-student-row--skeleton" />
                  ))}
                </div>
              )}

              {!loadingStudents && studentsError && (
                <div className="error-message">{studentsError}</div>
              )}

              {!loadingStudents &&
                !studentsError &&
                teamStudentsData?.students?.length === 0 && (
                  <div className="perf-empty-state">
                    <UserCircle2 size={32} className="perf-empty-icon" />
                    <p>No students found in this team.</p>
                  </div>
                )}

              {!loadingStudents &&
                !studentsError &&
                teamStudentsData?.students?.length > 0 && (
                  <ul className="perf-student-list">
                    {teamStudentsData.students.map((student) => (
                      <li key={student.id}>
                        <button
                          type="button"
                          className="perf-student-row"
                          onClick={() => handleStudentClick(student.id)}
                        >
                          <div className="perf-student-avatar">
                            {student.firstName?.[0]?.toUpperCase()}
                            {student.lastName?.[0]?.toUpperCase()}
                          </div>
                          <div className="perf-student-info">
                            <span className="perf-student-name">
                              {student.firstName} {student.lastName}
                            </span>
                            <span className="perf-student-num">
                              {student.studentNumber}
                            </span>
                          </div>
                          <div className="perf-student-right">
                            {student.peerEvalCount > 0 && (
                              <span className="perf-peer-chip">
                                {student.peerEvalCount} peer eval{student.peerEvalCount !== 1 ? "s" : ""}
                              </span>
                            )}
                            <ChevronRight size={16} className="perf-student-chevron" />
                          </div>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
            </div>

            <div className="perf-modal-footer">
              <span>
                {teamStudentsData?.students?.length ?? 0} student
                {teamStudentsData?.students?.length !== 1 ? "s" : ""}
              </span>
            </div>
          </div>
        </div>
      , document.body)}
    </div>
  );
};

export default Performance;
