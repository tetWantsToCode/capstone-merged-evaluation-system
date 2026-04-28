import React from 'react';
import './Aurora.css';

/**
 * Aurora — animated gradient background with soft glowing blobs.
 * Inspired by react-bits Aurora component.
 */
const Aurora = ({ colorStops = ['#8a151f', '#f2c94c', '#6c0f17', '#d4a843'], speed = 6 }) => {
  return (
    <div className="aurora-container" style={{ '--aurora-speed': `${speed}s` }}>
      {colorStops.map((color, i) => (
        <div
          key={i}
          className={`aurora-blob aurora-blob-${i + 1}`}
          style={{ '--blob-color': color }}
        />
      ))}
      <div className="aurora-noise" />
    </div>
  );
};

export default Aurora;
