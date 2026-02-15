import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import SummaryCard from "../../components/Cards/SummaryCard";
import { classAPI, studentAPI, teamAPI } from "../../services/api";
import "./Teacher.css";

const Teacher = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [classes, setClasses] = useState([]);
  const [students, setStudents] = useState([]);
  const [teams, setTeams] = useState([]);

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

        setClasses(teacherClasses);
        setStudents(teacherStudents);
        setTeams(teacherTeams);
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

  return (
    <div className="teacher-container">
      <TeacherSidebar />

      <div className="teacher-content">

        <h1>Teacher Dashboard</h1>

        <div className="summary-row">
          <SummaryCard title="Total Classes" value={loading ? "-" : String(classes.length)} />
          <SummaryCard title="Total Students" value={loading ? "-" : String(students.length)} />
          <SummaryCard title="Total Teams" value={loading ? "-" : String(teams.length)} />
          <SummaryCard title="Pending Evaluations" value={loading ? "-" : "0"} />
        </div>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <h2>Your Classes</h2>

          {loading ? (
            <p>Loading...</p>
          ) : classes.length === 0 ? (
            <p>No classes found.</p>
          ) : (

          <table className="class-table">
            <thead>
              <tr>
                <th>Class</th>
                <th>Section</th>
                <th>School Year</th>
                <th>Teams</th>
                <th>Action</th>
              </tr>
            </thead>

            <tbody>
              {classes.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.section || "N/A"}</td>
                  <td>{c.schoolYear}</td>
                  <td>{teamsByClassId.get(c.id) || 0} Teams</td>
                  <td>
                    <button className="btn" onClick={() => navigate("/teacher/classes")}>
                      Manage
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

export default Teacher;
