import React, { useEffect, useState, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AdviserSidebar from "../../components/Sidebar/AdviserSidebar";
import { adviserAPI, questionnaireAPI, teamAPI } from "../../services/api";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/Pagination/Pagination";
import "./Adviser.css";

const AdviserStudentEvaluations = () => {
  const { teamId } = useParams();
  const navigate = useNavigate();

  const [students, setStudents] = useState([]);
  const [questionnaires, setQuestionnaires] = useState([]);
  const [teamName, setTeamName] = useState("");
  const [selectedQuestionnaire, setSelectedQuestionnaire] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    const load = async () => {
      try {
        const team = await teamAPI.getTeamById(teamId);
        setTeamName(team?.name || "");

        const [studentsData, allQuestionnaires] = await Promise.all([
          adviserAPI.getTeamStudents(teamId),
          questionnaireAPI.getQuestionnairesByClass(team.classId),
        ]);
        const questionnairesData = allQuestionnaires.filter(q => q.target === 'ADVISER_STUDENT');

        setStudents(studentsData);
        setQuestionnaires(questionnairesData);

        // Default to first active questionnaire
        const firstActive = questionnairesData.find(q => q.isActive !== false);
        if (firstActive) setSelectedQuestionnaire(firstActive);
      } catch (err) {
        setError("Failed to load: " + err.message);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [teamId]);

  const filteredStudents = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return students;
    return students.filter(s =>
      `${s.firstName} ${s.lastName}`.toLowerCase().includes(term) ||
      s.studentNumber?.toLowerCase().includes(term)
    );
  }, [students, searchTerm]);

  const { currentPage, totalPages, paginatedData, goToPage } = usePagination(filteredStudents, 10);

  return (
    <div className="adviser-container">
      <AdviserSidebar />
      <div className="adviser-content">
        <h1>Student Evaluations</h1>

        <section className="adviser-hero adviser-hero-tight">
          <div>
            <p className="adviser-hero-kicker">Individual Student Evaluation</p>
            <h2 className="adviser-hero-title">
              {teamName ? `${teamName} — Students` : "Team Students"}
            </h2>
            <p className="adviser-hero-text">
              Select a questionnaire and evaluate each student individually.
            </p>
          </div>
          <div className="adviser-eval-metrics">
            <span><strong>{students.length}</strong> students</span>
            <span><strong>{questionnaires.length}</strong> questionnaires</span>
          </div>
        </section>

        {/* Questionnaire Selector */}
        <div className="section" style={{ marginBottom: 0 }}>
          <div className="section-header-row">
            <h2>Select Questionnaire</h2>
          </div>
          {loading ? (
            <p>Loading...</p>
          ) : questionnaires.length === 0 ? (
            <p style={{ color: "var(--dtm-muted)" }}>No questionnaires available for this team.</p>
          ) : (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "10px" }}>
              {questionnaires.map(q => {
                const isSelected = selectedQuestionnaire?.id === q.id;
                const isLocked = q.isActive === false;
                return (
                  <button
                    key={q.id}
                    onClick={() => !isLocked && setSelectedQuestionnaire(q)}
                    className={isSelected ? "btn" : "btn-secondary"}
                    style={{
                      opacity: isLocked ? 0.5 : 1,
                      cursor: isLocked ? "not-allowed" : "pointer",
                      position: "relative",
                    }}
                    title={isLocked ? "This questionnaire is locked" : q.title}
                  >
                    {q.title}
                    {isLocked && (
                      <span style={{
                        marginLeft: "8px",
                        fontSize: "11px",
                        color: "#ff8f8f",
                      }}>
                        Locked
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Students Table */}
        <div className="section">
          <div className="section-header-row">
            <h2>Students</h2>
            <input
              type="text"
              className="adviser-search-input"
              placeholder="Search by name or student number..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              style={{ maxWidth: "320px" }}
            />
          </div>

          {error && <div className="error-message">{error}</div>}

          {loading ? (
            <p>Loading students...</p>
          ) : filteredStudents.length === 0 ? (
            <p style={{ color: "var(--dtm-muted)" }}>
              {searchTerm ? "No students match your search." : "No students in this team."}
            </p>
          ) : (
            <>
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Student Number</th>
                  <th>Name</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {paginatedData.map((student, index) => (
                  <tr key={student.studentId}>
                    <td>{(currentPage - 1) * 10 + index + 1}</td>
                    <td>{student.studentNumber}</td>
                    <td>
                      <strong>{student.firstName} {student.lastName}</strong>
                    </td>
                    <td>
                      {!selectedQuestionnaire ? (
                        <span style={{ color: "var(--dtm-muted)", fontSize: "13px" }}>
                          Select a questionnaire first
                        </span>
                      ) : selectedQuestionnaire.isActive === false ? (
                        <span className="adviser-locked-label">Questionnaire Locked</span>
                      ) : (
                        <button
                          className="btn adviser-open-btn"
                          onClick={() =>
                            navigate(
                              `/adviser/student-evaluate/${teamId}/${student.studentId}/${selectedQuestionnaire.id}`
                            )
                          }
                        >
                          Evaluate Student
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={goToPage} />
            </>
          )}

          <div style={{ marginTop: "20px" }}>
            <button
              className="btn-secondary"
              onClick={() => navigate(`/adviser/evaluations/${teamId}`)}
            >
              ← Back to Team Evaluations
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdviserStudentEvaluations;