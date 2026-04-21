import React, { useRef, useEffect, useState, useCallback } from 'react';

/**
 * TextPressure — a mouse-reactive text component using CSS variable fonts.
 * Ported from https://codepen.io/JuanFuentes/full/rgXKGQ
 */
const TextPressure = ({
  text = 'Hello!',
  fontFamily = 'sans-serif',
  flex = true,
  alpha = false,
  stroke = false,
  width = true,
  weight = true,
  italic = true,
  textColor = '#ffffff',
  strokeColor = '#5227FF',
  minFontSize = 36,
}) => {
  const containerRef = useRef(null);
  const [cursor, setCursor] = useState({ x: 0, y: 0 });
  const [isHovering, setIsHovering] = useState(false);

  const handleMouseMove = useCallback((e) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    setCursor({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  }, []);

  const handleTouchMove = useCallback((e) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect || !e.touches[0]) return;
    setCursor({
      x: e.touches[0].clientX - rect.left,
      y: e.touches[0].clientY - rect.top,
    });
  }, []);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    el.addEventListener('mousemove', handleMouseMove);
    el.addEventListener('touchmove', handleTouchMove, { passive: true });
    return () => {
      el.removeEventListener('mousemove', handleMouseMove);
      el.removeEventListener('touchmove', handleTouchMove);
    };
  }, [handleMouseMove, handleTouchMove]);

  const getDistance = (charX, charY) => {
    const dx = cursor.x - charX;
    const dy = cursor.y - charY;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const getStyle = (index, total) => {
    if (!containerRef.current) return {};

    const rect = containerRef.current.getBoundingClientRect();
    const charX = (rect.width / (total + 1)) * (index + 1);
    const charY = rect.height / 2;

    const maxDist = Math.max(rect.width, rect.height);
    const dist = isHovering ? getDistance(charX, charY) : maxDist;
    const proximity = Math.max(0, 1 - dist / maxDist);

    const style = {
      display: 'inline-block',
      color: textColor,
      fontFamily,
      fontSize: `${minFontSize}px`,
      transition: 'font-variation-settings 0.3s ease, opacity 0.3s ease',
      WebkitTextStroke: stroke ? `1px ${strokeColor}` : 'none',
    };

    const settings = [];
    if (weight) settings.push(`"wght" ${Math.round(100 + proximity * 800)}`);
    if (width) settings.push(`"wdth" ${Math.round(75 + proximity * 50)}`);
    if (italic) settings.push(`"ital" ${proximity > 0.5 ? 1 : 0}`);

    if (settings.length > 0) {
      style.fontVariationSettings = settings.join(', ');
    }

    if (alpha) {
      style.opacity = 0.3 + proximity * 0.7;
    }

    if (flex) {
      style.transform = `scaleX(${1 + proximity * 0.3})`;
    }

    return style;
  };

  const chars = text.split('');

  return (
    <div
      ref={containerRef}
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => setIsHovering(false)}
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'default',
        userSelect: 'none',
        overflow: 'hidden',
      }}
    >
      <span style={{ display: 'flex', gap: '2px', alignItems: 'baseline' }}>
        {chars.map((char, i) => (
          <span key={i} style={getStyle(i, chars.length)}>
            {char === ' ' ? '\u00A0' : char}
          </span>
        ))}
      </span>
    </div>
  );
};

export default TextPressure;
