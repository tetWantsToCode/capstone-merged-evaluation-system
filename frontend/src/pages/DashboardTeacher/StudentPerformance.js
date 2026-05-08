import React, { useEffect, useMemo, useState, useCallback, useRef } from "react";
import ReactDOM from "react-dom";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { ChevronRight, X, RefreshCw } from "lucide-react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { performanceAPI } from "../../services/api";
import "./Teacher.css";

const API_BASE_URL =
  process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api";

const QA_FILTER_OPTIONS = ["all", "withScores", "textAnswers", "unanswered"];

// ─── Shared AI helpers (mirrors EvaluationDetail.js) ────────────────────────

const EVIDENCE_STOP_WORDS = new Set([
  "the","and","for","with","from","that","this","have","has","were","was","are","is","you",
  "your","their","they","them","into","onto","about","what","when","where","which","while",
  "there","here","been","being","than","then","very","more","most","some","many","much",
  "only","just","also","over","under","after","before","because","through","response","responses",
  "question","questions","answer","answers","general","comments","team","student","peer","self",
]);

const isLikelySectionTitle = (line) => {
  if (!line) return false;
  const trimmed = line.trim();
  if (trimmed.length < 3 || trimmed.length > 80) return false;
  if (!/[A-Za-z]/.test(trimmed)) return false;
  return trimmed === trimmed.toUpperCase() && !/[.:]$/.test(trimmed);
};

const parseAiFeedbackSections = (feedbackText) => {
  if (!feedbackText?.trim()) return [];
  const lines = feedbackText.split("\n").map((l) => l.trim());
  const sections = [];
  let current = null;

  const ensureCurrent = () => {
    if (!current) {
      current = { title: "AI Insight", paragraphs: [], bullets: [] };
      sections.push(current);
    }
  };

  lines.forEach((line) => {
    if (!line) return;
    const colonMatch = line.match(/^([A-Za-z][A-Za-z\s/&()\-]{1,60}):\s*(.*)$/);
    if (colonMatch && !line.startsWith("- ") && !line.startsWith("• ")) {
      current = { title: colonMatch[1].trim(), paragraphs: [], bullets: [] };
      sections.push(current);
      if (colonMatch[2].trim()) current.paragraphs.push(colonMatch[2].trim());
      return;
    }
    if (isLikelySectionTitle(line)) {
      current = { title: line, paragraphs: [], bullets: [] };
      sections.push(current);
      return;
    }
    ensureCurrent();
    if (line.startsWith("- ") || line.startsWith("• ")) {
      current.bullets.push(line.substring(2).trim());
    } else {
      current.paragraphs.push(line);
    }
  });
  return sections;
};

const toSortedNumber = (v) => {
  const p = Number(v);
  return Number.isFinite(p) ? p : Number.MAX_SAFE_INTEGER;
};

// ─── Q&A row builder (works for both individual and peer eval data) ──────────

const buildQARows = (evalData) => {
  const scores = Array.isArray(evalData?.scores) ? evalData.scores : [];
  const rows = [];
  const rendered = new Set();

  // Group by sectionTitle for display order
  const bySection = {};
  const noSection = [];

  scores.forEach((s) => {
    const sec = s.sectionTitle || null;
    if (sec) {
      if (!bySection[sec]) bySection[sec] = [];
      bySection[sec].push(s);
    } else {
      noSection.push(s);
    }
  });

  const pushRow = (score) => {
    if (rendered.has(score.id)) return;
    rendered.add(score.id);
    const hasNum = score.numericScore !== null && score.numericScore !== undefined;
    const hasTxt = !!(score.textResponse?.trim());
    let answerText = "Not answered";
    if (hasNum || hasTxt) {
      const parts = [];
      if (hasNum) {
        const scaleLabel =
          score.minScore !== null && score.maxScore !== null
            ? ` (Scale ${score.minScore}-${score.maxScore})`
            : "";
        parts.push(`${score.numericScore}${scaleLabel}`);
      }
      if (hasTxt) parts.push(score.textResponse.trim());
      answerText = parts.join(" | ");
    }
    rows.push({
      key: score.id ?? `row-${rows.length}`,
      sectionTitle: score.sectionTitle || null,
      questionText: score.questionText || "Untitled question",
      answerText,
      hasNumericAnswer: hasNum,
      hasTextAnswer: hasTxt,
      isAnswered: hasNum || hasTxt,
    });
  };

  Object.keys(bySection)
    .sort()
    .forEach((sec) => bySection[sec].forEach(pushRow));
  noSection.forEach(pushRow);

  return rows;
};

