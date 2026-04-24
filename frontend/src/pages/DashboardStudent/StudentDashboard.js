import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import StudentSidebar from "../../components/Sidebar/StudentSidebar";
import { useToast } from "../../contexts/ToastContext";
import "../DashboardTeacher/Teacher.css";

const API_BASE_URL = "http://localhost:8080";

const StudentDashboard = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [questionnaires, setQuestionnaires] = useState([]);
  
  const currentUser = useMemo(() => {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  }, []);

  useEffect(() => {
    const fetchQuestionnaires = async () => {
      try {
        const token = currentUser ? currentUser.token : '';
        if (!token) return;

        const res = await fetch(`${API_BASE_URL}/api/student/questionnaires`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });
        
        if (!res.ok) throw new Error("Failed to fetch questionnaires");
        
        const data = await res.json();
        setQuestionnaires(data);
      } catch (err) {
        toast.error(err.message);
      } finally {
        setLoading(false);
      }
    };
    
    fetchQuestionnaires();
  }, [currentUser, toast]);

  const handleActionClick = (q) => {
    navigate(`/student/evaluate/${q.id}`);
  };

  const statusCounts = useMemo(() => {
    const counts = { ALL: questionnaires.length, READY: 0, IN_PROGRESS: 0, SUBMITTED: 0 };
    questionnaires.forEach(q => {
      const isComplete = q.peerTasks?.every(t => t.status === 'SUBMITTED');
      const isStarted = q.peerTasks?.some(t => t.status === 'SUBMITTED');
      if (isComplete) counts.SUBMITTED++;
      else if (isStarted) counts.IN_PROGRESS++;
      else counts.READY++;
    });
    return counts;
  }, [questionnaires]);

  const [statusFilter, setStatusFilter] = useState("ALL");
  const [searchTerm, setSearchTerm] = useState("");

  const filteredQuestionnaires = useMemo(() => {
    return questionnaires.filter(q => {
      const isComplete = q.peerTasks?.every(t => t.status === 'SUBMITTED');
      const isStarted = q.peerTasks?.some(t => t.status === 'SUBMITTED');
      const status = isComplete ? 'SUBMITTED' : (isStarted ? 'IN_PROGRESS' : 'READY');
      
      if (statusFilter !== 'ALL' && status !== statusFilter) return false;
      if (searchTerm && !q.title.toLowerCase().includes(searchTerm.toLowerCase())) return false;
      return true;
    });
  }, [questionnaires, statusFilter, searchTerm]);

  const studentName = [currentUser?.firstName, currentUser?.lastName].filter(Boolean).join(" ") || "Student";

  return (
    <div className="teacher-container">
      <StudentSidebar />
      <div className="teacher-content">
        <h1 className="teacher-page-title">Student Dashboard</h1>

        <section className="teacher-hero">
          <div>
            <p className="teacher-hero-kicker">Student Evaluation Queue</p>
            <h2 className="teacher-hero-title">Welcome, {studentName}</h2>
            <p className="teacher-hero-text">
              Track your evaluation progress and complete pending tasks for your team.
            </p>
          </div>
          <div className="adviser-eval-metrics">
            <span><strong>{statusCounts.ALL}</strong> total</span>
            <span><strong>{statusCounts.IN_PROGRESS}</strong> in progress</span>
            <span><strong>{statusCounts.SUBMITTED}</strong> completed</span>
          </div>
        </section>

        <section className="section adviser-queue-controls">
          <div className="adviser-status-tabs">
            {[
              { key: "ALL", label: "All" },
              { key: "READY", label: "Ready" },
              { key: "IN_PROGRESS", label: "In Progress" },
              { key: "SUBMITTED", label: "Completed" },
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
            placeholder="Search questionnaire title..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </section>

        <div className="section">
          <div className="section-header-row">
            <h2>Your Evaluation Tasks</h2>
          </div>
          
          {loading ? (
            <p>Loading...</p>
          ) : questionnaires.length === 0 ? (
            <p style={{ marginTop: 20, color: 'var(--dtm-muted)' }}>You have no assigned questionnaires at this time.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Title</th>
                  <th>Description</th>
                  <th>Status</th>
                  <th>Progress</th>
                  <th>Assigned Date</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredQuestionnaires.map((q, idx) => {
                  const completed = q.peerTasks?.filter(t => t.status === 'SUBMITTED').length || 0;
                  const total = q.peerTasks?.length || 0;
                  const isComplete = completed === total && total > 0;
                  const isStarted = completed > 0;
                  const progressPercent = total > 0 ? Math.round((completed / total) * 100) : 0;

                  return (
                    <tr key={q.id}>
                      <td>{idx + 1}</td>
                      <td><strong>{q.title}</strong></td>
                      <td>{q.description || "No description"}</td>
                      <td>
                        {isComplete ? (
                          <span className="status-badge status-active">Completed</span>
                        ) : isStarted ? (
                          <span className="status-badge adviser-status-progress">In Progress</span>
                        ) : (
                          <span className="status-badge status-active">Ready</span>
                        )}
                      </td>
                      <td>
                        <div className="adviser-progress-wrap">
                          <div className="adviser-progress-track">
                            <div className="adviser-progress-fill" style={{ width: `${progressPercent}%` }}></div>
                          </div>
                          <span className="adviser-progress-text">
                            {progressPercent}% ({completed}/{total})
                          </span>
                        </div>
                      </td>
                      <td>{new Date(q.createdAt).toLocaleDateString()}</td>
                      <td>
                        {isComplete ? (
                          <span style={{ color: '#4ade80', fontSize: '0.85rem', fontWeight: 600 }}>Submitted ✓</span>
                        ) : (
                          <button 
                            className="btn btn-sm" 
                            onClick={() => handleActionClick(q)}
                          >
                            {isStarted ? 'Resume' : 'Start'}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default StudentDashboard;
