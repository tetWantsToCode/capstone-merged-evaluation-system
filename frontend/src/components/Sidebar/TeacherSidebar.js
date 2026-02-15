import React from "react";
import { useNavigate } from "react-router-dom";
import { authAPI } from "../../services/api";
import "./Sidebar.css";

const TeacherSidebar = () => {
  const navigate = useNavigate();

  const handleLogout = () => {
    authAPI.logout();
    navigate('/login');
  };

  return (
    <div className="sidebar">
      <h2>Teacher Panel</h2>
      <ul>
        <li onClick={() => navigate('/teacher/dashboard')}>Dashboard</li>
        <li onClick={() => navigate('/teacher/classes')}>Classes</li>
        <li onClick={() => navigate('/teacher/teams')}>Teams</li>
        <li onClick={() => navigate('/teacher/students')}>Students</li>
        <li onClick={() => navigate('/teacher/questionnaires')}>Questionnaires</li>
        <li onClick={() => navigate('/teacher/reports')}>Reports</li>
        <li onClick={() => navigate('/profile')}>Profile</li>
        <li onClick={handleLogout}>Logout</li>
      </ul>
    </div>
  );
};

export default TeacherSidebar;
