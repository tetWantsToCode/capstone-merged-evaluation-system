import React from "react";
import { useNavigate } from "react-router-dom";
import "../pages/Login/Login.css";

export default function ThankYou() {
  const navigate = useNavigate();
  const userStr = localStorage.getItem("user");
  const user = userStr ? JSON.parse(userStr) : null;
  const isStudent = user?.role === "STUDENT";

  return (
    <div className="login-container">
      <div className="login-box" style={{ maxWidth: 420, textAlign: "center" }}>
        <div style={{ fontSize: 52, marginBottom: 16, lineHeight: 1 }}>✓</div>
        <h1>Thank You</h1>
        <p style={{ color: "var(--dtm-muted, #a09890)", marginBottom: 24, fontSize: 15, lineHeight: 1.6 }}>
          Your peer evaluation has been submitted. Your feedback helps promote fairness and improvement.
        </p>
        <button
          type="button"
          className="login-button"
          onClick={() => navigate(isStudent ? "/student/dashboard" : "/teacher/dashboard")}
        >
          Back to Dashboard
        </button>
      </div>
    </div>
  );
}
