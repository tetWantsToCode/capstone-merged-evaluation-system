/**
 * Generate a decimal rating range for RATING questions
 * e.g., for 1-10: [10, 9.9, 9.8, 9.7, 9.6, 9.5, 9.4, 9.3, 9.2, 9.1, 9, 8, 7, 6, 5, 4, 3, 2, 1]
 * e.g., for 1-5: [5, 4.9, 4.8, 4.7, 4.6, 4.5, 4.4, 4.3, 4.2, 4.1, 4, 3, 2, 1]
 * e.g., for 1-3: [3, 2.9, 2.8, 2.7, 2.6, 2.5, 2.4, 2.3, 2.2, 2.1, 2, 1]
 */
export const generateDecimalRatingRange = (minScore, maxScore) => {
  const min = minScore ?? 1;
  const max = maxScore ?? 5;
  const range = [];

  // Add the highest value
  range.push(max);

  // Add 9 decimal increments from max-0.1 to max-0.9
  for (let i = 9; i >= 1; i--) {
    const decimalValue = parseFloat((max - (i / 10)).toFixed(1));
    range.push(decimalValue);
  }

  // Add remaining integer values from max-1 down to min
  for (let i = max - 1; i >= min; i--) {
    range.push(i);
  }

  return range;
};

/**
 * Generate a numeric range (no decimals) in reverse order
 * e.g., for 1-5: [5, 4, 3, 2, 1]
 */
export const generateNumericRange = (minScore, maxScore) => {
  const min = minScore ?? 1;
  const max = maxScore ?? 5;
  const range = [];
  for (let i = max; i >= min; i--) {
    range.push(i);
  }
  return range;
};
