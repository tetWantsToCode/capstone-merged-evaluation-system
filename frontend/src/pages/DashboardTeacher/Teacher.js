import React, { useEffect, useMemo, useState } from "react";
import { UserCheck, GraduationCap, Bell } from "lucide-react";
import { createPortal } from "react-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import PendingEvaluationsModal from "../../components/Modal/PendingEvaluationsModal";
import { classAPI, studentAPI, teamAPI, questionnaireAPI, teacherReportAPI } from "../../services/api";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/Pagination/Pagination";
import "./Teacher.css";

const Teacher = () => {
  const [error, setError] = useState(null);
  const [students, setStudents] = useState([]);
  const [teams, setTeams] = useState([]);
  const [questionnaires, setQuestionnaires] = useState([]);
  const [showTeamsModal, setShowTeamsModal] = useState(false);
  const [selectedClass] = useState(null);
  const [showTeamMembersModal, setShowTeamMembersModal] = useState(false);
  const [selectedTeam, setSelectedTeam] = useState(null);
  const [adviserPending, setAdviserPending] = useState([]);
  const [studentPending, setStudentPending] = useState([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [showPendingModal, setShowPendingModal] = useState(false);
  const [activityLogs, setActivityLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logSearch, setLogSearch] = useState("");

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        setError(null);

        if (!currentUser?.id) {
          setError("User not logged in");
          return;
        }

        const [allClasses, allStudents, allTeams] = await Promise.all([
          classAPI.getAllClasses(),
          studentAPI.getAllStudents(),
          teamAPI.getAllTeams(),
        ]);

        const teacherClasses = (allClasses || []).filter(
          (c) => String(c.teacherId) === String(currentUser.id)
        );

        const teacherClassIds = new Set(teacherClasses.map((c) => c.id));

        const teacherStudents = (allStudents || []).filter(
          (s) => String(s.createdBy) === String(currentUser.id) || 
                 (Array.isArray(s.classIds) && s.classIds.some((id) => teacherClassIds.has(id)))
        );

        const teacherTeams = (allTeams || []).filter(
          (t) => teacherClassIds.has(t.classId)
        );

        // Load questionnaires for all teacher's classes
        const questionnairesMap = {};
        for (const classItem of teacherClasses) {
          try {
            const classQuestionnaires = await questionnaireAPI.getQuestionnairesByClassForTeacher(classItem.id);
            questionnairesMap[classItem.id] = classQuestionnaires;
          } catch (err) {
            questionnairesMap[classItem.id] = [];
          }
        }

        setStudents(teacherStudents);
        setTeams(teacherTeams);
        setQuestionnaires(questionnairesMap);

        // Load pending evaluations
        try {
          const pendingData = await teacherReportAPI.getPendingEvaluations();
          setAdviserPending(pendingData.adviserPending || []);
          setStudentPending(pendingData.studentPending || []);
          setPendingCount(pendingData.total || 0);
        } catch (err) {
          console.error("Error fetching pending evaluations:", err);
          setPendingCount(0);
          setAdviserPending([]);
          setStudentPending([]);
        }

        // Load Activity Logs
        try {
          setLogsLoading(true);
          const logs = await teacherReportAPI.getActivityLogs();
          setActivityLogs(logs || []);
        } catch (err) {
          console.error("Error fetching activity logs:", err);
        } finally {
          setLogsLoading(false);
        }
      } catch (e) {
        setError(e?.message || "Failed to load dashboard data");
      }
    };

    load();
  }, [currentUser]);

  const filteredLogs = useMemo(() => {
    const term = logSearch.trim().toLowerCase();
    if (!term) return activityLogs;
    return activityLogs.filter(log => 
      log.message?.toLowerCase().includes(term)
    );
  }, [activityLogs, logSearch]);

  const { currentPage: curPageLogs, totalPages: totPageLogs, paginatedData: pagLogs, goToPage: goPageLogs } = usePagination(filteredLogs, 5);

  const getTeamsForClass = (classId) => {
    return teams.filter(t => t.classId === classId);
  };

  const getStudentsForClass = (classId) => {
    return students.filter(s => s.classIds && s.classIds.includes(classId));
  };

  const getQuestionnairesForClass = (classId) => {
    return questionnaires[classId] || [];
  };

  const getStudentsForTeam = (team) => {
    if (!team || !team.memberIds || team.memberIds.length === 0) return [];
    // Convert memberIds to strings for comparison since IDs can be stored as different types
    const memberIdStrings = team.memberIds.map(id => String(id));
    return students.filter(s => s && memberIdStrings.includes(String(s.id)));
  };
  
  const teamsForSelectedClass = selectedClass ? getTeamsForClass(selectedClass.id) : [];
  const { currentPage: curPageTeams, totalPages: totPageTeams, paginatedData: pagTeams, goToPage: goPageTeams } = usePagination(teamsForSelectedClass, 10);

  const questForSelectedClass = selectedClass ? getQuestionnairesForClass(selectedClass.id) : [];
  const { currentPage: curPageQuest, totalPages: totPageQuest, paginatedData: pagQuest, goToPage: goPageQuest } = usePagination(questForSelectedClass, 10);

  const studentsForSelectedTeam = selectedTeam ? getStudentsForTeam(selectedTeam) : [];
  const { currentPage: curPageStud, totalPages: totPageStud, paginatedData: pagStud, goToPage: goPageStud } = usePagination(studentsForSelectedTeam, 10);

  const handleViewTeamMembers = (team) => {
    setSelectedTeam(team);
    setShowTeamMembersModal(true);
  };

  const teacherName = [currentUser?.firstName, currentUser?.lastName].filter(Boolean).join(" ") || "Teacher";

  return (
    <div className="teacher-container">
      <TeacherSidebar />

      <div className="teacher-content">

        <h1 className="teacher-page-title">Teacher Dashboard</h1>

        <section className="teacher-hero">
          <div>
            <h2 className="teacher-hero-title">Welcome, {teacherName}</h2>
            <p className="teacher-hero-text">
              Track class setup, manage team readiness, and monitor questionnaire coverage from one focused view.
            </p>
          </div>
          <div className="teacher-hero-actions">
            <button 
              className="pending-badge-btn" 
              onClick={() => setShowPendingModal(true)}
              title="View Pending Evaluations"
            >
              <Bell size={18} />
              <span>{pendingCount} Pending Evaluations</span>
            </button>
          </div>
        </section>



        {error && <div className="error-message">{error}</div>}

        <div className="section activity-logs-section">
          <div className="section-header">
            <div className="header-left">
              <h2>Recent Submission Logs</h2>
              <input 
                type="text" 
                className="log-search-input" 
                placeholder="Search logs..." 
                value={logSearch}
                onChange={(e) => setLogSearch(e.target.value)}
              />
            </div>
            <div className="header-actions">
              <button 
                className="btn btn-secondary btn-sm" 
                onClick={async () => {
                  setLogsLoading(true);
                  try {
                    const logs = await teacherReportAPI.getActivityLogs();
                    setActivityLogs(logs || []);
                  } finally {
                    setLogsLoading(false);
                  }
                }}
                disabled={logsLoading}
              >
                {logsLoading ? "Refreshing..." : "Refresh"}
              </button>
            </div>
          </div>

          {logsLoading && activityLogs.length === 0 ? (
            <div className="logs-loading">
              <div className="spinner"></div>
              <p>Fetching latest updates...</p>
            </div>
          ) : activityLogs.length === 0 ? (
            <div className="no-logs-message">
              <p>No submission logs available yet.</p>
            </div>
          ) : (
            <>
              <div className="logs-feed">
                {pagLogs.map((log, idx) => (
                  <div key={idx} className={`log-item ${log.type?.toLowerCase()}`}>
                    <div className="log-icon">
                      {log.type === "ADVISER_SUBMISSION" ? (
                        <UserCheck size={20} color="#64b4ff" />
                      ) : (
                        <GraduationCap size={20} color="var(--dtm-gold)" />
                      )}
                    </div>
                    <div className="log-content">
                      <p className="log-message">{log.message}</p>
                      <span className="log-time">
                        {new Date(log.timestamp).toLocaleString(undefined, {
                          dateStyle: 'medium',
                          timeStyle: 'short'
                        })}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
              <Pagination 
                currentPage={curPageLogs} 
                totalPages={totPageLogs} 
                onPageChange={goPageLogs} 
              />
            </>
          )}
        </div>

      </div>
      {/* Manage Class Modal */}
      {showTeamsModal && selectedClass && createPortal((
        <div className="modal-overlay" onClick={() => setShowTeamsModal(false)}>
          <div className="modal-content modal-large" onClick={(e) => e.stopPropagation()}>
            <h2>Manage Class: {selectedClass.name} {selectedClass.section}</h2>
            
            <div className="class-overview-panel">
              <p><strong>Class:</strong> {selectedClass.name} {selectedClass.section || ""}</p>
              <p><strong>School Year:</strong> {selectedClass.schoolYear}</p>
              <p><strong>Students:</strong> {getStudentsForClass(selectedClass.id).length}</p>
              <p><strong>Teams:</strong> {getTeamsForClass(selectedClass.id).length}</p>
              <p><strong>Questionnaires:</strong> {getQuestionnairesForClass(selectedClass.id).length}</p>
            </div>

            {/* Teams Section */}
            <div className="team-section" style={{ marginTop: "24px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3>Teams ({getTeamsForClass(selectedClass.id).length})</h3>
              </div>
              {getTeamsForClass(selectedClass.id).length === 0 ? (
                <p>No teams in this class yet.</p>
              ) : (
                <>
                <table className="class-table" style={{ marginTop: "10px" }}>
                  <thead>
                    <tr>
                      <th>Team Name</th>
                      <th>Description</th>
                      <th>Members</th>
                      <th>Advisers</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagTeams.map((team) => (
                      <tr key={team.id}>
                        <td>{team.name}</td>
                        <td>{team.description || "N/A"}</td>
                        <td>{team.memberIds?.length || 0}</td>
                        <td>{team.adviserIds?.length || 0}</td>
                        <td>
                          <span style={{ 
                            color: team.isActive ? '#28a745' : '#6c757d',
                            fontWeight: 'bold'
                          }}>
                            {team.isActive ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td>
                          <button 
                            className="btn btn-sm" 
                            onClick={() => handleViewTeamMembers(team)}
                          >
                            View Members
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination currentPage={curPageTeams} totalPages={totPageTeams} onPageChange={goPageTeams} />
                </>
              )}
            </div>

            {/* Questionnaires Section */}
            <div className="team-section" style={{ marginTop: "20px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3>Questionnaires ({getQuestionnairesForClass(selectedClass.id).length})</h3>
              </div>
              {getQuestionnairesForClass(selectedClass.id).length === 0 ? (
                <p>No questionnaires assigned to this class yet.</p>
              ) : (
                <>
                <table className="class-table" style={{ marginTop: "10px" }}>
                  <thead>
                    <tr>
                      <th>Questionnaire Title</th>
                      <th>Description</th>
                      <th>Target</th>
                      <th>Questions</th>
                      <th>Status</th>
                      <th>Created Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagQuest.map((questionnaire) => (
                      <tr key={questionnaire.id}>
                        <td>{questionnaire.title}</td>
                        <td>{questionnaire.description || "N/A"}</td>
                        <td>
                          <span style={{
                            padding: '2px 8px',
                            borderRadius: '10px',
                            fontSize: '10px',
                            fontWeight: '700',
                            background: questionnaire.target === 'ADVISER' ? '#cce5ff' : '#fff3cd',
                            color: questionnaire.target === 'ADVISER' ? '#004085' : '#856404',
                          }}>
                            {questionnaire.target === 'ADVISER' ? 'Adviser' : 'Student'}
                          </span>
                        </td>
                        <td>{questionnaire.questionCount}</td>
                        <td>{questionnaire.isActive ? "Active" : "Inactive"}</td>
                        <td>{new Date(questionnaire.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination currentPage={curPageQuest} totalPages={totPageQuest} onPageChange={goPageQuest} />
                </>
              )}
            </div>

            <div className="modal-actions" style={{ marginTop: "20px" }}>
              <button className="btn btn-secondary" onClick={() => setShowTeamsModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      ), document.body)}

      {/* Team Members Modal */}
      {showTeamMembersModal && selectedTeam && createPortal((
        <div className="modal-overlay" onClick={() => setShowTeamMembersModal(false)}>
          <div className="modal-content modal-large" onClick={(e) => e.stopPropagation()}>
            <h2>Team Members: {selectedTeam.name}</h2>
            
            <div className="class-overview-panel">
              <p><strong>Team:</strong> {selectedTeam.name}</p>
              <p><strong>Description:</strong> {selectedTeam.description || "N/A"}</p>
              <p><strong>Total Members:</strong> {selectedTeam.memberIds?.length || 0}</p>
            </div>

            <div className="team-section" style={{ marginTop: "24px" }}>
              {getStudentsForTeam(selectedTeam).length === 0 ? (
                <div style={{ padding: "20px", textAlign: "center", color: "#6c757d" }}>
                  <p>No students assigned to this team yet.</p>
                </div>
              ) : (
                <div style={{
                  maxHeight: "450px",
                  overflowY: "auto",
                  border: "1px solid rgba(255, 255, 255, 0.1)",
                  borderRadius: "4px"
                }}>
                  <table className="class-table">
                    <thead>
                      <tr>
                        <th>Student ID</th>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Phone</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pagStud.map((student) => (
                        <tr key={student.id}>
                          <td>{student.studentId}</td>
                          <td>{student.firstName} {student.lastName}</td>
                          <td>{student.email || "N/A"}</td>
                          <td>{student.phoneNumber || "N/A"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <Pagination currentPage={curPageStud} totalPages={totPageStud} onPageChange={goPageStud} />
                </div>
              )}
            </div>

            <div className="modal-actions" style={{ marginTop: "20px" }}>
              <button className="btn btn-secondary" onClick={() => setShowTeamMembersModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      ), document.body)}

      {createPortal(
        <PendingEvaluationsModal 
          isOpen={showPendingModal} 
          onClose={() => setShowPendingModal(false)} 
          adviserPending={adviserPending}
          studentPending={studentPending}
        />,
        document.body
      )}
    </div>
  );
};

export default Teacher;
