import React, { useState, useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { authAPI } from "../../services/api";
import { LayoutDashboard, GraduationCap, UserCheck, ClipboardList, BarChart2, TrendingUp, Users } from "lucide-react";
import "./Sidebar.css";

const TeacherSidebar = () => {
  const navigate = useNavigate();
  const location = useLocation();
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

  const handleNavigate = (path) => {
    navigate(path);
  };

  const getInitials = () => {
    if (!user) return '?';
    const f = user.firstName?.[0] || '';
    const l = user.lastName?.[0] || '';
    return (f + l).toUpperCase() || user.email?.[0]?.toUpperCase() || '?';
  };

  const menuItems = [
    { label: "Dashboard", icon: LayoutDashboard, path: "/teacher/dashboard" },
    { label: "Students", icon: GraduationCap, path: "/teacher/students" },
    { label: "Advisers", icon: UserCheck, path: "/teacher/advisers" },
    { label: "Questionnaires", icon: ClipboardList, path: "/teacher/questionnaires" },
    { label: "Reports", icon: BarChart2, path: "/teacher/reports" },
    { label: "Performance", icon: TrendingUp, path: "/teacher/performance" },
    { label: "User Management", icon: Users, path: "/teacher/user-management" },
  ];

  return (
    <div className="sidebar sidebar--teacher">
      <h2>Teacher Panel</h2>
      <ul>
        {menuItems.map((item) => (
          <li
            key={item.path}
            className={location.pathname.startsWith(item.path) ? "is-active" : ""}
            onClick={() => handleNavigate(item.path)}
          >
            <item.icon size={18} className="nav-icon" aria-hidden="true" />
            <span>{item.label}</span>
          </li>
        ))}
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
            <div className="profile-dropdown-item" onClick={() => { handleNavigate('/profile'); setShowProfileMenu(false); }}>
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
