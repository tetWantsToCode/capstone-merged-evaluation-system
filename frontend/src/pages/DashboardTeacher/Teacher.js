import React, { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import SummaryCard from "../../components/Cards/SummaryCard";
import PendingEvaluationsModal from "../../components/Modal/PendingEvaluationsModal";
import { classAPI, studentAPI, teamAPI, questionnaireAPI, teacherReportAPI } from "../../services/api";
import "./Teacher.css";

const Teacher = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [classes, setClasses] = useState([]);
  const [students, setStudents] = useState([]);
  const [teams, setTeams] = useState([]);
  const [questionnaires, setQuestionnaires] = useState([]);
  const [classSearch, setClassSearch] = useState("");
  const [showTeamsModal, setShowTeamsModal] = useState(false);
  const [selectedClass, setSelectedClass] = useState(null);
  const [showTeamMembersModal, setShowTeamMembersModal] = useState(false);
  const [selectedTeam, setSelectedTeam] = useState(null);
  const [pendingEvaluations, setPendingEvaluations] = useState([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [showPendingModal, setShowPendingModal] = useState(false);

  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
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
          (c) => c.teacherId === currentUser.id
        );

        const teacherClassIds = new Set(teacherClasses.map((c) => c.id));

        const teacherStudents = (allStudents || []).filter(
          (s) => Array.isArray(s.classIds) && s.classIds.some((id) => teacherClassIds.has(id))
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

        setClasses(teacherClasses);
        setStudents(teacherStudents);
        setTeams(teacherTeams);
        setQuestionnaires(questionnairesMap);

        // Load pending evaluations
        try {
          const pendingData = await teacherReportAPI.getPendingEvaluations();
          setPendingEvaluations(pendingData.pending || []);
          setPendingCount(pendingData.total || 0);
        } catch (err) {
          console.error("Error fetching pending evaluations:", err);
          setPendingCount(0);
          setPendingEvaluations([]);
        }
      } catch (e) {
        setError(e?.message || "Failed to load dashboard data");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [currentUser]);

  const teamsByClassId = useMemo(() => {
    const map = new Map();
    for (const t of teams) {
      const key = t.classId;
      map.set(key, (map.get(key) || 0) + 1);
    }
    return map;
  }, [teams]);

  const handleManageClass = (classItem) => {
    setSelectedClass(classItem);
    setShowTeamsModal(true);
  };

  const getTeamsForClass = (classId) => {
    return teams.filter(t => t.classId === classId);
  };

  const getStudentsForClass = (classId) => {
    return students.filter(s => s.classIds && s.classIds.includes(classId));
  };

  const getQuestionnairesForClass = (classId) => {
    return questionnaires[classId] || [];
  };

  const filteredClasses = useMemo(() => {
    const normalizedSearch = classSearch.trim().toLowerCase();

    return classes.filter((c) => {
      const classText = `${c.name || ""} ${c.section || ""}`.toLowerCase();

      if (!normalizedSearch) {
        return true;
      }

      return classText.includes(normalizedSearch);
    });
  }, [classes, classSearch]);

  const getStudentsForTeam = (team) => {
    if (!team || !team.memberIds || team.memberIds.length === 0) return [];
    // Convert memberIds to strings for comparison since IDs can be stored as different types
    const memberIdStrings = team.memberIds.map(id => String(id));
    return students.filter(s => s && memberIdStrings.includes(String(s.id)));
  };

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
            <p className="teacher-hero-kicker">Academic Control Center</p>
            <h2 className="teacher-hero-title">Welcome, {teacherName}</h2>
            <p className="teacher-hero-text">
              Track class setup, manage team readiness, and monitor questionnaire coverage from one focused view.
            </p>
          </div>
          <div className="teacher-hero-actions">
          </div>
        </section>

        <div className="summary-row">
          <SummaryCard title="Total Classes" value={loading ? "-" : String(classes.length)} />
          <SummaryCard title="Total Students" value={loading ? "-" : String(students.length)} />
          <SummaryCard title="Total Teams" value={loading ? "-" : String(teams.length)} />
          <div className="summary-card-wrapper" onClick={() => setShowPendingModal(true)}>
            <SummaryCard title="Pending Evaluations" value={loading ? "-" : String(pendingCount)} />
          </div>
        </div>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div className="classes-header">
            <h2>Your Classes</h2>
          </div>

          {loading ? (
            <p>Loading...</p>
          ) : classes.length === 0 ? (
            <p>No classes found.</p>
          ) : filteredClasses.length === 0 ? (
            <p>No classes matched your current filter.</p>
          ) : (

          <table className="class-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Class</th>
                <th>Section</th>
                <th>School Year</th>
                <th>Students</th>
                <th>Teams</th>
                <th>Questionnaires</th>
                <th>Action</th>
              </tr>
            </thead>

            <tbody>
              {filteredClasses.map((c, index) => {
                const questionnaireCount = getQuestionnairesForClass(c.id).length;
                const hasQuestionnaire = questionnaireCount > 0;

                return (
                <tr key={c.id}>
                  <td>{index + 1}</td>
                  <td>{c.name}</td>
                  <td>{c.section || "N/A"}</td>
                  <td>{c.schoolYear}</td>
                  <td>{getStudentsForClass(c.id).length} Students</td>
                  <td>{teamsByClassId.get(c.id) || 0} Teams</td>
                  <td className="table-chip-cell">
                    {hasQuestionnaire ? (
                      <span className="questionnaire-count-badge is-available">
                        {questionnaireCount} {questionnaireCount === 1 ? "assigned" : "assigned"}
                      </span>
                    ) : (
                      <span className="questionnaire-count-badge is-zero">
                        0 assigned
                      </span>
                    )}
                  </td>
                  <td className="table-action-cell">
                    <button className="btn" onClick={() => handleManageClass(c)}>
                      Manage Class
                    </button>
                  </td>
                </tr>
              );})}
            </tbody>
          </table>

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
                    {getTeamsForClass(selectedClass.id).map((team) => (
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
                <table className="class-table" style={{ marginTop: "10px" }}>
                  <thead>
                    <tr>
                      <th>Title</th>
                      <th>Description</th>
                      <th>Questions</th>
                      <th>Status</th>
                      <th>Created Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {getQuestionnairesForClass(selectedClass.id).map((questionnaire) => (
                      <tr key={questionnaire.id}>
                        <td>{questionnaire.title}</td>
                        <td>{questionnaire.description || "N/A"}</td>
                        <td>{questionnaire.questionCount}</td>
                        <td>{questionnaire.isActive ? "Active" : "Inactive"}</td>
                        <td>{new Date(questionnaire.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
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
                  maxHeight: "400px",
                  overflowY: "auto",
                  border: "1px solid #e0e0e0",
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
                      {getStudentsForTeam(selectedTeam).map((student) => (
                        <tr key={student.id}>
                          <td>{student.studentId}</td>
                          <td>{student.firstName} {student.lastName}</td>
                          <td>{student.email || "N/A"}</td>
                          <td>{student.phoneNumber || "N/A"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
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
          pendingEvaluations={pendingEvaluations}
        />,
        document.body
      )}
    </div>
  );
};

export default Teacher;
