import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { authAPI } from "../../services/api";
import "./Sidebar.css";

const TeacherSidebar = () => {
  const navigate = useNavigate();
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [user, setUser] = useState(null);
  const menuRef = useRef(null);

  useEffect(() => {
    const stored = localStorage.getItem('user');
    if (stored) {
      setUser(JSON.parse(stored));
    }
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowProfileMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    authAPI.logout();
    navigate('/login');
  };

  const getInitials = () => {
    if (!user) return '?';
    const f = user.firstName?.[0] || '';
    const l = user.lastName?.[0] || '';
    return (f + l).toUpperCase() || user.email?.[0]?.toUpperCase() || '?';
  };

  return (
    <div className="sidebar">
      <h2>Teacher Panel</h2>
      <ul>
        <li onClick={() => navigate('/teacher/dashboard')}>Dashboard</li>
        <li onClick={() => navigate('/teacher/classes')}>Classes</li>
        <li onClick={() => navigate('/teacher/teams')}>Teams</li>
        <li onClick={() => navigate('/teacher/students')}>Students</li>
        <li onClick={() => navigate('/teacher/advisers')}>Advisers</li>
        <li onClick={() => navigate('/teacher/user-management')}>User Management</li>
        <li onClick={() => navigate('/teacher/questionnaires')}>Questionnaires</li>
        <li onClick={() => navigate('/teacher/reports')}>Reports</li>
      </ul>

      <div className="sidebar-profile" ref={menuRef}>
        <div className="profile-avatar" onClick={() => setShowProfileMenu(!showProfileMenu)}>
          <span className="avatar-initials">{getInitials()}</span>
          {user && <span className="avatar-name">{user.firstName} {user.lastName}</span>}
        </div>
        {showProfileMenu && (
          <div className="profile-dropdown">
            <div className="profile-dropdown-header">
              <span className="dropdown-email">{user?.email}</span>
            </div>
            <div className="profile-dropdown-item" onClick={() => { navigate('/profile'); setShowProfileMenu(false); }}>
              Profile
            </div>
            <div className="profile-dropdown-item profile-dropdown-logout" onClick={handleLogout}>
              Logout
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default TeacherSidebar;