// ─── Score summary helpers ────────────────────────────────────────────────────

const formatScoreLine = (summary) => {
  if (!summary || summary.totalScore === null) return "No numeric data";
  const pct =
    summary.percentage !== null ? ` (${summary.percentage.toFixed(1)}%)` : "";
  const max =
    summary.maxPossible !== null
      ? ` / ${summary.maxPossible}`
      : "";
  return `${summary.totalScore}${max}${pct}`;
};

const getPerformanceLevel = (pct) => {
  if (pct === null) return "N/A";
  if (pct < 50) return "Below Average";
  if (pct < 75) return "Average";
  return "Above Average";
};

const getLevelClass = (pct) => {
  if (pct === null) return "";
  if (pct < 50) return "perf-level--low";
  if (pct < 75) return "perf-level--mid";
  return "perf-level--high";
};

// ─── Aggregate score across multiple evaluations ──────────────────────────────

const aggregateSummaries = (evaluations) => {
  let total = 0;
  let maxPossible = 0;
  let hasData = false;

  (evaluations || []).forEach((ev) => {
    const s = ev.scoresSummary;
    if (s?.totalScore !== null && s?.totalScore !== undefined) {
      total += s.totalScore;
      hasData = true;
    }
    if (s?.maxPossible !== null && s?.maxPossible !== undefined) {
      maxPossible += s.maxPossible;
    }
  });

  if (!hasData) return { totalScore: null, maxPossible: null, percentage: null };
  const pct = maxPossible > 0 ? (total / maxPossible) * 100 : null;
  return {
    totalScore: Math.round(total * 100) / 100,
    maxPossible: maxPossible > 0 ? Math.round(maxPossible * 100) / 100 : null,
    percentage: pct !== null ? Math.round(pct * 100) / 100 : null,
  };
};

// ─── AI context builder ───────────────────────────────────────────────────────

const buildIndividualAiContext = (studentName, teamName, evaluations) => {
  const lines = [];
  lines.push(`Student: ${studentName}`);
  lines.push(`Team: ${teamName}`);
  lines.push(`Context: This is a team-level adviser evaluation. The results apply to the team environment in which ${studentName} participates.`);
  lines.push("");

  evaluations.forEach((ev, idx) => {
    lines.push(`--- Evaluation ${idx + 1}: ${ev.questionnaireTitle} ---`);
    lines.push(`Adviser: ${ev.adviserName || "N/A"}`);
    lines.push(`Submitted: ${ev.submittedAt ? new Date(ev.submittedAt).toLocaleString() : "N/A"}`);
    const s = ev.scoresSummary;
    if (s?.totalScore !== null) {
      lines.push(
        `Score: ${s.totalScore}${s.maxPossible !== null ? ` / ${s.maxPossible}` : ""}${s.percentage !== null ? ` (${s.percentage.toFixed(1)}%)` : ""}`
      );
    }
    lines.push(`General Comments: ${ev.generalComments?.trim() || "None"}`);
    const qaRows = buildQARows(ev);
    qaRows.forEach((row, i) => {
      lines.push(`Q${i + 1}: ${row.questionText}`);
      lines.push(`Answer: ${row.answerText}`);
    });
    lines.push("");
  });

  lines.push("Important: Do not invent data. Base your analysis strictly on the provided evaluation content.");
  lines.push("Provide insights on what this team evaluation implies for the individual student named above.");
  return lines.join("\n");
};

