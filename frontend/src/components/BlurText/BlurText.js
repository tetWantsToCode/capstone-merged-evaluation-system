import React, { useEffect, useState } from 'react';

/**
 * BlurText — text that fades in word-by-word with a blur-to-sharp animation.
 * Inspired by react-bits BlurText component.
 */
const BlurText = ({
  text = '',
  delay = 80,
  className = '',
  animateBy = 'words', // 'words' or 'letters'
}) => {
  const [visibleCount, setVisibleCount] = useState(0);
  const parts = animateBy === 'words' ? text.split(' ') : text.split('');

  useEffect(() => {
    if (visibleCount < parts.length) {
      const timer = setTimeout(() => setVisibleCount((c) => c + 1), delay);
      return () => clearTimeout(timer);
    }
  }, [visibleCount, parts.length, delay]);

  return (
    <span className={className}>
      {parts.map((part, i) => (
        <span
          key={i}
          style={{
            display: 'inline-block',
            transition: 'filter 0.5s ease, opacity 0.5s ease, transform 0.5s ease',
            filter: i < visibleCount ? 'blur(0px)' : 'blur(8px)',
            opacity: i < visibleCount ? 1 : 0,
            transform: i < visibleCount ? 'translateY(0)' : 'translateY(6px)',
          }}
        >
          {part}
          {animateBy === 'words' && i < parts.length - 1 ? '\u00A0' : ''}
        </span>
      ))}
    </span>
  );
};

export default BlurText;
