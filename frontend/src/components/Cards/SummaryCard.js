import React from "react";
import "./Cards.css";

const SummaryCard = ({ title, value }) => {
  return (
    <div className="summary-card">
      <h3>{title}</h3>
      <p>{value}</p>
    </div>
  );
};

export default SummaryCard;