const buildPeerAiContext = (studentName, evaluations) => {
  const lines = [];
  lines.push(`Student Being Evaluated: ${studentName}`);
  lines.push(`Context: These are peer-to-peer evaluations received by ${studentName} from their teammates.`);
  lines.push("");

  evaluations.forEach((ev, idx) => {
    lines.push(`--- Peer Evaluation ${idx + 1}: ${ev.questionnaireTitle} ---`);
    lines.push(`Evaluator: ${ev.evaluatorName || "Anonymous"}`);
    lines.push(`Submitted: ${ev.submittedAt ? new Date(ev.submittedAt).toLocaleString() : "N/A"}`);
    const s = ev.scoresSummary;
    if (s?.totalScore !== null) {
      lines.push(
        `Score: ${s.totalScore}${s.maxPossible !== null ? ` / ${s.maxPossible}` : ""}${s.percentage !== null ? ` (${s.percentage.toFixed(1)}%)` : ""}`
      );
    }
    const qaRows = buildQARows(ev);
    qaRows.forEach((row, i) => {
      lines.push(`Q${i + 1}: ${row.questionText}`);
      lines.push(`Answer: ${row.answerText}`);
    });
    lines.push("");
  });

  lines.push("Important: Do not invent data. Base analysis strictly on the provided peer evaluation responses.");
  return lines.join("\n");
};

// ─── QA Modal ────────────────────────────────────────────────────────────────

