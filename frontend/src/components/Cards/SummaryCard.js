import React from "react";
import "./Cards.css";

const SummaryCard = ({ title, value, icon }) => {
  return (
    <div className="summary-card">
      {icon && <div className="summary-card-icon">{icon}</div>}
      <h3>{title}</h3>
      <p>{value}</p>
    </div>
  );
};

export default SummaryCard;
