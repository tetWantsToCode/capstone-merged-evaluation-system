import React, { useEffect, useMemo, useRef, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teacherReportAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api";
const QA_FILTER_OPTIONS = ["all", "withScores", "textAnswers", "unanswered"];
const EVIDENCE_STOP_WORDS = new Set([
  "the", "and", "for", "with", "from", "that", "this", "have", "has", "were", "was", "are", "is", "you",
  "your", "their", "they", "them", "into", "onto", "about", "what", "when", "where", "which", "while",
  "there", "here", "been", "being", "than", "then", "very", "more", "most", "some", "many", "much",
  "only", "just", "also", "over", "under", "after", "before", "because", "through", "response", "responses",
  "question", "questions", "answer", "answers", "general", "comments", "team", "student", "peer", "self"
]);

const isLikelySectionTitle = (line) => {
  if (!line) return false;
  const trimmed = line.trim();
  if (trimmed.length < 3 || trimmed.length > 80) return false;
  if (!/[A-Za-z]/.test(trimmed)) return false;
  const noPunctuationTail = !/[.:]$/.test(trimmed);
  return trimmed === trimmed.toUpperCase() && noPunctuationTail;
};

const parseAiFeedbackSections = (feedbackText) => {
  if (!feedbackText || !feedbackText.trim()) {
    return [];
  }

  const lines = feedbackText.split("\n").map((line) => line.trim());
  const sections = [];
  let current = null;

  const ensureCurrent = () => {
    if (!current) {
      current = { title: "AI Insight", paragraphs: [], bullets: [] };
      sections.push(current);
    }
  };

  lines.forEach((line) => {
    if (!line) {
      return;
    }

    const colonSectionMatch = line.match(/^([A-Za-z][A-Za-z\s/&()\-]{1,60}):\s*(.*)$/);
    if (colonSectionMatch && !line.startsWith("- ") && !line.startsWith("• ")) {
      const sectionTitle = colonSectionMatch[1].trim();
      const sectionValue = colonSectionMatch[2].trim();
      current = { title: sectionTitle, paragraphs: [], bullets: [] };
      sections.push(current);
      if (sectionValue) {
        current.paragraphs.push(sectionValue);
      }
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

const StudentEvaluationDetail = () => {
  const { evaluationId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  
  const [evaluation, setEvaluation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [aiFeedback, setAiFeedback] = useState("");
  const [aiFeedbackLoading, setAiFeedbackLoading] = useState(false);
  const [aiFeedbackError, setAiFeedbackError] = useState(null);
  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false);
  const [qaFilter, setQaFilter] = useState("all");

  const token = useMemo(() => {
    try {
      const user = JSON.parse(localStorage.getItem("user"));
      return user?.token || null;
    } catch {
      return null;
    }
  }, []);

  useEffect(() => {
    loadEvaluation();
  }, [evaluationId]);

  useEffect(() => {
    if (evaluation && token) {
      generateAiFeedback(evaluation);
    }
  }, [evaluation, token]);

  useEffect(() => {
    if (!isInfoModalOpen) {
      return undefined;
    }

    const handleEscape = (event) => {
      if (event.key === "Escape") {
        setIsInfoModalOpen(false);
      }
    };

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleEscape);

    return () => {
      document.body.style.overflow = originalOverflow;
      window.removeEventListener("keydown", handleEscape);
    };
  }, [isInfoModalOpen]);

  const loadEvaluation = async () => {
    try {
      setLoading(true);
      const data = await teacherReportAPI.getStudentEvaluationDetails(evaluationId);
      setEvaluation(data);
    } catch (err) {
      setError("Failed to load evaluation: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const toSortedNumber = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : Number.MAX_SAFE_INTEGER;
  };

  const formatAnswerValue = (score, item) => {
    if (!score) return "Not answered";
    const parts = [];
    if (score.numericScore !== null && score.numericScore !== undefined) {
      const hasScale = item && item.minScore !== null && item.minScore !== undefined
        && item.maxScore !== null && item.maxScore !== undefined;
      if (hasScale) {
        parts.push(`${score.numericScore} (Scale ${item.minScore}-${item.maxScore})`);
      } else {
        parts.push(String(score.numericScore));
      }
    }
    if (score.textResponse && score.textResponse.trim()) {
      parts.push(score.textResponse.trim());
    }
    return parts.length ? parts.join(" | ") : "Not answered";
  };

  const buildQuestionAnswerRows = (evalData) => {
    if (!evalData) return [];
    const questionnaire = evalData.questionnaire || {};
    const scores = Array.isArray(evalData.scores) ? evalData.scores : [];
    const sections = Array.isArray(questionnaire.sections) ? questionnaire.sections : [];
    const standaloneItems = Array.isArray(questionnaire.items) ? questionnaire.items : [];

    const scoreByItemId = new Map(
      scores
        .filter((score) => score.questionnaireItemId !== null && score.questionnaireItemId !== undefined)
        .map((score) => [score.questionnaireItemId, score])
    );

    const rows = [];
    sections
      .slice()
      .sort((a, b) => toSortedNumber(a.orderIndex) - toSortedNumber(b.orderIndex))
      .forEach((section) => {
        const sectionItems = Array.isArray(section.items) ? section.items : [];
        sectionItems
          .slice()
          .sort((a, b) => toSortedNumber(a.orderIndex) - toSortedNumber(b.orderIndex))
          .forEach((item) => {
            const score = scoreByItemId.get(item.id);
            const hasNumericAnswer = score?.numericScore !== null && score?.numericScore !== undefined;
            const hasTextAnswer = !!(score?.textResponse && score.textResponse.trim());
            rows.push({
              key: item.id ?? `section-${section.id}-${rows.length}`,
              sectionTitle: section.sectionTitle || null,
              questionText: item.questionText || score?.questionText || "Untitled question",
              answerText: formatAnswerValue(score, item),
              hasNumericAnswer,
              hasTextAnswer,
              isAnswered: hasNumericAnswer || hasTextAnswer,
            });
          });
      });

    standaloneItems
      .slice()
      .sort((a, b) => toSortedNumber(a.orderIndex) - toSortedNumber(b.orderIndex))
      .forEach((item) => {
        const score = scoreByItemId.get(item.id);
        const hasNumericAnswer = score?.numericScore !== null && score?.numericScore !== undefined;
        const hasTextAnswer = !!(score?.textResponse && score.textResponse.trim());
        rows.push({
          key: item.id ?? `item-${rows.length}`,
          sectionTitle: null,
          questionText: item.questionText || score?.questionText || "Untitled question",
          answerText: formatAnswerValue(score, item),
          hasNumericAnswer,
          hasTextAnswer,
          isAnswered: hasNumericAnswer || hasTextAnswer,
        });
      });

    return rows;
  };

  const questionAnswerRows = useMemo(() => buildQuestionAnswerRows(evaluation), [evaluation]);

  const summarySnapshot = useMemo(() => {
    if (!evaluation) return {};
    
    const scores = Array.isArray(evaluation.scores) ? evaluation.scores : [];
    const numericScores = scores.filter(
      (score) => score?.numericScore !== null && score?.numericScore !== undefined
    );

    const itemById = new Map(
      [
        ...(Array.isArray(evaluation?.questionnaire?.items) ? evaluation.questionnaire.items : []),
        ...(Array.isArray(evaluation?.questionnaire?.sections)
          ? evaluation.questionnaire.sections.flatMap((section) =>
              Array.isArray(section?.items) ? section.items : []
            )
          : []),
      ]
        .filter((item) => item?.id !== null && item?.id !== undefined)
        .map((item) => [item.id, item])
    );

    let totalScore = 0;
    let maxPossible = 0;
    numericScores.forEach((score) => {
      totalScore += Number(score.numericScore) || 0;
      const item = itemById.get(score.questionnaireItemId);
      if (item?.maxScore !== null && item?.maxScore !== undefined) {
        maxPossible += Number(item.maxScore) || 0;
      }
    });

    const hasOverallScore = numericScores.length > 0 && maxPossible > 0;
    const percentage = hasOverallScore ? (totalScore / maxPossible) * 100 : null;
    const performanceLevel = percentage === null
      ? "Not enough data"
      : percentage < 50
        ? "Below Average"
        : percentage < 75
          ? "Average"
          : "Above Average";

    const answeredCount = questionAnswerRows.filter((row) => row.isAnswered).length;
    const totalQuestions = questionAnswerRows.length;
    const lastUpdatedRaw = evaluation.updatedAt || evaluation.submittedAt || evaluation.createdAt;

    return {
      overallScore: hasOverallScore
        ? `${totalScore.toFixed(1)} / ${maxPossible.toFixed(1)}`
        : "N/A",
      performanceLevel,
      responseCount: `${answeredCount}/${totalQuestions}`,
      lastUpdated: lastUpdatedRaw ? new Date(lastUpdatedRaw).toLocaleString() : "N/A",
    };
  }, [evaluation, questionAnswerRows]);

  const filteredQuestionAnswerRows = useMemo(() => {
    if (qaFilter === "withScores") return questionAnswerRows.filter((row) => row.hasNumericAnswer);
    if (qaFilter === "textAnswers") return questionAnswerRows.filter((row) => row.hasTextAnswer);
    if (qaFilter === "unanswered") return questionAnswerRows.filter((row) => !row.isAnswered);
    return questionAnswerRows;
  }, [questionAnswerRows, qaFilter]);

  const getSectionEvidence = (section, index) => {
    const sectionText = [
      section?.title || "",
      ...(Array.isArray(section?.paragraphs) ? section.paragraphs : []),
      ...(Array.isArray(section?.bullets) ? section.bullets : []),
    ].join(" ").toLowerCase();

    const qRefs = new Set();
    questionAnswerRows.forEach((row, qIndex) => {
      const sourceText = `${row.questionText} ${row.answerText}`.toLowerCase();
      const terms = sourceText
        .split(/[^a-z0-9]+/)
        .filter((term) => term.length >= 4 && !EVIDENCE_STOP_WORDS.has(term))
        .slice(0, 15);

      if (terms.some((term) => sectionText.includes(term))) {
        qRefs.add(`Q${qIndex + 1}`);
      }
    });

    const evidenceParts = [];
    if (qRefs.size > 0) evidenceParts.push([...qRefs].slice(0, 4).join(", "));
    if (evidenceParts.length === 0) evidenceParts.push(`Section ${index + 1} content`);

    return evidenceParts.join(" • ");
  };

  const copyQuestionnaireToClipboard = async () => {
    const lines = [
      `Questionnaire: ${evaluation?.questionnaire?.title || "N/A"}`,
      `Team: ${evaluation?.teamName || "N/A"}`,
      `Evaluator: ${evaluation?.evaluatorName || "N/A"}`,
      `Evaluatee: ${evaluation?.evaluateeName || "N/A"}`,
      "",
      "Questionnaire Q&A:",
      ...questionAnswerRows.map((row, index) => {
        const sectionLabel = row.sectionTitle ? ` [Section: ${row.sectionTitle}]` : "";
        return `Q${index + 1}${sectionLabel}: ${row.questionText}\nAnswer: ${row.answerText}`;
      }),
    ];
    try {
      await navigator.clipboard.writeText(lines.join("\n\n"));
      toast.success("Copied to clipboard");
    } catch {
      toast.error("Failed to copy");
    }
  };

  const buildAiContext = (evalData) => {
    const questionLines = buildQuestionAnswerRows(evalData).map((row, index) => {
      const sectionLabel = row.sectionTitle ? ` [Section: ${row.sectionTitle}]` : "";
      return `${index + 1}. Question${sectionLabel}: ${row.questionText}\n   Answer: ${row.answerText}`;
    });

    return [
      `Questionnaire: ${evalData?.questionnaire?.title || "N/A"}`,
      `Team: ${evalData?.teamName || "N/A"}`,
      `Evaluator: ${evalData?.evaluatorName || "N/A"}`,
      `Evaluatee: ${evalData?.evaluateeName || "N/A"}`,
      `Type: ${evalData?.isSelf ? "Self-Evaluation" : "Peer-Evaluation"}`,
      "Important constraints: summarize only what is explicitly present in the answers; do not invent data; if evidence is insufficient, write 'Not enough data from responses'.",
      "Responses:",
      questionLines.length ? questionLines.join("\n") : "No responses recorded.",
    ].join("\n\n");
  };

  const generateAiFeedback = async (evalData) => {
    if (!token || !evalData) return;
    setAiFeedbackLoading(true);
    setAiFeedbackError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          message: "Summarize this student evaluation using only the provided questionnaire answers. Use exactly these sections in this order: Overall Score, Performance Level, Key Strengths, Key Issues, Brief Summary, Suggested Actions.",
          contextType: "response_summary",
          context: buildAiContext(evalData),
          history: [],
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.message || "Failed to generate AI feedback");
      setAiFeedback(data?.reply || "AI feedback unavailable.");
    } catch (err) {
      setAiFeedbackError(err.message);
    } finally {
      setAiFeedbackLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content"><p>Loading evaluation...</p></div>
      </div>
    );
  }

  if (error || !evaluation) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content">
          <div className="error-message">{error || "Evaluation not found"}</div>
          <button className="btn-secondary" onClick={() => navigate("/teacher/reports")}>Back to Reports</button>
        </div>
      </div>
    );
  }

  const aiFeedbackSections = parseAiFeedbackSections(aiFeedback);

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <div style={{ marginBottom: "20px" }}>
          <button className="btn-secondary" onClick={() => navigate("/teacher/reports")}>← Back to Reports</button>
        </div>

        <h1>Student Evaluation Details</h1>

        <div className="evaluation-snapshot-row">
          <div className="evaluation-snapshot-card">
            <span className="evaluation-snapshot-label">Overall Score</span>
            <span className="evaluation-snapshot-value">{summarySnapshot.overallScore}</span>
          </div>
          <div className="evaluation-snapshot-card">
            <span className="evaluation-snapshot-label">Performance Level</span>
            <span className="evaluation-snapshot-value">{summarySnapshot.performanceLevel}</span>
          </div>
          <div className="evaluation-snapshot-card">
            <span className="evaluation-snapshot-label">Response Count</span>
            <span className="evaluation-snapshot-value">{summarySnapshot.responseCount}</span>
          </div>
          <div className="evaluation-snapshot-card">
            <span className="evaluation-snapshot-label">Last Updated</span>
            <span className="evaluation-snapshot-value is-sm">{summarySnapshot.lastUpdated}</span>
          </div>
        </div>

        <div className="section">
          <div className="evaluation-detail-topbar">
            <h2>Evaluation Information</h2>
            <button className="btn-secondary" onClick={() => setIsInfoModalOpen(true)}>View Questionnaire Q&A</button>
          </div>

          <div className="evaluation-info-grid">
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Questionnaire</span>
              <span className="evaluation-info-value">{evaluation.questionnaire?.title || "N/A"}</span>
            </div>
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Team</span>
              <span className="evaluation-info-value">{evaluation.teamName}</span>
            </div>
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Evaluator</span>
              <span className="evaluation-info-value">{evaluation.evaluatorName}</span>
            </div>
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Evaluatee</span>
              <span className="evaluation-info-value">{evaluation.evaluateeName} {evaluation.isSelf ? "(Self)" : "(Peer)"}</span>
            </div>
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Status</span>
              <span className={`evaluation-info-value ${evaluation.status === "SUBMITTED" ? "completed" : "pending"}`}>{evaluation.status}</span>
            </div>
            <div className="evaluation-info-item">
              <span className="evaluation-info-label">Submitted</span>
              <span className="evaluation-info-value">{evaluation.submittedAt ? new Date(evaluation.submittedAt).toLocaleString() : "Not submitted"}</span>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="evaluation-ai-panel">
            <div className="evaluation-feedback-header">
              <div>
                <h2>AI Feedback on Student Evaluation</h2>
                <div className="evaluation-feedback-subtitle">Analysis based on peer and self-evaluation responses.</div>
              </div>
              <button className="btn-secondary" onClick={() => generateAiFeedback(evaluation)} disabled={aiFeedbackLoading}>
                {aiFeedbackLoading ? "Analyzing..." : "Regenerate"}
              </button>
            </div>

            <div className="evaluation-feedback-chip-row">
              <span className="evaluation-feedback-chip">Peer & Self Feedback</span>
              <span className="evaluation-feedback-chip">Score Analysis</span>
              <span className="evaluation-feedback-chip">Growth Insights</span>
            </div>

            {aiFeedbackLoading ? (
              <div className="evaluation-ai-feedback-box ai-feedback-skeleton-box">
                <div className="ai-feedback-skeleton-card" /><div className="ai-feedback-skeleton-card" /><div className="ai-feedback-skeleton-card" />
              </div>
            ) : aiFeedback ? (
              <div className="evaluation-ai-feedback-box">
                <div className="ai-feedback-sections">
                  {aiFeedbackSections.map((section, idx) => (
                    <div key={idx} className="ai-feedback-section-card">
                      <h3 className="ai-feedback-section-title">{section.title}</h3>
                      <div className="ai-feedback-evidence">Based on: {getSectionEvidence(section, idx)}</div>
                      {section.paragraphs.map((p, pi) => <p key={pi} className="ai-feedback-paragraph">{p}</p>)}
                      {section.bullets.length > 0 && <ul className="ai-feedback-list">{section.bullets.map((b, bi) => <li key={bi}>{b}</li>)}</ul>}
                    </div>
                  ))}
                </div>
              </div>
            ) : <div className="evaluation-ai-feedback-box">No AI feedback generated yet.</div>}
          </div>
        </div>

        {isInfoModalOpen && (
          <div className="evaluation-info-modal-overlay" onClick={() => setIsInfoModalOpen(false)}>
            <div className="evaluation-info-modal" onClick={(e) => e.stopPropagation()}>
              <div className="evaluation-info-modal-header">
                <h2>Questionnaire Q&A</h2>
                <div className="evaluation-info-modal-actions">
                  <button className="btn-secondary" onClick={copyQuestionnaireToClipboard}>Copy Q&A</button>
                  <button className="btn-secondary" onClick={() => setIsInfoModalOpen(false)}>Close</button>
                </div>
              </div>
              <div className="evaluation-info-modal-body">
                <div className="evaluation-modal-filter-row">
                  {QA_FILTER_OPTIONS.map((f) => (
                    <button key={f} className={`evaluation-filter-chip ${qaFilter === f ? "is-active" : ""}`} onClick={() => setQaFilter(f)}>
                      {f === "all" ? "All" : f === "withScores" ? "With Scores" : f === "textAnswers" ? "Text Answers" : "Unanswered"}
                    </button>
                  ))}
                </div>
                {filteredQuestionAnswerRows.map((row, i) => (
                  <div key={i} className="evaluation-response-item">
                    {row.sectionTitle && <div className="evaluation-response-section">{row.sectionTitle}</div>}
                    <div className="evaluation-response-question"><strong>Q{i+1}:</strong> {row.questionText}</div>
                    <div className="evaluation-response-answer"><strong>Answer:</strong> {row.answerText}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StudentEvaluationDetail;