const QAModal = ({ evalData, onClose, label }) => {
  const [qaFilter, setQaFilter] = useState("all");
  const allRows = useMemo(() => buildQARows(evalData), [evalData]);

  const filtered = useMemo(() => {
    if (qaFilter === "withScores") return allRows.filter((r) => r.hasNumericAnswer);
    if (qaFilter === "textAnswers") return allRows.filter((r) => r.hasTextAnswer);
    if (qaFilter === "unanswered") return allRows.filter((r) => !r.isAnswered);
    return allRows;
  }, [allRows, qaFilter]);

  // Escape key
  useEffect(() => {
    const handle = (e) => { if (e.key === "Escape") onClose(); };
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handle);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", handle);
    };
  }, [onClose]);

  // Group filtered rows by section for display
  const groups = useMemo(() => {
    const result = [];
    const map = new Map();
    filtered.forEach((row) => {
      const key = row.sectionTitle ?? "__general__";
      if (!map.has(key)) {
        const g = { sectionTitle: row.sectionTitle, rows: [] };
        map.set(key, g);
        result.push(g);
      }
      map.get(key).rows.push(row);
    });
    return result;
  }, [filtered]);

  let globalQ = 0;

  return ReactDOM.createPortal(
    <div
      className="evaluation-info-modal-overlay"
      onClick={onClose}
    >
      <div
        className="evaluation-info-modal"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="evaluation-info-modal-header">
          <div>
            <h2>{evalData.questionnaireTitle || "N/A"}</h2>
            <span className="perf-modal-subtitle">{label}</span>
          </div>
          <div className="evaluation-info-modal-actions">
            <button className="btn-secondary" onClick={onClose}>Close</button>
          </div>
        </div>

        <div className="evaluation-info-modal-body">
          <div className="evaluation-modal-summary-row">
            <span className="evaluation-modal-count">
              Showing {filtered.length} of {allRows.length} questions
            </span>
          </div>

          <div className="evaluation-modal-filter-row">
            {QA_FILTER_OPTIONS.map((key) => {
              const chipLabel =
                key === "all" ? "All"
                : key === "withScores" ? "With Scores"
                : key === "textAnswers" ? "Text Answers"
                : "Unanswered";
              return (
                <button
                  key={key}
                  type="button"
                  className={`evaluation-filter-chip ${qaFilter === key ? "is-active" : ""}`}
                  onClick={() => setQaFilter(key)}
                >
                  {chipLabel}
                </button>
              );
            })}
          </div>

          {groups.length > 0 ? (
            groups.map((group, gIdx) => {
              return (
                <div key={group.sectionTitle ?? `group-${gIdx}`} className="qa-section-group">
                  {group.sectionTitle && (
                    <div className="qa-section-header">
                      <div className="qa-section-header-left">
                        <span className="qa-section-title">{group.sectionTitle}</span>
                      </div>
                      <span className="qa-section-q-count">{group.rows.length} Q</span>
                    </div>
                  )}
                  <div className="qa-question-list">
                    {group.rows.map((row) => {
                      globalQ += 1;
                      const qNum = globalQ;
                      return (
                        <div key={row.key} className={`qa-question-row ${!row.isAnswered ? "is-unanswered" : ""}`}>
                          <div className="qa-question-top">
                            <span className="qa-question-num">Q{qNum}</span>
                            <span className="qa-question-text">{row.questionText}</span>
                          </div>
                          <div className={`qa-answer-block ${!row.isAnswered ? "is-empty" : ""}`}>
                            {row.answerText}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })
          ) : (
            <p className="pending">No responses match this filter.</p>
          )}

          <div className="evaluation-general-comment-box">
            <strong>General Comments:</strong>{" "}
            {evalData.generalComments?.trim() || "None"}
          </div>
        </div>

        <div className="evaluation-info-modal-footer">
          <span>{filtered.length} visible question(s)</span>
        </div>
      </div>
    </div>,
    document.body
  );
};

// ─── AI Panel ─────────────────────────────────────────────────────────────────

const AiPanel = ({ title, subtitle, feedback, loading, error, onRegenerate, token }) => {
  const sections = useMemo(() => parseAiFeedbackSections(feedback), [feedback]);

  return (
    <div className="evaluation-ai-panel perf-ai-panel">
      <div className="evaluation-feedback-header">
        <div>
          <h3 className="perf-ai-title">{title}</h3>
          {subtitle && (
            <div className="evaluation-feedback-subtitle">{subtitle}</div>
          )}
        </div>
        <button
          className="btn-secondary"
          onClick={onRegenerate}
          disabled={loading || !token}
        >
          {loading ? (
            <><RefreshCw size={13} className="perf-spin" /> Analyzing...</>
          ) : (
            "Regenerate"
          )}
        </button>
      </div>

      <div className="evaluation-feedback-chip-row">
        <span className="evaluation-feedback-chip">Responses</span>
        <span className="evaluation-feedback-chip">No Invented Data</span>
        <span className="evaluation-feedback-chip">AI Insights</span>
      </div>

      {!token && (
        <p className="pending">AI feedback requires a valid login session.</p>
      )}

      {error && <div className="error-message">{error}</div>}

      {loading ? (
        <div className="evaluation-ai-feedback-box ai-feedback-skeleton-box">
          <div className="ai-feedback-skeleton-card" />
          <div className="ai-feedback-skeleton-card" />
          <div className="ai-feedback-skeleton-card" />
        </div>
      ) : feedback ? (
        <div className="evaluation-ai-feedback-box">
          {sections.length > 0 ? (
            <div className="ai-feedback-sections">
              {sections.map((sec, idx) => (
                <div key={`${sec.title}-${idx}`} className="ai-feedback-section-card">
                  <h4 className="ai-feedback-section-title">{sec.title}</h4>
                  {sec.paragraphs.map((p, pi) => (
                    <p key={pi} className="ai-feedback-paragraph">{p}</p>
                  ))}
                  {sec.bullets.length > 0 && (
                    <ul className="ai-feedback-list">
                      {sec.bullets.map((b, bi) => (
                        <li key={bi}>{b}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <span>{feedback}</span>
          )}
        </div>
      ) : (
        <div className="evaluation-ai-feedback-box">
          <span className="pending">No AI analysis available.</span>
        </div>
      )}
    </div>
  );
};

// ─── Evaluation Row (per questionnaire inside a card) ─────────────────────────

const EvalRow = ({ ev, onViewDetails, type }) => {
  const s = ev.scoresSummary;
  const pct = s?.percentage ?? null;
  const levelClass = getLevelClass(pct);

  return (
    <div className="perf-eval-row">
      <div className="perf-eval-row-left">
        <span className="perf-eval-title">{ev.questionnaireTitle || "N/A"}</span>
        <span className="perf-eval-meta">
          {type === "individual"
            ? `Adviser: ${ev.adviserName || "N/A"}`
            : `Evaluator: ${ev.evaluatorName || "Peer"}`}
          {ev.submittedAt ? " · " + new Date(ev.submittedAt).toLocaleDateString() : ""}
        </span>
      </div>
      <div className="perf-eval-row-right">
        {s?.totalScore !== null && (
          <span className={`perf-level-chip ${levelClass}`}>
            {formatScoreLine(s)}
          </span>
        )}
        <button
          type="button"
          className="btn-secondary perf-view-btn"
          onClick={() => onViewDetails(ev)}
        >
          View Details
        </button>
      </div>
    </div>
  );
};

// ─── Eval List Modal ────────────────────────────────────────────────────────

const EvalListModal = ({ title, evaluations, type, onClose, onViewDetails }) => {
  useEffect(() => {
    const handle = (e) => { if (e.key === "Escape") onClose(); };
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handle);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", handle);
    };
  }, [onClose]);

  return ReactDOM.createPortal(
    <div className="evaluation-info-modal-overlay" onClick={onClose}>
      <div
        className="evaluation-info-modal"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="evaluation-info-modal-header">
          <div>
            <h2>{title}</h2>
            <span className="perf-modal-subtitle">{evaluations.length} evaluation{evaluations.length !== 1 ? "s" : ""}</span>
          </div>
          <div className="evaluation-info-modal-actions">
            <button className="btn-secondary" onClick={onClose}>Close</button>
          </div>
        </div>

        <div className="evaluation-info-modal-body">
          <div className="perf-eval-list">
            {evaluations.map((ev) => (
              <EvalRow
                key={ev.id}
                ev={ev}
                type={type}
                onViewDetails={onViewDetails}
              />
            ))}
          </div>
        </div>

        <div className="evaluation-info-modal-footer">
          <span>{evaluations.length} questionnaire{evaluations.length !== 1 ? "s" : ""} listed</span>
        </div>
      </div>
    </div>,
    document.body
  );
};

// ─── Performance Card ─────────────────────────────────────────────────────────

const PerformanceCard = ({
  title,
  subtitle,
  evaluations,
  loading,
  error,
  type,
  aiFeedback,
  aiLoading,
  aiError,
  onRegenerate,
  token,
}) => {
  const [listModalOpen, setListModalOpen] = useState(false);
  const [qaModalEval, setQaModalEval] = useState(null);

  const aggregate = useMemo(
    () => aggregateSummaries(evaluations),
    [evaluations]
  );

  const pct = aggregate.percentage;

  const handleViewDetails = useCallback((ev) => {
    setQaModalEval(ev);
  }, []);

  return (
    <div className="section perf-card">
      <div className="perf-card-header">
        <div>
          <h2 className="perf-card-title">{title}</h2>
          {subtitle && <p className="perf-card-subtitle">{subtitle}</p>}
        </div>
        {!loading && !error && evaluations.length > 0 && (
          <div className="perf-aggregate-badge">
            <span className="perf-aggregate-score">{formatScoreLine(aggregate)}</span>
            <span className={`perf-level-text ${getLevelClass(pct)}`}>
              {getPerformanceLevel(pct)}
            </span>
          </div>
        )}
      </div>

      {loading && (
        <div className="perf-skeleton-grid">
          {[1, 2].map((n) => (
            <div key={n} className="perf-eval-row perf-eval-row--skeleton" />
          ))}
        </div>
      )}

      {!loading && error && <div className="error-message">{error}</div>}

      {!loading && !error && evaluations.length === 0 && (
        <p className="pending">No completed evaluations found for this category.</p>
      )}

      {!loading && !error && evaluations.length > 0 && (
        <>
          <div className="perf-eval-summary-row">
            <span className="perf-eval-count-chip">
              {evaluations.length} questionnaire{evaluations.length !== 1 ? "s" : ""} evaluated
            </span>
            <button
              type="button"
              className="btn-secondary perf-view-all-btn"
              onClick={() => setListModalOpen(true)}
            >
              View All Evaluations
            </button>
          </div>

          <AiPanel
            title={
              type === "individual"
                ? "AI Feedback on Individual Evaluations"
                : "AI Feedback on Peer Evaluations"
            }
            subtitle="Strict analysis based only on provided evaluation answers and comments."
            feedback={aiFeedback}
            loading={aiLoading}
            error={aiError}
            onRegenerate={onRegenerate}
            token={token}
          />
        </>
      )}

      {listModalOpen && (
        <EvalListModal
          title={title}
          evaluations={evaluations}
          type={type}
          onClose={() => setListModalOpen(false)}
          onViewDetails={handleViewDetails}
        />
      )}

      {qaModalEval && (
        <QAModal
          evalData={qaModalEval}
          onClose={() => setQaModalEval(null)}
          label={type === "individual" ? "Individual Evaluation" : "Peer Evaluation"}
        />
      )}
    </div>
  );
};

// ─── Main StudentPerformance page ─────────────────────────────────────────────

const StudentPerformance = () => {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const fromTeamId = searchParams.get("from");

  const token = useMemo(() => {
    try {
      return JSON.parse(localStorage.getItem("user"))?.token || null;
    } catch {
      return null;
    }
  }, []);

  // Individual data
  const [individualData, setIndividualData] = useState(null);
  const [loadingIndividual, setLoadingIndividual] = useState(true);
  const [individualError, setIndividualError] = useState(null);

  // Peer data
  const [peerData, setPeerData] = useState(null);
  const [loadingPeer, setLoadingPeer] = useState(true);
  const [peerError, setPeerError] = useState(null);

  // AI state — individual
  const [indAiFeedback, setIndAiFeedback] = useState("");
  const [indAiLoading, setIndAiLoading] = useState(false);
  const [indAiError, setIndAiError] = useState(null);
  const indAiGeneratedRef = useRef(false);

  // AI state — peer
  const [peerAiFeedback, setPeerAiFeedback] = useState("");
  const [peerAiLoading, setPeerAiLoading] = useState(false);
  const [peerAiError, setPeerAiError] = useState(null);
  const peerAiGeneratedRef = useRef(false);

  // Load data
  useEffect(() => {
    const loadInd = async () => {
      try {
        setLoadingIndividual(true);
        const data = await performanceAPI.getIndividualPerformance(studentId);
        setIndividualData(data);
      } catch (err) {
        setIndividualError("Failed to load individual evaluations: " + err.message);
      } finally {
        setLoadingIndividual(false);
      }
    };

    const loadPeer = async () => {
      try {
        setLoadingPeer(true);
        const data = await performanceAPI.getPeerPerformance(studentId);
        setPeerData(data);
      } catch (err) {
        setPeerError("Failed to load peer evaluations: " + err.message);
      } finally {
        setLoadingPeer(false);
      }
    };

    loadInd();
    loadPeer();
  }, [studentId]);

  // Auto-generate AI once data is loaded
  useEffect(() => {
    if (
      !loadingIndividual &&
      individualData?.evaluations?.length > 0 &&
      token &&
      !indAiGeneratedRef.current
    ) {
      indAiGeneratedRef.current = true;
      generateIndividualAi();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadingIndividual, individualData, token]);

  useEffect(() => {
    if (
      !loadingPeer &&
      peerData?.evaluations?.length > 0 &&
      token &&
      !peerAiGeneratedRef.current
    ) {
      peerAiGeneratedRef.current = true;
      generatePeerAi();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadingPeer, peerData, token]);

  const generateIndividualAi = useCallback(async () => {
    if (!token || !individualData?.evaluations?.length) return;
    setIndAiLoading(true);
    setIndAiError(null);
    try {
      const studentName = individualData.studentName || "the student";
      const teamName =
        individualData.evaluations[0]?.teamName || "the team";

      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          message: `Based on this team's adviser evaluation, here is what this implies for ${studentName}. Summarize using only the provided evaluation data. Do not invent data. Use exactly these sections in this order: Overall Team Score, Performance Implications for ${studentName}, Key Strengths, Key Issues, Suggested Growth Actions.`,
          contextType: "response_summary",
          context: buildIndividualAiContext(
            studentName,
            teamName,
            individualData.evaluations
          ),
          history: [],
        }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok)
        throw new Error(
          data?.message || data?.error || `AI request failed (HTTP ${res.status})`
        );
      setIndAiFeedback(data?.reply || "AI feedback is currently unavailable.");
    } catch (err) {
      setIndAiError(err.message || "Failed to generate AI feedback");
    } finally {
      setIndAiLoading(false);
    }
  }, [token, individualData]);

  const generatePeerAi = useCallback(async () => {
    if (!token || !peerData?.evaluations?.length) return;
    setPeerAiLoading(true);
    setPeerAiError(null);
    try {
      const studentName = peerData.studentName || "the student";
      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          message: `Analyze these peer-to-peer evaluations received by ${studentName}. Use only the provided data. Do not invent. Use exactly these sections: Overall Peer Score, Peer Perception, Strengths Recognized by Peers, Areas for Improvement, Suggested Actions.`,
          contextType: "response_summary",
          context: buildPeerAiContext(studentName, peerData.evaluations),
          history: [],
        }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok)
        throw new Error(
          data?.message || data?.error || `AI request failed (HTTP ${res.status})`
        );
      setPeerAiFeedback(data?.reply || "AI feedback is currently unavailable.");
    } catch (err) {
      setPeerAiError(err.message || "Failed to generate AI feedback");
    } finally {
      setPeerAiLoading(false);
    }
  }, [token, peerData]);

  const handleRegenIndividual = useCallback(() => {
    indAiGeneratedRef.current = true;
    generateIndividualAi();
  }, [generateIndividualAi]);

  const handleRegenPeer = useCallback(() => {
    peerAiGeneratedRef.current = true;
    generatePeerAi();
  }, [generatePeerAi]);

  const handleBack = () => {
    if (fromTeamId) {
      navigate(`/teacher/performance?team=${fromTeamId}`);
    } else {
      navigate("/teacher/performance");
    }
  };

  const studentName =
    individualData?.studentName || peerData?.studentName || "Student";

  const teamName =
    individualData?.evaluations?.[0]?.teamName || "";

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        {/* Breadcrumb */}
        <nav className="perf-breadcrumb" aria-label="breadcrumb">
          <button
            type="button"
            className="perf-breadcrumb-link"
            onClick={() => navigate("/teacher/performance")}
          >
            Performance
          </button>
          {teamName && (
            <>
              <ChevronRight size={13} className="perf-breadcrumb-sep" />
              <button
                type="button"
                className="perf-breadcrumb-link"
                onClick={handleBack}
              >
                {teamName}
              </button>
            </>
          )}
          <ChevronRight size={13} className="perf-breadcrumb-sep" />
          <span className="perf-breadcrumb-current">{studentName}</span>
        </nav>

        <div className="perf-page-header">
          <h2 className="perf-page-title">{studentName}</h2>
          {teamName && (
            <p className="perf-page-subtitle">Team: {teamName}</p>
          )}
        </div>

        {/* Individual Card */}
        <PerformanceCard
          title="Individual Evaluations"
          subtitle="Overall performance based on adviser team evaluations"
          evaluations={individualData?.evaluations ?? []}
          loading={loadingIndividual}
          error={individualError}
          type="individual"
          aiFeedback={indAiFeedback}
          aiLoading={indAiLoading}
          aiError={indAiError}
          onRegenerate={handleRegenIndividual}
          token={token}
        />

        {/* Peer Card */}
        <PerformanceCard
          title="Peer-to-Peer Evaluations"
          subtitle="Overall performance based on evaluations received from teammates"
          evaluations={peerData?.evaluations ?? []}
          loading={loadingPeer}
          error={peerError}
          type="peer"
          aiFeedback={peerAiFeedback}
          aiLoading={peerAiLoading}
          aiError={peerAiError}
          onRegenerate={handleRegenPeer}
          token={token}
        />
      </div>
    </div>
  );
};

export default StudentPerformance;
