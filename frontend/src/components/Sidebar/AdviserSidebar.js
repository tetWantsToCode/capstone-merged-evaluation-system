import React from "react";
import { useNavigate } from "react-router-dom";
import { authAPI } from "../../services/api";
import "./Sidebar.css";

const AdviserSidebar = () => {
  const navigate = useNavigate();

  const handleLogout = () => {
    authAPI.logout();
    navigate("/login");
  };

  return (
    <div className="sidebar">
      <h2>Adviser Panel</h2>
      <ul>
        <li onClick={() => navigate("/adviser/dashboard")}>
          Dashboard
        </li>

        {/* IMPORTANT:
            Evaluations require a teamId.
            Adviser must select a team from Dashboard first.
        */}
        <li onClick={() => navigate("/adviser/dashboard")}>
          Evaluations
        </li>

        <li onClick={() => navigate("/adviser/completed")}>
          Completed
        </li>

        <li onClick={() => navigate("/profile")}>
          Profile
        </li>

        <li onClick={handleLogout}>
          Logout
        </li>
      </ul>
    </div>
  );
};

export default AdviserSidebar;
